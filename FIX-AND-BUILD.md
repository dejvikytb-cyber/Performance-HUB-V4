# Performance Hub 2.0 — final source fix notes

Target: Minecraft Java 1.21.11, Fabric, Java 21.

## Compiler API fixes

- `BlockEntityCulling`: use the 1.21.11 no-argument `BlockState.isSolidRender()` method.
- `OcclusionCulling`: use the same no-argument method.
- `PerformanceHud`: use the 2D `Matrix3x2fStack` API (`pushMatrix`, `scale(x, y)`, `popMatrix`).

## Build

The repository's GitHub Actions workflow provisions Gradle 9.2.1 and Java 21, runs `gradle clean build`, and uploads the JAR as an Actions artifact. Open Actions → latest successful run → Artifacts to download it.

Do not upload the parent folder as a nested directory. The files in this archive are laid out for the repository root: `build.gradle`, `gradle.properties`, `src/`, and `.github/` must be directly at the root.

The build must run on GitHub Actions (or another machine with dependency network access) to verify dependency resolution and compile all Minecraft/Fabric APIs. A ZIP integrity check is not a substitute for a successful Gradle build.
