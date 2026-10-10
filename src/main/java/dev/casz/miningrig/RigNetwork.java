package dev.casz.miningrig;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Auto-discovers a collection network. All CC-to-CC links are bidirectional;
 * non-CC connections are one-way and never pulled unless a drill requests
 * filler or wall blocks. No chunks are forcibly loaded during graph search.
 */
public final class RigNetwork {
    private RigNetwork(){}
    public static final int FILLER=1, WALL_PAUSE=2, WALL_CONTINUE=3, OUTPUT=4;
    private record Source(CoordinatorEntity coordinator,Direction side,Container container,BlockPos pos,int distance){}

    public static List<CoordinatorEntity> members(ServerLevel world,DrillEntity drill){
        CoordinatorEntity start=null;
        BlockPos at=drill.getBlockPos();
        for(int y=1;y<=20;y++){
            BlockPos p=at.above(y);
            if(!world.hasChunkAt(p))break;
            if(world.getBlockEntity(p) instanceof CoordinatorEntity cc){start=cc;break;}
        }
        if(start==null)return List.of();
        return component(world,start);
    }
    public static List<CoordinatorEntity> component(ServerLevel world,CoordinatorEntity start){
        Queue<CoordinatorEntity> pending=new ArrayDeque<>();
        Set<BlockPos> seen=new HashSet<>();
        List<CoordinatorEntity> result=new ArrayList<>();
        pending.add(start);
        while(!pending.isEmpty() && result.size()<512){
            CoordinatorEntity cc=pending.remove();
            if(!seen.add(cc.getBlockPos()))continue;
            result.add(cc);
            for(Direction dir:Direction.values()){
                CoordinatorEntity neighbor=cc.nearest(world,dir);
                if(neighbor!=null&&!seen.contains(neighbor.getBlockPos()))pending.add(neighbor);
            }
        }
        return result;
    }
    public static boolean hasInput(ServerLevel world,DrillEntity drill,boolean walls){
        for(CoordinatorEntity cc:members(world,drill))
            for(Direction side:Direction.values())
                if((walls ? cc.isWallInput(side):cc.isFillerInput(side)) && cc.external(world,side)!=null)
                    return true;
        return false;
    }
    public static boolean hasOutput(ServerLevel world,DrillEntity drill){
        for(CoordinatorEntity cc:members(world,drill))
            if(hasOutputHere(world,cc))return true;
        return false;
    }
    public static boolean hasOutputHere(ServerLevel world,CoordinatorEntity cc){
        for(Direction side:Direction.values())
            if(cc.portMode(side)==OUTPUT && cc.external(world,side)!=null)return true;
        return false;
    }
    public static boolean wallsCanContinue(ServerLevel world,DrillEntity drill){
        boolean any=false;
        for(CoordinatorEntity cc:members(world,drill))
            for(Direction side:Direction.values())if(cc.isWallInput(side) && cc.external(world,side)!=null){
                any=true;
                if(cc.portMode(side)==WALL_PAUSE)return false;
            }
        return any;
    }
    private static List<Source> sources(ServerLevel world,DrillEntity drill,boolean walls){
        List<Source> result=new ArrayList<>();
        BlockPos origin=drill.getBlockPos();
        for(CoordinatorEntity cc:members(world,drill))
            for(Direction side:Direction.values()){
                if(!(walls?cc.isWallInput(side):cc.isFillerInput(side)))continue;
                Container container=cc.external(world,side);
                if(container==null)continue;
                BlockPos p=cc.getBlockPos().relative(side);
                int distance=Math.abs(origin.getX()-p.getX())+Math.abs(origin.getY()-p.getY())+
                    Math.abs(origin.getZ()-p.getZ());
                result.add(new Source(cc,side,container,p,distance));
            }
        result.sort(Comparator.comparingInt(Source::distance));
        return result;
    }
    public static boolean isFiller(ItemStack stack){
        if(!(stack.getItem() instanceof BlockItem item))return false;
        BlockState state=item.getBlock().defaultBlockState();
        return !state.hasBlockEntity() && !state.isAir()
            && state.getFluidState().isEmpty()
            && !state.is(MiningRig.DRILL) && !state.is(MiningRig.COORDINATOR)
            && !state.is(MiningRig.COMPUTER);
    }
    /**
     * Pull up to three blocks per request from the closest available input.
     * The source is never vacuumed into a CC without a drill request.
     */
    public static List<ItemStack> request(ServerLevel world,DrillEntity drill,boolean walls,int limit){
        List<ItemStack> cargo=new ArrayList<>();
        int remaining=Math.max(0,Math.min(3,limit));
        for(Source source:sources(world,drill,walls)){
            if(remaining==0)break;
            Container from=source.container();
            Direction access=source.side().getOpposite();
            int[] slots=from instanceof WorldlyContainer sided?sided.getSlotsForFace(access):null;
            int amount=slots==null?from.getContainerSize():slots.length;
            for(int i=0;i<amount && remaining>0;i++){
                int slot=slots==null?i:slots[i];
                if(slot<0 || slot>=from.getContainerSize())continue;
                ItemStack stack=from.getItem(slot);
                if(!isFiller(stack))continue;
                if(from instanceof WorldlyContainer sided
                    && !sided.canTakeItemThroughFace(slot,stack,access))continue;
                ItemStack taken=from.removeItem(slot,Math.min(remaining,stack.getCount()));
                if(taken.isEmpty())continue;
                cargo.add(taken);
                remaining-=taken.getCount();
            }
            if(!cargo.isEmpty())from.setChanged();
        }
        return cargo;
    }
    public static boolean hasAvailable(ServerLevel world,DrillEntity drill,boolean walls){
        for(Source source:sources(world,drill,walls)){
            Container from=source.container();
            Direction access=source.side().getOpposite();
            int[] slots=from instanceof WorldlyContainer sided?sided.getSlotsForFace(access):null;
            int n=slots==null?from.getContainerSize():slots.length;
            for(int i=0;i<n;i++){
                int slot=slots==null?i:slots[i];
                if(slot>=0&&slot<from.getContainerSize()&&isFiller(from.getItem(slot))
                    && (!(from instanceof WorldlyContainer sided) ||
                    sided.canTakeItemThroughFace(slot,from.getItem(slot),access)))return true;
            }
        }
        return false;
    }
    /**
     * Pin the entire discovered CC graph and adjacent container chunks while
     * the owning drill runs. Also pin intermediate chunks in each wireless
     * straight line so unloaded gaps do not break neighbor discovery.
     */
    public static void includeLoadedNetwork(ServerLevel world,DrillEntity drill,Set<ChunkPos> chunks){
        for(CoordinatorEntity cc:members(world,drill)){
            BlockPos base=cc.getBlockPos();
            chunks.add(new ChunkPos(base.getX()>>4,base.getZ()>>4));
            for(Direction side:Direction.values()){
                BlockPos neighbor=base.relative(side);
                chunks.add(new ChunkPos(neighbor.getX()>>4,neighbor.getZ()>>4));
                CoordinatorEntity other=cc.nearest(world,side);
                if(other==null)continue;
                for(int n=1;n<=20;n++){
                    BlockPos p=base.relative(side,n);
                    chunks.add(new ChunkPos(p.getX()>>4,p.getZ()>>4));
                    if(p.equals(other.getBlockPos()))break;
                }
            }
        }
    }
    /** Shortest CC hop which leads to any accessible output container. */
    public static CoordinatorEntity nextTowardOutput(ServerLevel world,CoordinatorEntity start){
        Queue<CoordinatorEntity> queue=new ArrayDeque<>();
        Map<BlockPos,CoordinatorEntity> firstHop=new HashMap<>();
        Set<BlockPos> seen=new HashSet<>();
        seen.add(start.getBlockPos());queue.add(start);
        while(!queue.isEmpty()&&seen.size()<512){
            CoordinatorEntity current=queue.remove();
            if(current!=start && hasOutputHere(world,current))return firstHop.get(current.getBlockPos());
            for(Direction dir:Direction.values()){
                CoordinatorEntity neighbor=current.nearest(world,dir);
                if(neighbor==null||!seen.add(neighbor.getBlockPos()))continue;
                firstHop.put(neighbor.getBlockPos(),current==start?neighbor:firstHop.get(current.getBlockPos()));
                queue.add(neighbor);
            }
        }
        return null;
    }
}
