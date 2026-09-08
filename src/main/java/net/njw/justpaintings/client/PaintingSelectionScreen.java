package net.njw.justpaintings.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.njw.justpaintings.network.PaintingPayloads;

import java.util.List;

public final class PaintingSelectionScreen extends Screen {
    private static final int PAGE_SIZE = 6;
    private final int hand;
    private final List<PaintingPayloads.Choice> choices;
    private int page;

    public PaintingSelectionScreen(int hand, List<PaintingPayloads.Choice> choices) {
        super(Component.translatable("screen.njw_just_paintings.selection.title"));
        this.hand = hand;
        this.choices = List.copyOf(choices);
    }

    @Override
    protected void init() {
        int start = page * PAGE_SIZE;
        int end = Math.min(start + PAGE_SIZE, choices.size());
        int buttonWidth = Math.min(360, width - 40);
        int x = (width - buttonWidth) / 2;
        int y = Math.max(35, height / 2 - 90);
        for (int i = start; i < end; i++) {
            PaintingPayloads.Choice choice = choices.get(i);
            Component label = Component.translatable("screen.njw_just_paintings.selection.entry", choice.fileName(), choice.width(), choice.height(), choice.uploader());
            addRenderableWidget(Button.builder(label, button -> select(choice)).bounds(x, y + (i - start) * 24, buttonWidth, 20).build());
        }
        if (page > 0) addRenderableWidget(Button.builder(Component.translatable("screen.njw_just_paintings.selection.previous"), button -> changePage(-1)).bounds(x, y + 150, 100, 20).build());
        if (end < choices.size()) addRenderableWidget(Button.builder(Component.translatable("screen.njw_just_paintings.selection.next"), button -> changePage(1)).bounds(x + buttonWidth - 100, y + 150, 100, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), button -> onClose()).bounds((width - 100) / 2, y + 176, 100, 20).build());
    }

    private void select(PaintingPayloads.Choice choice) {
        ClientPacketDistributor.sendToServer(new PaintingPayloads.SelectPaintingPayload(hand, choice.id()));
        Minecraft.getInstance().setScreen(null);
    }

    private void changePage(int delta) {
        page += delta;
        clearWidgets();
        init();
    }
}
