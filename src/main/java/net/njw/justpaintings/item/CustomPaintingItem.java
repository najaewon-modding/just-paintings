package net.njw.justpaintings.item;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;
import net.njw.justpaintings.entity.CustomPaintingEntity;
import net.njw.justpaintings.server.PaintingUploadManager;
import org.jspecify.annotations.Nullable;

public final class CustomPaintingItem extends Item {
    public CustomPaintingItem(Properties properties) {
        super(properties);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!PaintingItemData.isSelected(stack)) {
            if (!level.isClientSide() && player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) PaintingUploadManager.sendChoices(serverPlayer, hand);
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null) return InteractionResult.FAIL;
        ItemStack stack = context.getItemInHand();
        if (!PaintingItemData.isSelected(stack)) {
            if (!context.getLevel().isClientSide() && player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) PaintingUploadManager.sendChoices(serverPlayer, context.getHand());
            return InteractionResult.SUCCESS;
        }
        Direction face = context.getClickedFace();
        if (face.getAxis().isVertical()) return InteractionResult.FAIL;
        PaintingItemData.Selection selection = PaintingItemData.get(stack);
        if (selection == null) return InteractionResult.FAIL;
        if (!(context.getLevel() instanceof ServerLevel level)) return InteractionResult.SUCCESS;
        BlockPos clickedTopLeft = context.getClickedPos().relative(face);
        CustomPaintingEntity entity = findPlacement(level, clickedTopLeft, face, selection);
        if (entity == null) {
            player.sendSystemMessage(Component.translatable("message.njw_just_paintings.placement.failed"));
            return InteractionResult.FAIL;
        }
        entity.playPlacementSound();
        level.gameEvent(player, GameEvent.ENTITY_PLACE, entity.position());
        level.addFreshEntity(entity);
        if (!player.getAbilities().instabuild) stack.shrink(1);
        return InteractionResult.SUCCESS_SERVER;
    }

    private static @Nullable CustomPaintingEntity findPlacement(ServerLevel level, BlockPos clickedTopLeft, Direction face, PaintingItemData.Selection selection) {
        Direction left = face.getCounterClockWise().getOpposite();
        int maxDistance = selection.width() + selection.height() - 2;
        for (int distance = 0; distance <= maxDistance; distance++) {
            int maxUp = Math.min(distance, selection.height() - 1);
            for (int shiftUp = maxUp; shiftUp >= 0; shiftUp--) {
                int shiftLeft = distance - shiftUp;
                if (shiftLeft >= selection.width()) continue;
                BlockPos topLeft = clickedTopLeft.above(shiftUp).relative(left, shiftLeft);
                CustomPaintingEntity candidate = new CustomPaintingEntity(level, topLeft, face, selection);
                if (candidate.survives()) return candidate;
            }
        }
        return null;
    }
}
