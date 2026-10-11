package dev.casz.miningrig;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.world.level.ChunkPos;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jetbrains.annotations.Nullable;

/** Independent supervisor of up to four item-linked drills. */
public final class ComputerEntity extends BlockEntity implements MenuProvider {
    public static final int MAX_DRILLS=4;
    public enum Status {
        READY("Ready"), RUNNING("Mining"), STOPPED("Manually stopped"), DONE("Job done"),
        OUT_OF_BITS("Out of bits"), NO_COLLECTOR("No collector in range"),
        BLOCKED("Unable to break block"), FULL("Drill inventory full"),
        NO_DRILL("Drill missing"), WAITING("Not placed"), UNLOADED("Chunk unloaded"),
        NO_FILLER_CONTAINER("No Filler input"),NO_WALL_CONTAINER("No Walls input"),
        NO_OUTPUT("No Output storage"),OUT_OF_FILLER("Out of Filler / Fluid in hole"),
        OUT_OF_WALLS("Out of Walls"), FLUID_IN_HOLE("Fluid in hole"),
        FILL_FAILED("Fill blocked - help needed"),
        WALLING("Replacing walls"),FILLING("Sealing fluids"),
        RECONNECTING("Reconnecting CC network"),
        REPAIR_FAILED("CC repair failed - check Hardware");
        public final String display;
        Status(String value){display=value;}
    }
    private final String[] tokens={"","","",""};
    private final String[] nicknames={"Drill 1","Drill 2","Drill 3","Drill 4"};
    private final BlockPos[] positions=new BlockPos[MAX_DRILLS];
    private final Status[] statuses={Status.READY,Status.READY,Status.READY,Status.READY};
    private final boolean[] running=new boolean[MAX_DRILLS];
    private final int[] radii={2,2,2,2};
    private final int[] depths={12,12,12,12};
    private final boolean[] circles={true,true,true,true};
    // 0 OFF, 1 AIR boundary sealing, 2 ALWAYS (extended mining and wall blocks)
    private final int[] wallModes={0,0,0,0};
    // A per-coordinate safety ceiling, configured independently for each
    // linked drill. Higher limits permit tall sand/gravel shafts in oceans.
    public static final int MIN_FILL_TRIES=1,MAX_FILL_TRIES=4096,DEFAULT_FILL_TRIES=24;
    private final int[] fillTries={24,24,24,24};
    private int selected, activeSlot;
    private long lastBroadcast;
    private String tabletId;
    private long alertSerial,mutedAlertSerial;
    private final Set<String> ignoredDrills=new HashSet<>();
    private final RigNetworkRepair repair=new RigNetworkRepair();
    public boolean activeAlert(){return alertSerial>mutedAlertSerial && hasFault();}
    private boolean hasFault(){
        for(int i=0;i<MAX_DRILLS;i++)
            if(!tokens[i].isEmpty()&&!running[i]&&isFault(statuses[i]))return true;
        return false;
    }
    private static boolean isFault(Status status){
        return status!=Status.READY&&status!=Status.WAITING
            &&status!=Status.STOPPED&&status!=Status.DONE;
    }
    public void muteAlerts(){mutedAlertSerial=alertSerial;setChanged();}
    public void startAll(){for(int i=0;i<MAX_DRILLS;i++)if(!tokens[i].isEmpty()&&!running[i])toggle(i);}
    public void stopAll(){for(int i=0;i<MAX_DRILLS;i++)if(running[i])stop(i,Status.STOPPED);}
    public void resetTask(int slot){
        if(slot<0||slot>=MAX_DRILLS||positions[slot]==null)return;
        if(!(level instanceof ServerLevel server)||!server.hasChunkAt(positions[slot]))return;
        if(server.getBlockEntity(positions[slot]) instanceof DrillEntity drill
            && drill.matchesLink(tokens[slot])){
            if(running[slot])stop(slot,Status.STOPPED);
            drill.resetTask(server);
            statuses[slot]=Status.READY;setChanged();
        }
    }
    public void applySettings(int radius,int depth,int tries){
        if(radius<1||radius>12||depth<1||depth>256
            ||tries<MIN_FILL_TRIES||tries>MAX_FILL_TRIES)return;
        boolean change=radius!=radii[selected]||depth!=depths[selected];
        radii[selected]=radius;depths[selected]=depth;
        if(fillTries[selected]!=tries)setFillAttemptLimit(tries);
        if(change)settingsChanged();
    }
    private void autoDiscover(ServerLevel server){
        for(DrillEntity drill:RigTopology.networkDrills(server,this)){
            boolean changed=false;
            String id=drill.identity();
            if(ignoredDrills.contains(id))continue;
            int slot=-1;
            for(int i=0;i<MAX_DRILLS;i++)
                if(id.equals(tokens[i])){slot=i;break;}
            if(slot<0){
                for(int i=0;i<MAX_DRILLS;i++)
                    if(tokens[i].isEmpty()){slot=i;break;}
                if(slot<0)continue;
                tokens[slot]=id;positions[slot]=drill.getBlockPos().immutable();
                nicknames[slot]="Drill "+(slot+1);
                statuses[slot]=Status.READY;changed=true;
            }else if(positions[slot]==null||!positions[slot].equals(drill.getBlockPos())){
                positions[slot]=drill.getBlockPos().immutable();changed=true;
            }
            if(!drill.matchesLink(id)){drill.adoptController(server,this,id);changed=true;}
            if(changed)setChanged();
        }
    }
    public void allowRescan(){ignoredDrills.clear();setChanged();}

    public String tabletIdentity(){
        if(tabletId==null || tabletId.isBlank()){
            tabletId=UUID.randomUUID().toString();setChanged();
        }
        return tabletId;
    }
    private void syncActiveChunks(ServerLevel server){
        Set<ChunkPos> desired=new HashSet<>();
        boolean active=false;
        for(int i=0;i<MAX_DRILLS;i++){
            if(!running[i] || positions[i]==null)continue;
            active=true;
            RigChunkTickets.cover(desired,positions[i],radii[i]+1);
            if(server.hasChunkAt(positions[i]) &&
                server.getBlockEntity(positions[i]) instanceof DrillEntity drill){
                RigNetwork.includeLoadedNetwork(server,drill,desired);
                if(drill.lastConnectedCoordinator()!=null){
                    BlockPos keep=drill.lastConnectedCoordinator();
                    desired.add(new ChunkPos(keep.getX()>>4,keep.getZ()>>4));
                }
            }
        }
        // The remote controller must also tick when nobody is nearby.
        if(active)desired.add(new ChunkPos(worldPosition.getX()>>4,worldPosition.getZ()>>4));
        RigChunkTickets.sync(server,worldPosition,desired);
    }
    private void syncIfServer(){
        if(level instanceof ServerLevel server)syncActiveChunks(server);
    }

    public ComputerEntity(BlockPos pos,BlockState state){super(MiningRig.COMPUTER_ENTITY,pos,state);}
    public boolean belongsTo(ComputerMenu menu){return menu.computer()==this;}
    public int selected(){return selected;}
    public int selectedRadius(){return radii[selected];}
    public int selectedDepth(){return depths[selected];}
    public boolean selectedCircle(){return circles[selected];}
    public int selectedWallMode(){return wallModes[selected];}
    public int wallMode(){return wallModes[activeSlot];}
    public int fillAttemptLimit(){return fillTries[activeSlot];}
    public int selectedFillAttemptLimit(){return fillTries[selected];}
    public void setFillAttemptLimit(int value){
        int newValue=Math.max(MIN_FILL_TRIES,Math.min(MAX_FILL_TRIES,value));
        if(newValue==fillTries[selected])return;
        fillTries[selected]=newValue;
        // Adjusting a safety limit is permitted while drilling, and does
        // not reset mining progress, wall stage or reserved filler cargo.
        if(level instanceof ServerLevel server && !running[selected]
                && statuses[selected]==Status.FILL_FAILED && positions[selected]!=null
                && server.hasChunkAt(positions[selected])
                && server.getBlockEntity(positions[selected]) instanceof DrillEntity drill
                && drill.matchesLink(tokens[selected])
                && newValue>drill.failedFillAttempts()){
            // Keep already-spent attempts at this same coordinate. A higher
            // limit simply gives the drill more attempts to finish the job.
            running[selected]=true;
            statuses[selected]=Status.FILLING;
        }
        setChanged();syncIfServer();
    }
    public void setWallMode(int value){
        wallModes[selected]=Math.floorMod(value,3);
        settingsChanged();
    }
    public int radius(){return radii[activeSlot];}
    public int depth(){return depths[activeSlot];}
    public boolean circle(){return circles[activeSlot];}
    public boolean contains(int x,int z){
        int r=radius();
        return circle()?x*x+z*z<=r*r:Math.max(Math.abs(x),Math.abs(z))<=r;
    }
    public void select(int slot){
        if(slot>=0&&slot<MAX_DRILLS){selected=slot;activeSlot=slot;setChanged();}
    }
    public void setRadius(int value){radii[selected]=Math.max(1,Math.min(12,value));settingsChanged();}
    public void setDepth(int value){depths[selected]=Math.max(1,Math.min(256,value));settingsChanged();}
    public void setCircle(boolean value){circles[selected]=value;settingsChanged();}
    private void settingsChanged(){
        releaseDrillSupplies(selected);
        running[selected]=false;
        if(!tokens[selected].isEmpty())statuses[selected]=Status.STOPPED;
        setChanged();syncIfServer();
    }
    /** Called on computer interaction while holding the drill block item. */
    public int registerItem(ServerLevel world,ItemStack stack){
        DrillBinding.Link previous=DrillBinding.read(stack);
        if(previous!=null && previous.dimension().equals(world.dimension().identifier().toString())
                && previous.computer().equals(worldPosition)){
            for(int i=0;i<MAX_DRILLS;i++)if(tokens[i].equals(previous.token()))return i;
        }
        for(int i=0;i<MAX_DRILLS;i++){
            if(tokens[i].isEmpty()){
                String token=UUID.randomUUID().toString();
                tokens[i]=token;
                positions[i]=null;
                statuses[i]=Status.WAITING;
                nicknames[i]="Drill "+(i+1);
                selected=i;
                DrillBinding.write(stack,world,worldPosition,token);
                setChanged();
                return i;
            }
        }
        return -1;
    }
    /** Fresh placement resets only the paired slot's running state. */
    public boolean placedFresh(String token,BlockPos place){
        if(!placed(token,place))return false;
        for(int i=0;i<MAX_DRILLS;i++)if(token.equals(tokens[i])){
            positions[i]=place.immutable();
            running[i]=false;statuses[i]=Status.READY;
            setChanged();syncIfServer();
            return true;
        }
        return false;
    }
    /** Retry registration for an already-running drill must be idempotent. */
    public boolean placed(String token,BlockPos place){
        if(token==null || token.isBlank())return false;
        for(int i=0;i<MAX_DRILLS;i++){
            if(!tokens[i].isEmpty()&&tokens[i].equals(token)){
                // A completely different drill in an old drill's coordinates
                // MUST NOT inherit that old slot's status or layer progress.
                for(int j=0;j<MAX_DRILLS;j++){
                    if(j!=i&&place.equals(positions[j])){
                        positions[j]=null;running[j]=false;
                        statuses[j]=Status.NO_DRILL;
                    }
                }
                if(place.equals(positions[i])){setChanged();return true;}
                positions[i]=place.immutable();
                statuses[i]=Status.READY;
                running[i]=false;
                setChanged();syncIfServer();return true;
            }
        }
        return false;
    }
    public boolean rename(int index,String newName){
        if(index<0||index>=MAX_DRILLS||tokens[index].isEmpty())return false;
        String name=newName.strip().replace('\n',' ').replace('\r',' ').replace('\t',' ');
        if(name.isBlank() || name.length()>24)return false;
        nicknames[index]=name;setChanged();return true;
    }
    public void unlink(int index){
        if(index<0||index>=MAX_DRILLS)return;
        releaseDrillSupplies(index);
        if(level instanceof ServerLevel server && positions[index]!=null
                && server.hasChunkAt(positions[index])
                && server.getBlockEntity(positions[index]) instanceof DrillEntity current)
            repair.cancel(server,index,current);
        if(level instanceof ServerLevel server && positions[index]!=null &&
                server.hasChunkAt(positions[index])
                && server.getBlockEntity(positions[index]) instanceof DrillEntity drill
                && drill.matchesLink(tokens[index]))drill.detachController();
        if(!tokens[index].isEmpty())ignoredDrills.add(tokens[index]);
        tokens[index]="";positions[index]=null;running[index]=false;
        statuses[index]=Status.READY;nicknames[index]="Drill "+(index+1);setChanged();syncIfServer();
    }
    public void toggle(){toggle(selected);}
    public void toggle(int slot){
        if(slot<0||slot>=MAX_DRILLS)return;
        Status before=statuses[slot];
        if(running[slot]){stop(slot,Status.STOPPED);}
        else if(tokens[slot].isEmpty()||positions[slot]==null)
            statuses[slot]=tokens[slot].isEmpty()?Status.NO_DRILL:Status.WAITING;
        else if(level instanceof ServerLevel server){
            BlockPos at=positions[slot];
            if(!server.hasChunkAt(at))statuses[slot]=Status.UNLOADED;
            else if(!(server.getBlockEntity(at) instanceof DrillEntity drill)
                    || !drill.matchesLink(tokens[slot]))
                statuses[slot]=Status.NO_DRILL;
            else if(!RigTopology.joined(server,this,drill))
                statuses[slot]=Status.NO_COLLECTOR;
            else if(!RigNetwork.hasInput(server,drill,false))
                statuses[slot]=Status.NO_FILLER_CONTAINER;
            else if(!RigNetwork.hasOutput(server,drill))
                statuses[slot]=Status.NO_OUTPUT;
            else if(wallModes[slot]==2&&!RigNetwork.hasInput(server,drill,true))
                statuses[slot]=Status.NO_WALL_CONTAINER;
            else {
                // An explicit restart after 'Fill blocked' must reset the
                // 24-attempt latch or the exact same error repeats forever.
                if(statuses[slot]==Status.FILL_FAILED)drill.retryFailedFill(server);
                running[slot]=true;statuses[slot]=Status.RUNNING;
            }
        }else statuses[slot]=Status.NO_DRILL;
        if(isFault(statuses[slot]) && statuses[slot]!=before)alertSerial++;
        setChanged();syncIfServer();
    }
    public void stop(Status reason){stop(activeSlot,reason);}
    private void releaseDrillSupplies(int slot){
        if(slot<0||slot>=MAX_DRILLS || !(level instanceof ServerLevel server)
                || positions[slot]==null || !server.hasChunkAt(positions[slot]))return;
        if(server.getBlockEntity(positions[slot]) instanceof DrillEntity drill
                && drill.matchesLink(tokens[slot]))drill.pauseSupply(server);
    }
    public void stop(int slot,Status reason){
        if(slot<0||slot>=MAX_DRILLS)return;
        releaseDrillSupplies(slot);
        if(level instanceof ServerLevel server && positions[slot]!=null
                && server.hasChunkAt(positions[slot])
                && server.getBlockEntity(positions[slot]) instanceof DrillEntity drill)
            repair.cancel(server,slot,drill);
        boolean newFault=isFault(reason)
            && (running[slot]||statuses[slot]!=reason);
        running[slot]=false;statuses[slot]=reason;
        if(newFault)alertSerial++;
        setChanged();syncIfServer();
    }
    public void moved(String token,BlockPos oldPos,BlockPos newPos){
        if(token==null||token.isBlank())return;
        for(int i=0;i<MAX_DRILLS;i++)
            if(token.equals(tokens[i])&&oldPos.equals(positions[i])){
                positions[i]=newPos.immutable();setChanged();syncIfServer();
            }
    }
    public String screenStatus(){
        int active=0,alerts=0;
        for(int i=0;i<MAX_DRILLS;i++){
            if(running[i])active++;
            else if(!tokens[i].isEmpty() && isFault(statuses[i]))alerts++;
        }
        return active+" mining | "+alerts+" alerts";
    }
    /** Stable severity independent of the block's alternating lamp phase. */
    public int tabletLightLevel(){
        boolean alarm=false;
        for(int i=0;i<MAX_DRILLS;i++){
            if(!tokens[i].isEmpty()&&!running[i] && isFault(statuses[i]))alarm=true;
        }
        if(alarm)return 3;
        if(level instanceof ServerLevel server)
            for(int i=0;i<MAX_DRILLS;i++)
                if(running[i]&&positions[i]!=null&&server.hasChunkAt(positions[i])
                    && server.getBlockEntity(positions[i]) instanceof DrillEntity drill
                    && drill.fluidWarning())return 2;
        return 1;
    }
    public boolean running(){
        for(boolean value:running)if(value)return true;return false;
    }
    public String description(){
        return "Caszual Mining Rig | "+screenStatus()+" | Drill "+(selected+1)
            +" "+(circles[selected]?"Circle":"Square")+" r="+radii[selected]
            +" depth="+depths[selected];
    }
    public String snapshot(ServerLevel server){
        StringBuilder out=new StringBuilder().append(selected);
        for(int i=0;i<MAX_DRILLS;i++){
            BlockPos pos=positions[i];
            String location=pos==null?"Not placed":
                pos.getX()+","+pos.getY()+","+pos.getZ();
            String status=tokens[i].isEmpty()?"Empty":statuses[i].display;
            int eta=-1;
            if(!tokens[i].isEmpty()&&pos!=null){
                if(!server.hasChunkAt(pos))status=Status.UNLOADED.display;
                else if(server.getBlockEntity(pos) instanceof DrillEntity drill
                        && drill.matchesLink(tokens[i])){
                    if(running[i]){
                        if(!drill.hasWorkingBits())status=Status.OUT_OF_BITS.display;
                        else if(drill.freeStorage()==0)status=Status.FULL.display;
                        else if(!drill.canConnectAbove(server))status="Mining / No collector";
                    }
                    if(!running[i] && statuses[i]==Status.FILL_FAILED
                            && drill.failedFillPosition()!=null){
                        BlockPos failed=drill.failedFillPosition();
                        status="Fill blocked @ "+failed.getX()+","
                            +failed.getY()+","+failed.getZ();
                    }
                    eta=drill.estimatedSeconds(radii[i],depths[i],circles[i],running[i]);
                }else status=Status.NO_DRILL.display;
            }
            out.append('\n').append(tokens[i].isEmpty()?"0":"1")
                .append('\t').append(nicknames[i]).append('\t').append(location)
                .append('\t').append(status).append('\t').append(eta)
                .append('\t').append(radii[i]).append('\t').append(depths[i])
                .append('\t').append(circles[i]?"O":"S")
                .append('\t').append(wallModes[i])
                .append('\t').append(pos==null||!server.hasChunkAt(pos)?0:
                    server.getBlockEntity(pos) instanceof DrillEntity drill
                    && drill.matchesLink(tokens[i])?drill.completedLayers():0)
                .append('\t').append(fillTries[i]);
        }
        out.append('\n').append("ALERT").append('\t').append(activeAlert()?"1":"0");
        out.append(RigStorageMonitor.snapshot(server,this));
        return out.toString();
    }
    public static void tick(Level level,BlockPos pos,BlockState state,ComputerEntity computer){
        if(!(level instanceof ServerLevel server))return;
        long now=server.getGameTime();
        // Give machines placed by v0.3.2 their clickable upper target when
        // the space is clear, without overwriting a player-built block.
        if(now%20==0 && server.getBlockState(pos.above()).isAir())
            server.setBlock(pos.above(),MiningRig.COMPUTER_UPPER.defaultBlockState(),Block.UPDATE_ALL);
        if(now%20==0)computer.autoDiscover(server);
        computer.syncActiveChunks(server);
        if(now%60==0&&computer.activeAlert())RigMachineSounds.problem(server,pos);
        // A halted drill recovers automatically once the exact failed block
        // becomes permanently solid; do not require unlinking or replacing it.
        if(now%20==0)for(int i=0;i<MAX_DRILLS;i++){
            if(computer.running[i]||computer.statuses[i]!=Status.FILL_FAILED
                    ||computer.positions[i]==null
                    ||!server.hasChunkAt(computer.positions[i]))continue;
            if(server.getBlockEntity(computer.positions[i]) instanceof DrillEntity drill
                    && drill.matchesLink(computer.tokens[i])
                    && drill.canAutoRecoverFill(server)){
                drill.retryFailedFill(server);
                computer.running[i]=true;
                computer.statuses[i]=Status.FILLING;
                computer.setChanged();
            }
        }
        for(int i=0;i<MAX_DRILLS;i++){
            if(!computer.running[i])continue;
            BlockPos drillPos=computer.positions[i];
            if(drillPos==null){computer.stop(i,Status.WAITING);continue;}
            if(!server.hasChunkAt(drillPos)){
                // Do not stop or force-load a remote drill; resume when it loads.
                continue;
            }
            if(!(server.getBlockEntity(drillPos) instanceof DrillEntity drill)
                    || !drill.matchesLink(computer.tokens[i])){
                computer.stop(i,Status.NO_DRILL);continue;
            }
            if(!RigTopology.joined(server,computer,drill)){
                RigNetworkRepair.Result recovered=computer.repair.tick(server,i,drill,computer);
                if(recovered==RigNetworkRepair.Result.FAILED){
                    computer.stop(i,Status.REPAIR_FAILED);
                    continue;
                }
                if(recovered!=RigNetworkRepair.Result.CONNECTED){
                    if(computer.statuses[i]!=Status.RECONNECTING){
                        drill.pauseSupply(server);
                        computer.statuses[i]=Status.RECONNECTING;
                        computer.setChanged();
                    }
                    continue;
                }
            }
            computer.activeSlot=i;
            Status result=drill.mineTick(server,computer);
            if(result==Status.RUNNING||result==Status.WALLING||result==Status.FILLING){
                if(computer.statuses[i]!=result){
                    computer.statuses[i]=result;computer.setChanged();
                }
            }else computer.stop(i,result);
        }
        computer.syncActiveChunks(server);
        if(now%10==0){
            boolean warn=false;
            for(int i=0;i<MAX_DRILLS;i++)if(computer.running[i] && computer.positions[i]!=null
                    && server.hasChunkAt(computer.positions[i])
                    && server.getBlockEntity(computer.positions[i]) instanceof DrillEntity drill
                    && drill.fluidWarning())warn=true;
            boolean warning=warn && ((now/10)%2==0);
            if(state.getValue(ComputerBlock.WARN)!=warning){
                state=state.setValue(ComputerBlock.WARN,warning);
                server.setBlock(pos,state,Block.UPDATE_CLIENTS);
            }
            boolean alarm=false;
            for(int i=0;i<MAX_DRILLS;i++){
                if(!computer.tokens[i].isEmpty()&&!computer.running[i]
                    && computer.statuses[i]!=Status.READY
                    && computer.statuses[i]!=Status.WAITING)alarm=true;
            }
            alarm=alarm&&((now/10)%2==0);
            if(state.getValue(ComputerBlock.ALARM)!=alarm)
                server.setBlock(pos,state.setValue(ComputerBlock.ALARM,alarm),Block.UPDATE_CLIENTS);
        }
        // Draw the selected drill's perimeter, not all four simultaneously.
        if(now%20==0){
            computer.activeSlot=computer.selected;
            BlockPos selectedPos=computer.positions[computer.selected];
            if(selectedPos!=null&&server.hasChunkAt(selectedPos)){
                int rad=computer.radius();
                for(int dx=-rad;dx<=rad;dx++)for(int dz=-rad;dz<=rad;dz++){
                    boolean boundary=computer.contains(dx,dz)
                        &&(!computer.contains(dx+1,dz)||!computer.contains(dx-1,dz)
                            ||!computer.contains(dx,dz+1)||!computer.contains(dx,dz-1));
                    if(boundary)server.sendParticles(ParticleTypes.END_ROD,
                        selectedPos.getX()+dx+.5,selectedPos.getY()+.2,selectedPos.getZ()+dz+.5,1,0,0,0,0);
                }
            }
        }
        if(now%10==0){
            for(ServerPlayer player:server.players()) {
                if(player.containerMenu instanceof ComputerMenu menu && computer.belongsTo(menu))
                    ServerPlayNetworking.send(player,new ComputerStatePayload(computer.snapshot(server)));
            }
        }
    }
    @Override public void setRemoved(){
        if(level instanceof ServerLevel server)RigChunkTickets.release(server,worldPosition);
        super.setRemoved();
    }
    @Override public Component getDisplayName(){return Component.literal("Caszual Mining Rig Computer");}
    @Nullable @Override public AbstractContainerMenu createMenu(int id,Inventory inventory,Player player){
        return new ComputerMenu(id,inventory,this);
    }
    @Override protected void saveAdditional(ValueOutput out){
        super.saveAdditional(out);
        out.putInt("Selected",selected);
        if(tabletId!=null)out.putString("TabletIdentity",tabletId);
        out.putLong("AlertSerial",alertSerial);
        out.putLong("MutedSerial",mutedAlertSerial);
        out.putInt("IgnoredDrills",Math.min(256,ignoredDrills.size()));
        int skip=0;for(String id:ignoredDrills){
            if(skip>=256)break;
            out.putString("IgnoredDrill"+skip++,id);
        }
        repair.save(out);
        for(int i=0;i<MAX_DRILLS;i++){
            out.putString("Token"+i,tokens[i]);
            out.putString("Name"+i,nicknames[i]);
            out.putBoolean("Placed"+i,positions[i]!=null);
            if(positions[i]!=null)out.putLong("DrillPos"+i,positions[i].asLong());
            out.putBoolean("Running"+i,running[i]);
            out.putString("Status"+i,statuses[i].name());
            out.putInt("Radius"+i,radii[i]);out.putInt("Depth"+i,depths[i]);
            out.putBoolean("Circle"+i,circles[i]);
            out.putInt("WallMode"+i,wallModes[i]);
            out.putInt("FillTries"+i,fillTries[i]);
        }
    }
    @Override protected void loadAdditional(ValueInput in){
        super.loadAdditional(in);
        tabletId=in.getStringOr("TabletIdentity","");
        alertSerial=in.getLongOr("AlertSerial",0);
        mutedAlertSerial=in.getLongOr("MutedSerial",0);
        ignoredDrills.clear();
        for(int j=0;j<Math.min(256,in.getIntOr("IgnoredDrills",0));j++)
            ignoredDrills.add(in.getStringOr("IgnoredDrill"+j,""));
        selected=Math.max(0,Math.min(MAX_DRILLS-1,in.getIntOr("Selected",0)));
        activeSlot=selected;
        repair.load(in);
        for(int i=0;i<MAX_DRILLS;i++){
            tokens[i]=in.getStringOr("Token"+i,"");
            nicknames[i]=in.getStringOr("Name"+i,"Drill "+(i+1));
            positions[i]=in.getBooleanOr("Placed"+i,false)?BlockPos.of(in.getLongOr("DrillPos"+i,0)):null;
            running[i]=in.getBooleanOr("Running"+i,false);
            try{statuses[i]=Status.valueOf(in.getStringOr("Status"+i,"READY"));}
            catch(IllegalArgumentException e){statuses[i]=Status.READY;}
            radii[i]=Math.max(1,Math.min(12,in.getIntOr("Radius"+i,2)));
            depths[i]=Math.max(1,Math.min(256,in.getIntOr("Depth"+i,12)));
            circles[i]=in.getBooleanOr("Circle"+i,true);
            wallModes[i]=Math.max(0,Math.min(2,in.getIntOr("WallMode"+i,0)));
            fillTries[i]=Math.max(MIN_FILL_TRIES,
                Math.min(MAX_FILL_TRIES,in.getIntOr("FillTries"+i,DEFAULT_FILL_TRIES)));
        }
        // Migrate one-drill prototype saves into the four-drill supervisor.
        if(tokens[0].isEmpty() && in.getBooleanOr("Linked",false)){
            tokens[0]=UUID.randomUUID().toString();
            positions[0]=BlockPos.of(in.getLongOr("Drill",0));
            running[0]=in.getBooleanOr("Running",false);
            radii[0]=Math.max(1,Math.min(12,in.getIntOr("Radius",2)));
            depths[0]=Math.max(1,Math.min(256,in.getIntOr("Depth",12)));
            circles[0]=in.getBooleanOr("Circle",true);
        }
    }
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registry){return saveWithoutMetadata(registry);}
    @Override public Packet<ClientGamePacketListener> getUpdatePacket(){return ClientboundBlockEntityDataPacket.create(this);}
    @Override public void setChanged(){
        super.setChanged();
        if(level!=null&&!level.isClientSide())
            level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),Block.UPDATE_CLIENTS);
    }
}
