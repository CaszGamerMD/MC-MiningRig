package dev.casz.miningrig;

import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.CustomModelData;

/** Stable machine UUID stops tablets from silently controlling a replacement computer. */
public final class TabletLink {
    private TabletLink(){}
    public record Target(String dimension,BlockPos computer,String identity){}
    public static Target read(ItemStack stack){
        CustomData data=stack.get(DataComponents.CUSTOM_DATA);
        if(data==null)return null;
        CompoundTag tag=data.copyTag();
        String dim=tag.getStringOr("TabletDim","");
        String id=tag.getStringOr("TabletComputerId","");
        if(dim.isBlank()||id.isBlank()||!tag.contains("TabletComputerPos"))return null;
        return new Target(dim,BlockPos.of(tag.getLongOr("TabletComputerPos",0)),id);
    }
    public static void write(ItemStack stack,Target target){
        CustomData old=stack.get(DataComponents.CUSTOM_DATA);
        CompoundTag tag=old==null?new CompoundTag():old.copyTag();
        tag.putString("TabletDim",target.dimension());
        tag.putLong("TabletComputerPos",target.computer().asLong());
        tag.putString("TabletComputerId",target.identity());
        stack.set(DataComponents.CUSTOM_DATA,CustomData.of(tag));
    }
    public static Target of(ServerLevel level,ComputerEntity entity){
        return new Target(level.dimension().identifier().toString(),entity.getBlockPos(),entity.tabletIdentity());
    }
    public static void light(ItemStack stack,String name){
        CustomModelData current=stack.getOrDefault(DataComponents.CUSTOM_MODEL_DATA,CustomModelData.EMPTY);
        if(!name.equals(current.getString(0)))
            stack.set(DataComponents.CUSTOM_MODEL_DATA,
                new CustomModelData(List.of(),List.of(),List.of(name),List.of()));
    }
}
