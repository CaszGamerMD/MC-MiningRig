package dev.casz.miningrig;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/** Networked computer console; status text is synchronized through ComputerStatePayload. */
public final class ComputerMenu extends AbstractContainerMenu {
    @Nullable private final ComputerEntity computer;
    public ComputerMenu(int id,Inventory inventory){this(id,inventory,null);}
    public ComputerMenu(int id,Inventory inventory,@Nullable ComputerEntity computer){
        super(RigMenus.COMPUTER,id);this.computer=computer;
    }
    @Nullable public ComputerEntity computer(){return computer;}
    @Override public boolean stillValid(Player player){
        return computer==null ||
            (computer.getLevel()!=null && computer.getLevel().getBlockEntity(computer.getBlockPos())==computer
            && player.distanceToSqr(computer.getBlockPos().getX()+.5,
                computer.getBlockPos().getY()+.5,computer.getBlockPos().getZ()+.5)<=64);
    }
    @Override public boolean clickMenuButton(Player player,int id){
        if(computer==null)return id>=0&&id<=36;
        if(id>=0&&id<4)computer.select(id);
        else if(id>=10&&id<14)computer.toggle(id-10);
        else if(id>=20&&id<24)computer.unlink(id-20);
        else if(id==30)computer.setRadius(computer.radius()-1);
        else if(id==31)computer.setRadius(computer.radius()+1);
        else if(id==32)computer.setDepth(computer.depth()-1);
        else if(id==33)computer.setDepth(computer.depth()+1);
        else if(id==34)computer.setCircle(!computer.circle());
        else return false;
        broadcastChanges();return true;
    }
    @Override public ItemStack quickMoveStack(Player player,int index){return ItemStack.EMPTY;}
}
