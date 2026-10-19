package dev.casz.miningrig;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import org.jetbrains.annotations.Nullable;

/**
 * Safely retrieve one idle CC into a configured storage port.
 *
 * Resolve and reserve a real insertable inventory slot BEFORE removing the
 * coordinator. A Hardware port wins; Output is used only if none can accept
 * it. Never replace items, force-load chunks or void overflow.
 */
public final class CoordinatorRetrieval {
    private CoordinatorRetrieval(){}

    private record Destination(BlockPos pos,Container inventory,int slot,
            Direction incoming,boolean hardware){}

    private static void say(Player player,String message){
        player.sendSystemMessage(Component.literal(message));
    }

    /** Use the same sided-insertion checks as regular CC output routing. */
    private static boolean accepts(Container inventory,int slot,Direction incoming,
            ItemStack stack) {
        if(slot<0 || slot>=inventory.getContainerSize()
                || !inventory.canPlaceItem(slot,stack))return false;
        if(inventory instanceof WorldlyContainer sided
                && !sided.canPlaceItemThroughFace(slot,stack,incoming))return false;
        ItemStack current=inventory.getItem(slot);
        if(!current.isEmpty()&&!ItemStack.isSameItemSameComponents(current,stack))
            return false;
        int cap=Math.min(stack.getMaxStackSize(),inventory.getMaxStackSize(stack));
        return current.getCount()<cap;
    }

    @Nullable private static Destination destination(ServerLevel world,
            List<CoordinatorEntity> network,int portMode,ItemStack stack){
        // Merge into an existing stack before using an empty slot. This avoids
        // filling a slot that would be useful for a different item.
        for(int pass=0;pass<2;pass++)
            for(CoordinatorEntity cc:network)
                for(Direction side:Direction.values()){
                    if(cc.portMode(side)!=portMode)continue;
                    Container dest=cc.external(world,side);
                    if(dest==null)continue;
                    BlockPos at=cc.getBlockPos().relative(side);
                    Direction incoming=side.getOpposite();
                    int[] faces=dest instanceof WorldlyContainer sided
                        ?sided.getSlotsForFace(incoming):null;
                    int count=faces==null?dest.getContainerSize():faces.length;
                    for(int n=0;n<count;n++){
                        int slot=faces==null?n:faces[n];
                        if(slot<0 || slot>=dest.getContainerSize())continue;
                        boolean occupied=!dest.getItem(slot).isEmpty();
                        if(occupied!=(pass==0))continue;
                        if(accepts(dest,slot,incoming,stack))
                            return new Destination(at,dest,slot,incoming,
                                portMode==RigNetwork.HARDWARE);
                    }
                }
        return null;
    }

    /** Called on a right-clicked coordinator while the organizer is in retrieval mode. */
    public static void recall(ServerLevel world,BlockPos pos,Player player,
            ItemStack organizer,Direction face) {
        // The target must be physically within the 100-block reach even when
        // invoked through a nearby block interaction rather than the air ray.
        net.minecraft.world.phys.Vec3 eye=player.getEyePosition();
        double nearX=Math.max(pos.getX(),Math.min(eye.x,pos.getX()+1.0));
        double nearY=Math.max(pos.getY(),Math.min(eye.y,pos.getY()+1.0));
        double nearZ=Math.max(pos.getZ(),Math.min(eye.z,pos.getZ()+1.0));
        double dx=eye.x-nearX,dy=eye.y-nearY,dz=eye.z-nearZ;
        if(dx*dx+dy*dy+dz*dz>
                CoordinatorGuideItem.RETRIEVAL_REACH*CoordinatorGuideItem.RETRIEVAL_REACH){
            say(player,"CC Organizer: retrieval target exceeds 100 blocks.");
            return;
        }
        if(!world.hasChunkAt(pos)
                || !(world.getBlockEntity(pos) instanceof CoordinatorEntity target)) {
            say(player,"CC Organizer: the targeted coordinator is no longer loaded.");
            return;
        }
        if(!world.mayInteract(player,pos)||!player.mayUseItemAt(pos,face,organizer)){
            say(player,"CC Organizer: you cannot retrieve this coordinator.");
            return;
        }
        // CCs own transit packets, reserve caches and ordinary output. Recall
        // only when completely idle, so no active items are unexpectedly lost
        // or spilled as a side effect of removing its block entity.
        if(target.hasRecallCargo()){
            say(player,"CC Organizer: coordinator is carrying items or supplies. "
                +"Empty it and finish in-flight deliveries before retrieval.");
            return;
        }

        List<CoordinatorEntity> network=RigNetwork.component(world,target);
        ItemStack recovered=new ItemStack(MiningRig.COORDINATOR);
        Destination store=destination(world,network,RigNetwork.HARDWARE,recovered);
        if(store==null)store=destination(world,network,RigNetwork.OUTPUT,recovered);
        if(store==null){
            say(player,"CC Organizer: no available Hardware or Output storage "
                +"on this coordinator's network. Retrieval cancelled.");
            return;
        }
        // Block and external inventory are manipulated on the same server tick.
        // Check that the destination still has room and exists before committing.
        if(!world.hasChunkAt(store.pos())
                || world.getBlockEntity(store.pos())!=store.inventory()
                || !accepts(store.inventory(),store.slot(),store.incoming(),recovered)){
            say(player,"CC Organizer: destination inventory changed; try again.");
            return;
        }
        if(!world.removeBlock(pos,false)){
            say(player,"CC Organizer: could not remove coordinator.");
            return;
        }

        Container inventory=world.getBlockEntity(store.pos()) instanceof Container present
            ?present:null;
        if(inventory!=store.inventory()
                || !accepts(inventory,store.slot(),store.incoming(),recovered)){
            // Never void the retrieved CC if a neighboring inventory vanished.
            Block.popResource(world,pos,recovered);
            say(player,"CC Organizer: storage became unavailable; retrieved CC dropped at its location.");
            return;
        }
        ItemStack existing=inventory.getItem(store.slot());
        if(existing.isEmpty())inventory.setItem(store.slot(),recovered);
        else {existing.grow(1);inventory.setChanged();}
        inventory.setChanged();
        CoordinatorGuideItem.onRecalled(organizer,pos);
        world.playSound(null,pos,SoundEvents.ANVIL_LAND,SoundSource.BLOCKS,.35f,1.75f);
        say(player,"CC Organizer: retrieved CC at "+pos.toShortString()+" into "
            +(store.hardware()?"Hardware":"Output")+" storage.");
    }

}
