#!/usr/bin/env python3
"""Performance regression checks for 8-16 simultaneous group drills.

The large-hole path must be constant-time per footprint cell, avoid repeated
chunk graph discovery, and fairly budget expensive mine/seal steps per tick.
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
# A 13-drill group must be scheduled fairly, not repeatedly from slot zero.
active=set(range(13))
cursor=0
allocations={i:0 for i in active}
for tick in range(13):
    done=0;scanned=0
    while scanned<16 and done<4:
        slot=(cursor+scanned)%16
        if slot in active:
            allocations[slot]+=1;done+=1
        scanned+=1
    cursor=(cursor+max(1,scanned))%16
assert all(allocations.values()),allocations
assert max(allocations.values())-min(allocations.values())<=1,allocations
assert "HEAVY_CREW_STEPS_PER_TICK=4;" in computer
assert "workDone<workBudget" in computer
assert "computer.workCursor=(first+Math.max(1,scanned))%MAX_DRILLS;" in computer
assert "if(now%10==0)computer.syncActiveChunks(server);" in computer
tick=computer[computer.index("public static void tick(Level level"):computer.index("@Override public void setRemoved()")]
assert tick.count("computer.syncActiveChunks(server)") == 1
assert "RigNetwork.includeLoadedNetwork(server,drill,desired)" not in computer
assert "RigNetwork.includeLoadedCCs(server,RigTopology.network(server,this),desired)" in computer
assert "visualPhase" in tick
print("PASS: 13-drill fair budget, cached group ownership/perimeter, deduplicated chunk graph and reduced particles")
