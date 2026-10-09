package dev.casz.miningrig;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

public final class DrillBlock extends BaseEntityBlock {
    public DrillBlock(Properties properties) { super(properties); }
    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return simpleCodec(DrillBlock::new); }
    @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
    @Nullable @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new DrillEntity(pos,state); }
    @Override protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                                      Player player, InteractionHand hand, BlockHitResult hit) {
        if (stack.is(MiningRig.LINKER)) return InteractionResult.PASS;
        if (!(level.getBlockEntity(pos) instanceof DrillEntity drill) || !(stack.getItem() instanceof DrillBit))
            return InteractionResult.PASS;
        if (!level.isClientSide()) {
            if (!drill.tip().isEmpty()) {
                player.sendSystemMessage(Component.literal("Remove old bit first (sneak + empty hand)."));
            } else {
                drill.install(stack.copyWithCount(1));
                if (!player.getAbilities().instabuild) stack.shrink(1);
                player.sendSystemMessage(Component.literal("Drill bit installed: "+drill.tip().getDisplayName().getString()));
            }
        }
        return InteractionResult.SUCCESS;
    }
    @Override protected InteractionResult useWithoutItem(BlockState state,Level level,BlockPos pos,Player player,BlockHitResult hit) {
        if (level.getBlockEntity(pos) instanceof DrillEntity drill && !level.isClientSide()) {
            if (player.isShiftKeyDown() && !drill.tip().isEmpty()) {
                ItemStack removed = drill.removeTip();
                if (!player.addItem(removed)) Block.popResource(level,pos,removed);
            } else player.sendSystemMessage(Component.literal(drill.tip().isEmpty() ? "No drill bit installed" : "Installed: "+drill.tip().getDisplayName().getString()));
        }
        return InteractionResult.SUCCESS;
    }
    @Override protected void onRemove(BlockState state,Level level,BlockPos pos,BlockState newState,boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof DrillEntity drill && !drill.relocating()
            && !drill.tip().isEmpty() && !level.isClientSide()) {
            Block.popResource(level,pos,drill.tip().copy());
        }
        super.onRemove(state,level,pos,newState,movedByPiston);
    }
}
