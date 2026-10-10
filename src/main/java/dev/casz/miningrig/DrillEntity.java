package dev.casz.miningrig;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jetbrains.annotations.Nullable;

/**
 * Three independent mining lanes. Each equipped bit is one worker with its own
 * hardness-progress counter, so three bits can mine three blocks simultaneously.
 * Storage is nine visible stacks but is limited to 64 individual items total.
 */
public final class DrillEntity extends BlockEntity implements Container, MenuProvider {
    public static final int BIT_SLOTS=3, SLOT_COUNT=67, CAPACITY=64;
    private final NonNullList<ItemStack> items=NonNullList.withSize(SLOT_COUNT,ItemStack.EMPTY);
    private final int[] assigned={-1,-1,-1};
    private final float[] progress=new float[BIT_SLOTS];
    private int startY, completedLayers, cursor;
    private boolean initialized, relocating, contentsDropped;
    private int page;
    private final DrillSealing sealing=new DrillSealing();
    public boolean fluidWarning(){return sealing.warning();}
    private long workTicks;
    private String linkedToken="";
    private BlockPos computerPos;
    private String computerDimension="";
    private boolean linkConfirmed;
    private final ContainerData menuData=new ContainerData() {
        @Override public int get(int index) {return index==0?stored():index==1?page:0;}
        @Override public void set(int index,int value) {if(index==1)page=Math.max(0,Math.min(7,value));}
        @Override public int getCount() {return 2;}
    };

    public DrillEntity(BlockPos pos,BlockState state){super(MiningRig.DRILL_ENTITY,pos,state);}
    public boolean relocating(){return relocating;}
    public void applyBinding(DrillBinding.Link link){
        linkedToken=link.token();computerPos=link.computer();
        computerDimension=link.dimension();linkConfirmed=false;setChanged();
    }
    public static void tick(Level level,BlockPos pos,BlockState state,DrillEntity drill){
        if(!(level instanceof ServerLevel server) || drill.computerPos==null
                || drill.linkConfirmed || server.getGameTime()%20!=0)return;
        if(!server.dimension().identifier().toString().equals(drill.computerDimension)
                || !server.hasChunkAt(drill.computerPos))return;
        if(server.getBlockEntity(drill.computerPos) instanceof ComputerEntity computer
                && computer.placed(drill.linkedToken,pos)){
            drill.linkConfirmed=true;drill.setChanged();
        }
    }
    public boolean canConnectAbove(ServerLevel world){
        for(int n=1;n<=20;n++){
            BlockPos at=worldPosition.above(n);
            if(!world.hasChunkAt(at))return false;
            if(world.getBlockEntity(at) instanceof CoordinatorEntity cc && cc.acceptsDrillFromBelow())return true;
        }
        return false;
    }
    public int estimatedSeconds(int radius,int depth,boolean circle,boolean mining){
        if(completedLayers>=depth)return 0;
        if(!hasWorkingBits())return -1;
        int perLayer=0;
        for(int x=-radius;x<=radius;x++)for(int z=-radius;z<=radius;z++)
            if(!circle||x*x+z*z<=radius*radius)perLayer++;
        int edge=2*radius+1;
        int visited=completedLayers*perLayer;
        for(int i=0;i<Math.min(cursor,edge*edge);i++){
            int x=i%edge-radius,z=i/edge-radius;
            if(!circle||x*x+z*z<=radius*radius)visited++;
        }
        for(int idx:assigned)if(idx>=0)visited=Math.max(0,visited-1);
        int remaining=Math.max(0,depth*perLayer-visited);
        if(remaining==0)return 0;
        double ticksPerCell;
        if(workTicks>0 && visited>0)ticksPerCell=Math.max(1,workTicks/(double)visited);
        else {
            double speed=0;
            for(int i=0;i<BIT_SLOTS;i++)if(items.get(i).getItem() instanceof DrillBit bit)
                speed+=bit.miningSpeed()/(30.0*1.5);
            ticksPerCell=speed<=0?60:Math.max(1,1/speed);
        }
        return Math.max(1,(int)Math.ceil(remaining*ticksPerCell/20));
    }
    public int stored(){return RigStorage.count(items,BIT_SLOTS);}
    public int freeStorage(){return CAPACITY-stored();}
    public boolean canReceive(List<ItemStack> drops){return RigStorage.canInsert(items,BIT_SLOTS,CAPACITY,drops);}
    public boolean receive(List<ItemStack> drops) {
        if(!canReceive(drops))return false;
        boolean ok=RigStorage.insert(items,BIT_SLOTS,CAPACITY,drops);
        if(ok)setChanged();
        return ok;
    }
    public boolean hasWorkingBits() {
        for(int i=0;i<BIT_SLOTS;i++)if(items.get(i).getItem() instanceof DrillBit)return true;
        return false;
    }
    public int insertBit(ItemStack stack) {
        if(!(stack.getItem() instanceof DrillBit))return -1;
        for(int i=0;i<BIT_SLOTS;i++)if(items.get(i).isEmpty()){
            items.set(i,stack.copyWithCount(1));setChanged();return i;
        }
        return -1;
    }
    public ItemStack takeForCollector(int limit){
        for(int i=BIT_SLOTS;i<SLOT_COUNT;i++) {
            ItemStack stack=items.get(i);
            if(!stack.isEmpty()){
                ItemStack extracted=stack.split(Math.min(limit,stack.getCount()));
                if(stack.isEmpty())items.set(i,ItemStack.EMPTY);
                setChanged();return extracted;
            }
        }
        return ItemStack.EMPTY;
    }
    public ItemStack peekForCollector(){
        for(int i=BIT_SLOTS;i<SLOT_COUNT;i++)if(!items.get(i).isEmpty())return items.get(i);
        return ItemStack.EMPTY;
    }
    private int enchantmentLevel(ServerLevel world,ItemStack stack,net.minecraft.resources.ResourceKey<Enchantment> key){
        return stack.getOrDefault(net.minecraft.core.component.DataComponents.ENCHANTMENTS,
                net.minecraft.world.item.enchantment.ItemEnchantments.EMPTY)
                .getLevel(world.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(key));
    }
    private float speed(ServerLevel world,ItemStack tool,BlockState state) {
        DrillBit bit=(DrillBit) tool.getItem();
        float speed=bit.miningSpeed();
        int eff=enchantmentLevel(world,tool,Enchantments.EFFICIENCY);
        if(eff>0)speed+=eff*eff+1;
        float hardness=state.getDestroySpeed(world,worldPosition);
        return hardness<=0?1.0f:Math.min(1.0f,speed/Math.max(0.00001f,hardness*30.0f));
    }
    /** Called once each server tick, not every 8 ticks; speed follows vanilla break progress. */
    public ComputerEntity.Status mineTick(ServerLevel world,ComputerEntity computer) {
        if(completedLayers>=computer.depth())return ComputerEntity.Status.DONE;
        workTicks++;
        ComputerEntity.Status fluid=sealing.beforeMining(world,this,computer);
        if(fluid!=null)return fluid;
        if(!hasWorkingBits())return ComputerEntity.Status.OUT_OF_BITS;
        if(freeStorage()==0)return ComputerEntity.Status.FULL;
        if(!initialized){startY=worldPosition.getY();initialized=true;}
        boolean expanded=computer.wallMode()==2&&!sealing.fallback();
        int r=computer.radius()+(expanded?1:0),edge=2*r+1,total=edge*edge;
        int active=0;
        for(int lane=0;lane<BIT_SLOTS;lane++){
            ItemStack bit=items.get(lane);
            if(!(bit.getItem() instanceof DrillBit drillBit)) {
                if(assigned[lane]>=0){cursor=Math.min(cursor,assigned[lane]);assigned[lane]=-1;}
                progress[lane]=0;continue;
            }
            active++;
            if(assigned[lane]<0){
                while(cursor<total){
                    int index=cursor++,dx=index%edge-r,dz=index/edge-r;
                    if(!DrillSealing.contains(computer,dx,dz,expanded))continue;
                    BlockPos p=worldPosition.offset(dx,-1,dz);
                    if(p.getY()<world.getMinY())return ComputerEntity.Status.DONE;
                    if(!world.hasChunkAt(p)){cursor--;return ComputerEntity.Status.BLOCKED;}
                    if(world.getBlockState(p).isAir()||DrillSealing.immune(world.getBlockState(p)))continue;
                    assigned[lane]=index;break;
                }
            }
            if(assigned[lane]<0)continue;
            int index=assigned[lane],dx=index%edge-r,dz=index/edge-r;
            BlockPos p=worldPosition.offset(dx,-1,dz);
            if(!world.hasChunkAt(p))return ComputerEntity.Status.BLOCKED;
            BlockState state=world.getBlockState(p);
            if(state.isAir()||DrillSealing.immune(state)){
                assigned[lane]=-1;progress[lane]=0;continue;
            }
            if(state.getDestroySpeed(world,p)<0 || state.hasBlockEntity() || !drillBit.canMine(state))
                return ComputerEntity.Status.BLOCKED;
            progress[lane]+=speed(world,bit,state);
            if(progress[lane]<1.0f)continue;
            List<ItemStack> drops=Block.getDrops(state,world,p,null,null,bit);
            if(!canReceive(drops))return ComputerEntity.Status.FULL;
            if(!world.setBlock(p,Blocks.AIR.defaultBlockState(),Block.UPDATE_ALL))return ComputerEntity.Status.BLOCKED;
            if(!receive(drops))return ComputerEntity.Status.FULL;
            assigned[lane]=-1;progress[lane]=0;
            // Enchanted Unbreaking tips consume durability only on a successful roll.
            int unbreaking=enchantmentLevel(world,bit,Enchantments.UNBREAKING);
            if(world.getRandom().nextInt(unbreaking+1)==0){
                int wear=bit.getDamageValue()+1;
                if(wear>=bit.getMaxDamage())items.set(lane,ItemStack.EMPTY);
                else bit.setDamageValue(wear);
            }
            setChanged();
        }
        if(active==0)return ComputerEntity.Status.OUT_OF_BITS;
        if(cursor>=total){
            for(int lane=0;lane<BIT_SLOTS;lane++)if(assigned[lane]>=0)return ComputerEntity.Status.RUNNING;
            ComputerEntity.Status walls=sealing.afterMining(world,this,computer);
            if(walls!=null)return walls;
            return descend(world,computer);
        }
        return ComputerEntity.Status.RUNNING;
    }
    private ComputerEntity.Status descend(ServerLevel world,ComputerEntity computer){
        int nextLayer=completedLayers+1;
        if(nextLayer>=computer.depth()) {
            completedLayers=nextLayer;setChanged();return ComputerEntity.Status.DONE;
        }
        BlockPos previous=worldPosition;
        BlockPos next=previous.below();
        if(next.getY()<world.getMinY())return ComputerEntity.Status.DONE;
        if(!world.hasChunkAt(next) || !world.getBlockState(next).isAir())return ComputerEntity.Status.BLOCKED;
        // Mark BEFORE any neighbor changes. Once the destination is populated,
        // clear every source stack before replacing the old block, so neither
        // Minecraft's replacement callbacks nor the drill's own drop path can
        // duplicate tools or mined items during the downward move.
        relocating=true;
        if(!world.setBlock(next,MiningRig.DRILL.defaultBlockState(),Block.UPDATE_CLIENTS)){
            relocating=false;return ComputerEntity.Status.BLOCKED;
        }
        if(!(world.getBlockEntity(next) instanceof DrillEntity moved)) {
            relocating=false;
            world.removeBlock(next,false);return ComputerEntity.Status.BLOCKED;
        }
        for(int i=0;i<SLOT_COUNT;i++)moved.items.set(i,items.get(i).copy());
        moved.initialized=true;moved.startY=startY;
        moved.completedLayers=nextLayer;moved.cursor=0;
        moved.workTicks=workTicks;
        moved.linkedToken=linkedToken;moved.computerPos=computerPos;
        moved.computerDimension=computerDimension;
        moved.linkConfirmed=linkConfirmed;
        sealing.copyTo(moved.sealing);
        moved.sealing.nextLayer();
        moved.setChanged();
        // Ownership has transferred: source must be genuinely EMPTY before
        // callbacks run for the block at the previous position.
        for(int i=0;i<SLOT_COUNT;i++)items.set(i,ItemStack.EMPTY);
        contentsDropped=true;
        // Move the two-block structure down together. The old lower block
        // becomes the new upper block; no inventory is dropped during motion.
        if(world.getBlockState(previous.above()).is(MiningRig.DRILL_UPPER))
            world.setBlock(previous.above(),Blocks.AIR.defaultBlockState(),Block.UPDATE_ALL);
        world.setBlock(previous,MiningRig.DRILL_UPPER.defaultBlockState(),Block.UPDATE_ALL);
        computer.moved(previous,next);
        return ComputerEntity.Status.RUNNING;
    }
    /** Drops only on actual destruction, not on the machine moving to the next Y. */
    public void dropInventory(){
        if(contentsDropped || relocating || level==null || level.isClientSide())return;
        contentsDropped=true;
        for(ItemStack stack:items)if(!stack.isEmpty())Block.popResource(level,worldPosition,stack.copy());
        for(int i=0;i<SLOT_COUNT;i++)items.set(i,ItemStack.EMPTY);
        setChanged();
    }
    @Override public int getContainerSize(){return SLOT_COUNT;}
    @Override public boolean isEmpty(){for(ItemStack item:items)if(!item.isEmpty())return false;return true;}
    @Override public ItemStack getItem(int slot){return items.get(slot);}
    @Override public ItemStack removeItem(int slot,int count) {
        ItemStack item=ContainerHelper.removeItem(items,slot,count);
        if(!item.isEmpty()){if(slot<BIT_SLOTS){assigned[slot]=-1;progress[slot]=0;}setChanged();}
        return item;
    }
    @Override public ItemStack removeItemNoUpdate(int slot) {
        ItemStack item=ContainerHelper.takeItem(items,slot);
        if(slot<BIT_SLOTS){assigned[slot]=-1;progress[slot]=0;}
        setChanged();return item;
    }
    @Override public boolean canPlaceItem(int slot,ItemStack stack) {
        return slot<BIT_SLOTS?stack.getItem() instanceof DrillBit:
            !(stack.getItem() instanceof DrillBit) && freeStorage()>0;
    }
    @Override public int getMaxStackSize(ItemStack stack) {
        return Math.min(stack.getMaxStackSize(),Math.max(1,freeStorage()));
    }
    @Override public void setItem(int slot,ItemStack stack) {
        if(slot<BIT_SLOTS){
            if(!stack.isEmpty() && !(stack.getItem() instanceof DrillBit))return;
            items.set(slot,stack.isEmpty()?ItemStack.EMPTY:stack.copyWithCount(1));
        } else {
            if(!stack.isEmpty() && stack.getItem() instanceof DrillBit)return;
            int old=items.get(slot).getCount();
            int allowed=Math.max(0,Math.min(stack.getMaxStackSize(),CAPACITY-stored()+old));
            items.set(slot,stack.isEmpty()?ItemStack.EMPTY:stack.copyWithCount(Math.min(stack.getCount(),allowed)));
        }
        setChanged();
    }
    @Override public boolean stillValid(Player player){return Container.stillValidBlockEntity(this,player);}
    @Override public void clearContent(){for(int i=0;i<SLOT_COUNT;i++)items.set(i,ItemStack.EMPTY);setChanged();}
    @Override public Component getDisplayName(){return Component.literal("Mining Drill");}
    @Nullable @Override public AbstractContainerMenu createMenu(int id,Inventory playerInventory,Player player) {
        return new DrillMenu(id,playerInventory,new PagedRigContainer(this,menuData,1,3,9),menuData);
    }
    @Override protected void saveAdditional(ValueOutput out){
        super.saveAdditional(out);
        ContainerHelper.saveAllItems(out,items);
        out.putInt("StartY",startY);out.putInt("Layers",completedLayers);
        out.putInt("Cursor",cursor);out.putBoolean("Initialized",initialized);
        out.putLong("WorkTicks",workTicks);
        sealing.save(out);
        if(computerPos!=null){
            out.putLong("Supervisor",computerPos.asLong());
            out.putString("SupervisorDimension",computerDimension);
            out.putString("SupervisorToken",linkedToken);
            out.putBoolean("SupervisorConfirmed",linkConfirmed);
        }
        for(int i=0;i<BIT_SLOTS;i++){
            out.putInt("Assigned"+i,assigned[i]);
            out.putFloat("Progress"+i,progress[i]);
        }
    }
    @Override protected void loadAdditional(ValueInput in){
        super.loadAdditional(in);
        ContainerHelper.loadAllItems(in,items);
        // Old saves had one tip. Migrate it to bit slot zero.
        if(items.get(0).isEmpty())items.set(0,in.read("Tip",ItemStack.CODEC).orElse(ItemStack.EMPTY));
        startY=in.getIntOr("StartY",worldPosition.getY());completedLayers=in.getIntOr("Layers",0);
        cursor=in.getIntOr("Cursor",0);initialized=in.getBooleanOr("Initialized",false);
        workTicks=in.getLongOr("WorkTicks",0);
        sealing.load(in);
        computerPos=in.getLong("Supervisor").isPresent()
            ? BlockPos.of(in.getLongOr("Supervisor",0)):null;
        computerDimension=in.getStringOr("SupervisorDimension","");
        linkedToken=in.getStringOr("SupervisorToken","");
        linkConfirmed=in.getBooleanOr("SupervisorConfirmed",false);
        for(int i=0;i<BIT_SLOTS;i++){
            assigned[i]=in.getIntOr("Assigned"+i,-1);
            progress[i]=in.getFloatOr("Progress"+i,0.0f);
        }
    }
}
