package dev.casz.miningrig;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

public final class CoordinatorBlock extends BaseEntityBlock {
    public CoordinatorBlock(Properties properties) { super(properties); }
    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return simpleCodec(CoordinatorBlock::new); }
    @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
    @Nullable @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state) { return new CoordinatorEntity(pos,state); }
    @Nullable @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level,BlockState state,BlockEntityType<T> type) {
        return level.isClientSide()?null:createTickerHelper(type,MiningRig.COORDINATOR_ENTITY,CoordinatorEntity::tick);
    }
    @Override protected InteractionResult useWithoutItem(BlockState state,Level level,BlockPos pos,Player player,BlockHitResult hit) {
        if(!level.isClientSide() && level.getBlockEntity(pos) instanceof CoordinatorEntity collector)
            player.sendSystemMessage(Component.literal("Collection Coordinator: "+collector.filledSlots()+"/27 slots used. Hoppers can extract; use Rig Linker to configure wireless output."));
        return InteractionResult.SUCCESS;
    }
    @Override protected void affectNeighborsAfterRemoval(BlockState state, net.minecraft.server.level.ServerLevel level, BlockPos pos, boolean moved) {
        if (level.getBlockEntity(pos) instanceof CoordinatorEntity entity && !level.isClientSide()) {
            for(int i=0;i<entity.getContainerSize();i++) {
                if(!entity.getItem(i).isEmpty()) popResource(level,pos,entity.getItem(i));
            }
        }
        super.affectNeighborsAfterRemoval(state,level,pos,moved);
    }
}
