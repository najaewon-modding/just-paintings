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
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class PaintingUploadManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Map<UploadKey, UploadSession> SESSIONS = new HashMap<>();

    private PaintingUploadManager() {
    }

    public static void handleStart(UploadPayloads.UploadStartPayload payload, IPayloadContext context) {
        ServerPlayer player = (ServerPlayer) context.player();
        if (payload.width() < 1 || payload.width() > 3 || payload.height() < 1 || payload.height() > 3) {
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
            String displayFileName = save(player, session);
            player.sendSystemMessage(Component.translatable("message.njw_just_paintings.upload.success", displayFileName));
        } catch (Exception e) {
            player.sendSystemMessage(Component.translatable("message.njw_just_paintings.upload.failed"));
        }
    }

    public static void sendChoices(ServerPlayer player, InteractionHand hand) {
        try {
            List<StoredPainting> paintings = readPaintings(player.level().getServer());
            List<PaintingPayloads.Choice> choices = paintings.stream()
                    .filter(p -> p.stored() && p.width() >= 1 && p.width() <= 3 && p.height() >= 1 && p.height() <= 3)
                    .map(p -> new PaintingPayloads.Choice(p.id(), p.displayFileName(), p.width(), p.height(), p.uploader(), player.getUUID().toString().equals(p.uploaderUuid())))
                    .toList();
            PacketDistributor.sendToPlayer(player, new PaintingPayloads.PaintingChoicesPayload(hand == InteractionHand.MAIN_HAND ? 0 : 1, choices));
        } catch (IOException e) {
            player.sendSystemMessage(Component.translatable("message.njw_just_paintings.selection.failed"));
        }
    }

    public static void handleSelection(PaintingPayloads.SelectPaintingPayload payload, IPayloadContext context) {
        ServerPlayer player = (ServerPlayer) context.player();
        InteractionHand hand = payload.hand() == 0 ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
        ItemStack stack = player.getItemInHand(hand);
        if (stack.getItem() != ModContent.CUSTOM_PAINTING.get() || PaintingItemData.isSelected(stack)) return;
        try {
            StoredPainting painting = findPainting(player.level().getServer(), payload.imageId());
            if (painting == null || !painting.stored() || painting.width() < 1 || painting.width() > 3 || painting.height() < 1 || painting.height() > 3) {
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
        ServerPlayer player = (ServerPlayer) context.player();
        InteractionHand hand = payload.hand() == 0 ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
        try {
            StoredPainting painting = findPainting(player.level().getServer(), payload.imageId());
            if (painting == null || !player.getUUID().toString().equals(painting.uploaderUuid())) {
                player.sendSystemMessage(Component.translatable("message.njw_just_paintings.delete.denied"));
                sendChoices(player, hand);
                return;
            }
            deletePainting(player.level().getServer(), painting.id(), painting.storedFileName());
            player.sendSystemMessage(Component.translatable("message.njw_just_paintings.delete.success", painting.displayFileName()));
            sendChoices(player, hand);
        } catch (IOException e) {
            player.sendSystemMessage(Component.translatable("message.njw_just_paintings.delete.failed"));
            sendChoices(player, hand);
        }
    }

    public static void handleImageRequest(PaintingPayloads.RequestImagePayload payload, IPayloadContext context) {
        ServerPlayer player = (ServerPlayer) context.player();
        try {
            StoredPainting painting = findPainting(player.level().getServer(), payload.imageId());
            if (painting == null || !painting.stored()) return;
            Path imagePath = root(player.level().getServer()).resolve("images").resolve(painting.storedFileName());
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

    public static int list(ServerPlayer player) {
        try {
            List<StoredPainting> paintings = readPaintings(player.level().getServer());
            if (paintings.isEmpty()) {
                player.sendSystemMessage(Component.translatable("command.njw_just_paintings.list.empty"));
                return 0;
            }
            player.sendSystemMessage(Component.translatable("command.njw_just_paintings.list.header", paintings.size()));
            for (StoredPainting painting : paintings) {
                Component status = Component.translatable(painting.stored() ? "command.njw_just_paintings.list.status.stored" : "command.njw_just_paintings.list.status.missing");
                player.sendSystemMessage(Component.translatable("command.njw_just_paintings.list.entry", painting.displayFileName(), painting.width(), painting.height(), painting.uploader(), status));
            }
            return paintings.size();
        } catch (Exception e) {
            player.sendSystemMessage(Component.translatable("command.njw_just_paintings.list.failed"));
            return 0;
        }
    }

    private static String save(ServerPlayer player, UploadSession session) throws IOException {
        byte[] bytes = session.data.toByteArray();
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
        if (image == null) throw new IOException("Unsupported image file");
        Path root = root(player.level().getServer());
        Path images = root.resolve("images");
        Files.createDirectories(images);
        UUID imageId = UUID.randomUUID();
        String storedFileName = imageId + ".png";
        Path imagePath = images.resolve(storedFileName);
        Path tempImagePath = images.resolve(storedFileName + ".tmp");
        if (!ImageIO.write(image, "PNG", tempImagePath.toFile())) throw new IOException("Could not encode PNG");
        moveAtomically(tempImagePath, imagePath);
        try {
            appendMetadata(root.resolve("metadata.json"), imageId, storedFileName, session, player, image.getWidth(), image.getHeight());
            StoredPainting storedPainting = findPainting(player.level().getServer(), imageId);
            return storedPainting != null ? storedPainting.displayFileName() : session.fileName;
        } catch (Exception e) {
            Files.deleteIfExists(imagePath);
            if (e instanceof IOException ioException) throw ioException;
            throw new IOException("Could not save metadata", e);
        }
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
                boolean stored = !storedFileName.isBlank() && Files.isRegularFile(root.resolve("images").resolve(storedFileName));
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
        if (!storedFileName.isBlank()) Files.deleteIfExists(root.resolve("images").resolve(storedFileName));
    }

    private static Path root(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).resolve(JustPaintings.MOD_ID);
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
        return (slash >= 0 ? normalized.substring(slash + 1) : normalized).strip();
    }

    private static String stringValue(JsonObject object, String key, String fallback) {
        JsonElement value = object.get(key);
        return value != null && value.isJsonPrimitive() ? value.getAsString() : fallback;
    }

    private static int intValue(JsonObject object, String key, int fallback) {
        JsonElement value = object.get(key);
        return value != null && value.isJsonPrimitive() ? value.getAsInt() : fallback;
    }

    public record StoredPainting(UUID id, String fileName, String displayFileName, String storedFileName, String uploaderUuid, String uploader, int width, int height, boolean stored) {
    }

    private record UploadKey(UUID playerId, UUID uploadId) {
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
            this.data = new ByteArrayOutputStream(totalSize);
        }

        private boolean append(byte[] chunk) {
            if ((long) data.size() + chunk.length > totalSize) return false;
            data.writeBytes(chunk);
            return true;
        }
    }
}
