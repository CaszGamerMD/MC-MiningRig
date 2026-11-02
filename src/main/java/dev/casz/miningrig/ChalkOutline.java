package dev.casz.miningrig;

import java.util.ArrayList;
import java.util.Base64;
import java.util.BitSet;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/**
 * Immutable, per-drill cross-section cut mask, relative to the drill head.
 * Both downward and horizontal drills retain the same u/v coordinates when moving.
 * Unlike global-world markers, this is saved with the owning computer's slot.
 */
public final class ChalkOutline {
    public static final int LIMIT = ComputerEntity.MAX_RADIUS;
    private static final int SIDE = LIMIT * 2 + 1;
    private static final int CELLS = SIDE * SIDE;
    private final BitSet mask;
    private final int radius;
    private final int count;

    private ChalkOutline(BitSet bits) {
        mask = (BitSet) bits.clone();
        int farthest = 0;
        for (int index = mask.nextSetBit(0); index >= 0 && index < CELLS;
                index = mask.nextSetBit(index + 1)) {
            int u = index % SIDE - LIMIT;
            int v = index / SIDE - LIMIT;
            farthest = Math.max(farthest, Math.max(Math.abs(u), Math.abs(v)));
        }
        radius = Math.max(1, farthest);
        count = mask.cardinality();
    }

    private static int index(int u, int v) {
        return (v + LIMIT) * SIDE + (u + LIMIT);
    }

    public int radius() { return radius; }
    public int count() { return count; }
    public boolean has(int u, int v) {
        return Math.abs(u) <= LIMIT && Math.abs(v) <= LIMIT && mask.get(index(u,v));
    }
    /** A one-block outside border used by fluid and wall passes. */
    public boolean contains(int u, int v, boolean expanded) {
        if (has(u, v)) return true;
        if (!expanded) return false;
        for (int du=-1; du<=1; du++)
            for (int dv=-1; dv<=1; dv++)
                if (has(u+du,v+dv)) return true;
        return false;
    }
    public String encode() {
        return Base64.getEncoder().encodeToString(mask.toByteArray());
    }
    public static ChalkOutline decode(String encoded) {
        if (encoded == null || encoded.isBlank() || encoded.length() > 512) return null;
        try {
            BitSet bits = BitSet.valueOf(Base64.getDecoder().decode(encoded));
            if (bits.length() > CELLS || !bits.get(index(0,0))) return null;
            ChalkOutline result = new ChalkOutline(bits);
            return result.count > 0 ? result : null;
        } catch (IllegalArgumentException ex) { return null; }
    }

    /** The chalk has independent replace, additive, and subtractive contour modes. */
    public enum Operation { REPLACE, ADD, CUT, GROUP }

    /** Existing single-polygon behavior, retained for existing callers. */
    public static ChalkOutline create(List<BlockPos> marks, DrillEntity drill, Direction face) {
        return apply(marks, drill, face, null, Operation.REPLACE);
    }

    /**
     * Rasterize a player-drawn polygon in the drill's local slice plane and
     * combine it with the previously saved bit mask. Multiple contours can
     * therefore form concave arches, donuts, letters, notches, and islands.
     * The resulting mask must still include the drill head.
     */
    public static ChalkOutline apply(List<BlockPos> marks, DrillEntity drill,
            Direction face, ChalkOutline previous, Operation operation) {
        Direction forward = drill.drillingDirection();
        if (marks.size() < 3 || marks.size() > TargetChalkItem.MAX_POINTS
                || forward != face.getOpposite()) return null;
        if (operation != Operation.REPLACE && previous == null) return null;
        BlockPos origin = drill.getBlockPos();
        List<int[]> vertices = new ArrayList<>();
        for (BlockPos p : marks) {
            int u, v;
            if (forward == Direction.DOWN) {
                u = p.getX() - origin.getX();
                v = p.getZ() - origin.getZ();
            } else {
                u = (p.getX() - origin.getX()) * forward.getClockWise().getStepX()
                    + (p.getZ() - origin.getZ()) * forward.getClockWise().getStepZ();
                v = p.getY() - origin.getY();
            }
            if (Math.abs(u) > LIMIT || Math.abs(v) > LIMIT) return null;
            if (vertices.isEmpty() || vertices.get(vertices.size()-1)[0] != u
                    || vertices.get(vertices.size()-1)[1] != v)
                vertices.add(new int[]{u,v});
        }
        if (vertices.size() < 3) return null;

        BitSet region = new BitSet(CELLS);
        for (int u=-LIMIT;u<=LIMIT;u++)
            for (int v=-LIMIT;v<=LIMIT;v++)
                if (inside(vertices,u,v))region.set(index(u,v));
        if (region.isEmpty()) return null;
        if (operation == Operation.REPLACE) {
            if (!region.get(index(0,0))) return null;
            return new ChalkOutline(region);
        }

        // A new contour must touch the current outline. This avoids applying
        // an edit to unrelated drills merely because they are within 20 blocks.
        BitSet overlap = (BitSet)region.clone();
        overlap.and(previous.mask);
        if (overlap.isEmpty()) {
            if (operation == Operation.CUT) return null;
            boolean adjoining = false;
            for (int u=-LIMIT;u<=LIMIT && !adjoining;u++)
                for (int v=-LIMIT;v<=LIMIT;v++)
                    if (region.get(index(u,v)) && previous.contains(u,v,true)){
                        adjoining=true;break;
                    }
            if (!adjoining) return null;
        }
        BitSet composite = (BitSet) previous.mask.clone();
        if (operation == Operation.ADD) composite.or(region);
        else composite.andNot(region);
        if (composite.isEmpty() || !composite.get(index(0,0))
                || composite.equals(previous.mask)) return null;
        return new ChalkOutline(composite);
    }

    private static boolean inside(List<int[]> poly,int u,int v) {
        boolean inside=false;
        for (int i=0,j=poly.size()-1;i<poly.size();j=i++) {
            int[] a=poly.get(j), b=poly.get(i);
            long cross=(long)(u-a[0])*(b[1]-a[1])-(long)(v-a[1])*(b[0]-a[0]);
            if (cross==0 && u>=Math.min(a[0],b[0]) && u<=Math.max(a[0],b[0])
                    && v>=Math.min(a[1],b[1]) && v<=Math.max(a[1],b[1]))
                return true;
            if ((a[1]>v)!=(b[1]>v)
                    && u < a[0] + (double)(v-a[1])*(b[0]-a[0])/(b[1]-a[1]))
                inside=!inside;
        }
        return inside;
    }
}
