package dev.casz.miningrig;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Display;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jetbrains.annotations.Nullable;

/**
 * Wall supplies are reserved only after EVERY outer-ring block has been mined.
 * The network is told the whole material demand, then sends 3-block packets
 * concurrently, ahead of the drill's comparatively slow placement step.
 *
 * All items have exactly one owner: input chest -> in-flight packet -> ready
 * buffer -> placed wall. Packet and buffer cargo persist in the drill's NBT.
 */
final class WallSupplyPipeline {
    private static final int PER_PACKET=3;
    private static final int MAX_ACTIVE_FLIGHTS=12;
    private static final int MAX_AHEAD=48;
    private static final int NEW_FLIGHTS_PER_TICK=4;
    private static final int MAX_SAVED_PACKETS=96;

    private static final class Packet {
        final RigNetwork.Supply cargo;
        int elapsed;
        @Nullable Display.ItemDisplay visual;
        Packet(RigNetwork.Supply cargo,int elapsed){this.cargo=cargo;this.elapsed=elapsed;}
        int units(){return cargo.stack().getCount();}
        void discard(){
            if(visual!=null)visual.discard();
            visual=null;
        }
    }
    private final List<Packet> flights=new ArrayList<>();
    private final List<ItemStack> ready=new ArrayList<>();
    private int requestedTotal,deliveredCount;

    int readyCount(){
        int n=0;for(ItemStack stack:ready)n+=stack.getCount();return n;
    }
    int flightCount(){
        int n=0;for(Packet p:flights)n+=p.units();return n;
    }
    boolean hasSupply(){return readyCount()>0;}
    boolean hasFlights(){return !flights.isEmpty();}
    int demand(){return requestedTotal;}
    int delivered(){return deliveredCount;}
    void plan(int blocks){requestedTotal=Math.max(0,blocks);}

    private static int legTicks(BlockPos a,BlockPos b){
        int dist=Math.abs(a.getX()-b.getX())+Math.abs(a.getY()-b.getY())+
            Math.abs(a.getZ()-b.getZ());
        return Math.max(3,(int)Math.ceil(dist*2.5));
    }
    private static void spawn(ServerLevel world,Packet packet){
        if(packet.visual!=null || packet.cargo.path().isEmpty())return;
        Display.ItemDisplay item=new Display.ItemDisplay(
            BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.withDefaultNamespace("item_display")),world);
        item.setItemStack(packet.cargo.stack().copyWithCount(1));
        item.setItemTransform(ItemDisplayContext.GROUND);
        item.setTransformation(new com.mojang.math.Transformation(
            new org.joml.Vector3f(0,0,0),new org.joml.Quaternionf(),
            new org.joml.Vector3f(.55f,.55f,.55f),new org.joml.Quaternionf()));
        item.setNoGravity(true);
        BlockPos at=packet.cargo.path().get(0);
        item.setPos(at.getX()+.5,at.getY()+.55,at.getZ()+.5);
        world.addFreshEntity(item);
        packet.visual=item;
    }
    /** Advance all concurrently in-flight packets, independent of placement. */
    void tick(ServerLevel world,DrillEntity drill){
        if(flights.isEmpty())return;
        for(int i=flights.size()-1;i>=0;i--){
            Packet packet=flights.get(i);
            spawn(world,packet);
            packet.elapsed++;
            int remaining=packet.elapsed;
            int segment=-1,local=0;
            List<BlockPos> route=packet.cargo.path();
            for(int hop=1;hop<route.size();hop++){
                int duration=legTicks(route.get(hop-1),route.get(hop));
                if(remaining<duration){segment=hop;local=remaining;break;}
                remaining-=duration;
            }
            if(segment>=1){
                BlockPos a=route.get(segment-1),b=route.get(segment);
                float t=local/(float)legTicks(a,b);
                if(packet.visual!=null)
                    packet.visual.setPos(a.getX()+.5+(b.getX()-a.getX())*t,
                        a.getY()+.55+(b.getY()-a.getY())*t+.1*Math.sin(Math.PI*t),
                        a.getZ()+.5+(b.getZ()-a.getZ())*t);
            }else{
                ready.add(packet.cargo.stack().copy());
                deliveredCount+=packet.units();
                packet.discard();
                flights.remove(i);
            }
        }
        drill.setChanged();
    }
    /**
     * Called with the current number of wall targets still requiring a block.
     * The drill asks the network for that complete count; reservations are
     * throttled to at most four 3-item flights/tick and 48 items ahead.
     */
    void request(ServerLevel world,DrillEntity drill,int stillNeeded){
        int lacking=Math.max(0,stillNeeded-readyCount()-flightCount());
        int packets=0;
        while(lacking>0&&flights.size()<MAX_ACTIVE_FLIGHTS
                &&readyCount()+flightCount()<MAX_AHEAD&&packets<NEW_FLIGHTS_PER_TICK){
            int amount=Math.min(PER_PACKET,Math.min(lacking,MAX_AHEAD-readyCount()-flightCount()));
            RigNetwork.Supply cargo=RigNetwork.reservePacket(world,drill,true,amount);
            if(cargo==null)break;
            flights.add(new Packet(cargo,0));
            lacking-=cargo.stack().getCount();
            packets++;
        }
        if(packets>0)drill.setChanged();
    }
    /** Consume exactly one block only after the world accepts its placement. */
    ItemStack peek(){
        return ready.isEmpty()?ItemStack.EMPTY:ready.get(0);
    }
    void consume(){
        if(ready.isEmpty())return;
        ItemStack top=ready.get(0);
        top.shrink(1);
        if(top.isEmpty())ready.remove(0);
    }
    private static void refund(ServerLevel world,DrillEntity drill,ItemStack stack){
        if(stack.isEmpty())return;
        if(!drill.receive(List.of(stack)))
            Block.popResource(world,drill.getBlockPos(),stack.copy());
    }
    void flush(ServerLevel world,DrillEntity drill){
        // Any over-requested wall supply is returned to drill output storage;
        // if its 64-item inventory cannot accept it, drop it rather than void.
        for(Packet p:flights){
            p.discard();refund(world,drill,p.cargo.stack());
        }
        flights.clear();
        for(ItemStack stack:ready)refund(world,drill,stack);
        ready.clear();
        requestedTotal=0;deliveredCount=0;
        drill.setChanged();
    }
    void moveTo(WallSupplyPipeline next){
        next.flights.clear();
        next.flights.addAll(flights);
        next.ready.clear();
        for(ItemStack stack:ready)next.ready.add(stack.copy());
        next.requestedTotal=requestedTotal;next.deliveredCount=deliveredCount;
        flights.clear();ready.clear();
        requestedTotal=0;deliveredCount=0;
    }
    void save(ValueOutput out){
        out.putInt("WallBatchDemand",requestedTotal);
        out.putInt("WallBatchArrived",deliveredCount);
        out.putInt("WallBatchFlights",flights.size());
        for(int i=0;i<flights.size();i++){
            Packet packet=flights.get(i);
            out.store("WallFlightItem"+i,ItemStack.CODEC,packet.cargo.stack());
            out.putInt("WallFlightTick"+i,packet.elapsed);
            List<BlockPos> path=packet.cargo.path();
            out.putInt("WallFlightPathLength"+i,path.size());
            for(int j=0;j<path.size();j++)
                out.putLong("WallFlightPath"+i+"_"+j,path.get(j).asLong());
        }
        out.putInt("WallBatchReady",ready.size());
        for(int i=0;i<ready.size();i++)
            out.store("WallReadyItem"+i,ItemStack.CODEC,ready.get(i));
    }
    void load(ValueInput in){
        for(Packet packet:flights)packet.discard();
        flights.clear();ready.clear();
        requestedTotal=Math.max(0,in.getIntOr("WallBatchDemand",0));
        deliveredCount=Math.max(0,in.getIntOr("WallBatchArrived",0));
        int count=Math.max(0,Math.min(MAX_SAVED_PACKETS,in.getIntOr("WallBatchFlights",0)));
        for(int i=0;i<count;i++){
            ItemStack item=in.read("WallFlightItem"+i,ItemStack.CODEC).orElse(ItemStack.EMPTY);
            int pathLen=Math.max(0,Math.min(256,in.getIntOr("WallFlightPathLength"+i,0)));
            List<BlockPos> path=new ArrayList<>();
            for(int j=0;j<pathLen;j++)
                path.add(BlockPos.of(in.getLongOr("WallFlightPath"+i+"_"+j,0)));
            if(!item.isEmpty()&&path.size()>=2)
                flights.add(new Packet(new RigNetwork.Supply(item,path),
                    Math.max(0,in.getIntOr("WallFlightTick"+i,0))));
        }
        int readyCount=Math.max(0,Math.min(MAX_SAVED_PACKETS,in.getIntOr("WallBatchReady",0)));
        for(int i=0;i<readyCount;i++){
            ItemStack item=in.read("WallReadyItem"+i,ItemStack.CODEC).orElse(ItemStack.EMPTY);
            if(!item.isEmpty())ready.add(item);
        }
    }
}
