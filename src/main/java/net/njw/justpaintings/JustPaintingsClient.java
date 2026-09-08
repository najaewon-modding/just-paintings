package net.njw.justpaintings;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.njw.justpaintings.client.ClientPaintingTextures;
import net.njw.justpaintings.client.CustomPaintingRenderer;
import net.njw.justpaintings.client.PaintingSelectionScreen;
import net.njw.justpaintings.network.PaintingPayloads;
import net.njw.justpaintings.network.UploadPayloads;
import net.njw.justpaintings.registry.ModContent;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.UUID;

@Mod(value = JustPaintings.MOD_ID, dist = Dist.CLIENT)
public final class JustPaintingsClient {
    public JustPaintingsClient(IEventBus modEventBus) {
        NeoForge.EVENT_BUS.addListener(RegisterClientCommandsEvent.class, JustPaintingsClient::registerCommands);
        modEventBus.addListener(JustPaintingsClient::registerPayloadHandlers);
        modEventBus.addListener(JustPaintingsClient::registerRenderers);
    }

    private static void registerCommands(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("paintings")
                .then(Commands.literal("upload")
                        .then(Commands.argument("height", IntegerArgumentType.integer(1, 3))
                                .then(Commands.argument("width", IntegerArgumentType.integer(1, 3))
                                        .executes(context -> openFilePicker(
                                                IntegerArgumentType.getInteger(context, "height"),
                                                IntegerArgumentType.getInteger(context, "width")))))));
    }

    private static void registerPayloadHandlers(RegisterClientPayloadHandlersEvent event) {
        event.register(PaintingPayloads.PaintingChoicesPayload.TYPE, (payload, context) -> Minecraft.getInstance().setScreen(new PaintingSelectionScreen(payload.hand(), payload.choices())));
        event.register(PaintingPayloads.ImageStartPayload.TYPE, (payload, context) -> ClientPaintingTextures.start(payload));
        event.register(PaintingPayloads.ImageChunkPayload.TYPE, (payload, context) -> ClientPaintingTextures.chunk(payload));
        event.register(PaintingPayloads.ImageFinishPayload.TYPE, (payload, context) -> ClientPaintingTextures.finish(payload));
    }

    private static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModContent.CUSTOM_PAINTING_ENTITY.get(), CustomPaintingRenderer::new);
    }

    private static int openFilePicker(int height, int width) {
        String selected = TinyFileDialogs.tinyfd_openFileDialog(Component.translatable("dialog.njw_just_paintings.upload.title").getString(), null, null, null, false);
        if (selected == null) return 1;
        Path path = Path.of(selected);
        Thread.startVirtualThread(() -> prepareUpload(path, width, height));
        return 1;
    }

    private static void prepareUpload(Path path, int width, int height) {
        try {
            if (!Files.isRegularFile(path)) {
                showMessage(Component.translatable("message.njw_just_paintings.upload.file_unreadable"));
                return;
            }
            long fileSize = Files.size(path);
            if (fileSize <= 0 || fileSize > UploadPayloads.MAX_UPLOAD_SIZE) {
                showMessage(Component.translatable("message.njw_just_paintings.upload.file_too_large", UploadPayloads.MAX_UPLOAD_SIZE / 1024 / 1024));
                return;
            }
            byte[] data = Files.readAllBytes(path);
            String fileName = path.getFileName().toString();
            Minecraft.getInstance().execute(() -> sendUpload(fileName, width, height, data));
        } catch (IOException e) {
            showMessage(Component.translatable("message.njw_just_paintings.upload.prepare_failed"));
        }
    }

    private static void sendUpload(String fileName, int width, int height, byte[] data) {
        UUID uploadId = UUID.randomUUID();
        ClientPacketDistributor.sendToServer(new UploadPayloads.UploadStartPayload(uploadId, fileName, width, height, data.length));
        for (int offset = 0; offset < data.length; offset += UploadPayloads.MAX_CHUNK_SIZE) {
            byte[] chunk = Arrays.copyOfRange(data, offset, Math.min(offset + UploadPayloads.MAX_CHUNK_SIZE, data.length));
            ClientPacketDistributor.sendToServer(new UploadPayloads.UploadChunkPayload(uploadId, chunk));
        }
        ClientPacketDistributor.sendToServer(new UploadPayloads.UploadFinishPayload(uploadId));
    }

    private static void showMessage(Component message) {
        Minecraft.getInstance().execute(() -> {
            if (Minecraft.getInstance().player != null) Minecraft.getInstance().player.sendSystemMessage(message);
        });
    }
}
