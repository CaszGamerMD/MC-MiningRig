# Caszual Mining Rig — Fabric 26.2

**Version 0.3.2 — new machine models and drill output hatch**

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

Run `gradle build` using Java 25; or download the `caszual-mining-rig-0.3.2.jar` inside the `miningrig-26.2` artifact in the most recent successful GitHub Actions workflow.

**Status:** GitHub Actions compile verification is used, but it does *not* test actual Minecraft interaction, server permissions, chunk reloads, or multiplayer naming/GUI synchronization. Test in a backed-up world and treat this as a development build until in-game verified.
