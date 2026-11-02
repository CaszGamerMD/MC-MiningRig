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
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import org.jetbrains.annotations.Nullable;

/**
 * CC Organizer: placement and retrieval in the same saved item.
 *
 * Sneak-right-click anywhere to switch modes (never also select or recall).
 * Placement right-clicks select a CC face / set spacing / confirm in air.
 * Retrieval right-clicks a CC (or visible CC up to 100 blocks) to recall.
 * Every action performs ONE operation; clicks never unexpectedly place.
 * The original registry ID and saved placement plans remain compatible.
 */
public final class CoordinatorGuideItem extends Item {
    private static final int MAX_DISTANCE=20;
    public static final int RETRIEVAL_REACH=100;
    public CoordinatorGuideItem(Properties properties){super(properties);}

    /** Old guide items default to Placement; no item ID or NBT migration. */
    public static boolean isRetrieval(ItemStack tool){
        CustomData data=tool.get(DataComponents.CUSTOM_DATA);
        return data!=null&&data.copyTag().getBooleanOr("OrganizerRetrieval",false);
    }
    /** Item-local settings, saved to CustomData to survive inventory transfers. */
    public static int distance(ItemStack tool){
        CustomData data=tool.get(DataComponents.CUSTOM_DATA);
        if(data==null)return 8;
        CompoundTag tag=data.copyTag();
        return Math.max(1,Math.min(MAX_DISTANCE,tag.getIntOr("OrganizerDistance",
            tag.getIntOr("GuideDistance",8))));
    }
    public static boolean previewFirst(ItemStack tool){
        CustomData data=tool.get(DataComponents.CUSTOM_DATA);
        return data!=null&&data.copyTag().getBooleanOr("OrganizerPreviewFirst",false);
    }
    public static void writeOptions(ItemStack tool,boolean retrieval,int distance,boolean preview){
        CustomData data=tool.get(DataComponents.CUSTOM_DATA);
        CompoundTag tag=data==null?new CompoundTag():data.copyTag();
        tag.putBoolean("OrganizerRetrieval",retrieval);
        tag.putInt("OrganizerDistance",Math.max(1,Math.min(MAX_DISTANCE,distance)));
        tag.putBoolean("OrganizerPreviewFirst",preview);
        tag.remove("OrganizerPreviewActive");
        tool.set(DataComponents.CUSTOM_DATA,CustomData.of(tag));
    }
    private static void openSettings(Player player,InteractionHand hand){
        if(!(player instanceof ServerPlayer))return;
        player.openMenu(new SimpleMenuProvider(
            (id,inv,owner)->new OrganizerMenu(id,inv,hand),
            Component.literal("CC ORGANIZER")));
    }
    private static void clearPreview(ItemStack tool){
        CustomData data=tool.get(DataComponents.CUSTOM_DATA);
        if(data==null)return;
        CompoundTag tag=data.copyTag();
        tag.remove("OrganizerPreviewActive");
        tool.set(DataComponents.CUSTOM_DATA,CustomData.of(tag));
    }
    private static boolean matchingPreview(ItemStack tool,Plan plan,long now){
        CustomData data=tool.get(DataComponents.CUSTOM_DATA);
        if(data==null)return false;
        CompoundTag tag=data.copyTag();
        long began=tag.getLongOr("OrganizerPreviewTick",-1);
        return tag.getBooleanOr("OrganizerPreviewActive",false)
            && began>=0 && now>=began && now-began<=12
            && tag.getLongOr("OrganizerPreviewOrigin",Long.MIN_VALUE)==plan.origin().asLong()
            && tag.getIntOr("OrganizerPreviewFace",-1)==plan.direction().get3DDataValue()
            && tag.getStringOr("OrganizerPreviewDimension","").equals(plan.dimension());
    }
    private static void markPreview(ItemStack tool,Plan plan,long now){
        CustomData data=tool.get(DataComponents.CUSTOM_DATA);
        CompoundTag tag=data==null?new CompoundTag():data.copyTag();
        tag.putBoolean("OrganizerPreviewActive",true);
        tag.putLong("OrganizerPreviewTick",now);
        tag.putLong("OrganizerPreviewOrigin",plan.origin().asLong());
        tag.putInt("OrganizerPreviewFace",plan.direction().get3DDataValue());
        tag.putString("OrganizerPreviewDimension",plan.dimension());
        tool.set(DataComponents.CUSTOM_DATA,CustomData.of(tag));
    }

    /** Discard a placement plan if its origin was recalled. */
    public static void onRecalled(ItemStack tool,BlockPos pos){
        Plan current=read(tool);
        if(current==null || !current.origin().equals(pos))return;
        CustomData data=tool.get(DataComponents.CUSTOM_DATA);
        if(data==null)return;
        CompoundTag tag=data.copyTag();
        for(String key:new String[]{"GuideActive","GuideDimension","GuideOrigin",
                "GuideDirection","GuideDistance","OrganizerPreviewActive"})tag.remove(key);
        tool.set(DataComponents.CUSTOM_DATA,CustomData.of(tag));
    }

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
        if(player.isShiftKeyDown()){
            openSettings(player,player.getMainHandItem()==tool
                ?InteractionHand.MAIN_HAND:InteractionHand.OFF_HAND);
            return InteractionResult.SUCCESS;
        }
        if(isRetrieval(tool)){
            CoordinatorRetrieval.recall(server,ccPos,player,tool,outward);
            return InteractionResult.SUCCESS;
        }
        Plan plan=new Plan(dimension(server),ccPos.immutable(),outward,distance(tool));
        if(previewFirst(tool)){
            boolean confirmed=matchingPreview(tool,plan,server.getGameTime());
            if(!confirmed){
                save(tool,plan);
                markPreview(tool,plan,server.getGameTime());
                String problem=problem(server,plan);
                say(player,"CC Organizer: PREVIEW "+plan.distance()+" blocks "+
                    outward.getName()+" at "+
                    ccPos.relative(outward,plan.distance()).toShortString()
                    +(problem==null?". Double-click this CC face to place.":". "+problem));
                return InteractionResult.SUCCESS;
            }
        }
        clearPreview(tool);
        place(server,player,tool,plan);
        return InteractionResult.SUCCESS;
    }

    @Override public InteractionResult useOn(UseOnContext context){
        Level world=context.getLevel();
        if(!(world instanceof ServerLevel))return InteractionResult.SUCCESS;
        Player player=context.getPlayer();
        if(player==null)return InteractionResult.PASS;
        ItemStack tool=context.getItemInHand();
        if(player.isShiftKeyDown()){
            openSettings(player,context.getHand());
            return InteractionResult.SUCCESS;
        }
        BlockPos clicked=context.getClickedPos();
        if(world.getBlockState(clicked).is(MiningRig.COORDINATOR))
            return select(world,clicked,context.getClickedFace(),player,tool);
        say(player,isRetrieval(tool)
            ?"CC Organizer: aim at a visible CC (100-block reach) and right-click."
            :"CC Organizer: click the FACE of an existing CC to "+
                (previewFirst(tool)?"preview, then double-click to place.":"place immediately."));
        return InteractionResult.SUCCESS;
    }

    @Override public InteractionResult use(Level world,Player player,InteractionHand hand){
        if(!(world instanceof ServerLevel server))return InteractionResult.SUCCESS;
        ItemStack tool=player.getItemInHand(hand);
        if(player.isShiftKeyDown()){
            openSettings(player,hand);
            return InteractionResult.SUCCESS;
        }
        if(isRetrieval(tool))retrieveInSight(server,player,tool);
        else say(player,"CC Organizer: right-click the FACE of a CC. Distance "+
            distance(tool)+"/20 blocks. "+
            (previewFirst(tool)?"Double-click to confirm a preview.":"One click to place."));
        return InteractionResult.SUCCESS;
    }

    /**
     * In Retrieval mode the vanilla interaction range (about five blocks)
     * does not permit useItemOn to reach a distant CC. The client calls
     * Item.use on a miss; raycast on the server to a full 100 blocks.
     *
     * Stops at the FIRST collidable block. No retrieving through walls,
     * no nearest-CC guesses, and no forced chunk loading at long range.
     */
    private static void retrieveInSight(ServerLevel world,Player player,ItemStack tool){
        Vec3 eye=player.getEyePosition();
        Vec3 look=player.getViewVector(1.0F).normalize();
        // Stop the ray at the FIRST unloaded chunk, not at any unloaded
        // chunk farther away than an otherwise valid CC target. The old
        // 100-block precheck rejected a visible close CC if distant terrain
        // was not loaded. Sample every half block without force-loading.
        double safe=0;
        for(double step=.5;step<=RETRIEVAL_REACH;step+=.5){
            BlockPos along=BlockPos.containing(eye.add(look.scale(step)));
            if(!world.hasChunkAt(along))break;
            safe=step;
        }
        if(safe==0){
            say(player,"CC Organizer: no loaded line of sight; move closer.");
            return;
        }
        Vec3 reach=eye.add(look.scale(safe));
        BlockHitResult hit=world.clip(new ClipContext(eye,reach,
            ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,player));
        if(hit.getType()!=HitResult.Type.BLOCK
                || !world.getBlockState(hit.getBlockPos()).is(MiningRig.COORDINATOR)){
            say(player,"CC Organizer: no visible CC within "+RETRIEVAL_REACH
                +" blocks of loaded line of sight (walls block retrieval).");
            return;
        }
        CoordinatorRetrieval.recall(world,hit.getBlockPos(),player,tool,hit.getDirection());
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

    /** Render the requested next-CC preview only after a preview-first click.
     * Markers expire and never load terrain chunks or leave placed blocks. */
    public static void tickServer(MinecraftServer server){
        for(ServerPlayer player:server.getPlayerList().getPlayers()){
            ServerLevel world=(ServerLevel)player.level();
            if(world.getGameTime()%8!=0)continue;
            ItemStack item=player.getMainHandItem().is(MiningRig.CC_GUIDE)
                ?player.getMainHandItem():player.getOffhandItem();
            if(!item.is(MiningRig.CC_GUIDE)||isRetrieval(item)||!previewFirst(item))continue;
            CustomData data=item.get(DataComponents.CUSTOM_DATA);
            if(data==null)continue;
            CompoundTag tag=data.copyTag();
            if(!tag.getBooleanOr("OrganizerPreviewActive",false))continue;
            long age=world.getGameTime()-tag.getLongOr("OrganizerPreviewTick",0);
            if(age<0||age>160)continue;
            Plan plan=read(item);
            if(plan==null||!dimension(world).equals(plan.dimension())
                    ||!world.hasChunkAt(plan.origin()))continue;
            Direction dir=plan.direction();
            BlockPos target=plan.origin().relative(dir,plan.distance());
            if(!world.hasChunkAt(target))continue;
            boolean valid=problem(world,plan)==null;
            // Project a 3D block outline exactly where the new coordinator
            // would be placed; green denotes valid, smoke denotes blocked.
            for(int x=0;x<=1;x++)for(int y=0;y<=1;y++)for(int z=0;z<=1;z++)
                world.sendParticles(valid?ParticleTypes.END_ROD:ParticleTypes.SMOKE,
                    target.getX()+.09+x*.82,target.getY()+.09+y*.82,
                    target.getZ()+.09+z*.82,1,0,0,0,0);
            for(int step=1;step<plan.distance();step+=3){
                BlockPos at=plan.origin().relative(dir,step);
                if(!world.hasChunkAt(at))break;
                world.sendParticles(ParticleTypes.END_ROD,at.getX()+.5,
                    at.getY()+.5,at.getZ()+.5,1,0,0,0,0);
            }
        }
    }
}
