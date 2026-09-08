package net.njw.justpaintings;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.njw.justpaintings.network.UploadPayloads;

@Mod(JustPaintings.MOD_ID)
public final class JustPaintings {
    public static final String MOD_ID = "njw_just_paintings";

    public JustPaintings(IEventBus modEventBus) {
        modEventBus.addListener(UploadPayloads::register);
    }
}
