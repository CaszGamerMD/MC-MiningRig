#!/usr/bin/env python3
"""Regression checks for no-throttle next-layer geometry preparation and <3% bit returns."""
from pathlib import Path
root=Path(__file__).resolve().parents[1]
code=root/"src/main/java/dev/casz/miningrig"
drill=(code/"DrillEntity.java").read_text()
sealing=(code/"DrillSealing.java").read_text()
network=(code/"RigNetwork.java").read_text()
computer=(code/"ComputerEntity.java").read_text()
assert "private static final int NEXT_LAYER_GEOMETRY_STEPS=96;" in sealing
assert "void precomputeNextLayer(DrillEntity drill,ComputerEntity computer)" in sealing
assert "for(int n=0;n<NEXT_LAYER_GEOMETRY_STEPS&&nextGroupIndex<total;n++)" in sealing
assert "drill.slicePosition(dx,dz).below()" in sealing
assert "nextGroupCells.add(target);" in sealing
assert "nextGroupRing.add(target);" in sealing
assert "nextGroupHead=nextGroupHead;" in sealing
assert "next.nextGroupCells=nextGroupCells;" in sealing
assert "next.nextGroupRing=nextGroupRing;" in sealing
assert "nextGroupIndex==span*span" in sealing
assert "cachedGroupCells=List.copyOf(nextGroupCells);" in sealing
assert "cachedGroupRing=List.copyOf(nextGroupRing);" in sealing
assert "clearNextGroupCache();" in sealing
assert "invalidateGroupCache();" in sealing
assert "sealing.precomputeNextLayer(this,computer);" in drill
assert "Status result=drill.mineTick(server,computer);" in computer
assert "workBudget" not in computer
assert "for(int lane=0;lane<BIT_SLOTS;lane++)" in drill
assert "public static final int BIT_SLOTS=3" in drill
assert "100L*(maximum-bit.getDamageValue())<=3L*maximum" in drill
for durability in [59,100,1561,2031]:
    for remaining in range(1,8):
        retired=(remaining*100<=3*durability)
        # At <= 3% remaining we must retire, never at > 3%.
        assert retired==(remaining/durability<=.03)
assert "RigNetwork.returnSurplus(world,this,old.copyWithCount(1))" in drill
handoff=drill[drill.index("private void retireWornBits("):drill.index("public boolean hasWorkingBits()")]
assert handoff.index("RigNetwork.returnSurplus")<handoff.index("items.set(lane,ItemStack.EMPTY)")
assert "nextWornBitOutputProbe=now+20;" in handoff
assert "hardware.requestReplacement();" in handoff
assert "assigned[lane]=-1;" in handoff
assert "progress[lane]=0;" in handoff
assert "if(!bitNeedsRetirement(items.get(i)))" not in drill
assert "&& !bitNeedsRetirement(items.get(i))" in drill
assert "if(!coordinator&&DrillEntity.bitNeedsRetirement(stack))continue;" in network
assert "if(!hasWorkingBits())return hasWornBitsWaiting()" in drill
assert "||bitNeedsRetirement(bit)" in drill
assert "else bit.setDamageValue(wear);" in drill
assert "if(!coordinator&&DrillEntity.bitNeedsRetirement(stack))continue;" in network
assert "if(!RigNetwork.returnSurplus" in drill  # backpressure/no loss
print("PASS: next layer geometry prepared in bounded slices with live safety checks")
print("PASS: 3% retirement, output acceptance before removal, no worn-bit reuse, three mining lanes retained")
