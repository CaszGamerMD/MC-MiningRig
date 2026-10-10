package dev.casz.miningrig;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;

/**
 * Maps a small, fixed set of visible GUI slots into a larger bundle-style inventory.
 * The server's ContainerData page index is also synchronized to the client menu.
 * Vanilla hoppers see the complete underlying inventory, not just the current page.
 */
public final class PagedRigContainer implements Container {
    private final Container backing;
    private final ContainerData pageData;
    private final int pageField, pinned, visible, start;
    public PagedRigContainer(Container backing,ContainerData pageData,int pageField,int pinned,int visible){
        this.backing=backing;this.pageData=pageData;this.pageField=pageField;
        this.pinned=pinned;this.visible=visible;this.start=pinned;
    }
    private int backingSlot(int visibleSlot){
        if(visibleSlot<pinned)return visibleSlot;
        return start+Math.max(0,pageData.get(pageField))*visible+(visibleSlot-pinned);
    }
    private boolean valid(int i){return i>=0 && i<getContainerSize() && backingSlot(i)<backing.getContainerSize();}
    @Override public int getContainerSize(){return pinned+visible;}
    @Override public boolean isEmpty(){
        for(int i=0;i<getContainerSize();i++)if(!getItem(i).isEmpty())return false;return true;
    }
    @Override public ItemStack getItem(int slot){
        return valid(slot)?backing.getItem(backingSlot(slot)):ItemStack.EMPTY;
    }
    @Override public ItemStack removeItem(int slot,int count){
        return valid(slot)?backing.removeItem(backingSlot(slot),count):ItemStack.EMPTY;
    }
    @Override public ItemStack removeItemNoUpdate(int slot){
        return valid(slot)?backing.removeItemNoUpdate(backingSlot(slot)):ItemStack.EMPTY;
    }
    @Override public void setItem(int slot,ItemStack stack){
        if(valid(slot))backing.setItem(backingSlot(slot),stack);
    }
    @Override public boolean canPlaceItem(int slot,ItemStack stack){
        return valid(slot) && backing.canPlaceItem(backingSlot(slot),stack);
    }
    @Override public int getMaxStackSize(ItemStack stack){return backing.getMaxStackSize(stack);}
    @Override public void setChanged(){backing.setChanged();}
    @Override public boolean stillValid(Player player){return backing.stillValid(player);}
    @Override public void clearContent(){
        for(int i=0;i<getContainerSize();i++)if(valid(i))backing.setItem(backingSlot(i),ItemStack.EMPTY);
        backing.setChanged();
    }
    @Override public void startOpen(Player player){backing.startOpen(player);}
    @Override public void stopOpen(Player player){backing.stopOpen(player);}
}
