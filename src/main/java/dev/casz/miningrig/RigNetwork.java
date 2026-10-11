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
    /**
     * Return unneeded reserved wall/filler material to the connected CC
     * network's ordinary output stream without filling the drill's 64-item
     * mining buffer and prematurely stopping the restarted excavation.
     */
    public static boolean returnSurplus(ServerLevel world,DrillEntity drill,ItemStack stack){
        if(stack.isEmpty())return true;
        List<ItemStack> cargo=List.of(stack);
        for(CoordinatorEntity cc:members(world,drill))
            if(cc.canReceive(cargo) && cc.receive(cargo))return true;
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
        // The nearest designated Walls container owns the empty behavior.
        // Alternative wall containers are still searched first for material.
        List<Source> options=sources(world,drill,true);
        return !options.isEmpty()
            && options.get(0).coordinator().portMode(options.get(0).side())==WALL_CONTINUE;
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
            && !state.canBeReplaced()
            && !state.is(MiningRig.DRILL) && !state.is(MiningRig.COORDINATOR)
            && !state.is(MiningRig.COMPUTER);
    }
    /** One requested material packet and its real path through linked CCs. */
    /**
     * Each packet remembers its original storage to return unused supplies to
     * the input first, even after CC staging, drill movement and world saves.
     */
    public record Supply(ItemStack stack,List<BlockPos> path,
            @org.jetbrains.annotations.Nullable BlockPos sourcePos,
            @org.jetbrains.annotations.Nullable Direction sourceFace){
        public Supply(ItemStack stack,List<BlockPos> path){this(stack,path,null,null);}
    }
    @org.jetbrains.annotations.Nullable
    public static CoordinatorEntity nearestDrillCoordinator(ServerLevel world,DrillEntity drill){
        for(int y=1;y<=20;y++){
            BlockPos p=drill.getBlockPos().above(y);
            if(!world.hasChunkAt(p))break;
            if(world.getBlockEntity(p) instanceof CoordinatorEntity cc)return cc;
        }
        return null;
    }
    /**
     * Insert unused material back into the original Filler/Wall input (only
     * when that same storage remains loaded/connected). Return any overflow
     * for the regular output network; never replace items in occupied slots.
     */
    public static ItemStack returnToSource(ServerLevel world,Supply cargo){
        ItemStack remaining=cargo.stack().copy();
        if(remaining.isEmpty()||cargo.sourcePos()==null||cargo.sourceFace()==null
                ||!world.hasChunkAt(cargo.sourcePos()))return remaining;
        if(!(world.getBlockEntity(cargo.sourcePos()) instanceof Container container))
            return remaining;
        Direction side=cargo.sourceFace();
        int[] slots=container instanceof WorldlyContainer sided
            ?sided.getSlotsForFace(side):null;
        int n=slots==null?container.getContainerSize():slots.length;
        // Merge with matching stacks first; use empty slots afterward.
        for(int pass=0;pass<2 && !remaining.isEmpty();pass++)
            for(int i=0;i<n && !remaining.isEmpty();i++){
                int slot=slots==null?i:slots[i];
                if(slot<0||slot>=container.getContainerSize()
                        ||!container.canPlaceItem(slot,remaining))continue;
                if(container instanceof WorldlyContainer sided &&
                        !sided.canPlaceItemThroughFace(slot,remaining,side))continue;
                ItemStack present=container.getItem(slot);
                if((pass==0&&present.isEmpty())||(pass==1&&!present.isEmpty()))
                    continue;
                if(!present.isEmpty()&&!ItemStack.isSameItemSameComponents(present,remaining))
                    continue;
                int capacity=Math.min(remaining.getMaxStackSize(),
                    container.getMaxStackSize(remaining))-(present.isEmpty()?0:present.getCount());
                int amount=Math.min(remaining.getCount(),Math.max(0,capacity));
                if(amount<=0)continue;
                if(present.isEmpty())container.setItem(slot,remaining.copyWithCount(amount));
                else {present.grow(amount);container.setChanged();}
                remaining.shrink(amount);
            }
        return remaining;
    }
    private static List<BlockPos> route(ServerLevel world,CoordinatorEntity source,DrillEntity drill){
        CoordinatorEntity start=null;
        for(int i=1;i<=20;i++){
            BlockPos p=drill.getBlockPos().above(i);
            if(!world.hasChunkAt(p))break;
            if(world.getBlockEntity(p) instanceof CoordinatorEntity cc){start=cc;break;}
        }
        if(start==null)return List.of();
        Map<BlockPos,BlockPos> prev=new HashMap<>();
        Queue<CoordinatorEntity> todo=new ArrayDeque<>();
        Set<BlockPos> visited=new HashSet<>();
        todo.add(source);visited.add(source.getBlockPos());
        while(!todo.isEmpty() && visited.size()<512){
            CoordinatorEntity cc=todo.remove();
            if(cc.getBlockPos().equals(start.getBlockPos()))break;
            for(Direction dir:Direction.values()){
                CoordinatorEntity next=cc.nearest(world,dir);
                if(next==null||!visited.add(next.getBlockPos()))continue;
                prev.put(next.getBlockPos(),cc.getBlockPos());
                todo.add(next);
            }
        }
        if(!visited.contains(start.getBlockPos()))return List.of();
        List<BlockPos> path=new ArrayList<>();
        BlockPos current=start.getBlockPos();
        path.add(current);
        while(!current.equals(source.getBlockPos())){
            current=prev.get(current);
            if(current==null)return List.of();
            path.add(0,current);
        }
        path.add(drill.getBlockPos());
        return path;
    }
    /**
     * The drill requests a NUMBER of wall blocks after it finishes clearing
     * the ring. Each network packet carries up to three of one usable block
     * type, taking from the nearest stocked, correctly configured wall input.
     * The item leaves source inventory only when this reservation succeeds.
     */
    public static Supply reservePacket(ServerLevel world,DrillEntity drill,boolean walls,int requested){
        int max=Math.max(0,Math.min(3,requested));
        if(!walls&&max>0){
            CoordinatorEntity near=nearestDrillCoordinator(world,drill);
            if(near!=null){
                Supply staged=near.takeCachedFiller(world,drill,max);
                if(staged!=null)return staged;
            }
        }
        return reserveFromInput(world,drill,walls,max);
    }
    /** For CC prefetch: bypass its own cache to avoid recirculation. */
    public static Supply reserveForCache(ServerLevel world,DrillEntity drill,int amount){
        Supply cargo=reserveFromInput(world,drill,false,amount);
        if(cargo==null)return null;
        List<BlockPos> path=new ArrayList<>(cargo.path());
        if(!path.isEmpty()&&path.get(path.size()-1).equals(drill.getBlockPos()))
            path.remove(path.size()-1);
        return new Supply(cargo.stack(),path,cargo.sourcePos(),cargo.sourceFace());
    }
    private static Supply reserveFromInput(ServerLevel world,DrillEntity drill,boolean walls,int requested){
        int max=Math.max(0,Math.min(3,requested));
        if(max==0)return null;
        for(Source source:sources(world,drill,walls)){
            List<BlockPos> hops=route(world,source.coordinator(),drill);
            if(hops.isEmpty())continue;
            Container from=source.container();
            Direction side=source.side().getOpposite();
            int[] slots=from instanceof WorldlyContainer sided?sided.getSlotsForFace(side):null;
            int count=slots==null?from.getContainerSize():slots.length;
            for(int i=0;i<count;i++){
                int slot=slots==null?i:slots[i];
                if(slot<0||slot>=from.getContainerSize())continue;
                ItemStack available=from.getItem(slot);
                if(!isFiller(available))continue;
                if(from instanceof WorldlyContainer sided
                        && !sided.canTakeItemThroughFace(slot,available,side))continue;
                ItemStack taken=from.removeItem(slot,Math.min(max,available.getCount()));
                if(taken.isEmpty())continue;
                from.setChanged();
                List<BlockPos> path=new ArrayList<>();
                path.add(source.pos());
                path.addAll(hops);
                return new Supply(taken,path,source.pos(),side);
            }
        }
        return null;
    }
    /** Reserve exactly one block for an actual requested drill delivery. */
    public static Supply reserve(ServerLevel world,DrillEntity drill,boolean walls){
        for(Source source:sources(world,drill,walls)){
            List<BlockPos> hops=route(world,source.coordinator(),drill);
            if(hops.isEmpty())continue;
            Container from=source.container();
            Direction face=source.side().getOpposite();
            int[] slots=from instanceof WorldlyContainer side?side.getSlotsForFace(face):null;
            int n=slots==null?from.getContainerSize():slots.length;
            for(int i=0;i<n;i++){
                int slot=slots==null?i:slots[i];
                if(slot<0||slot>=from.getContainerSize())continue;
                ItemStack stack=from.getItem(slot);
                if(!isFiller(stack))continue;
                if(from instanceof WorldlyContainer side &&
                        !side.canTakeItemThroughFace(slot,stack,face))continue;
                ItemStack taken=from.removeItem(slot,1);
                if(taken.isEmpty())continue;
                from.setChanged();
                List<BlockPos> fullPath=new ArrayList<>();
                fullPath.add(source.pos());
                fullPath.addAll(hops);
                return new Supply(taken,fullPath,source.pos(),face);
            }
        }
        return null;
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
