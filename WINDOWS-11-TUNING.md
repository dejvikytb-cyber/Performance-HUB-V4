# Windows 11 quick-tuning guide (8 GB RAM)

## Launcher

Use 64-bit Java 21 and start with:

```text
-Xms512M -Xmx3G
```

Do not add `-XX:ActiveProcessorCount=12` just because the CPU has 12 logical threads. Java normally detects the available logical processors itself, and forcing a count does not make the render thread parallel. If testing reveals that Java reports too few processors, diagnose the launcher/JVM environment first.

The G1 collector is the Java 21 default. Avoid large copied “FPS boost” argument lists and experimental GC flags unless you benchmark them; more aggressive settings can increase memory use or worsen pauses on an 8 GB PC.

## Windows

- Settings → Gaming → Game Mode: On.
- Settings → System → Power & battery: Best performance while plugged in.
- Settings → System → Display → Graphics: add/select the launcher's Java runtime (`javaw.exe`) and choose High performance.
- Close browsers and recording overlays if Windows memory pressure is high.

## What the mod can safely parallelize

V4 reduces allocations in telemetry and adapts optional culling work according to measured frame-time and heap pressure. It deliberately does not run arbitrary Minecraft world access or GL rendering on its own worker threads: Minecraft's live world data and rendering context are not generally thread-safe. Chunk loading/rendering workers are managed by Minecraft and installed renderer mods. The best way to improve real parallel work is a compatible chunk renderer (for example, Sodium) and a controlled before/after benchmark, not a fake “use all cores” toggle.
