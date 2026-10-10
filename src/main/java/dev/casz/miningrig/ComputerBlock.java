package dev.casz.miningrig;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

public final class ComputerBlock extends BaseEntityBlock {
    public static final BooleanProperty ALARM = BooleanProperty.create("alarm");
    public static final BooleanProperty WARN = BooleanProperty.create("warn");
    public ComputerBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(ALARM, false).setValue(WARN,false));
    }
    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return simpleCodec(ComputerBlock::new); }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
        builder.add(ALARM,WARN);
    }
    @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
    @Nullable @Override public BlockState getStateForPlacement(BlockPlaceContext context){
        if(!context.getLevel().getBlockState(context.getClickedPos().above()).canBeReplaced(context))
            return null;
        return super.getStateForPlacement(context);
    }
    @Override public void setPlacedBy(Level level,BlockPos pos,BlockState state,
            @Nullable LivingEntity placer,ItemStack stack){
        super.setPlacedBy(level,pos,state,placer,stack);
        if(level.getBlockState(pos.above()).canBeReplaced())
            level.setBlock(pos.above(),MiningRig.COMPUTER_UPPER.defaultBlockState(),Block.UPDATE_ALL);
    }
    @Nullable @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new ComputerEntity(pos,state); }
    @Nullable @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide() ? null : createTickerHelper(type, MiningRig.COMPUTER_ENTITY, ComputerEntity::tick);
    }
    public static InteractionResult handleItem(ItemStack stack,Level level,BlockPos pos,Player player) {
        if (!(level.getBlockEntity(pos) instanceof ComputerEntity computer)) return InteractionResult.PASS;
        if(stack.is(MiningRig.DRILL.asItem())){
            if(level instanceof net.minecraft.server.level.ServerLevel server){
                int slot=computer.registerItem(server,stack);
                player.sendSystemMessage(Component.literal(slot<0
                    ? "Computer already controls four drills. Unlink one in the computer UI first."
                    : "Drill linked in computer slot "+(slot+1)+". Place this drill to report its coordinates."));
            }
            return InteractionResult.SUCCESS;
        }
        if (stack.is(Items.STICK) || stack.is(Items.BONE) || stack.is(Items.LAPIS_LAZULI)) {
            if (!level.isClientSide()) {
                if (stack.is(Items.STICK)) computer.setRadius(computer.selectedRadius() + (player.isShiftKeyDown() ? -1 : 1));
                if (stack.is(Items.BONE)) computer.setDepth(computer.selectedDepth() + (player.isShiftKeyDown() ? -1 : 1));
                if (stack.is(Items.LAPIS_LAZULI)) computer.setCircle(!computer.selectedCircle());
                player.sendSystemMessage(Component.literal(computer.description()));
            }
            return InteractionResult.SUCCESS;
        }
        if(!level.isClientSide())player.openMenu(computer);
        return InteractionResult.SUCCESS;
    }
    @Override protected InteractionResult useItemOn(ItemStack stack,BlockState state,Level level,
            BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit){
        return handleItem(stack,level,pos,player);
    }
    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                                          Player player, BlockHitResult hit) {
        if(!level.isClientSide() && level.getBlockEntity(pos) instanceof ComputerEntity computer)
            player.openMenu(computer);
        return InteractionResult.SUCCESS;
    }
    @Override public BlockState playerWillDestroy(Level level,BlockPos pos,BlockState state,Player player){
        if(!level.isClientSide() && level.getBlockState(pos.above()).is(MiningRig.COMPUTER_UPPER))
            level.removeBlock(pos.above(),false);
        return super.playerWillDestroy(level,pos,state,player);
    }
    @Override protected void affectNeighborsAfterRemoval(BlockState state,ServerLevel level,BlockPos pos,boolean moved){
        if(level.getBlockState(pos.above()).is(MiningRig.COMPUTER_UPPER))
            level.removeBlock(pos.above(),false);
        super.affectNeighborsAfterRemoval(state,level,pos,moved);
    }
}
