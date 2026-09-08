package com.najaewon.njw_just_paintings;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.commands.Commands;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

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
                                        .executes(context -> openFilePicker())))));
    }

    private static int openFilePicker() {
        TinyFileDialogs.tinyfd_openFileDialog("Select painting image", null, null, null, false);
        return 1;
    }
}
