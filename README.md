# Caszual Mining Rig — Fabric 26.2

**Version 0.4.0 — drill-controlled collection networks, fluid sealing, wall building**

Caszual Mining Rig is a layer-by-layer mining mod with three interchangeable tool-grade bits per drill, bundle-sized item storage, animated wireless Collection Coordinators, and a single computer capable of running **up to four drills independently**.

> The in-game mod name and Gradle artifact name are **Caszual Mining Rig**. The original resource namespace `miningrig` is deliberately retained so existing saves, recipes and placed machines remain recognizable.

## New linking procedure

1. Craft or obtain a **Mining Drill** item; each drill is unstackable so its identity can be tracked individually.
2. **Hold that Mining Drill in your hand and right-click the Mining Computer**. The computer assigns one of four slots and writes the pairing into that exact item. It does **not** consume the drill.
3. **Place the linked drill** anywhere in the same dimension. The computer recognizes it by its persistent pairing token and immediately displays its **X/Y/Z coordinates**, name, status, and estimated completion time. If the computer chunk is not loaded when placed, the drill retries registration when it becomes loaded.
4. **Empty-hand right-click** the Mining Computer to open the new four-drill console.

The old sneak-and-empty-hand linking workflow and separate Rig Linker item are gone.

## Four-drill console

Four drill rows show:
- Drill's nickname (default Drill 1–4).
- Placed drill position in `X,Y,Z` format (or **Not placed**).
- Mining / ready / stopped / completed / out of bits / full storage / blocked / missing drill / unloaded chunk status.
- A **rough ETA** based on the programmed footprint, depth, installed drill-bit speed and actual progress. The estimate is refined as mining proceeds; block hardness, air pockets, tool damage and pauses can change it.

Click **Select** next to a drill to edit its settings or nickname. Click **Start/Stop** in the corresponding row to run or pause that drill independently. Use **R-/R+** for radius (1–12), **D-/D+** for depth (1–256), and **Shape** for circle/square. Type in the **Nickname** field and click **Save name**. A slot can be freed using **Unlink**.

Every drill has its **own** settings, running flag, error state, and tracked position. The computer's physical indicator flashes red whenever a registered drill has a reported halt/error. The on-face readout summarizes working drills and alerts.

### ETA caveat

ETA is shown as an estimate, not a guaranteed finish time. It begins with approximate tool speed and becomes based on actual per-cell progress after digging. It shows `--` if no usable bit is present, and zero when the configured depth has finished.

## v0.4.0: automatic CC networks, filler inputs and walling

This development build replaces the old coordinator IN/OUT six-port network-direction logic with **automatic bidirectional CC-to-CC links**. CCs still communicate along the six cardinal axes (up to 20 blocks) but route packets toward a configured external output without creating ping-pong loops.

### Color-coded connection editor

Right-click a CC and select one of its six direction boxes: **U, D, N, S, E, W**. The box shows the connection's role:

| Color | Connection |
| --- | --- |
| Teal | Auto-linked drill or neighboring CC |
| Red | External inventory: Filler input |
| Yellow | External inventory: Wall input |
| Green | External inventory: Output |
| Gray | Disconnected or unassigned |

For an attached non-CC chest/container, click **Type** to cycle **Unassigned → Filler → Wall → Output**. When set to Wall, click **When Empty** to switch between **Pause** and **Continue**. Connected CC links are automatic and cannot be manually marked as item-direction-only; attached non-CC inventories are always one-way.

Filler and wall inputs are **never automatically vacuumed**. A drill requests specific blocks as needed, and the network tries the **closest stocked input container first**, then other inputs in the same discovered CC component. Outputs are identified by CC faces set to Output. A drill must have an accessible Filler input and Output and, in Always mode, a Wall input **before starting**. Removing them mid-operation halts the drill with an error.

### Fluid and wall behavior

Each working layer is scanned for fluids inside the mining region and **up to one block outside** its selected boundary. When fluid is seen, the drill suspends its excavation, requests solid filler blocks from the network, and replaces source/flowing fluid. It waits **8 game ticks after each placement** to check that a full solid block remains; if sand/gravel fell, or fluid moved away leaving air, it fills again. It will not proceed while unsealed fluid spots remain. If all configured Filler input containers run out while fluid remains, the computer reports **Out of Filler / Fluid in hole**. Upon recovery, the computer's indicator can briefly blink yellow as a fluid caution.

The Mining Computer's **Wall** button cycles its per-drill **Wall the Hole** setting:

- **Off:** Only repair actual fluid locations and nearby fluid boundaries.
- **Air:** After each layer, fill every air/fluid opening in the one-block-wide outer wall with Filler blocks.
- **Always:** Excavate one block beyond the chosen selection on every layer, then build the boundary using the separate Wall input.
  - **Wall → Pause:** If all wall inputs are exhausted, halt with **Out of Walls**.
  - **Wall → Continue:** Fall back to Air-style filling using Filler stock. The next untouched layer checks whether Wall material has been replenished.

When the drill descends, **bit ownership and stored items move to the next block before the old block is replaced**, preventing the moving block from dropping/duplicating its bits. This is an important regression to check during gameplay.

Protected Mining Drills, both drill halves, Computers, and Collection Coordinators are ignored rather than mined. Unbreakable/unharvestable blocks remain blocking obstacles.

### Compact visual item transport

Drill-to-CC and CC-to-CC wireless flights now carry **no more than 3 individual items per packet**, even when those are three different item types. Each visible ItemDisplay uses the **GROUND presentation at one-quarter scale**, has no pickup hitbox, and remains an animation only. CC-to-adjacent-output transfers also limit each operation to 3 items. Multiple flights still run concurrently: up to **four 3-item pickups (12 items) per game tick** from a drill and **eight 3-item wireless sends (24 items) per tick** per CC, before receiver capacity and chunk availability are considered. Display entities are capped at **48 per coordinator** to protect rendering performance; packets beyond that cap still travel and deliver safely. They retain their full cargo in NBT until delivered.

While mining, the drill's footprint, computer and **already-discovered CC network corridors and adjacent inventories** use temporary simulation chunk tickets; tickets are released on stop. As before, network discovery must encounter loaded chunks when starting.

### Important early-development limitations

- **Inputs are consumed directly by the requesting drill from a network-connected input inventory.** The visible flight animation currently covers mined-item output (drill→CC→CC), not the reverse Filler/Wall request. No unsent filler supply is buffered at the drill; it requests one block just before placing it.
- Fluid sealing currently checks one horizontal layer at a time and does not actively drain source liquids outside the one-block boundary.
- Minecraft in-game behavior (especially fluid updates, falling-block placement, multiplayer and moving-machine drops) has **not** been verified by a compiled build alone; test on a backed-up world.

## v0.3.3 visual and interaction fixes

- Lower drill and centered coordinators now use **non-occluding model properties**, so they no longer conceal surrounding terrain and create a square x-ray hole.
- Regenerated drill cone/housing and monitor geometry remove coplanar overlapping faces that produced flicker and texture fighting.
- Visible drill-bit dots now sit **on** the lower drill's metal collar and are properly depth tested, rather than showing through geometry. The computer text is also depth tested and rendered only when looking at the actual front face.
- Clicking either half of the drill opens its inventory, unless a held drill bit is being installed. Clicking any normal held item on a coordinator opens its inventory. A computer now has a **clickable upper screen target**, not just an upper model extending beyond its one-block click area. Holding a drill item still pairs it to the computer; normal held items and empty hands open its console.
- Computer upper interaction blocks automatically appear on pre-existing computers where the space above them is air.
- All six drill bits use larger **3D broad shovel-head/spade bit models** instead of reused vanilla pickaxe sprites.

## Physical machine designs

- **Mining Drill:** occupies a real two-block-tall vertical space. The lower block has a stepped conical iron drill bit and three visible colored dots that follow the materials of the bits installed in the inventory. The upper half is a weathered yellow machine housing with a **recessed top output hatch, metal lip, rusted lid, and handle**. A top hatch indicates the drill's upward wireless unloading direction; it is cosmetic, and mined items still transfer through the coordinator above without requiring the hatch to be opened.
- **Mining Computer:** 1.5-block-tall workstation silhouette, with a raised dark screen, metal bezel, keyboard tray, and status lamp. The screen's status text renders higher to match the display.
- **Collection Coordinator:** compact centered metal box. Any actually connected side set to **IN** renders a flared hopper/intake nozzle; connected sides set to **OUT** render a short metal pipe. The same cues apply to both adjacent containers and other wireless coordinators. Connections are calculated from the server state.
- The visible structures use built-in Minecraft textures, retaining the existing `miningrig` item and block IDs for world compatibility.

## Drill behavior

A drill has **three independently working bit slots** (wood, stone, iron, gold, diamond and netherite) and can mine one block per working bit concurrently at a tool-grade break rate. Enchantments apply through block loot and bit wear: Efficiency, Fortune, Silk Touch, Unbreaking. The drill stores up to **64 individual mined items**, shown over inventory pages. It descends one Y layer after clearing the configured footprint.

- Empty-hand right-click drill: drill inventory.
- Right-click drill holding a drill bit: install into a free slot.
- Drills stop when there are no working bits, when their buffer reaches 64 items, when digging encounters a protected/unbreakable or unharvestable block, or when the job is complete.
- The computer tracks the drill's changing Y coordinate as it moves.
- **While mining is active**, each drill requests temporary simulation chunk tickets for every chunk its configured mining footprint intersects (radius 1–12). Its **Mining Computer's own chunk** stays active as well, so remote computer ticks do not stop when players walk away.
- **When stopped**, finished, blocked, out of bits, out of storage, unlinked, or when the computer is destroyed, all tickets belonging to that rig are released. Tickets are reference-counted across active computer owners, so one rig cannot release another rig's chunk.
- Tickets are **temporary, not vanilla permanent force-loaded chunks**. After a full server restart, a computer must first be loaded normally to resume a saved running job; once it ticks it reapplies the working-area tickets.

## Collection Coordinator

One Collection Coordinator block handles all wireless item routing. Its internal inventory stores **192 individual items**, with pages for mixed item types and inventory dropping on destruction.

- CC ↔ CC automatically detects the first coordinator on each cardinal direction up to **20 blocks**, and its six directional GUI controls can select **IN/OUT/OFF**.
- Drill → CC can transfer wirelessly to a coordinator **directly above the drill** at matching X/Z coordinates, up to **20 blocks** vertically. Set the coordinator's DOWN side to IN; it glows blue when the drill is detected.
- Items visibly float as non-interactable item displays. A 20-block hop still takes approximately 2.5 seconds.
- **Pipelined throughput:** each packet can transport up to **64 items of mixed types** while showing one representative floating item. The coordinator can pull **up to 64 mined items every server tick**, dispatch **up to 4 wireless packets (256 items) per tick**, and attempt **32 direct-inventory insertion operations per tick**, when inventory space, destination chunk loading, and receiving containers allow. The 2.5-second animation does **not** serialize transfers one at a time.
- Up to **128 packets may be in flight per coordinator**. Their contents and destinations are saved with the coordinator and drop on its destruction. A blocked/full destination holds packets rather than deleting cargo; backpressure still eventually stops a drill if downstream storage cannot accept items.
- Adjacent chests and inventories accept items directly from a CC OUT side, without wireless transport.

## Developer build

Minecraft **26.2**, Fabric Loader **0.19.5**, Fabric API **0.161.0+26.2**, Java **25**, Gradle **9.7.0**, Loom **1.18.3**.

Run `gradle build` using Java 25; or download the `caszual-mining-rig-0.4.0.jar` inside the `miningrig-26.2` artifact in the most recent successful GitHub Actions workflow.

**Status:** GitHub Actions compile verification is used, but it does *not* test actual Minecraft interaction, server permissions, chunk reloads, or multiplayer naming/GUI synchronization. Test in a backed-up world and treat this as a development build until in-game verified.
