package dev.casz.miningrig;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.StateDefinition;
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
import net.minecraft.network.chat.Component;

/**
 * Visible yellow housing and top output hatch of the mining drill.
 * Has no separate item/inventory; every action delegates to the drill below.
 */
public final class DrillUpperBlock extends Block {
    public static final EnumProperty<Direction> FACING=BlockStateProperties.FACING;
    public DrillUpperBlock(Properties properties){
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING,Direction.DOWN));
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){
        builder.add(FACING);
    }
    @Override protected MapCodec<? extends Block> codec(){return simpleCodec(DrillUpperBlock::new);}
    @Override protected RenderShape getRenderShape(BlockState state){return RenderShape.MODEL;}
    /** The drill head sits forward from this body on its FACING axis. */
    private static BlockPos headPosition(BlockPos body,BlockState state){
        return body.relative(state.getValue(FACING));
    }
    private static DrillEntity owner(Level level,BlockPos body,BlockState state){
        BlockPos head=headPosition(body,state);
        if(level.getBlockEntity(head) instanceof DrillEntity drill
                &&drill.drillingDirection()==state.getValue(FACING))return drill;
        return null;
    }
    @Override protected InteractionResult useItemOn(ItemStack stack,BlockState state,Level level,
            BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit){
        if(!(stack.getItem() instanceof DrillBit)){
            if(!level.isClientSide()){
                DrillEntity drill=owner(level,pos,state);
                if(drill!=null)player.openMenu(drill);
            }
            return InteractionResult.SUCCESS;
        }
        if(!level.isClientSide() && owner(level,pos,state) instanceof DrillEntity drill){
            int slot=drill.insertBit(stack);
            if(slot<0)player.sendSystemMessage(Component.literal("All three drill-bit slots are occupied."));
            else{
                if(!player.getAbilities().instabuild)stack.shrink(1);
                player.sendSystemMessage(Component.literal("Installed bit into slot "+(slot+1)));
            }
        }
        return InteractionResult.SUCCESS;
    }
    @Override protected InteractionResult useWithoutItem(BlockState state,Level level,BlockPos pos,
            Player player,BlockHitResult hit){
        if(!level.isClientSide()){
            DrillEntity drill=owner(level,pos,state);
            if(drill!=null)player.openMenu(drill);
        }
        return InteractionResult.SUCCESS;
    }
    @Override public BlockState playerWillDestroy(Level level,BlockPos pos,BlockState state,Player player){
        if(level instanceof ServerLevel server){
            BlockPos head=headPosition(pos,state);
            if(server.getBlockState(head).is(MiningRig.DRILL)
                    &&owner(server,pos,state) instanceof DrillEntity drill){
                drill.dropInventory();
                server.destroyBlock(head,!player.getAbilities().instabuild);
            }
        }
        return super.playerWillDestroy(level,pos,state,player);
    }
}
