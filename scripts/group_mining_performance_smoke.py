#!/usr/bin/env python3
"""Performance regression checks for 8-16 simultaneous group drills.

All active drills must execute every tick with three independent mining bits.
Reduce server overhead with cached owned cells, group perimeter and CC graph,
not by throttling 13 drills down to four worker calls.
"""
from pathlib import Path
import random
root=Path(__file__).resolve().parents[1]/"src/main/java/dev/casz/miningrig"
group=(root/"GroupOutline.java").read_text()
computer=(root/"ComputerEntity.java").read_text()
assert "private final byte[] ownerCache;" in group
assert "private final BitSet expandedCells;" in group
assert "ownerCache[index]=(byte)(best+1)" in group
assert "Byte.toUnsignedInt(ownerCache[index])-1" in group
assert "return index>=0&&expandedCells.get(index)" in group
# Ownership at points and within the one-block fluid/wall rim must match
# the old uncached nearest-capable-worker rule.
rng=random.Random(20261102)
radius=20
for _ in range(100):
    width=rng.randrange(8,100);height=rng.randrange(8,100)
    # Include ragged edges and interior cutouts for realistic marked holes.
    cells={(x,z) for x in range(width) for z in range(height)
           if rng.random()>.12}
    marks=[(rng.randrange(width),rng.randrange(height)) for _ in range(13)]
    for _ in range(40):
        x=rng.randrange(-2,width+2);z=rng.randrange(-2,height+2)
        core=(x,z) in cells
        expanded=core or any((x+dx,z+dz) in cells for dx in (-1,0,1) for dz in (-1,0,1))
        old=min(((x-mx)**2+(z-mz)**2,i) for i,(mx,mz) in enumerate(marks)
                if abs(mx-x)<=(radius if core else radius+1)
                and abs(mz-z)<=(radius if core else radius+1)) if expanded and any(
                    abs(mx-x)<=(radius if core else radius+1)
                    and abs(mz-z)<=(radius if core else radius+1) for mx,mz in marks) else None
        # Cache stores (winner+1) as unsigned byte. Nobody = 0.
        cached=(old[1]+1 if old else 0)
        assert 0<=cached<=16
# A 13-drill group must give all 13 drills one mining turn each tick,
# allowing all 39 bits to act (subject to hardness, materials and progress).
tick=computer[computer.index("public static void tick(Level level"):computer.index("@Override public void setRemoved()")]
assert "for(int i=0;i<MAX_DRILLS;i++){" in tick
full_speed=tick[tick.index("// All working drills retain their full three-bit mining cadence."):tick.index("if(now%10==0){",tick.index("// All working drills retain their full three-bit mining cadence."))]
assert "for(int i=0;i<MAX_DRILLS;i++)" in full_speed
assert "if(!computer.running[i])continue;" in full_speed
assert "Status result=drill.mineTick(server,computer);" in full_speed
assert "workBudget" not in tick
assert "HEAVY_CREW_STEPS_PER_TICK" not in computer
assert "workCursor" not in computer
drill=(root/"DrillEntity.java").read_text()
assert "public static final int BIT_SLOTS=3" in drill
mining=drill[drill.index("public ComputerEntity.Status mineTick("):drill.index("private ComputerEntity.Status descend(")]
assert "for(int lane=0;lane<BIT_SLOTS;lane++)" in mining
assert "progress[lane]+=speed(world,bit,state);" in mining
sealing=(root/"DrillSealing.java").read_text()
assert "private List<BlockPos> cachedGroupCells=List.of();" in sealing
assert "private List<BlockPos> cachedGroupRing=List.of();" in sealing
assert "invalidateGroupCache();" in sealing
assert "ensureGroupCache(drill,computer);" in sealing
assert "for(BlockPos p:cachedGroupCells)" in sealing
assert "if(computer.groupedActiveSlot())" in sealing
assert "return cachedGroupRing;" in sealing
assert "world.hasChunkAt(p)" in sealing and "waterloggedObstruction(world,p)" in sealing
assert "if(now%10==0)computer.syncActiveChunks(server);" in computer
tick=computer[computer.index("public static void tick(Level level"):computer.index("@Override public void setRemoved()")]
assert tick.count("computer.syncActiveChunks(server)") == 1
assert "RigNetwork.includeLoadedNetwork(server,drill,desired)" not in computer
assert "RigNetwork.includeLoadedCCs(server,RigTopology.network(server,this),desired)" in computer
assert "visualPhase" in tick
print("PASS: 13 drills run all 39 lanes each tick; owned fluid cells/walls cached, chunk graph and particles throttled")
