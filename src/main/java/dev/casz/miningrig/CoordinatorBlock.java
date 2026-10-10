package dev.casz.miningrig;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
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
    public enum PortVisual implements StringRepresentable {
        OFF("off"), IN("in"), OUT("out"), LINK("link");
        private final String name;
        PortVisual(String name){this.name=name;}
        @Override public String getSerializedName(){return name;}
    }
    public static final EnumProperty<PortVisual> DOWN=EnumProperty.create("down",PortVisual.class);
    public static final EnumProperty<PortVisual> UP=EnumProperty.create("up",PortVisual.class);
    public static final EnumProperty<PortVisual> NORTH=EnumProperty.create("north",PortVisual.class);
    public static final EnumProperty<PortVisual> SOUTH=EnumProperty.create("south",PortVisual.class);
    public static final EnumProperty<PortVisual> WEST=EnumProperty.create("west",PortVisual.class);
    public static final EnumProperty<PortVisual> EAST=EnumProperty.create("east",PortVisual.class);
    @SuppressWarnings("unchecked")
    public static final EnumProperty<PortVisual>[] PORTS=new EnumProperty[]{DOWN,UP,NORTH,SOUTH,WEST,EAST};
    public CoordinatorBlock(Properties properties){
        super(properties);
        BlockState def=defaultBlockState();
        for(EnumProperty<PortVisual> port:PORTS)def=def.setValue(port,PortVisual.OFF);
        registerDefaultState(def);
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){
        builder.add(PORTS);
    }
    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return simpleCodec(CoordinatorBlock::new); }
    @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
    @Nullable @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state) { return new CoordinatorEntity(pos,state); }
    @Nullable @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level,BlockState state,BlockEntityType<T> type) {
        return level.isClientSide()?null:createTickerHelper(type,MiningRig.COORDINATOR_ENTITY,CoordinatorEntity::tick);
    }
    @Override protected InteractionResult useItemOn(ItemStack stack,BlockState state,Level level,
            BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit){
        if(!level.isClientSide() && level.getBlockEntity(pos) instanceof CoordinatorEntity coordinator)
            player.openMenu(coordinator);
        return InteractionResult.SUCCESS;
    }
    @Override protected InteractionResult useWithoutItem(BlockState state,Level level,BlockPos pos,Player player,BlockHitResult hit) {
        if(!level.isClientSide() && level.getBlockEntity(pos) instanceof CoordinatorEntity cc)player.openMenu(cc);
        return InteractionResult.SUCCESS;
    }
    @Override public BlockState playerWillDestroy(Level level,BlockPos pos,BlockState state,Player player) {
        if(!level.isClientSide() && level.getBlockEntity(pos) instanceof CoordinatorEntity entity)entity.dropContents();
        return super.playerWillDestroy(level,pos,state,player);
    }
    @Override protected void affectNeighborsAfterRemoval(BlockState state, net.minecraft.server.level.ServerLevel level, BlockPos pos, boolean moved) {
        if(level.getBlockEntity(pos) instanceof CoordinatorEntity cc)cc.dropContents();
        super.affectNeighborsAfterRemoval(state,level,pos,moved);
    }
}
