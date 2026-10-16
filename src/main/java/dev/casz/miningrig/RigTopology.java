package dev.casz.miningrig;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * Loaded-chunk-only index for CCs, computers, and drills. The machine tickers
 * maintain the index; searches never force-load or scan thousands of blocks.
 * Stale entries from an unloaded chunk are skipped and lazily removed when
 * the block is observed as something other than the registered machine.
 */
public final class RigTopology {
    private RigTopology(){}
    public static final int RANGE=24;
    private static final Map<ServerLevel,Set<BlockPos>> coordinators=new WeakHashMap<>();
    private static final Map<ServerLevel,Set<BlockPos>> drills=new WeakHashMap<>();
    private record NetworkCache(long tick,BlockPos root,List<CoordinatorEntity> component){}
    private static final Map<ServerLevel,NetworkCache> cachedNetworks=new WeakHashMap<>();
    static List<CoordinatorEntity> network(ServerLevel world,ComputerEntity computer){
        CoordinatorEntity root=nearest(world,computer.getBlockPos());
        if(root==null)return List.of();
        NetworkCache cached=cachedNetworks.get(world);
        if(cached!=null&&cached.tick()==world.getGameTime()
            &&cached.root().equals(root.getBlockPos()))return cached.component();
        List<CoordinatorEntity> result=RigNetwork.component(world,root);
        cachedNetworks.put(world,new NetworkCache(world.getGameTime(),root.getBlockPos(),result));
        return result;
    }
    public static void coordinator(ServerLevel world,BlockPos pos){
        if(coordinators.computeIfAbsent(world,w->new HashSet<>()).add(pos.immutable()))
            cachedNetworks.remove(world);
    }
    public static void drill(ServerLevel world,BlockPos pos){
        drills.computeIfAbsent(world,w->new HashSet<>()).add(pos.immutable());
    }
    public static void forgetCoordinator(ServerLevel world,BlockPos pos){
        Set<BlockPos> points=coordinators.get(world);
        if(points!=null)points.remove(pos);
        cachedNetworks.remove(world);
    }
    public static void forgetDrill(ServerLevel world,BlockPos pos){
        Set<BlockPos> points=drills.get(world);
        if(points!=null)points.remove(pos);
    }
    private static boolean nearby(BlockPos a,BlockPos b){
        return a.distSqr(b)<=RANGE*RANGE;
    }
    /** Either half of the placed 2-block machine may be in CC range. */
    private static double drillDistanceSq(BlockPos cc,DrillEntity drill){
        return Math.min(cc.distSqr(drill.getBlockPos()),
            cc.distSqr(drill.housingPosition()));
    }
    public static boolean nearDrill(BlockPos cc,DrillEntity drill){
        return drillDistanceSq(cc,drill)<=RANGE*RANGE;
    }
    /** Search both head and body, without checking/chunk-loading air along the way. */
    public static List<CoordinatorEntity> coordinators(ServerLevel world,DrillEntity drill){
        Map<BlockPos,CoordinatorEntity> found=new HashMap<>();
        for(BlockPos point:new BlockPos[]{drill.getBlockPos(),drill.housingPosition()})
            for(CoordinatorEntity cc:coordinators(world,point))
                if(nearDrill(cc.getBlockPos(),drill))
                    found.put(cc.getBlockPos(),cc);
        List<CoordinatorEntity> result=new ArrayList<>(found.values());
        result.sort(Comparator.comparingDouble(cc->drillDistanceSq(cc.getBlockPos(),drill)));
        return result;
    }
    /**
     * A paired drill must use a CC on its computer's component, rather than
     * silently choosing a closer but unrelated warehouse/rig CC network.
     * An unpaired drill can still be auto-discovered from nearby CCs.
     */
    public static CoordinatorEntity nearestDrillCoordinator(ServerLevel world,DrillEntity drill){
        List<CoordinatorEntity> nearby=coordinators(world,drill);
        if(nearby.isEmpty())return null;
        BlockPos computerPos=drill.pairedComputerPosition(world);
        if(computerPos!=null&&world.hasChunkAt(computerPos)
                &&world.getBlockEntity(computerPos) instanceof ComputerEntity computer){
            Set<BlockPos> owned=new HashSet<>();
            for(CoordinatorEntity cc:network(world,computer))owned.add(cc.getBlockPos());
            for(CoordinatorEntity cc:nearby)
                if(owned.contains(cc.getBlockPos()))return cc;
            return null;
        }
        return nearby.get(0);
    }
    /** Select the visual face pointing towards the nearer machine half. */
    public static net.minecraft.core.Direction drillLinkFace(BlockPos cc,DrillEntity drill){
        BlockPos head=drill.getBlockPos(),body=drill.housingPosition();
        BlockPos target=cc.distSqr(body)<=cc.distSqr(head)?body:head;
        int x=target.getX()-cc.getX(),y=target.getY()-cc.getY(),
            z=target.getZ()-cc.getZ();
        int ax=Math.abs(x),ay=Math.abs(y),az=Math.abs(z);
        if(ay>=ax&&ay>=az)return y<0
            ?net.minecraft.core.Direction.DOWN:net.minecraft.core.Direction.UP;
        if(ax>=az)return x<0
            ?net.minecraft.core.Direction.WEST:net.minecraft.core.Direction.EAST;
        return z<0?net.minecraft.core.Direction.NORTH:net.minecraft.core.Direction.SOUTH;
    }
    public static List<CoordinatorEntity> coordinators(ServerLevel world,BlockPos origin){
        List<CoordinatorEntity> result=new ArrayList<>();
        Set<BlockPos> positions=coordinators.get(world);
        if(positions==null)return result;
        for(BlockPos pos:new ArrayList<>(positions)){
            if(!nearby(origin,pos)||!world.hasChunkAt(pos))continue;
            if(world.getBlockEntity(pos) instanceof CoordinatorEntity cc)result.add(cc);
            else positions.remove(pos);
        }
        result.sort(Comparator.comparingDouble(cc->cc.getBlockPos().distSqr(origin)));
        return result;
    }
    public static CoordinatorEntity nearest(ServerLevel world,BlockPos origin){
        List<CoordinatorEntity> candidates=coordinators(world,origin);
        return candidates.isEmpty()?null:candidates.get(0);
    }
    public static List<DrillEntity> drills(ServerLevel world,BlockPos origin){
        List<DrillEntity> result=new ArrayList<>();
        Set<BlockPos> positions=drills.get(world);
        if(positions==null)return result;
        for(BlockPos pos:new ArrayList<>(positions)){
            // Horizontal machines have a housing block behind their head.
            // A CC within 24 blocks of that housing can service the drill
            // even if the leading bit is one more block away.
            if(!world.hasChunkAt(pos))continue;
            if(world.getBlockEntity(pos) instanceof DrillEntity drill){
                if(nearDrill(origin,drill))result.add(drill);
            }else positions.remove(pos);
        }
        result.sort(Comparator.comparingDouble(d->drillDistanceSq(origin,d)));
        return result;
    }
    public static List<DrillEntity> networkDrills(ServerLevel world,ComputerEntity computer){
        List<DrillEntity> result=new ArrayList<>();
        Set<BlockPos> seen=new HashSet<>();
        for(CoordinatorEntity cc:network(world,computer))
            for(DrillEntity drill:drills(world,cc.getBlockPos()))
                if(seen.add(drill.getBlockPos()))result.add(drill);
        return result;
    }
    public static boolean joined(ServerLevel world,ComputerEntity computer,DrillEntity drill){
        for(CoordinatorEntity cc:network(world,computer))
            if(nearDrill(cc.getBlockPos(),drill))return true;
        return false;
    }
}
