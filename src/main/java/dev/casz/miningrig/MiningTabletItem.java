package dev.casz.miningrig;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

/** Right-click a computer to bind, right-click air to control, click a face to mount. */
public final class MiningTabletItem extends BlockItem {
    public MiningTabletItem(Properties properties){super(MiningRig.TABLET_BLOCK,properties);}
    @Override public InteractionResult useOn(UseOnContext context){
        Level level=context.getLevel();
        BlockPos pos=context.getClickedPos();
        if(level.getBlockState(pos).is(MiningRig.COMPUTER_UPPER))pos=pos.below();
        if(level.getBlockEntity(pos) instanceof ComputerEntity computer){
            if(level instanceof ServerLevel server){
                TabletLink.write(context.getItemInHand(),TabletLink.of(server,computer));
                context.getPlayer().sendSystemMessage(Component.literal("Tablet linked to Mining Computer at "+pos.toShortString()));
            }
            return InteractionResult.SUCCESS;
        }
        return super.useOn(context);
    }
    @Override public InteractionResult use(Level level,Player player,InteractionHand hand){
        if(player instanceof ServerPlayer serverPlayer)
            TabletAccess.open(serverPlayer,TabletLink.read(player.getItemInHand(hand)));
        return InteractionResult.SUCCESS;
    }
}
