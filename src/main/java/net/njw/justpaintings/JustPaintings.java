package net.njw.justpaintings;

import net.minecraft.commands.Commands;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.njw.justpaintings.network.PaintingPayloads;
import net.njw.justpaintings.network.UploadPayloads;
import net.njw.justpaintings.registry.ModContent;
import net.njw.justpaintings.server.PaintingUploadManager;

@Mod(JustPaintings.MOD_ID)
public final class JustPaintings {
    public static final String MOD_ID = "njw_just_paintings";

    public JustPaintings(IEventBus modEventBus) {
        ModContent.ITEMS.register(modEventBus);
        ModContent.ENTITIES.register(modEventBus);
        modEventBus.addListener(UploadPayloads::register);
        modEventBus.addListener(PaintingPayloads::register);
        NeoForge.EVENT_BUS.addListener(JustPaintings::registerCommands);
    }

    private static void registerCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("paintings")
                .then(Commands.literal("list")
                        .executes(context -> PaintingUploadManager.openList(context.getSource().getPlayerOrException()))));
    }
}
