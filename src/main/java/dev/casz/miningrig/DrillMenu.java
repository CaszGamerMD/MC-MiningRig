package dev.casz.miningrig;

import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** Three independent working bits and nine storage stacks, capped at 64 total items. */
public final class DrillMenu extends AbstractContainerMenu {
    private final Container drill;
    private final ContainerData data;
    public DrillMenu(int id, Inventory player) { this(id,player,new SimpleContainer(12),new SimpleContainerData(2)); }
    public DrillMenu(int id, Inventory player, Container container,ContainerData data) {
        super(RigMenus.DRILL,id);
        checkContainerSize(container,12);
        this.drill=container;
        this.data=data;
        addDataSlots(data);
        container.startOpen(player.player);
        for(int i=0;i<3;i++)addSlot(new Slot(container,i,62+i*18,19) {
            @Override public boolean mayPlace(ItemStack stack) { return stack.getItem() instanceof DrillBit; }
            @Override public int getMaxStackSize(ItemStack stack) { return 1; }
        });
        for(int i=0;i<9;i++)addSlot(new Slot(container,i+3,8+i*18,52) {
            @Override public boolean mayPlace(ItemStack stack) {
                return drill.canPlaceItem(getContainerSlot(),stack);
            }
            @Override public int getMaxStackSize(ItemStack stack) {
                return Math.min(stack.getMaxStackSize(),drill.getMaxStackSize(stack)+getItem().getCount());
            }
        });
        addStandardInventorySlots(player,8,100);
    }
    public int stored(){return data.get(0);}
    public int page(){return data.get(1);}
    @Override public boolean clickMenuButton(Player player,int id){
        if(id!=0 && id!=1)return false;
        data.set(1,Math.max(0,Math.min(7,data.get(1)+(id==1?1:-1))));
        broadcastChanges();return true;
    }
    @Override public boolean stillValid(Player player){return drill.stillValid(player);}
    @Override public void removed(Player player){super.removed(player);drill.stopOpen(player);}
    @Override public ItemStack quickMoveStack(Player player,int slotIndex) {
        Slot slot=slots.get(slotIndex);
        if(!slot.hasItem())return ItemStack.EMPTY;
        ItemStack stack=slot.getItem(),original=stack.copy();
        if(slotIndex<12) {
            if(!moveItemStackTo(stack,12,slots.size(),true))return ItemStack.EMPTY;
        } else if(stack.getItem() instanceof DrillBit) {
            if(!moveItemStackTo(stack,0,3,false))return ItemStack.EMPTY;
        } else {
            if(!moveItemStackTo(stack,3,12,false))return ItemStack.EMPTY;
        }
        if(stack.isEmpty())slot.setByPlayer(ItemStack.EMPTY);else slot.setChanged();
        return original;
    }
}
