package dev.casz.miningrig;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jetbrains.annotations.Nullable;

public final class CoordinatorEntity extends BlockEntity implements Container, MenuProvider {
    private static final int CAPACITY=192, SLOTS=27;
    private final NonNullList<ItemStack> items=NonNullList.withSize(SLOTS,ItemStack.EMPTY);
    // OFF / IN / OUT, defaults to IN to avoid loops until a player selects outgoing faces.
    private final int[] mode={1,1,1,1,1,1};
    private int wirelessMask,adjacentMask;
    private ItemStack inFlight=ItemStack.EMPTY;
    private BlockPos flightTarget;
    private long flightStart;
    private int flightDuration;
    @Nullable private Display.ItemDisplay visual;
    private boolean dropped;

    private final ContainerData data=new ContainerData(){
        @Override public int get(int i){return i<6?mode[i]:i==6?wirelessMask|adjacentMask:i==7?stored():adjacentMask;}
        @Override public void set(int i,int v){if(i<6)mode[i]=Math.max(0,Math.min(2,v));}
        @Override public int getCount(){return 9;}
    };
    public CoordinatorEntity(BlockPos pos,BlockState state){super(MiningRig.COORDINATOR_ENTITY,pos,state);}
    public int stored(){return RigStorage.count(items,0);}
    public int freeStorage(){return CAPACITY-stored();}
    public boolean canReceive(List<ItemStack> cargo){return RigStorage.canInsert(items,0,CAPACITY,cargo);}
    public boolean receive(List<ItemStack> cargo){
        if(!canReceive(cargo))return false;
        if(!RigStorage.insert(items,0,CAPACITY,cargo))return false;
        setChanged();return true;
    }
    public void cycle(Direction direction){
        int i=direction.get3DDataValue();mode[i]=(mode[i]+1)%3;setChanged();
    }
    private CoordinatorEntity nearest(ServerLevel world,Direction dir){
        for(int dist=1;dist<=20;dist++){
            BlockPos at=worldPosition.relative(dir,dist);
            if(!world.hasChunkAt(at))return null;
            if(world.getBlockEntity(at) instanceof CoordinatorEntity cc)return cc;
        }
        return null;
    }
    private void refresh(ServerLevel world){
        int w=0,a=0;
        for(Direction dir:Direction.values()){
            int bit=1<<dir.get3DDataValue();
            if(nearest(world,dir)!=null)w|=bit;
            BlockPos p=worldPosition.relative(dir);
            if(world.hasChunkAt(p) && world.getBlockEntity(p) instanceof Container
                && !(world.getBlockEntity(p) instanceof CoordinatorEntity))a|=bit;
        }
        if(w!=wirelessMask||a!=adjacentMask){wirelessMask=w;adjacentMask=a;setChanged();}
    }
    public static void tick(Level level,BlockPos pos,BlockState state,CoordinatorEntity cc){
        if(!(level instanceof ServerLevel world))return;
        long time=world.getGameTime();
        if(time%10==0)cc.refresh(world);
        if(time%2==0)cc.collectAdjacentDrill(world);
        if(!cc.inFlight.isEmpty())cc.advance(world,time);
        else if(time%5==0 && !cc.outputToContainer(world))cc.dispatch(world,time);
    }
    private void collectAdjacentDrill(ServerLevel world){
        if(freeStorage()==0)return;
        for(Direction side:Direction.values()){
            if(mode[side.get3DDataValue()]!=1)continue;
            BlockPos next=worldPosition.relative(side);
            if(!world.hasChunkAt(next)||!(world.getBlockEntity(next) instanceof DrillEntity drill))continue;
            ItemStack sample=drill.peekForCollector();
            if(sample.isEmpty())continue;
            int n=Math.min(sample.getCount(),freeStorage());
            if(!canReceive(List.of(sample.copyWithCount(n))))continue;
            ItemStack taken=drill.takeForCollector(n);
            if(!taken.isEmpty())receive(List.of(taken));
            break;
        }
    }
    private boolean outputToContainer(ServerLevel world){
        for(Direction side:Direction.values()){
            if(mode[side.get3DDataValue()]!=2)continue;
            BlockPos next=worldPosition.relative(side);
            if(!world.hasChunkAt(next))continue;
            if(!(world.getBlockEntity(next) instanceof Container destination)
                    || destination instanceof CoordinatorEntity || destination instanceof DrillEntity)continue;
            for(int i=0;i<SLOTS;i++){
                ItemStack item=items.get(i);
                if(item.isEmpty())continue;
                for(int slot=0;slot<destination.getContainerSize();slot++){
                    if(!destination.canPlaceItem(slot,item))continue;
                    ItemStack existing=destination.getItem(slot);
                    if(!existing.isEmpty() && !ItemStack.isSameItemSameComponents(existing,item))continue;
                    int room=Math.min(item.getMaxStackSize(),64)-existing.getCount();
                    int count=Math.min(room,item.getCount());
                    if(count<=0)continue;
                    if(existing.isEmpty())destination.setItem(slot,item.copyWithCount(count));
                    else {existing.grow(count);destination.setChanged();}
                    item.shrink(count);
                    if(item.isEmpty())items.set(i,ItemStack.EMPTY);
                    setChanged();return true;
                }
            }
        }
        return false;
    }
    private void dispatch(ServerLevel world,long time){
        if(stored()==0)return;
        for(Direction side:Direction.values()){
            if(mode[side.get3DDataValue()]!=2)continue;
            // Adjacent container output takes precedence on its face.
            BlockPos beside=worldPosition.relative(side);
            if(world.hasChunkAt(beside) && world.getBlockEntity(beside) instanceof Container
                && !(world.getBlockEntity(beside) instanceof CoordinatorEntity))continue;
            CoordinatorEntity dest=nearest(world,side);
            if(dest==null || dest.mode[side.getOpposite().get3DDataValue()]!=1)continue;
            for(int i=0;i<SLOTS;i++){
                ItemStack stack=items.get(i);
                if(stack.isEmpty()||!dest.canReceive(List.of(stack)))continue;
                inFlight=stack.copy();items.set(i,ItemStack.EMPTY);
                flightTarget=dest.worldPosition.immutable();flightStart=time;
                int distance=Math.abs(worldPosition.getX()-flightTarget.getX())+
                    Math.abs(worldPosition.getY()-flightTarget.getY())+
                    Math.abs(worldPosition.getZ()-flightTarget.getZ());
                flightDuration=Math.max(3,(int)Math.ceil(distance*2.5));
                spawn(world);setChanged();return;
            }
        }
    }
    private void spawn(ServerLevel world){
        if(visual!=null && visual.isAlive())return;
        Display.ItemDisplay item=new Display.ItemDisplay(EntityType.ITEM_DISPLAY,world);
        item.setItemStack(inFlight.copyWithCount(1));
        item.setNoGravity(true);
        item.setPos(worldPosition.getX()+.5,worldPosition.getY()+.6,worldPosition.getZ()+.5);
        world.addFreshEntity(item);
        visual=item;
    }
    private void advance(ServerLevel world,long time){
        if(flightTarget==null || !world.hasChunkAt(flightTarget))return;
        double part=Math.min(1,Math.max(0,(time-flightStart)/(double)Math.max(1,flightDuration)));
        if(part<1){
            spawn(world);
            if(visual!=null)visual.setPos(worldPosition.getX()+.5+(flightTarget.getX()-worldPosition.getX())*part,
                    worldPosition.getY()+.55+(flightTarget.getY()-worldPosition.getY())*part+.12*Math.sin(Math.PI*part),
                    worldPosition.getZ()+.5+(flightTarget.getZ()-worldPosition.getZ())*part);
            return;
        }
        ItemStack cargo=inFlight.copy();
        if(!(world.getBlockEntity(flightTarget) instanceof CoordinatorEntity receiver)
                || !receiver.receive(List.of(cargo))) {
            if(!receive(List.of(cargo)))Block.popResource(world,worldPosition,cargo);
        }
        if(visual!=null)visual.discard();
        visual=null;inFlight=ItemStack.EMPTY;flightTarget=null;setChanged();
    }
    public void dropContents(){
        if(dropped || level==null || level.isClientSide())return;
        dropped=true;
        if(visual!=null)visual.discard();
        if(!inFlight.isEmpty())Block.popResource(level,worldPosition,inFlight.copy());
        inFlight=ItemStack.EMPTY;
        for(int i=0;i<SLOTS;i++){
            ItemStack item=items.get(i);
            if(!item.isEmpty())Block.popResource(level,worldPosition,item.copy());
            items.set(i,ItemStack.EMPTY);
        }
        setChanged();
    }
    @Override public int getContainerSize(){return SLOTS;}
    @Override public boolean isEmpty(){for(ItemStack s:items)if(!s.isEmpty())return false;return true;}
    @Override public ItemStack getItem(int i){return items.get(i);}
    @Override public ItemStack removeItem(int i,int n){
        ItemStack result=ContainerHelper.removeItem(items,i,n);if(!result.isEmpty())setChanged();return result;
    }
    @Override public ItemStack removeItemNoUpdate(int i){
        ItemStack result=ContainerHelper.takeItem(items,i);setChanged();return result;
    }
    @Override public boolean canPlaceItem(int i,ItemStack stack){
        return !(stack.getItem() instanceof DrillBit)&&freeStorage()>0;
    }
    @Override public int getMaxStackSize(ItemStack stack){
        return Math.min(stack.getMaxStackSize(),Math.max(1,freeStorage()));
    }
    @Override public void setItem(int i,ItemStack stack){
        if(!stack.isEmpty() && stack.getItem() instanceof DrillBit)return;
        int max=Math.max(0,Math.min(stack.getMaxStackSize(),CAPACITY-stored()+items.get(i).getCount()));
        items.set(i,stack.isEmpty()?ItemStack.EMPTY:stack.copyWithCount(Math.min(max,stack.getCount())));
        setChanged();
    }
    @Override public boolean stillValid(Player player){return Container.stillValidBlockEntity(this,player);}
    @Override public void clearContent(){for(int i=0;i<SLOTS;i++)items.set(i,ItemStack.EMPTY);setChanged();}
    @Override public Component getDisplayName(){return Component.literal("Collection Coordinator");}
    @Nullable @Override public AbstractContainerMenu createMenu(int id,Inventory inventory,Player player){
        return new CoordinatorMenu(id,inventory,this,data);
    }
    @Override protected void saveAdditional(ValueOutput out){
        super.saveAdditional(out);ContainerHelper.saveAllItems(out,items);
        for(int i=0;i<6;i++)out.putInt("Face"+i,mode[i]);
        if(!inFlight.isEmpty()){
            out.store("Transit",ItemStack.CODEC,inFlight);
            out.putLong("TransitTarget",flightTarget.asLong());
            out.putLong("TransitStart",flightStart);out.putInt("TransitDuration",flightDuration);
        }
    }
    @Override protected void loadAdditional(ValueInput in){
        super.loadAdditional(in);ContainerHelper.loadAllItems(in,items);
        for(int i=0;i<6;i++)mode[i]=Math.max(0,Math.min(2,in.getIntOr("Face"+i,1)));
        inFlight=in.read("Transit",ItemStack.CODEC).orElse(ItemStack.EMPTY);
        if(!inFlight.isEmpty()){
            flightTarget=BlockPos.of(in.getLongOr("TransitTarget",0));
            flightStart=in.getLongOr("TransitStart",0);
            flightDuration=Math.max(3,in.getIntOr("TransitDuration",50));
        }
    }
}
