# Caszual Mining Rig — Minecraft Java 26.2 / Fabric

A configurable layer-mining drill with three independent enchantable bits and a directional network of collection coordinators. **This branch is a working-source prototype: CI compilation is checked, but Minecraft runtime and server protection compatibility are not yet fully verified.** Keep a backup of the test world.

## New system overview

| Block | Job |
| --- | --- |
| Mining Computer | Sets drill depth and radius, circle/square mode; starts/stops drilling; announces status on its face; flashes red if halted. |
| Mining Drill | Mined layers physically lower the drill on Y. Holds up to **3 bits** and **64 mined items** total, with 8 pages for mixed blocks. |
| Collection Coordinator | One block handles both collection and wireless transfer. Holds up to **192 individual items total**, with 8 pages of mixed storage. Discovers the first CC up to 20 blocks along each of six cardinal directions. |

### Drill interface

Empty-hand right-click the drill to open its inventory. Three dedicated top slots hold any mix of wooden, stone, iron, gold, diamond or netherite drill bits; the other slots show mined items. Use the **< / >** page buttons to browse all 64 inventory positions. You can also right-click with a bit to install into the first empty bit socket.

Each equipped bit independently targets **one block at a time**, with per-tick mining progress based on the block's hardness and the bit material's ideal-tool speed. All three bits work concurrently. Efficiency speeds up digging; Unbreaking reduces wear; Fortune and Silk Touch feed through Minecraft's normal block-loot calculation. Bits have the durability and harvesting tier of their corresponding vanilla pickaxe.

The drill **automatically stops** when there are no working bits, its internal storage is full, or it reaches the programmed depth. A forbidden/unbreakable tile or unavailable chunk pauses the work for safety. After a completed layer, the drill block descends one Y level; stored bit wear, inventory, and mining cursor follow the block.

### Coordinator inventory & I/O panel

Empty-hand right-click a Collection Coordinator to open its inventory and six-direction control row. The coordinator accepts up to **192 total items**, *not* 192 stacks. Mixed item types are retained in paginated storage. Hoppers can still interact with the actual inventory. Contents and any in-flight cargo are dropped when the block is player-broken.

The first other coordinator discovered along each axis within 20 blocks is the automatic wireless partner for that side. Blocks in between don't have to be cleared, but both chunks must be loaded. No Rig Linker is required for coordinator-to-coordinator connections.

Buttons are ordered **Down, Up, North, South, West, East**. Repeated clicking cycles **IN → OUT → OFF → IN**. The line beneath each side indicates **green** for wireless coordinator, **gold** for an adjacent inventory/container, or **red** for no valid neighbor. The adjacent receiver CC face must be set to **IN**; the sending face must be set to **OUT**.

When moving a stack wirelessly, its representative block/item floats through the world in an **untouchable ItemDisplay** rather than instantly teleporting. Travel duration is scaled to 2.5 seconds for 20 blocks (50 game ticks). Cargo is recorded in source data until arrival so chunk unloading doesn't erase the stack. A destination unable to accept the cargo returns it to the sender, or ejects it when no storage is available.

**Only CC-to-CC links are wireless.** A coordinator can automatically accept items from a drill **directly adjacent** on a side configured as **IN**. A side set to **OUT** can insert items into an adjacent chest or other compatible container directly, without a wireless animation. This respects container sided-insertion rules where supported. **The drill does not transmit wirelessly**.

**Current practical limitation:** once the drill descends away from a stationary adjacent coordinator, it will mine into its internal 64-item store. A longer-term mechanical collection attachment or pipe will be useful for deeper uninterrupted operations.

### Computer setup

Use the Rig Linker on a Mining Drill, then on its Mining Computer to pair them (this is the *only* manual wireless pairing). Use a **stick** to increase radius, a **bone** to increase depth, and **lapis lazuli** to change circle/square footprint. Sneak with stick/bone to decrease the value.

- Radius: 1–12 blocks from drill center.
- Depth: 1–256 layers below the drill's original Y.
- Empty-hand right-click computer: start/stop.
- Sneak + empty-hand right-click computer: inspect status without toggling.
- Floating particles show the currently selected surface outline.
- Computer screen shows progress/status, flashing red on stopped/error/completed states.

### Recipes

Drill bits are crafted from **four matching materials in a 2×2 square**. All six materials match Minecraft's corresponding pickaxes. The Mining Computer, Mining Drill, Collection Coordinator, and Rig Linker have ordinary crafting-table recipes available in the project recipe JSON.

## Build and compatibility

Target: Minecraft **26.2**, Fabric Loader **0.19.5**, Fabric API **0.161.0+26.2**, Java **25**, Gradle **9.7.0**, Loom **1.18.3**.

Run `gradle build` with those prerequisites. The GitHub Actions workflow uploads the compiled jar under **Actions → newest passing Fabric 26.2 Build → Artifacts → miningrig-26.2**.

### To verify in-game next

- Player survival/creative break drops, hopper and furnace insertion, UI paging/shift-click and multiple mixed stacks.
- Fortune/Silk Touch/Unbreaking consistency with all vanilla and modded loot tables.
- Drill relocation/stop/restart across chunk unloads and server restarts.
- Floating ItemDisplay appearance while traveling 20 blocks, and cargo recovery on chunk unload/break.
- Red warning light visibility and multi-player menu synchronization.
- Modded block protection/claims and server profiling; current mining intentionally refuses block entities.

This is a **draft release, not yet an in-game verified stable jar**.
