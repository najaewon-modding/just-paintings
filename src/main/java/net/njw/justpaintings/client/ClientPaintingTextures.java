package net.njw.justpaintings.client;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.njw.justpaintings.JustPaintings;
import net.njw.justpaintings.network.PaintingPayloads;
import org.jspecify.annotations.Nullable;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class ClientPaintingTextures {
    private static final Map<UUID, Identifier> TEXTURES = new HashMap<>();
    private static final Map<UUID, Download> DOWNLOADS = new HashMap<>();
    private static final Set<UUID> REQUESTED = new HashSet<>();
    private static final Set<UUID> MISSING = new HashSet<>();

    private ClientPaintingTextures() {
    }

    public static @Nullable Identifier getOrRequest(UUID imageId) {
        Identifier texture = TEXTURES.get(imageId);
        if (texture != null) return texture;
        if (MISSING.contains(imageId)) return null;
        if ((imageId.getMostSignificantBits() != 0L || imageId.getLeastSignificantBits() != 0L) && REQUESTED.add(imageId)) {
            ClientPacketDistributor.sendToServer(new PaintingPayloads.RequestImagePayload(imageId));
        }
        return null;
    }

    public static void start(PaintingPayloads.ImageStartPayload payload) {
        if (MISSING.contains(payload.imageId())) return;
        if (payload.totalSize() < 1 || payload.totalSize() > PaintingPayloads.MAX_IMAGE_SIZE) {
            DOWNLOADS.remove(payload.imageId());
            REQUESTED.remove(payload.imageId());
            return;
        }
        DOWNLOADS.put(payload.imageId(), new Download(payload.totalSize()));
    }

    public static void chunk(PaintingPayloads.ImageChunkPayload payload) {
        Download download = DOWNLOADS.get(payload.imageId());
        if (download == null || !download.append(payload.data())) DOWNLOADS.remove(payload.imageId());
    }

    public static void finish(PaintingPayloads.ImageFinishPayload payload) {
        if (MISSING.contains(payload.imageId())) {
            DOWNLOADS.remove(payload.imageId());
            return;
        }
        Download download = DOWNLOADS.remove(payload.imageId());
        if (download == null || download.data.size() != download.totalSize) {
            REQUESTED.remove(payload.imageId());
            return;
        }
        try {
            NativeImage image = NativeImage.read(download.data.toByteArray());
            Identifier id = Identifier.fromNamespaceAndPath(JustPaintings.MOD_ID, "dynamic/" + payload.imageId());
            DynamicTexture texture = new DynamicTexture(() -> "Just Paintings " + payload.imageId(), image);
            Minecraft.getInstance().getTextureManager().register(id, texture);
            texture.upload();
            TEXTURES.put(payload.imageId(), id);
        } catch (IOException e) {
            REQUESTED.remove(payload.imageId());
        }
    }

    public static void remove(PaintingPayloads.ImageRemovedPayload payload) {
        DOWNLOADS.remove(payload.imageId());
        REQUESTED.remove(payload.imageId());
        MISSING.add(payload.imageId());
        Identifier texture = TEXTURES.remove(payload.imageId());
        if (texture != null) Minecraft.getInstance().getTextureManager().release(texture);
    }

    private static final class Download {
        private final int totalSize;
        private final ByteArrayOutputStream data;

        private Download(int totalSize) {
            this.totalSize = totalSize;
            this.data = new ByteArrayOutputStream(Math.min(totalSize, PaintingPayloads.MAX_IMAGE_CHUNK_SIZE));
        }

        private boolean append(byte[] bytes) {
            if ((long) data.size() + bytes.length > totalSize) return false;
            data.writeBytes(bytes);
            return true;
        }
    }
}
