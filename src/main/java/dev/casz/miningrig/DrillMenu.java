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

/** Three bits plus tabs for three independent 64-item bundle inventories. */
public final class DrillMenu extends AbstractContainerMenu {
    private final Container drill;
    private final ContainerData data;
    public DrillMenu(int id, Inventory player) { this(id,player,new SimpleContainer(12),new SimpleContainerData(6)); }
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
        for(int i=0;i<9;i++)addSlot(new Slot(container,i+3,8+i*18,80) {
            @Override public boolean mayPlace(ItemStack stack) {
                return drill.canPlaceItem(getContainerSlot(),stack);
            }
            @Override public int getMaxStackSize(ItemStack stack) {
                return Math.min(stack.getMaxStackSize(),
                    drill instanceof PagedDrillContainer view
                        ?view.roomForSlot(getContainerSlot())
                        :drill.getMaxStackSize(stack));
            }
        });
        addStandardInventorySlots(player,8,149);
    }
    public int stored(){return data.get(switch(data.get(4)){case 1->2;case 2->3;default->0;});}
    public int page(){return data.get(1);}
    public int bank(){return data.get(4);}
    public boolean randomWalls(){return data.get(5)!=0;}
    public String bankName(){return switch(bank()){case 1->"FILLER";case 2->"WALL";default->"OUTPUT";};}
    @Override public boolean clickMenuButton(Player player,int id){
        switch(id){
            case 0 -> data.set(1,Math.max(0,data.get(1)-1));
            case 1 -> data.set(1,Math.min(7,data.get(1)+1));
            case 2 -> data.set(4,(data.get(4)+1)%3);
            case 3 -> data.set(5,data.get(5)==0?1:0);
            default -> {return false;}
        }
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
