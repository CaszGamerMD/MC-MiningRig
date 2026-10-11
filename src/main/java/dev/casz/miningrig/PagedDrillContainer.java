package dev.casz.miningrig;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;

/**
 * Drill UI: 3 pinned bit slots and one paged 9-slot view into three bundle
 * inventories. Bank 0 is mined-item output, bank 1 Filler, bank 2 Walls.
 * Supply bundle items retain their original source until consumed or returned.
 */
public final class PagedDrillContainer implements Container {
    private final DrillEntity drill;
    private final ContainerData data;

    public PagedDrillContainer(DrillEntity drill,ContainerData data){
        this.drill=drill;
        this.data=data;
    }
    private int bank(){return data.get(4);}
    private int offset(int slot){return data.get(1)*9+slot-3;}
    private boolean valid(int slot){
        return slot>=0&&slot<12 && (slot<3||offset(slot)<64);
    }
    private int outputSlot(int slot){return DrillEntity.BIT_SLOTS+offset(slot);}
    @Override public int getContainerSize(){return 12;}
    @Override public boolean isEmpty(){
        for(int i=0;i<12;i++)if(!getItem(i).isEmpty())return false;
        return true;
    }
    @Override public ItemStack getItem(int slot){
        if(!valid(slot))return ItemStack.EMPTY;
        if(slot<3)return drill.getItem(slot);
        return bank()==0?drill.getItem(outputSlot(slot)):
            drill.supplyItem(bank()==2,offset(slot));
    }
    @Override public ItemStack removeItem(int slot,int count){
        if(!valid(slot))return ItemStack.EMPTY;
        return slot<3||bank()==0?drill.removeItem(slot<3?slot:outputSlot(slot),count):
            drill.removeSupply(bank()==2,offset(slot),count);
    }
    @Override public ItemStack removeItemNoUpdate(int slot){
        if(!valid(slot))return ItemStack.EMPTY;
        return slot<3||bank()==0?drill.removeItemNoUpdate(slot<3?slot:outputSlot(slot)):
            drill.removeSupply(bank()==2,offset(slot),Integer.MAX_VALUE);
    }
    @Override public void setItem(int slot,ItemStack stack){
        if(!valid(slot))return;
        if(slot<3||bank()==0)drill.setItem(slot<3?slot:outputSlot(slot),stack);
        else drill.setSupply(bank()==2,offset(slot),stack);
    }
    @Override public boolean canPlaceItem(int slot,ItemStack stack){
        if(!valid(slot))return false;
        if(slot<3||bank()==0)return drill.canPlaceItem(slot<3?slot:outputSlot(slot),stack);
        return RigNetwork.isFiller(stack) && drill.supplyRoom(bank()==2,offset(slot))>0;
    }
    /** Remaining room includes this slot's current items so vanilla slot swapping works. */
    public int roomForSlot(int slot){
        if(!valid(slot))return 0;
        if(slot<3)return 1;
        return bank()==0?drill.freeStorage()+getItem(slot).getCount():
            drill.supplyRoom(bank()==2,offset(slot));
    }
    @Override public int getMaxStackSize(ItemStack stack){return stack.getMaxStackSize();}
    @Override public void setChanged(){drill.setChanged();}
    @Override public boolean stillValid(Player player){return drill.stillValid(player);}
    @Override public void clearContent(){
        // Work backwards because bundle entries compact when removed.
        for(int i=11;i>=0;i--)setItem(i,ItemStack.EMPTY);
    }
    public void startOpen(Player player){drill.startOpen(player);}
    public void stopOpen(Player player){drill.stopOpen(player);}
}
