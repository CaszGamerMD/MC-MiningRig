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
import net.minecraft.core.Direction;

/** 27 visible stacks, but a global 192-item bundle-style capacity. */
public final class CoordinatorMenu extends AbstractContainerMenu {
    private final Container coordinator;
    private final ContainerData data;
    public CoordinatorMenu(int id,Inventory player) { this(id,player,new SimpleContainer(27),new SimpleContainerData(13)); }
    public CoordinatorMenu(int id,Inventory player,Container inventory,ContainerData data) {
        super(RigMenus.COORDINATOR,id);
        checkContainerSize(inventory,27);
        this.coordinator=inventory;
        this.data=data;
        addDataSlots(data);
        inventory.startOpen(player.player);
        for(int i=0;i<27;i++) {
            addSlot(new Slot(inventory,i,8+(i%9)*18,88+(i/9)*18) {
                @Override public boolean mayPlace(ItemStack stack) {
                    return coordinator.canPlaceItem(getContainerSlot(),stack);
                }
                @Override public int getMaxStackSize(ItemStack stack) {
                    return Math.min(stack.getMaxStackSize(),coordinator.getMaxStackSize(stack)+getItem().getCount());
                }
            });
        }
        addStandardInventorySlots(player,8,166);
    }
    public CoordinatorEntity coordinator(){
        return coordinator instanceof PagedRigContainer view?view.coordinator():null;
    }
    public int mode(Direction side){return data.get(side.get3DDataValue());}
    public boolean connected(Direction side){return ((data.get(6)>>>side.get3DDataValue())&1)!=0;}
    public boolean drillSide(Direction side){return ((data.get(10)>>>side.get3DDataValue())&1)!=0;}
    public boolean containerSide(Direction side){return ((data.get(8)>>>side.get3DDataValue())&1)!=0;}
    public int stored(){return data.get(7);}
    /** Supply depot belongs to fluid filling; ordinary storage is separate. */
    public int fillerPending(){return data.get(11);}
    public int wallPending(){return data.get(12);}
    public int page(){return data.get(9);}
    @Override public boolean clickMenuButton(Player player,int buttonId) {
        if(buttonId==6 || buttonId==7){
            data.set(9,Math.max(0,Math.min(7,data.get(9)+(buttonId==7?1:-1))));
            broadcastChanges();return true;
        }
        if(buttonId>=8&&buttonId<14){
            if(coordinator instanceof PagedRigContainer view)
                view.coordinatorToggleWallBehavior(Direction.from3DDataValue(buttonId-8));
            return true;
        }
        if(buttonId<0 || buttonId>=6)return false;
        if(coordinator instanceof PagedRigContainer view) {
            // Only the authoritative server entity changes the face mode.
            view.coordinatorCycle(Direction.from3DDataValue(buttonId));
        }
        return true;
    }
    @Override public boolean stillValid(Player player){return coordinator.stillValid(player);}
    @Override public void removed(Player player){super.removed(player);coordinator.stopOpen(player);}
    @Override public ItemStack quickMoveStack(Player player,int slotIndex) {
        Slot slot=slots.get(slotIndex);
        if(!slot.hasItem())return ItemStack.EMPTY;
        ItemStack stack=slot.getItem(),original=stack.copy();
        if(slotIndex<27){if(!moveItemStackTo(stack,27,slots.size(),true))return ItemStack.EMPTY;}
        else {if(!moveItemStackTo(stack,0,27,false))return ItemStack.EMPTY;}
        if(stack.isEmpty())slot.setByPlayer(ItemStack.EMPTY);else slot.setChanged();
        return original;
    }
}
