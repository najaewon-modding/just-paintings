package net.njw.justpaintings.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.njw.justpaintings.JustPaintings;
import net.njw.justpaintings.server.PaintingUploadManager;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class PaintingPayloads {
    public static final int MAX_IMAGE_CHUNK_SIZE = 24 * 1024;
    private static final int MAX_CHOICES = 4096;

    private PaintingPayloads() {
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("4");
        registrar.playToClient(PaintingChoicesPayload.TYPE, PaintingChoicesPayload.STREAM_CODEC);
        registrar.playToServer(SelectPaintingPayload.TYPE, SelectPaintingPayload.STREAM_CODEC, PaintingUploadManager::handleSelection);
        registrar.playToServer(DeletePaintingPayload.TYPE, DeletePaintingPayload.STREAM_CODEC, PaintingUploadManager::handleDeletion);
        registrar.playToServer(RequestImagePayload.TYPE, RequestImagePayload.STREAM_CODEC, PaintingUploadManager::handleImageRequest);
        registrar.playToClient(ImageStartPayload.TYPE, ImageStartPayload.STREAM_CODEC);
        registrar.playToClient(ImageChunkPayload.TYPE, ImageChunkPayload.STREAM_CODEC);
        registrar.playToClient(ImageFinishPayload.TYPE, ImageFinishPayload.STREAM_CODEC);
        registrar.playToClient(ImageRemovedPayload.TYPE, ImageRemovedPayload.STREAM_CODEC);
    }

    public record Choice(UUID id, String fileName, int width, int height, String uploader, boolean deletable) {
        private static void encode(RegistryFriendlyByteBuf buffer, Choice choice) {
            buffer.writeUUID(choice.id);
            buffer.writeUtf(choice.fileName, 255);
            buffer.writeVarInt(choice.width);
            buffer.writeVarInt(choice.height);
            buffer.writeUtf(choice.uploader, 255);
            buffer.writeBoolean(choice.deletable);
        }

        private static Choice decode(RegistryFriendlyByteBuf buffer) {
            return new Choice(buffer.readUUID(), buffer.readUtf(255), buffer.readVarInt(), buffer.readVarInt(), buffer.readUtf(255), buffer.readBoolean());
        }
    }

    public record PaintingChoicesPayload(int hand, boolean selectable, List<Choice> choices) implements CustomPacketPayload {
        public static final Type<PaintingChoicesPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(JustPaintings.MOD_ID, "painting_choices"));
        public static final StreamCodec<RegistryFriendlyByteBuf, PaintingChoicesPayload> STREAM_CODEC = StreamCodec.of(PaintingChoicesPayload::encode, PaintingChoicesPayload::decode);

        private static void encode(RegistryFriendlyByteBuf buffer, PaintingChoicesPayload payload) {
            buffer.writeByte(payload.hand);
            buffer.writeBoolean(payload.selectable);
            buffer.writeVarInt(payload.choices.size());
            for (Choice choice : payload.choices) Choice.encode(buffer, choice);
        }

        private static PaintingChoicesPayload decode(RegistryFriendlyByteBuf buffer) {
            int hand = buffer.readUnsignedByte();
            boolean selectable = buffer.readBoolean();
            int size = buffer.readVarInt();
            if (size < 0 || size > MAX_CHOICES) throw new IllegalArgumentException("Invalid painting choice count: " + size);
            List<Choice> choices = new ArrayList<>(size);
            for (int i = 0; i < size; i++) choices.add(Choice.decode(buffer));
            return new PaintingChoicesPayload(hand, selectable, List.copyOf(choices));
        }

        @Override
        public Type<PaintingChoicesPayload> type() {
            return TYPE;
        }
    }

    public record SelectPaintingPayload(int hand, UUID imageId) implements CustomPacketPayload {
        public static final Type<SelectPaintingPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(JustPaintings.MOD_ID, "select_painting"));
        public static final StreamCodec<RegistryFriendlyByteBuf, SelectPaintingPayload> STREAM_CODEC = StreamCodec.of((buffer, payload) -> {
            buffer.writeByte(payload.hand);
            buffer.writeUUID(payload.imageId);
        }, buffer -> new SelectPaintingPayload(buffer.readUnsignedByte(), buffer.readUUID()));

        @Override
        public Type<SelectPaintingPayload> type() {
            return TYPE;
        }
    }

    public record DeletePaintingPayload(int hand, boolean selectable, UUID imageId) implements CustomPacketPayload {
        public static final Type<DeletePaintingPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(JustPaintings.MOD_ID, "delete_painting"));
        public static final StreamCodec<RegistryFriendlyByteBuf, DeletePaintingPayload> STREAM_CODEC = StreamCodec.of((buffer, payload) -> {
            buffer.writeByte(payload.hand);
            buffer.writeBoolean(payload.selectable);
            buffer.writeUUID(payload.imageId);
        }, buffer -> new DeletePaintingPayload(buffer.readUnsignedByte(), buffer.readBoolean(), buffer.readUUID()));

        @Override
        public Type<DeletePaintingPayload> type() {
            return TYPE;
        }
    }

    public record RequestImagePayload(UUID imageId) implements CustomPacketPayload {
        public static final Type<RequestImagePayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(JustPaintings.MOD_ID, "request_image"));
        public static final StreamCodec<RegistryFriendlyByteBuf, RequestImagePayload> STREAM_CODEC = StreamCodec.of((buffer, payload) -> buffer.writeUUID(payload.imageId), buffer -> new RequestImagePayload(buffer.readUUID()));

        @Override
        public Type<RequestImagePayload> type() {
            return TYPE;
        }
    }

    public record ImageStartPayload(UUID imageId, int totalSize) implements CustomPacketPayload {
        public static final Type<ImageStartPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(JustPaintings.MOD_ID, "image_start"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ImageStartPayload> STREAM_CODEC = StreamCodec.of((buffer, payload) -> {
            buffer.writeUUID(payload.imageId);
            buffer.writeVarInt(payload.totalSize);
        }, buffer -> new ImageStartPayload(buffer.readUUID(), buffer.readVarInt()));

        @Override
        public Type<ImageStartPayload> type() {
            return TYPE;
        }
    }

    public record ImageChunkPayload(UUID imageId, byte[] data) implements CustomPacketPayload {
        public static final Type<ImageChunkPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(JustPaintings.MOD_ID, "image_chunk"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ImageChunkPayload> STREAM_CODEC = StreamCodec.of((buffer, payload) -> {
            buffer.writeUUID(payload.imageId);
            buffer.writeByteArray(payload.data);
        }, buffer -> new ImageChunkPayload(buffer.readUUID(), buffer.readByteArray(MAX_IMAGE_CHUNK_SIZE)));

        @Override
        public Type<ImageChunkPayload> type() {
            return TYPE;
        }
    }

    public record ImageFinishPayload(UUID imageId) implements CustomPacketPayload {
        public static final Type<ImageFinishPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(JustPaintings.MOD_ID, "image_finish"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ImageFinishPayload> STREAM_CODEC = StreamCodec.of((buffer, payload) -> buffer.writeUUID(payload.imageId), buffer -> new ImageFinishPayload(buffer.readUUID()));

        @Override
        public Type<ImageFinishPayload> type() {
            return TYPE;
        }
    }

    public record ImageRemovedPayload(UUID imageId) implements CustomPacketPayload {
        public static final Type<ImageRemovedPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(JustPaintings.MOD_ID, "image_removed"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ImageRemovedPayload> STREAM_CODEC = StreamCodec.of((buffer, payload) -> buffer.writeUUID(payload.imageId), buffer -> new ImageRemovedPayload(buffer.readUUID()));

        @Override
        public Type<ImageRemovedPayload> type() {
            return TYPE;
        }
    }
}
