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
            if(!nearby(origin,pos)||!world.hasChunkAt(pos))continue;
            if(world.getBlockEntity(pos) instanceof DrillEntity drill)result.add(drill);
            else positions.remove(pos);
        }
        result.sort(Comparator.comparingDouble(d->d.getBlockPos().distSqr(origin)));
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
            if(nearby(cc.getBlockPos(),drill.getBlockPos()))return true;
        return false;
    }
}
