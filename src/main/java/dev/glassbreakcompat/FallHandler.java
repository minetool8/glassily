package dev.glassbreakcompat;

import dev.betterglassbreaking.forge.BetterGlassBreaking;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.List;

/** Landing on glass after a long fall breaks it. */
public final class FallHandler {

    @SubscribeEvent
    public void onFall(LivingFallEvent e) {
        if (!CompatConfig.FALL_BREAK.get()) return;
        LivingEntity entity = e.getEntity();
        if (!(entity.level() instanceof ServerLevel level)) return;
        if (e.getDistance() < CompatConfig.FALL_MIN_DISTANCE.get()) return;

        AABB bb = entity.getBoundingBox();
        int y = (int) Math.floor(bb.minY - 0.05);
        double power = CompatConfig.FALL_POWER.get();
        Vec3 down = new Vec3(0, -1, 0);

        List<BlockPos> broken = new ArrayList<>();
        for (int x = (int) Math.floor(bb.minX); x <= (int) Math.floor(bb.maxX); x++) {
            for (int z = (int) Math.floor(bb.minZ); z <= (int) Math.floor(bb.maxZ); z++) {
                BlockPos pos = new BlockPos(x, y, z);
                BlockState state = level.getBlockState(pos);
                switch (GlassImpacts.classify(level, pos, state)) {
                    case NORMAL -> GlassImpacts.normalBreak(level, pos, entity);
                    case BGB -> {
                        boolean ok = isPaneLike(state)
                                ? paneImpacts(level, pos, entity, power)
                                : cubeImpacts(level, pos, entity, power, down);
                        if (ok) broken.add(pos);
                    }
                    default -> { }
                }
            }
        }
        if (!broken.isEmpty() && CompatConfig.NEIGHBOR_CRACKS.get()) {
            SprintImpactHandler.crackNeighbors(level, entity instanceof net.minecraft.server.level.ServerPlayer sp ? sp : null,
                    broken, down);
        }
    }

    private static boolean isPaneLike(BlockState state) {
        return state.getBlock() instanceof IronBarsBlock || state.is(BetterGlassBreaking.PANE.get());
    }

    /** Glass cubes: a 3x3 grid of rays straight down through the block. */
    private static boolean cubeImpacts(ServerLevel level, BlockPos pos, LivingEntity who, double power, Vec3 down) {
        boolean any = false;
        double[] offs = {-0.25, 0.0, 0.25};
        for (double a : offs) {
            for (double b : offs) {
                Vec3 aim = new Vec3(pos.getX() + 0.5 + a, pos.getY() + 0.7, pos.getZ() + 0.5 + b);
                any |= GlassImpacts.impact(level, pos, aim, down, power, who);
            }
        }
        return any;
    }

    /** Thin panes are hit across the face from both horizontal axes (whichever plane the pane lies in). */
    private static boolean paneImpacts(ServerLevel level, BlockPos pos, LivingEntity who, double power) {
        boolean any = false;
        double[] lateral = {-0.2, 0.2};
        double[] heights = {0.3, 0.8};
        Vec3[] dirs = {new Vec3(1, 0, 0), new Vec3(0, 0, 1)};
        for (Vec3 dir : dirs) {
            Vec3 n = new Vec3(-dir.z, 0, dir.x);
            for (double l : lateral) {
                for (double h : heights) {
                    Vec3 aim = new Vec3(pos.getX() + 0.5, pos.getY() + h, pos.getZ() + 0.5).add(n.scale(l));
                    any |= GlassImpacts.impact(level, pos, aim, dir, power, who);
                }
            }
        }
        return any;
    }
}
