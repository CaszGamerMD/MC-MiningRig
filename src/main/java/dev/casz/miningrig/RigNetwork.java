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
    public static final int FILLER=1, WALL_PAUSE=2, WALL_CONTINUE=3, OUTPUT=4, HARDWARE=5;
    private record Source(CoordinatorEntity coordinator,Direction side,Container container,BlockPos pos,int distance){}

    public static List<CoordinatorEntity> members(ServerLevel world,DrillEntity drill){
        CoordinatorEntity start=nearestDrillCoordinator(world,drill);
        return start==null?List.of():component(world,start);
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
            if((hasOutputHere(world,cc)||nextTowardOutput(world,cc)!=null)
                    && cc.canReceive(cargo) && cc.receive(cargo))return true;
        return false;
    }
    /** Count also material whose prefetch flight has not reached the CC. */
    public static boolean supplyDepotPending(ServerLevel world,DrillEntity drill,
            boolean walls){
        CoordinatorEntity depot=nearestDrillCoordinator(world,drill);
        return depot!=null && depot.cachedSupplyPending(walls)>0;
    }
    public static boolean fillerDepotPending(ServerLevel world,DrillEntity drill){
        return supplyDepotPending(world,drill,false);
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
    public static boolean isHardware(ItemStack stack){
        return stack.getItem() instanceof DrillBit || stack.is(MiningRig.COORDINATOR.asItem());
    }
    /** Reserve a single hardware item along the connected CC network. */
    @org.jetbrains.annotations.Nullable public static Supply reserveHardware(ServerLevel world,
            CoordinatorEntity destination,boolean coordinator){
        for(CoordinatorEntity cc:component(world,destination))
            for(Direction side:Direction.values()){
                if(cc.portMode(side)!=HARDWARE)continue;
                Container box=cc.external(world,side);
                if(box==null)continue;
                Direction face=side.getOpposite();
                int[] slots=box instanceof WorldlyContainer sided?sided.getSlotsForFace(face):null;
                int count=slots==null?box.getContainerSize():slots.length;
                for(int i=0;i<count;i++){
                    int slot=slots==null?i:slots[i];
                    if(slot<0||slot>=box.getContainerSize())continue;
                    ItemStack stack=box.getItem(slot);
                    if(stack.isEmpty()||!isHardware(stack)
                        ||(coordinator?!stack.is(MiningRig.COORDINATOR.asItem())
                            :!(stack.getItem() instanceof DrillBit)))continue;
                    if(box instanceof WorldlyContainer sided &&
                        !sided.canTakeItemThroughFace(slot,stack,face))continue;
                    List<BlockPos> route=routeBetween(world,cc,destination);
                    if(route.isEmpty())continue;
                    ItemStack result=box.removeItem(slot,1);
                    if(result.isEmpty())continue;
                    box.setChanged();
                    List<BlockPos> path=new ArrayList<>();
                    path.add(cc.getBlockPos().relative(side));path.addAll(route);
                    return new Supply(result,path,cc.getBlockPos().relative(side),face);
                }
            }
        return null;
    }
    private static List<BlockPos> routeBetween(ServerLevel world,
            CoordinatorEntity source,CoordinatorEntity target){
        Queue<CoordinatorEntity> queue=new ArrayDeque<>();
        Map<BlockPos,BlockPos> prev=new HashMap<>();
        Set<BlockPos> visited=new HashSet<>();
        queue.add(source);visited.add(source.getBlockPos());
        while(!queue.isEmpty()){
            CoordinatorEntity cc=queue.remove();
            if(cc.getBlockPos().equals(target.getBlockPos()))break;
            for(Direction side:Direction.values()){
                CoordinatorEntity other=cc.nearest(world,side);
                if(other!=null && visited.add(other.getBlockPos())){
                    prev.put(other.getBlockPos(),cc.getBlockPos());queue.add(other);
                }
            }
        }
        if(!visited.contains(target.getBlockPos()))return List.of();
        List<BlockPos> positions=new ArrayList<>();
        BlockPos pos=target.getBlockPos();
        positions.add(pos);
        while(!pos.equals(source.getBlockPos())){
            pos=prev.get(pos);if(pos==null)return List.of();
            positions.add(0,pos);
        }
        return positions;
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
        // Head and housing make up ONE drill, and already-linked drills must
        // dispatch/receive cargo through the same CC graph as their computer.
        return RigTopology.nearestDrillCoordinator(world,drill);
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
        CoordinatorEntity start=nearestDrillCoordinator(world,drill);
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
        if(max>0){
            CoordinatorEntity near=nearestDrillCoordinator(world,drill);
            if(near!=null){
                Supply staged=near.takeCachedSupply(world,drill,walls,max);
                if(staged!=null)return staged;
                // A Wall shipment has already left its input chest and is
                // on the way to the depot. Wait for it instead of reserving
                // a second copy directly from the same container.
                if(walls && near.cachedSupplyPending(true)>0)return null;
            }
        }
        return reserveFromInput(world,drill,walls,max);
    }
    /** For CC prefetch: bypass its own cache to avoid recirculation. */
    public static Supply reserveForCache(ServerLevel world,DrillEntity drill,
            boolean walls,int amount){
        Supply cargo=reserveFromInput(world,drill,walls,amount);
        if(cargo==null)return null;
        List<BlockPos> path=new ArrayList<>(cargo.path());
        if(!path.isEmpty()&&path.get(path.size()-1).equals(drill.getBlockPos()))
            path.remove(path.size()-1);
        return new Supply(cargo.stack(),path,cargo.sourcePos(),cargo.sourceFace());
    }
    private record WallCandidate(Source source,int slot,List<BlockPos> route){}

    /**
     * Random wall selection acts on the actual input inventory slots.
     * Pull one block per draw so a large first chest stack cannot monopolize
     * the 64-item wall bundle or the CC's 192-item prefetch depot.
     */
    private static Supply reserveRandomWall(ServerLevel world,DrillEntity drill){
        List<WallCandidate> options=new ArrayList<>();
        List<WallCandidate> alternatives=new ArrayList<>();
        for(Source source:sources(world,drill,true)){
            List<BlockPos> hops=route(world,source.coordinator(),drill);
            if(hops.isEmpty())continue;
            Container container=source.container();
            Direction face=source.side().getOpposite();
            int[] slots=container instanceof WorldlyContainer sided
                ?sided.getSlotsForFace(face):null;
            int n=slots==null?container.getContainerSize():slots.length;
            for(int i=0;i<n;i++){
                int slot=slots==null?i:slots[i];
                if(slot<0||slot>=container.getContainerSize())continue;
                ItemStack item=container.getItem(slot);
                if(!isFiller(item))continue;
                if(container instanceof WorldlyContainer sided &&
                        !sided.canTakeItemThroughFace(slot,item,face))continue;
                WallCandidate candidate=new WallCandidate(source,slot,hops);
                options.add(candidate);
                if(!drill.wasLastWallSource(source.pos(),slot))alternatives.add(candidate);
            }
        }
        if(options.isEmpty())return null;
        List<WallCandidate> pool=alternatives.isEmpty()?options:alternatives;
        WallCandidate selected=pool.get(world.getRandom().nextInt(pool.size()));
        Source source=selected.source();
        Direction face=source.side().getOpposite();
        ItemStack taken=source.container().removeItem(selected.slot(),1);
        if(taken.isEmpty())return null;
        source.container().setChanged();
        drill.recordWallSource(source.pos(),selected.slot());
        List<BlockPos> path=new ArrayList<>();
        path.add(source.pos());path.addAll(selected.route());
        return new Supply(taken,path,source.pos(),face);
    }
    private static Supply reserveFromInput(ServerLevel world,DrillEntity drill,boolean walls,int requested){
        int max=Math.max(0,Math.min(3,requested));
        if(max==0)return null;
        if(walls&&drill.randomWalls())return reserveRandomWall(world,drill);
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
        includeLoadedCCs(world,members(world,drill),chunks);
    }
    public static void includeLoadedCCs(ServerLevel world,List<CoordinatorEntity> connected,Set<ChunkPos> chunks){
        for(CoordinatorEntity cc:connected){
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
    /**
     * Determine which actual output faces can accept the candidate item.
     * A non-full container can still reject items (different stack, sided
     * restriction, non-stackable slots); inspect real slots rather than
     * treating an output port as a guaranteed destination.
     */
    public static boolean canOutput(ServerLevel world,CoordinatorEntity cc,ItemStack item){
        if(item.isEmpty())return false;
        for(Direction face:Direction.values()){
            if(cc.portMode(face)!=OUTPUT)continue;
            Container destination=cc.external(world,face);
            if(destination==null)continue;
            Direction incoming=face.getOpposite();
            int[] slots=destination instanceof WorldlyContainer sided
                ?sided.getSlotsForFace(incoming):null;
            int count=slots==null?destination.getContainerSize():slots.length;
            for(int n=0;n<count;n++){
                int slot=slots==null?n:slots[n];
                if(slot<0||slot>=destination.getContainerSize()
                    ||!destination.canPlaceItem(slot,item))continue;
                if(destination instanceof WorldlyContainer sided
                    && !sided.canPlaceItemThroughFace(slot,item,incoming))continue;
                ItemStack existing=destination.getItem(slot);
                if(!existing.isEmpty()
                    && !ItemStack.isSameItemSameComponents(existing,item))continue;
                int limit=Math.min(item.getMaxStackSize(),destination.getMaxStackSize(item));
                if(existing.getCount()<limit)return true;
            }
        }
        return false;
    }

    /** Shortest CC hop with output space for an actual packet, not merely
     * a configured output face. Avoid routing to blocked CCs and retry other
     * containers elsewhere on the network as their available slots change. */
    public static CoordinatorEntity nextTowardOutput(ServerLevel world,CoordinatorEntity start,
            ItemStack cargo){
        return nextTowardOutput(world,start,cargo,null);
    }
    public static CoordinatorEntity nextTowardOutput(ServerLevel world,CoordinatorEntity start,
            ItemStack cargo,BlockPos avoid){
        Queue<CoordinatorEntity> queue=new ArrayDeque<>();
        Map<BlockPos,CoordinatorEntity> firstHop=new HashMap<>();
        Set<BlockPos> seen=new HashSet<>();
        seen.add(start.getBlockPos());queue.add(start);
        while(!queue.isEmpty()&&seen.size()<512){
            CoordinatorEntity current=queue.remove();
            if(current!=start && (cargo==null
                ?hasOutputHere(world,current):canOutput(world,current,cargo)))
                return firstHop.get(current.getBlockPos());
            for(Direction dir:Direction.values()){
                CoordinatorEntity neighbor=current.nearest(world,dir);
                if(neighbor==null||neighbor.getBlockPos().equals(avoid)
                    ||!seen.add(neighbor.getBlockPos()))continue;
                // Do not send a packet into a CC with a blocked bundle, even
                // when other connected branches may still accept it.
                if(cargo!=null&&!neighbor.canReceive(List.of(cargo)))continue;
                firstHop.put(neighbor.getBlockPos(),current==start
                    ?neighbor:firstHop.get(current.getBlockPos()));
                queue.add(neighbor);
            }
        }
        return null;
    }
    public static CoordinatorEntity nextTowardOutput(ServerLevel world,CoordinatorEntity start){
        return nextTowardOutput(world,start,null,null);
    }
}
