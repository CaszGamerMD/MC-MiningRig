package dev.casz.miningrig;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;

/**
 * Lightweight four-value menu for the held CC Organizer. All button actions
 * are validated on the server and saved into the item's CustomData.
 * 0 retrieval, 1 distance, 2 preview-first, 3 hand.
 */
public final class OrganizerMenu extends AbstractContainerMenu {
    private final SimpleContainerData data=new SimpleContainerData(4);
    private final InteractionHand hand;

    public OrganizerMenu(int id,Inventory inventory){
        this(id,inventory,
            inventory.player.getMainHandItem().is(MiningRig.CC_GUIDE)
                ?InteractionHand.MAIN_HAND:InteractionHand.OFF_HAND);
    }

    public OrganizerMenu(int id,Inventory inventory,InteractionHand hand){
        super(RigMenus.ORGANIZER,id);
        this.hand=hand;
        ItemStack tool=inventory.player.getItemInHand(hand);
        data.set(0,CoordinatorGuideItem.isRetrieval(tool)?1:0);
        data.set(1,CoordinatorGuideItem.distance(tool));
        data.set(2,CoordinatorGuideItem.previewFirst(tool)?1:0);
        data.set(3,hand==InteractionHand.OFF_HAND?1:0);
        addDataSlots(data);
    }
    public boolean retrieval(){return data.get(0)==1;}
    public int distance(){return data.get(1);}
    public boolean preview(){return data.get(2)==1;}
    @Override public boolean stillValid(Player player){
        return player.getItemInHand(hand).is(MiningRig.CC_GUIDE);
    }
    @Override public boolean clickMenuButton(Player player,int id){
        if(!stillValid(player)||id<0||id>3)return false;
        ItemStack tool=player.getItemInHand(hand);
        int mode=data.get(0),dist=data.get(1),preview=data.get(2);
        switch(id){
            case 0 -> mode=1-mode;
            case 1 -> dist=Math.max(1,dist-1);
            case 2 -> dist=Math.min(20,dist+1);
            case 3 -> preview=1-preview;
            default -> {return false;}
        }
        CoordinatorGuideItem.writeOptions(tool,mode==1,dist,preview==1);
        data.set(0,mode);data.set(1,dist);data.set(2,preview);
        broadcastChanges();
        return true;
    }
    @Override public ItemStack quickMoveStack(Player player,int index){return ItemStack.EMPTY;}
}
