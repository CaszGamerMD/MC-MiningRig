package dev.casz.miningrig.client;

import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraft.client.gui.screens.MenuScreens;
import dev.casz.miningrig.RigMenus;
import dev.casz.miningrig.MiningRig;

public final class MiningRigClient implements ClientModInitializer {
    @Override public void onInitializeClient() {
        BlockEntityRenderers.register(MiningRig.COMPUTER_ENTITY, ComputerScreenRenderer::new);
        MenuScreens.register(RigMenus.DRILL, DrillScreen::new);
        MenuScreens.register(RigMenus.COORDINATOR, CoordinatorScreen::new);
    }
}
