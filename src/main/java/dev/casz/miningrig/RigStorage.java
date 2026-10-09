package dev.casz.miningrig;

import java.util.List;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;

/** Capacity is counted in individual items, not stack slots. */
public final class RigStorage {
    private RigStorage() {}
    public static int count(List<ItemStack> slots, int start) {
        int total=0;
        for(int i=start;i<slots.size();i++) total+=slots.get(i).getCount();
        return total;
    }
    public static int room(List<ItemStack> slots, int start, int capacity) {
        return Math.max(0, capacity-count(slots,start));
    }
    /** Atomic all-or-nothing insertion. Never silently voids overflow. */
    public static boolean canInsert(List<ItemStack> inventory,int start,int capacity,List<ItemStack> incoming) {
        NonNullList<ItemStack> simulation=NonNullList.withSize(inventory.size(),ItemStack.EMPTY);
        for(int i=0;i<inventory.size();i++)simulation.set(i,inventory.get(i).copy());
        return insert(simulation,start,capacity,incoming);
    }
    public static boolean insert(List<ItemStack> inventory,int start,int capacity,List<ItemStack> incoming) {
        int total=count(inventory,start);
        for(ItemStack original:incoming) {
            ItemStack remaining=original.copy();
            if(remaining.isEmpty())continue;
            if((long) total+remaining.getCount()>capacity) return false;
            total+=remaining.getCount();
            for(int i=start;i<inventory.size() && !remaining.isEmpty();i++) {
                ItemStack at=inventory.get(i);
                if(!at.isEmpty() && ItemStack.isSameItemSameComponents(at,remaining)) {
                    int amount=Math.min(Math.min(at.getMaxStackSize(),64)-at.getCount(),remaining.getCount());
                    if(amount>0){at.grow(amount);remaining.shrink(amount);}
                }
            }
            for(int i=start;i<inventory.size() && !remaining.isEmpty();i++) {
                if(inventory.get(i).isEmpty()) {
                    int amount=Math.min(remaining.getCount(),Math.min(remaining.getMaxStackSize(),64));
                    inventory.set(i,remaining.copyWithCount(amount));
                    remaining.shrink(amount);
                }
            }
            if(!remaining.isEmpty())return false;
        }
        return true;
    }
}
