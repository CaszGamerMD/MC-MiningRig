# Caszual Mining Rig — Fabric 26.2

**Version 0.6.1 — connected sideways drill assembly**

Caszual Mining Rig is a layer-by-layer mining mod with three interchangeable tool-grade bits per drill, bundle-sized item storage, animated wireless Collection Coordinators, and a single computer capable of running **up to four drills independently**.

> The in-game mod name and Gradle artifact name are **Caszual Mining Rig**. The original resource namespace `miningrig` is deliberately retained so existing saves, recipes and placed machines remain recognizable.

## v0.6.1 — Correct horizontal drill model and structure

- **Rotate the entire two-block drill.** The mining head leads, and the yellow housing sits one block **behind** the head along the drill's travel axis, with its model rotated identically. A sideways head no longer dangles beneath an upright yellow housing. The down-facing 2-block assembly stays unchanged.
- Directional **bit/housing GUI access**, installing bits, destroying either half, and dropping stored inventory now use the actual orientation. When the drill moves, the old head becomes the new housing, preserving data and eliminating detached blocks.
- Existing v0.6.0 horizontal machines whose housing is still above the head auto-migrate the housing to the rear only when the rear cell is **loaded and replaceable** and matching old housing. Otherwise mining safely pauses rather than overwriting an occupied block; clear the space or re-place the drill.
- Wireless CC network repair avoids both occupied machine cells; horizontal tunnel diameter/shape, filler, wall, hopper upgrade, bits, and distance-in-blocks Depth controls remain unchanged.
- Built with Fabric 26.2; run-time testing in a backed-up world is recommended.

## v0.6.0 — Horizontal drilling

- When placing the **Mining Drill** against the side of a block, it drills **into the clicked wall**. Placing on the top of a block keeps the original **downward** mode. Placing underneath a block (which would drill upward) is disabled.
- The drill's **Depth** field on the Mining Computer now means **distance traveled in blocks** for both modes. Radius and Square/Circle shape still define the excavation cross-section. A horizontal drill cuts a **vertical** circular/square tunnel with the same width logic.
- Each horizontal step executes the same **boundary walls → fluid sealing → core mining → advance** stages, reusing the existing filler, gravity checks, mining bits, automatic CC routing and hardware replenishment, output, alarms, chunk tickets and hopper upgrade.
- The two-block-tall rig travels intact one block into the wall after the next tunnel slice is cleared. It preserves linked computer token, drill settings, bit wear, output contents, wall/filler inventory, supply flights, hopper upgrade and progress. Obstructions in either upcoming housing cell are removed with a bit before advancing, with normal drops and capacity checks.
- Drill-bit cone models rotate to indicate the selected horizontal direction; the top housing displays a directional marker. The Mining Computer preview outlines the **vertical slice** ahead of the drill, and its location column includes the direction.
- No upward drilling. Existing placed drills default to downward-facing via their block state migration. Tests in a backed-up world are recommended before running in large caves or under oceans.

## v0.5.3 — obstacle-aware bottom-up gravity fill

**Support detection.** Sand and gravel continue to scan downward up to 32 loaded blocks to find a genuinely sturdy floor, then work upward. The support check uses the actual sturdy top face of the block: a top slab that supports falling sand (including a waterlogged top slab) is a valid foundation and will not be unnecessarily destroyed.

**Partial-block cleanup.** Where the requested fill position or the shaft beneath it contains a non-full obstruction such as a torch, rail, bottom slab, or trapdoor, the drill checks that a reachable safe foundation exists, then breaks the lowest obstructing partial block before filling. It uses an installed drill bit with hardness progress, enchantment-based speed and normal bit durability. The drops are inserted into the drill's standard 64-item Output buffer for CC transport. If Output is full, excavation pauses rather than losing drops. Waterlogged partial blocks leave their fluid behind for the fill pass.

**Safety.** Machine blocks, unbreakable/protected blocks, block entities, unloaded chunks, and shafts without a safe support floor are not automatically excavated. When safe filling is impossible the drill reports Fill blocked instead of discarding sand/gravel.

**Validation.** Compiled for Fabric 26.2; runtime tests are still needed for modded partial blocks and underwater fill.

## v0.5.2 — Drill hopper upgrade and stable falling-block placement

**Hopper upgrade:** The Mining Drill now has a **dedicated hopper slot** beside its three drill bits. Install one ordinary vanilla hopper to activate nearby dropped-item collection. Every five server ticks, it scans a bounded region around the drill (at least four blocks horizontally and up to the configured radius plus one, capped at fourteen), and pulls compatible item drops into the drill's existing **64-item Output** buffer. It accepts partial stacks without deleting the rest of an entity; an incompatible/full buffer leaves the items safely on the ground. When CCs empty Output, the upgrade can resume collecting. The hopper is not output cargo, never counts against the 64-item buffer, persists on save/reload and travels with the drill during descent. Remove the hopper to turn the collector off; it is returned as an item when the drill is broken.

**Safer wall and fluid filler:** Bit speed and Efficiency still influence placement, but normal filler is slower and gravity-affected sand/gravel has a substantially slower rate and a longer verification pause. When a falling block has no sturdy support, the drill first uses a stocked non-falling filler block if one is available. Otherwise, it builds a supported column bottom-up (up to 32 loaded blocks deep), verifying each foundation block before approaching the original target. If no safe foundation is available, the drill reports **Fill blocked** at the target rather than throwing sand or gravel down a bottomless shaft. Verification now waits five ticks for ordinary blocks and at least eighteen for falling blocks; fluid sealing and the original per-coordinate retry ceiling remain in effect.

**Verification:** GitHub Actions compiles and packages the Fabric 26.2 mod. Test hopper suction, sand/gravel settlement under water, sided CC extraction, and drill descent in a backed-up world before depending on the changes.

## v0.5.1 — output failover, supply priming, JEI bounds and visuals

**Output failover.** CCs no longer route items to the first configured Output regardless of capacity. Routing inspects the actual items and sided destination inventory slots, including compatible partial stacks, empty slots, stack limits, and sided insertion permissions. A full or incompatible output is skipped immediately in favor of another reachable one. If a packet's destination CC becomes blocked after dispatch, the packet is held safely, returned to its sending CC when possible, or rerouted via an alternate CC after a short stall. No item is discarded due to lack of space. A failed-routing scan has a short five-tick cooldown to prevent a full CC from repeatedly scanning the entire network.

**Placement speed.** Wall blocks and fluid filler placements both use the installed drill bits' mining speed and Efficiency enchantments, with a 3x placement-work multiplier relative to a stone block. Normal solid blocks only need two ticks of stability verification between placements instead of eight. Sand, gravel, and other falling blocks still receive longer checks for real gravity settling.

**Immediate priming.** Pressing Start on an individual drill or Start All immediately begins replenishing that drill's independent 64-item Filler and 64-item Wall buffers. Priming continues every mining tick, including while the mined-item output or drill-bit supply is temporarily stalled. Wall buffering runs when replacement-wall mode is selected; other modes use the Filler stock.

**Computer and JEI.** The full-screen computer reports its actual occupied screen rectangle to Minecraft's menu system, rather than the old fixed 460x330 space. This prevents JEI from treating the computer panel as empty overlay space and painting ingredient lists over its controls.

**Stuck CC cargo visuals.** Animated CC transfers and CC prefetch shipments now tag their transient display entities with the coordinator that owns them. Orphans left by save/reload, interrupted transfers, or a removed CC are automatically pruned while the item cargo stays in machine inventory/NBT. Older untagged no-gravity block packet displays directly against CC faces are also cleaned after a grace period. Cleanup runs staggered across CCs to minimize spikes.

**Verification:** Minecraft 26.2 / Fabric compilation succeeds, but mixed modded sided inventories, JEI's in-game overlay, world-reloaded legacy ghost displays and concurrent drilling speed require a backed-up-world test.

## v0.5.0 — CC networking, automatic hardware servicing, and full-screen computer

**Automatic discovery.** Place a Mining Computer within 24 blocks of a loaded Collection Coordinator, and it will discover and assign up to four drills near **any loaded CC in the same connected CC network**. Drills no longer need item pairing or manual linking. Each drill has a persistent identity even as it moves down. Discovery is limited to loaded chunks; old assignments survive save/load and drill relocation. Use **Rescan** if you previously removed a drill assignment; removing one assignment does not reset the drill's own excavation. A separate **Reset Task** clears that drill's layers and cursor without breaking it or removing its bits.

**New computer console.** The interface expands to the screen size in a black/green theme, with Start/Stop per drill, Start All, Stop All, a current-alert mute, selective task resets, Shape/Wall buttons, and editable Radius (1-12), Depth (1-256), and Fill Tries (1-4096). Press **Apply** to submit all three numbers to the server with range validation. Focused text boxes swallow inventory and other gameplay shortcut keys; drill nicknames have a separate Save Name button.

**Network Storage page.** Each connected CC face can assign a **container nickname**, up to 32 characters, which is saved per CC face and shown in its GUI. The computer and linked tablet display connected container names, source coordinates, assigned type (Filler, Walls, Output, or Hardware), and live item count/estimated stack capacity. The CC graph and its loaded adjacent inventories are the source of truth; no forced loads for offline containers. Storage pages support more than ten containers.

**Hardware port.** Each CC face gains **Hardware** (after Output in the input mode cycle). A Hardware container supplies **drill bits** and **Collection Coordinator blocks only**. At the **start of every mining layer**, the drill queues a bit for **each empty bit slot** (up to three), including slots that were intentionally left empty. It also queues one replacement immediately whenever a bit breaks. Orders remain pending until suitable Hardware stock becomes available, but existing working bits keep excavating while the new bit travels to the drill. When all three bit slots are empty, the computer stays in its running state and the drill waits for restock rather than timing out. Deliveries do not reserve beyond the number of actual empty slots; a player's manually installed bit cancels any excess request. Pausing returns in-flight hardware to its source container and keeps the remaining demand for when the drill resumes. These queue and per-layer request markers survive saves and drill descent.

**Auto network repair.** When a running drill descends beyond the 24-block CC network, the computer enters **Reconnecting CC network**. It searches from the last connected CC for a loaded, empty, straight-line valid position near where the drill last had connectivity; requests one CC from Hardware; animates its transit and places it to restore a CC-to-drill connection. Successful restoration resumes mining automatically; failure after a finite timeout produces a repair-failure alert. The network's CC chunks remain loaded during this recovery. Existing construction and block ownership are not overwritten.

**Shared alarms and sounds.** CCs play a quiet beacon-like hum; active drills play a grindstone-like drilling sound. These are now server-played vanilla sound events, so the game uses their ordinary sound files and subtitles rather than relying on chained mod sound event references. Unmuted computer problems cause a repeating beep on the computer and on linked placed/carried tablets; **Mute Alert** silences the current problem for all terminals without disabling future alert events.

**Tablet item:** the handheld and inventory variants now use a slim, paper-like front-facing tablet icon/model with a minimal frame, pale screen, and colored alert lamp, independently of the placed tablet's 3D model.

**Multi-drill CC supply:** a CC now fairly collects mined output from multiple nearby drills and maintains its wall and filler depots while any of its served drills needs stock, rather than restricting the network to one drill directly underneath.

**Playtesting needed:** Java/Fabric CI verifies compilation and assembles the JAR, but real-world alert subtitles, GUI scale, multiple simultaneous drills, hardware item travel, loaded-chunk discovery, protected areas, and auto-placed CC geometry require testing in a backed-up world.

## v0.4.11 — three 64-item drill bundles and wall ordering

Each mining drill now maintains three **independent** capacities, all capped at **64 individual items**:

- **Output (64):** mined blocks and drops only, transferred up to Collection Coordinators.
- **Filler (64):** valid sealing blocks for liquid repair and Air-mode perimeter patches.
- **Wall (64):** valid wall blocks for Replace mode. Separate from both Filler and mined output.

Open the Mining Drill and use **Next storage** to switch between the three paged bundle views (up to eight pages each). The interface reports each storage's own used capacity. The wall selector is **Wall: Order** by default. **Wall: Random** picks from a different available wall input stack when possible, randomly selecting **one block per source draw** rather than depleting the first stack; wall placement also randomly samples the ready wall bundle, avoiding the previously used packet when an alternative is available. The chosen mode is saved on the drill and travels with it as it descends.

**While running**, Filler continually requests **64 total ready/in-flight blocks**, and Wall does the same in Replace mode. The nearest CC maintains separate upstream Filler and Wall depots (up to 192 each) to keep both drill buffers fed even while other phases are active. Unlike older versions, the buffers are **not emptied at each completed wall or fluid phase**.

**When paused or stopped**, the drill refunds unused reserve packets and immediately tells its nearest CC to refund its supply depots before chunk tickets are released. Unused supplies return to their **original input inventories first**, with overflow routed through configured **Output** CCs; the existing safe fallback behavior prevents item loss when all destinations are full. Switching Wall: Order/Random also returns the outstanding Wall stock before refilling according to the new policy, without clearing Filler.

**Validation:** Fabric 26.2 GitHub Actions checks compilation. In-world throughput, mixed-wall patterns, pause/resume refunds and GUI stack interactions should still be playtested with backup worlds.

## v0.4.10 — continuously stocked Wall + Filler depots

Both **Replace walling** and **fluid sealing** now use the same fast, two-stage delivery strategy:

- The **nearest Collection Coordinator** above each actively working drill now has *two independent, persistent supply depots*: up to **192 Filler** and up to **192 Wall blocks**. These reserves are separate from the coordinator's 192-item mined-output inventory. The Wall depot is capped to the **remaining outside-ring block count minus stock already reserved at the drill**, so small rings do not pull hundreds of unnecessary blocks. The CC stages Wall supplies **while the drill is still excavating the outside ring**, rather than waiting until the wall has been cleared.
- The drill keeps its own **64-item Filler** and **64-item Wall** supply buffers (including ready and incoming reserved blocks). In Replace mode, the drill requests the number of remaining wall positions, capped at the 64-block buffer; replacement starts from material already on its way. Fluid sealing keeps Filler at 64, while **Air-mode wall repairs also use that same 64-block Filler reserve** instead of requesting one block at a time.
- Both CC and drill buffers begin replenishing as soon as a shipment is taken or a placed block consumes stock. Wall shipments already heading into the closest CC are not double-reserved directly from input chests; Air-mode walls only pull Filler when there are actual voids to repair. Each visual packet holds **at most 3 items**, with multiple independent flights. The incoming network flight time was shortened from approximately **2.5 ticks per block to 0.8 ticks per block** (minimum 2 ticks per leg), so a 20-block link takes around **16 game ticks**, rather than 50. The items remain visibly animated, uncollectable in flight, and use the 55%-size ground-model display.
- Filler and Wall packets retain source-container coordinates, insertion side, inventory ownership and flight progress through saves, unloading, and drill descent. Surplus supply tries the **original input container** first, then a reachable **output**, then the safe drill-inventory/world-drop fallback. CC depots stop restocking when a drill stops or changes to a different work mode.
- The CC GUI now shows **F: N/192** and **W: N/192**, allowing live inspection of both depots. Normal CC storage is still displayed separately.

The mine's execution order remains **clear Replace wall → place each wall and verify → seal outer fluids → seal inner fluids → mine the layer**. The configurable per-position Fill Attempts limit is unchanged.

**Testing:** use a backup world with a deep drill, multiple CC hops and separate Wall/Filler input chests. Verify prefetch during wall excavation, 64-block drill stock while placing, both 192-block CC depots, shorter visible flights, Pause/Continue when Wall stock runs out, and leftovers returning to their original chests. CI validates compilation, not actual in-game throughput.

## v0.4.9 — CC Placement Guide

A new craftable **CC Placement Guide** assists with laying out Collection Coordinators so their existing *automatic 20-block straight-axis wireless links* line up without trial-and-error. Available in the **Redstone** creative tab.

**Crafting:** Iron–Comparator–Iron / Amethyst–Redstone–Amethyst / Empty–Stick–Empty.

**How to use**
1. Hold the Guide and **right-click a face of an existing Collection Coordinator**. That locks the face and X/Y/Z axis. Unlike normal right-clicking, this selects the guide instead of opening the CC inventory.
2. An **END_ROD particle beam** shows the 20-block wireless axis. Eight faint particle corners outline the proposed placement (bright when valid, smoke when blocked). Only visible while holding the Guide and when the chunks are loaded.
3. **Sneak + right-click air** to change the placement distance by one (1 through 20, wrapping). **Right-click air normally** to place a Collection Coordinator at the outlined location.
4. Alternatively, **right-click a nearby ordinary block** along the beam to select the corresponding distance and immediately place the CC *snapped exactly to the wirelessly connected line* (up to four blocks' lateral correction).
5. In survival, the tool consumes **one CC from your inventory** for each placement; in creative it doesn't consume any. After placement, the guide automatically switches its anchor to the newly placed CC, preserving the chosen distance so building straight CC chains is quick.

**Safety and restrictions:** The guide never overwrites solid blocks or block entities. It enforces the 1–20 range, current dimension, loaded chunks, world interaction permissions, and proper one-to-one network discovery. It rejects a proposed link if an existing nearer CC would intercept the wireless connection or if an attached storage container occupies either link face. Air, water and other replaceable positions can be targeted; the original CC auto-link and routing code is unchanged. If a target is blocked, try another spacing or reposition the tool's selection.

**Playtest** on Minecraft 26.2 Fabric: select each of the six CC faces, place several CCs precisely on their axes, confirm teal link panels and actual item transfer, test walls/solid blocks between wireless endpoints, place in water, check survival CC consumption, and verify rejection when a chest occupies a wireless face. CI compilation does not replace in-game visual/gameplay testing.

## v0.4.8 — quiet machine sounds

- **Collection Coordinator:** A very soft, low electrical ambient hum plays from the CC's position while it is loaded, using the **Blocks** audio slider. Nearby CCs have staggered playback phases so they do not all restart simultaneously. Default sound volume is **10%** of normal.
- **Mining Drill:** A subdued mechanical grinding sound starts during **actual block excavation** (including clearing wall blocks in Replace mode), repeats at most once every **24 ticks**, and moves with the drill as it descends. With all three drill bits running, the machine still plays only **one sound bed**, not three overlapping copies. Sound volume is **36%** of normal.
- **No idle drilling:** The drilling sound does **not** start while the drill is paused, stopped, out of bits, verifying placements, or waiting for Filler and Wall stock. Playback naturally finishes when cutting stops.
- **Sound sources:** This first pass references compatible, built-in Minecraft beacon-ambient and grindstone sounds through registered mod sound events, so it requires **no external resource pack** or extra dependencies. The source effects can be replaced by original recordings later without changing the machine logic.
- **Audio controls:** Both are positional and respect Minecraft's **Blocks** sound slider. Each sound has a subtitle for accessibility.

Please check the balance around several connected CCs, next to a digging drill, during ocean fluid recovery (drilling should stop), and when the drill descends. GitHub Actions verifies code compilation but not perceived audio loudness.

## v0.4.7 — Fixed handheld tablet and two-stage filler buffering

### Handheld tablet texture repair

The handheld tablet's red/yellow item models now inherit the proven dark handheld model and override **only the lamp material** with built-in Minecraft redstone/yellow textures. This removes the custom animated texture lookup that could produce a large black/magenta missing-texture rendering. **The placed tablet remains unchanged**, including its animated computer-linked status indicator. Handheld red/yellow indicators are steady colors in this compatibility-first fix (they still change when the linked computer's severity changes); this avoids the old whole-item jitter and broken sprites.

### Drill + Collection Coordinator both stock Filler

When the nearest Collection Coordinator detects that its linked drill is actively **Sealing fluids**, it switches on its own **192-item dedicated Filler depot**. The drill independently maintains its **64-item Filler supply**. These dedicated buffers do **not** take over the normal mined-item/output inventories:

- The **nearest CC only** reserves Filler from connected input storage, fills its depot using **up to eight 3-block incoming packets per tick**, and maintains up to **32 simultaneous supply flights** (with bounded cosmetic visuals). Shipments can traverse connected CCs before reaching the depot.
- The drill draws preferentially from **this ready CC cache**, in ordinary animated 3-block CC→drill packets, and replenishes its own 64-block supply immediately as room becomes available. If the CC cache is not yet filled, the original direct source→drill supply path remains available.
- A pending depot shipment counts as **real available incoming Filler**, avoiding a premature Out of Filler status while items are still on their way to the CC. All requested cargo is preserved in block-entity save data through restarts and drill movement.
- Each cached and drill-ready stack tracks **which input container and face it originally came from**. When sealing finishes, surplus Filler first attempts to **merge back into that source inventory**, respecting container capacity and sided input rules. If the source is gone or full, material goes into a CC with a valid path toward a configured **Output**. If output storage also cannot accept it, it falls back to the drill's inventory or a dropped item rather than vanishing.
- A cache automatically drains/refunds when the filling drill disconnects, stops filling, or is removed. These are **real stored items**, not creative-mode generated stock.

The fluid repair order remains **outer liquid wall → interior liquid cells → resumed mining**. Individual coordinates must verify as solid, and the configurable per-coordinate Fill Attempts limit remains supported.

**Development testing:** in a backup ocean world, try Filler inputs feeding multiple CC hops, watch both buffers refill after each placement, then stop the water flow and verify unused material returns to its input chest first. Test with that input chest full to confirm the Output fallback. Verify the handheld tablet no longer shows black/magenta.

## v0.4.6 — ocean mining: wall-fluid → interior-fluid → excavation

An ocean mine can repeatedly refill its excavated space. The new **fluid recovery order** is:

1. **Detect fluid** in the working layer or the one-block surrounding ring, regardless of Wall setting.
2. **Seal the outer ring first.** For each fluid/air target, request a Filler block, place it, then **verify that very same coordinate is solid** after falling-block and water updates. Retry that coordinate until solid or its configured safety limit is reached; do **not** advance to a different coordinate prematurely.
3. **Seal the interior second.** The drill examines every fluid location inside its selected mining footprint and applies the same one-coordinate-at-a-time placement and verification. A fresh scan can return it to the outer wall if water spreads back.
4. **Resume mining** only after the full ring and mining area have been verified solid. The 64-Filler ready/in-flight stock stays replenished during the entire recovery operation; unused material returns to the network afterward.

The existing normal **Wall → Fluid → Mining** execution order remains: Replace walls can still be mined and reconstructed ahead of fluid management. For fluid management specifically, the outer wall is sealed before the hole interior even if Wall Mode is Off.

### Computer setting: Fill Attempts

In the Mining Computer (or linked tablet's **same computer GUI**), select a drill and use the new **Fill tries / block** controls. Adjust by **±1** or **±25**, with a **default of 24** and supported range **1–4096**. Each of the four drills stores its own independently adjustable limit. It is saved to the computer and takes effect immediately without restarting or resetting the current excavation.

The attempt counter belongs to **one X/Y/Z position**, never the entire wall ring, fluid phase, or drill depth. A 50-block shaft with sand falling through the water can therefore use a higher limit (for example, 100) without raising the limit for the other three drills. Deep gravity shafts receive additional settling time, but the configured number—not an automatic arbitrary threshold—is the cap.

If the drill stops at the limit, the screen identifies **the blocked coordinates**. Increase Fill Attempts beyond the current count to resume automatically, or press Start/Stop to manually retry the exact same cell. If someone repairs the target with a lasting solid block, the drill can detect that and resume. The retry count and target coordinate survive saves/reloads.

**Test on a backed-up ocean world:** start with a high limit, plentiful Filler storage and an output; watch the drill plug ocean-side boundary fluid first, then interior water, staying on each cell until verified solid. A falling sand/gravel shaft deeper than 24 should progress with a suitable higher limit instead of triggering the old stuck error.

## v0.4.5 — correct coordinator vertical link and handheld indicator animation

- **Vertical Collection Coordinator faces:** Corrected the Minecraft blockstate X rotations on every up/down attachment: the side with a linked drill **below** now shows the teal link on the **bottom** face, not on top. An upward-facing CC connection is displayed on the top. This also fixes up/down orientations for filler input and storage output models.
- **Mining Tablet handheld light:** Removed the old `minecraft:time` **random-range model swap** for red/yellow states. That random selection produced irregular whole-model flickering which resembled Z-fighting. The handheld model stays *completely still*, and **only its lamp's dedicated pixel texture animates**, alternating 10-tick OFF/ON frames (a 20-tick cycle), like the placed Mining Computer's status blink. The installed tablet still uses its separate blockstate-driven lamp animation.
- **Zero block/item ID changes.** A previously linked tablet and existing CCs work without conversion. Java 25 / Minecraft 26.2 Fabric build verified by CI; in-game shader and multiplayer rendering still need a playtest.

## v0.4.4 — Clear every wall block before material deliveries

The **Replace** mode now has two strict stages on each mining layer:

1. **Wall excavation:** The drill clears **all non-air/non-fluid blocks in the one-block-wide outside ring** using real drill bits, honoring mining speed, tool tier, wear, unbreakable blocks, and storage. **No Wall supplies are requested or placed during this stage.**
2. **Wall replacement:** After the whole ring is clear, the drill counts the **actual number of wall blocks needed** and asks the connected network for that quantity. Nearest stocked Walls inputs reserve blocks as **three-item packets**, with up to **four packets launched per server tick**, **12 concurrent trips**, and a **48-block ready-plus-in-flight prefetch target**. Items remain non-pickup animated ground models along each CC route. The drill places the blocks using the existing **1.3x tool time/durability** and eight-tick block verification while deliveries are already arriving.

If a new solid block falls into the ring after the clearance stage, the drill returns to clearing that obstruction instead of silently overwriting it. **When Empty → Continue** still switches remaining unfinished wall positions to Air-style Filler repair; **Pause** stops with **Out of Walls**.

### Fluid filling: stay stocked until fully sealed

If any fluid is found on the current layer or its one-block outer boundary, the drill enters **Sealing fluids** mode. Mining stays suspended, and the drill requests a separate, persistent **64-block Filler supply buffer**, continuously replenishing it from the nearest configured Filler input. The 64-block cap counts **blocks already inside the drill plus reserved packets still in transit**, so it does not over-pull from storage. Each input shipment still contains no more than three items and is visible along the network.

The drill remains in this filling mode through all flowing-fluid changes, gravity/falling-block rechecks, and eight-tick solidity verification. It cannot restart mining while detected spots remain unsealed. If every Filler container is exhausted *and the drill's supply buffer is empty*, it stops with **Out of Filler / Fluid in hole**. The previous **24 placement retries per position** safeguard remains.

Once **every** leak is confirmed filled, spare Filler blocks are sent into the connected CC output network (or retained in drill mined-item inventory if the network is full, with a safe dropped-item fallback rather than deleting material). Only then does the computer status change from **Sealing fluids** back to **Mining**, preserving the original **Wall → Fluid → Mining** phase order. While Replace is working, the computer displays **Replacing walls**.

Both Wall and Filler reservation buffers, plus each animated in-flight packet, are persisted alongside the moving drill's state. Breaking the drill safely drops/refunds reserved stock rather than duplicating it.

**Please test in a backed-up world:** a stone-filled Replace perimeter, low Wall stock with Pause/Continue, a water/lava source outside the hole, 64-block filler replenishment, falling sand, server reload mid-supply flight, and automatic restart of mining once fully sealed. Fabric compilation does not verify world-side behavior.

## v0.4.3 — fresh drill pairing and handheld model repairs

- **Fresh excavation progress:** Placing a drill item starts at **Layers: 0 / configured depth** even if the exact block location previously contained another drill, or the item somehow retained old block-entity excavation data. Fresh placement clears the old drill's mining cursor, completed layers, hardness progress, tool-placement charge, wall sealing state, and pending work. It does **not** clear physically installed mining bits or mined-item storage. Planned automatic drill descent retains progress normally.
- **Per-drill identity protection:** Mining Computers no longer treat any drill at a remembered coordinate as the registered drill. They validate the registered item's unique pairing token for start/stop, mining ticks, ETA, layer counts, and downward relocation. Linking a new drill in another drill's old location invalidates the obsolete location, without transferring the prior drill's progress or starting it accidentally.
- **Handheld model rebuilt:** The mining drill item model is generated from the **current two placed block models**, including the correct worn-yellow top housing, matching material aliases, lower steel/raw-iron cone, dirt/rust accents, and top hatch. Legacy copied item geometry and mismatched old material keys were discarded. All 25 visible model elements use explicit face UVs and the joint upper/lower surface faces are removed. A geometric audit found **zero duplicated, outward-facing coplanar surfaces** in the handheld model. Item and placed housing use the same yellow-concrete material for consistent color.
- **In-game verification:** With the computer set to depth 12, link and place a new drill where an 8/12 drill used to be. The new machine should read **0/12, Ready**, while the old pairing no longer controls it. A drill legitimately continuing to descend should preserve its own layer count and bits. Rotate the drill in first- and third-person views to inspect its yellow paint, cone and model edges. Compile verification is not an in-game render test.

## v0.4.2: in-game feedback fixes

**Collection Coordinator proportions:** steel housing is **4 model pixels smaller overall**, 12-pixel-wide centered body, leaving 2 pixels on either side for its adaptive input hopper, output pipe, and teal link panel. Attachments are inset to fit inside the one-block envelope. A geometry audit over 13 block models found **no coplanar outward-facing element overlaps** after transformation; Minecraft shaders and actual textures still need live review.

**Bidirectional item visualization:** mined-item packets leaving the drill and CC are now rendered at **55% of the GROUND-item model size**, up from 25%. Filler/Wall input stock is reserved only when a drill specifically requests it, and an untouchable ground-model item visibly travels from the source chest through its CC-to-CC route to the drill *before* placement. Supply identity, path and current progress are persisted in the moving drill block; breaking the drill refunds/drops a reserved supply rather than deleting it. Reverse transit sends one requested block per trip while ordinary mining output remains batched to a maximum of three items per packet.

**Layer order:** every working Y-level runs **Wall Step → Fluid Step → Mining Step**. Wall Off skips wall work; Air patches voids in the external ring from a Filler input; **Replace** (formerly Always) digs the one-block perimeter with legitimate bits then installs wall material. Only after wall work completes does fluid treatment examine the selected hole and one-block boundary. Normal core mining then clears the user-selected footprint and moves down by one Y. The drill's real **Layers: N / target** appears in the four-drill computer GUI to diagnose early stoppages. Falling-block debris that drops into the drill's next downward position is mined if its tier allows, instead of immediately ending the descent. Unbreakable blocks still halt mining.

**Tool cost on placement:** wall/filler placement has a tool-dependent preparation speed equal to **1.3× the corresponding stone mining duration**. Each successful placement consumes standard mining-bit durability with a **30% chance of an extra wear roll** (both rolls respect Unbreaking). Replace-mode boundary excavation still requires and damages a suitable mining bit.

**Unfillable spots:** falling sand/gravel, fluid displacement and other placement failures receive up to **24 attempts at a single target** with **8 game ticks between placed-block verification**. After that, the drill halts with **Fill blocked – help needed** instead of repeating indefinitely. This cannot diagnose every falling-block mod interaction, so test near gravity-affected blocks.

**Mining Tablet:** a direction-specific thin selection/collision box fits the actual mounted tablet model rather than using a full cubic hitbox. The handheld tablet now keeps a **stable red/yellow/green state component** until the underlying machine's severity actually changes; blinking red/yellow lighting is a **client-side model flicker** rather than repeated server item replacement. Mounted tablets continue flashing normally.

**Development test checklist:** run in a backed-up Minecraft 26.2 world; verify exactly 12 completed layers with surviving bits and available output, status when bits/storage halt early, the no-dupe descent transfer, falling sand retry/stop, wall-before-fluid behavior, reverse visual supply movement on multiple CC hops, bit wear with/without Unbreaking, CC face clipping, and handheld tablet stability.

## v0.4.1: redesigned Collection Coordinators and portable tablet

### Coordinator face designs

Every face is part of the **same six-sided steel Collection Coordinator**, based on the flat / input / output / wireless render reference:
- **Flat:** dark, inset steel panel and corner rivets; no nearby configured link.
- **Input:** inset hopper-like collector mouth for an attached Filler or Wall input inventory.
- **Output:** hollow projecting metal tube for a configured output inventory.
- **Teal link:** luminous-looking cyan signal core with corner lights for an automatic CC–CC or drill–CC link.

The shapes are generated as independent blockstate multipart model elements and rotate onto all six faces. A model-geometry Z-fighting audit was done for the CC, drill, computer, and tablet. Overlapping outward coplanar teal-panel trim pieces were separated; model overlays sit on different depth planes. The machine still uses the original `miningrig:collection_coordinator` ID and 192-item storage/network rules.

### Mining Computer Tablet

Craft a **Mining Computer Tablet** with iron, glass pane, comparator and redstone, or find it in the Redstone creative category.

1. **Hold the tablet and right-click a real Mining Computer** (including its raised upper section) to pair it. The computer generates a persistent machine identity. You can link multiple tablets to one computer; they don't consume any of its four drill slots.
2. **Right-click air while holding the linked tablet** to open the **actual remote computer GUI**, including all four drills, live statuses/ETAs, nicknames, running controls and wall modes. Tablets do **not** accept drill pairing; drills must still be linked to a placed Mining Computer.
3. **Right-click the side of another block with the tablet** to install it as a compact wall/ceiling/floor-mounted workstation. Right-click the installed terminal to open that same remote computer GUI. The linked machine coordinates, dimension and unique machine ID persist through placement and normal player break/pickup.
4. The handheld item uses **dark/green/yellow/red** visual variants synchronized to the real computer's warning and alarm lamp. The installed terminal's indicator also reflects the computer's current light state; yellow/red blink phases follow the source computer when its chunk is loaded.

**Remote access:** tablet terminals can open their authoritative computer from a different location or dimension. Opening a session loads the computer chunk temporarily and releases that session ticket when the menu closes; this does not add a second computer/drill controller. The link is rejected if the old computer block has been broken and replaced. A tablet outside a loaded computer chunk may appear dark until a session opens or drilling keeps the computer loaded; this is expected and avoids indefinitely force-loading computers from unattended tablets.

**Early-stage testing:** run this development build in a backup world and verify front/back tablet orientation, real multiplayer tablet control, status lamp flashes, the mounted block surviving break/replacement, and every CC face attachment. GitHub Actions verifies compilation, not actual in-world rendering.

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

Filler and wall inputs are **never automatically vacuumed**. A drill requests specific blocks as needed, and the network tries the **closest stocked input container first**, then other inputs in the same discovered CC component. Outputs are identified by CC faces set to Output. A drill must have an accessible Filler input and Output and, in Replace mode, a Wall input **before starting**. Removing them mid-operation halts the drill with an error.

### Fluid and wall behavior

Each working layer is scanned for fluids inside the mining region and **up to one block outside** its selected boundary. When fluid is seen, the drill suspends its excavation, requests solid filler blocks from the network, and replaces source/flowing fluid. It waits **8 game ticks after each placement** to check that a full solid block remains; if sand/gravel fell, or fluid moved away leaving air, it fills again. It will not proceed while unsealed fluid spots remain. If all configured Filler input containers run out while fluid remains, the computer reports **Out of Filler / Fluid in hole**. Upon recovery, the computer's indicator can briefly blink yellow as a fluid caution.

The Mining Computer's **Wall** button cycles its per-drill **Wall the Hole** setting:

- **Off:** Only repair actual fluid locations and nearby fluid boundaries.
- **Air:** After each layer, fill every air/fluid opening in the one-block-wide outer wall with Filler blocks.
- **Replace:** Excavate one block beyond the chosen selection on every layer, then build the boundary using the separate Wall input.
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

Run `gradle build` using Java 25; or download the `caszual-mining-rig-0.4.4.jar` inside the `miningrig-26.2` artifact in the most recent successful GitHub Actions workflow.

**Status:** GitHub Actions compile verification is used, but it does *not* test actual Minecraft interaction, server permissions, chunk reloads, or multiplayer naming/GUI synchronization. Test in a backed-up world and treat this as a development build until in-game verified.
