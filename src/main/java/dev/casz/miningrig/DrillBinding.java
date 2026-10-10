package dev.casz.miningrig;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;

/** Persistent per-drill item identity and its chosen mining computer. */
public final class DrillBinding {
    private DrillBinding(){}
    public record Link(String token,BlockPos computer, String dimension){}
    public static Link read(ItemStack stack){
        CustomData data=stack.get(DataComponents.CUSTOM_DATA);
        if(data==null)return null;
        CompoundTag tag=data.copyTag();
        String token=tag.getStringOr("CaszualRigToken","");
        String dimension=tag.getStringOr("CaszualRigDimension","");
        if(token.isBlank() || dimension.isBlank() || !tag.contains("CaszualRigComputer"))return null;
        return new Link(token,BlockPos.of(tag.getLongOr("CaszualRigComputer",0)),dimension);
    }
    public static void write(ItemStack stack,ServerLevel level,BlockPos computer,String token){
        CustomData current=stack.get(DataComponents.CUSTOM_DATA);
        CompoundTag tag=current==null?new CompoundTag():current.copyTag();
        tag.putString("CaszualRigToken",token);
        tag.putLong("CaszualRigComputer",computer.asLong());
        tag.putString("CaszualRigDimension",level.dimension().identifier().toString());
        stack.set(DataComponents.CUSTOM_DATA,CustomData.of(tag));
    }
}
