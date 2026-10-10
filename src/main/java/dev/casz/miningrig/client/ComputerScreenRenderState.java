package dev.casz.miningrig.client;

import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;

public final class ComputerScreenRenderState extends BlockEntityRenderState {
    String message = "Ready";
    boolean running;
    boolean circle;
    int radius, depth;
}
