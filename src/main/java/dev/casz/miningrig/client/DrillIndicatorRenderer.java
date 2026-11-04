package dev.casz.miningrig.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.casz.miningrig.DrillEntity;
import dev.casz.miningrig.MiningRig;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.gizmos.DrawableGizmoPrimitives;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.core.Direction;
import net.minecraft.world.item.Item;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/** Three colored metal dots show the installed bits; selection outlines the full machine. */
public final class DrillIndicatorRenderer implements BlockEntityRenderer<DrillEntity,DrillIndicatorRenderState> {
    private static final int SELECTED_COLOR=0xff55f4ff;
    private static final float SELECTED_LINE_WIDTH=3.0f;
    private static final double OUTLINE_PADDING=0.025D;

    private final Font font;
    public DrillIndicatorRenderer(BlockEntityRendererProvider.Context context){font=context.font();}
    @Override public DrillIndicatorRenderState createRenderState(){return new DrillIndicatorRenderState();}
    @Override public void extractRenderState(DrillEntity drill,DrillIndicatorRenderState state,float partialTick,
             Vec3 cameraPos,@Nullable ModelFeatureRenderer.CrumblingOverlay overlay){
        BlockEntityRenderer.super.extractRenderState(drill,state,partialTick,cameraPos,overlay);
        state.selected=drill.controllerSelected();
        state.blockX=drill.getBlockPos().getX();
        state.blockY=drill.getBlockPos().getY();
        state.blockZ=drill.getBlockPos().getZ();
        Direction bodyOffset=drill.drillingDirection().getOpposite();
        state.housingOffsetX=bodyOffset.getStepX();
        state.housingOffsetY=bodyOffset.getStepY();
        state.housingOffsetZ=bodyOffset.getStepZ();
        for(int i=0;i<3;i++){
            Item item=drill.getItem(i).getItem();
            state.colors[i]=item==MiningRig.WOOD_BIT?0xffb88a50
                :item==MiningRig.STONE_BIT?0xff858a8c
                :item==MiningRig.IRON_BIT?0xffe0e4e5
                :item==MiningRig.GOLD_BIT?0xffffde55
                :item==MiningRig.DIAMOND_BIT?0xff49fff3
                :item==MiningRig.NETHERITE_BIT?0xff9b779c:0xff35373d;
        }
    }
    @Override public void submit(DrillIndicatorRenderState state,PoseStack pose,SubmitNodeCollector queue,
                                  CameraRenderState camera){
        // Repeated on all four cone faces; visible regardless of drill orientation.
        // The collar is [2,11,2] -> [14,14,14]. Place indicators just
        // outside its four actual surfaces instead of at empty block edges.
        final float[] x={.5f,14/16f+.009f,.5f,2/16f-.009f};
        final float[] z={2/16f-.009f,.5f,14/16f+.009f,.5f};
        final float[] rotations={180f,90f,0f,270f};
        for(int side=0;side<4;side++){
            pose.pushPose();
            pose.translate(x[side],.815,z[side]);
            pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(rotations[side]));
            pose.scale(.0125f,.0125f,.0125f);
            for(int lane=0;lane<3;lane++){
                String dot="●";
                queue.submitText(pose,(lane-1)*18-font.width(dot)/2f,0,
                    Component.literal(dot).getVisualOrderText(),false,Font.DisplayMode.NORMAL,
                    state.lightCoords,state.colors[lane],0,0);
            }
            pose.popPose();
        }
        if(state.selected)submitSelectionOutline(state,queue,camera);
    }

    /**
     * A block entity is not an Entity, so it cannot receive vanilla's glowing
     * entity tag. Draw the same kind of cyan, through-wall outline around the
     * complete two-block machine using the 26.2 always-on-top gizmo phase.
     */
    private static void submitSelectionOutline(DrillIndicatorRenderState state,
            SubmitNodeCollector queue,CameraRenderState camera){
        double minX=state.blockX+Math.min(0,state.housingOffsetX)-OUTLINE_PADDING;
        double minY=state.blockY+Math.min(0,state.housingOffsetY)-OUTLINE_PADDING;
        double minZ=state.blockZ+Math.min(0,state.housingOffsetZ)-OUTLINE_PADDING;
        double maxX=state.blockX+Math.max(0,state.housingOffsetX)+1.0D+OUTLINE_PADDING;
        double maxY=state.blockY+Math.max(0,state.housingOffsetY)+1.0D+OUTLINE_PADDING;
        double maxZ=state.blockZ+Math.max(0,state.housingOffsetZ)+1.0D+OUTLINE_PADDING;

        Vec3[] corners={
            new Vec3(minX,minY,minZ),new Vec3(maxX,minY,minZ),
            new Vec3(minX,maxY,minZ),new Vec3(maxX,maxY,minZ),
            new Vec3(minX,minY,maxZ),new Vec3(maxX,minY,maxZ),
            new Vec3(minX,maxY,maxZ),new Vec3(maxX,maxY,maxZ)
        };
        DrawableGizmoPrimitives outline=new DrawableGizmoPrimitives();
        // Four edges along each axis make one clean cuboid around both halves.
        int[][] edges={
            {0,1},{2,3},{4,5},{6,7},
            {0,2},{1,3},{4,6},{5,7},
            {0,4},{1,5},{2,6},{3,7}
        };
        for(int[] edge:edges)
            outline.addLine(corners[edge[0]],corners[edge[1]],SELECTED_COLOR,SELECTED_LINE_WIDTH);
        outline.submit(queue,camera,true);
    }
}
