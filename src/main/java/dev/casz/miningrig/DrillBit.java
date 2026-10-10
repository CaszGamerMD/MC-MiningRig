package dev.casz.miningrig;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.tags.BlockTags;

/** Mining speed follows the corresponding vanilla material pickaxe baseline. */
public final class DrillBit extends Item {
    private final int rank;
    private final float miningSpeed;
    public DrillBit(Properties properties,int rank,float miningSpeed) {
        super(properties);this.rank=rank;this.miningSpeed=miningSpeed;
    }
    public float miningSpeed(){return miningSpeed;}
    public boolean canMine(BlockState state){
        if(state.is(BlockTags.NEEDS_DIAMOND_TOOL))return rank>=3;
        if(state.is(BlockTags.NEEDS_IRON_TOOL))return rank>=2;
        if(state.is(BlockTags.NEEDS_STONE_TOOL))return rank>=1;
        return true;
    }
}
