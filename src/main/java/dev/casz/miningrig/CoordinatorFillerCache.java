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
 * One independent 192-block Filler or Wall supply depot at the nearest CC.
 *
 * Each requested material type is prefetched, 3 blocks per packet, at the
 * same time that the drill replenishes its own 64-block buffer. Every unit has one owner: original
 * container -> inbound packet -> CC cache -> drill packet -> placed block.
 * No player can extract the ItemDisplay entities during transit.
 */
final class CoordinatorFillerCache {
    private static final int CAPACITY=192,PACKET=3,MAX_FLIGHTS=32,
        DISPATCH_PER_TICK=8,MAX_VISIBLE=24;
    // Each instance owns its own depot and saved NBT namespace.
    private final boolean walls;
    private final String key;
    CoordinatorFillerCache(boolean walls,String key){this.walls=walls;this.key=key;}
    private static final class Flight {
        final RigNetwork.Supply cargo;
        int ticks;
        @Nullable Display.ItemDisplay visual;
        Flight(RigNetwork.Supply cargo,int ticks){this.cargo=cargo;this.ticks=ticks;}
        int count(){return cargo.stack().getCount();}
        void discard(){if(visual!=null)visual.discard();visual=null;}
    }
    private final List<RigNetwork.Supply> available=new ArrayList<>();
    private final List<Flight> inbound=new ArrayList<>();

    int stored(){
        int n=0;
        for(RigNetwork.Supply item:available)n+=item.stack().getCount();
        for(Flight f:inbound)n+=f.count();
        return n;
    }
    int ready(){
        int n=0;for(RigNetwork.Supply item:available)n+=item.stack().getCount();
        return n;
    }
    boolean isEmpty(){return inbound.isEmpty()&&available.isEmpty();}
    private static int legTicks(BlockPos a,BlockPos b){
        int d=Math.abs(a.getX()-b.getX())+Math.abs(a.getY()-b.getY())+
            Math.abs(a.getZ()-b.getZ());
        return Math.max(2,(int)Math.ceil(d*0.8));
    }
    private void moveDisplay(ServerLevel world,Flight f){
        List<BlockPos> path=f.cargo.path();
        if(path.size()<2)return;
        if(f.visual==null){
            int visible=0;for(Flight other:inbound)
                if(other.visual!=null&&other.visual.isAlive())visible++;
            if(visible>=MAX_VISIBLE)return;
            Display.ItemDisplay display=new Display.ItemDisplay(
                BuiltInRegistries.ENTITY_TYPE.getValue(
                    Identifier.withDefaultNamespace("item_display")),world);
            display.setItemStack(f.cargo.stack().copyWithCount(1));
            display.setItemTransform(ItemDisplayContext.GROUND);
            display.setTransformation(new com.mojang.math.Transformation(
                new org.joml.Vector3f(),new org.joml.Quaternionf(),
                new org.joml.Vector3f(.55f,.55f,.55f),new org.joml.Quaternionf()));
            display.setNoGravity(true);
            BlockPos first=path.get(0);
            display.setPos(first.getX()+.5,first.getY()+.55,first.getZ()+.5);
            world.addFreshEntity(display);
            f.visual=display;
        }
        int remain=f.ticks;
        for(int i=1;i<path.size();i++){
            BlockPos a=path.get(i-1),b=path.get(i);
            int duration=legTicks(a,b);
            if(remain>=duration){remain-=duration;continue;}
            float part=remain/(float)duration;
            if(f.visual!=null)f.visual.setPos(
                a.getX()+.5+(b.getX()-a.getX())*part,
                a.getY()+.55+(b.getY()-a.getY())*part+
                    .1*Math.sin(Math.PI*part),
                a.getZ()+.5+(b.getZ()-a.getZ())*part);
            return;
        }
    }
    private boolean arrived(Flight f){
        int time=f.ticks;
        List<BlockPos> path=f.cargo.path();
        for(int i=1;i<path.size();i++){
            time-=legTicks(path.get(i-1),path.get(i));
            if(time<0)return false;
        }
        return true;
    }
    void tick(ServerLevel world,CoordinatorEntity cc,DrillEntity drill){
        boolean changed=false;
        for(int i=0;i<inbound.size();){
            Flight f=inbound.get(i);
            f.ticks++;
            if(arrived(f)){
                available.add(f.cargo);
                f.discard();
                inbound.remove(i);
                changed=true;
            }else {moveDisplay(world,f);i++;}
        }
        // The supply cache exists only during active FILLING mode. It is not
        // general coordinator inventory and cannot be routed out by mistake.
        // Only the nearest CC above this drill is its designated supply depot.
        // Farther CCs must not all siphon extra filler from the same network.
        if(drill!=null && RigNetwork.nearestDrillCoordinator(world,drill)!=cc)
            drill=null;
        if(drill==null || !(walls?drill.activelyWalling(world):drill.activelyFilling(world))){
            if(!isEmpty())flush(world,cc);
            if(changed)cc.setChanged();
            return;
        }
        // Replace can calculate its exact ring demand, unlike unpredictable
        // ocean fluid flow. Do not vacuum 192 Wall blocks for a 16-block ring.
        int target=walls?Math.min(CAPACITY,drill.wallDepotTarget()):CAPACITY;
        int spawned=0;
        while(stored()<target && inbound.size()<MAX_FLIGHTS
                && spawned<DISPATCH_PER_TICK){
            RigNetwork.Supply cargo=RigNetwork.reserveForCache(world,drill,walls,
                Math.min(PACKET,target-stored()));
            if(cargo==null)break;
            if(cargo.path().size()<2)available.add(cargo);
            else inbound.add(new Flight(cargo,0));
            spawned++;changed=true;
        }
        if(changed)cc.setChanged();
    }
    @Nullable RigNetwork.Supply take(DrillEntity drill,CoordinatorEntity cc,int max){
        if(max<=0)return null;
        for(int i=0;i<available.size();i++){
            RigNetwork.Supply entry=available.get(i);
            ItemStack stack=entry.stack();
            if(stack.isEmpty()){available.remove(i--);continue;}
            int amount=Math.min(Math.min(3,max),stack.getCount());
            ItemStack taken=stack.copyWithCount(amount);
            stack.shrink(amount);
            if(stack.isEmpty())available.remove(i);
            cc.setChanged();
            return new RigNetwork.Supply(taken,
                List.of(cc.getBlockPos(),drill.getBlockPos()),
                entry.sourcePos(),entry.sourceFace());
        }
        return null;
    }
    private static void refund(ServerLevel world,CoordinatorEntity cc,RigNetwork.Supply cargo){
        ItemStack remainder=RigNetwork.returnToSource(world,cargo);
        if(remainder.isEmpty())return;
        if(cc.canReceive(List.of(remainder))&&cc.receive(List.of(remainder)))return;
        Block.popResource(world,cc.getBlockPos(),remainder);
    }
    void flush(ServerLevel world,CoordinatorEntity cc){
        for(Flight f:inbound){
            f.discard();
            refund(world,cc,f.cargo);
        }
        inbound.clear();
        for(RigNetwork.Supply supply:available)refund(world,cc,supply);
        available.clear();
        cc.setChanged();
    }
    private static void saveCargo(ValueOutput out,String prefix,RigNetwork.Supply cargo){
        out.store(prefix+"Item",ItemStack.CODEC,cargo.stack());
        if(cargo.sourcePos()!=null&&cargo.sourceFace()!=null){
            out.putBoolean(prefix+"HasOrigin",true);
            out.putLong(prefix+"Origin",cargo.sourcePos().asLong());
            out.putInt(prefix+"Face",cargo.sourceFace().get3DDataValue());
        }
        out.putInt(prefix+"PathSize",cargo.path().size());
        for(int i=0;i<cargo.path().size();i++)
            out.putLong(prefix+"Hop"+i,cargo.path().get(i).asLong());
    }
    @Nullable private static RigNetwork.Supply readCargo(ValueInput in,String prefix){
        ItemStack stack=in.read(prefix+"Item",ItemStack.CODEC).orElse(ItemStack.EMPTY);
        if(stack.isEmpty())return null;
        BlockPos source=in.getBooleanOr(prefix+"HasOrigin",false)
            ?BlockPos.of(in.getLongOr(prefix+"Origin",0)):null;
        Direction face=source==null?null:
            Direction.from3DDataValue(in.getIntOr(prefix+"Face",0));
        int hops=Math.max(0,Math.min(256,in.getIntOr(prefix+"PathSize",0)));
        List<BlockPos> path=new ArrayList<>();
        for(int i=0;i<hops;i++)
            path.add(BlockPos.of(in.getLongOr(prefix+"Hop"+i,0)));
        return new RigNetwork.Supply(stack,path,source,face);
    }
    void save(ValueOutput out){
        out.putInt(key+"FillDepotReady",available.size());
        for(int i=0;i<available.size();i++)
            saveCargo(out,key+"DepotReady"+i,available.get(i));
        out.putInt(key+"FillDepotFlights",inbound.size());
        for(int i=0;i<inbound.size();i++){
            saveCargo(out,key+"DepotFlight"+i,inbound.get(i).cargo);
            out.putInt(key+"DepotTicks"+i,inbound.get(i).ticks);
        }
    }
    void load(ValueInput in){
        for(Flight flight:inbound)flight.discard();
        inbound.clear();available.clear();
        int ready=Math.max(0,Math.min(192,in.getIntOr(key+"FillDepotReady",0)));
        for(int i=0;i<ready;i++){
            RigNetwork.Supply cargo=readCargo(in,key+"DepotReady"+i);
            if(cargo!=null)available.add(cargo);
        }
        int flights=Math.max(0,Math.min(MAX_FLIGHTS,in.getIntOr(key+"FillDepotFlights",0)));
        for(int i=0;i<flights;i++){
            RigNetwork.Supply cargo=readCargo(in,key+"DepotFlight"+i);
            if(cargo!=null)inbound.add(new Flight(cargo,
                Math.max(0,in.getIntOr(key+"DepotTicks"+i,0))));
        }
    }
}
