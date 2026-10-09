package dev.casz.miningrig;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

public final class CoordinatorEntity extends BlockEntity implements Container {
    private final NonNullList<ItemStack> items=NonNullList.withSize(27,ItemStack.EMPTY);
    private BlockPos output;
    public CoordinatorEntity(BlockPos pos,BlockState state) { super(MiningRig.COORDINATOR_ENTITY,pos,state); }
    public int filledSlots() { int count=0;for(ItemStack item:items)if(!item.isEmpty()) count++;return count; }
    public static boolean aligned(BlockPos a,BlockPos b) {
        int axes=(a.getX()!=b.getX()?1:0)+(a.getY()!=b.getY()?1:0)+(a.getZ()!=b.getZ()?1:0);
        int distance=Math.abs(a.getX()-b.getX())+Math.abs(a.getY()-b.getY())+Math.abs(a.getZ()-b.getZ());
        return axes==1 && distance>0 && distance<=20;
    }
    public boolean link(BlockPos target) {
        if (!aligned(worldPosition,target) || level==null || !(level.getBlockEntity(target) instanceof CoordinatorEntity)) return false;
        // Reject loops before accepting any transfer direction.
        BlockPos walk=target;
        for(int hops=0;hops<64;hops++) {
            if(walk.equals(worldPosition)) return false;
            if(!(level.getBlockEntity(walk) instanceof CoordinatorEntity node) || node.output==null) break;
            walk=node.output;
        }
        output=target.immutable();setChanged();return true;
    }
    public boolean canReceive(List<ItemStack> drops) {
        NonNullList<ItemStack> simulation=NonNullList.withSize(27,ItemStack.EMPTY);
        for(int i=0;i<27;i++)simulation.set(i,items.get(i).copy());
        for(ItemStack stack:drops) if(!insert(simulation,stack.copy()))return false;
        return true;
    }
    public boolean receive(List<ItemStack> drops) {
        if(!canReceive(drops)) return false;
        for(ItemStack stack:drops) insert(items,stack.copy());
        setChanged();return true;
    }
    private static boolean insert(List<ItemStack> target,ItemStack incoming) {
        if(incoming.isEmpty())return true;
        for(int i=0;i<target.size();i++) {
            ItemStack existing=target.get(i);
            if(!existing.isEmpty() && ItemStack.isSameItemSameComponents(existing,incoming)) {
                int space=Math.min(existing.getMaxStackSize(),64)-existing.getCount();
                int moved=Math.min(space,incoming.getCount());
                if(moved>0){existing.grow(moved);incoming.shrink(moved);}
                if(incoming.isEmpty())return true;
            }
        }
        for(int i=0;i<target.size();i++)if(target.get(i).isEmpty()) {
            int moved=Math.min(incoming.getCount(),Math.min(64,incoming.getMaxStackSize()));
            target.set(i,incoming.copyWithCount(moved));incoming.shrink(moved);
            if(incoming.isEmpty())return true;
        }
        return false;
    }
    public static void tick(Level level,BlockPos pos,BlockState state,CoordinatorEntity entity) {
        if(level.isClientSide() || level.getGameTime()%8!=0 || entity.output==null || !level.hasChunkAt(entity.output))return;
        if(!(level.getBlockEntity(entity.output) instanceof CoordinatorEntity target) || !aligned(pos,entity.output))return;
        for(int i=0;i<entity.items.size();i++){
            ItemStack stack=entity.items.get(i);
            if(stack.isEmpty())continue;
            List<ItemStack> transfer=List.of(stack.copy());
            if(target.receive(transfer)){entity.items.set(i,ItemStack.EMPTY);entity.setChanged();}
            break;
        }
    }
    @Override public int getContainerSize(){return items.size();}
    @Override public boolean isEmpty(){for(ItemStack stack:items)if(!stack.isEmpty())return false;return true;}
    @Override public ItemStack getItem(int index){return items.get(index);}
    @Override public ItemStack removeItem(int index,int count){
        ItemStack result=ContainerHelper.removeItem(items,index,count);
        if(!result.isEmpty())setChanged();
        return result;
    }
    @Override public ItemStack removeItemNoUpdate(int index){return ContainerHelper.takeItem(items,index);}
    @Override public void setItem(int index,ItemStack stack){items.set(index,stack);stack.limitSize(getMaxStackSize(stack));setChanged();}
    @Override public boolean stillValid(Player player){return level!=null && level.getBlockEntity(worldPosition)==this && player.distanceToSqr(worldPosition.getX()+.5,worldPosition.getY()+.5,worldPosition.getZ()+.5)<=64;}
    @Override public void clearContent(){for(int i=0;i<27;i++)items.set(i,ItemStack.EMPTY);setChanged();}
    @Override protected void saveAdditional(ValueOutput out){
        super.saveAdditional(out);
        ContainerHelper.saveAllItems(out,items);
        if(output!=null)out.putLong("Output",output.asLong());
    }
    @Override protected void loadAdditional(ValueInput in){
        super.loadAdditional(in);
        ContainerHelper.loadAllItems(in,items);
        output=in.getLong("Output").isPresent()?BlockPos.of(in.getLongOr("Output",0)):null;
    }
}
