package dev.casz.miningrig;

import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.server.level.TicketType;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;

/**
 * Linked tablets are remote terminals only. The original computer remains
 * the sole authority for drill positions, settings and all four inventories.
 */
public final class TabletAccess {
    private TabletAccess(){}
    private static final TicketType SESSION=Registry.register(BuiltInRegistries.TICKET_TYPE,
        MiningRig.id("tablet_session"),new TicketType(TicketType.NO_TIMEOUT,
            TicketType.FLAG_LOADING|TicketType.FLAG_SIMULATION));
    private static final Map<ServerLevel,Map<ChunkPos,Integer>> SESSIONS=new WeakHashMap<>();
    public static void initialize(){}
    public static ServerLevel dimension(MinecraftServer server,String name){
        for(ServerLevel level:server.getAllLevels())
            if(level.dimension().identifier().toString().equals(name))return level;
        return null;
    }
    public static ComputerEntity find(MinecraftServer server,TabletLink.Target link,boolean load){
        if(link==null)return null;
        ServerLevel world=dimension(server,link.dimension());
        if(world==null)return null;
        if(load)world.getChunkAt(link.computer());
        else if(!world.hasChunkAt(link.computer()))return null;
        return world.getBlockEntity(link.computer()) instanceof ComputerEntity computer
            && computer.tabletIdentity().equals(link.identity())?computer:null;
    }
    public static int lamp(MinecraftServer server,TabletLink.Target link){
        ComputerEntity computer=find(server,link,false);
        if(computer==null)return 0;
        var state=computer.getBlockState();
        if(state.getValue(ComputerBlock.ALARM))return 3;
        if(state.getValue(ComputerBlock.WARN))return 2;
        return 1;
    }
    private static void acquire(ServerLevel world,BlockPos pos){
        ChunkPos key=new ChunkPos(pos);
        var map=SESSIONS.computeIfAbsent(world,w->new HashMap<>());
        int n=map.getOrDefault(key,0);
        if(n==0)world.getChunkSource().addTicketWithRadius(SESSION,key,0);
        map.put(key,n+1);
    }
    public static void release(ServerLevel world,BlockPos pos){
        Map<ChunkPos,Integer> map=SESSIONS.get(world);
        if(map==null)return;
        ChunkPos key=new ChunkPos(pos);
        int n=map.getOrDefault(key,0);
        if(n<=1){map.remove(key);world.getChunkSource().removeTicketWithRadius(SESSION,key,0);}
        else map.put(key,n-1);
        if(map.isEmpty())SESSIONS.remove(world);
    }
    public static void open(ServerPlayer player,TabletLink.Target link){
        if(link==null){
            player.sendSystemMessage(Component.literal("Unlinked tablet. Right-click a Mining Computer with it."));
            return;
        }
        ServerLevel world=dimension(player.getServer(),link.dimension());
        if(world==null){player.sendSystemMessage(Component.literal("Computer dimension unavailable."));return;}
        acquire(world,link.computer());
        ComputerEntity computer=find(player.getServer(),link,true);
        if(computer==null){
            release(world,link.computer());
            player.sendSystemMessage(Component.literal("Linked Mining Computer missing or replaced."));
            return;
        }
        final ComputerEntity resolved=computer;
        final BlockPos target=link.computer();
        player.openMenu(new MenuProvider(){
            @Override public Component getDisplayName(){return resolved.getDisplayName();}
            @Override public AbstractContainerMenu createMenu(int id,Inventory inventory,Player user){
                return new ComputerMenu(id,inventory,resolved,true,world,target);
            }
        });
    }
    public static void tickServer(MinecraftServer server){
        for(ServerPlayer player:server.getPlayerList().getPlayers()){
            long time=player.level().getGameTime();
            if(time%10!=0)continue;
            if(player.containerMenu instanceof ComputerMenu menu && menu.remote()
                    && menu.computer()!=null && menu.computer().getLevel() instanceof ServerLevel world){
                ServerPlayNetworking.send(player,
                    new ComputerStatePayload(menu.computer().snapshot(world)));
            }
            for(int i=0;i<player.getInventory().getContainerSize();i++){
                ItemStack item=player.getInventory().getItem(i);
                if(item.is(MiningRig.TABLET)){
                    int lamp=lamp(server,TabletLink.read(item));
                    String status=lamp==3?"red":lamp==2?"yellow":lamp==1?"green":"dark";
                    TabletLink.light(item,status);
                }
            }
            ItemStack offhand=player.getOffhandItem();
            if(offhand.is(MiningRig.TABLET)){
                int lamp=lamp(server,TabletLink.read(offhand));
                TabletLink.light(offhand,lamp==3?"red":lamp==2?"yellow":lamp==1?"green":"dark");
            }
        }
    }
}
