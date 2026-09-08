package net.njw.justpaintings.server;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.njw.justpaintings.JustPaintings;
import net.njw.justpaintings.item.PaintingItemData;
import net.njw.justpaintings.network.PaintingPayloads;
import net.njw.justpaintings.network.UploadPayloads;
import net.njw.justpaintings.registry.ModContent;

import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class PaintingUploadManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Map<UploadKey, UploadSession> SESSIONS = new HashMap<>();
    private static final int MAX_UPLOADS_PER_PLAYER = 10;
    private static final int MAX_UPLOADS_SERVER = 100;
    private static final int MAX_STORED_IMAGE_DIMENSION = 4096;

    private PaintingUploadManager() {
    }

    public static void handleStart(UploadPayloads.UploadStartPayload payload, IPayloadContext context) {
        ServerPlayer player = (ServerPlayer) context.player();
        if (!validDimensions(payload.width(), payload.height())) {
            player.sendSystemMessage(Component.translatable("message.njw_just_paintings.upload.invalid_dimensions"));
            return;
        }
        if (payload.totalSize() < 1 || payload.totalSize() > UploadPayloads.MAX_UPLOAD_SIZE) {
            player.sendSystemMessage(Component.translatable("message.njw_just_paintings.upload.invalid_request"));
            return;
        }
        String fileName = sanitizeFileName(payload.fileName());
        if (fileName.isBlank()) {
            player.sendSystemMessage(Component.translatable("message.njw_just_paintings.upload.invalid_filename"));
            return;
        }
        try {
            UploadLimit limit = getUploadLimit(player);
            if (limit != null) {
                player.sendSystemMessage(Component.translatable(limit.translationKey(), limit.maximum()));
                return;
            }
        } catch (IOException e) {
            player.sendSystemMessage(Component.translatable("message.njw_just_paintings.upload.failed"));
            return;
        }
        SESSIONS.entrySet().removeIf(entry -> entry.getKey().playerId().equals(player.getUUID()));
        SESSIONS.put(new UploadKey(player.getUUID(), payload.uploadId()), new UploadSession(fileName, payload.width(), payload.height(), payload.totalSize()));
    }

    public static void handleChunk(UploadPayloads.UploadChunkPayload payload, IPayloadContext context) {
        ServerPlayer player = (ServerPlayer) context.player();
        UploadKey key = new UploadKey(player.getUUID(), payload.uploadId());
        UploadSession session = SESSIONS.get(key);
        if (session == null) return;
        if (payload.data().length > UploadPayloads.MAX_CHUNK_SIZE || !session.append(payload.data())) {
            SESSIONS.remove(key);
            player.sendSystemMessage(Component.translatable("message.njw_just_paintings.upload.failed"));
        }
    }

    public static void handleFinish(UploadPayloads.UploadFinishPayload payload, IPayloadContext context) {
        ServerPlayer player = (ServerPlayer) context.player();
        UploadSession session = SESSIONS.remove(new UploadKey(player.getUUID(), payload.uploadId()));
        if (session == null) return;
        if (session.data.size() != session.totalSize) {
            player.sendSystemMessage(Component.translatable("message.njw_just_paintings.upload.failed"));
            return;
        }
        try {
            UploadLimit limit = getUploadLimit(player);
            if (limit != null) {
                player.sendSystemMessage(Component.translatable(limit.translationKey(), limit.maximum()));
                return;
            }
            String displayFileName = save(player, session);
            player.sendSystemMessage(Component.translatable("message.njw_just_paintings.upload.success", displayFileName));
        } catch (Exception e) {
            player.sendSystemMessage(Component.translatable("message.njw_just_paintings.upload.failed"));
        }
    }

    public static void sendChoices(ServerPlayer player, InteractionHand hand) {
        sendChoices(player, hand, true);
    }

    public static int openList(ServerPlayer player) {
        sendChoices(player, InteractionHand.MAIN_HAND, false);
        return 1;
    }

    private static void sendChoices(ServerPlayer player, InteractionHand hand, boolean selectable) {
        try {
            String playerId = player.getUUID().toString();
            List<PaintingPayloads.Choice> choices = readPaintings(player.level().getServer()).stream()
                    .filter(p -> p.stored() && validDimensions(p.width(), p.height()))
                    .map(p -> new PaintingPayloads.Choice(p.id(), p.displayFileName(), p.width(), p.height(), p.uploader(), playerId.equals(p.uploaderUuid())))
                    .toList();
            PacketDistributor.sendToPlayer(player, new PaintingPayloads.PaintingChoicesPayload(hand == InteractionHand.MAIN_HAND ? 0 : 1, selectable, choices));
        } catch (IOException e) {
            player.sendSystemMessage(Component.translatable(selectable ? "message.njw_just_paintings.selection.failed" : "command.njw_just_paintings.list.failed"));
        }
    }

    public static void handleSelection(PaintingPayloads.SelectPaintingPayload payload, IPayloadContext context) {
        if (payload.hand() > 1) return;
        ServerPlayer player = (ServerPlayer) context.player();
        InteractionHand hand = payload.hand() == 0 ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
        ItemStack stack = player.getItemInHand(hand);
        if (stack.getItem() != ModContent.CUSTOM_PAINTING.get() || PaintingItemData.isSelected(stack)) return;
        try {
            StoredPainting painting = findPainting(player.level().getServer(), payload.imageId());
            if (painting == null || !painting.stored() || !validDimensions(painting.width(), painting.height())) {
                player.sendSystemMessage(Component.translatable("message.njw_just_paintings.selection.failed"));
                return;
            }
            PaintingItemData.set(stack, painting.id(), painting.displayFileName(), painting.width(), painting.height());
            player.sendSystemMessage(Component.translatable("message.njw_just_paintings.selection.success", painting.displayFileName()));
        } catch (IOException e) {
            player.sendSystemMessage(Component.translatable("message.njw_just_paintings.selection.failed"));
        }
    }

    public static void handleDeletion(PaintingPayloads.DeletePaintingPayload payload, IPayloadContext context) {
        if (payload.hand() > 1) return;
        ServerPlayer player = (ServerPlayer) context.player();
        InteractionHand hand = payload.hand() == 0 ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
        try {
            StoredPainting painting = findPainting(player.level().getServer(), payload.imageId());
            if (painting == null) {
                player.sendSystemMessage(Component.translatable("message.njw_just_paintings.delete.failed"));
                sendChoices(player, hand, payload.selectable());
                return;
            }
            if (!player.getUUID().toString().equals(painting.uploaderUuid())) {
                player.sendSystemMessage(Component.translatable("message.njw_just_paintings.delete.denied"));
                sendChoices(player, hand, payload.selectable());
                return;
            }
            deletePainting(player.level().getServer(), painting.id(), painting.storedFileName());
            PacketDistributor.sendToAllPlayers(new PaintingPayloads.ImageRemovedPayload(painting.id()));
            player.sendSystemMessage(Component.translatable("message.njw_just_paintings.delete.success", painting.displayFileName()));
            sendChoices(player, hand, payload.selectable());
        } catch (IOException e) {
            player.sendSystemMessage(Component.translatable("message.njw_just_paintings.delete.failed"));
            sendChoices(player, hand, payload.selectable());
        }
    }

    public static void handleImageRequest(PaintingPayloads.RequestImagePayload payload, IPayloadContext context) {
        ServerPlayer player = (ServerPlayer) context.player();
        try {
            StoredPainting painting = findPainting(player.level().getServer(), payload.imageId());
            if (painting == null || !painting.stored()) return;
            Path imagePath = imagePath(root(player.level().getServer()), painting.storedFileName());
            byte[] data = Files.readAllBytes(imagePath);
            if (data.length < 1 || data.length > UploadPayloads.MAX_UPLOAD_SIZE) return;
            PacketDistributor.sendToPlayer(player, new PaintingPayloads.ImageStartPayload(painting.id(), data.length));
            for (int offset = 0; offset < data.length; offset += PaintingPayloads.MAX_IMAGE_CHUNK_SIZE) {
                byte[] chunk = Arrays.copyOfRange(data, offset, Math.min(offset + PaintingPayloads.MAX_IMAGE_CHUNK_SIZE, data.length));
                PacketDistributor.sendToPlayer(player, new PaintingPayloads.ImageChunkPayload(painting.id(), chunk));
            }
            PacketDistributor.sendToPlayer(player, new PaintingPayloads.ImageFinishPayload(painting.id()));
        } catch (IOException ignored) {
        }
    }

    public static boolean isImageStored(MinecraftServer server, UUID imageId) {
        try {
            StoredPainting painting = findPainting(server, imageId);
            return painting != null && painting.stored();
        } catch (IOException e) {
            return false;
        }
    }

    private static boolean validDimensions(int width, int height) {
        return width >= 1 && width <= 3 && height >= 1 && height <= 3;
    }

    private static UploadLimit getUploadLimit(ServerPlayer player) throws IOException {
        List<StoredPainting> paintings = readPaintings(player.level().getServer());
        String playerId = player.getUUID().toString();
        long playerCount = paintings.stream().filter(StoredPainting::stored).filter(p -> playerId.equals(p.uploaderUuid())).count();
        if (playerCount >= MAX_UPLOADS_PER_PLAYER) return new UploadLimit("message.njw_just_paintings.upload.limit.player", MAX_UPLOADS_PER_PLAYER);
        long serverCount = paintings.stream().filter(StoredPainting::stored).count();
        if (serverCount >= MAX_UPLOADS_SERVER) return new UploadLimit("message.njw_just_paintings.upload.limit.server", MAX_UPLOADS_SERVER);
        return null;
    }

    private static String save(ServerPlayer player, UploadSession session) throws IOException {
        DecodedImage decoded = decodeImage(session.data.toByteArray());
        BufferedImage image = decoded.image();
        Path root = root(player.level().getServer());
        Path images = root.resolve("images");
        Files.createDirectories(images);
        UUID imageId = UUID.randomUUID();
        String storedFileName = imageId + ".png";
        Path imagePath = imagePath(root, storedFileName);
        Path tempImagePath = images.resolve(storedFileName + ".tmp");
        boolean moved = false;
        try {
            if (!ImageIO.write(image, "PNG", tempImagePath.toFile())) throw new IOException("Could not encode PNG");
            moveAtomically(tempImagePath, imagePath);
            moved = true;
            appendMetadata(root.resolve("metadata.json"), imageId, storedFileName, session, player, decoded.sourceWidth(), decoded.sourceHeight());
            StoredPainting storedPainting = findPainting(player.level().getServer(), imageId);
            return storedPainting != null ? storedPainting.displayFileName() : session.fileName;
        } catch (Exception e) {
            if (moved) Files.deleteIfExists(imagePath);
            if (e instanceof IOException ioException) throw ioException;
            throw new IOException("Could not save painting", e);
        } finally {
            image.flush();
            Files.deleteIfExists(tempImagePath);
        }
    }

    private static DecodedImage decodeImage(byte[] bytes) throws IOException {
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            if (input == null) throw new IOException("Unsupported image file");
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new IOException("Unsupported image file");
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                int sourceWidth = reader.getWidth(0);
                int sourceHeight = reader.getHeight(0);
                if (sourceWidth < 1 || sourceHeight < 1) throw new IOException("Invalid image dimensions");
                int sample = Math.max(subsampling(sourceWidth), subsampling(sourceHeight));
                ImageReadParam param = reader.getDefaultReadParam();
                if (sample > 1) param.setSourceSubsampling(sample, sample, 0, 0);
                BufferedImage image = reader.read(0, param);
                if (image == null) throw new IOException("Unsupported image file");
                return new DecodedImage(image, sourceWidth, sourceHeight);
            } finally {
                reader.dispose();
            }
        }
    }

    private static int subsampling(int dimension) {
        return Math.max(1, 1 + (dimension - 1) / MAX_STORED_IMAGE_DIMENSION);
    }

    private static List<StoredPainting> readPaintings(MinecraftServer server) throws IOException {
        Path root = root(server);
        Path metadataPath = root.resolve("metadata.json");
        if (!Files.exists(metadataPath)) return List.of();
        JsonArray entries = readMetadata(metadataPath);
        List<StoredPainting> result = new ArrayList<>();
        Map<String, Integer> nameCounts = new HashMap<>();
        for (JsonElement element : entries) {
            if (!element.isJsonObject()) continue;
            JsonObject metadata = element.getAsJsonObject();
            String idText = stringValue(metadata, "id", "");
            String fileName = stringValue(metadata, "originalFileName", "?");
            String storedFileName = stringValue(metadata, "storedFileName", "");
            String uploaderUuid = stringValue(metadata, "uploaderUuid", "");
            String uploader = stringValue(metadata, "uploaderName", "?");
            int width = intValue(metadata, "paintingWidth", 0);
            int height = intValue(metadata, "paintingHeight", 0);
            try {
                UUID id = UUID.fromString(idText);
                int occurrence = nameCounts.merge(fileName, 1, Integer::sum);
                String displayFileName = occurrence == 1 ? fileName : fileName + " (" + occurrence + ")";
                boolean stored = false;
                if (!storedFileName.isBlank()) {
                    try {
                        stored = Files.isRegularFile(imagePath(root, storedFileName));
                    } catch (IOException ignored) {
                    }
                }
                result.add(new StoredPainting(id, fileName, displayFileName, storedFileName, uploaderUuid, uploader, width, height, stored));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return List.copyOf(result);
    }

    private static StoredPainting findPainting(MinecraftServer server, UUID id) throws IOException {
        for (StoredPainting painting : readPaintings(server)) if (painting.id().equals(id)) return painting;
        return null;
    }

    private static void deletePainting(MinecraftServer server, UUID id, String storedFileName) throws IOException {
        Path root = root(server);
        Path storedPath = storedFileName.isBlank() ? null : imagePath(root, storedFileName);
        Path metadataPath = root.resolve("metadata.json");
        JsonArray entries = readMetadata(metadataPath);
        JsonArray remaining = new JsonArray();
        boolean found = false;
        for (JsonElement element : entries) {
            if (element.isJsonObject() && id.toString().equals(stringValue(element.getAsJsonObject(), "id", ""))) {
                found = true;
                continue;
            }
            remaining.add(element.deepCopy());
        }
        if (!found) throw new IOException("Painting metadata not found");
        writeMetadata(metadataPath, remaining);
        if (storedPath != null) Files.deleteIfExists(storedPath);
    }

    private static Path root(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).resolve(JustPaintings.MOD_ID);
    }

    private static Path imagePath(Path root, String storedFileName) throws IOException {
        Path images = root.resolve("images").normalize();
        Path image = images.resolve(storedFileName).normalize();
        if (!images.equals(image.getParent())) throw new IOException("Invalid stored image path");
        return image;
    }

    private static void appendMetadata(Path metadataPath, UUID imageId, String storedFileName, UploadSession session, ServerPlayer player, int sourceWidth, int sourceHeight) throws IOException {
        JsonArray entries = Files.exists(metadataPath) ? readMetadata(metadataPath) : new JsonArray();
        JsonObject metadata = new JsonObject();
        metadata.addProperty("id", imageId.toString());
        metadata.addProperty("originalFileName", session.fileName);
        metadata.addProperty("storedFileName", storedFileName);
        metadata.addProperty("uploaderUuid", player.getUUID().toString());
        metadata.addProperty("uploaderName", player.getGameProfile().name());
        metadata.addProperty("paintingWidth", session.width);
        metadata.addProperty("paintingHeight", session.height);
        metadata.addProperty("sourceWidth", sourceWidth);
        metadata.addProperty("sourceHeight", sourceHeight);
        metadata.addProperty("uploadedAt", System.currentTimeMillis());
        entries.add(metadata);
        writeMetadata(metadataPath, entries);
    }

    private static JsonArray readMetadata(Path metadataPath) throws IOException {
        if (!Files.exists(metadataPath)) return new JsonArray();
        try (var reader = Files.newBufferedReader(metadataPath, StandardCharsets.UTF_8)) {
            JsonElement existing = JsonParser.parseReader(reader);
            if (!existing.isJsonArray()) throw new IOException("Invalid metadata format");
            return existing.getAsJsonArray();
        } catch (RuntimeException e) {
            throw new IOException("Invalid metadata format", e);
        }
    }

    private static void writeMetadata(Path metadataPath, JsonArray entries) throws IOException {
        Files.createDirectories(metadataPath.getParent());
        Path tempMetadataPath = metadataPath.resolveSibling(metadataPath.getFileName() + ".tmp");
        Files.writeString(tempMetadataPath, GSON.toJson(entries), StandardCharsets.UTF_8);
        moveAtomically(tempMetadataPath, metadataPath);
    }

    private static void moveAtomically(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static String sanitizeFileName(String fileName) {
        String normalized = fileName.replace('\\', '/');
        int slash = normalized.lastIndexOf('/');
        String baseName = (slash >= 0 ? normalized.substring(slash + 1) : normalized).strip();
        StringBuilder sanitized = new StringBuilder(baseName.length());
        baseName.codePoints().filter(cp -> !Character.isISOControl(cp)).forEach(sanitized::appendCodePoint);
        return sanitized.toString().strip();
    }

    private static String stringValue(JsonObject object, String key, String fallback) {
        JsonElement value = object.get(key);
        try {
            return value != null && value.isJsonPrimitive() ? value.getAsString() : fallback;
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    private static int intValue(JsonObject object, String key, int fallback) {
        JsonElement value = object.get(key);
        try {
            return value != null && value.isJsonPrimitive() ? value.getAsInt() : fallback;
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    public record StoredPainting(UUID id, String fileName, String displayFileName, String storedFileName, String uploaderUuid, String uploader, int width, int height, boolean stored) {
    }

    private record UploadKey(UUID playerId, UUID uploadId) {
    }

    private record UploadLimit(String translationKey, int maximum) {
    }

    private record DecodedImage(BufferedImage image, int sourceWidth, int sourceHeight) {
    }

    private static final class UploadSession {
        private final String fileName;
        private final int width;
        private final int height;
        private final int totalSize;
        private final ByteArrayOutputStream data;

        private UploadSession(String fileName, int width, int height, int totalSize) {
            this.fileName = fileName;
            this.width = width;
            this.height = height;
            this.totalSize = totalSize;
            this.data = new ByteArrayOutputStream(Math.min(totalSize, UploadPayloads.MAX_CHUNK_SIZE));
        }

        private boolean append(byte[] chunk) {
            if ((long) data.size() + chunk.length > totalSize) return false;
            data.writeBytes(chunk);
            return true;
        }
    }
}
