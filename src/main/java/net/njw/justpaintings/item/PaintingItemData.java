package net.njw.justpaintings.item;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.util.UUID;

public final class PaintingItemData {
    private static final String IMAGE_ID = "PaintingImageId";
    private static final String FILE_NAME = "PaintingFileName";
    private static final String WIDTH = "PaintingWidth";
    private static final String HEIGHT = "PaintingHeight";

    private PaintingItemData() {
    }

    public static boolean isSelected(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data != null && data.copyTag().contains(IMAGE_ID);
    }

    public static Selection get(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) return null;
        CompoundTag tag = data.copyTag();
        String id = tag.getStringOr(IMAGE_ID, "");
        if (id.isBlank()) return null;
        try {
            return new Selection(UUID.fromString(id), tag.getStringOr(FILE_NAME, ""), tag.getIntOr(WIDTH, 1), tag.getIntOr(HEIGHT, 1));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public static void set(ItemStack stack, UUID imageId, String fileName, int width, int height) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        tag.putString(IMAGE_ID, imageId.toString());
        tag.putString(FILE_NAME, fileName);
        tag.putInt(WIDTH, width);
        tag.putInt(HEIGHT, height);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    public record Selection(UUID imageId, String fileName, int width, int height) {
    }
}
