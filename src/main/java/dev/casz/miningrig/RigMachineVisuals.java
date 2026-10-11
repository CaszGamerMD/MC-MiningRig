package dev.casz.miningrig;

import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Display;
import net.minecraft.world.phys.AABB;

/**
 * ItemDisplay particles for machine cargo are visuals, not item ownership.
 * They must never survive after their packet owner has unloaded/reloaded or
 * cancelled a flight. Cargo itself is retained in the machines' NBT.
 */
final class RigMachineVisuals {
    private RigMachineVisuals(){}
    private static final String MARK="miningrig.transit";
    private static String owner(BlockPos pos){return "miningrig.owner."+pos.asLong();}
    static void tag(Display.ItemDisplay entity,BlockPos pos){
        entity.addTag(MARK);
        entity.addTag(owner(pos));
    }
    static void cleanupCoordinator(ServerLevel world,BlockPos ccPos,Set<UUID> live,
            boolean legacy){
        String expected=owner(ccPos);
        for(Display.ItemDisplay display:world.getEntitiesOfClass(
                Display.ItemDisplay.class,new AABB(ccPos).inflate(32))){
            if(display.entityTags().contains(expected)){
                if(!live.contains(display.getUUID()))display.discard();
                continue;
            }
            if(!legacy || display.entityTags().contains(MARK)
                    || !display.entityTags().isEmpty())continue;
            // Older releases did not tag transit entities. Their distinctive
            // 0.55x scale, no-gravity ground transform, and proximity to the CC
            // identify abandoned packet models without deleting regular drops.
            // The previous untagged 0.55-size packets usually remain
            // against CC faces after a server restart. Only remove old
            // no-gravity *block* displays in the immediate CC footprint.
            // Never touch tagged player/entity displays or any live packet.
            if(!display.isNoGravity() || display.tickCount<200
                || display.getItemTransform()!=net.minecraft.world.item.ItemDisplayContext.GROUND
                || display.distanceToSqr(ccPos.getX()+.5,
                    ccPos.getY()+.5,ccPos.getZ()+.5)>3.24)continue;
            if(display.getItemStack().getItem() instanceof net.minecraft.world.item.BlockItem
                && !live.contains(display.getUUID()))display.discard();
        }
    }
}
