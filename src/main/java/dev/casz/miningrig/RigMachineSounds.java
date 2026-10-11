package dev.casz.miningrig;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

/**
 * Quiet positional machine sound beds in the BLOCKS audio category.
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
    public static final SoundEvent ALARM=register("computer_alarm");
    public static void initialize(){}
    private static SoundEvent builtin(String name){
        SoundEvent event=BuiltInRegistries.SOUND_EVENT.getValue(Identifier.withDefaultNamespace(name));
        return event==null?DRILL_GRIND:event;
    }

    /** Stagger neighboring CC loops, preventing all coordinators firing in
     * the same server tick. Sound naturally stops when a CC unloads/breaks. */
    public static void coordinatorTick(ServerLevel level,BlockPos at){
        long phase=Math.floorMod((long)at.hashCode(),60);
        if((level.getGameTime()+phase)%60L!=0L)return;
        level.playSound(null,at,builtin("block.beacon.ambient"),SoundSource.BLOCKS,0.34f,0.85f);
    }

    public static void problem(ServerLevel level,BlockPos at){
        level.playSound(null,at,builtin("block.note_block.pling"),SoundSource.BLOCKS,0.85f,1.8f);
    }
    /** Caller runs only during actual solid-block excavation. */
    public static void cutting(ServerLevel level,BlockPos at){
        level.playSound(null,at,builtin("block.grindstone.use"),SoundSource.BLOCKS,0.62f,0.85f);
    }
}
