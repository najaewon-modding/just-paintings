package net.njw.justpaintings.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.HangingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.njw.justpaintings.item.PaintingItemData;
import net.njw.justpaintings.registry.ModContent;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

public final class CustomPaintingEntity extends HangingEntity {
    private static final EntityDataAccessor<String> IMAGE_ID = SynchedEntityData.defineId(CustomPaintingEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> FILE_NAME = SynchedEntityData.defineId(CustomPaintingEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Integer> WIDTH = SynchedEntityData.defineId(CustomPaintingEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> HEIGHT = SynchedEntityData.defineId(CustomPaintingEntity.class, EntityDataSerializers.INT);

    public CustomPaintingEntity(EntityType<? extends CustomPaintingEntity> type, Level level) {
        super(type, level);
    }

    public CustomPaintingEntity(Level level, BlockPos topLeft, Direction direction, PaintingItemData.Selection selection) {
        super(ModContent.CUSTOM_PAINTING_ENTITY.get(), level, topLeft);
        entityData.set(IMAGE_ID, selection.imageId().toString());
        entityData.set(FILE_NAME, selection.fileName());
        entityData.set(WIDTH, selection.width());
        entityData.set(HEIGHT, selection.height());
        setDirection(direction);
        recalculateBoundingBox();
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(IMAGE_ID, "");
        builder.define(FILE_NAME, "");
        builder.define(WIDTH, 1);
        builder.define(HEIGHT, 1);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> accessor) {
        super.onSyncedDataUpdated(accessor);
        if (WIDTH.equals(accessor) || HEIGHT.equals(accessor)) recalculateBoundingBox();
    }

    public UUID getImageId() {
        try {
            return UUID.fromString(entityData.get(IMAGE_ID));
        } catch (IllegalArgumentException e) {
            return new UUID(0L, 0L);
        }
    }

    public String getFileName() {
        return entityData.get(FILE_NAME);
    }

    public int getPaintingWidth() {
        return Math.clamp(entityData.get(WIDTH), 1, 3);
    }

    public int getPaintingHeight() {
        return Math.clamp(entityData.get(HEIGHT), 1, 3);
    }

    @Override
    protected AABB calculateBoundingBox(BlockPos topLeft, Direction direction) {
        int width = getPaintingWidth();
        int height = getPaintingHeight();
        Direction right = direction.getCounterClockWise();
        Vec3 center = Vec3.atCenterOf(topLeft)
                .relative(direction.getOpposite(), 0.46875)
                .add(right.getStepX() * (width - 1) * 0.5, -(height - 1) * 0.5, right.getStepZ() * (width - 1) * 0.5);
        double sizeX = direction.getAxis() == Direction.Axis.Z ? width : 0.0625;
        double sizeZ = direction.getAxis() == Direction.Axis.X ? width : 0.0625;
        return AABB.ofSize(center, sizeX, height, sizeZ);
    }

    @Override
    public void playPlacementSound() {
        playSound(SoundEvents.PAINTING_PLACE, 1.0F, 1.0F);
    }

    @Override
    public void dropItem(ServerLevel level, @Nullable Entity breaker) {
        playSound(SoundEvents.PAINTING_BREAK, 1.0F, 1.0F);
        ItemStack stack = new ItemStack(ModContent.CUSTOM_PAINTING.get());
        PaintingItemData.set(stack, getImageId(), getFileName(), getPaintingWidth(), getPaintingHeight());
        spawnAtLocation(level, stack, 0.0F);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putString("PaintingImageId", entityData.get(IMAGE_ID));
        output.putString("PaintingFileName", entityData.get(FILE_NAME));
        output.putInt("PaintingWidth", getPaintingWidth());
        output.putInt("PaintingHeight", getPaintingHeight());
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        entityData.set(IMAGE_ID, input.getStringOr("PaintingImageId", ""));
        entityData.set(FILE_NAME, input.getStringOr("PaintingFileName", ""));
        entityData.set(WIDTH, Math.clamp(input.getIntOr("PaintingWidth", 1), 1, 3));
        entityData.set(HEIGHT, Math.clamp(input.getIntOr("PaintingHeight", 1), 1, 3));
        recalculateBoundingBox();
    }
}
