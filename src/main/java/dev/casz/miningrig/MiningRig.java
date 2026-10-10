package dev.casz.miningrig;

import java.util.function.Function;
import net.fabricmc.api.ModInitializer;
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
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;

public final class MiningRig implements ModInitializer {
    public static final String ID = "miningrig";
    public static Identifier id(String path) { return Identifier.fromNamespaceAndPath(ID, path); }

    private static Block block(String name, Function<BlockBehaviour.Properties, Block> factory) {
        BlockItemId key = BlockItemId.create(id(name), id(name));
        Block block = Registry.register(BuiltInRegistries.BLOCK, key.block(),
                factory.apply(BlockBehaviour.Properties.of().strength(4.0f).requiresCorrectToolForDrops().setId(key.block())));
        Registry.register(BuiltInRegistries.ITEM, key.item(),
                new BlockItem(block, new Item.Properties().useBlockDescriptionPrefix().setId(key.item())));
        return block;
    }
    private static Item item(String name, Function<Item.Properties, Item> factory, Item.Properties properties) {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, id(name));
        return Registry.register(BuiltInRegistries.ITEM, key, factory.apply(properties.setId(key)));
    }

    public static final Block COMPUTER = block("mining_computer", ComputerBlock::new);
    public static final Block DRILL = block("mining_drill", DrillBlock::new);
    public static final Block COORDINATOR = block("collection_coordinator", CoordinatorBlock::new);
    public static final BlockEntityType<ComputerEntity> COMPUTER_ENTITY = Registry.register(
            BuiltInRegistries.BLOCK_ENTITY_TYPE, id("mining_computer"),
            FabricBlockEntityTypeBuilder.create(ComputerEntity::new, COMPUTER).build());
    public static final BlockEntityType<DrillEntity> DRILL_ENTITY = Registry.register(
            BuiltInRegistries.BLOCK_ENTITY_TYPE, id("mining_drill"),
            FabricBlockEntityTypeBuilder.create(DrillEntity::new, DRILL).build());
    public static final BlockEntityType<CoordinatorEntity> COORDINATOR_ENTITY = Registry.register(
            BuiltInRegistries.BLOCK_ENTITY_TYPE, id("collection_coordinator"),
            FabricBlockEntityTypeBuilder.create(CoordinatorEntity::new, COORDINATOR).build());

    public static final Item LINKER = item("rig_linker", RigLinker::new, new Item.Properties().stacksTo(1));
    public static final Item WOOD_BIT = bit("wood_bit", 59, 0, 2);
    public static final Item STONE_BIT = bit("stone_bit", 131, 1, 4);
    public static final Item IRON_BIT = bit("iron_bit", 250, 2, 6);
    public static final Item GOLD_BIT = bit("gold_bit", 32, 0, 12);
    public static final Item DIAMOND_BIT = bit("diamond_bit", 1561, 3, 8);
    public static final Item NETHERITE_BIT = bit("netherite_bit", 2031, 4, 9);
    private static Item bit(String name, int durability, int rank, float speed) {
        return item(name, properties -> new DrillBit(properties, rank, speed),
                new Item.Properties().durability(durability).enchantable(14));
    }
    @Override public void onInitialize() {
        RigMenus.initialize();
        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.REDSTONE_BLOCKS).register(tab -> {
            tab.accept(COMPUTER); tab.accept(DRILL); tab.accept(COORDINATOR); tab.accept(LINKER);
            tab.accept(WOOD_BIT); tab.accept(STONE_BIT); tab.accept(IRON_BIT);
            tab.accept(GOLD_BIT); tab.accept(DIAMOND_BIT); tab.accept(NETHERITE_BIT);
        });
    }
}
