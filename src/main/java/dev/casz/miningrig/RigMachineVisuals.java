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
            if(display.getTags().contains(expected)){
                if(!live.contains(display.getUUID()))display.discard();
                continue;
            }
            if(!legacy || display.getTags().contains(MARK)
                    || !display.getTags().isEmpty())continue;
            // Older releases did not tag transit entities. Their distinctive
            // 0.55x scale, no-gravity ground transform, and proximity to the CC
            // identify abandoned packet models without deleting regular drops.
            if(!display.isNoGravity() || display.distanceToSqr(
                ccPos.getX()+.5,ccPos.getY()+.5,ccPos.getZ()+.5)>12.25)continue;
            org.joml.Vector3f scale=display.getTransformation().getScale();
            if(Math.abs(scale.x-0.55f)<0.0002f
                &&Math.abs(scale.y-0.55f)<0.0002f
                &&Math.abs(scale.z-0.55f)<0.0002f
                &&!live.contains(display.getUUID()))display.discard();
        }
    }
}
