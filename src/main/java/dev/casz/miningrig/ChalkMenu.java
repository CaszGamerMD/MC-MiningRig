package dev.casz.miningrig;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;

/**
 * Server-authoritative editing controls for the Target Chalk held in hand.
 * Data slots: count, mode, hand (0 main, 1 offhand). Only the server modifies
 * the actual CustomData; existing drawings and installed shapes are untouched
 * unless the user explicitly finishes or clears the unfinished draft.
 */
public final class ChalkMenu extends AbstractContainerMenu {
    private final SimpleContainerData data=new SimpleContainerData(3);
    private final InteractionHand hand;
    public ChalkMenu(int id,Inventory inventory){
        this(id,inventory,
            inventory.player.getMainHandItem().is(MiningRig.TARGET_CHALK)
                ?InteractionHand.MAIN_HAND:InteractionHand.OFF_HAND);
    }
    public ChalkMenu(int id,Inventory inventory,InteractionHand hand){
        super(RigMenus.TARGET_CHALK,id);
        this.hand=hand;
        refresh(inventory.player);
        addDataSlots(data);
    }
    private void refresh(Player player){
        ItemStack chalk=player.getItemInHand(hand);
        data.set(0,TargetChalkItem.pointCount(chalk));
        data.set(1,TargetChalkItem.operation(chalk).ordinal());
        data.set(2,hand==InteractionHand.OFF_HAND?1:0);
    }
    public int points(){return data.get(0);}
    public int maxPoints(){return TargetChalkItem.MAX_POINTS;}
    public ChalkOutline.Operation mode(){
        ChalkOutline.Operation[] values=ChalkOutline.Operation.values();
        int index=data.get(1);
        return index>=0&&index<values.length?values[index]:values[0];
    }
    @Override public boolean stillValid(Player player){
        return player.getItemInHand(hand).is(MiningRig.TARGET_CHALK);
    }
    @Override public boolean clickMenuButton(Player player,int id){
        if(!stillValid(player)||!(player.level() instanceof ServerLevel world))return false;
        ItemStack chalk=player.getItemInHand(hand);
        switch(id){
            case 0 -> TargetChalkItem.undoLastPoint(world,player,chalk);
            case 1 -> TargetChalkItem.finishDraft(world,player,chalk);
            case 2 -> TargetChalkItem.clearDraft(world,player,chalk);
            case 3 -> {
                // Prevent switching the operation halfway through a drawing,
                // which otherwise risks applying a cut as a replacement.
                if(TargetChalkItem.pointCount(chalk)==0)
                    TargetChalkItem.cycleOperation(chalk,player);
                else player.sendSystemMessage(
                    net.minecraft.network.chat.Component.literal(
                        "Finish or clear this draft before changing Target Chalk mode."));
            }
            default -> {return false;}
        }
        refresh(player);
        broadcastChanges();
        return true;
    }
    @Override public ItemStack quickMoveStack(Player player,int index){
        return ItemStack.EMPTY;
    }
}
