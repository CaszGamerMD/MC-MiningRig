package dev.casz.miningrig;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

public final class RigPairing {
    private RigPairing(){}
    private record Selection(ResourceKey<Level> dimension,BlockPos drill,long at){}
    private static final Map<UUID,Selection> CHOICES=new HashMap<>();
    public static void select(ServerLevel level,Player player,BlockPos drill){
        CHOICES.put(player.getUUID(),new Selection(level.dimension(),drill.immutable(),level.getGameTime()));
    }
    public static boolean linkSelected(ServerLevel level,Player player,ComputerEntity computer){
        Selection choice=CHOICES.remove(player.getUUID());
        if(choice==null || !choice.dimension().equals(level.dimension())
                || level.getGameTime()<choice.at() || level.getGameTime()-choice.at()>6000
                || !level.hasChunkAt(choice.drill()) || !(level.getBlockEntity(choice.drill()) instanceof DrillEntity))
            return false;
        computer.link(choice.drill());return true;
    }
}
