package dev.casz.miningrig;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.Display;
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

/**
 * Fast coordinator: physical cargo moves in multiple independent packet lanes.
 * One packet may contain up to 64 mixed items but needs only one visible ItemDisplay.
 * Flights remain in NBT until receiver inventory accepts their entire payload.
 */
public final class CoordinatorEntity extends BlockEntity implements Container,MenuProvider {
    private static final int CAPACITY=192,SLOTS=192,PACKET_SIZE=64;
    private static final int MAX_FLIGHTS=128,OUTBOUND_PER_TICK=4,CONTAINER_ACTIONS_PER_TICK=32;
    private final NonNullList<ItemStack> items=NonNullList.withSize(SLOTS,ItemStack.EMPTY);
    private final int[] mode={1,1,1,1,1,1};
    private int wirelessMask,adjacentMask,drillMask,page;
    private boolean dropped;
    private final List<Flight> flights=new ArrayList<>();

    private static final class Flight {
        final List<ItemStack> cargo;
        final BlockPos origin,target;
        final boolean fromDrill;
        final long start;
        final int duration;
        @Nullable Display.ItemDisplay visual;
        Flight(List<ItemStack> cargo,BlockPos origin,BlockPos target,boolean fromDrill,long start,int duration){
            this.cargo=cargo;this.origin=origin.immutable();this.target=target.immutable();
            this.fromDrill=fromDrill;this.start=start;this.duration=Math.max(3,duration);
        }
        int count(){int n=0;for(ItemStack s:cargo)n+=s.getCount();return n;}
        void discard(){if(visual!=null)visual.discard();visual=null;}
    }
    private final ContainerData data=new ContainerData(){
        @Override public int get(int i){
            return i<6?mode[i]:i==6?wirelessMask|adjacentMask
                :i==7?stored():i==8?adjacentMask:i==9?page:drillMask;
        }
        @Override public void set(int i,int v){
            if(i<6)mode[i]=Math.max(0,Math.min(2,v));
            else if(i==9)page=Math.max(0,Math.min(7,v));
        }
        @Override public int getCount(){return 11;}
    };
    public CoordinatorEntity(BlockPos pos,BlockState state){super(MiningRig.COORDINATOR_ENTITY,pos,state);}
    public boolean acceptsDrillFromBelow(){return mode[Direction.DOWN.get3DDataValue()]==1;}
    public int stored(){return RigStorage.count(items,0);}
    public int freeStorage(){return CAPACITY-stored();}
    public boolean canReceive(List<ItemStack> cargo){return RigStorage.canInsert(items,0,CAPACITY,cargo);}
    public boolean receive(List<ItemStack> cargo){
        if(!canReceive(cargo)||!RigStorage.insert(items,0,CAPACITY,cargo))return false;
        setChanged();return true;
    }
    public void cycle(Direction direction){
        int i=direction.get3DDataValue();mode[i]=(mode[i]+1)%3;setChanged();
    }
    private CoordinatorEntity nearest(ServerLevel world,Direction dir){
        for(int n=1;n<=20;n++){
            BlockPos at=worldPosition.relative(dir,n);
            if(!world.hasChunkAt(at))return null;
            if(world.getBlockEntity(at) instanceof CoordinatorEntity cc)return cc;
        }
        return null;
    }
    private DrillEntity drillBelow(ServerLevel world){
        for(int n=1;n<=20;n++){
            BlockPos at=worldPosition.below(n);
            if(!world.hasChunkAt(at))return null;
            if(world.getBlockEntity(at) instanceof DrillEntity drill)return drill;
        }
        return null;
    }
    private void refresh(ServerLevel world){
        int w=0,a=0;
        for(Direction dir:Direction.values()){
            int bit=1<<dir.get3DDataValue();
            if(nearest(world,dir)!=null)w|=bit;
            BlockPos adjacent=worldPosition.relative(dir);
            if(world.hasChunkAt(adjacent)&&world.getBlockEntity(adjacent) instanceof Container container
                    && !(container instanceof CoordinatorEntity))a|=bit;
        }
        int d=drillBelow(world)==null?0:1<<Direction.DOWN.get3DDataValue();
        if(w!=wirelessMask||a!=adjacentMask||d!=drillMask){
            wirelessMask=w;adjacentMask=a;drillMask=d;setChanged();
        }
        // Only render a hopper/pipe for a VALID connected side. Both adjacent
        // containers and wireless connections use the same side preference.
        BlockState visualState=getBlockState();
        for(Direction side:Direction.values()){
            int index=side.get3DDataValue(),bit=1<<index;
            boolean connected=((w|a|d)&bit)!=0;
            CoordinatorBlock.PortVisual appearance=!connected||mode[index]==0
                ?CoordinatorBlock.PortVisual.OFF
                :mode[index]==1?CoordinatorBlock.PortVisual.IN:CoordinatorBlock.PortVisual.OUT;
            visualState=visualState.setValue(CoordinatorBlock.PORTS[index],appearance);
        }
        if(visualState!=getBlockState())
            world.setBlock(worldPosition,visualState,Block.UPDATE_CLIENTS);
    }
    public static void tick(Level level,BlockPos pos,BlockState state,CoordinatorEntity cc){
        if(!(level instanceof ServerLevel world))return;
        long now=world.getGameTime();
        if(now%10==0)cc.refresh(world);
        cc.advance(world,now);
        // Adjacent container output does not consume wireless travel bandwidth.
        for(int n=0;n<CONTAINER_ACTIONS_PER_TICK;n++)
            if(!cc.outputToContainer(world))break;
        cc.collectDrillBelow(world,now);
        for(int n=0;n<OUTBOUND_PER_TICK;n++)
            if(!cc.dispatch(world,now))break;
    }
    /** Preview and remove mixed items as one 64-item packet, without waste. */
    private static List<ItemStack> peek(Container container,int start,int limit){
        int remaining=limit;
        List<ItemStack> cargo=new ArrayList<>();
        for(int i=start;i<container.getContainerSize()&&remaining>0;i++){
            ItemStack s=container.getItem(i);
            if(s.isEmpty())continue;
            int count=Math.min(s.getCount(),remaining);
            cargo.add(s.copyWithCount(count));remaining-=count;
        }
        return cargo;
    }
    private static List<ItemStack> take(Container container,int start,int limit){
        int remaining=limit;
        List<ItemStack> result=new ArrayList<>();
        for(int i=start;i<container.getContainerSize()&&remaining>0;i++){
            ItemStack s=container.getItem(i);
            if(s.isEmpty())continue;
            ItemStack removed=container.removeItem(i,Math.min(remaining,s.getCount()));
            if(!removed.isEmpty()){result.add(removed);remaining-=removed.getCount();}
        }
        return result;
    }
    private int inboundFromDrill(){
        int total=0;
        for(Flight flight:flights)if(flight.fromDrill)total+=flight.count();
        return total;
    }
    private void startFlight(ServerLevel world,List<ItemStack> cargo,BlockPos origin,
                             BlockPos target,boolean fromDrill,long now){
        int distance=Math.abs(origin.getX()-target.getX())+
            Math.abs(origin.getY()-target.getY())+Math.abs(origin.getZ()-target.getZ());
        Flight flight=new Flight(cargo,origin,target,fromDrill,now,
            Math.max(3,(int)Math.ceil(distance*2.5)));
        flights.add(flight);
        spawn(world,flight);
        setChanged();
    }
    private void collectDrillBelow(ServerLevel world,long now){
        if(!acceptsDrillFromBelow()||flights.size()>=MAX_FLIGHTS)return;
        // Reserve room for every packet already traveling upward into this CC.
        int budget=Math.min(PACKET_SIZE,Math.max(0,freeStorage()-inboundFromDrill()));
        if(budget==0)return;
        DrillEntity drill=drillBelow(world);
        if(drill==null)return;
        List<ItemStack> cargo=peek(drill,DrillEntity.BIT_SLOTS,budget);
        if(cargo.isEmpty()||!canReceive(cargo))return;
        List<ItemStack> taken=take(drill,DrillEntity.BIT_SLOTS,budget);
        if(!taken.isEmpty())startFlight(world,taken,drill.getBlockPos(),worldPosition,true,now);
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
                    if(destination instanceof WorldlyContainer sided){
                        boolean valid=false;
                        for(int allowed:sided.getSlotsForFace(side.getOpposite()))if(allowed==slot){valid=true;break;}
                        if(!valid||!sided.canPlaceItemThroughFace(slot,item,side.getOpposite()))continue;
                    }
                    ItemStack existing=destination.getItem(slot);
                    if(!existing.isEmpty()&&!ItemStack.isSameItemSameComponents(existing,item))continue;
                    int room=Math.min(Math.min(item.getMaxStackSize(),64),destination.getMaxStackSize(item))
                        -existing.getCount();
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
    private boolean dispatch(ServerLevel world,long now){
        if(stored()==0||flights.size()>=MAX_FLIGHTS)return false;
        for(Direction side:Direction.values()){
            if(mode[side.get3DDataValue()]!=2)continue;
            BlockPos adjacent=worldPosition.relative(side);
            if(world.hasChunkAt(adjacent)&&world.getBlockEntity(adjacent) instanceof Container container
                    && !(container instanceof CoordinatorEntity))continue;
            CoordinatorEntity target=nearest(world,side);
            if(target==null||target.mode[side.getOpposite().get3DDataValue()]!=1)continue;
            List<ItemStack> cargo=peek(this,0,PACKET_SIZE);
            if(cargo.isEmpty()||!target.canReceive(cargo))continue;
            List<ItemStack> taken=take(this,0,PACKET_SIZE);
            if(taken.isEmpty())return false;
            startFlight(world,taken,worldPosition,target.worldPosition,false,now);
            return true;
        }
        return false;
    }
    private void spawn(ServerLevel world,Flight flight){
        if(flight.visual!=null&&flight.visual.isAlive())return;
        if(flight.cargo.isEmpty())return;
        Display.ItemDisplay item=new Display.ItemDisplay(
            net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getValue(
                net.minecraft.resources.Identifier.withDefaultNamespace("item_display")),world);
        item.setItemStack(flight.cargo.get(0).copyWithCount(1));
        item.setNoGravity(true);
        item.setPos(flight.origin.getX()+.5,flight.origin.getY()+.6,flight.origin.getZ()+.5);
        world.addFreshEntity(item);
        flight.visual=item;
    }
    private void advance(ServerLevel world,long now){
        boolean changed=false;
        for(Iterator<Flight> iterator=flights.iterator();iterator.hasNext();){
            Flight flight=iterator.next();
            if(!world.hasChunkAt(flight.target))continue;
            double part=Math.min(1,Math.max(0,(now-flight.start)/(double)flight.duration));
            if(part<1){
                spawn(world,flight);
                if(flight.visual!=null)flight.visual.setPos(
                    flight.origin.getX()+.5+(flight.target.getX()-flight.origin.getX())*part,
                    flight.origin.getY()+.55+(flight.target.getY()-flight.origin.getY())*part
                        +.12*Math.sin(Math.PI*part),
                    flight.origin.getZ()+.5+(flight.target.getZ()-flight.origin.getZ())*part);
                continue;
            }
            boolean delivered=false;
            if(world.getBlockEntity(flight.target) instanceof CoordinatorEntity receiver)
                delivered=receiver.receive(flight.cargo);
            else if(!flight.fromDrill)delivered=receive(flight.cargo);
            // Full target: leave the packet safely in transit and retry next tick,
            // rather than ejecting it into the world or voiding it.
            if(!delivered)continue;
            flight.discard();iterator.remove();changed=true;
        }
        if(changed)setChanged();
    }
    public void dropContents(){
        if(dropped||level==null||level.isClientSide())return;
        dropped=true;
        for(Flight flight:flights){
            flight.discard();
            for(ItemStack item:flight.cargo)
                if(!item.isEmpty())Block.popResource(level,worldPosition,item.copy());
        }
        flights.clear();
        for(int i=0;i<SLOTS;i++){
            ItemStack item=items.get(i);
            if(!item.isEmpty())Block.popResource(level,worldPosition,item.copy());
            items.set(i,ItemStack.EMPTY);
        }
        setChanged();
    }
    @Override public int getContainerSize(){return SLOTS;}
    @Override public boolean isEmpty(){for(ItemStack s:items)if(!s.isEmpty())return false;return true;}
    @Override public ItemStack getItem(int slot){return items.get(slot);}
    @Override public ItemStack removeItem(int slot,int amount){
        ItemStack result=ContainerHelper.removeItem(items,slot,amount);
        if(!result.isEmpty())setChanged();return result;
    }
    @Override public ItemStack removeItemNoUpdate(int slot){
        ItemStack result=ContainerHelper.takeItem(items,slot);setChanged();return result;
    }
    @Override public boolean canPlaceItem(int slot,ItemStack item){
        return !(item.getItem() instanceof DrillBit)&&freeStorage()>0;
    }
    @Override public int getMaxStackSize(ItemStack stack){
        return Math.min(stack.getMaxStackSize(),Math.max(1,freeStorage()));
    }
    @Override public void setItem(int slot,ItemStack stack){
        if(!stack.isEmpty()&&stack.getItem() instanceof DrillBit)return;
        int max=Math.max(0,Math.min(stack.getMaxStackSize(),CAPACITY-stored()+items.get(slot).getCount()));
        items.set(slot,stack.isEmpty()?ItemStack.EMPTY:stack.copyWithCount(Math.min(max,stack.getCount())));
        setChanged();
    }
    @Override public boolean stillValid(Player player){return Container.stillValidBlockEntity(this,player);}
    @Override public void clearContent(){
        for(int i=0;i<SLOTS;i++)items.set(i,ItemStack.EMPTY);
        setChanged();
    }
    @Override public Component getDisplayName(){return Component.literal("Collection Coordinator");}
    @Nullable @Override public AbstractContainerMenu createMenu(int id,Inventory inventory,Player player){
        return new CoordinatorMenu(id,inventory,new PagedRigContainer(this,data,9,0,27),data);
    }
    @Override protected void saveAdditional(ValueOutput out){
        super.saveAdditional(out);ContainerHelper.saveAllItems(out,items);
        for(int i=0;i<6;i++)out.putInt("Face"+i,mode[i]);
        out.putInt("FlightCount",flights.size());
        for(int i=0;i<flights.size();i++){
            Flight f=flights.get(i);
            String key="Flight"+i;
            out.putLong(key+"Origin",f.origin.asLong());
            out.putLong(key+"Target",f.target.asLong());
            out.putBoolean(key+"FromDrill",f.fromDrill);
            out.putLong(key+"Start",f.start);
            out.putInt(key+"Duration",f.duration);
            out.putInt(key+"Items",f.cargo.size());
            for(int j=0;j<f.cargo.size();j++)
                out.store(key+"Stack"+j,ItemStack.CODEC,f.cargo.get(j));
        }
    }
    @Override protected void loadAdditional(ValueInput in){
        super.loadAdditional(in);ContainerHelper.loadAllItems(in,items);
        for(Flight flight:flights)flight.discard();
        flights.clear();
        for(int i=0;i<6;i++)mode[i]=Math.max(0,Math.min(2,in.getIntOr("Face"+i,1)));
        for(int i=0;i<Math.min(MAX_FLIGHTS,in.getIntOr("FlightCount",0));i++){
            String key="Flight"+i;
            List<ItemStack> cargo=new ArrayList<>();
            for(int j=0;j<Math.min(64,in.getIntOr(key+"Items",0));j++){
                ItemStack stack=in.read(key+"Stack"+j,ItemStack.CODEC).orElse(ItemStack.EMPTY);
                if(!stack.isEmpty())cargo.add(stack);
            }
            if(!cargo.isEmpty())flights.add(new Flight(cargo,
                BlockPos.of(in.getLongOr(key+"Origin",worldPosition.asLong())),
                BlockPos.of(in.getLongOr(key+"Target",worldPosition.asLong())),
                in.getBooleanOr(key+"FromDrill",false),in.getLongOr(key+"Start",0),
                in.getIntOr(key+"Duration",50)));
        }
        // Version 0.3.0 stored one legacy packet. Import it without losing cargo.
        ItemStack legacy=in.read("Transit",ItemStack.CODEC).orElse(ItemStack.EMPTY);
        if(!legacy.isEmpty()&&flights.size()<MAX_FLIGHTS){
            flights.add(new Flight(List.of(legacy),
                BlockPos.of(in.getLongOr("TransitOrigin",worldPosition.asLong())),
                BlockPos.of(in.getLongOr("TransitTarget",worldPosition.asLong())),
                in.getBooleanOr("TransitFromDrill",false),in.getLongOr("TransitStart",0),
                in.getIntOr("TransitDuration",50)));
        }
    }
}
