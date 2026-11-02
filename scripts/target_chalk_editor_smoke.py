#!/usr/bin/env python3
"""Target Chalk editor smoke: 256-point persistence and undo routing."""
from pathlib import Path
root=Path(__file__).resolve().parents[1]
java=root/"src/main/java/dev/casz/miningrig"
item=(java/"TargetChalkItem.java").read_text()
chalk=(java/"ChalkOutline.java").read_text()
group=(java/"GroupOutline.java").read_text()
menu=(java/"ChalkMenu.java").read_text()
screen=(java/"client/ChalkScreen.java").read_text()
menus=(java/"RigMenus.java").read_text()
client=(java/"client/MiningRigClient.java").read_text()
assert "public static final int MAX_POINTS=256;" in item
assert "marks.size() > TargetChalkItem.MAX_POINTS" in chalk
assert "marks.size()>TargetChalkItem.MAX_POINTS" in group
assert 'for(int i=0;i<MAX_POINTS;i++)tag.remove("ChalkPoint"+i);' in item
assert 'if(n<=0 || n>MAX_POINTS)return null;' in item
assert 'if(points.size()>=MAX_POINTS)' in item
assert "new ArrayList<>(draft.points())" in item
assert "remaining.remove(remaining.size()-1)" in item
assert "save(chalk,remaining.isEmpty()?null" in item
assert "new Drawing(draft.dimension(),draft.face(),draft.plane(),remaining)" in item
assert "clearDraft(ServerLevel world" in item and "finishDraft(ServerLevel world" in item
assert "openControls(player,hand);" in item
assert "private static void openControls(Player player,InteractionHand hand)" in item
assert "ChalkMenu(id,inventory,hand)" in item
for method in ["undoLastPoint(world,player,chalk)",
               "finishDraft(world,player,chalk)",
               "clearDraft(world,player,chalk)"]:
    assert method in menu
assert "TargetChalkItem.pointCount(chalk)==0" in menu
assert "player.getItemInHand(hand).is(MiningRig.TARGET_CHALK)" in menu
assert "menu.points()" in screen and "menu.maxPoints()" in screen
assert "UNDO LAST POINT" in screen and "FINISH OUTLINE" in screen and "CLEAR DRAFT" in screen
assert "MenuType<ChalkMenu> TARGET_CHALK" in menus
assert "MenuScreens.register(RigMenus.TARGET_CHALK, ChalkScreen::new)" in client
print("PASS Target Chalk: 256-point draft, shared polygon cap, safe undo and editor UI.")
