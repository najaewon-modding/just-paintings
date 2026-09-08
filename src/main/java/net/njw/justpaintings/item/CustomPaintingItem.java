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
import net.njw.justpaintings.entity.CustomPaintingEntity;
import net.njw.justpaintings.server.PaintingUploadManager;

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
        if (face.getAxis() == Direction.Axis.Y) return InteractionResult.FAIL;
        PaintingItemData.Selection selection = PaintingItemData.get(stack);
        if (selection == null) return InteractionResult.FAIL;
        if (!(context.getLevel() instanceof ServerLevel level)) return InteractionResult.SUCCESS;
        BlockPos initialTopLeft = context.getClickedPos().relative(face);
        Direction left = face.getClockWise();
        int maxShift = selection.width() + selection.height() - 2;
        for (int totalShift = 0; totalShift <= maxShift; totalShift++) {
            for (int shiftUp = 0; shiftUp < selection.height(); shiftUp++) {
                int shiftLeft = totalShift - shiftUp;
                if (shiftLeft < 0 || shiftLeft >= selection.width()) continue;
                BlockPos candidate = initialTopLeft.above(shiftUp).relative(left, shiftLeft);
                CustomPaintingEntity entity = new CustomPaintingEntity(level, candidate, face, selection);
                if (!entity.survives()) continue;
                entity.playPlacementSound();
                level.addFreshEntity(entity);
                if (!player.getAbilities().instabuild) stack.shrink(1);
                return InteractionResult.SUCCESS_SERVER;
            }
        }
        player.sendSystemMessage(Component.translatable("message.njw_just_paintings.placement.failed"));
        return InteractionResult.FAIL;
    }
}
