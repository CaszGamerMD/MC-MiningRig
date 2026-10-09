package dev.casz.miningrig;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

public final class DrillEntity extends BlockEntity {
    private ItemStack tip = ItemStack.EMPTY;
    private int startY, completedLayers, cursor;
    private boolean initialized, relocating;
    public DrillEntity(BlockPos pos, BlockState state) { super(MiningRig.DRILL_ENTITY,pos,state); }
    public ItemStack tip() { return tip; }
    public boolean relocating() { return relocating; }
    public void install(ItemStack stack) { tip = stack; setChanged(); }
    public ItemStack removeTip() { ItemStack result=tip; tip=ItemStack.EMPTY; setChanged(); return result; }
    public ComputerEntity.Status mineOne(ServerLevel world,ComputerEntity computer) {
        if (tip.isEmpty()) return ComputerEntity.Status.OUT_OF_BITS;
        if (!initialized) { startY=worldPosition.getY(); initialized=true; }
        if (completedLayers >= computer.depth()) return ComputerEntity.Status.DONE;
        CoordinatorEntity collector = nearestCollector(world);
        if (collector == null) return ComputerEntity.Status.NO_COLLECTOR;

        int r=computer.radius(), edge=2*r+1, total=edge*edge;
        while (cursor < total) {
            int index=cursor++;
            int dx=index%edge-r, dz=index/edge-r;
            if (!computer.contains(dx,dz)) continue;
            BlockPos target=worldPosition.offset(dx,-1,dz);
            if (target.getY()<world.getMinY()) return ComputerEntity.Status.DONE;
            if (!world.hasChunkAt(target)) { cursor--; return ComputerEntity.Status.BLOCKED; }
            BlockState state=world.getBlockState(target);
            if (state.isAir()) continue;
            if (state.getDestroySpeed(world,target)<0 || !(tip.getItem() instanceof DrillBit bit) || !bit.canMine(state)
                || state.hasBlockEntity() || state.is(MiningRig.DRILL) || state.is(MiningRig.COMPUTER) || state.is(MiningRig.COORDINATOR)) {
                cursor--; return ComputerEntity.Status.BLOCKED;
            }
            List<ItemStack> drops=Block.getDrops(state,world,target,null,null,tip);
            if (!collector.canReceive(drops)) { cursor--; return ComputerEntity.Status.FULL; }
            if (!world.setBlock(target, Blocks.AIR.defaultBlockState(),Block.UPDATE_ALL)) {
                cursor--; return ComputerEntity.Status.BLOCKED;
            }
            collector.receive(drops);
            tip.setDamageValue(tip.getDamageValue()+1);
            if (tip.getDamageValue() >= tip.getMaxDamage()) tip=ItemStack.EMPTY;
            setChanged();
            return tip.isEmpty() ? ComputerEntity.Status.OUT_OF_BITS : ComputerEntity.Status.RUNNING;
        }
        // All positions in the current layer were visited. Descend the drill itself.
        BlockPos previous=worldPosition;
        BlockPos next=previous.below();
        completedLayers++;
        if (next.getY()<world.getMinY()) return ComputerEntity.Status.DONE;
        // The center was mined when this layer was swept, so it should be air.
        if (!world.getBlockState(next).isAir()) return ComputerEntity.Status.BLOCKED;
        relocating=true;
        world.setBlock(previous,Blocks.AIR.defaultBlockState(),Block.UPDATE_ALL);
        world.setBlock(next,MiningRig.DRILL.defaultBlockState(),Block.UPDATE_ALL);
        if (!(world.getBlockEntity(next) instanceof DrillEntity moved)) return ComputerEntity.Status.BLOCKED;
        moved.tip=tip.copy();moved.startY=startY;moved.initialized=true;
        moved.completedLayers=completedLayers;moved.cursor=0;moved.setChanged();
        computer.moved(previous,next);
        return completedLayers >= computer.depth() ? ComputerEntity.Status.DONE : ComputerEntity.Status.RUNNING;
    }
    private CoordinatorEntity nearestCollector(ServerLevel world) {
        // Strict same-axis alignment, up to 20 blocks. Never force-load a chunk.
        for (int dist=1;dist<=20;dist++) for (Direction direction:Direction.values()) {
            BlockPos scan=worldPosition.relative(direction,dist);
            if(world.hasChunkAt(scan) && world.getBlockEntity(scan) instanceof CoordinatorEntity collector)
                return collector;
        }
        return null;
    }
    @Override protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        out.putInt("StartY",startY);out.putInt("Layers",completedLayers);out.putInt("Cursor",cursor);
        out.putBoolean("Initialized",initialized);
        if (!tip.isEmpty()) out.store("Tip",ItemStack.CODEC,tip);
    }
    @Override protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        startY=in.getIntOr("StartY",worldPosition.getY());completedLayers=in.getIntOr("Layers",0);
        cursor=in.getIntOr("Cursor",0);initialized=in.getBooleanOr("Initialized",false);
        tip=in.read("Tip",ItemStack.CODEC).orElse(ItemStack.EMPTY);
    }
}
