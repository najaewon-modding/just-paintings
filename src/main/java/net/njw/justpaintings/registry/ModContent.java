package net.njw.justpaintings.registry;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.njw.justpaintings.JustPaintings;
import net.njw.justpaintings.entity.CustomPaintingEntity;
import net.njw.justpaintings.item.CustomPaintingItem;

public final class ModContent {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(JustPaintings.MOD_ID);
    public static final DeferredRegister.Entities ENTITIES = DeferredRegister.createEntities(JustPaintings.MOD_ID);
    public static final DeferredItem<CustomPaintingItem> CUSTOM_PAINTING = ITEMS.registerItem("custom_painting", CustomPaintingItem::new, properties -> properties.stacksTo(1));
    public static final DeferredHolder<EntityType<?>, EntityType<CustomPaintingEntity>> CUSTOM_PAINTING_ENTITY = ENTITIES.registerEntityType("custom_painting", CustomPaintingEntity::new, MobCategory.MISC, builder -> builder.sized(0.5F, 0.5F).clientTrackingRange(10).updateInterval(20));

    private ModContent() {
    }
}
