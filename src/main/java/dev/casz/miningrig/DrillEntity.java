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
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
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
 * Mining output, filler and wall materials are three separate 64-item bundles.
 */
public final class DrillEntity extends BlockEntity implements Container, MenuProvider {
    public static final int BIT_SLOTS=3, OUTPUT_SLOTS=64, UPGRADE_SLOT=BIT_SLOTS+OUTPUT_SLOTS,
        SLOT_COUNT=UPGRADE_SLOT+1, CAPACITY=64;
    private final NonNullList<ItemStack> items=NonNullList.withSize(SLOT_COUNT,ItemStack.EMPTY);
    private final int[] assigned={-1,-1,-1};
    private final float[] progress=new float[BIT_SLOTS];
    private int startY, completedLayers, cursor;
    private boolean initialized, relocating, contentsDropped, layerBitsRequested;
    private String hardwareIdentity="";
    @Nullable private BlockPos lastConnectedCoordinator;
    @Nullable private BlockPos lastConnectedPosition;
    private final RigHardware hardware=new RigHardware();
    private int page,storageBank;
    private boolean randomWalls;
    @Nullable private BlockPos lastWallSource;
    private int lastWallSourceSlot=-1;
    private final DrillSealing sealing=new DrillSealing();
    private float placementCharge,wallWorkCharge;
    private long lastCuttingAudioTick=Long.MIN_VALUE;
    private int placementBit=-1;
    private BlockPos wallWorkTarget;
    public int completedLayers(){return completedLayers;}
    /** Travel direction is saved in the block state, including during motion. */
    public Direction drillingDirection(){
        BlockState state=getBlockState();
        Direction dir=state.hasProperty(DrillBlock.FACING)
            ?state.getValue(DrillBlock.FACING):Direction.DOWN;
        return dir==Direction.UP?Direction.DOWN:dir;
    }
    /**
     * Map the computer's two-dimensional x/z footprint onto the NEXT
     * excavation slice: horizontal x/z when mining down, vertical/upright
     * when traveling horizontally. The direction property never changes.
     */
    public BlockPos slicePosition(int u,int v){
        Direction forward=drillingDirection();
        BlockPos face=worldPosition.relative(forward);
        if(forward==Direction.DOWN)return face.offset(u,0,v);
        return face.relative(forward.getClockWise(),u).above(v);
    }
    /** The main yellow body is always behind the drill head. */
    public BlockPos housingPosition(){
        return worldPosition.relative(drillingDirection().getOpposite());
    }
    /**
     * Fix housings placed above horizontal drills by the first horizontal
     * release. Move the original casing only when the correct position is
     * free, without breaking a player's existing block.
     */
    private void migrateLegacyHorizontalHousing(ServerLevel world){
        Direction travel=drillingDirection();
        if(travel==Direction.DOWN)return;
        BlockPos oldBody=worldPosition.above(),newBody=housingPosition();
        if(!world.hasChunkAt(oldBody)||!world.hasChunkAt(newBody))return;
        BlockState old=world.getBlockState(oldBody);
        if(!old.is(MiningRig.DRILL_UPPER)||old.getValue(DrillUpperBlock.FACING)!=travel
                ||!world.getBlockState(newBody).canBeReplaced())return;
        if(world.setBlock(newBody,MiningRig.DRILL_UPPER.defaultBlockState()
                .setValue(DrillUpperBlock.FACING,travel),Block.UPDATE_ALL)){
            world.setBlock(oldBody,Blocks.AIR.defaultBlockState(),Block.UPDATE_ALL);
        }
    }
    public boolean inSlice(BlockPos block,ComputerEntity computer,boolean expanded){
        Direction forward=drillingDirection();
        BlockPos face=worldPosition.relative(forward);
        int dx=block.getX()-face.getX(),dy=block.getY()-face.getY(),
            dz=block.getZ()-face.getZ();
        if(forward==Direction.DOWN)return dy==0
            && DrillSealing.contains(computer,dx,dz,expanded);
        int u=dx*forward.getClockWise().getStepX()
            +dz*forward.getClockWise().getStepZ();
        int plane=dx*forward.getStepX()+dz*forward.getStepZ();
        return plane==0&&DrillSealing.contains(computer,u,dy,expanded);
    }
    public String identity(){
        if(hardwareIdentity.isBlank()){hardwareIdentity=java.util.UUID.randomUUID().toString();setChanged();}
        return hardwareIdentity;
    }
    public boolean waitingForHardware(){return hardware.awaitingReplacement()&&!hardware.waitExpired();}
    public int emptyBitSlots(){
        int n=0;for(int i=0;i<BIT_SLOTS;i++)if(items.get(i).isEmpty())n++;
        return n;
    }
    @Nullable public BlockPos lastConnectedCoordinator(){return lastConnectedCoordinator;}
    @Nullable public BlockPos lastConnectedPosition(){return lastConnectedPosition;}
    /**
     * Saved pairing is authoritative for choosing the CC component. Never
     * force-load the computer solely to resolve a wireless connection.
     */
    @Nullable public BlockPos pairedComputerPosition(ServerLevel world){
        if(linkedToken.isBlank()||computerPos==null
                ||!world.dimension().identifier().toString().equals(computerDimension))
            return null;
        return computerPos;
    }
    public void adoptController(ServerLevel world,ComputerEntity computer,String token){
        linkedToken=token;
        computerPos=computer.getBlockPos().immutable();
        computerDimension=world.dimension().identifier().toString();
        linkConfirmed=true;
        setChanged();
    }
    public void detachController(){
        linkedToken="";computerPos=null;computerDimension="";linkConfirmed=false;
        setChanged();
    }
    /** Reset the excavation without removing the machine or its three bits. */
    public void resetTask(ServerLevel world){
        pauseSupply(world);
        sealing.resetForPlacement(world,worldPosition);
        initialized=false;layerBitsRequested=false;startY=worldPosition.getY();
        completedLayers=0;cursor=0;workTicks=0;
        placementCharge=0;wallWorkCharge=0;
        placementBit=-1;wallWorkTarget=null;
        for(int i=0;i<BIT_SLOTS;i++){assigned[i]=-1;progress[i]=0;}
        setChanged();
    }
    public boolean fluidWarning(){return sealing.warning();}
    /** Only recovery mode requests the 192-item CC filler depot. */
    private long lastControllerTick=Long.MIN_VALUE;
    private int collectionRadius=6;
    private int lastWallMode;
    public boolean fluidFilling(){return sealing.fluidFilling();}
    private boolean recentlyProcessing(ServerLevel world){
        long now=world.getGameTime();
        return lastControllerTick>=0 && now>=lastControllerTick
            && now-lastControllerTick<=2;
    }
    /** CC staging begins during wall excavation, before the drill places
     * its first replacement block. All three phases keep their own stock. */
    public boolean activelyWalling(ServerLevel world){
        return recentlyProcessing(world) && lastWallMode==2;
    }
    public int wallDepotTarget(){return sealing.wallDepotTarget();}
    public boolean randomWalls(){return randomWalls;}
    public boolean wasLastWallSource(BlockPos origin,int slot){
        return origin.equals(lastWallSource)&&slot==lastWallSourceSlot;
    }
    public void recordWallSource(BlockPos origin,int slot){
        lastWallSource=origin.immutable();
        lastWallSourceSlot=slot;
        setChanged();
    }
    private void chooseWallMode(boolean random){
        if(randomWalls==random)return;
        randomWalls=random;
        lastWallSource=null;lastWallSourceSlot=-1;
        if(level instanceof ServerLevel server){
            sealing.clearWallSupply(server,this);
            CoordinatorEntity cc=RigNetwork.nearestDrillCoordinator(server,this);
            if(cc!=null)cc.returnWallCache(server,this);
        }
        setChanged();
    }
    public int supplyStored(boolean wall){return sealing.supplyStored(wall);}
    public ItemStack supplyItem(boolean wall,int slot){return sealing.supplyItem(wall,slot);}
    public int supplyRoom(boolean wall,int slot){return sealing.supplyRoom(wall,slot);}
    public ItemStack removeSupply(boolean wall,int slot,int count){
        ItemStack result=sealing.removeSupply(wall,slot,count);
        if(!result.isEmpty())setChanged();
        return result;
    }
    public void setSupply(boolean wall,int slot,ItemStack stack){
        sealing.setSupply(wall,slot,stack);
        setChanged();
    }
    /** Begin stockpiling 64 filler and 64 wall blocks at the instant Start
     * is pressed, before the first excavation or perimeter phase. */
    public void primeSupply(ServerLevel world,ComputerEntity computer){
        lastControllerTick=world.getGameTime();
        lastWallMode=computer.wallMode();
        collectionRadius=Math.max(4,Math.min(14,computer.radius()+1));
        sealing.maintainSupply(world,this,computer);
        setChanged();
    }
    /** Prevent any further CC prefetch on a stop and return all unplaced stock. */
    public void pauseSupply(ServerLevel world){
        lastControllerTick=Long.MIN_VALUE;
        hardware.pause(world,this);
        sealing.pauseSupply(world,this);
        CoordinatorEntity cc=RigNetwork.nearestDrillCoordinator(world,this);
        if(cc!=null)cc.returnCachedSupplies(world,this);
        setChanged();
    }
    /** The Filler cache also feeds Air-mode wall patches, not only water. */
    public boolean activelyFilling(ServerLevel world){
        return recentlyProcessing(world);
    }
    public boolean activelyMining(ServerLevel world){return recentlyProcessing(world);}
    public void retryFailedFill(ServerLevel world){sealing.retryFailedFill(world,this);}
    public boolean canAutoRecoverFill(ServerLevel world){return sealing.canAutoRecover(world);}
    @Nullable public BlockPos failedFillPosition(){return sealing.stuckPosition();}
    public int failedFillAttempts(){return sealing.failedAttempts();}
    private long workTicks;
    private String linkedToken="";
    private BlockPos computerPos;
    private String computerDimension="";
    private boolean linkConfirmed;
    private final ContainerData menuData=new ContainerData() {
        @Override public int get(int index) {
            return switch(index){
                case 0 -> stored();
                case 1 -> page;
                case 2 -> supplyStored(false);
                case 3 -> supplyStored(true);
                case 4 -> storageBank;
                case 5 -> randomWalls?1:0;
                default -> 0;
            };
        }
        @Override public void set(int index,int value) {
            if(index==1)page=Math.max(0,Math.min(7,value));
            else if(index==4){storageBank=Math.max(0,Math.min(2,value));page=0;}
            else if(index==5)chooseWallMode(value!=0);
        }
        @Override public int getCount() {return 6;}
    };

    public DrillEntity(BlockPos pos,BlockState state){super(MiningRig.DRILL_ENTITY,pos,state);}
    public boolean relocating(){return relocating;}
    /** A saved/previous drill can only be controlled by its own pairing token. */
    public boolean matchesLink(String token){
        return token!=null&&!token.isBlank()&&token.equals(linkedToken);
    }
    /**
     * Player placement starts a NEW excavation. Do not reuse the saved
     * excavation cursor/layers from another machine that occupied this spot,
     * or an accidentally NBT-carrying drill item. Programmed depth lives on
     * the computer; physical drill progress is reset on placement only.
     * Moving downward intentionally does NOT call this method.
     */
    public void resetForPlacement(ServerLevel world){
        sealing.resetForPlacement(world,worldPosition);
        initialized=false;layerBitsRequested=false;startY=worldPosition.getY();
        completedLayers=0;cursor=0;workTicks=0;
        placementCharge=0;placementBit=-1;
        page=0;storageBank=0;
        lastWallSource=null;lastWallSourceSlot=-1;
        wallWorkCharge=0;wallWorkTarget=null;
        lastCuttingAudioTick=Long.MIN_VALUE;
        for(int i=0;i<BIT_SLOTS;i++){assigned[i]=-1;progress[i]=0;}
        relocating=false;contentsDropped=false;
        // Binding comes exclusively from the newly placed item's pairing
        // component. Never retain a prior block entity's old supervisor.
        linkedToken="";computerPos=null;computerDimension="";
        linkConfirmed=false;
        setChanged();
    }
    public void applyBinding(DrillBinding.Link link){
        linkedToken=link.token();computerPos=link.computer();
        computerDimension=link.dimension();linkConfirmed=false;setChanged();
    }
    public static void tick(Level level,BlockPos pos,BlockState state,DrillEntity drill){
        if(!(level instanceof ServerLevel server))return;
        RigTopology.drill(server,pos);
        if(server.getGameTime()%20==0)drill.migrateLegacyHorizontalHousing(server);
        if(server.getGameTime()%10==0){
            CoordinatorEntity nearest=RigNetwork.nearestDrillCoordinator(server,drill);
            if(nearest!=null){
                drill.lastConnectedCoordinator=nearest.getBlockPos().immutable();
                drill.lastConnectedPosition=pos.immutable();
            }
        }
        drill.hardware.tick(server,drill);
        if(server.getGameTime()%5==Math.floorMod(pos.hashCode(),5))
            drill.collectGroundDrops(server);
        if(drill.computerPos==null || drill.linkConfirmed
                || server.getGameTime()%20!=0)return;
        if(!server.dimension().identifier().toString().equals(drill.computerDimension)
                || !server.hasChunkAt(drill.computerPos))return;
        if(server.getBlockEntity(drill.computerPos) instanceof ComputerEntity computer
                && computer.placed(drill.linkedToken,pos)){
            drill.linkConfirmed=true;drill.setChanged();
        }
    }
    public boolean canConnectAbove(ServerLevel world){return RigNetwork.nearestDrillCoordinator(world,this)!=null;}
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
    /** Upgrade slot is outside the 64-item mined-output inventory. */
    public boolean hopperInstalled(){return items.get(UPGRADE_SLOT).is(Items.HOPPER);}
    private void collectGroundDrops(ServerLevel world){
        if(!hopperInstalled()||freeStorage()<=0)return;
        double radius=collectionRadius;
        AABB area=new AABB(worldPosition).inflate(radius,3.5,radius).move(0,-1.5,0);
        int inspected=0;
        for(ItemEntity entity:world.getEntitiesOfClass(ItemEntity.class,area)){
            if(++inspected>48 || freeStorage()<=0)break;
            if(!entity.isAlive())continue;
            ItemStack onGround=entity.getItem();
            if(onGround.isEmpty())continue;
            int room=Math.min(onGround.getCount(),freeStorage());
            if(room<=0)break;
            ItemStack incoming=onGround.copyWithCount(room);
            while(room>0&&!canReceive(List.of(incoming))){
                room--;
                if(room>0)incoming=onGround.copyWithCount(room);
            }
            if(room<=0||!receive(List.of(incoming)))continue;
            if(room>=onGround.getCount())entity.discard();
            else {
                ItemStack remainder=onGround.copy();
                remainder.shrink(room);
                entity.setItem(remainder);
            }
        }
    }
    private List<ItemStack> outputItems(){return items.subList(0,UPGRADE_SLOT);}
    public int stored(){return RigStorage.count(outputItems(),BIT_SLOTS);}
    public int freeStorage(){return CAPACITY-stored();}
    public boolean canReceive(List<ItemStack> drops){return RigStorage.canInsert(outputItems(),BIT_SLOTS,CAPACITY,drops);}
    public boolean receive(List<ItemStack> drops) {
        if(!canReceive(drops))return false;
        boolean ok=RigStorage.insert(outputItems(),BIT_SLOTS,CAPACITY,drops);
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
        for(int i=BIT_SLOTS;i<UPGRADE_SLOT;i++) {
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
        for(int i=BIT_SLOTS;i<UPGRADE_SLOT;i++)if(!items.get(i).isEmpty())return items.get(i);
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
    private int bestBit(ServerLevel world,BlockState state){
        int best=-1;float score=0;
        for(int i=0;i<BIT_SLOTS;i++)if(items.get(i).getItem() instanceof DrillBit bit
                && bit.canMine(state)){
            float value=bit.miningSpeed()+enchantmentLevel(world,items.get(i),Enchantments.EFFICIENCY)*2;
            if(value>score){best=i;score=value;}
        }
        return best;
    }
    /**
     * Placement is bit-speed dependent but not identical for every material.
     * Ordinary walls can go in promptly; filler must leave time for liquids
     * to update, and sand/gravel must not overtake their falling physics.
     */
    public boolean placementReady(ServerLevel world){
        return placementReady(world,false,false);
    }
    public boolean placementReady(ServerLevel world,boolean gravity,boolean filler){
        int slot=bestBit(world,Blocks.STONE.defaultBlockState());
        if(slot<0)return false;
        placementBit=slot;
        if(placementCharge>=1)return true;
        ItemStack tool=items.get(slot);
        float multiplier=gravity?0.85f:(filler?1.55f:2.5f);
        placementCharge+=Math.min(1.0f,
            speed(world,tool,Blocks.STONE.defaultBlockState())*multiplier);
        return placementCharge>=1;
    }
    private void playCuttingAudio(ServerLevel world){
        long now=world.getGameTime();
        // Only one audio bed for all three concurrent bits; moving block
        // entities carry the timer down to the next excavation level.
        if(lastCuttingAudioTick!=Long.MIN_VALUE && now>=lastCuttingAudioTick
                && now-lastCuttingAudioTick<24)return;
        lastCuttingAudioTick=now;
        RigMachineSounds.cutting(world,worldPosition);
    }
    private void damageBit(ServerLevel world,int slot,float multiplier){
        if(slot<0||slot>=BIT_SLOTS)return;
        ItemStack bit=items.get(slot);
        if(!(bit.getItem() instanceof DrillBit))return;
        int rolls=1+(world.getRandom().nextFloat()<(multiplier-1.0f)?1:0);
        int unbreaking=enchantmentLevel(world,bit,Enchantments.UNBREAKING);
        for(int j=0;j<rolls;j++){
            if(world.getRandom().nextInt(unbreaking+1)!=0)continue;
            int damage=bit.getDamageValue()+1;
            if(damage>=bit.getMaxDamage()){items.set(slot,ItemStack.EMPTY);hardware.requestReplacement();break;}
            bit.setDamageValue(damage);
        }
        setChanged();
    }
    /** A tool only wears when its delivered placement successfully occurs. */
    public void placementWear(ServerLevel world){
        damageBit(world,placementBit,1.3f);
        placementCharge=0;placementBit=-1;
        setChanged();
    }
    /**
     * Replacement walls and filler obstructions share bit wear, normal block
     * hardness and drop ownership. Waterlogged slabs must be mined and leave
     * the water in place; original wall clearing still skips fluid spaces.
     */
    public ComputerEntity.Status mineWall(ServerLevel world,BlockPos target){
        return mineBoundaryBlock(world,target,false);
    }
    public ComputerEntity.Status mineFillObstruction(ServerLevel world,BlockPos target){
        if(!DrillSealing.partialObstruction(world,target))return null;
        return mineBoundaryBlock(world,target,true);
    }
    private ComputerEntity.Status mineBoundaryBlock(ServerLevel world,BlockPos target,
            boolean partial){
        if(!world.hasChunkAt(target))return ComputerEntity.Status.BLOCKED;
        BlockState state=world.getBlockState(target);
        if(state.isAir()||(!partial&&!state.getFluidState().isEmpty()))return null;
        if(DrillSealing.immune(state)||state.getDestroySpeed(world,target)<0
                ||state.hasBlockEntity())return ComputerEntity.Status.BLOCKED;
        int slot=bestBit(world,state);
        if(slot<0)return ComputerEntity.Status.BLOCKED;
        if(!target.equals(wallWorkTarget)){
            wallWorkTarget=target.immutable();wallWorkCharge=0;
        }
        DrillBit bit=(DrillBit)items.get(slot).getItem();
        float power=bit.miningSpeed();
        int efficiency=enchantmentLevel(world,items.get(slot),Enchantments.EFFICIENCY);
        if(efficiency>0)power+=efficiency*efficiency+1;
        float hardness=state.getDestroySpeed(world,target);
        playCuttingAudio(world);
        wallWorkCharge+=hardness<=0?1:Math.min(1,power/Math.max(.00001f,hardness*30.0f));
        if(wallWorkCharge<1)return ComputerEntity.Status.RUNNING;
        List<ItemStack> drops=Block.getDrops(state,world,target,null,null,items.get(slot));
        if(!canReceive(drops))return ComputerEntity.Status.FULL;
        // A waterlogged partial may contain a water source: clearing the
        // obstacle must preserve that fluid for the upcoming sealing pass.
        BlockState cleared=partial?state.getFluidState().createLegacyBlock()
            :Blocks.AIR.defaultBlockState();
        if(!world.setBlock(target,cleared,Block.UPDATE_ALL))
            return ComputerEntity.Status.BLOCKED;
        if(!receive(drops))return ComputerEntity.Status.FULL;
        damageBit(world,slot,1);
        wallWorkTarget=null;wallWorkCharge=0;
        setChanged();
        return null;
    }
    /** Called once each server tick, not every 8 ticks; speed follows vanilla break progress. */
    public ComputerEntity.Status mineTick(ServerLevel world,ComputerEntity computer) {
        lastControllerTick=world.getGameTime();
        lastWallMode=computer.wallMode();
        // Never advance a head whose housing is missing or is still at the
        // old v0.6.0 vertical location. The ticker migrates old structures
        // only when the correct rear cell can safely accept the body.
        BlockPos body=housingPosition();
        if(!world.hasChunkAt(body) || !world.getBlockState(body).is(MiningRig.DRILL_UPPER)
                || world.getBlockState(body).getValue(DrillUpperBlock.FACING)
                    !=drillingDirection())return ComputerEntity.Status.BLOCKED;
        if(completedLayers>=computer.depth() ||
                (drillingDirection()==Direction.DOWN&&worldPosition.getY()-1<world.getMinY()))
            return ComputerEntity.Status.DONE;
        if(!layerBitsRequested){
            // Exactly one missing-slot scan at the start of each layer. Supply
            // flights run independently from excavation and fluid/wall phases.
            hardware.requestLayerBits(emptyBitSlots());
            layerBitsRequested=true;
            setChanged();
        }
        workTicks++;
        // If the CC spine or its required inventories disappear while a rig is
        // running, do not mine indefinitely into an isolated 64-item buffer.
        if(world.getGameTime()%20==0){
            if(RigNetwork.members(world,this).isEmpty())return ComputerEntity.Status.NO_COLLECTOR;
            if(!RigNetwork.hasOutput(world,this))return ComputerEntity.Status.NO_OUTPUT;
            if(!RigNetwork.hasInput(world,this,false))return ComputerEntity.Status.NO_FILLER_CONTAINER;
            if(computer.wallMode()==2&&!sealing.fallback()
                    && !RigNetwork.hasInput(world,this,true))
                return ComputerEntity.Status.NO_WALL_CONTAINER;
        }
        // Refill supply even if the output buffer is full or all three bits
        // are on their way. Once the blockage clears, mining resumes primed.
        sealing.maintainSupply(world,this,computer);
        if(!hasWorkingBits())return hardware.awaitingReplacement()&&!hardware.waitExpired()
            ?ComputerEntity.Status.RUNNING:ComputerEntity.Status.OUT_OF_BITS;
        if(freeStorage()==0)return ComputerEntity.Status.FULL;
        // Operational order is explicitly WALL -> FLUID -> MINING.
        // The controller owns the setting; the sealing state owns the
        // per-coordinate count. Update live for every tick and after reload.
        sealing.setTryLimit(computer.fillAttemptLimit());
        ComputerEntity.Status walls=sealing.wallStep(world,this,computer);
        if(walls!=null)return walls==ComputerEntity.Status.RUNNING
            ?ComputerEntity.Status.WALLING:walls;
        ComputerEntity.Status fluid=sealing.fluidStep(world,this,computer);
        if(fluid!=null)return fluid==ComputerEntity.Status.RUNNING
            ?ComputerEntity.Status.FILLING:fluid;
        if(!initialized){startY=worldPosition.getY();initialized=true;}
        // Replace mode excavates its one-block perimeter in the Wall step.
        // Core mining ALWAYS scans the selected footprint and all requested
        // depth layers, regardless of wall fallback.
        int r=computer.radius(),edge=2*r+1,total=edge*edge;
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
                    if(!computer.contains(dx,dz))continue;
                    BlockPos p=slicePosition(dx,dz);
                    if(p.getY()<world.getMinY()||p.getY()>=world.getMaxY())
                        return ComputerEntity.Status.BLOCKED;
                    if(!world.hasChunkAt(p)){cursor--;return ComputerEntity.Status.BLOCKED;}
                    if(world.getBlockState(p).isAir()||DrillSealing.immune(world.getBlockState(p)))continue;
                    assigned[lane]=index;break;
                }
            }
            if(assigned[lane]<0)continue;
            int index=assigned[lane],dx=index%edge-r,dz=index/edge-r;
            BlockPos p=slicePosition(dx,dz);
            if(!world.hasChunkAt(p))return ComputerEntity.Status.BLOCKED;
            BlockState state=world.getBlockState(p);
            if(state.isAir()||DrillSealing.immune(state)){
                assigned[lane]=-1;progress[lane]=0;continue;
            }
            if(state.getDestroySpeed(world,p)<0 || state.hasBlockEntity() || !drillBit.canMine(state))
                return ComputerEntity.Status.BLOCKED;
            playCuttingAudio(world);
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
                if(wear>=bit.getMaxDamage()){
                    items.set(lane,ItemStack.EMPTY);
                    hardware.requestReplacement();
                }
                else bit.setDamageValue(wear);
            }
            setChanged();
        }
        if(active==0)return hardware.awaitingReplacement()&&!hardware.waitExpired()
            ?ComputerEntity.Status.RUNNING:ComputerEntity.Status.OUT_OF_BITS;
        if(cursor>=total){
            for(int lane=0;lane<BIT_SLOTS;lane++)if(assigned[lane]>=0)return ComputerEntity.Status.RUNNING;
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
        Direction travel=drillingDirection();
        BlockPos next=previous.relative(travel);
        if(next.getY()<world.getMinY())return ComputerEntity.Status.DONE;
        if(next.above().getY()>=world.getMaxY())return ComputerEntity.Status.BLOCKED;
        // The drill bit is always the leading block, while the yellow
        // housing is directly behind it on the reverse travel axis. After
        // advancing, the old bit cell becomes the new housing cell, whether
        // this machine is drilling down or sideways.
        if(!world.hasChunkAt(next))return ComputerEntity.Status.BLOCKED;
        BlockState obstruction=world.getBlockState(next);
        if(!obstruction.isAir()&&obstruction.getFluidState().isEmpty()){
            if(DrillSealing.immune(obstruction))return ComputerEntity.Status.BLOCKED;
            ComputerEntity.Status cleanup=mineWall(world,next);
            if(cleanup!=null)return cleanup;
            if(!world.getBlockState(next).isAir())
                return ComputerEntity.Status.RUNNING;
        }
        // Mark BEFORE any neighbor changes. Once the destination is populated,
        // clear every source stack before replacing the old block, so neither
        // Minecraft's replacement callbacks nor the drill's own drop path can
        // duplicate tools or mined items during the downward move.
        relocating=true;
        if(!world.setBlock(next,MiningRig.DRILL.defaultBlockState()
                .setValue(DrillBlock.FACING,travel),Block.UPDATE_CLIENTS)){
            relocating=false;return ComputerEntity.Status.BLOCKED;
        }
        if(!(world.getBlockEntity(next) instanceof DrillEntity moved)) {
            relocating=false;
            world.removeBlock(next,false);return ComputerEntity.Status.BLOCKED;
        }
        // The new housing is the old bit cell. It must not be changed
        // until inventory and sealing state are completely transferred.
        for(int i=0;i<SLOT_COUNT;i++)moved.items.set(i,items.get(i).copy());
        moved.initialized=true;moved.startY=startY;
        moved.collectionRadius=collectionRadius;
        moved.layerBitsRequested=false; // the descending drill starts a new layer
        moved.hardwareIdentity=identity();
        moved.lastConnectedCoordinator=lastConnectedCoordinator;
        moved.lastConnectedPosition=lastConnectedPosition;
        hardware.moveTo(moved.hardware,next);
        RigTopology.forgetDrill(world,previous);
        moved.completedLayers=nextLayer;moved.cursor=0;
        moved.workTicks=workTicks;
        moved.linkedToken=linkedToken;moved.computerPos=computerPos;
        moved.computerDimension=computerDimension;
        moved.linkConfirmed=linkConfirmed;
        moved.lastControllerTick=lastControllerTick;
        moved.lastWallMode=lastWallMode;
        moved.lastCuttingAudioTick=lastCuttingAudioTick;
        moved.placementCharge=placementCharge;
        moved.placementBit=placementBit;
        moved.randomWalls=randomWalls;
        moved.lastWallSource=lastWallSource;
        moved.lastWallSourceSlot=lastWallSourceSlot;
        moved.storageBank=storageBank;
        moved.page=page;
        sealing.copyTo(moved.sealing);
        moved.sealing.nextLayer();
        moved.setChanged();
        // Ownership has transferred: source must be genuinely EMPTY before
        // callbacks run for the block at the previous position.
        for(int i=0;i<SLOT_COUNT;i++)items.set(i,ItemStack.EMPTY);
        contentsDropped=true;
        // Remove the old body, then change the previous bit cell into the
        // new body's location. Same operation for DOWN and all four
        // horizontal directions, and no transient duplicate inventory.
        BlockPos oldBody=previous.relative(travel.getOpposite());
        BlockState oldBodyState=world.getBlockState(oldBody);
        if(oldBodyState.is(MiningRig.DRILL_UPPER)
                &&oldBodyState.getValue(DrillUpperBlock.FACING)==travel)
            world.setBlock(oldBody,Blocks.AIR.defaultBlockState(),Block.UPDATE_ALL);
        world.setBlock(previous,MiningRig.DRILL_UPPER.defaultBlockState()
                .setValue(DrillUpperBlock.FACING,travel),Block.UPDATE_ALL);
        computer.moved(linkedToken,previous,next);
        return ComputerEntity.Status.RUNNING;
    }
    /** Drops only on actual destruction, not on the machine moving to the next Y. */
    public void dropInventory(){
        if(contentsDropped || relocating || level==null || level.isClientSide())return;
        contentsDropped=true;
        sealing.dropCargo((ServerLevel)level,worldPosition);
        hardware.drop((ServerLevel)level,this);
        RigTopology.forgetDrill((ServerLevel)level,worldPosition);
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
        if(slot<0||slot>=SLOT_COUNT)return false;
        if(slot==UPGRADE_SLOT)return stack.is(Items.HOPPER);
        return slot<BIT_SLOTS?stack.getItem() instanceof DrillBit:
            !(stack.getItem() instanceof DrillBit) && freeStorage()>0;
    }
    @Override public int getMaxStackSize(ItemStack stack) {
        return Math.min(stack.getMaxStackSize(),Math.max(1,freeStorage()));
    }
    @Override public void setItem(int slot,ItemStack stack) {
        if(slot==UPGRADE_SLOT){
            if(!stack.isEmpty()&&!stack.is(Items.HOPPER))return;
            items.set(slot,stack.isEmpty()?ItemStack.EMPTY:stack.copyWithCount(1));
        }else if(slot<BIT_SLOTS){
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
        return new DrillMenu(id,playerInventory,new PagedDrillContainer(this,menuData),menuData);
    }
    @Override protected void saveAdditional(ValueOutput out){
        super.saveAdditional(out);
        ContainerHelper.saveAllItems(out,items);
        out.putInt("StartY",startY);out.putInt("Layers",completedLayers);
        out.putInt("Cursor",cursor);out.putBoolean("Initialized",initialized);
        out.putBoolean("LayerBitsRequested",layerBitsRequested);
        out.putLong("WorkTicks",workTicks);
        out.putInt("CollectRadius",collectionRadius);
        out.putString("DrillIdentity",identity());
        if(lastConnectedCoordinator!=null)out.putLong("LastCC",lastConnectedCoordinator.asLong());
        if(lastConnectedPosition!=null)out.putLong("LastCCDrill",lastConnectedPosition.asLong());
        hardware.save(out);
        out.putBoolean("RandomWalls",randomWalls);
        if(lastWallSource!=null){
            out.putLong("LastWallSource",lastWallSource.asLong());
            out.putInt("LastWallSourceSlot",lastWallSourceSlot);
        }
        out.putFloat("PlacementCharge",placementCharge);
        out.putInt("PlacementBit",placementBit);
        out.putFloat("WallWorkCharge",wallWorkCharge);
        if(wallWorkTarget!=null)out.putLong("WallWorkTarget",wallWorkTarget.asLong());
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
        layerBitsRequested=in.getBooleanOr("LayerBitsRequested",false);
        workTicks=in.getLongOr("WorkTicks",0);
        collectionRadius=Math.max(4,Math.min(14,in.getIntOr("CollectRadius",6)));
        hardwareIdentity=in.getStringOr("DrillIdentity","");
        lastConnectedCoordinator=in.getLong("LastCC").isPresent()
            ?BlockPos.of(in.getLongOr("LastCC",0)):null;
        lastConnectedPosition=in.getLong("LastCCDrill").isPresent()
            ?BlockPos.of(in.getLongOr("LastCCDrill",0)):null;
        hardware.load(in);
        randomWalls=in.getBooleanOr("RandomWalls",false);
        lastWallSource=in.getLong("LastWallSource").isPresent()
            ?BlockPos.of(in.getLongOr("LastWallSource",0)):null;
        lastWallSourceSlot=in.getIntOr("LastWallSourceSlot",-1);
        placementCharge=in.getFloatOr("PlacementCharge",0);
        placementBit=in.getIntOr("PlacementBit",-1);
        wallWorkCharge=in.getFloatOr("WallWorkCharge",0);
        wallWorkTarget=in.getLong("WallWorkTarget").isPresent()
            ?BlockPos.of(in.getLongOr("WallWorkTarget",0)):null;
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
