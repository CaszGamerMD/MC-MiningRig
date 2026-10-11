package dev.casz.miningrig;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Display;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jetbrains.annotations.Nullable;

/**
 * Layer phases: boundary wall -> fluid sealing -> mining.
 * Material moves from the configured storage through every CC in the actual
 * network path, with one non-pickup, ground-model display per reserved item.
 */
final class DrillSealing {
    private static final int VERIFY_TICKS=8;
    private static final int MAX_FILL_ATTEMPTS=24;
    private final LinkedHashSet<BlockPos> fluidSpots=new LinkedHashSet<>();
    private final LinkedHashSet<BlockPos> finishedWalls=new LinkedHashSet<>();
    @Nullable private BlockPos pending;
    private boolean pendingWall;
    private int wait,attempts;
    private boolean wallFallback,wallFinished;
    private int cautionTicks;
    @Nullable private RigNetwork.Supply delivery;
    @Nullable private BlockPos deliveryTarget;
    private boolean deliveryWall;
    private int flightTicks;
    @Nullable private Display.ItemDisplay visual;

    boolean warning(){return !fluidSpots.isEmpty()||pending!=null||cautionTicks>0;}
    boolean fallback(){return wallFallback;}
    void retryWallSource(ServerLevel world,DrillEntity drill){
        if(wallFallback && RigNetwork.hasAvailable(world,drill,true))wallFallback=false;
    }
    static boolean contains(ComputerEntity computer,int x,int z,boolean expanded){
        int r=computer.radius()+(expanded?1:0);
        return computer.circle()?x*x+z*z<=r*r:Math.max(Math.abs(x),Math.abs(z))<=r;
    }
    static boolean immune(BlockState state){
        return state.is(MiningRig.DRILL)||state.is(MiningRig.DRILL_UPPER)
            ||state.is(MiningRig.COMPUTER)||state.is(MiningRig.COMPUTER_UPPER)
            ||state.is(MiningRig.COORDINATOR)||state.is(MiningRig.TABLET_BLOCK);
    }
    private static boolean solid(ServerLevel world,BlockPos p){
        BlockState state=world.getBlockState(p);
        return !state.isAir()&&state.getFluidState().isEmpty()&&!state.canBeReplaced();
    }
    private void discardVisual(){
        if(visual!=null)visual.discard();
        visual=null;
    }
    void dropCargo(ServerLevel world,BlockPos at){
        if(delivery!=null&&!delivery.stack().isEmpty())
            Block.popResource(world,at,delivery.stack().copy());
        delivery=null;deliveryTarget=null;discardVisual();
    }
    void copyTo(DrillSealing next){
        next.fluidSpots.clear();next.fluidSpots.addAll(fluidSpots);
        next.finishedWalls.clear();next.finishedWalls.addAll(finishedWalls);
        next.pending=pending;next.pendingWall=pendingWall;
        next.wait=wait;next.attempts=attempts;
        next.wallFallback=wallFallback;next.wallFinished=wallFinished;
        next.cautionTicks=cautionTicks;
        next.delivery=delivery;next.deliveryTarget=deliveryTarget;
        next.deliveryWall=deliveryWall;next.flightTicks=flightTicks;
        next.visual=visual;
        delivery=null;deliveryTarget=null;visual=null; // ownership moves, never duplicates
    }
    void nextLayer(){
        fluidSpots.clear();finishedWalls.clear();pending=null;pendingWall=false;
        wait=0;attempts=0;wallFinished=false;
    }
    private void scanFluids(ServerLevel world,DrillEntity drill,ComputerEntity computer){
        int r=computer.radius()+1;
        BlockPos base=drill.getBlockPos();
        for(int dx=-r;dx<=r;dx++)for(int dz=-r;dz<=r;dz++){
            if(!contains(computer,dx,dz,true))continue;
            BlockPos p=base.offset(dx,-1,dz);
            if(world.hasChunkAt(p)&&!world.getFluidState(p).isEmpty()){
                fluidSpots.add(p);
                cautionTicks=200;
            }
        }
    }
    private static int distance(BlockPos a,BlockPos b){
        return Math.abs(a.getX()-b.getX())+Math.abs(a.getY()-b.getY())+
            Math.abs(a.getZ()-b.getZ());
    }
    private static int legTicks(BlockPos a,BlockPos b){
        return Math.max(3,(int)Math.ceil(distance(a,b)*2.5));
    }
    private void createVisual(ServerLevel world){
        if(delivery==null||visual!=null||delivery.path().isEmpty())return;
        Display.ItemDisplay display=new Display.ItemDisplay(
            net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getValue(
                net.minecraft.resources.Identifier.withDefaultNamespace("item_display")),world);
        display.setItemStack(delivery.stack().copyWithCount(1));
        display.setItemTransform(ItemDisplayContext.GROUND);
        display.setTransformation(new com.mojang.math.Transformation(
            new org.joml.Vector3f(0,0,0),new org.joml.Quaternionf(),
            new org.joml.Vector3f(.55f,.55f,.55f),new org.joml.Quaternionf()));
        display.setNoGravity(true);
        BlockPos p=delivery.path().get(0);
        display.setPos(p.getX()+.5,p.getY()+.55,p.getZ()+.5);
        world.addFreshEntity(display);
        visual=display;
    }
    private void refund(ServerLevel world,DrillEntity drill,ItemStack stack){
        if(!drill.receive(List.of(stack)))
            Block.popResource(world,drill.getBlockPos(),stack.copy());
    }
    /** Returns RUNNING while moving and while waiting for gravity to settle. */
    private ComputerEntity.Status advanceDelivery(ServerLevel world,DrillEntity drill){
        if(delivery==null)return null;
        createVisual(world);
        flightTicks++;
        int elapsed=flightTicks, segment=0,local=0;
        List<BlockPos> route=delivery.path();
        for(int i=1;i<route.size();i++){
            int leg=legTicks(route.get(i-1),route.get(i));
            if(elapsed<leg){segment=i;local=elapsed;break;}
            elapsed-=leg;
        }
        if(segment>0){
            BlockPos a=route.get(segment-1),b=route.get(segment);
            float progress=local/(float)legTicks(a,b);
            if(visual!=null)visual.setPos(
                a.getX()+.5+(b.getX()-a.getX())*progress,
                a.getY()+.55+(b.getY()-a.getY())*progress
                    +.1*Math.sin(Math.PI*progress),
                a.getZ()+.5+(b.getZ()-a.getZ())*progress);
            return ComputerEntity.Status.RUNNING;
        }
        BlockPos target=deliveryTarget;
        ItemStack material=delivery.stack();
        discardVisual();
        delivery=null;deliveryTarget=null;
        if(target==null || !(material.getItem() instanceof BlockItem item)){
            refund(world,drill,material);return ComputerEntity.Status.BLOCKED;
        }
        BlockState existing=world.getBlockState(target);
        if(immune(existing)||solid(world,target)){
            // Another machine or solid block arrived while the cargo traveled.
            refund(world,drill,material);
            pending=target;pendingWall=deliveryWall;wait=0;attempts=0;
            return ComputerEntity.Status.RUNNING;
        }
        if(attempts>=MAX_FILL_ATTEMPTS){
            refund(world,drill,material);
            return ComputerEntity.Status.FILL_FAILED;
        }
        if(!world.setBlock(target,item.getBlock().defaultBlockState(),Block.UPDATE_ALL)){
            refund(world,drill,material);
            return ComputerEntity.Status.BLOCKED;
        }
        attempts++;
        pending=target;pendingWall=deliveryWall;wait=VERIFY_TICKS;
        drill.placementWear(world);
        drill.setChanged();
        return ComputerEntity.Status.RUNNING;
    }
    private ComputerEntity.Status beginPlacement(ServerLevel world,DrillEntity drill,BlockPos p,boolean wallMaterial){
        if(attempts>=MAX_FILL_ATTEMPTS)return ComputerEntity.Status.FILL_FAILED;
        if(!drill.placementReady(world))return ComputerEntity.Status.RUNNING;
        RigNetwork.Supply result=RigNetwork.reserve(world,drill,wallMaterial);
        if(result==null)return wallMaterial?ComputerEntity.Status.OUT_OF_WALLS:
            ComputerEntity.Status.OUT_OF_FILLER;
        delivery=result;deliveryTarget=p.immutable();deliveryWall=wallMaterial;flightTicks=0;
        createVisual(world);drill.setChanged();
        return ComputerEntity.Status.RUNNING;
    }
    private void markDone(BlockPos p,boolean walls){
        if(walls)finishedWalls.add(p);
        else fluidSpots.remove(p);
        attempts=0;pending=null;wait=0;
    }
    private ComputerEntity.Status verify(ServerLevel world,DrillEntity drill){
        if(pending==null)return null;
        if(wait>0){wait--;return ComputerEntity.Status.RUNNING;}
        if(solid(world,pending)){
            markDone(pending,pendingWall);drill.setChanged();
            return null;
        }
        if(attempts>=MAX_FILL_ATTEMPTS)return ComputerEntity.Status.FILL_FAILED;
        return beginPlacement(world,drill,pending,pendingWall);
    }
    /**
     * FIRST step of each layer. Air seals voids. Replace excavates the outside
     * ring using proper bits and installs wall material before core mining.
     */
    ComputerEntity.Status wallStep(ServerLevel world,DrillEntity drill,ComputerEntity computer){
        int setting=computer.wallMode();
        if(setting==0||wallFinished)return null;
        if(delivery!=null)return advanceDelivery(world,drill);
        if(pending!=null&&pendingWall){
            ComputerEntity.Status check=verify(world,drill);
            if(check==ComputerEntity.Status.OUT_OF_WALLS &&
                    RigNetwork.wallsCanContinue(world,drill)){
                wallFallback=true;return beginPlacement(world,drill,pending,false);
            }
            if(check!=null)return check;
        }
        boolean replace=setting==2&&!wallFallback;
        int r=computer.radius()+1;
        BlockPos origin=drill.getBlockPos();
        for(int dx=-r;dx<=r;dx++)for(int dz=-r;dz<=r;dz++){
            if(!contains(computer,dx,dz,true)||contains(computer,dx,dz,false))continue;
            BlockPos p=origin.offset(dx,-1,dz);
            if(finishedWalls.contains(p))continue;
            if(!world.hasChunkAt(p))return ComputerEntity.Status.BLOCKED;
            BlockState state=world.getBlockState(p);
            if(immune(state)){finishedWalls.add(p);continue;}
            if(replace && !state.isAir() && state.getFluidState().isEmpty()){
                ComputerEntity.Status mining=drill.mineWall(world,p);
                if(mining!=null)return mining;
            }else if(!replace && solid(world,p)){
                finishedWalls.add(p);continue;
            }
            if(!replace && world.getBlockState(p).isAir()==false &&
                    world.getFluidState(p).isEmpty()){
                finishedWalls.add(p);continue;
            }
            ComputerEntity.Status send=beginPlacement(world,drill,p,replace);
            if(send==ComputerEntity.Status.OUT_OF_WALLS&&
                    RigNetwork.wallsCanContinue(world,drill)){
                wallFallback=true;
                return beginPlacement(world,drill,p,false);
            }
            return send;
        }
        wallFinished=true;
        drill.setChanged();
        return null;
    }
    /** SECOND step: clear fluids after the wall pass but before mining. */
    ComputerEntity.Status fluidStep(ServerLevel world,DrillEntity drill,ComputerEntity computer){
        if(delivery!=null)return advanceDelivery(world,drill);
        scanFluids(world,drill,computer);
        if(pending!=null && !pendingWall){
            ComputerEntity.Status checked=verify(world,drill);
            if(checked!=null)return checked;
        }
        for(Iterator<BlockPos> it=fluidSpots.iterator();it.hasNext();){
            BlockPos p=it.next();
            if(!world.hasChunkAt(p))return ComputerEntity.Status.BLOCKED;
            if(solid(world,p)){it.remove();continue;}
            return beginPlacement(world,drill,p,false);
        }
        if(cautionTicks>0)cautionTicks--;
        return null;
    }
    void save(ValueOutput out){
        out.putInt("FluidSpots",fluidSpots.size());
        int i=0;for(BlockPos p:fluidSpots)out.putLong("FluidSpot"+i++,p.asLong());
        out.putInt("FinishedWalls",finishedWalls.size());
        i=0;for(BlockPos p:finishedWalls)out.putLong("WallPos"+i++,p.asLong());
        out.putBoolean("WallFinished",wallFinished);
        out.putBoolean("FillPending",pending!=null);
        if(pending!=null)out.putLong("FillPosition",pending.asLong());
        out.putInt("FillType",pendingWall?2:1);out.putInt("FillWait",wait);
        out.putInt("FillAttempts",attempts);
        out.putBoolean("WallFallback",wallFallback);
        out.putInt("CautionTicks",cautionTicks);
        out.putBoolean("HasSupply",delivery!=null);
        if(delivery!=null){
            out.store("SupplyItem",ItemStack.CODEC,delivery.stack());
            out.putInt("SupplyPathLength",delivery.path().size());
            for(int j=0;j<delivery.path().size();j++)
                out.putLong("SupplyHop"+j,delivery.path().get(j).asLong());
            out.putBoolean("SupplyIsWall",deliveryWall);
            out.putLong("SupplyTarget",deliveryTarget==null?0:deliveryTarget.asLong());
            out.putInt("SupplyTick",flightTicks);
        }
    }
    void load(ValueInput in){
        discardVisual();
        fluidSpots.clear();
        for(int i=0;i<Math.min(1024,in.getIntOr("FluidSpots",0));i++)
            fluidSpots.add(BlockPos.of(in.getLongOr("FluidSpot"+i,0)));
        finishedWalls.clear();
        for(int i=0;i<Math.min(4096,in.getIntOr("FinishedWalls",0));i++)
            finishedWalls.add(BlockPos.of(in.getLongOr("WallPos"+i,0)));
        wallFinished=in.getBooleanOr("WallFinished",false);
        pending=in.getBooleanOr("FillPending",false)
            ?BlockPos.of(in.getLongOr("FillPosition",0)):null;
        pendingWall=in.getIntOr("FillType",0)==2;
        wait=Math.max(0,in.getIntOr("FillWait",0));
        attempts=Math.max(0,in.getIntOr("FillAttempts",0));
        wallFallback=in.getBooleanOr("WallFallback",false);
        cautionTicks=Math.max(0,in.getIntOr("CautionTicks",0));
        delivery=null;deliveryTarget=null;
        if(in.getBooleanOr("HasSupply",false)){
            ItemStack stack=in.read("SupplyItem",ItemStack.CODEC).orElse(ItemStack.EMPTY);
            int length=Math.max(0,Math.min(256,in.getIntOr("SupplyPathLength",0)));
            List<BlockPos> route=new ArrayList<>();
            for(int j=0;j<length;j++)route.add(BlockPos.of(in.getLongOr("SupplyHop"+j,0)));
            if(!stack.isEmpty()&&route.size()>=2){
                delivery=new RigNetwork.Supply(stack,route);
                deliveryWall=in.getBooleanOr("SupplyIsWall",false);
                deliveryTarget=BlockPos.of(in.getLongOr("SupplyTarget",0));
                flightTicks=Math.max(0,in.getIntOr("SupplyTick",0));
            }
        }
    }
}
