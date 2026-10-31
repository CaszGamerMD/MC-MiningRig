package dev.casz.miningrig;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

/**
 * Right-click successive top-face or wall-face blocks to paint the vertices
 * of a polygon. Click the first vertex again, or sneak-use air, to finish.
 * Finished outlines are attached to all loaded registered drills they enclose.
 * Never changes or breaks the clicked world blocks.
 */
public final class TargetChalkItem extends Item {
    public static final int MAX_POINTS=256;
    public TargetChalkItem(Properties properties){super(properties);}

    public static ChalkOutline.Operation operation(ItemStack chalk){
        CustomData custom=chalk.get(DataComponents.CUSTOM_DATA);
        String stored=custom==null?"REPLACE":custom.copyTag().getStringOr("ChalkMode","REPLACE");
        try{return ChalkOutline.Operation.valueOf(stored);}
        catch(IllegalArgumentException ignored){return ChalkOutline.Operation.REPLACE;}
    }
    public static void cycleOperation(ItemStack chalk,Player player){
        ChalkOutline.Operation previous=operation(chalk);
        ChalkOutline.Operation[] modes=ChalkOutline.Operation.values();
        ChalkOutline.Operation next=modes[(previous.ordinal()+1)%modes.length];
        CustomData custom=chalk.get(DataComponents.CUSTOM_DATA);
        CompoundTag data=custom==null?new CompoundTag():custom.copyTag();
        data.putString("ChalkMode",next.name());
        chalk.set(DataComponents.CUSTOM_DATA,CustomData.of(data));
        say(player,"Target Chalk mode: "+next.name()+" — "+
            switch(next){
                case REPLACE->"draw a new enclosing outline for a drill.";
                case ADD->"draw a second area touching the existing excavation to extend it.";
                case CUT->"draw an interior area to leave unmined, such as the inside of a rainbow.";
                case GROUP->"select a Mining Computer, then outline a shared hole; the drill markers are automatic.";
            });
    }

    private static void selectComputer(ItemStack chalk,ServerLevel world,BlockPos pos){
        CustomData data=chalk.get(DataComponents.CUSTOM_DATA);
        CompoundTag tag=data==null?new CompoundTag():data.copyTag();
        tag.putString("GroupDimension",dimension(world));
        tag.putLong("GroupComputer",pos.asLong());
        chalk.set(DataComponents.CUSTOM_DATA,CustomData.of(tag));
    }
    private static ComputerEntity selectedComputer(ItemStack chalk,ServerLevel world){
        CustomData data=chalk.get(DataComponents.CUSTOM_DATA);
        if(data==null)return null;
        CompoundTag tag=data.copyTag();
        if(!dimension(world).equals(tag.getStringOr("GroupDimension",""))
                ||!tag.contains("GroupComputer"))return null;
        BlockPos pos=BlockPos.of(tag.getLongOr("GroupComputer",0));
        if(!world.hasChunkAt(pos))return null;
        return world.getBlockEntity(pos) instanceof ComputerEntity cc?cc:null;
    }
    private record Drawing(String dimension,Direction face,int plane,List<BlockPos> points){}
    private static String dimension(ServerLevel world) {
        return world.dimension().identifier().toString();
    }
    private static void say(Player player,String message) {
        player.sendSystemMessage(Component.literal(message));
    }
    private static int plane(BlockPos pos,Direction face) {
        return switch(face.getAxis()) {
            case X->pos.getX();
            case Y->pos.getY();
            case Z->pos.getZ();
        };
    }
    private static Drawing read(ItemStack chalk) {
        CustomData custom=chalk.get(DataComponents.CUSTOM_DATA);
        if(custom==null)return null;
        CompoundTag tag=custom.copyTag();
        int n=tag.getIntOr("ChalkCount",0);
        if(n<=0 || n>MAX_POINTS)return null;
        String dimension=tag.getStringOr("ChalkDimension","");
        Direction face=Direction.from3DDataValue(tag.getIntOr("ChalkFace",1));
        List<BlockPos> points=new ArrayList<>();
        for(int i=0;i<n;i++)points.add(BlockPos.of(tag.getLongOr("ChalkPoint"+i,0)));
        return new Drawing(dimension,face,tag.getIntOr("ChalkPlane",0),points);
    }
    private static void save(ItemStack chalk,Drawing drawing) {
        CustomData old=chalk.get(DataComponents.CUSTOM_DATA);
        CompoundTag tag=old==null?new CompoundTag():old.copyTag();
        // Remove obsolete vertices when restarting a shorter plan.
        for(int i=0;i<MAX_POINTS;i++)tag.remove("ChalkPoint"+i);
        if(drawing==null){
            tag.remove("ChalkCount");
            tag.remove("ChalkDimension");
            tag.remove("ChalkFace");
            tag.remove("ChalkPlane");
        }else{
            tag.putInt("ChalkCount",drawing.points().size());
            tag.putString("ChalkDimension",drawing.dimension());
            tag.putInt("ChalkFace",drawing.face().get3DDataValue());
            tag.putInt("ChalkPlane",drawing.plane());
            for(int i=0;i<drawing.points().size();i++)
                tag.putLong("ChalkPoint"+i,drawing.points().get(i).asLong());
        }
        chalk.set(DataComponents.CUSTOM_DATA,CustomData.of(tag));
    }
    /** Editing the held sketch never changes an already-installed drill mask,
     * the owning computer, or any placed blocks. The draft is stored on the
     * exact Target Chalk stack and survives saves/reloads. */
    public static int pointCount(ItemStack chalk){
        Drawing drawing=read(chalk);
        return drawing==null?0:drawing.points().size();
    }
    public static boolean undoLastPoint(ServerLevel world,Player player,ItemStack chalk){
        Drawing draft=read(chalk);
        if(draft==null){
            say(player,"Target Chalk: no points to undo.");
            return false;
        }
        if(!draft.dimension().equals(dimension(world))){
            say(player,"Target Chalk: this outline belongs to another dimension.");
            return false;
        }
        List<BlockPos> remaining=new ArrayList<>(draft.points());
        BlockPos removed=remaining.remove(remaining.size()-1);
        // With zero points there is no active draft. A new click chooses a
        // fresh drawing plane, allowing the player to correct the first mark.
        save(chalk,remaining.isEmpty()?null
            :new Drawing(draft.dimension(),draft.face(),draft.plane(),remaining));
        say(player,"Target Chalk: undone "+removed.toShortString()
            +". "+remaining.size()+"/"+MAX_POINTS+" points remain.");
        return true;
    }
    public static boolean clearDraft(ServerLevel world,Player player,ItemStack chalk){
        Drawing draft=read(chalk);
        if(draft==null){
            say(player,"Target Chalk: no unfinished outline to clear.");
            return false;
        }
        if(!draft.dimension().equals(dimension(world))){
            say(player,"Target Chalk: draft is in another dimension.");
            return false;
        }
        save(chalk,null);
        say(player,"Target Chalk: erased the unfinished "+draft.points().size()+"-point outline.");
        return true;
    }
    public static boolean finishDraft(ServerLevel world,Player player,ItemStack chalk){
        Drawing draft=read(chalk);
        if(draft==null||draft.points().size()<3){
            say(player,"Target Chalk: at least three points are required to finish.");
            return false;
        }
        if(!draft.dimension().equals(dimension(world))){
            say(player,"Target Chalk: outline is in another dimension.");
            return false;
        }
        return finish(world,player,chalk,draft)==InteractionResult.SUCCESS
            && read(chalk)==null;
    }
    private static void openControls(Player player,InteractionHand hand){
        if(player instanceof ServerPlayer)
            player.openMenu(new SimpleMenuProvider(
                (id,inventory,owner)->new ChalkMenu(id,inventory,hand),
                Component.literal("TARGET CHALK")));
    }
    private static void stroke(ServerLevel world,BlockPos first,BlockPos second,Direction face) {
        double dx=second.getX()-first.getX(),dy=second.getY()-first.getY(),
            dz=second.getZ()-first.getZ();
        int steps=Math.min(100,Math.max(1,(int)Math.ceil(Math.sqrt(dx*dx+dy*dy+dz*dz)*2)));
        for(int step=0;step<=steps;step++){
            double t=step/(double)steps;
            world.sendParticles(ParticleTypes.END_ROD,
                first.getX()+.5+dx*t+face.getStepX()*.55,
                first.getY()+.5+dy*t+face.getStepY()*.55,
                first.getZ()+.5+dz*t+face.getStepZ()*.55,
                1,0,0,0,0);
        }
    }
    public static InteractionResult interact(Level level,Player player,ItemStack chalk,
            BlockPos clicked,Direction face) {
        if(!(level instanceof ServerLevel world))return InteractionResult.SUCCESS;
        Drawing draft=read(chalk);
        if(draft!=null && !draft.dimension().equals(dimension(world))) {
            save(chalk,null);draft=null;
            say(player,"Target Chalk: draft cleared after changing dimensions.");
        }
        if(operation(chalk)==ChalkOutline.Operation.GROUP && draft==null
                && !player.isShiftKeyDown()){
            BlockPos computerPos=clicked;
            if(world.getBlockState(computerPos).is(MiningRig.COMPUTER_UPPER))
                computerPos=computerPos.below();
            if(world.getBlockEntity(computerPos) instanceof ComputerEntity){
                selectComputer(chalk,world,computerPos);
                say(player,"Target Chalk GROUP: selected Mining Computer "
                    +computerPos.toShortString()+". Outline the large hole; "
                    +"the computer will calculate and mark required drill locations.");
                return InteractionResult.SUCCESS;
            }
        }
        if(player.isShiftKeyDown()){
            if(draft!=null&&draft.points().size()>=3)return finish(world,player,chalk,draft);
            if(draft!=null){save(chalk,null);say(player,"Target Chalk: incomplete outline erased.");return InteractionResult.SUCCESS;}
            if(clearAt(world,player,clicked))return InteractionResult.SUCCESS;
            say(player,"Target Chalk: no drill mask to clear. Sneak-use in AIR to switch between REPLACE, ADD and CUT modes.");
            return InteractionResult.SUCCESS;
        }
        if(operation(chalk)==ChalkOutline.Operation.GROUP
                && selectedComputer(chalk,world)==null){
            say(player,"Target Chalk GROUP: first right-click your Mining Computer.");
            return InteractionResult.SUCCESS;
        }
        if(operation(chalk)==ChalkOutline.Operation.GROUP&&face!=Direction.UP){
            say(player,"Target Chalk GROUP: mark corners on the TOP surface of the hole.");
            return InteractionResult.SUCCESS;
        }
        if(face!=Direction.UP && face.getAxis()==Direction.Axis.Y){
            say(player,"Target Chalk: draw on top of ground or on a vertical wall.");
            return InteractionResult.SUCCESS;
        }
        if(draft!=null && (draft.face()!=face || draft.plane()!=plane(clicked,face))) {
            say(player,"Target Chalk: every mark must be on the same flat "+draft.face().getName()+"-facing plane. Sneak-use to erase.");
            return InteractionResult.SUCCESS;
        }
        if(draft==null)draft=new Drawing(dimension(world),face,plane(clicked,face),new ArrayList<>());
        List<BlockPos> points=draft.points();
        if(points.size()>=3 && clicked.equals(points.get(0)))return finish(world,player,chalk,draft);
        if(!points.isEmpty()&&clicked.equals(points.get(points.size()-1))){
            say(player,"Target Chalk: already marked. Click the first mark again to close.");
            return InteractionResult.SUCCESS;
        }
        if(points.size()>=MAX_POINTS){
            say(player,"Target Chalk: maximum "+MAX_POINTS+" points. Open controls to undo or finish.");
            return InteractionResult.SUCCESS;
        }
        if(!points.isEmpty())stroke(world,points.get(points.size()-1),clicked,face);
        else stroke(world,clicked,clicked,face);
        points.add(clicked.immutable());
        save(chalk,draft);
        say(player,"Target Chalk ["+operation(chalk)+"]: "+points.size()
            +"/"+MAX_POINTS+" points. Click the first point again, sneak-use air to finish, "
            +"or right-click air for Undo / Finish / Clear controls.");
        return InteractionResult.SUCCESS;
    }
    @Override public InteractionResult useOn(UseOnContext context){
        Player player=context.getPlayer();
        if(player==null)return InteractionResult.PASS;
        return interact(context.getLevel(),player,context.getItemInHand(),
            context.getClickedPos(),context.getClickedFace());
    }
    @Override public InteractionResult use(Level level,Player player,InteractionHand hand){
        if(!(level instanceof ServerLevel world))return InteractionResult.SUCCESS;
        ItemStack chalk=player.getItemInHand(hand);
        Drawing draft=read(chalk);
        if(draft==null){
            if(player.isShiftKeyDown())cycleOperation(chalk,player);
            else openControls(player,hand);
        }else if(player.isShiftKeyDown()){
            if(draft.points().size()>=3)finish(world,player,chalk,draft);
            else{save(chalk,null);say(player,"Target Chalk: incomplete draft erased.");}
        }else{
            openControls(player,hand);
        }
        return InteractionResult.SUCCESS;
    }

    private static InteractionResult finish(ServerLevel world,Player player,ItemStack chalk,Drawing drawing){
        if(drawing.points().size()<3){say(player,"Target Chalk: at least three corners required.");return InteractionResult.SUCCESS;}
        ChalkOutline.Operation mode=operation(chalk);
        if(mode==ChalkOutline.Operation.GROUP){
            ComputerEntity computer=selectedComputer(chalk,world);
            if(computer==null){
                say(player,"Target Chalk GROUP: select a loaded Mining Computer first.");
                return InteractionResult.SUCCESS;
            }
            if(drawing.face()!=Direction.UP){
                say(player,"Target Chalk GROUP: only horizontal holes are supported.");
                return InteractionResult.SUCCESS;
            }
            GroupOutline raw=GroupOutline.apply(drawing.points(),null,
                ChalkOutline.Operation.REPLACE);
            if(raw==null){
                say(player,"Target Chalk GROUP: invalid outline or larger than 256x256.");
                return InteractionResult.SUCCESS;
            }
            GroupOutline planned=raw.plan(drawing.plane()+1);
            if(planned==null){
                say(player,"Target Chalk GROUP: this hole requires more than 16 drills. "
                    +"Split the area between multiple computers.");
                return InteractionResult.SUCCESS;
            }
            String result=computer.planGroup(world,planned);
            say(player,result);
            if(computer.group()!=planned)return InteractionResult.SUCCESS;
            save(chalk,null);
            List<BlockPos> marks=drawing.points();
            stroke(world,marks.get(marks.size()-1),marks.get(0),drawing.face());
            int n=1;
            for(BlockPos marker:planned.markers())
                say(player,"DRILL MARKER "+n+++"/"+planned.requiredDrills()+": "
                    +marker.toShortString()+" (place drill TIP here, facing DOWN)");
            return InteractionResult.SUCCESS;
        }
        // Resolve registered, loaded drills only; no forced world-chunk scans.
        int assigned=0,lastCells=0;
        for(DrillEntity drill:RigTopology.loadedDrills(world)){
            if(drill.drillingDirection()!=drawing.face().getOpposite())continue;
            BlockPos computerPos=drill.pairedComputerPosition(world);
            if(computerPos==null || !world.hasChunkAt(computerPos))continue;
            if(!(world.getBlockEntity(computerPos) instanceof ComputerEntity computer))continue;
            ChalkOutline before=computer.outline(drill);
            ChalkOutline outline=ChalkOutline.apply(drawing.points(),drill,
                drawing.face(),before,mode);
            if(outline==null)continue;
            if(computer.installOutline(world,drill,outline)){
                assigned++;lastCells=outline.count();
            }
        }
        if(assigned==0){
            say(player,"Target Chalk ["+mode+"]: no matching drill footprint changed. "
                +"REPLACE must surround a registered drill. ADD/CUT need an existing "
                +"overlapping shape, must stay within 20 blocks, and cannot remove the drill itself. Draft kept.");
            return InteractionResult.SUCCESS;
        }
        List<BlockPos> points=drawing.points();
        stroke(world,points.get(points.size()-1),points.get(0),drawing.face());
        save(chalk,null);
        say(player,"Target Chalk ["+mode+"]: updated "+assigned+" drill(s); "
            +lastCells+" excavation cells in the last footprint. "
            +"Sneak-use air to switch modes; mining paused and tasks reset.");
        return InteractionResult.SUCCESS;
    }
    /** Redraw unfinished marks for players holding the chalk so the sketch
     * remains legible while walking from corner to corner. */
    public static void tickServer(net.minecraft.server.MinecraftServer minecraft){
        if(minecraft.overworld()==null || minecraft.overworld().getGameTime()%20!=0)return;
        for(net.minecraft.server.level.ServerPlayer player:minecraft.getPlayerList().getPlayers()){
            ItemStack chalk=player.getMainHandItem().is(MiningRig.TARGET_CHALK)
                ?player.getMainHandItem():player.getOffhandItem();
            if(!chalk.is(MiningRig.TARGET_CHALK))continue;
            Drawing drawing=read(chalk);
            if(drawing==null || !(player.level() instanceof ServerLevel world)
                    || !drawing.dimension().equals(dimension(world)))continue;
            // Avoid a global particle storm from remote unfinished sketches.
            List<BlockPos> points=drawing.points();
            if(points.isEmpty() || player.blockPosition().distSqr(points.get(points.size()-1))>80*80)
                continue;
            for(int i=0;i<points.size();i++){
                BlockPos p=points.get(i);
                if(player.blockPosition().distSqr(p)>80*80)continue;
                if(i==0)stroke(world,p,p,drawing.face());
                else if(player.blockPosition().distSqr(points.get(i-1))<=80*80)
                    stroke(world,points.get(i-1),p,drawing.face());
            }
        }
    }

    private static boolean clearAt(ServerLevel world,Player player,BlockPos clicked){
        BlockPos at=clicked;
        if(world.getBlockState(at).is(MiningRig.COMPUTER_UPPER))at=at.below();
        if(world.getBlockEntity(at) instanceof ComputerEntity computer){
            if(computer.group()!=null){
                say(player,computer.clearGroup(world));
                return true;
            }
            if(computer.clearOutline(world,computer.selected())){
                say(player,"Target Chalk: selected drill returned to its circle/square footprint.");
            }else say(player,"Target Chalk: selected drill has no chalk outline.");
            return true;
        }
        if(world.getBlockState(at).is(MiningRig.DRILL_UPPER))
            at=at.relative(world.getBlockState(at).getValue(DrillUpperBlock.FACING));
        if(world.getBlockEntity(at) instanceof DrillEntity drill){
            BlockPos computerPos=drill.pairedComputerPosition(world);
            if(computerPos!=null && world.hasChunkAt(computerPos)
                    && world.getBlockEntity(computerPos) instanceof ComputerEntity computer){
                if(computer.clearOutline(world,drill))
                    say(player,"Target Chalk: removed this drill's custom excavation mask.");
                else say(player,"Target Chalk: this drill has no custom outline.");
            }else say(player,"Target Chalk: drill has no loaded paired Mining Computer.");
            return true;
        }
        return false;
    }
}
