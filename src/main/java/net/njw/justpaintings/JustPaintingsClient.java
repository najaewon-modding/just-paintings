package net.njw.justpaintings;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.common.NeoForge;
import net.njw.justpaintings.network.UploadPayloads;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.UUID;

@Mod(value = JustPaintings.MOD_ID, dist = Dist.CLIENT)
public final class JustPaintingsClient {
    public JustPaintingsClient() {
        NeoForge.EVENT_BUS.addListener(RegisterClientCommandsEvent.class, JustPaintingsClient::registerCommands);
    }

    private static void registerCommands(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("paintings")
                .then(Commands.literal("upload")
                        .then(Commands.argument("width", IntegerArgumentType.integer(1))
                                .then(Commands.argument("height", IntegerArgumentType.integer(1))
                                        .executes(context -> openFilePicker(
                                                IntegerArgumentType.getInteger(context, "width"),
                                                IntegerArgumentType.getInteger(context, "height")))))));
    }

    private static int openFilePicker(int width, int height) {
        String selected = TinyFileDialogs.tinyfd_openFileDialog("Select painting image", null, null, null, false);
        if (selected == null) return 1;
        Path path = Path.of(selected);
        Thread.startVirtualThread(() -> prepareUpload(path, width, height));
        return 1;
    }

    private static void prepareUpload(Path path, int width, int height) {
        try {
            if (!Files.isRegularFile(path)) throw new IllegalArgumentException("선택한 파일을 읽을 수 없습니다.");
            long fileSize = Files.size(path);
            if (fileSize <= 0 || fileSize > UploadPayloads.MAX_UPLOAD_SIZE) throw new IllegalArgumentException("이미지 파일은 16 MiB 이하여야 합니다.");
            byte[] data = Files.readAllBytes(path);
            String fileName = path.getFileName().toString();
            Minecraft.getInstance().execute(() -> sendUpload(fileName, width, height, data));
        } catch (Exception e) {
            showMessage("업로드 준비에 실패했습니다: " + e.getMessage());
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

    private static void showMessage(String message) {
        Minecraft.getInstance().execute(() -> {
            if (Minecraft.getInstance().player != null) Minecraft.getInstance().player.sendSystemMessage(Component.literal(message));
        });
    }
}
