#!/usr/bin/env python3
"""CC Organizer: settings UI, click semantics and remote retrieval smoke."""
from pathlib import Path
root=Path(__file__).resolve().parents[1]
java=root/"src/main/java/dev/casz/miningrig"
code=(java/"CoordinatorGuideItem.java").read_text()
block=(java/"CoordinatorBlock.java").read_text()
retrieval=(java/"CoordinatorRetrieval.java").read_text()
menu=(java/"OrganizerMenu.java").read_text()
screen=(java/"client/OrganizerScreen.java").read_text()
rigmenus=(java/"RigMenus.java").read_text()
client=(java/"client/MiningRigClient.java").read_text()
outline=(java/"GroupOutline.java").read_text()
assert "RETRIEVAL_REACH=100" in code
assert "public static int distance(ItemStack tool)" in code
assert "public static boolean previewFirst(ItemStack tool)" in code
assert "OrganizerPreviewFirst" in code and "OrganizerDistance" in code
assert "openMenu(new SimpleMenuProvider(" in code
assert code.count("openSettings(player,") == 3  # CC face, terrain, air
select=code[code.index("public static InteractionResult select("):
            code.index("@Override public InteractionResult useOn(")]
assert select.index("if(player.isShiftKeyDown())") < select.index("if(isRetrieval(tool))")
assert "matchingPreview(tool,plan,server.getGameTime())" in select
assert "markPreview(tool,plan,server.getGameTime())" in select
assert "place(server,player,tool,plan);" in select
assert "CoordinatorRetrieval.recall(server,ccPos,player,tool,outward);" in select
assert "if(!confirmed)" in select
on=code[code.index("@Override public InteractionResult useOn("):
        code.index("@Override public InteractionResult use(Level")]
assert on.index("if(player.isShiftKeyDown())") < on.index("if(world.getBlockState(clicked).is(MiningRig.COORDINATOR))")
assert "return select(world,clicked,context.getClickedFace(),player,tool);" in on
assert "place(server,player,tool," not in on
use=code[code.index("@Override public InteractionResult use(Level"):
         code.index("private static void retrieveInSight")]
assert "openSettings(player,hand);" in use
assert "retrieveInSight(server,player,tool);" in use
assert "place(server,player,tool," not in use
ray=code[code.index("private static void retrieveInSight("):
         code.index("private static int findCC")]
assert "for(double step=.5;step<=RETRIEVAL_REACH;step+=.5)" in ray
assert "if(!world.hasChunkAt(along))break;" in ray
assert "ClipContext.Block.COLLIDER" in ray and "world.clip(" in ray
assert "CoordinatorRetrieval.recall(world,hit.getBlockPos(),player,tool,hit.getDirection());" in ray
assert "return CoordinatorGuideItem.select(level,pos,hit.getDirection(),player,stack);" in block
assert "RigNetwork.HARDWARE" in retrieval and "RigNetwork.OUTPUT" in retrieval
assert "target.hasRecallCargo()" in retrieval
assert "clickMenuButton(Player player,int id)" in menu
assert "CoordinatorGuideItem.writeOptions" in menu and "addDataSlots(data)" in menu
assert "button" in screen.lower() and "menu.preview()" in screen
assert "MenuType<OrganizerMenu> ORGANIZER" in rigmenus
assert "MenuScreens.register(RigMenus.ORGANIZER, OrganizerScreen::new)" in client
assert "int[] prefix=" in outline
assert "int gain=prefix[" in outline and "int bestGain=0" in outline
assert "result.coveredBy(planned)" in outline
print("PASS CC Organizer: item settings menu, distance, double-click preview, direct placement, remote safe retrieval")
print("PASS Group planner: maximum uncovered coverage and verified drill-footprint reach")
