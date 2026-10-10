package dev.casz.miningrig;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** Invisible upper target for the 1.5-block-high workstation screen. */
public final class ComputerUpperBlock extends Block {
    public ComputerUpperBlock(Properties properties){super(properties);}
    @Override protected MapCodec<? extends Block> codec(){return simpleCodec(ComputerUpperBlock::new);}
    @Override protected RenderShape getRenderShape(BlockState state){return RenderShape.INVISIBLE;}
    @Override protected InteractionResult useItemOn(ItemStack stack,BlockState state,Level level,
            BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit){
        return ComputerBlock.handleItem(stack,level,pos.below(),player);
    }
    @Override protected InteractionResult useWithoutItem(BlockState state,Level level,
            BlockPos pos,Player player,BlockHitResult hit){
        if(!level.isClientSide() && level.getBlockEntity(pos.below()) instanceof ComputerEntity computer)
            player.openMenu(computer);
        return InteractionResult.SUCCESS;
    }
    @Override public BlockState playerWillDestroy(Level level,BlockPos pos,BlockState state,Player player){
        if(level instanceof ServerLevel server && server.getBlockState(pos.below()).is(MiningRig.COMPUTER))
            server.destroyBlock(pos.below(),!player.getAbilities().instabuild);
        return super.playerWillDestroy(level,pos,state,player);
    }
}
