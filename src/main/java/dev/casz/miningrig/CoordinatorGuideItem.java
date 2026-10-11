package dev.casz.miningrig;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import org.jetbrains.annotations.Nullable;

/**
 * Opt-in placement assist. Never changes the existing CC networking rules.
 *
 * Click a CC face to lock an axis, sneak-use in air to choose 1..20 blocks,
 * use in air to place, or click a nearby surface to snap to that distance.
 * Placement uses one CC from inventory and checks both ends of the link.
 */
public final class CoordinatorGuideItem extends Item {
    private static final int MAX_DISTANCE=20;
    public CoordinatorGuideItem(Properties properties){super(properties);}

    private record Plan(String dimension,BlockPos origin,Direction direction,int distance){}
    @Nullable private static Plan read(ItemStack stack){
        CustomData custom=stack.get(DataComponents.CUSTOM_DATA);
        if(custom==null)return null;
        CompoundTag tag=custom.copyTag();
        if(!tag.getBooleanOr("GuideActive",false))return null;
        String dimension=tag.getStringOr("GuideDimension","");
        if(dimension.isBlank()||!tag.contains("GuideOrigin"))return null;
        Direction dir=Direction.from3DDataValue(tag.getIntOr("GuideDirection",2));
        int d=Math.max(1,Math.min(MAX_DISTANCE,tag.getIntOr("GuideDistance",1)));
        return new Plan(dimension,BlockPos.of(tag.getLongOr("GuideOrigin",0)),dir,d);
    }
    private static void save(ItemStack stack,Plan plan){
        CustomData old=stack.get(DataComponents.CUSTOM_DATA);
        CompoundTag tag=old==null?new CompoundTag():old.copyTag();
        tag.putBoolean("GuideActive",true);
        tag.putString("GuideDimension",plan.dimension());
        tag.putLong("GuideOrigin",plan.origin().asLong());
        tag.putInt("GuideDirection",plan.direction().get3DDataValue());
        tag.putInt("GuideDistance",plan.distance());
        stack.set(DataComponents.CUSTOM_DATA,CustomData.of(tag));
    }
    private static void say(Player player,String message){
        player.sendSystemMessage(Component.literal(message));
    }
    private static String dimension(ServerLevel world){
        return world.dimension().identifier().toString();
    }
    /** Also called from CoordinatorBlock because the block normally owns
     * right-clicks; without this interception the guide opens the CC GUI. */
    public static InteractionResult select(Level world,BlockPos ccPos,
            Direction outward,Player player,ItemStack tool){
        if(!(world instanceof ServerLevel server))return InteractionResult.SUCCESS;
        Plan previous=read(tool);
        int length=previous==null?1:previous.distance();
        save(tool,new Plan(dimension(server),ccPos.immutable(),outward,length));
        say(player,"CC guide: "+outward.getName()+" from "+ccPos.toShortString()
            +" | "+length+"/20 blocks. Sneak-use air to change distance; use air to place.");
        return InteractionResult.SUCCESS;
    }

    @Override public InteractionResult useOn(UseOnContext context){
        Level world=context.getLevel();
        BlockPos clicked=context.getClickedPos();
        if(world.getBlockState(clicked).is(MiningRig.COORDINATOR))
            return select(world,clicked,context.getClickedFace(),
                context.getPlayer(),context.getItemInHand());
        if(!(world instanceof ServerLevel server))return InteractionResult.SUCCESS;
        Player player=context.getPlayer();
        if(player==null)return InteractionResult.PASS;
        ItemStack tool=context.getItemInHand();
        Plan plan=read(tool);
        if(plan==null){
            say(player,"Right-click a Collection Coordinator face first.");
            return InteractionResult.SUCCESS;
        }
        // Use the block the player clicked only as a distance reference.
        // Snap the actual placement to the selected CC's exact X/Z/Y axis.
        BlockPos aimed=clicked.relative(context.getClickedFace());
        BlockPos origin=plan.origin();
        Direction dir=plan.direction();
        int dx=aimed.getX()-origin.getX(),dy=aimed.getY()-origin.getY(),
            dz=aimed.getZ()-origin.getZ();
        int distance=dx*dir.getStepX()+dy*dir.getStepY()+dz*dir.getStepZ();
        if(distance<1||distance>MAX_DISTANCE){
            say(player,"Target must be 1–20 blocks outward from the selected CC face.");
            return InteractionResult.SUCCESS;
        }
        int offX=dx-distance*dir.getStepX();
        int offY=dy-distance*dir.getStepY();
        int offZ=dz-distance*dir.getStepZ();
        if(Math.max(Math.abs(offX),Math.max(Math.abs(offY),Math.abs(offZ)))>4){
            say(player,"Aim within four blocks of the guide beam, then click.");
            return InteractionResult.SUCCESS;
        }
        Plan chosen=new Plan(plan.dimension(),origin,dir,distance);
        save(tool,chosen);
        place(server,player,tool,chosen);
        return InteractionResult.SUCCESS;
    }

    @Override public InteractionResult use(Level world,Player player,InteractionHand hand){
        if(!(world instanceof ServerLevel server))return InteractionResult.SUCCESS;
        ItemStack tool=player.getItemInHand(hand);
        Plan plan=read(tool);
        if(plan==null){
            say(player,"Right-click a Collection Coordinator face to start a placement guide.");
            return InteractionResult.SUCCESS;
        }
        if(player.isShiftKeyDown()){
            int next=plan.distance()%MAX_DISTANCE+1;
            save(tool,new Plan(plan.dimension(),plan.origin(),plan.direction(),next));
            say(player,"CC spacing: "+next+"/20 blocks. Release sneak and use to place.");
            return InteractionResult.SUCCESS;
        }
        place(server,player,tool,plan);
        return InteractionResult.SUCCESS;
    }

    private static int findCC(Player player){
        for(int i=0;i<player.getInventory().getContainerSize();i++)
            if(player.getInventory().getItem(i).is(MiningRig.COORDINATOR.asItem()))
                return i;
        return -1;
    }

    /** Requires a real two-way wireless link: loaded chunks, no intervening
     * CC, no blocking attached inventory at either wireless port. */
    private static String problem(ServerLevel world,Plan plan){
        if(!dimension(world).equals(plan.dimension()))return "Guide is linked in a different dimension.";
        if(!world.hasChunkAt(plan.origin()))return "Original coordinator chunk is not loaded.";
        if(!(world.getBlockEntity(plan.origin()) instanceof CoordinatorEntity start))
            return "Original coordinator is missing. Select a new CC face.";
        Direction dir=plan.direction();
        if(start.external(world,dir)!=null)
            return "This face is occupied by a storage connection, so wireless linking is disabled.";
        BlockPos end=plan.origin().relative(dir,plan.distance());
        if(end.getY()<world.getMinY()||end.getY()>=world.getMaxY())
            return "Target exceeds the build height.";
        if(!world.hasChunkAt(end))return "Target chunk is not loaded. Move closer.";
        BlockState state=world.getBlockState(end);
        if(!state.canBeReplaced()||world.getBlockEntity(end)!=null)
            return "Target location is occupied; choose another spacing.";
        for(int n=1;n<plan.distance();n++){
            BlockPos between=plan.origin().relative(dir,n);
            if(!world.hasChunkAt(between))return "An intermediate chunk is not loaded.";
            if(world.getBlockEntity(between) instanceof CoordinatorEntity)
                return "Another CC lies between the two machines and would intercept this link.";
        }
        // An inventory immediately behind the NEW CC would claim its incoming
        // face and disable the reverse wireless connection.
        BlockPos incoming=end.relative(dir.getOpposite());
        if(plan.distance()>1 && world.getBlockEntity(incoming)
                instanceof net.minecraft.world.Container c
                && !(c instanceof CoordinatorEntity)
                && !(c instanceof DrillEntity))
            return "An adjacent inventory would block the new CC's wireless face.";
        return null;
    }

    private static void place(ServerLevel world,Player player,ItemStack tool,Plan plan){
        String error=problem(world,plan);
        if(error!=null){say(player,error);return;}
        BlockPos pos=plan.origin().relative(plan.direction(),plan.distance());
        if(!world.mayInteract(player,pos)||!player.mayUseItemAt(pos,plan.direction(),tool)){
            say(player,"You cannot place a coordinator at that position.");
            return;
        }
        int slot=player.getAbilities().instabuild?-1:findCC(player);
        if(!player.getAbilities().instabuild&&slot<0){
            say(player,"Carry a Collection Coordinator in your inventory to use the guide.");
            return;
        }
        if(!world.setBlock(pos,MiningRig.COORDINATOR.defaultBlockState(),Block.UPDATE_ALL)){
            say(player,"Couldn't place a Collection Coordinator there.");
            return;
        }
        if(slot>=0)player.getInventory().getItem(slot).shrink(1);
        world.playSound(null,pos,SoundEvents.ANVIL_PLACE,SoundSource.BLOCKS,.35f,1.5f);
        say(player,"Placed linked CC "+plan.distance()+" blocks "+plan.direction().getName()
            +" from "+plan.origin().toShortString()+". Guide continues from new CC.");
        // Continue the route at the same spacing; rapidly build CC chains.
        save(tool,new Plan(plan.dimension(),pos.immutable(),plan.direction(),plan.distance()));
    }

    /** Faint axis and box preview only while holding the guide.
     * No client mixins, network packets or chunk-force-loading required. */
    public static void tickServer(MinecraftServer server){
        for(ServerPlayer player:server.getPlayerList().getPlayers()){
            ServerLevel world=(ServerLevel)player.level();
            if(world.getGameTime()%15!=0)continue;
            ItemStack item=player.getMainHandItem().is(MiningRig.CC_GUIDE)
                ?player.getMainHandItem():player.getOffhandItem();
            if(!item.is(MiningRig.CC_GUIDE))continue;
            Plan plan=read(item);
            if(plan==null||!dimension(world).equals(plan.dimension())
                    ||!world.hasChunkAt(plan.origin()))continue;
            if(!(world.getBlockEntity(plan.origin()) instanceof CoordinatorEntity))continue;
            Direction dir=plan.direction();
            for(int i=1;i<=MAX_DISTANCE;i+=2){
                BlockPos step=plan.origin().relative(dir,i);
                if(!world.hasChunkAt(step))break;
                world.sendParticles(ParticleTypes.END_ROD,step.getX()+.5,
                    step.getY()+.5,step.getZ()+.5,1,0,0,0,0);
            }
            BlockPos target=plan.origin().relative(dir,plan.distance());
            if(!world.hasChunkAt(target))continue;
            boolean valid=problem(world,plan)==null;
            for(int x=0;x<=1;x++)for(int y=0;y<=1;y++)for(int z=0;z<=1;z++)
                world.sendParticles(valid?ParticleTypes.END_ROD:ParticleTypes.SMOKE,
                    target.getX()+.12+.76*x,target.getY()+.12+.76*y,
                    target.getZ()+.12+.76*z,1,0,0,0,0);
        }
    }
}
