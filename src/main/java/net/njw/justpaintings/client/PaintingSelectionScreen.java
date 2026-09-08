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
    private static final int MAX_VISIBLE_ROWS = 6;
    private static final int ROW_HEIGHT = 24;
    private static final int ROW_GAP = 6;
    private static final int DELETE_WIDTH = 48;
    private static final int SIDE_MARGIN = 20;
    private static final int LIST_TOP = 52;
    private static final int CONTROL_BOTTOM_MARGIN = 12;
    private static final int CONTROL_GAP = 10;
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
        Layout layout = layout();
        graphics.text(font, title, width / 2 - font.width(title) / 2, Math.max(10, layout.y - 23), 0xFFFFFFFF, true);
        int end = Math.min(firstVisible + layout.visibleRows, choices.size());
        for (int i = firstVisible; i < end; i++) {
            int rowY = layout.y + (i - firstVisible) * (ROW_HEIGHT + ROW_GAP);
            PaintingPayloads.Choice choice = choices.get(i);
            int deleteX = layout.x + layout.panelWidth - DELETE_WIDTH;
            boolean deleteHovered = choice.deletable() && inside(mouseX, mouseY, deleteX, rowY, DELETE_WIDTH, ROW_HEIGHT);
            boolean rowHovered = inside(mouseX, mouseY, layout.x, rowY, layout.panelWidth, ROW_HEIGHT) && !deleteHovered;
            graphics.fill(layout.x, rowY, layout.x + layout.panelWidth, rowY + ROW_HEIGHT, rowHovered ? 0x885A5A5A : 0x66000000);
            int textRight = choice.deletable() ? deleteX - 6 : layout.x + layout.panelWidth - 8;
            Component label = Component.translatable("screen.njw_just_paintings.selection.entry", choice.fileName(), choice.width(), choice.height(), choice.uploader());
            graphics.text(font, trim(label, textRight - (layout.x + 8)), layout.x + 8, rowY + (ROW_HEIGHT - font.lineHeight) / 2 + 1, 0xFFFFFFFF, false);
            if (choice.deletable()) {
                graphics.fill(deleteX, rowY, layout.x + layout.panelWidth, rowY + ROW_HEIGHT, deleteHovered ? 0x885A5A5A : 0x66000000);
                Component delete = Component.translatable("screen.njw_just_paintings.selection.delete");
                graphics.text(font, delete, deleteX + (DELETE_WIDTH - font.width(delete)) / 2, rowY + (ROW_HEIGHT - font.lineHeight) / 2 + 1, 0xFFFFFFFF, false);
            }
        }
        if (choices.size() > layout.visibleRows) drawScrollBar(graphics, layout.x + layout.panelWidth + 4, layout.y, layout.visibleRows);
        drawControl(graphics, mouseX, mouseY, width / 2 - 45, layout.controlsY, 90, Component.translatable("gui.cancel"));
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() != 0) return super.mouseClicked(event, doubleClick);
        int mouseX = (int) event.x();
        int mouseY = (int) event.y();
        Layout layout = layout();
        int end = Math.min(firstVisible + layout.visibleRows, choices.size());
        for (int i = firstVisible; i < end; i++) {
            int rowY = layout.y + (i - firstVisible) * (ROW_HEIGHT + ROW_GAP);
            PaintingPayloads.Choice choice = choices.get(i);
            int deleteX = layout.x + layout.panelWidth - DELETE_WIDTH;
            if (choice.deletable() && inside(mouseX, mouseY, deleteX, rowY, DELETE_WIDTH, ROW_HEIGHT)) {
                ClientPacketDistributor.sendToServer(new PaintingPayloads.DeletePaintingPayload(hand, choice.id()));
                return true;
            }
            if (inside(mouseX, mouseY, layout.x, rowY, layout.panelWidth, ROW_HEIGHT)) {
                select(choice);
                return true;
            }
        }
        if (inside(mouseX, mouseY, width / 2 - 45, layout.controlsY, 90, ROW_HEIGHT)) {
            onClose();
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        Layout layout = layout();
        if (!inside((int) mouseX, (int) mouseY, layout.x, layout.y, layout.panelWidth, layout.listHeight)) return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        int maxFirst = Math.max(0, choices.size() - layout.visibleRows);
        if (scrollY > 0.0) firstVisible = Math.max(0, firstVisible - 1);
        else if (scrollY < 0.0) firstVisible = Math.min(maxFirst, firstVisible + 1);
        return true;
    }

    private Layout layout() {
        int panelWidth = Math.min(420, Math.max(120, width - SIDE_MARGIN * 2));
        int x = (width - panelWidth) / 2;
        int controlsY = Math.max(LIST_TOP + ROW_HEIGHT, height - CONTROL_BOTTOM_MARGIN - ROW_HEIGHT);
        int availableListHeight = Math.max(ROW_HEIGHT, controlsY - CONTROL_GAP - LIST_TOP);
        int visibleRows = Math.max(1, Math.min(MAX_VISIBLE_ROWS, (availableListHeight + ROW_GAP) / (ROW_HEIGHT + ROW_GAP)));
        int listHeight = visibleRows * (ROW_HEIGHT + ROW_GAP) - ROW_GAP;
        int y = Math.max(30, Math.min(LIST_TOP, controlsY - CONTROL_GAP - listHeight));
        int maxFirst = Math.max(0, choices.size() - visibleRows);
        if (firstVisible > maxFirst) firstVisible = maxFirst;
        return new Layout(x, y, panelWidth, visibleRows, listHeight, controlsY);
    }

    private Component trim(Component text, int maxWidth) {
        if (font.width(text) <= maxWidth) return text;
        String value = text.getString();
        String ellipsis = "...";
        int ellipsisWidth = font.width(ellipsis);
        while (!value.isEmpty() && font.width(value) + ellipsisWidth > maxWidth) value = value.substring(0, value.length() - 1);
        return Component.literal(value + ellipsis);
    }

    private void drawScrollBar(GuiGraphicsExtractor graphics, int x, int y, int visibleRows) {
        int trackHeight = visibleRows * (ROW_HEIGHT + ROW_GAP) - ROW_GAP;
        int thumbHeight = Math.max(18, trackHeight * visibleRows / choices.size());
        int maxFirst = choices.size() - visibleRows;
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

    private record Layout(int x, int y, int panelWidth, int visibleRows, int listHeight, int controlsY) {
    }
}
