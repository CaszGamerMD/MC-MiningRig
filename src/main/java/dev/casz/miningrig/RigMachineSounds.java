package dev.casz.miningrig;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

/**
 * Quiet, three-second, positional machine beds in the BLOCKS audio category.
 * Repeated only while the owning machine is ticking/actually cutting.
 * No global player audio, background music, or pickup-able sound entities.
 */
public final class RigMachineSounds {
    private RigMachineSounds(){}
    private static SoundEvent register(String name){
        Identifier id=MiningRig.id(name);
        return Registry.register(BuiltInRegistries.SOUND_EVENT,id,
            SoundEvent.createVariableRangeEvent(id));
    }
    public static final SoundEvent COORDINATOR_HUM=register("coordinator_hum");
    public static final SoundEvent DRILL_GRIND=register("drill_grind");
    public static void initialize(){}

    /** Stagger neighboring CC loops, preventing all coordinators firing in
     * the same server tick. Sound naturally stops when a CC unloads/breaks. */
    public static void coordinatorTick(ServerLevel level,BlockPos at){
        long phase=Math.floorMod((long)at.hashCode(),60);
        if((level.getGameTime()+phase)%60L!=0L)return;
        level.playSound(null,at,COORDINATOR_HUM,SoundSource.BLOCKS,0.10f,1.0f);
    }

    /** Caller runs only during actual solid-block excavation. */
    public static void cutting(ServerLevel level,BlockPos at){
        level.playSound(null,at,DRILL_GRIND,SoundSource.BLOCKS,0.36f,1.0f);
    }
}
