package net.njw.justpaintings.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.HangingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DiodeBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.njw.justpaintings.item.PaintingItemData;
import net.njw.justpaintings.registry.ModContent;
import org.jspecify.annotations.Nullable;

import java.util.Optional;
import java.util.UUID;

public final class CustomPaintingEntity extends HangingEntity {
    private static final EntityDataAccessor<String> IMAGE_ID = SynchedEntityData.defineId(CustomPaintingEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> FILE_NAME = SynchedEntityData.defineId(CustomPaintingEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Integer> WIDTH = SynchedEntityData.defineId(CustomPaintingEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> HEIGHT = SynchedEntityData.defineId(CustomPaintingEntity.class, EntityDataSerializers.INT);
    public static final float DEPTH = 0.0625F;

    public CustomPaintingEntity(EntityType<? extends CustomPaintingEntity> type, Level level) {
        super(type, level);
    }

    public CustomPaintingEntity(Level level, BlockPos anchor, Direction direction, PaintingItemData.Selection selection) {
        super(ModContent.CUSTOM_PAINTING_ENTITY.get(), level, anchor);
        entityData.set(IMAGE_ID, selection.imageId().toString());
        entityData.set(FILE_NAME, selection.fileName());
        entityData.set(WIDTH, selection.width());
        entityData.set(HEIGHT, selection.height());
        setDirection(direction);
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

    public static BlockPos anchorFromTopLeft(BlockPos topLeft, Direction direction, int width, int height) {
        return topLeft.relative(direction.getCounterClockWise(), (width - 1) / 2).below(height / 2);
    }

    @Override
    protected AABB calculateBoundingBox(BlockPos pos, Direction direction) {
        Vec3 attachedToWall = Vec3.atCenterOf(pos).relative(direction, -0.46875);
        double horizontalOffset = offsetForPaintingSize(getPaintingWidth());
        double verticalOffset = offsetForPaintingSize(getPaintingHeight());
        Direction left = direction.getCounterClockWise();
        Vec3 position = attachedToWall.relative(left, horizontalOffset).relative(Direction.UP, verticalOffset);
        Direction.Axis axis = direction.getAxis();
        double xSize = axis == Direction.Axis.X ? DEPTH : getPaintingWidth();
        double ySize = getPaintingHeight();
        double zSize = axis == Direction.Axis.Z ? DEPTH : getPaintingWidth();
        return AABB.ofSize(position, xSize, ySize, zSize);
    }

    private static double offsetForPaintingSize(int size) {
        return size % 2 == 0 ? 0.5 : 0.0;
    }

    @Override
    public boolean survives() {
        if (hasLevelCollision(getPopBox())) return false;
        boolean supported = BlockPos.betweenClosedStream(calculateSupportBox()).anyMatch(pos -> {
            BlockState state = level().getBlockState(pos);
            return state.isSolid() || DiodeBlock.isDiode(state);
        });
        return supported && canCoexist(false);
    }

    @Override
    public void dropItem(ServerLevel level, @Nullable Entity causedBy) {
        if (!level.getGameRules().get(GameRules.ENTITY_DROPS)) return;
        playSound(SoundEvents.PAINTING_BREAK, 1.0F, 1.0F);
        if (causedBy instanceof Player player && player.hasInfiniteMaterials()) return;
        spawnAtLocation(level, createPaintingStack());
    }

    @Override
    public void playPlacementSound() {
        playSound(SoundEvents.PAINTING_PLACE, 1.0F, 1.0F);
    }

    @Override
    public void snapTo(double x, double y, double z, float yRot, float xRot) {
        setPos(x, y, z);
    }

    @Override
    public Vec3 trackingPosition() {
        return Vec3.atLowerCornerOf(pos);
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket(ServerEntity serverEntity) {
        return new ClientboundAddEntityPacket(this, getDirection().get3DDataValue(), getPos());
    }

    @Override
    public void recreateFromPacket(ClientboundAddEntityPacket packet) {
        super.recreateFromPacket(packet);
        setDirection(Direction.from3DDataValue(packet.getData()));
    }

    @Override
    public ItemStack getPickResult() {
        return createPaintingStack();
    }

    private ItemStack createPaintingStack() {
        ItemStack stack = new ItemStack(ModContent.CUSTOM_PAINTING.get());
        PaintingItemData.set(stack, getImageId(), getFileName(), getPaintingWidth(), getPaintingHeight());
        return stack;
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        output.store("facing", Direction.LEGACY_ID_CODEC_2D, getDirection());
        super.addAdditionalSaveData(output);
        output.putString("PaintingImageId", entityData.get(IMAGE_ID));
        output.putString("PaintingFileName", entityData.get(FILE_NAME));
        output.putInt("PaintingWidth", getPaintingWidth());
        output.putInt("PaintingHeight", getPaintingHeight());
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        Optional<Direction> storedFacing = input.read("facing", Direction.LEGACY_ID_CODEC_2D);
        boolean legacyAnchor = storedFacing.isEmpty();
        Direction direction = storedFacing.orElseGet(() -> Direction.from2DDataValue(Math.floorMod(input.getIntOr("PaintingFacing", Direction.SOUTH.get2DDataValue()), 4)));
        super.readAdditionalSaveData(input);
        entityData.set(IMAGE_ID, input.getStringOr("PaintingImageId", ""));
        entityData.set(FILE_NAME, input.getStringOr("PaintingFileName", ""));
        entityData.set(WIDTH, Math.clamp(input.getIntOr("PaintingWidth", 1), 1, 3));
        entityData.set(HEIGHT, Math.clamp(input.getIntOr("PaintingHeight", 1), 1, 3));
        if (legacyAnchor) pos = anchorFromTopLeft(pos, direction, getPaintingWidth(), getPaintingHeight());
        setDirection(direction);
    }
}
