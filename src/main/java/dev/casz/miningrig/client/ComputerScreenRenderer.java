package dev.casz.miningrig.client;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import org.jetbrains.annotations.Nullable;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

import dev.casz.miningrig.ComputerEntity;

/**
 * Drawn on the front/north screen of the computer, rather than only in chat.
 * Uses the modern 26.2 submit pipeline (works with the Vulkan renderer).
 */
public final class ComputerScreenRenderer implements BlockEntityRenderer<ComputerEntity, ComputerScreenRenderState> {
    private final Font font;
    public ComputerScreenRenderer(BlockEntityRendererProvider.Context context) {
        font = context.font();
    }
    @Override public ComputerScreenRenderState createRenderState() {
        return new ComputerScreenRenderState();
    }
    @Override public void extractRenderState(ComputerEntity entity, ComputerScreenRenderState state,
                                            float partialTick, Vec3 cameraPos,
                                            @Nullable ModelFeatureRenderer.CrumblingOverlay crumblingOverlay) {
        BlockEntityRenderer.super.extractRenderState(entity, state, partialTick, cameraPos, crumblingOverlay);
        state.message = entity.screenStatus();
        state.running = entity.running();
        state.radius = entity.radius();
        state.depth = entity.depth();
        state.circle = entity.circle();
    }
    @Override public void submit(ComputerScreenRenderState state, PoseStack matrices, SubmitNodeCollector queue,
                                 CameraRenderState cameraState) {
        matrices.pushPose();
        // Computer model has a screen on its northern face, z = 0.
        matrices.translate(0.5, 0.69, -0.009);
        matrices.mulPose(Axis.YP.rotationDegrees(180f));
        matrices.scale(1f / 128, 1f / 128, 1f / 128);
        draw(queue, matrices, state, "MINING RIG", 0, 0xff90efff);
        List<String> lines = wrap(state.message, 82);
        int color = state.running ? 0xff7dff8b : 0xffff7070;
        for (int i = 0; i < Math.min(2, lines.size()); i++) {
            draw(queue, matrices, state, lines.get(i), -(i + 1) * 13, color);
        }
        String setting = (state.circle ? "O " : "[] ") + "R:" + state.radius + " D:" + state.depth;
        draw(queue, matrices, state, setting, -39, 0xffffffff);
        matrices.popPose();
    }
    private List<String> wrap(String input, int pixels) {
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String word : input.split(" ")) {
            String trial = current.length() == 0 ? word : current + " " + word;
            if (font.width(trial) > pixels && current.length() > 0) {
                result.add(current.toString());
                current = new StringBuilder(word);
            } else {
                current = new StringBuilder(trial);
            }
        }
        if (current.length() > 0) result.add(current.toString());
        return result;
    }
    private void draw(SubmitNodeCollector queue, PoseStack pose, ComputerScreenRenderState state,
                      String label, float y, int color) {
        queue.submitText(pose, -font.width(label) / 2f, y, Component.literal(label).getVisualOrderText(),
                false, Font.DisplayMode.SEE_THROUGH, state.lightCoords, color, 0, 0);
    }
}
