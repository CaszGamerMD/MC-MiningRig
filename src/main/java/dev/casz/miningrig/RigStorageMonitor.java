package dev.casz.miningrig;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

/** Loaded inventories attached to faces of this computer's CC graph. */
final class RigStorageMonitor {
    private RigStorageMonitor(){}
    static String snapshot(ServerLevel world,ComputerEntity computer){
        StringBuilder out=new StringBuilder();
        CoordinatorEntity entry=RigTopology.nearest(world,computer.getBlockPos());
        if(entry==null)return "";
        int count=0;
        for(CoordinatorEntity cc:RigNetwork.component(world,entry)){
            for(Direction side:Direction.values()){
                int mode=cc.portMode(side);
                if(mode==0)continue;
                Container container=cc.external(world,side);
                if(container==null)continue;
                BlockPos pos=cc.getBlockPos().relative(side);
                int used=0,capacity=0;
                for(int slot=0;slot<container.getContainerSize();slot++){
                    ItemStack item=container.getItem(slot);
                    used+=item.getCount();
                    capacity+=item.isEmpty()?64:Math.min(64,item.getMaxStackSize());
                }
                String label=cc.containerNickname(side);
                if(label.isBlank())label="Container "+(count+1);
                String type=switch(mode){
                    case RigNetwork.FILLER->"Filler";
                    case RigNetwork.WALL_PAUSE->"Walls (Pause)";
                    case RigNetwork.WALL_CONTINUE->"Walls (Continue)";
                    case RigNetwork.OUTPUT->"Output";
                    case RigNetwork.HARDWARE->"Hardware";
                    default->"Unassigned";
                };
                out.append('\n').append("STORAGE").append('\t')
                    .append(label.replace('\t',' ').replace('\n',' ')).append('\t')
                    .append(type).append('\t')
                    .append(pos.getX()).append(',').append(pos.getY()).append(',').append(pos.getZ()).append('\t')
                    .append(used).append('\t').append(capacity);
                count++;
            }
        }
        return out.toString();
    }
}
