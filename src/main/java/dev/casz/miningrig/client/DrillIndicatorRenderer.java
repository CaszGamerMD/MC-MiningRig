package dev.casz.miningrig.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.casz.miningrig.DrillEntity;
import dev.casz.miningrig.MiningRig;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/** Three colored metal dots on the drill cone show its actual installed bits. */
public final class DrillIndicatorRenderer implements BlockEntityRenderer<DrillEntity,DrillIndicatorRenderState> {
    private final Font font;
    public DrillIndicatorRenderer(BlockEntityRendererProvider.Context context){font=context.font();}
    @Override public DrillIndicatorRenderState createRenderState(){return new DrillIndicatorRenderState();}
    @Override public void extractRenderState(DrillEntity drill,DrillIndicatorRenderState state,float partialTick,
             Vec3 cameraPos,@Nullable ModelFeatureRenderer.CrumblingOverlay overlay){
        BlockEntityRenderer.super.extractRenderState(drill,state,partialTick,cameraPos,overlay);
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
        final float[] x={0.5f,1.019f,0.5f,-.019f};
        final float[] z={-.019f,.5f,1.019f,.5f};
        final float[] rotations={180f,90f,0f,270f};
        for(int side=0;side<4;side++){
            pose.pushPose();
            pose.translate(x[side],.825,z[side]);
            pose.mulPose(Axis.YP.rotationDegrees(rotations[side]));
            pose.scale(.0125f,.0125f,.0125f);
            for(int lane=0;lane<3;lane++){
                String dot="●";
                queue.submitText(pose,(lane-1)*18-font.width(dot)/2f,0,
                    Component.literal(dot).getVisualOrderText(),false,Font.DisplayMode.SEE_THROUGH,
                    state.lightCoords,state.colors[lane],0,0);
            }
            pose.popPose();
        }
    }
}
