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
    private static final int MAX_POINTS=128;
    public TargetChalkItem(Properties properties){super(properties);}

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
        if(player.isShiftKeyDown()){
            if(draft!=null&&draft.points().size()>=3)return finish(world,player,chalk,draft);
            if(draft!=null){save(chalk,null);say(player,"Target Chalk: incomplete outline erased.");return InteractionResult.SUCCESS;}
            if(clearAt(world,player,clicked))return InteractionResult.SUCCESS;
            say(player,"Target Chalk: no outline to clear. Click points to draw an excavation boundary.");
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
            say(player,"Target Chalk: maximum 128 points. Sneak-use to finish.");
            return InteractionResult.SUCCESS;
        }
        if(!points.isEmpty())stroke(world,points.get(points.size()-1),clicked,face);
        else stroke(world,clicked,clicked,face);
        points.add(clicked.immutable());
        save(chalk,draft);
        say(player,"Target Chalk: "+points.size()+" points. Click the first point again or sneak-right-click air to finish.");
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
            say(player,"Target Chalk: right-click outline corners on the ground or a wall. Close by clicking the first mark.");
        }else if(player.isShiftKeyDown()){
            if(draft.points().size()>=3)finish(world,player,chalk,draft);
            else{save(chalk,null);say(player,"Target Chalk: incomplete draft erased.");}
        }else{
            say(player,"Target Chalk: "+draft.points().size()+" points. Sneak-right-click air to close.");
        }
        return InteractionResult.SUCCESS;
    }

    private static InteractionResult finish(ServerLevel world,Player player,ItemStack chalk,Drawing drawing){
        if(drawing.points().size()<3){say(player,"Target Chalk: at least three corners required.");return InteractionResult.SUCCESS;}
        // Resolve all registered, loaded drills; this never force-loads distant chunks.
        int assigned=0,lastCells=0;
        for(DrillEntity drill:RigTopology.loadedDrills(world)){
            if(drill.drillingDirection()!=drawing.face().getOpposite())continue;
            ChalkOutline outline=ChalkOutline.create(drawing.points(),drill,drawing.face());
            if(outline==null)continue;
            BlockPos computerPos=drill.pairedComputerPosition(world);
            if(computerPos==null || !world.hasChunkAt(computerPos))continue;
            if(!(world.getBlockEntity(computerPos) instanceof ComputerEntity computer))continue;
            if(computer.installOutline(world,drill,outline)){
                assigned++;lastCells=outline.count();
            }
        }
        if(assigned==0){
            say(player,"Target Chalk: no registered drill inside the outline, or shape exceeds 20 blocks from its drill. The draft is kept for editing.");
            return InteractionResult.SUCCESS;
        }
        List<BlockPos> points=drawing.points();
        stroke(world,points.get(points.size()-1),points.get(0),drawing.face());
        save(chalk,null);
        say(player,"Target Chalk: applied custom excavation to "+assigned+" drill(s); "+lastCells+" cells on the last footprint. Mining paused and tasks reset.");
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
