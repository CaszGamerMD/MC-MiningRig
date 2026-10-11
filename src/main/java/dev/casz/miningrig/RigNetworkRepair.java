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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jetbrains.annotations.Nullable;

/**
 * Repair attempts are owned by the computer, not the moving drill. Reserves
 * one CC from a Hardware input and physically transports it along the network
 * to an empty valid straight-line extension inside the drill's 24-block range.
 * Never places into occupied blocks or silently consumes hardware.
 */
final class RigNetworkRepair {
    enum Result {WAITING,CONNECTED,FAILED}
    private static final int MAX_WAIT_TICKS=400;
    private static final class Job {
        @Nullable RigNetwork.Supply cargo;
        @Nullable BlockPos destination;
        @Nullable Display.ItemDisplay display;
        int ticks,elapsed;
        void clear(){
            if(display!=null)display.discard();
            display=null;cargo=null;destination=null;ticks=0;elapsed=0;
        }
    }
    private final Job[] jobs={new Job(),new Job(),new Job(),new Job()};
    private static int distance(BlockPos a,BlockPos b){
        return Math.abs(a.getX()-b.getX())
            +Math.abs(a.getY()-b.getY())+Math.abs(a.getZ()-b.getZ());
    }
    private static int duration(List<BlockPos> route){
        int total=0;
        for(int i=1;i<route.size();i++)
            total+=Math.max(2,(int)Math.ceil(distance(route.get(i-1),route.get(i))*0.8));
        return Math.max(3,total);
    }
    @Nullable private static BlockPos locate(ServerLevel world,CoordinatorEntity connected,
            DrillEntity drill){
        BlockPos origin=connected.getBlockPos();
        BlockPos desired=drill.lastConnectedPosition()==null
            ?drill.getBlockPos():drill.lastConnectedPosition();
        BlockPos best=null;
        double score=Double.MAX_VALUE;
        // New CC must be linearly discoverable by the existing CC (<=20) and
        // detect the drill (<=24). Search only already-loaded candidate chunks.
        for(Direction dir:Direction.values())for(int d=1;d<=20;d++){
            BlockPos target=origin.relative(dir,d);
            if(!world.hasChunkAt(target)||target.distSqr(drill.getBlockPos())>24*24)continue;
            if(!world.getBlockState(target).isAir())continue;
            if(target.equals(drill.getBlockPos())||target.equals(drill.getBlockPos().above()))continue;
            double rating=target.distSqr(desired)+d*.05;
            if(rating<score){score=rating;best=target.immutable();}
        }
        return best;
    }
    private void refund(ServerLevel world,DrillEntity drill,Job job){
        if(job.cargo==null)return;
        ItemStack rest=RigNetwork.returnToSource(world,job.cargo);
        if(!rest.isEmpty()&&!RigNetwork.returnSurplus(world,drill,rest))
            Block.popResource(world,drill.getBlockPos(),rest.copy());
    }
    void cancel(ServerLevel world,int slot,DrillEntity drill){
        Job job=jobs[slot];
        refund(world,drill,job);job.clear();
    }
    Result tick(ServerLevel world,int slot,DrillEntity drill,ComputerEntity computer){
        Job job=jobs[slot];
        if(RigTopology.joined(world,computer,drill)){
            if(job.cargo!=null)refund(world,drill,job);
            job.clear();return Result.CONNECTED;
        }
        job.ticks++;
        if(job.ticks>=MAX_WAIT_TICKS){
            refund(world,drill,job);job.clear();return Result.FAILED;
        }
        if(job.cargo==null){
            CoordinatorEntity cc=null;
            BlockPos last=drill.lastConnectedCoordinator();
            CoordinatorEntity root=RigTopology.nearest(world,computer.getBlockPos());
            if(last!=null && world.hasChunkAt(last)
                    && world.getBlockEntity(last) instanceof CoordinatorEntity old
                    && root!=null && RigNetwork.component(world,root).contains(old))cc=old;
            if(cc==null)return Result.WAITING;
            BlockPos target=locate(world,cc,drill);
            if(target==null)return Result.WAITING;
            RigNetwork.Supply reserved=RigNetwork.reserveHardware(world,cc,true);
            if(reserved==null)return Result.WAITING;
            List<BlockPos> route=new ArrayList<>(reserved.path());route.add(target);
            job.cargo=new RigNetwork.Supply(reserved.stack(),route,
                reserved.sourcePos(),reserved.sourceFace());
            job.destination=target;job.elapsed=0;
            computer.setChanged();
        }
        if(job.cargo==null)return Result.WAITING;
        job.elapsed++;
        List<BlockPos> route=job.cargo.path();
        if(job.display==null){
            Display.ItemDisplay visual=new Display.ItemDisplay(
                BuiltInRegistries.ENTITY_TYPE.getValue(
                    Identifier.withDefaultNamespace("item_display")),world);
            visual.setItemStack(job.cargo.stack().copyWithCount(1));
            visual.setItemTransform(ItemDisplayContext.GROUND);
            visual.setNoGravity(true);
            world.addFreshEntity(visual);
            job.display=visual;
        }
        int elapsed=job.elapsed;
        for(int i=1;i<route.size();i++){
            BlockPos a=route.get(i-1),b=route.get(i);
            int period=Math.max(2,(int)Math.ceil(distance(a,b)*0.8));
            if(elapsed>=period){elapsed-=period;continue;}
            double f=elapsed/(double)period;
            if(job.display!=null)
                job.display.setPos(a.getX()+.5+(b.getX()-a.getX())*f,
                    a.getY()+.55+(b.getY()-a.getY())*f,
                    a.getZ()+.5+(b.getZ()-a.getZ())*f);
            return Result.WAITING;
        }
        BlockPos target=job.destination;
        if(target!=null&&world.hasChunkAt(target)&&world.getBlockState(target).isAir()
                &&world.setBlock(target,MiningRig.COORDINATOR.defaultBlockState(),Block.UPDATE_ALL)){
            job.clear();
            RigTopology.coordinator(world,target);
            computer.setChanged();
            return RigTopology.joined(world,computer,drill)?Result.CONNECTED:Result.WAITING;
        }
        refund(world,drill,job);job.clear();
        return Result.WAITING;
    }
    void save(ValueOutput out){
        for(int i=0;i<jobs.length;i++){
            Job job=jobs[i];
            out.putInt("RepairTicks"+i,job.ticks);
            out.putBoolean("RepairActive"+i,job.cargo!=null);
            if(job.cargo==null)continue;
            out.store("RepairItem"+i,ItemStack.CODEC,job.cargo.stack());
            out.putInt("RepairElapsed"+i,job.elapsed);
            out.putLong("RepairTarget"+i,job.destination.asLong());
            out.putInt("RepairPathSize"+i,job.cargo.path().size());
            for(int n=0;n<job.cargo.path().size();n++)
                out.putLong("RepairPath"+i+"_"+n,job.cargo.path().get(n).asLong());
            if(job.cargo.sourcePos()!=null&&job.cargo.sourceFace()!=null){
                out.putLong("RepairSource"+i,job.cargo.sourcePos().asLong());
                out.putInt("RepairFace"+i,job.cargo.sourceFace().get3DDataValue());
            }
        }
    }
    void load(ValueInput in){
        for(int i=0;i<jobs.length;i++){
            Job job=jobs[i];job.clear();
            job.ticks=in.getIntOr("RepairTicks"+i,0);
            if(!in.getBooleanOr("RepairActive"+i,false))continue;
            ItemStack item=in.read("RepairItem"+i,ItemStack.CODEC).orElse(ItemStack.EMPTY);
            if(item.isEmpty())continue;
            List<BlockPos> route=new ArrayList<>();
            int size=Math.min(512,Math.max(0,in.getIntOr("RepairPathSize"+i,0)));
            for(int n=0;n<size;n++)
                route.add(BlockPos.of(in.getLongOr("RepairPath"+i+"_"+n,0)));
            BlockPos source=in.getLong("RepairSource"+i).isPresent()
                ?BlockPos.of(in.getLongOr("RepairSource"+i,0)):null;
            Direction face=source==null?null:
                Direction.from3DDataValue(in.getIntOr("RepairFace"+i,0));
            job.cargo=new RigNetwork.Supply(item,route,source,face);
            job.destination=BlockPos.of(in.getLongOr("RepairTarget"+i,0));
            job.elapsed=Math.max(0,in.getIntOr("RepairElapsed"+i,0));
        }
    }
}
