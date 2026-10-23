package dev.casz.miningrig;

import java.util.function.Function;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.references.BlockItemId;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;

public final class MiningRig implements ModInitializer {
    public static final String ID = "miningrig";
    public static Identifier id(String path) { return Identifier.fromNamespaceAndPath(ID, path); }
    /** Optional Caszual Additions creative page; no classes or APIs from that
     * mod are linked, so Mining Rig remains usable on its own. */
    private static final ResourceKey<CreativeModeTab> CASZUAL_ADDITIONS_TAB =
        ResourceKey.create(Registries.CREATIVE_MODE_TAB,
            Identifier.fromNamespaceAndPath("caszual_additions","main"));

    private static Block block(String name, Function<BlockBehaviour.Properties, Block> factory) {
        BlockItemId key = BlockItemId.create(id(name), id(name));
        Block block = Registry.register(BuiltInRegistries.BLOCK, key.block(),
                factory.apply(BlockBehaviour.Properties.of().strength(4.0f).noOcclusion().requiresCorrectToolForDrops().setId(key.block())));
        Registry.register(BuiltInRegistries.ITEM, key.item(),
                new BlockItem(block, new Item.Properties().useBlockDescriptionPrefix()
                        .stacksTo(name.equals("mining_drill")?1:64).setId(key.item())));
        return block;
    }
    /** An upper machine section with no obtainable item of its own. */
    private static Block machinePart(String name,Function<BlockBehaviour.Properties,Block> factory){
        BlockItemId key=BlockItemId.create(id(name),id(name));
        return Registry.register(BuiltInRegistries.BLOCK,key.block(),
            factory.apply(BlockBehaviour.Properties.of().strength(4.0f).noOcclusion().setId(key.block())));
    }
    private static Item item(String name, Function<Item.Properties, Item> factory, Item.Properties properties) {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, id(name));
        return Registry.register(BuiltInRegistries.ITEM, key, factory.apply(properties.setId(key)));
    }

    public static final Block COMPUTER = block("mining_computer", ComputerBlock::new);
    public static final Block COMPUTER_UPPER = machinePart("mining_computer_upper",ComputerUpperBlock::new);
    public static final Block DRILL = block("mining_drill", DrillBlock::new);
    public static final Block DRILL_UPPER = machinePart("mining_drill_upper",DrillUpperBlock::new);
    public static final Block COORDINATOR = block("collection_coordinator", CoordinatorBlock::new);
    // Tablet is a placeable item but needs its own link-aware BlockItem class.
    private static Block tabletBlock(){
        BlockItemId key=BlockItemId.create(id("mining_tablet"),id("mining_tablet"));
        return Registry.register(BuiltInRegistries.BLOCK,key.block(),
            new MiningTabletBlock(BlockBehaviour.Properties.of().strength(2).noOcclusion().setId(key.block())));
    }
    public static final Block TABLET_BLOCK=tabletBlock();
    public static final Item TARGET_CHALK=item("target_chalk",TargetChalkItem::new,
        new Item.Properties().stacksTo(1));
    public static final Item CC_GUIDE=item("cc_placement_guide",CoordinatorGuideItem::new,
        new Item.Properties().stacksTo(1));
    public static final Item TABLET=item("mining_tablet",MiningTabletItem::new,
        new Item.Properties().stacksTo(1));
    public static final BlockEntityType<ComputerEntity> COMPUTER_ENTITY = Registry.register(
            BuiltInRegistries.BLOCK_ENTITY_TYPE, id("mining_computer"),
            FabricBlockEntityTypeBuilder.create(ComputerEntity::new, COMPUTER).build());
    public static final BlockEntityType<DrillEntity> DRILL_ENTITY = Registry.register(
            BuiltInRegistries.BLOCK_ENTITY_TYPE, id("mining_drill"),
            FabricBlockEntityTypeBuilder.create(DrillEntity::new, DRILL).build());
    public static final BlockEntityType<MiningTabletEntity> TABLET_ENTITY = Registry.register(
            BuiltInRegistries.BLOCK_ENTITY_TYPE,id("mining_tablet"),
            FabricBlockEntityTypeBuilder.create(MiningTabletEntity::new,TABLET_BLOCK).build());
    public static final BlockEntityType<CoordinatorEntity> COORDINATOR_ENTITY = Registry.register(
            BuiltInRegistries.BLOCK_ENTITY_TYPE, id("collection_coordinator"),
            FabricBlockEntityTypeBuilder.create(CoordinatorEntity::new, COORDINATOR).build());

    public static final Item WOOD_BIT = bit("wood_bit", 59, 0, 2, ToolMaterial.WOOD);
    public static final Item STONE_BIT = bit("stone_bit", 131, 1, 4, ToolMaterial.STONE);
    public static final Item IRON_BIT = bit("iron_bit", 250, 2, 6, ToolMaterial.IRON);
    public static final Item GOLD_BIT = bit("gold_bit", 32, 0, 12, ToolMaterial.GOLD);
    public static final Item DIAMOND_BIT = bit("diamond_bit", 1561, 3, 8, ToolMaterial.DIAMOND);
    public static final Item NETHERITE_BIT = bit("netherite_bit", 2031, 4, 9, ToolMaterial.NETHERITE);
    private static Item bit(String name, int durability, int rank, float speed, ToolMaterial material) {
        return item(name, properties -> new DrillBit(properties, rank, speed),
                new Item.Properties().pickaxe(material, 1.0f, -2.8f).durability(durability).enchantable(14));
    }
    /** All twelve obtainable Mining Rig items; the upper machine halves
     * deliberately have no item registrations and must not be listed. */
    private static void addMiningRigCreativeItems(CreativeModeTab.Output output){
        output.accept(COMPUTER);
        output.accept(DRILL);
        output.accept(COORDINATOR);
        output.accept(CC_GUIDE);
        output.accept(TARGET_CHALK);
        output.accept(TABLET);
        output.accept(WOOD_BIT);
        output.accept(STONE_BIT);
        output.accept(IRON_BIT);
        output.accept(GOLD_BIT);
        output.accept(DIAMOND_BIT);
        output.accept(NETHERITE_BIT);
    }

    @Override public void onInitialize() {
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(TargetChalkItem::tickServer);
        RigMachineSounds.initialize();
        RigChunkTickets.initialize();
        RigMenus.initialize();
        RigNetworking.initialize();
        TabletAccess.initialize();
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(TabletAccess::tickServer);
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(CoordinatorGuideItem::tickServer);
        // Keep the historical Redstone entries, including standalone installs.
        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.REDSTONE_BLOCKS)
            .register(MiningRig::addMiningRigCreativeItems);
        // Add the identical set to Caszual Additions' existing creative page
        // when that mod is loaded. Different tabs may show the same items;
        // register this callback only once to avoid duplicate stacks IN a tab.
        if(FabricLoader.getInstance().isModLoaded("caszual_additions"))
            CreativeModeTabEvents.modifyOutputEvent(CASZUAL_ADDITIONS_TAB)
                .register(MiningRig::addMiningRigCreativeItems);
    }
}
