package dev.casz.miningrig;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;

/**
 * One non-persistent simulation ticket for each active chunk, shared by
 * computers. Multiple drills/computers cannot accidentally release each
 * other's ticket. The entire footprint (plus the computer) is simulated.
 */
public final class RigChunkTickets {
    private RigChunkTickets(){}
    private static final TicketType ACTIVE=Registry.register(
        BuiltInRegistries.TICKET_TYPE,MiningRig.id("active_mining_chunk"),
        new TicketType(TicketType.NO_TIMEOUT,TicketType.FLAG_LOADING|TicketType.FLAG_SIMULATION));
    private static final Map<ServerLevel, Map<BlockPos,Set<Long>>> OWNERS=new WeakHashMap<>();

    public static void initialize() {}

    private static boolean usedByOther(Map<BlockPos,Set<Long>> perOwner,BlockPos owner,long chunk){
        for(var entry:perOwner.entrySet())
            if(!entry.getKey().equals(owner)&&entry.getValue().contains(chunk))return true;
        return false;
    }
    public static void sync(ServerLevel world,BlockPos computer,Set<Long> requested){
        Map<BlockPos,Set<Long>> owners=OWNERS.computeIfAbsent(world,k->new HashMap<>());
        BlockPos key=computer.immutable();
        Set<Long> old=owners.getOrDefault(key,Set.of());
        for(long chunk:requested){
            if(!old.contains(chunk)&&!usedByOther(owners,key,chunk))
                world.getChunkSource().addTicketWithRadius(ACTIVE,new ChunkPos(chunk),0);
        }
        for(long chunk:old){
            if(!requested.contains(chunk)&&!usedByOther(owners,key,chunk))
                world.getChunkSource().removeTicketWithRadius(ACTIVE,new ChunkPos(chunk),0);
        }
        if(requested.isEmpty())owners.remove(key);
        else owners.put(key,new HashSet<>(requested));
        if(owners.isEmpty())OWNERS.remove(world);
    }
    public static void release(ServerLevel world,BlockPos computer){sync(world,computer,Set.of());}
    public static void cover(Set<Long> desired,BlockPos drill,int radius){
        int minX=(drill.getX()-radius)>>4,maxX=(drill.getX()+radius)>>4;
        int minZ=(drill.getZ()-radius)>>4,maxZ=(drill.getZ()+radius)>>4;
        for(int x=minX;x<=maxX;x++)for(int z=minZ;z<=maxZ;z++)
            desired.add(ChunkPos.asLong(x,z));
    }
}
