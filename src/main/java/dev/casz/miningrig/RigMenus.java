package dev.casz.miningrig;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.inventory.MenuType;

public final class RigMenus {
    public static final MenuType<DrillMenu> DRILL = Registry.register(BuiltInRegistries.MENU,
            MiningRig.id("drill_menu"),new MenuType<>(DrillMenu::new,FeatureFlagSet.of()));
    public static final MenuType<CoordinatorMenu> COORDINATOR = Registry.register(BuiltInRegistries.MENU,
            MiningRig.id("coordinator_menu"),new MenuType<>(CoordinatorMenu::new,FeatureFlagSet.of()));
    public static void initialize() { }
}
