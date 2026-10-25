# Performance Hub 2.0

A client-side Fabric performance dashboard for Minecraft Java **1.21.11** (Java 21). The goal is to expose performance controls in plain language, provide safe starter profiles, and show useful frame-time data without changing server gameplay.

## What is new in 2.0

- Cleaner compact dark HUD with FPS, frame-time/P95, client tick time, heap and culling counters; toggle with **F8**.
- Minimalist in-game control panel; open with **Right Shift**.
- Simple Competitive / Balanced / Visual profiles plus a hardware-informed recommendation.
- Optional entity and block-entity render culling, with conservative checks and bounded work.
- Particle distance filtering, spawn budget and adaptive budget.
- Optional adaptive render distance to keep performance near a target FPS.
- Optimizer-mod detector for Sodium, Sodium Extra, Lithium, FerriteCore and ImmediatelyFast. It shows which ones are installed.
- Vanilla settings snapshot and restore when using built-in profiles.
- Settings saved to `config/performancehub.json`.

## Recommended setup for a GTX 1060 3 GB + 8 GB RAM

Start with the **Competitive / max FPS** profile, 6–8 chunks render distance, 5–6 simulation distance, clouds off, minimal particles, and 2–4 GB maximum Java heap (do not allocate all system RAM; Windows and other apps need memory too). With only 8 GB system RAM, FerriteCore and keeping background apps closed can help reduce memory pressure. A performance mod cannot guarantee a particular FPS; world complexity, shaders, server and drivers matter.

## Optional performance mods

These are separate mods and must be downloaded for the same Minecraft version and Fabric loader. Performance Hub does **not** embed or redistribute their code; the Mods tab only detects them.

- [Sodium](https://modrinth.com/mod/sodium) — faster chunk rendering
- [Sodium Extra](https://modrinth.com/mod/sodium-extra) — additional Sodium settings
- [Lithium](https://modrinth.com/mod/lithium) — game logic/tick optimizations
- [FerriteCore](https://modrinth.com/mod/ferrite-core) — memory reduction
- [ImmediatelyFast](https://modrinth.com/mod/immediatelyfast) — rendering optimizations

Download only builds explicitly compatible with Minecraft 1.21.11 and Fabric. If a mod has not released a compatible build, leave it out; never force an older version into the mods folder. Sodium Extra requires Sodium. Check other mods' compatibility notes before combining render mods.

## Important limitations

- All custom culling and particle controls are client-side visual/performance trade-offs. They do not change server TPS, mob AI, redstone, collision or packets.
- Entity culling uses CPU raycasts; disable it if a modded entity appears incorrectly hidden.
- Particle budgets deliberately discard cosmetic effects when overloaded.
- The detector does not automatically download, configure or replace other mods. Sodium/Sodium Extra settings are managed by those mods themselves.
- No honest project can promise a specific FPS uplift for every world. Test changes one at a time.

## Build

Requires JDK 21 and internet access for dependency resolution.

```bash
gradle clean build --no-daemon
```

The output JAR is in `build/libs/`. The GitHub Actions workflow builds the project and uploads the JAR as an artifact.
