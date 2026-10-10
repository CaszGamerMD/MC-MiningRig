package dev.casz.miningrig;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

public final class RigLinker extends Item {
    private record Selection(ResourceKey<Level> world,BlockPos pos) {}
    private static final Map<UUID,Selection> START=new HashMap<>();
    public RigLinker(Properties properties){super(properties);}
    @Override public InteractionResult useOn(UseOnContext context){
        if(!(context.getLevel() instanceof ServerLevel world))return InteractionResult.SUCCESS;
        var player=context.getPlayer();
        if(player==null)return InteractionResult.PASS;
        BlockPos pos=context.getClickedPos();
        BlockEntity target=world.getBlockEntity(pos);
        Selection source=START.get(player.getUUID());
        if(target instanceof DrillEntity) {
            START.put(player.getUUID(),new Selection(world.dimension(),pos.immutable()));
            player.sendSystemMessage(Component.literal("Selected drill "+pos.toShortString()+". Click the mining computer."));
            return InteractionResult.SUCCESS;
        }
        if(target instanceof ComputerEntity computer) {
            if(source!=null && source.world.equals(world.dimension()) && world.getBlockEntity(source.pos) instanceof DrillEntity) {
                computer.link(source.pos);
                START.remove(player.getUUID());
                player.sendSystemMessage(Component.literal("Computer linked to drill."));
            } else player.sendSystemMessage(Component.literal("Select a drill first."));
            return InteractionResult.SUCCESS;
        }
        if(target instanceof CoordinatorEntity) {
            player.sendSystemMessage(Component.literal("Coordinators automatically discover their first neighbor on each axis within 20 blocks. Empty-hand right-click for I/O settings."));
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }
}
