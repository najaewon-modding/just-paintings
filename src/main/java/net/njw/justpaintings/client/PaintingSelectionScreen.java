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
    private static final int VISIBLE_ROWS = 6;
    private static final int ROW_HEIGHT = 24;
    private static final int ROW_GAP = 6;
    private static final int DELETE_WIDTH = 48;
    private final int hand;
    private final List<PaintingPayloads.Choice> choices;
    private int firstVisible;

    public PaintingSelectionScreen(int hand, List<PaintingPayloads.Choice> choices) {
        super(Component.translatable("screen.njw_just_paintings.selection.title"));
        this.hand = hand;
        this.choices = List.copyOf(choices);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0x66000000);
        int panelWidth = Math.min(420, width - 40);
        int x = (width - panelWidth) / 2;
        int y = Math.max(38, height / 2 - 105);
        graphics.text(font, title, width / 2 - font.width(title) / 2, y - 23, 0xFFFFFFFF, true);
        int end = Math.min(firstVisible + VISIBLE_ROWS, choices.size());
        for (int i = firstVisible; i < end; i++) {
            int rowY = y + (i - firstVisible) * (ROW_HEIGHT + ROW_GAP);
            PaintingPayloads.Choice choice = choices.get(i);
            int deleteX = x + panelWidth - DELETE_WIDTH;
            boolean deleteHovered = choice.deletable() && inside(mouseX, mouseY, deleteX, rowY, DELETE_WIDTH, ROW_HEIGHT);
            boolean rowHovered = inside(mouseX, mouseY, x, rowY, panelWidth, ROW_HEIGHT) && !deleteHovered;
            graphics.fill(x, rowY, x + panelWidth, rowY + ROW_HEIGHT, rowHovered ? 0x885A5A5A : 0x66000000);
            int textRight = choice.deletable() ? deleteX - 6 : x + panelWidth - 8;
            Component label = Component.translatable("screen.njw_just_paintings.selection.entry", choice.fileName(), choice.width(), choice.height(), choice.uploader());
            graphics.text(font, trim(label, textRight - (x + 8)), x + 8, rowY + (ROW_HEIGHT - font.lineHeight) / 2 + 1, 0xFFFFFFFF, false);
            if (choice.deletable()) {
                graphics.fill(deleteX, rowY, x + panelWidth, rowY + ROW_HEIGHT, deleteHovered ? 0xAA7A3030 : 0x884A2020);
                Component delete = Component.translatable("screen.njw_just_paintings.selection.delete");
                graphics.text(font, delete, deleteX + (DELETE_WIDTH - font.width(delete)) / 2, rowY + (ROW_HEIGHT - font.lineHeight) / 2 + 1, 0xFFFFFFFF, false);
            }
        }
        if (choices.size() > VISIBLE_ROWS) drawScrollBar(graphics, x + panelWidth + 4, y);
        int controlsY = y + VISIBLE_ROWS * (ROW_HEIGHT + ROW_GAP) + 4;
        drawControl(graphics, mouseX, mouseY, width / 2 - 45, controlsY, 90, Component.translatable("gui.cancel"));
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() != 0) return super.mouseClicked(event, doubleClick);
        int mouseX = (int) event.x();
        int mouseY = (int) event.y();
        int panelWidth = Math.min(420, width - 40);
        int x = (width - panelWidth) / 2;
        int y = Math.max(38, height / 2 - 105);
        int end = Math.min(firstVisible + VISIBLE_ROWS, choices.size());
        for (int i = firstVisible; i < end; i++) {
            int rowY = y + (i - firstVisible) * (ROW_HEIGHT + ROW_GAP);
            PaintingPayloads.Choice choice = choices.get(i);
            int deleteX = x + panelWidth - DELETE_WIDTH;
            if (choice.deletable() && inside(mouseX, mouseY, deleteX, rowY, DELETE_WIDTH, ROW_HEIGHT)) {
                ClientPacketDistributor.sendToServer(new PaintingPayloads.DeletePaintingPayload(hand, choice.id()));
                return true;
            }
            if (inside(mouseX, mouseY, x, rowY, panelWidth, ROW_HEIGHT)) {
                select(choice);
                return true;
            }
        }
        int controlsY = y + VISIBLE_ROWS * (ROW_HEIGHT + ROW_GAP) + 4;
        if (inside(mouseX, mouseY, width / 2 - 45, controlsY, 90, ROW_HEIGHT)) {
            onClose();
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int panelWidth = Math.min(420, width - 40);
        int x = (width - panelWidth) / 2;
        int y = Math.max(38, height / 2 - 105);
        int listHeight = VISIBLE_ROWS * (ROW_HEIGHT + ROW_GAP) - ROW_GAP;
        if (!inside((int) mouseX, (int) mouseY, x, y, panelWidth, listHeight)) return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        int maxFirst = Math.max(0, choices.size() - VISIBLE_ROWS);
        if (scrollY > 0.0) firstVisible = Math.max(0, firstVisible - 1);
        else if (scrollY < 0.0) firstVisible = Math.min(maxFirst, firstVisible + 1);
        return true;
    }

    private Component trim(Component text, int maxWidth) {
        if (font.width(text) <= maxWidth) return text;
        String value = text.getString();
        String ellipsis = "...";
        int ellipsisWidth = font.width(ellipsis);
        while (!value.isEmpty() && font.width(value) + ellipsisWidth > maxWidth) value = value.substring(0, value.length() - 1);
        return Component.literal(value + ellipsis);
    }

    private void drawScrollBar(GuiGraphicsExtractor graphics, int x, int y) {
        int trackHeight = VISIBLE_ROWS * (ROW_HEIGHT + ROW_GAP) - ROW_GAP;
        int thumbHeight = Math.max(18, trackHeight * VISIBLE_ROWS / choices.size());
        int maxFirst = choices.size() - VISIBLE_ROWS;
        int thumbY = y + (trackHeight - thumbHeight) * firstVisible / maxFirst;
        graphics.fill(x, y, x + 3, y + trackHeight, 0x44000000);
        graphics.fill(x, thumbY, x + 3, thumbY + thumbHeight, 0xAAFFFFFF);
    }

    private void drawControl(GuiGraphicsExtractor graphics, int mouseX, int mouseY, int x, int y, int controlWidth, Component label) {
        boolean hovered = inside(mouseX, mouseY, x, y, controlWidth, ROW_HEIGHT);
        graphics.fill(x, y, x + controlWidth, y + ROW_HEIGHT, hovered ? 0x885A5A5A : 0x66000000);
        graphics.text(font, label, x + (controlWidth - font.width(label)) / 2, y + (ROW_HEIGHT - font.lineHeight) / 2 + 1, 0xFFFFFFFF, false);
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
