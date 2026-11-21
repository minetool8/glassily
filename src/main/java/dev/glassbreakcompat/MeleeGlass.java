package dev.glassbreakcompat;

import net.bettercombat.api.WeaponAttributes.HitBoxShape;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Better Combat swing -> glass. The attack's hitbox shape and range are rebuilt as an oriented box in front of
 * the player. Glass Better Glass Breaking supports gets one fracture ray per sample point that lies inside the
 * box, so the broken area follows the hitbox. Other glass-named blocks inside the box break normally.
 * At most {@code maxBlocksPerSwing} blocks (nearest first) are affected per swing.
 */
public final class MeleeGlass {
    private static final double[] SAMPLES = {0.2, 0.5, 0.8};

    private record Candidate(BlockPos pos, GlassImpacts.Kind kind, List<Vec3> inside, double dist) {}

    private MeleeGlass() {}

    public static void swing(ServerPlayer p, ServerLevel level, double attackRange, HitBoxShape shape, double damageMultiplier) {
        if (CompatConfig.DEBUG.get()) {
            GlassBreakCompat.LOGGER.info("[glassbreakcompat] Better Combat attack received: player={} range={} shape={}",
                    p.getName().getString(), attackRange, shape);
        }
        try {
            doSwing(p, level, attackRange, shape == null ? HitBoxShape.FORWARD_BOX : shape, damageMultiplier);
        } catch (Throwable t) {
            GlassBreakCompat.LOGGER.warn("[glassbreakcompat] melee glass break failed", t);
        }
    }

    private static void doSwing(ServerPlayer p, ServerLevel level, double attackRange, HitBoxShape shape, double damageMultiplier) {
        if (!CompatConfig.MELEE_BREAK.get() || p.isSpectator()) return;

        Vec3 eye = p.getEyePosition();
        Vec3 fwd = p.getLookAngle().normalize();
        Vec3 right = fwd.cross(new Vec3(0, 1, 0));
        if (right.lengthSqr() < 1.0e-6) right = new Vec3(1, 0, 0);
        right = right.normalize();
        Vec3 up = right.cross(fwd).normalize();

        double reach = Mth.clamp(attackRange + 0.5, 1.5, 6.0);
        double halfW, halfH;
        switch (shape) {
            case HORIZONTAL_PLANE -> { halfW = reach; halfH = 0.4; }
            case VERTICAL_PLANE -> { halfW = 0.3; halfH = Math.min(reach * 0.9, 2.2); }
            default -> { halfW = reach * 0.5; halfH = reach * 0.4; }
        }
        double depth = reach;

        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE, minZ = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sy = -1; sy <= 1; sy += 2) {
                for (int sz = 0; sz <= 1; sz++) {
                    Vec3 c = eye.add(right.scale(sx * halfW)).add(up.scale(sy * halfH)).add(fwd.scale(sz * depth));
                    minX = Math.min(minX, c.x); maxX = Math.max(maxX, c.x);
                    minY = Math.min(minY, c.y); maxY = Math.max(maxY, c.y);
                    minZ = Math.min(minZ, c.z); maxZ = Math.max(maxZ, c.z);
                }
            }
        }
        AABB bounds = new AABB(minX, minY, minZ, maxX, maxY, maxZ);

        List<Candidate> found = new ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(bounds.minX, bounds.minY, bounds.minZ),
                BlockPos.containing(bounds.maxX, bounds.maxY, bounds.maxZ))) {
            GlassImpacts.Kind kind = GlassImpacts.classify(level, pos, level.getBlockState(pos));
            if (kind == GlassImpacts.Kind.NONE) continue;

            List<Vec3> inside = new ArrayList<>();
            for (double a : SAMPLES) for (double b : SAMPLES) for (double c : SAMPLES) {
                Vec3 pt = new Vec3(pos.getX() + a, pos.getY() + b, pos.getZ() + c);
                Vec3 rel = pt.subtract(eye);
                double w = rel.dot(fwd);
                if (w < 0 || w > depth) continue;
                if (Math.abs(rel.dot(right)) > halfW || Math.abs(rel.dot(up)) > halfH) continue;
                inside.add(pt);
            }
            if (inside.isEmpty()) continue;
            BlockPos fixed = pos.immutable();
            if (!lineOfSight(level, p, eye, fixed)) continue;
            found.add(new Candidate(fixed, kind, inside, eye.distanceToSqr(Vec3.atCenterOf(fixed))));
        }
        if (found.isEmpty()) return;

        found.sort(Comparator.comparingDouble(Candidate::dist));
        int limit = Math.min(CompatConfig.MELEE_MAX_BLOCKS.get(), found.size());
        double power = CompatConfig.MELEE_POWER.get() * Mth.clamp(damageMultiplier, 0.7, 1.6);

        List<BlockPos> procedural = new ArrayList<>();
        for (int i = 0; i < limit; i++) {
            Candidate cand = found.get(i);
            if (cand.kind() == GlassImpacts.Kind.NORMAL) {
                GlassImpacts.normalBreak(level, cand.pos(), p);
                continue;
            }
            boolean any = false;
            int step = Math.max(1, cand.inside().size() / 9);
            for (int k = 0; k < cand.inside().size(); k += step) {
                any |= GlassImpacts.impact(level, cand.pos(), cand.inside().get(k), fwd, power, p);
            }
            if (any) procedural.add(cand.pos());
        }

        if (!procedural.isEmpty() && CompatConfig.NEIGHBOR_CRACKS.get()) {
            SprintImpactHandler.crackNeighbors(level, p, procedural, fwd);
        }
        if (CompatConfig.DEBUG.get()) {
            GlassBreakCompat.LOGGER.info("[glassbreakcompat] melee swing: candidates={} used={} procedural={} shape={} reach={}",
                    found.size(), limit, procedural.size(), shape, String.format("%.1f", reach));
        }
    }

    /** Solid non-glass blocks between the player's eyes and the glass block stop the swing. */
    private static boolean lineOfSight(ServerLevel level, ServerPlayer p, Vec3 eye, BlockPos target) {
        BlockHitResult hit = level.clip(new ClipContext(eye, Vec3.atCenterOf(target),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
        if (hit.getType() == HitResult.Type.MISS || hit.getBlockPos().equals(target)) return true;
        BlockPos first = hit.getBlockPos();
        return GlassImpacts.classify(level, first, level.getBlockState(first)) != GlassImpacts.Kind.NONE;
    }
}
