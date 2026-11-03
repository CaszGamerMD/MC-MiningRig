#!/usr/bin/env python3
"""Regression checks for one-item drill multiblocks, fault lamps and selection locator."""
from pathlib import Path
import json
root=Path(__file__).resolve().parents[1]
java=root/"src/main/java/dev/casz/miningrig"
assets=root/"src/main/resources/assets/miningrig"
data=root/"src/main/resources/data/miningrig"
drill=(java/"DrillBlock.java").read_text()
upper=(java/"DrillUpperBlock.java").read_text()
entity=(java/"DrillEntity.java").read_text()
computer=(java/"ComputerEntity.java").read_text()
renderer=(java/"client/DrillIndicatorRenderer.java").read_text()
rig=(java/"MiningRig.java").read_text()
# Both halves resolve to the ONE obtainable item; the housing has no BlockItem.
assert "new ItemStack(MiningRig.DRILL.asItem())" in drill
assert "new ItemStack(MiningRig.DRILL.asItem())" in upper
assert "drill.copyBindingTo(stack)" in drill and "drill.copyBindingTo(stack)" in upper
assert 'DRILL_UPPER = machinePart("mining_drill_upper"' in rig
assert "private static Block machinePart(" in rig
machine_part=rig[rig.index("private static Block machinePart("):rig.index("private static Item item(")]
assert "BuiltInRegistries.ITEM" not in machine_part
assert "Block.UPDATE_CLIENTS" in drill and "MiningRig.DRILL_UPPER" in drill
assert "level.destroyBlock(head,true)" in upper
assert "!drill.relocating()" in upper
assert "drill.dropInventory()" in drill
head_loot=json.loads((data/"loot_table/blocks/mining_drill.json").read_text())
body_loot=json.loads((data/"loot_table/blocks/mining_drill_upper.json").read_text())
assert head_loot["pools"][0]["entries"][0]["name"]=="miningrig:mining_drill"
assert body_loot["pools"]==[]
# Selected head blockstate is synced as a boolean property for every facing.
head_states=json.loads((assets/"blockstates/mining_drill.json").read_text())["variants"]
assert len(head_states)==12
for facing in ("down","north","east","south","west","up"):
    for selected in ("true","false"):
        assert f"facing={facing},selected={selected}" in head_states
assert "BooleanProperty SELECTED=BooleanProperty.create(\"selected\")" in drill
assert "state.setValue(DrillBlock.SELECTED,selected)" in entity
assert ".setValue(DrillBlock.SELECTED,controllerSelected())" in entity
assert "state.selected=drill.controllerSelected();" in renderer
assert 'String marker="✦";' in renderer and "15728880" in renderer
# Housing warning lamp uses an off model and red flashing model; light emission follows state.
upper_states=json.loads((assets/"blockstates/mining_drill_upper.json").read_text())["variants"]
assert len(upper_states)==12
for facing in ("down","north","east","south","west","up"):
    assert f"alarm=false,facing={facing}" in upper_states
    assert f"alarm=true,facing={facing}" in upper_states
normal=json.loads((assets/"models/block/mining_drill_upper.json").read_text())
alarm=json.loads((assets/"models/block/mining_drill_upper_alarm.json").read_text())
assert normal["textures"]["lamp"]=="minecraft:block/black_concrete"
assert alarm["textures"]["lamp"]=="minecraft:block/redstone_block"
assert "lightLevel(state->state.getValue(ALARM)?8:0)" in upper
assert "state.getValue(DrillUpperBlock.ALARM)!=on" in entity
assert "level.setBlock(body,state.setValue(DrillUpperBlock.ALARM,on),Block.UPDATE_CLIENTS)" in entity
assert "case READY,RUNNING,STOPPED,DONE,WAITING,WALLING,FILLING -> false;" in computer
assert "computer.updateDrillAlarmLights(server,now);" in computer
assert "computer.syncSelectedDrill(server);" in computer
assert "drill.setControllerSelected(false);" in computer
assert "drill.setAlarmLight(false);" in computer
print("PASS: head and housing both pick as one bound Mining Drill; one loot source only")
print("PASS: housing warning lamp blinks red and emits light only for fault statuses")
print("PASS: computer SELECT highlights the drill with a synced full-bright locator")
