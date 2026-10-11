package dev.casz.miningrig;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jetbrains.annotations.Nullable;

/** Placed tablet stores only a permanent link to its authoritative computer. */
public final class MiningTabletEntity extends BlockEntity {
    private TabletLink.Target target;
    private boolean dropped;
    public MiningTabletEntity(BlockPos pos,BlockState state){super(MiningRig.TABLET_ENTITY,pos,state);}
    @Nullable public TabletLink.Target target(){return target;}
    public void link(TabletLink.Target target){this.target=target;setChanged();}
    public void dropLinkedItem(boolean drop){
        if(dropped || level==null || level.isClientSide())return;
        dropped=true;
        if(!drop)return;
        ItemStack stack=new ItemStack(MiningRig.TABLET);
        if(target!=null)TabletLink.write(stack,target);
        Block.popResource(level,worldPosition,stack);
    }
    public static void tick(Level level,BlockPos pos,BlockState state,MiningTabletEntity tablet){
        if(!(level instanceof ServerLevel server)||server.getGameTime()%10!=0)return;
        int signal=TabletAccess.lamp(server.getServer(),tablet.target);
        // Only the placed block's visual lamp alternates; handheld item stacks
        // keep a stable model component, preventing repeated re-equip bobbing.
        if(signal>=2 && (server.getGameTime()/10)%2==0)signal=0;
        MiningTabletBlock.Lamp value=MiningTabletBlock.Lamp.from(signal);
        if(state.getValue(MiningTabletBlock.LAMP)!=value)
            server.setBlock(pos,state.setValue(MiningTabletBlock.LAMP,value),Block.UPDATE_CLIENTS);
    }
    @Override protected void saveAdditional(ValueOutput out){
        super.saveAdditional(out);
        if(target!=null){
            out.putString("TabletDimension",target.dimension());
            out.putLong("TabletComputer",target.computer().asLong());
            out.putString("TabletIdentity",target.identity());
        }
    }
    @Override protected void loadAdditional(ValueInput in){
        super.loadAdditional(in);
        String dim=in.getStringOr("TabletDimension","");
        String id=in.getStringOr("TabletIdentity","");
        target=dim.isBlank()||id.isBlank()?null:
            new TabletLink.Target(dim,BlockPos.of(in.getLongOr("TabletComputer",0)),id);
    }
}
