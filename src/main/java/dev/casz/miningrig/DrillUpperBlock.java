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
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.network.chat.Component;

/**
 * Visible yellow housing and top output hatch of the mining drill.
 * Has no separate item/inventory; every action delegates to the drill head.
 */
public final class DrillUpperBlock extends Block {
    public static final EnumProperty<Direction> FACING=BlockStateProperties.FACING;
    /** Pulses red while the paired computer reports a drill fault. */
    public static final BooleanProperty ALARM=BooleanProperty.create("alarm");
    public DrillUpperBlock(Properties properties){
        super(properties.lightLevel(state->state.getValue(ALARM)?8:0));
        registerDefaultState(stateDefinition.any().setValue(FACING,Direction.DOWN).setValue(ALARM,false));
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){
        builder.add(FACING,ALARM);
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
    @Override public ItemStack getCloneItemStack(BlockState state,LevelReader level,BlockPos pos,Player player){
        BlockPos head=headPosition(pos,state);
        ItemStack stack=new ItemStack(MiningRig.DRILL.asItem());
        if(level.getBlockEntity(head) instanceof DrillEntity drill)drill.copyBindingTo(stack);
        return stack;
    }
    @Override public BlockState playerWillDestroy(Level level,BlockPos pos,BlockState state,Player player){
        if(level instanceof ServerLevel server){
            BlockPos head=headPosition(pos,state);
            DrillEntity drill=owner(server,pos,state);
            if(drill!=null&&!drill.relocating()&&server.getBlockState(head).is(MiningRig.DRILL))
                // Respect creative mode: the head is the sole item/loot source.
                server.destroyBlock(head,!player.getAbilities().instabuild);
        }
        return super.playerWillDestroy(level,pos,state,player);
    }
    /** Either half is the same machine: breaking the housing removes the head,
     * which is the sole item/loot source. During normal drill movement the old
     * housing is also removed, but its owner is marked relocating. */
    @Override protected void affectNeighborsAfterRemoval(BlockState state,ServerLevel level,
            BlockPos pos,boolean moved){
        if(!moved){
            DrillEntity drill=owner(level,pos,state);
            BlockPos head=headPosition(pos,state);
            if(drill!=null&&!drill.relocating()&&level.getBlockState(head).is(MiningRig.DRILL))
                level.destroyBlock(head,true);
        }
        super.affectNeighborsAfterRemoval(state,level,pos,moved);
    }
    @Override protected InteractionResult useItemOn(ItemStack stack,BlockState state,Level level,
            BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit){
        if(stack.is(MiningRig.TARGET_CHALK))
            return TargetChalkItem.interact(level,player,stack,pos,hit.getDirection());
        if(!(stack.getItem() instanceof DrillBit)){
            if(!level.isClientSide()){
                DrillEntity drill=owner(level,pos,state);
                if(drill!=null)player.openMenu(drill);
            }
            return InteractionResult.SUCCESS;
        }
        if(!level.isClientSide()){
            DrillEntity drill=owner(level,pos,state);
            if(drill!=null){
                int slot=drill.insertBit(stack);
                if(slot<0)player.sendSystemMessage(Component.literal("All three drill-bit slots are occupied."));
                else{
                    if(!player.getAbilities().instabuild)stack.shrink(1);
                    player.sendSystemMessage(Component.literal("Installed bit into slot "+(slot+1)));
                }
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
}
