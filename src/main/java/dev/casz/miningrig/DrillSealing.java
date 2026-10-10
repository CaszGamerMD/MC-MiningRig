package dev.casz.miningrig;

import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * Active layer's sealing work. The drill never mines while a fluid source is
 * present in its footprint/boundary. Each placed block must survive eight
 * game ticks (falling sand/gravel, flowing fluids) before work resumes.
 */
final class DrillSealing {
    private final LinkedHashSet<BlockPos> fluidSpots=new LinkedHashSet<>();
    private BlockPos pending;
    private int pendingType; // 1 filler, 2 wall
    private int wait;
    private boolean wallFallback;
    private int cautionTicks;
    boolean warning(){return !fluidSpots.isEmpty()||pending!=null||cautionTicks>0;}
    boolean fallback(){return wallFallback;}
    void retryWallSource(ServerLevel world,DrillEntity drill){
        // When a continuing Walls input is refilled, restore ALWAYS on
        // the next untouched layer rather than staying in AIR forever.
        if(wallFallback && RigNetwork.hasAvailable(world,drill,true))
            wallFallback=false;
    }
    static boolean contains(ComputerEntity computer,int x,int z,boolean expanded){
        int radius=computer.radius()+(expanded?1:0);
        return computer.circle()?x*x+z*z<=radius*radius:
            Math.max(Math.abs(x),Math.abs(z))<=radius;
    }
    static boolean immune(BlockState state){
        return state.is(MiningRig.DRILL) || state.is(MiningRig.DRILL_UPPER)
            || state.is(MiningRig.COMPUTER) || state.is(MiningRig.COMPUTER_UPPER)
            || state.is(MiningRig.COORDINATOR);
    }
    void copyTo(DrillSealing target){
        target.fluidSpots.clear();target.fluidSpots.addAll(fluidSpots);
        target.pending=pending;target.pendingType=pendingType;target.wait=wait;
        target.wallFallback=wallFallback;target.cautionTicks=cautionTicks;
    }
    void nextLayer(){
        fluidSpots.clear();pending=null;pendingType=0;wait=0;
    }
    private void scan(ServerLevel world,DrillEntity drill,ComputerEntity computer){
        int r=computer.radius()+1;
        BlockPos base=drill.getBlockPos();
        for(int x=-r;x<=r;x++)for(int z=-r;z<=r;z++){
            if(!contains(computer,x,z,true))continue;
            BlockPos p=base.offset(x,-1,z);
            if(world.hasChunkAt(p)&&!world.getFluidState(p).isEmpty()){
                fluidSpots.add(p);cautionTicks=200;
            }
        }
    }
    private boolean solid(ServerLevel world,BlockPos p){
        BlockState at=world.getBlockState(p);
        return !at.isAir() && at.getFluidState().isEmpty()
            && !at.canBeReplaced();
    }
    private boolean put(ServerLevel world,DrillEntity drill,BlockPos p,boolean walls){
        List<ItemStack> supply=RigNetwork.request(world,drill,walls,1);
        if(supply.isEmpty())return false;
        if(!(supply.get(0).getItem() instanceof BlockItem blockItem))return false;
        BlockState material=blockItem.getBlock().defaultBlockState();
        if(!world.setBlock(p,material,Block.UPDATE_ALL)){
            // Never void material if the world refuses to place it.
            drill.receive(List.of(supply.get(0)));
            return false;
        }
        pending=p.immutable();pendingType=walls?2:1;wait=8;
        drill.setChanged();
        return true;
    }
    private ComputerEntity.Status verify(ServerLevel world,DrillEntity drill,boolean walls){
        if(wait>0){wait--;return ComputerEntity.Status.RUNNING;}
        if(solid(world,pending)){
            fluidSpots.remove(pending);pending=null;pendingType=0;
            drill.setChanged();
            return null;
        }
        if(put(world,drill,pending,walls))return ComputerEntity.Status.RUNNING;
        return walls?ComputerEntity.Status.OUT_OF_WALLS:ComputerEntity.Status.OUT_OF_FILLER;
    }
    ComputerEntity.Status beforeMining(ServerLevel world,DrillEntity drill,ComputerEntity computer){
        scan(world,drill,computer);
        if(pending!=null && pendingType==1){
            ComputerEntity.Status v=verify(world,drill,false);
            if(v!=null)return v;
        }
        for(Iterator<BlockPos> it=fluidSpots.iterator();it.hasNext();){
            BlockPos p=it.next();
            if(!world.hasChunkAt(p))return ComputerEntity.Status.BLOCKED;
            if(solid(world,p)){it.remove();continue;}
            if(!put(world,drill,p,false))return ComputerEntity.Status.OUT_OF_FILLER;
            return ComputerEntity.Status.RUNNING;
        }
        if(cautionTicks>0)cautionTicks--;
        return null;
    }
    ComputerEntity.Status afterMining(ServerLevel world,DrillEntity drill,ComputerEntity computer){
        int setting=computer.wallMode();
        if(setting==0)return null;
        if(pending!=null && pendingType==2){
            boolean walls=setting==2&&!wallFallback;
            ComputerEntity.Status check=verify(world,drill,walls);
            if(check==ComputerEntity.Status.OUT_OF_WALLS && RigNetwork.wallsCanContinue(world,drill)){
                wallFallback=true;
                if(put(world,drill,pending,false))return ComputerEntity.Status.RUNNING;
                return ComputerEntity.Status.OUT_OF_FILLER;
            }
            if(check!=null)return check;
        }
        int r=computer.radius()+1;
        BlockPos base=drill.getBlockPos();
        for(int x=-r;x<=r;x++)for(int z=-r;z<=r;z++){
            if(!contains(computer,x,z,true)||contains(computer,x,z,false))continue;
            BlockPos p=base.offset(x,-1,z);
            if(!world.hasChunkAt(p))return ComputerEntity.Status.BLOCKED;
            BlockState at=world.getBlockState(p);
            if(immune(at)||solid(world,p))continue;
            boolean walls=setting==2&&!wallFallback;
            if(!put(world,drill,p,walls)){
                if(walls&&RigNetwork.wallsCanContinue(world,drill)){
                    wallFallback=true;
                    if(!put(world,drill,p,false))return ComputerEntity.Status.OUT_OF_FILLER;
                }else return walls?ComputerEntity.Status.OUT_OF_WALLS:ComputerEntity.Status.OUT_OF_FILLER;
            }
            return ComputerEntity.Status.RUNNING;
        }
        return null;
    }
    void save(ValueOutput out){
        out.putInt("FluidSpots",fluidSpots.size());
        int i=0;for(BlockPos p:fluidSpots)out.putLong("FluidSpot"+i++,p.asLong());
        out.putBoolean("FillPending",pending!=null);
        if(pending!=null)out.putLong("FillPosition",pending.asLong());
        out.putInt("FillType",pendingType);out.putInt("FillWait",wait);
        out.putBoolean("WallFallback",wallFallback);out.putInt("CautionTicks",cautionTicks);
    }
    void load(ValueInput in){
        fluidSpots.clear();
        for(int i=0;i<Math.min(1024,in.getIntOr("FluidSpots",0));i++)
            fluidSpots.add(BlockPos.of(in.getLongOr("FluidSpot"+i,0)));
        pending=in.getBooleanOr("FillPending",false)
            ?BlockPos.of(in.getLongOr("FillPosition",0)):null;
        pendingType=in.getIntOr("FillType",0);
        wait=Math.max(0,in.getIntOr("FillWait",0));
        wallFallback=in.getBooleanOr("WallFallback",false);
        cautionTicks=Math.max(0,in.getIntOr("CautionTicks",0));
    }
}
