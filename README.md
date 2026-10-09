# Caszual Mining Rig (Fabric 26.2)

A first compiled, **not yet in-game verified** implementation of a configurable mining machine for Minecraft Java 26.2 with Fabric API. A first-pass in-world computer status screen is rendered on the face of the machine; a dedicated configuration GUI and polished 3D models are next.

## Current prototype controls

1. Craft/place the **Mining Drill** at the intended top-center of the excavation.
2. Craft/place a **Mining Computer**, a **Collection Coordinator** (aligned on an X/Y/Z axis with the drill, 1–20 blocks away), a drill bit, and a **Rig Linker**.
3. With the Rig Linker, right-click the **drill**, then the **computer** to pair them wirelessly.
4. Install a bit by right-clicking the drill with the bit. **Sneak + empty-hand right-click** on the drill to remove a bit. All six material types have pickaxe durability.
5. At the computer, right-click with a **stick** to raise radius, **bone** to raise depth, **lapis lazuli** to toggle circle/square. **Sneak** while using the stick/bone to decrease the value. Radius 1–12, depth 1–256.
6. **Empty-hand right-click** computer to start/stop. **Sneak + empty-hand click** to inspect status without toggling. The footprint perimeter has floating particles. Computer's indicator blinks red when halted for an error/status.
7. The drill mines a block roughly every 8 ticks and moves down by one block after scanning each footprint layer. It saves bit, cursor, and completed layers so a reload does not restart the job.
8. Coordinator collects up to **27 item stacks**. Attach a hopper to extract. To configure a directed wireless path, use the Rig Linker on **source coordinator**, then **destination coordinator**. Each hop requires identical two coordinates and <=20 blocks along the remaining axis; cyclic links are rejected.

## Current statuses

Ready, Mining, Manually stopped, Job done, Out of bits, Out of range of a collector, Unable to break block, Collector storage full, No linked drill.

## Scope and safety

- Actual placement, damage, collection and block removal run only on the server. Drill stops at unbreakable blocks, block entities and incorrect bit harvest tiers. It won't force-load chunks.
- Silk Touch and Fortune information is passed to the vanilla loot generation with the drill bit as tool. Durability currently decreases by 1 for each mined block (Unbreaking behavior still needs testing/implementation).
- **Not yet delivered:** a clickable dedicated computer GUI, dynamic text drawn on the computer face, durable line-outline client renderer, detailed custom textures/3D animations, survival game-test verification, complete world-claim/protection integration, energy/power system.
- Because the drill is physically relocated by removing/replacing its block, test in a **backed-up world** before running it on a server.
- If a coordinator fills, the job halts rather than discarding mined loot. An outgoing wireless link can send its contents to another coordinator; final destination is accessible to hoppers.

## Build

This repository targets **Minecraft 26.2, Fabric Loader 0.19.5, Fabric API 0.161.0+26.2, Java 25, Gradle 9.7.0**. You can run `gradle build` with Gradle 9.7.0 and a Java 25 JDK. The GitHub Actions workflow uses these versions and uploads the compiled artifacts after a successful build.

**Status:** Gradle compilation has passed on GitHub Actions. Runtime/in-game behavior has not yet been verified; do not treat as a fully tested release.
