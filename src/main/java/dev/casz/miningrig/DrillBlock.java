package dev.casz.miningrig;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

public final class DrillBlock extends BaseEntityBlock {
    public DrillBlock(Properties properties){super(properties);}
    @Override protected MapCodec<? extends BaseEntityBlock> codec(){return simpleCodec(DrillBlock::new);}
    @Override protected RenderShape getRenderShape(BlockState state){return RenderShape.MODEL;}
    @Nullable @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return new DrillEntity(pos,state);}
    @Override protected InteractionResult useItemOn(ItemStack stack,BlockState state,Level level,BlockPos pos,
                                            Player player,InteractionHand hand,BlockHitResult hit){
        if(!(stack.getItem() instanceof DrillBit))return InteractionResult.PASS;
        if(level.getBlockEntity(pos) instanceof DrillEntity drill && !level.isClientSide()){
            int slot=drill.insertBit(stack);
            if(slot<0)player.sendSystemMessage(Component.literal("All three drill-bit slots are occupied."));
            else {
                if(!player.getAbilities().instabuild)stack.shrink(1);
                player.sendSystemMessage(Component.literal("Installed bit into slot "+(slot+1)));
            }
        }
        return InteractionResult.SUCCESS;
    }
    @Override protected InteractionResult useWithoutItem(BlockState state,Level level,BlockPos pos,Player player,BlockHitResult hit){
        if(!level.isClientSide() && level.getBlockEntity(pos) instanceof DrillEntity drill){
            if(player.isShiftKeyDown() && level instanceof ServerLevel server){
                RigPairing.select(server,player,pos);
                player.sendSystemMessage(Component.literal("Drill selected. Sneak-right-click your Mining Computer with an empty hand."));
            }else player.openMenu(drill);
        }
        return InteractionResult.SUCCESS;
    }
    @Override public BlockState playerWillDestroy(Level level,BlockPos pos,BlockState state,Player player) {
        if(!level.isClientSide() && level.getBlockEntity(pos) instanceof DrillEntity entity)entity.dropInventory();
        return super.playerWillDestroy(level,pos,state,player);
    }
    @Override protected void affectNeighborsAfterRemoval(BlockState state,ServerLevel level,BlockPos pos,boolean moved){
        if(level.getBlockEntity(pos) instanceof DrillEntity drill)drill.dropInventory();
        super.affectNeighborsAfterRemoval(state,level,pos,moved);
    }
}
