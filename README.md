# Performance Hub V4 — Windows 11 / low-memory focus

Client-side Fabric mod for Minecraft Java 1.21.11 (Java 21). V4 focuses on reducing the mod's own allocation churn, bounding visibility-cache memory, and dynamically throttling CPU-side visibility tests when frame time or Java heap pressure is high.

## V4 changes

- **Low-allocation metrics:** fixed reusable frame/tick sort buffers and a fixed graph buffer replace temporary arrays on every HUD refresh. System CPU/RAM and process CPU load are sampled at most once per second.
- **Windows/system telemetry:** detects Windows vs. other OSes, logical processor count, physical system RAM, Java heap use, process/system CPU load and GC collection count. All OS metrics are diagnostic; the mod does not require administrator privileges.
- **Adaptive culling-work budget:** visibility-raycast budgets gradually rise when P95 frame time has headroom and fall when the frame-time target is missed. The checks remain on Minecraft's client thread because live world/block queries and the OpenGL context are not generally thread-safe.
- **Low-memory mode:** shorter cache lifetime, configurable entity/block-entity cache caps and a stricter cap when Java heap pressure is detected. Cosmetic particle admission backs off near the configured heap-pressure threshold.
- **Bounded caches:** stale visibility entries are pruned regularly; caps are configurable in the System tab.
- **Updated System tab:** shows the OS, JVM-visible logical thread count and physical RAM, and provides memory/culling budget controls.

## What this can and cannot do

A client mod cannot safely make every part of Minecraft multithreaded. Rendering and OpenGL submission remain constrained by the renderer's thread model; moving arbitrary world raycasts to a random background thread would risk race conditions and crashes. V4 adapts the amount of optional CPU work instead of making unsafe worker calls.

Likewise, a mod cannot resize `-Xmx` after the JVM starts. Low-memory mode reduces this mod's cache/particle pressure; the launcher JVM arguments control the Java heap itself. It cannot change Minecraft from OpenGL to Vulkan/DirectX through one setting.

All culling features affect local visual submission only. They do not stop entity AI, block entity ticking, redstone, client/server packets or server ticks. The HUD's tick number is **client tick duration**, not server TPS.

Entity occlusion culling uses CPU raycasts. Budgets are deliberately bounded to avoid a culler becoming the new bottleneck. Players and unusually large/custom entities are excluded. If a modded entity or block entity disappears incorrectly, disable the corresponding culling switch.

## Windows 11 setup for a PC with 8 GB RAM

1. Use a 64-bit Java 21 runtime for the Fabric instance.
2. In the launcher's instance settings, use `-Xms512M -Xmx3G` as a starting point for vanilla/lightly modded Fabric. With a heavy modpack, test 3.5G only if Windows is not paging; do not blindly allocate most of the PC's 8 GB to Java.
3. Leave `-XX:ActiveProcessorCount` unset unless a measured JVM/container issue has hidden processors. The mod reports `Runtime.availableProcessors()`; it does not pin or arbitrarily reserve cores.
4. Enable Windows **Game Mode** and set **Settings → System → Power & battery → Power mode → Best performance** when plugged in. In **Settings → System → Display → Graphics**, select the high-performance GPU for the Java runtime actually used by the launcher (`javaw.exe`).
5. Close memory-heavy browsers and launchers while playing. A GTX 1060 3 GB has a separate VRAM limit; lowering texture-pack resolution and avoiding shaders can matter as much as heap tuning.
6. For rendering/chunk generation, benchmark compatible purpose-built mods such as Sodium and Lithium for your exact modpack. Avoid stacking multiple mods that hook the same renderer unless they are known to be compatible.

Suggested heap arguments above are a starting point, not a universal optimum. Monitor Windows memory pressure and the V4 HUD in the same world/scene before and after a change.

## Controls

- **Right Shift:** open Performance Hub.
- **F8:** show/hide the diagnostics HUD.
- **System tab:** memory mode, adaptive culling work budget, system telemetry, heap-pressure threshold, and visibility-cache caps.

Settings are saved to `config/performancehub.json`.

## Build

Requires JDK 21 and network access to resolve Gradle/Fabric/Minecraft dependencies.

```bash
gradle build --no-daemon
```

Output: `build/libs/performance-hub-4.0.0.jar`. The GitHub Actions workflow uploads the JAR as `performance-hub-jar`.
