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
    private static final int MAX_AHEAD=64;
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
    private final String key;
    private final boolean walls;
    private final int bufferLimit;
    WallSupplyPipeline(String key,boolean walls,int bufferLimit){
        this.key=key;this.walls=walls;
        this.bufferLimit=Math.max(3,Math.min(MAX_AHEAD,bufferLimit));
    }
    private final List<Packet> flights=new ArrayList<>();
    private final List<RigNetwork.Supply> ready=new ArrayList<>();
    private int requestedTotal,deliveredCount;
    private long lastAdvanceTick=Long.MIN_VALUE;
    private int selectedIndex=-1;
    @Nullable private RigNetwork.Supply lastUsed;

    /** The ready queue is the drill's 64-item bundle. Flights reserve room in it. */
    int bundleCount(){return readyCount();}
    int bundleSize(){return 64;}
    ItemStack bundleItem(int index){
        return index>=0&&index<ready.size()?ready.get(index).stack():ItemStack.EMPTY;
    }
    ItemStack removeBundleItem(int index,int count){
        if(index<0||index>=ready.size()||count<=0)return ItemStack.EMPTY;
        ItemStack result=ready.get(index).stack().split(count);
        if(ready.get(index).stack().isEmpty())ready.remove(index);
        selectedIndex=-1;
        return result;
    }
    void setBundleItem(int index,ItemStack item){
        if(index<0||index>=bundleSize())return;
        if(index>ready.size())return;
        RigNetwork.Supply previous=index<ready.size()?ready.get(index):null;
        int previousCount=previous==null?0:previous.stack().getCount();
        int room=Math.max(0,bufferLimit-readyCount()-flightCount()+previousCount);
        if(item.isEmpty()){
            if(previous!=null)ready.remove(index);
        }else if(RigNetwork.isFiller(item)){
            int accepted=Math.min(item.getCount(),Math.min(item.getMaxStackSize(),room));
            if(accepted<=0)return;
            RigNetwork.Supply next=new RigNetwork.Supply(item.copyWithCount(accepted),
                previous!=null&&ItemStack.isSameItemSameComponents(previous.stack(),item)
                    ?previous.path():List.of(),
                previous!=null&&ItemStack.isSameItemSameComponents(previous.stack(),item)
                    ?previous.sourcePos():null,
                previous!=null&&ItemStack.isSameItemSameComponents(previous.stack(),item)
                    ?previous.sourceFace():null);
            if(previous!=null)ready.set(index,next);else ready.add(next);
        }
        selectedIndex=-1;
    }
    int bundleRoom(int index){
        if(index<0||index>=bundleSize()||index>ready.size())return 0;
        return Math.max(0,bufferLimit-readyCount()-flightCount()
            +(index>=0&&index<ready.size()?ready.get(index).stack().getCount():0));
    }

    int readyCount(){
        int n=0;for(RigNetwork.Supply supply:ready)n+=supply.stack().getCount();return n;
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
        return Math.max(2,(int)Math.ceil(dist*0.8));
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
        // Air walls and fluid sealing may both run in one game tick.
        // Never advance the same flight twice in that case.
        long time=world.getGameTime();
        if(lastAdvanceTick==time)return;
        lastAdvanceTick=time;
        if(flights.isEmpty())return;
        for(int i=0;i<flights.size();){
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
                i++;
            }else{
                ready.add(packet.cargo);
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
                &&readyCount()+flightCount()<bufferLimit&&packets<NEW_FLIGHTS_PER_TICK){
            int amount=Math.min(PER_PACKET,Math.min(lacking,bufferLimit-readyCount()-flightCount()));
            RigNetwork.Supply cargo=RigNetwork.reservePacket(world,drill,walls,amount);
            if(cargo==null)break;
            flights.add(new Packet(cargo,0));
            lacking-=cargo.stack().getCount();
            packets++;
        }
        if(packets>0)drill.setChanged();
    }
    /** Select among material stacks. Random avoids the previously used stack when possible. */
    ItemStack peek(ServerLevel world,boolean random){
        if(ready.isEmpty()){selectedIndex=-1;return ItemStack.EMPTY;}
        selectedIndex=0;
        if(random && ready.size()>1){
            List<Integer> options=new ArrayList<>();
            for(int i=0;i<ready.size();i++)
                if(ready.get(i)!=lastUsed)options.add(i);
            if(options.isEmpty())for(int i=0;i<ready.size();i++)options.add(i);
            selectedIndex=options.get(world.getRandom().nextInt(options.size()));
        }
        return ready.get(selectedIndex).stack();
    }
    /**
     * Prefer a non-gravity block at unsupported fill positions, avoiding
     * unnecessary sand/gravel towers when a solid filler is already staged.
     * Keeps the queue item's original source for correct refunds.
     */
    ItemStack peekStable(ServerLevel world,boolean random){
        List<Integer> candidates=new ArrayList<>();
        for(int i=0;i<ready.size();i++){
            ItemStack stack=ready.get(i).stack();
            if(stack.getItem() instanceof net.minecraft.world.item.BlockItem block
                && !(block.getBlock() instanceof net.minecraft.world.level.block.FallingBlock))
                candidates.add(i);
        }
        if(candidates.isEmpty())return ItemStack.EMPTY;
        if(random&&candidates.size()>1){
            List<Integer> different=new ArrayList<>();
            for(int index:candidates)if(ready.get(index)!=lastUsed)different.add(index);
            if(!different.isEmpty())candidates=different;
            selectedIndex=candidates.get(world.getRandom().nextInt(candidates.size()));
        }else selectedIndex=candidates.get(0);
        return ready.get(selectedIndex).stack();
    }
    void consume(){
        if(ready.isEmpty())return;
        int index=selectedIndex>=0&&selectedIndex<ready.size()?selectedIndex:0;
        RigNetwork.Supply supply=ready.get(index);
        lastUsed=supply;
        supply.stack().shrink(1);
        if(supply.stack().isEmpty())ready.remove(index);
        selectedIndex=-1;
    }
    private static void refund(ServerLevel world,DrillEntity drill,RigNetwork.Supply packet){
        if(packet.stack().isEmpty())return;
        ItemStack leftover=RigNetwork.returnToSource(world,packet);
        if(leftover.isEmpty())return;
        if(RigNetwork.returnSurplus(world,drill,leftover))return;
        if(!drill.receive(List.of(leftover)))
            Block.popResource(world,drill.getBlockPos(),leftover.copy());
    }
    void flush(ServerLevel world,DrillEntity drill){
        // Any over-requested wall supply is returned to drill output storage;
        // if its 64-item inventory cannot accept it, drop it rather than void.
        for(Packet p:flights){
            p.discard();refund(world,drill,p.cargo);
        }
        flights.clear();
        for(RigNetwork.Supply supply:ready)refund(world,drill,supply);
        ready.clear();
        requestedTotal=0;deliveredCount=0;
        selectedIndex=-1;lastUsed=null;
        drill.setChanged();
    }
    void moveTo(WallSupplyPipeline next){
        for(Packet previous:next.flights)previous.discard();
        next.flights.clear();
        next.flights.addAll(flights);
        next.ready.clear();
        for(RigNetwork.Supply cargo:ready)next.ready.add(new RigNetwork.Supply(
            cargo.stack().copy(),cargo.path(),cargo.sourcePos(),cargo.sourceFace()));
        next.requestedTotal=requestedTotal;next.deliveredCount=deliveredCount;
        next.selectedIndex=-1;next.lastUsed=null;
        next.lastAdvanceTick=lastAdvanceTick;
        flights.clear();ready.clear();
        requestedTotal=0;deliveredCount=0;
        selectedIndex=-1;lastUsed=null;
    }
    private static void saveOrigin(ValueOutput out,String prefix,RigNetwork.Supply cargo){
        if(cargo.sourcePos()!=null&&cargo.sourceFace()!=null){
            out.putBoolean(prefix+"HasSource",true);
            out.putLong(prefix+"Source",cargo.sourcePos().asLong());
            out.putInt(prefix+"SourceFace",cargo.sourceFace().get3DDataValue());
        }
    }
    private static RigNetwork.Supply withOrigin(ValueInput in,String prefix,
            ItemStack stack,List<BlockPos> path){
        boolean source=in.getBooleanOr(prefix+"HasSource",false);
        return new RigNetwork.Supply(stack,path,
            source?BlockPos.of(in.getLongOr(prefix+"Source",0)):null,
            source?net.minecraft.core.Direction.from3DDataValue(
                in.getIntOr(prefix+"SourceFace",0)):null);
    }
    void save(ValueOutput out){
        out.putInt(key+"WallBatchDemand",requestedTotal);
        out.putInt(key+"WallBatchArrived",deliveredCount);
        out.putInt(key+"WallBatchFlights",flights.size());
        for(int i=0;i<flights.size();i++){
            Packet packet=flights.get(i);
            out.store(key+"WallFlightItem"+i,ItemStack.CODEC,packet.cargo.stack());
            saveOrigin(out,key+"WallFlight"+i,packet.cargo);
            out.putInt(key+"WallFlightTick"+i,packet.elapsed);
            List<BlockPos> path=packet.cargo.path();
            out.putInt(key+"WallFlightPathLength"+i,path.size());
            for(int j=0;j<path.size();j++)
                out.putLong(key+"WallFlightPath"+i+"_"+j,path.get(j).asLong());
        }
        out.putInt(key+"WallBatchReady",ready.size());
        for(int i=0;i<ready.size();i++){
            out.store(key+"WallReadyItem"+i,ItemStack.CODEC,ready.get(i).stack());
            saveOrigin(out,key+"WallReady"+i,ready.get(i));
        }
    }
    void load(ValueInput in){
        for(Packet packet:flights)packet.discard();
        flights.clear();ready.clear();
        selectedIndex=-1;lastUsed=null;
        requestedTotal=Math.max(0,in.getIntOr(key+"WallBatchDemand",0));
        deliveredCount=Math.max(0,in.getIntOr(key+"WallBatchArrived",0));
        int count=Math.max(0,Math.min(MAX_SAVED_PACKETS,in.getIntOr(key+"WallBatchFlights",0)));
        for(int i=0;i<count;i++){
            ItemStack item=in.read(key+"WallFlightItem"+i,ItemStack.CODEC).orElse(ItemStack.EMPTY);
            int pathLen=Math.max(0,Math.min(256,in.getIntOr(key+"WallFlightPathLength"+i,0)));
            List<BlockPos> path=new ArrayList<>();
            for(int j=0;j<pathLen;j++)
                path.add(BlockPos.of(in.getLongOr(key+"WallFlightPath"+i+"_"+j,0)));
            if(!item.isEmpty()&&path.size()>=2)
                flights.add(new Packet(withOrigin(in,key+"WallFlight"+i,item,path),
                    Math.max(0,in.getIntOr(key+"WallFlightTick"+i,0))));
        }
        int readyCount=Math.max(0,Math.min(MAX_SAVED_PACKETS,in.getIntOr(key+"WallBatchReady",0)));
        for(int i=0;i<readyCount;i++){
            ItemStack item=in.read(key+"WallReadyItem"+i,ItemStack.CODEC).orElse(ItemStack.EMPTY);
            if(!item.isEmpty())ready.add(withOrigin(in,key+"WallReady"+i,
                item,List.of()));
        }
    }
}
