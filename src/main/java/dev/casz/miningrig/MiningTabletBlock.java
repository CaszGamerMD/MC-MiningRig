package dev.casz.miningrig;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/** Thin face-mounted remote workstation. Never accepts drill pairing. */
public final class MiningTabletBlock extends BaseEntityBlock {
    public enum Lamp implements StringRepresentable {
        DARK("dark"),GREEN("green"),YELLOW("yellow"),RED("red");
        private final String label;
        Lamp(String label){this.label=label;}
        @Override public String getSerializedName(){return label;}
        public static Lamp from(int index){return values()[Math.max(0,Math.min(3,index))];}
    }
    public static final EnumProperty<Direction> FACING=BlockStateProperties.FACING;
    public static final EnumProperty<Lamp> LAMP=EnumProperty.create("lamp",Lamp.class);
    public MiningTabletBlock(Properties properties){
        super(properties);
        registerDefaultState(defaultBlockState().setValue(FACING,Direction.NORTH).setValue(LAMP,Lamp.DARK));
    }
    @Override protected MapCodec<? extends BaseEntityBlock> codec(){return simpleCodec(MiningTabletBlock::new);}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block,BlockState> builder){builder.add(FACING,LAMP);}
    @Override protected RenderShape getRenderShape(BlockState state){return RenderShape.MODEL;}
    @Nullable @Override public BlockState getStateForPlacement(BlockPlaceContext ctx){
        return defaultBlockState().setValue(FACING,ctx.getClickedFace());
    }
    @Nullable @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return new MiningTabletEntity(pos,state);}
    @Nullable @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level,BlockState state,BlockEntityType<T> type){
        return level.isClientSide()?null:createTickerHelper(type,MiningRig.TABLET_ENTITY,MiningTabletEntity::tick);
    }
    @Override public void setPlacedBy(Level level,BlockPos pos,BlockState state,
            @Nullable LivingEntity player,ItemStack stack){
        super.setPlacedBy(level,pos,state,player,stack);
        if(level.getBlockEntity(pos) instanceof MiningTabletEntity tablet)
            tablet.link(TabletLink.read(stack));
    }
    @Override protected InteractionResult useItemOn(ItemStack held,BlockState state,Level level,
            BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit){
        return open(level,pos,player);
    }
    @Override protected InteractionResult useWithoutItem(BlockState state,Level level,
            BlockPos pos,Player player,BlockHitResult hit){return open(level,pos,player);}
    private InteractionResult open(Level level,BlockPos pos,Player player){
        if(level instanceof ServerLevel && player instanceof ServerPlayer serverPlayer
                && level.getBlockEntity(pos) instanceof MiningTabletEntity tablet)
            TabletAccess.open(serverPlayer,tablet.target());
        return InteractionResult.SUCCESS;
    }
    @Override public BlockState playerWillDestroy(Level level,BlockPos pos,BlockState state,Player player){
        if(!level.isClientSide() && level.getBlockEntity(pos) instanceof MiningTabletEntity tablet)
            tablet.dropLinkedItem(!player.getAbilities().instabuild);
        return super.playerWillDestroy(level,pos,state,player);
    }
    @Override protected void affectNeighborsAfterRemoval(BlockState state,ServerLevel level,BlockPos pos,boolean moved){
        if(level.getBlockEntity(pos) instanceof MiningTabletEntity tablet)tablet.dropLinkedItem(true);
        super.affectNeighborsAfterRemoval(state,level,pos,moved);
    }
}
