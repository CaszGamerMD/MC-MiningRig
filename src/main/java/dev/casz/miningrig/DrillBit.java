package dev.casz.miningrig;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.tags.BlockTags;
/** Rank tracks the minimum harvest level; loot generation receives the actual enchanted tip. */
public final class DrillBit extends Item {
    private final int rank;
    public DrillBit(Properties properties, int rank) { super(properties); this.rank = rank; }
    public boolean canMine(BlockState state) {
        if (state.is(BlockTags.NEEDS_DIAMOND_TOOL)) return rank >= 3;
        if (state.is(BlockTags.NEEDS_IRON_TOOL)) return rank >= 2;
        if (state.is(BlockTags.NEEDS_STONE_TOOL)) return rank >= 1;
        return true;
    }
}
