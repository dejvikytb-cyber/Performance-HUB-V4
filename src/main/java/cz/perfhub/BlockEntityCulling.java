package cz.perfhub;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Iterator;
import java.util.Map;
import java.util.WeakHashMap;

/** Bounded distance and opaque-occlusion filtering for block-entity render-state extraction. */
public final class BlockEntityCulling {
    private static final Map<BlockEntity, Entry> CACHE = new WeakHashMap<>();
    private static int checksThisTick;
    private static int culledThisTick;
    private static int lastCulled;
    private static long currentTick = Long.MIN_VALUE;

    private static final class Entry {
        final long tick;
        final BlockPos cameraBlock;
        final BlockPos blockEntityPos;
        final Vec3 cameraPosition;
        final float cameraYaw;
        final float cameraPitch;
        final boolean occluded;

        Entry(long tick, BlockPos cameraBlock, BlockPos blockEntityPos, Vec3 cameraPosition,
              float cameraYaw, float cameraPitch, boolean occluded) {
            this.tick = tick;
            this.cameraBlock = cameraBlock;
            this.blockEntityPos = blockEntityPos;
            this.cameraPosition = cameraPosition;
            this.cameraYaw = cameraYaw;
            this.cameraPitch = cameraPitch;
            this.occluded = occluded;
        }
    }

    private BlockEntityCulling() {}

    public static void beginTick(long tick) {
        if (currentTick != tick) {
            lastCulled = culledThisTick;
            culledThisTick = 0;
            checksThisTick = 0;
            currentTick = tick;
            if (tick % 100 == 0) {
                Iterator<Entry> iterator = CACHE.values().iterator();
                while (iterator.hasNext()) {
                    if (tick - iterator.next().tick > 40) iterator.remove();
                }
                if (CACHE.size() > 4096) CACHE.clear();
            }
        }
    }

    public static boolean shouldCull(BlockEntity blockEntity) {
        PerfConfig cfg = PerfConfig.get();
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || blockEntity.getLevel() != mc.level) return false;

        var cameraObject = mc.gameRenderer.getMainCamera();
        Vec3 camera = cameraObject.position();
        BlockPos pos = blockEntity.getBlockPos();
        double x = pos.getX() + 0.5 - camera.x;
        double y = pos.getY() + 0.5 - camera.y;
        double z = pos.getZ() + 0.5 - camera.z;
        double distanceSq = x * x + y * y + z * z;

        if (cfg.blockEntityDistanceCulling && cfg.blockEntityRenderDistance < 128
                && distanceSq > (double) cfg.blockEntityRenderDistance * cfg.blockEntityRenderDistance) {
            culledThisTick++;
            return true;
        }

        if (!cfg.blockEntityOcclusionCulling || cfg.maxBlockEntityOcclusionChecksPerTick <= 0
                || distanceSq < 25.0
                || distanceSq > (double) cfg.occlusionMaxDistance * cfg.occlusionMaxDistance) return false;

        BlockPos cameraBlock = BlockPos.containing(camera);
        BlockPos targetBlock = pos.immutable();
        float yaw = cameraObject.yRot();
        float pitch = cameraObject.xRot();
        long nowTick = PerformanceMetrics.tickId();
        Entry cached = CACHE.get(blockEntity);
        if (cached != null && nowTick - cached.tick < cfg.blockEntityOcclusionCacheTicks
                && cached.cameraBlock.equals(cameraBlock) && cached.blockEntityPos.equals(targetBlock)
                && cached.cameraPosition.distanceToSqr(camera) < 0.25
                && angleDistance(cached.cameraYaw, yaw) < 8.0f
                && Math.abs(cached.cameraPitch - pitch) < 8.0f) {
            if (cached.occluded) culledThisTick++;
            return cached.occluded;
        }

        if (checksThisTick >= cfg.maxBlockEntityOcclusionChecksPerTick) return false;
        checksThisTick++;
        Vec3 center = new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        Vec3 lower = new Vec3(center.x, pos.getY() + 0.18, center.z);
        Vec3 upper = new Vec3(center.x, pos.getY() + 0.82, center.z);
        boolean occluded = blockedBeforeTarget(mc, camera, center, targetBlock)
                && blockedBeforeTarget(mc, camera, lower, targetBlock)
                && blockedBeforeTarget(mc, camera, upper, targetBlock);
        CACHE.put(blockEntity, new Entry(nowTick, cameraBlock.immutable(), targetBlock, camera, yaw, pitch, occluded));
        if (occluded) culledThisTick++;
        return occluded;
    }

    private static boolean blockedBeforeTarget(Minecraft mc, Vec3 start, Vec3 end, BlockPos targetBlock) {
        try {
            BlockHitResult hit = mc.level.clip(new ClipContext(start, end, ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE, mc.player));
            if (hit.getType() != HitResult.Type.BLOCK || hit.getBlockPos().equals(targetBlock)) return false;
            if (hit.getLocation().distanceTo(start) < 0.25 || hit.getLocation().distanceTo(end) < 0.40) return false;
            BlockPos hitPos = hit.getBlockPos();
            BlockState state = mc.level.getBlockState(hitPos);
            return state.isSolidRender();
        } catch (Throwable ignored) {
            return false; // visibility uncertainty must never hide a block entity
        }
    }

    private static float angleDistance(float a, float b) {
        float delta = Math.abs(a - b) % 360.0f;
        return delta > 180.0f ? 360.0f - delta : delta;
    }

    public static int lastTickCulled() { return lastCulled; }
    public static void clear() { CACHE.clear(); checksThisTick = culledThisTick = lastCulled = 0; currentTick = Long.MIN_VALUE; }
}
