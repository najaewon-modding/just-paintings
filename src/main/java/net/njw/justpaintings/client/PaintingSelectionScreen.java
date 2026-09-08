package net.njw.justpaintings.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.njw.justpaintings.network.PaintingPayloads;

import java.util.List;

public final class PaintingSelectionScreen extends Screen {
    private static final int PAGE_SIZE = 6;
    private static final int ROW_HEIGHT = 24;
    private static final int ROW_GAP = 6;
    private final int hand;
    private final List<PaintingPayloads.Choice> choices;
    private int page;

    public PaintingSelectionScreen(int hand, List<PaintingPayloads.Choice> choices) {
        super(Component.translatable("screen.njw_just_paintings.selection.title"));
        this.hand = hand;
        this.choices = List.copyOf(choices);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0x66000000);
        int panelWidth = Math.min(380, width - 40);
        int x = (width - panelWidth) / 2;
        int y = Math.max(38, height / 2 - 105);
        graphics.text(font, title, width / 2 - font.width(title) / 2, y - 24, 0xFFFFFFFF, true);
        int start = page * PAGE_SIZE;
        int end = Math.min(start + PAGE_SIZE, choices.size());
        for (int i = start; i < end; i++) {
            int rowY = y + (i - start) * (ROW_HEIGHT + ROW_GAP);
            boolean hovered = inside(mouseX, mouseY, x, rowY, panelWidth, ROW_HEIGHT);
            graphics.fill(x, rowY, x + panelWidth, rowY + ROW_HEIGHT, hovered ? 0x885A5A5A : 0x66000000);
            PaintingPayloads.Choice choice = choices.get(i);
            Component label = Component.translatable("screen.njw_just_paintings.selection.entry", choice.fileName(), choice.width(), choice.height(), choice.uploader());
            graphics.text(font, label, x + 8, rowY + (ROW_HEIGHT - font.lineHeight) / 2, 0xFFFFFFFF, false);
        }
        int controlsY = y + PAGE_SIZE * (ROW_HEIGHT + ROW_GAP) + 4;
        if (page > 0) drawControl(graphics, mouseX, mouseY, x, controlsY, 90, Component.translatable("screen.njw_just_paintings.selection.previous"));
        if (end < choices.size()) drawControl(graphics, mouseX, mouseY, x + panelWidth - 90, controlsY, 90, Component.translatable("screen.njw_just_paintings.selection.next"));
        drawControl(graphics, mouseX, mouseY, width / 2 - 45, controlsY + 30, 90, Component.translatable("gui.cancel"));
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() != 0) return super.mouseClicked(event, doubleClick);
        int mouseX = (int) event.x();
        int mouseY = (int) event.y();
        int panelWidth = Math.min(380, width - 40);
        int x = (width - panelWidth) / 2;
        int y = Math.max(38, height / 2 - 105);
        int start = page * PAGE_SIZE;
        int end = Math.min(start + PAGE_SIZE, choices.size());
        for (int i = start; i < end; i++) {
            int rowY = y + (i - start) * (ROW_HEIGHT + ROW_GAP);
            if (inside(mouseX, mouseY, x, rowY, panelWidth, ROW_HEIGHT)) {
                select(choices.get(i));
                return true;
            }
        }
        int controlsY = y + PAGE_SIZE * (ROW_HEIGHT + ROW_GAP) + 4;
        if (page > 0 && inside(mouseX, mouseY, x, controlsY, 90, ROW_HEIGHT)) {
            page--;
            return true;
        }
        if (end < choices.size() && inside(mouseX, mouseY, x + panelWidth - 90, controlsY, 90, ROW_HEIGHT)) {
            page++;
            return true;
        }
        if (inside(mouseX, mouseY, width / 2 - 45, controlsY + 30, 90, ROW_HEIGHT)) {
            onClose();
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    private void drawControl(GuiGraphicsExtractor graphics, int mouseX, int mouseY, int x, int y, int controlWidth, Component label) {
        boolean hovered = inside(mouseX, mouseY, x, y, controlWidth, ROW_HEIGHT);
        graphics.fill(x, y, x + controlWidth, y + ROW_HEIGHT, hovered ? 0x885A5A5A : 0x66000000);
        graphics.text(font, label, x + (controlWidth - font.width(label)) / 2, y + (ROW_HEIGHT - font.lineHeight) / 2, 0xFFFFFFFF, false);
    }

    private void select(PaintingPayloads.Choice choice) {
        ClientPacketDistributor.sendToServer(new PaintingPayloads.SelectPaintingPayload(hand, choice.id()));
        Minecraft.getInstance().setScreen(null);
    }

    private static boolean inside(int mouseX, int mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
