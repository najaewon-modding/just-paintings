package net.njw.justpaintings.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.njw.justpaintings.JustPaintings;
import net.njw.justpaintings.server.PaintingUploadManager;

import java.util.UUID;

public final class UploadPayloads {
    public static final int MAX_CHUNK_SIZE = 24 * 1024;
    public static final int MAX_UPLOAD_SIZE = 16 * 1024 * 1024;

    private UploadPayloads() {
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToServer(UploadStartPayload.TYPE, UploadStartPayload.STREAM_CODEC, PaintingUploadManager::handleStart);
        registrar.playToServer(UploadChunkPayload.TYPE, UploadChunkPayload.STREAM_CODEC, PaintingUploadManager::handleChunk);
        registrar.playToServer(UploadFinishPayload.TYPE, UploadFinishPayload.STREAM_CODEC, PaintingUploadManager::handleFinish);
    }

    public record UploadStartPayload(UUID uploadId, String fileName, int width, int height, int totalSize) implements CustomPacketPayload {
        public static final Type<UploadStartPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(JustPaintings.MOD_ID, "upload_start"));
        public static final StreamCodec<RegistryFriendlyByteBuf, UploadStartPayload> STREAM_CODEC = StreamCodec.of(UploadStartPayload::encode, UploadStartPayload::decode);

        private static void encode(RegistryFriendlyByteBuf buffer, UploadStartPayload payload) {
            buffer.writeUUID(payload.uploadId);
            buffer.writeUtf(payload.fileName, 255);
            buffer.writeVarInt(payload.width);
            buffer.writeVarInt(payload.height);
            buffer.writeVarInt(payload.totalSize);
        }

        private static UploadStartPayload decode(RegistryFriendlyByteBuf buffer) {
            return new UploadStartPayload(buffer.readUUID(), buffer.readUtf(255), buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt());
        }

        @Override
        public Type<UploadStartPayload> type() {
            return TYPE;
        }
    }

    public record UploadChunkPayload(UUID uploadId, byte[] data) implements CustomPacketPayload {
        public static final Type<UploadChunkPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(JustPaintings.MOD_ID, "upload_chunk"));
        public static final StreamCodec<RegistryFriendlyByteBuf, UploadChunkPayload> STREAM_CODEC = StreamCodec.of(UploadChunkPayload::encode, UploadChunkPayload::decode);

        private static void encode(RegistryFriendlyByteBuf buffer, UploadChunkPayload payload) {
            buffer.writeUUID(payload.uploadId);
            buffer.writeByteArray(payload.data);
        }

        private static UploadChunkPayload decode(RegistryFriendlyByteBuf buffer) {
            return new UploadChunkPayload(buffer.readUUID(), buffer.readByteArray(MAX_CHUNK_SIZE));
        }

        @Override
        public Type<UploadChunkPayload> type() {
            return TYPE;
        }
    }

    public record UploadFinishPayload(UUID uploadId) implements CustomPacketPayload {
        public static final Type<UploadFinishPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(JustPaintings.MOD_ID, "upload_finish"));
        public static final StreamCodec<RegistryFriendlyByteBuf, UploadFinishPayload> STREAM_CODEC = StreamCodec.of(UploadFinishPayload::encode, UploadFinishPayload::decode);

        private static void encode(RegistryFriendlyByteBuf buffer, UploadFinishPayload payload) {
            buffer.writeUUID(payload.uploadId);
        }

        private static UploadFinishPayload decode(RegistryFriendlyByteBuf buffer) {
            return new UploadFinishPayload(buffer.readUUID());
        }

        @Override
        public Type<UploadFinishPayload> type() {
            return TYPE;
        }
    }
}
