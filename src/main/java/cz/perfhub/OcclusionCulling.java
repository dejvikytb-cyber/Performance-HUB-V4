package cz.perfhub;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Iterator;
import java.util.Map;
import java.util.WeakHashMap;

/** Conservative, cached, CPU raycast occlusion culling for non-player entities. */
public final class OcclusionCulling {
    private static final Map<Entity, Entry> CACHE = new WeakHashMap<>();
    private static long currentTick = Long.MIN_VALUE;
    private static int checksThisTick, culledThisTick, lastCulled;

    private static final class Entry {
        final long tick;
        final BlockPos cameraBlock;
        final BlockPos entityBlock;
        final Vec3 cameraPosition;
        final float cameraYaw;
        final float cameraPitch;
        final boolean occluded;
        Entry(long tick, BlockPos cameraBlock, BlockPos entityBlock, Vec3 cameraPosition,
              float cameraYaw, float cameraPitch, boolean occluded) {
            this.tick = tick;
            this.cameraBlock = cameraBlock;
            this.entityBlock = entityBlock;
            this.cameraPosition = cameraPosition;
            this.cameraYaw = cameraYaw;
            this.cameraPitch = cameraPitch;
            this.occluded = occluded;
        }
    }

    private OcclusionCulling() {}

    public static void beginTick(long tick) {
        if (currentTick != tick) {
            lastCulled = culledThisTick;
            culledThisTick = 0;
            checksThisTick = 0;
            currentTick = tick;
            if (tick % 100 == 0) {
                Iterator<Entry> iterator = CACHE.values().iterator();
                // WeakHashMap cleans collected keys itself; periodic access also bounds stale entries.
                while (iterator.hasNext()) {
                    Entry e = iterator.next();
                    if (tick - e.tick > 40) iterator.remove();
                }
                if (CACHE.size() > 8192) CACHE.clear();
            }
        }
    }

    public static boolean shouldCull(Entity entity) {
        PerfConfig cfg = PerfConfig.get();
        if (!cfg.entityOcclusionCulling || cfg.maxOcclusionChecksPerTick <= 0) return false;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || entity == mc.player || entity instanceof Player
                || entity.level() != mc.level || entity.isRemoved()) return false;

        var activeCamera = mc.gameRenderer.getMainCamera();
        Vec3 camera = activeCamera.position();
        double distanceSq = entity.distanceToSqr(camera.x, camera.y, camera.z);
        if (distanceSq < 25.0 || distanceSq > (double) cfg.occlusionMaxDistance * cfg.occlusionMaxDistance) return false;

        AABB entityBox = entity.getBoundingBox();
        // Very large/custom models are unsafe to hide from a few sight lines.
        if (entityBox.getXsize() > 1.6 || entityBox.getZsize() > 1.6 || entityBox.getYsize() > 3.2) return false;
        BlockPos cameraBlock = BlockPos.containing(camera);
        BlockPos entityBlock = entity.blockPosition();
        float cameraYaw = activeCamera.yRot();
        float cameraPitch = activeCamera.xRot();
        Entry cached = CACHE.get(entity);
        long nowTick = PerformanceMetrics.tickId();
        if (cached != null && nowTick - cached.tick < cfg.occlusionCacheTicks
                && cached.cameraBlock.equals(cameraBlock) && cached.entityBlock.equals(entityBlock)
                && cached.cameraPosition.distanceToSqr(camera) < 0.25
                && angleDistance(cached.cameraYaw, cameraYaw) < 8.0f
                && Math.abs(cached.cameraPitch - cameraPitch) < 8.0f) {
            if (cached.occluded) culledThisTick++;
            return cached.occluded;
        }

        if (checksThisTick >= cfg.maxOcclusionChecksPerTick) return false;
        checksThisTick++;
        boolean occluded = isEntityOccluded(mc, entity, camera);
        CACHE.put(entity, new Entry(nowTick, cameraBlock.immutable(), entityBlock.immutable(), camera,
                cameraYaw, cameraPitch, occluded));
        if (occluded) culledThisTick++;
        return occluded;
    }

    private static boolean isEntityOccluded(Minecraft mc, Entity entity, Vec3 camera) {
        AABB box = entity.getBoundingBox();
        Vec3 center = box.getCenter();
        double yPad = Math.min(0.12, Math.max(0.0, box.getYsize() * 0.20));
        Vec3 lower = new Vec3(center.x, Math.min(box.maxY, box.minY + yPad), center.z);
        Vec3 upper = new Vec3(center.x, Math.max(box.minY, box.maxY - yPad), center.z);
        // Only hide the entity when three conservative sight lines are all blocked by opaque blocks.
        return blockedByOpaqueBlock(mc, camera, center)
                && blockedByOpaqueBlock(mc, camera, lower)
                && blockedByOpaqueBlock(mc, camera, upper);
    }

    private static boolean blockedByOpaqueBlock(Minecraft mc, Vec3 start, Vec3 end) {
        BlockHitResult hit;
        try {
            hit = mc.level.clip(new ClipContext(start, end, ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE, mc.player));
        } catch (Throwable ignored) {
            return false; // A failed visibility test must never hide an entity.
        }
        if (hit.getType() != HitResult.Type.BLOCK) return false;
        if (hit.getLocation().distanceTo(start) < 0.25) return false;
        BlockPos pos = hit.getBlockPos();
        BlockState state = mc.level.getBlockState(pos);
        if (!state.isSolidRender()) return false; // glass/leaves/other transparent blocks: conservative visible
        return hit.getLocation().distanceTo(end) > 0.40;
    }

    private static float angleDistance(float a, float b) {
        float delta = Math.abs(a - b) % 360.0f;
        return delta > 180.0f ? 360.0f - delta : delta;
    }

    public static int lastTickCulledEntities() { return lastCulled; }
    public static int currentTickChecks() { return checksThisTick; }
    public static void clear() { CACHE.clear(); checksThisTick = culledThisTick = lastCulled = 0; }
}
