package dev.casz.miningrig;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.core.HolderLookup;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.nbt.CompoundTag;

public final class ComputerEntity extends BlockEntity {
    public enum Status {
        READY("Ready"), RUNNING("Mining"), STOPPED("Manually stopped"),
        DONE("Job done"), OUT_OF_BITS("Out of bits"), NO_COLLECTOR("Out of range of a collector"),
        BLOCKED("Unable to break block"), FULL("Collector storage full"), NO_DRILL("No linked drill");
        final String display;
        Status(String display) { this.display = display; }
    }
    private int radius = 2, depth = 12;
    private boolean circle = true, running = false, linked = false;
    private BlockPos drill = BlockPos.ZERO;
    private Status status = Status.READY;

    public ComputerEntity(BlockPos pos, BlockState state) { super(MiningRig.COMPUTER_ENTITY,pos,state); }
    public int radius() { return radius; }
    public int depth() { return depth; }
    public boolean circle() { return circle; }
    public void setRadius(int value) { radius = Math.max(1, Math.min(12, value)); stopForSetting(); }
    public void setDepth(int value) { depth = Math.max(1, Math.min(256, value)); stopForSetting(); }
    public void setCircle(boolean value) { circle = value; stopForSetting(); }
    private void stopForSetting() { running = false; status = Status.STOPPED; setChanged(); }
    public String screenStatus() { return status.display; }
    public boolean running() { return running; }
    public String description() {
        return "Mining Rig | " + status.display + " | " + (circle ? "Circle" : "Square")
                + " r=" + radius + " depth=" + depth + (linked ? " | Linked" : " | No drill");
    }
    public void link(BlockPos drillPos) {
        this.drill = drillPos.immutable(); this.linked = true;
        this.running = false; this.status = Status.READY; setChanged();
    }
    public void moved(BlockPos oldPos, BlockPos newPos) {
        if (linked && drill.equals(oldPos)) { drill = newPos.immutable(); setChanged(); }
    }
    public void toggle() {
        if (running) { running = false; status = Status.STOPPED; }
        else {
            if (!linked) { status = Status.NO_DRILL; }
            else if (level != null && level.getBlockEntity(drill) instanceof DrillEntity) {
                running = true; status = Status.RUNNING;
            } else status = Status.NO_DRILL;
        }
        setChanged();
    }
    public void stop(Status reason) { running = false; status = reason; setChanged(); }
    public static void tick(Level level, BlockPos pos, BlockState state, ComputerEntity entity) {
        if (!(level instanceof ServerLevel server)) return;
        long time = server.getGameTime();
        if (time % 8 == 0 && entity.running) {
            if (!server.hasChunkAt(entity.drill) || !(server.getBlockEntity(entity.drill) instanceof DrillEntity drill)) {
                entity.stop(Status.NO_DRILL);
            } else {
                Status result = drill.mineOne(server, entity);
                if (result != Status.RUNNING) entity.stop(result);
            }
        }
        if (time % 10 == 0) {
            boolean alarm = !entity.running && entity.status != Status.READY && ((time / 10) % 2 == 0);
            if (state.getValue(ComputerBlock.ALARM) != alarm) {
                server.setBlock(pos, state.setValue(ComputerBlock.ALARM, alarm), Block.UPDATE_CLIENTS);
            }
        }
        // Server-origin particles: visible from outside a UI to show the footprint currently set.
        if (time % 20 == 0 && entity.linked && server.hasChunkAt(entity.drill)) {
            int rad = entity.radius;
            for (int dx = -rad; dx <= rad; dx++) for (int dz = -rad; dz <= rad; dz++) {
                boolean inside = entity.contains(dx,dz);
                boolean boundary = inside && (!entity.contains(dx+1,dz) || !entity.contains(dx-1,dz)
                                 || !entity.contains(dx,dz+1) || !entity.contains(dx,dz-1));
                if (boundary) {
                    server.sendParticles(ParticleTypes.END_ROD, entity.drill.getX()+dx+.5,
                        entity.drill.getY()+.2, entity.drill.getZ()+dz+.5, 1, 0,0,0,0);
                }
            }
        }
    }
    public boolean contains(int dx,int dz) { return circle ? dx*dx+dz*dz <= radius*radius : Math.max(Math.abs(dx),Math.abs(dz)) <= radius; }
    @Override protected void saveAdditional(ValueOutput out) {
        out.putInt("Radius",radius);out.putInt("Depth",depth);out.putBoolean("Circle",circle);
        out.putBoolean("Running",running);out.putBoolean("Linked",linked);out.putLong("Drill",drill.asLong());
        out.putString("Status",status.name()); super.saveAdditional(out);
    }
    @Override protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        radius=Math.max(1, Math.min(12,in.getIntOr("Radius",2)));
        depth=Math.max(1,Math.min(256,in.getIntOr("Depth",12)));
        circle=in.getBooleanOr("Circle",true);running=in.getBooleanOr("Running",false);
        linked=in.getBooleanOr("Linked",false);drill=BlockPos.of(in.getLongOr("Drill",0));
        try { status=Status.valueOf(in.getStringOr("Status","READY")); }
        catch (IllegalArgumentException err) { status=Status.READY; }
    }
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registry) { return saveWithoutMetadata(registry); }
    @Override public Packet<ClientGamePacketListener> getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
    @Override public void setChanged() {
        super.setChanged();
        if(level != null && !level.isClientSide()) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(),Block.UPDATE_CLIENTS);
    }
}
