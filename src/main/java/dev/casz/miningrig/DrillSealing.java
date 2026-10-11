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
import net.minecraft.world.level.block.FallingBlock;
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
    private static final int DEFAULT_FILL_ATTEMPTS=24;
    private final LinkedHashSet<BlockPos> fluidSpots=new LinkedHashSet<>();
    private final LinkedHashSet<BlockPos> finishedWalls=new LinkedHashSet<>();
    @Nullable private BlockPos pending;
    private boolean pendingWall;
    private int wait,attempts;
    // Attempts MUST belong to one physical target, never the layer as a
    // whole. A sand/gravel column may be much deeper than 24 blocks.
    @Nullable private BlockPos attemptTarget;
    private int allowedAttempts=DEFAULT_FILL_ATTEMPTS;
    private int configuredLimit=DEFAULT_FILL_ATTEMPTS;
    private int gravityDropDepth;
    private boolean wallFallback,wallFinished,wallSourceChecked;
    private int cautionTicks;
    private final WallSupplyPipeline walls=new WallSupplyPipeline("Replace",true,64);
    private final WallSupplyPipeline filler=new WallSupplyPipeline("Fluid",false,64);
    private boolean replaceCleared,fluidMode,legacyWallPhase;
    private int wallPositionsRemaining;
    private boolean airWallNeeded;
    @Nullable private RigNetwork.Supply delivery;
    @Nullable private BlockPos deliveryTarget;
    private boolean deliveryWall,pendingSourceWalls;
    private int flightTicks;
    @Nullable private Display.ItemDisplay visual;

    boolean warning(){return !fluidSpots.isEmpty()||pending!=null||cautionTicks>0;}
    boolean fluidFilling(){return fluidMode;}
    boolean needsWallSupply(){return !wallFinished;}
    boolean needsAirFiller(){return !wallFinished && airWallNeeded;}
    int wallDepotTarget(){return 192;}
    int supplyStored(boolean wall){return (wall?walls:filler).bundleCount();}
    ItemStack supplyItem(boolean wall,int slot){return (wall?walls:filler).bundleItem(slot);}
    int supplyRoom(boolean wall,int slot){return (wall?walls:filler).bundleRoom(slot);}
    ItemStack removeSupply(boolean wall,int slot,int count){
        return (wall?walls:filler).removeBundleItem(slot,count);
    }
    void setSupply(boolean wall,int slot,ItemStack stack){
        (wall?walls:filler).setBundleItem(slot,stack);
    }
    /**
     * Material reservations never stop between wall/fluid phases: these are
     * permanent 64-item running buffers, independent of the 64 mined drops.
     */
    void maintainSupply(ServerLevel world,DrillEntity drill,ComputerEntity computer){
        filler.tick(world,drill);
        filler.request(world,drill,64);
        walls.tick(world,drill);
        if(computer.wallMode()==2)walls.request(world,drill,64);
    }
    /** Pausing returns drill stock to the original bins, then the output CC. */
    void pauseSupply(ServerLevel world,DrillEntity drill){
        walls.flush(world,drill);
        filler.flush(world,drill);
    }
    boolean fallback(){return wallFallback;}
    /** Saved failing coordinate; do not report a generic error with no target. */
    @Nullable BlockPos stuckPosition(){
        return attempts>=allowedAttempts?attemptTarget:null;
    }
    int failedAttempts(){return attempts;}
    int attemptLimit(){return allowedAttempts;}
    void setTryLimit(int limit){
        configuredLimit=Math.max(ComputerEntity.MIN_FILL_TRIES,
            Math.min(ComputerEntity.MAX_FILL_TRIES,limit));
        allowedAttempts=configuredLimit;
    }
    int fallDepth(){return gravityDropDepth;}
    private void target(ServerLevel world,BlockPos at,@Nullable ItemStack stack){
        if(attemptTarget==null || !attemptTarget.equals(at)){
            attemptTarget=at.immutable();
            attempts=0;
            allowedAttempts=configuredLimit;
            gravityDropDepth=0;
        }
        if(stack==null || !(stack.getItem() instanceof BlockItem item)
                || !(item.getBlock() instanceof FallingBlock))return;
        // Sand/gravel can drop through an ocean shaft far deeper than 24.
        // Count the potentially unsupported positions immediately beneath
        // this exact coordinate. Do not treat ordinary stacked falling
        // blocks as a "failed 24 attempts" job.
        int gap=0;
        for(int y=at.getY()-1;y>=world.getMinY();y--){
            BlockPos below=new BlockPos(at.getX(),y,at.getZ());
            if(!world.hasChunkAt(below))break;
            BlockState state=world.getBlockState(below);
            if(!state.isAir()&&state.getFluidState().isEmpty()
                    && !state.canBeReplaced())break;
            gap++;
        }
        gravityDropDepth=Math.max(gravityDropDepth,gap);
        // A few extra tries beyond the physical drop height are useful for
        // flowing water and entity settling, but the check is still finite.
        // The operator chooses the attempt ceiling in the Mining Computer.
        // Still measure gravity depth to extend settling time, but do not
        // override the configured maximum with an automatic retry allowance.
    }
    private int settleTicks(ItemStack item){
        return item.getItem() instanceof BlockItem block
            && block.getBlock() instanceof FallingBlock
            ?VERIFY_TICKS+Math.min(40,gravityDropDepth/3)
            :VERIFY_TICKS;
    }
    private boolean failed(){return attempts>=allowedAttempts;}

    /**
     * Restart is an explicit retry of the SAME target, not a new excavation.
     * Previously 'attempts=24' persisted forever and pressing Start/Stop
     * immediately halted with the same error without placing any blocks.
     */
    void retryFailedFill(ServerLevel world,DrillEntity drill){
        if(!failed())return;
        attempts=0;
        if(pending!=null && solid(world,pending)){
            markDone(pending,pendingWall);
        }else{
            // Keep pending position and reserve/ready stock: don't skip a wet
            // or now-air coordinate just because the fluid has flowed away.
            wait=0;
        }
        drill.setChanged();
    }
    /** A real environmental repair (stable solid at the failed target) is
     * enough to auto-resume; an unchanged fluid/air spot needs user retry. */
    boolean canAutoRecover(ServerLevel world){
        return failed() && pending!=null
            && world.hasChunkAt(pending) && solid(world,pending);
    }
    void retryWallSource(ServerLevel world,DrillEntity drill){
        if(wallSourceChecked)return;
        wallSourceChecked=true;
        // Check once on each new layer, not again for every tick during the
        // wall pass; Continue mode stays in Air fallback until next layer.
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
        if(world.getBlockEntity(at) instanceof DrillEntity drill){
            walls.flush(world,drill);filler.flush(world,drill);
        }
    }
    void copyTo(DrillSealing next){
        next.fluidSpots.clear();next.fluidSpots.addAll(fluidSpots);
        next.finishedWalls.clear();next.finishedWalls.addAll(finishedWalls);
        next.pending=pending;next.pendingWall=pendingWall;
        next.wait=wait;next.attempts=attempts;
        next.attemptTarget=attemptTarget;
        next.allowedAttempts=allowedAttempts;
        next.configuredLimit=configuredLimit;
        next.gravityDropDepth=gravityDropDepth;
        next.wallFallback=wallFallback;next.wallFinished=wallFinished;
        next.wallSourceChecked=wallSourceChecked;
        next.cautionTicks=cautionTicks;
        next.replaceCleared=replaceCleared;next.fluidMode=fluidMode;
        next.wallPositionsRemaining=wallPositionsRemaining;
        next.airWallNeeded=airWallNeeded;
        next.legacyWallPhase=legacyWallPhase;
        walls.moveTo(next.walls);filler.moveTo(next.filler);
        next.delivery=delivery;next.deliveryTarget=deliveryTarget;
        next.deliveryWall=deliveryWall;next.pendingSourceWalls=pendingSourceWalls;
        next.flightTicks=flightTicks;
        next.visual=visual;
        delivery=null;deliveryTarget=null;visual=null; // ownership moves, never duplicates
    }
    void resetForPlacement(ServerLevel world,BlockPos at){
        dropCargo(world,at);
        nextLayer();
        wallFallback=false;wallSourceChecked=false;wallFinished=false;
        cautionTicks=0;pending=null;pendingWall=false;
        replaceCleared=false;fluidMode=false;legacyWallPhase=false;
        wallPositionsRemaining=0;airWallNeeded=false;
        deliveryWall=false;pendingSourceWalls=false;flightTicks=0;attempts=0;wait=0;
        attemptTarget=null;allowedAttempts=configuredLimit;gravityDropDepth=0;
        discardVisual();
    }
    void nextLayer(){
        fluidSpots.clear();finishedWalls.clear();pending=null;pendingWall=false;
        wait=0;attempts=0;wallFinished=false;wallSourceChecked=false;
        attemptTarget=null;allowedAttempts=configuredLimit;gravityDropDepth=0;
        replaceCleared=false;fluidMode=false;
        wallPositionsRemaining=0;airWallNeeded=false;
    }
    private boolean outsideHole(BlockPos pos,DrillEntity drill,ComputerEntity computer){
        return !contains(computer,pos.getX()-drill.getBlockPos().getX(),
            pos.getZ()-drill.getBlockPos().getZ(),false);
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
        pendingSourceWalls=deliveryWall;
        discardVisual();
        delivery=null;deliveryTarget=null;
        if(target==null || !(material.getItem() instanceof BlockItem item)){
            refund(world,drill,material);return ComputerEntity.Status.BLOCKED;
        }
        BlockState existing=world.getBlockState(target);
        if(immune(existing)||solid(world,target)){
            // Another machine or solid block arrived while the cargo traveled.
            refund(world,drill,material);
            pending=target;pendingWall=legacyWallPhase;wait=0;attempts=0;
            return ComputerEntity.Status.RUNNING;
        }
        target(world,target,material);
        if(failed()){
            refund(world,drill,material);
            return ComputerEntity.Status.FILL_FAILED;
        }
        if(!world.setBlock(target,item.getBlock().defaultBlockState(),Block.UPDATE_ALL)){
            refund(world,drill,material);
            return ComputerEntity.Status.BLOCKED;
        }
        attempts++;
        pending=target;pendingWall=legacyWallPhase;wait=settleTicks(material);
        drill.placementWear(world);
        drill.setChanged();
        return ComputerEntity.Status.RUNNING;
    }
    private ComputerEntity.Status beginPlacement(ServerLevel world,DrillEntity drill,BlockPos p,boolean wallMaterial){
        target(world,p,null);
        if(failed())return ComputerEntity.Status.FILL_FAILED;
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
        attemptTarget=null;allowedAttempts=configuredLimit;gravityDropDepth=0;
    }
    private ComputerEntity.Status verify(ServerLevel world,DrillEntity drill){
        if(pending==null)return null;
        if(wait>0){wait--;return ComputerEntity.Status.RUNNING;}
        if(solid(world,pending)){
            markDone(pending,pendingWall);drill.setChanged();
            return null;
        }
        if(failed())return ComputerEntity.Status.FILL_FAILED;
        return beginPlacement(world,drill,pending,pendingSourceWalls);
    }
    private ComputerEntity.Status applyFromStock(ServerLevel world,DrillEntity drill,
            WallSupplyPipeline stock,BlockPos target,boolean wall){
        if(!drill.placementReady(world))return ComputerEntity.Status.RUNNING;
        ItemStack available=stock.peek(world,stock==walls&&drill.randomWalls());
        if(available.isEmpty())return ComputerEntity.Status.RUNNING;
        if(!(available.getItem() instanceof BlockItem item))return ComputerEntity.Status.BLOCKED;
        target(world,target,available);
        if(failed())return ComputerEntity.Status.FILL_FAILED;
        BlockState state=world.getBlockState(target);
        if(immune(state))return ComputerEntity.Status.BLOCKED;
        if(solid(world,target)){
            // Water finished draining or someone repaired the spot during
            // transit. Treat the stable block as success, not 'blocked'.
            markDone(target,wall);
            drill.setChanged();
            return ComputerEntity.Status.RUNNING;
        }
        if(!world.setBlock(target,item.getBlock().defaultBlockState(),Block.UPDATE_ALL))
            return ComputerEntity.Status.BLOCKED;
        stock.consume();
        pending=target.immutable();pendingWall=wall;
        pendingSourceWalls=stock==walls;
        wait=settleTicks(available);attempts++;
        drill.placementWear(world);drill.setChanged();
        return ComputerEntity.Status.RUNNING;
    }
    private ComputerEntity.Status verifyStock(ServerLevel world,DrillEntity drill){
        if(pending==null)return null;
        if(wait>0){wait--;return ComputerEntity.Status.RUNNING;}
        if(solid(world,pending)){
            markDone(pending,pendingWall);drill.setChanged();return null;
        }
        if(failed())return ComputerEntity.Status.FILL_FAILED;
        // Retry EXACTLY this position, rather than selecting another fluid
        // cell from a newly reordered LinkedHashSet and carrying its 24 tries.
        WallSupplyPipeline stock=pendingSourceWalls?walls:filler;
        if(!stock.hasSupply()){
            if(stock.hasFlights() || RigNetwork.supplyDepotPending(world,drill,pendingSourceWalls))
                return ComputerEntity.Status.RUNNING;
            if(pendingSourceWalls && RigNetwork.wallsCanContinue(world,drill)){
                wallFallback=true;
                pendingSourceWalls=false;
                return ComputerEntity.Status.RUNNING;
            }
            return pendingSourceWalls?ComputerEntity.Status.OUT_OF_WALLS:
                ComputerEntity.Status.OUT_OF_FILLER;
        }
        return applyFromStock(world,drill,stock,pending,pendingWall);
    }
    private List<BlockPos> ring(DrillEntity drill,ComputerEntity computer){
        List<BlockPos> positions=new ArrayList<>();
        int r=computer.radius()+1;
        BlockPos origin=drill.getBlockPos();
        for(int dx=-r;dx<=r;dx++)for(int dz=-r;dz<=r;dz++)
            if(contains(computer,dx,dz,true)&&!contains(computer,dx,dz,false))
                positions.add(origin.offset(dx,-1,dz));
        return positions;
    }
    /**
     * REPLACE PHASE 1: finish mining EVERY solid boundary position before
     * requesting a single wall-material packet or placing a wall block.
     * REPLACE PHASE 2: count all vacant wall positions, then ask CCs for that
     * quantity in advance as concurrently moving three-block packets.
     */
    private ComputerEntity.Status replaceWall(ServerLevel world,DrillEntity drill,
                                              ComputerEntity computer){
        List<BlockPos> perimeter=ring(drill,computer);
        // Stage the required Wall blocks WHILE the ring is being excavated.
        // Never pull beyond the unbuilt positions already covered by stock.
        wallPositionsRemaining=0;
        for(BlockPos at:perimeter)
            if(!finishedWalls.contains(at)&&!immune(world.getBlockState(at)))
                wallPositionsRemaining++;
        if(!replaceCleared){
            for(BlockPos at:perimeter){
                if(!world.hasChunkAt(at))return ComputerEntity.Status.BLOCKED;
                if(finishedWalls.contains(at))continue;
                BlockState state=world.getBlockState(at);
                if(immune(state)){finishedWalls.add(at);continue;}
                if(!state.isAir()&&state.getFluidState().isEmpty()){
                    ComputerEntity.Status mined=drill.mineWall(world,at);
                    if(mined!=null)return mined;
                    return ComputerEntity.Status.RUNNING;
                }
            }
            int count=0;
            for(BlockPos at:perimeter)
                if(!finishedWalls.contains(at)&&!immune(world.getBlockState(at)))count++;
            replaceCleared=true;
            walls.plan(count);
            drill.setChanged();
        }
        walls.tick(world,drill);
        if(pending!=null&&pendingWall){
            ComputerEntity.Status verified=verifyStock(world,drill);
            if(verified!=null)return verified;
        }
        int remaining=0;BlockPos next=null;
        for(BlockPos at:perimeter){
            if(finishedWalls.contains(at))continue;
            if(!world.hasChunkAt(at))return ComputerEntity.Status.BLOCKED;
            BlockState state=world.getBlockState(at);
            if(immune(state)){finishedWalls.add(at);continue;}
            remaining++;
            if(next==null)next=at;
            if(solid(world,at)){
                // Dynamic/falling blocks made a previously cleared area solid:
                // return to clearing, preserving all already reserved stock.
                replaceCleared=false;
                return ComputerEntity.Status.RUNNING;
            }
        }
        wallPositionsRemaining=remaining;
        if(remaining==0){
            replaceCleared=false;wallFinished=true;drill.setChanged();
            return null;
        }
        walls.request(world,drill,64);
        if(!walls.hasSupply()){
            if(walls.hasFlights() || RigNetwork.supplyDepotPending(world,drill,true))
                return ComputerEntity.Status.RUNNING;
            if(RigNetwork.wallsCanContinue(world,drill)){
                walls.flush(world,drill);
                wallFallback=true;replaceCleared=false;
                return ComputerEntity.Status.RUNNING;
            }
            return ComputerEntity.Status.OUT_OF_WALLS;
        }
        return applyFromStock(world,drill,walls,next,true);
    }
    /** Wall handling always precedes the fluid check and core excavation. */
    ComputerEntity.Status wallStep(ServerLevel world,DrillEntity drill,ComputerEntity computer){
        retryWallSource(world,drill);
        if(computer.wallMode()==0||wallFinished){
            wallPositionsRemaining=0;airWallNeeded=false;
            return null;
        }
        legacyWallPhase=true;
        if(delivery!=null)return advanceDelivery(world,drill);
        if(computer.wallMode()==2&&!wallFallback){
            airWallNeeded=false;
            return replaceWall(world,drill,computer);
        }
        wallPositionsRemaining=0;
        airWallNeeded=pending!=null&&pendingWall;
        for(BlockPos at:ring(drill,computer)){
            if(finishedWalls.contains(at)||!world.hasChunkAt(at))continue;
            BlockState state=world.getBlockState(at);
            if(!immune(state)&&!solid(world,at)){
                airWallNeeded=true;break;
            }
        }
        // Air-mode walls draw from the SAME 64-item Filler reservoir used for
        // ocean sealing. No more one-by-one source-to-drill supply waits.
        // Do not reserve 64 blocks on fully solid ring layers: that caused
        // needless chest withdrawal/refund cycles before any actual work.
        if(airWallNeeded){
            filler.tick(world,drill);
            filler.request(world,drill,64);
        }
        if(pending!=null&&pendingWall){
            ComputerEntity.Status check=verifyStock(world,drill);
            if(check!=null)return check;
        }
        for(BlockPos at:ring(drill,computer)){
            if(finishedWalls.contains(at))continue;
            if(!world.hasChunkAt(at))return ComputerEntity.Status.BLOCKED;
            BlockState state=world.getBlockState(at);
            if(immune(state)||solid(world,at)){finishedWalls.add(at);continue;}
            if(!filler.hasSupply()){
                if(filler.hasFlights() || RigNetwork.supplyDepotPending(world,drill,false))
                    return ComputerEntity.Status.RUNNING;
                return ComputerEntity.Status.OUT_OF_FILLER;
            }
            return applyFromStock(world,drill,filler,at,true);
        }
        wallFinished=true;airWallNeeded=false;drill.setChanged();
        return null;
    }
    /**
     * Fluid recovery holds the full 64-block filler stock (ready + in flight)
     * for as long as ANY leak is being sealed. Supplies are kept replenished
     * before every placement, so the drill never returns to mining early.
     */
    ComputerEntity.Status fluidStep(ServerLevel world,DrillEntity drill,ComputerEntity computer){
        legacyWallPhase=false;
        if(delivery!=null)return advanceDelivery(world,drill);
        scanFluids(world,drill,computer);
        if(!fluidSpots.isEmpty()||pending!=null&&!pendingWall)fluidMode=true;
        if(!fluidMode){
            // Keep filler stocked through ordinary mining. Release only on pause.
            if(cautionTicks>0)cautionTicks--;
            return null;
        }
        filler.tick(world,drill);
        // Ask for a full 64-item buffer, not merely the currently visible
        // source count: fluid may spread, and sand/gravel may fail verification.
        filler.request(world,drill,64);
        if(pending!=null&&!pendingWall){
            ComputerEntity.Status checked=verifyStock(world,drill);
            if(checked!=null)return checked;
        }
        // This is a STRICT two-stage sealing order, even with Wall Mode Off:
        // (1) plug FLUID in the one-block exterior ring, one coordinate until
        // verified solid; (2) do the same inside the mining footprint.
        //
        // Scan both regions on each tick, but always prioritize the ring.
        // Fluid source updates in an ocean can re-wet a finished ring cell.
        for(int phase=0;phase<2;phase++){
            boolean wallPhase=phase==0;
            for(Iterator<BlockPos> it=fluidSpots.iterator();it.hasNext();){
                BlockPos at=it.next();
                if(outsideHole(at,drill,computer)!=wallPhase)continue;
                if(!world.hasChunkAt(at))return ComputerEntity.Status.BLOCKED;
                if(immune(world.getBlockState(at))||solid(world,at)){
                    it.remove();continue;
                }
                // Don't skip this individual coordinate just because its
                // water temporarily turned into air. Keep filling until a
                // permanent solid block exists here.
                target(world,at,null);
                if(failed())return ComputerEntity.Status.FILL_FAILED;
                if(!filler.hasSupply()){
                    if(filler.hasFlights() || RigNetwork.fillerDepotPending(world,drill))
                        return ComputerEntity.Status.RUNNING;
                    return ComputerEntity.Status.OUT_OF_FILLER;
                }
                return applyFromStock(world,drill,filler,at,false);
            }
            // Check again for outer-wall fluid introduced by a neighbor
            // update before advancing from boundary sealing to inner area.
            if(wallPhase){
                scanFluids(world,drill,computer);
                for(BlockPos at:fluidSpots)
                    if(outsideHole(at,drill,computer)
                            && !immune(world.getBlockState(at)) && !solid(world,at))
                        return ComputerEntity.Status.RUNNING;
            }
        }
        // Retain the filler buffer so the next fluid pocket needs no cold start.
        fluidMode=false;
        drill.setChanged();
        if(cautionTicks>0)cautionTicks--;
        return null;
    }
    void save(ValueOutput out){
        out.putInt("FluidSpots",fluidSpots.size());
        int i=0;for(BlockPos p:fluidSpots)out.putLong("FluidSpot"+i++,p.asLong());
        out.putInt("FinishedWalls",finishedWalls.size());
        i=0;for(BlockPos p:finishedWalls)out.putLong("WallPos"+i++,p.asLong());
        out.putBoolean("WallFinished",wallFinished);
        out.putBoolean("WallSourceChecked",wallSourceChecked);
        out.putBoolean("FillPending",pending!=null);
        if(pending!=null)out.putLong("FillPosition",pending.asLong());
        out.putInt("FillType",pendingWall?2:1);out.putInt("FillWait",wait);
        out.putBoolean("PendingWallSource",pendingSourceWalls);
        out.putInt("FillAttempts",attempts);
        out.putBoolean("HasAttemptTarget",attemptTarget!=null);
        if(attemptTarget!=null)out.putLong("AttemptTarget",attemptTarget.asLong());
        out.putInt("AllowedAttempts",allowedAttempts);
        out.putInt("ConfiguredFillTries",configuredLimit);
        out.putInt("GravityDropDepth",gravityDropDepth);
        out.putBoolean("WallFallback",wallFallback);
        out.putInt("CautionTicks",cautionTicks);
        out.putBoolean("ReplaceCleared",replaceCleared);
        out.putBoolean("FluidFillingMode",fluidMode);
        walls.save(out);
        filler.save(out);
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
        wallSourceChecked=in.getBooleanOr("WallSourceChecked",false);
        pending=in.getBooleanOr("FillPending",false)
            ?BlockPos.of(in.getLongOr("FillPosition",0)):null;
        pendingWall=in.getIntOr("FillType",0)==2;
        pendingSourceWalls=in.getBooleanOr("PendingWallSource",pendingWall);
        wait=Math.max(0,in.getIntOr("FillWait",0));
        attempts=Math.max(0,in.getIntOr("FillAttempts",0));
        attemptTarget=in.getBooleanOr("HasAttemptTarget",false)
            ?BlockPos.of(in.getLongOr("AttemptTarget",0)):pending;
        configuredLimit=Math.max(ComputerEntity.MIN_FILL_TRIES,
            Math.min(ComputerEntity.MAX_FILL_TRIES,
                in.getIntOr("ConfiguredFillTries",DEFAULT_FILL_ATTEMPTS)));
        allowedAttempts=configuredLimit;
        gravityDropDepth=Math.max(0,in.getIntOr("GravityDropDepth",0));
        wallFallback=in.getBooleanOr("WallFallback",false);
        cautionTicks=Math.max(0,in.getIntOr("CautionTicks",0));
        replaceCleared=in.getBooleanOr("ReplaceCleared",false);
        fluidMode=in.getBooleanOr("FluidFillingMode",false);
        walls.load(in);
        filler.load(in);
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
