#!/usr/bin/env python3
"""CC Organizer: no input should do two conflicting actions."""
from pathlib import Path
code=(Path(__file__).resolve().parents[1]/
      "src/main/java/dev/casz/miningrig/CoordinatorGuideItem.java").read_text()
block=(Path(__file__).resolve().parents[1]/
       "src/main/java/dev/casz/miningrig/CoordinatorBlock.java").read_text()
retrieval=(Path(__file__).resolve().parents[1]/
          "src/main/java/dev/casz/miningrig/CoordinatorRetrieval.java").read_text()
assert "RETRIEVAL_REACH=100" in code
assert 'tag.putBoolean("OrganizerRetrieval",retrieving)' in code
assert 'toggleMode(tool,player);' in code
assert code.count('toggleMode(tool,player);')==3  # select CC, click terrain, use air
sel=code[code.index("public static InteractionResult select("):code.index("@Override public InteractionResult useOn(")]
assert sel.index("if(player.isShiftKeyDown())") < sel.index("if(isRetrieval(tool))")
assert 'boolean same=previous!=null' in sel
assert 'if(same)length=length%MAX_DISTANCE+1;' in sel
assert "CoordinatorRetrieval.recall(server,ccPos,player,tool,outward);" in sel
on=code[code.index("@Override public InteractionResult useOn("):code.index("@Override public InteractionResult use(Level")]
assert on.index("if(player.isShiftKeyDown())") < on.index("if(world.getBlockState(clicked).is(MiningRig.COORDINATOR))")
assert "save(tool,chosen);" in on
assert "place(server,player,tool,chosen)" not in on
assert "right-click air to place" in on.lower()
use=code[code.index("@Override public InteractionResult use(Level"):code.index("private static void retrieveInSight")]
assert use.index("if(player.isShiftKeyDown())") < use.index("if(isRetrieval(tool))")
assert "place(server,player,tool,plan);" in use
assert "retrieveInSight(server,player,tool);" in use
ray=code[code.index("private static void retrieveInSight("):code.index("private static int findCC")]
assert "for(double step=.5;step<=RETRIEVAL_REACH;step+=.5)" in ray
assert "if(!world.hasChunkAt(along))break;" in ray
assert "Vec3 reach=eye.add(look.scale(safe));" in ray
assert "ClipContext.Block.COLLIDER" in ray and "world.clip(" in ray
assert "CoordinatorRetrieval.recall(world,hit.getBlockPos(),player,tool,hit.getDirection());" in ray
assert "if(stack.is(MiningRig.CC_GUIDE))" in block
assert "return CoordinatorGuideItem.select(level,pos,hit.getDirection(),player,stack);" in block
assert "RigNetwork.HARDWARE" in retrieval and "RigNetwork.OUTPUT" in retrieval
assert "target.hasRecallCargo()" in retrieval
print("PASS CC Organizer: unified sneak toggle, select/space/air-place, 100-block unobstructed retrieval and safe storage")
