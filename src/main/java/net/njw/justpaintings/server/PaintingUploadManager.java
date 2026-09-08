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
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.njw.justpaintings.JustPaintings;
import net.njw.justpaintings.network.UploadPayloads;

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
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class PaintingUploadManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Map<UploadKey, UploadSession> SESSIONS = new HashMap<>();

    private PaintingUploadManager() {
    }

    public static void handleStart(UploadPayloads.UploadStartPayload payload, IPayloadContext context) {
        ServerPlayer player = (ServerPlayer) context.player();
        if (payload.width() < 1 || payload.height() < 1 || payload.totalSize() < 1 || payload.totalSize() > UploadPayloads.MAX_UPLOAD_SIZE) {
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
            save(player, session);
            player.sendSystemMessage(Component.translatable("message.njw_just_paintings.upload.success", session.fileName));
        } catch (Exception e) {
            player.sendSystemMessage(Component.translatable("message.njw_just_paintings.upload.failed"));
        }
    }

    public static int list(ServerPlayer player) {
        try {
            MinecraftServer server = player.level().getServer();
            Path root = server.getWorldPath(LevelResource.ROOT).resolve(JustPaintings.MOD_ID);
            Path metadataPath = root.resolve("metadata.json");
            if (!Files.exists(metadataPath)) {
                player.sendSystemMessage(Component.translatable("command.njw_just_paintings.list.empty"));
                return 0;
            }
            JsonArray entries;
            try (var reader = Files.newBufferedReader(metadataPath, StandardCharsets.UTF_8)) {
                JsonElement existing = JsonParser.parseReader(reader);
                if (!existing.isJsonArray()) throw new IOException("Invalid metadata format");
                entries = existing.getAsJsonArray();
            }
            if (entries.isEmpty()) {
                player.sendSystemMessage(Component.translatable("command.njw_just_paintings.list.empty"));
                return 0;
            }
            player.sendSystemMessage(Component.translatable("command.njw_just_paintings.list.header", entries.size()));
            for (JsonElement element : entries) {
                if (!element.isJsonObject()) continue;
                JsonObject metadata = element.getAsJsonObject();
                String originalFileName = stringValue(metadata, "originalFileName", "?");
                String storedFileName = stringValue(metadata, "storedFileName", "");
                String uploaderName = stringValue(metadata, "uploaderName", "?");
                int width = intValue(metadata, "paintingWidth", 0);
                int height = intValue(metadata, "paintingHeight", 0);
                boolean stored = !storedFileName.isBlank() && Files.isRegularFile(root.resolve("images").resolve(storedFileName));
                Component status = Component.translatable(stored ? "command.njw_just_paintings.list.status.stored" : "command.njw_just_paintings.list.status.missing");
                player.sendSystemMessage(Component.translatable("command.njw_just_paintings.list.entry", originalFileName, width, height, uploaderName, status));
            }
            return entries.size();
        } catch (Exception e) {
            player.sendSystemMessage(Component.translatable("command.njw_just_paintings.list.failed"));
            return 0;
        }
    }

    private static void save(ServerPlayer player, UploadSession session) throws IOException {
        byte[] bytes = session.data.toByteArray();
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
        if (image == null) throw new IOException("Unsupported image file");
        MinecraftServer server = player.level().getServer();
        Path root = server.getWorldPath(LevelResource.ROOT).resolve(JustPaintings.MOD_ID);
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
        } catch (Exception e) {
            Files.deleteIfExists(imagePath);
            if (e instanceof IOException ioException) throw ioException;
            throw new IOException("Could not save metadata", e);
        }
    }

    private static void appendMetadata(Path metadataPath, UUID imageId, String storedFileName, UploadSession session, ServerPlayer player, int sourceWidth, int sourceHeight) throws IOException {
        JsonArray entries = new JsonArray();
        if (Files.exists(metadataPath)) {
            try (var reader = Files.newBufferedReader(metadataPath, StandardCharsets.UTF_8)) {
                JsonElement existing = JsonParser.parseReader(reader);
                if (!existing.isJsonArray()) throw new IOException("Invalid metadata format");
                entries = existing.getAsJsonArray();
            }
        }
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
