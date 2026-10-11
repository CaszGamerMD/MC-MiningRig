package dev.casz.miningrig;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Display;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jetbrains.annotations.Nullable;

/**
 * One owner per hardware item while a replacement bit travels from an input
 * container, across the CC route, to a vacant drill bit slot.
 */
final class RigHardware {
    @Nullable private RigNetwork.Supply cargo;
    @Nullable private Display.ItemDisplay visual;
    private int elapsed;
    private long requestedAt=-1000;
    private int pendingReplacements,unfilledTicks;
    void requestReplacement(){pendingReplacements++;unfilledTicks=0;}
    boolean awaitingReplacement(){return pendingReplacements>0 || cargo!=null;}
    boolean waitExpired(){return unfilledTicks>=400;}

    void tick(ServerLevel world,DrillEntity drill){
        if(cargo==null){
            if(pendingReplacements<=0)return;
            if(drill.emptyBitSlots()==0){pendingReplacements=0;return;}
            unfilledTicks++;
            if(world.getGameTime()-requestedAt<20)return;
            requestedAt=world.getGameTime();
            CoordinatorEntity cc=RigNetwork.nearestDrillCoordinator(world,drill);
            if(cc==null)return;
            RigNetwork.Supply requested=RigNetwork.reserveHardware(world,cc,false);
            if(requested==null)return;
            List<BlockPos> path=new ArrayList<>(requested.path());
            path.add(drill.getBlockPos());
            cargo=new RigNetwork.Supply(requested.stack(),path,
                requested.sourcePos(),requested.sourceFace());
            elapsed=0;
            drill.setChanged();
        }
        if(cargo==null)return;
        elapsed++;
        List<BlockPos> path=cargo.path();
        if(path.size()<2){finish(world,drill);return;}
        if(visual==null){
            Display.ItemDisplay display=new Display.ItemDisplay(
                BuiltInRegistries.ENTITY_TYPE.getValue(
                    Identifier.withDefaultNamespace("item_display")),world);
            display.setItemStack(cargo.stack().copyWithCount(1));
            display.setItemTransform(ItemDisplayContext.GROUND);
            display.setNoGravity(true);
            world.addFreshEntity(display);
            visual=display;
        }
        int tick=elapsed;
        for(int i=1;i<path.size();i++){
            BlockPos from=path.get(i-1),to=path.get(i);
            int distance=Math.abs(from.getX()-to.getX())
                +Math.abs(from.getY()-to.getY())
                +Math.abs(from.getZ()-to.getZ());
            int duration=Math.max(2,(int)Math.ceil(distance*0.8));
            if(tick>=duration){tick-=duration;continue;}
            double ratio=tick/(double)duration;
            if(visual!=null)visual.setPos(
                from.getX()+.5+(to.getX()-from.getX())*ratio,
                from.getY()+.55+(to.getY()-from.getY())*ratio,
                from.getZ()+.5+(to.getZ()-from.getZ())*ratio);
            return;
        }
        finish(world,drill);
    }
    private void clear(){
        if(visual!=null)visual.discard();
        visual=null;cargo=null;elapsed=0;
    }
    private void refund(ServerLevel world,DrillEntity drill,RigNetwork.Supply supply){
        ItemStack left=RigNetwork.returnToSource(world,supply);
        if(!left.isEmpty()&&!RigNetwork.returnSurplus(world,drill,left))
            Block.popResource(world,drill.getBlockPos(),left.copy());
    }
    private void finish(ServerLevel world,DrillEntity drill){
        if(cargo==null)return;
        RigNetwork.Supply item=cargo;
        if(drill.insertBit(item.stack())<0)refund(world,drill,item);
        else {pendingReplacements=Math.max(0,pendingReplacements-1);unfilledTicks=0;}
        clear();drill.setChanged();
    }
    void drop(ServerLevel world,DrillEntity drill){
        if(cargo!=null)refund(world,drill,cargo);
        clear();
    }
    void moveTo(RigHardware next){
        next.clear();
        next.cargo=cargo;next.elapsed=elapsed;next.requestedAt=requestedAt;
        next.pendingReplacements=pendingReplacements;
        next.unfilledTicks=unfilledTicks;
        next.visual=visual;
        cargo=null;visual=null;pendingReplacements=0;
    }
    void save(ValueOutput out){
        out.putInt("HardwareReplacements",pendingReplacements);
        out.putInt("HardwareMissingTicks",unfilledTicks);
        out.putBoolean("HardwareInFlight",cargo!=null);
        if(cargo==null)return;
        out.store("HardwareItem",ItemStack.CODEC,cargo.stack());
        out.putInt("HardwareElapsed",elapsed);
        out.putInt("HardwareRouteSize",cargo.path().size());
        for(int i=0;i<cargo.path().size();i++)
            out.putLong("HardwareRoute"+i,cargo.path().get(i).asLong());
        if(cargo.sourcePos()!=null&&cargo.sourceFace()!=null){
            out.putLong("HardwareSource",cargo.sourcePos().asLong());
            out.putInt("HardwareFace",cargo.sourceFace().get3DDataValue());
        }
    }
    void load(ValueInput in){
        clear();
        pendingReplacements=Math.max(0,in.getIntOr("HardwareReplacements",0));
        unfilledTicks=Math.max(0,in.getIntOr("HardwareMissingTicks",0));
        if(!in.getBooleanOr("HardwareInFlight",false))return;
        ItemStack stack=in.read("HardwareItem",ItemStack.CODEC).orElse(ItemStack.EMPTY);
        if(stack.isEmpty())return;
        int length=Math.max(0,Math.min(512,in.getIntOr("HardwareRouteSize",0)));
        List<BlockPos> path=new ArrayList<>();
        for(int i=0;i<length;i++)path.add(BlockPos.of(in.getLongOr("HardwareRoute"+i,0)));
        BlockPos source=in.getLong("HardwareSource").isPresent()
            ?BlockPos.of(in.getLongOr("HardwareSource",0)):null;
        Direction face=source==null?null:
            Direction.from3DDataValue(in.getIntOr("HardwareFace",0));
        cargo=new RigNetwork.Supply(stack,path,source,face);
        elapsed=Math.max(0,in.getIntOr("HardwareElapsed",0));
    }
}
