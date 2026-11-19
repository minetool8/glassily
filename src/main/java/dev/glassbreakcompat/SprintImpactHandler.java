package dev.glassbreakcompat;

import dev.betterglassbreaking.core.GameplayConfig;
import org.slf4j.Logger;
import dev.betterglassbreaking.core.GameplayRules.Source;
import dev.betterglassbreaking.core.GameplayRules.SourceFacts;
import dev.betterglassbreaking.forge.BetterGlassBreaking;
import dev.betterglassbreaking.forge.GlassConfig;
import dev.betterglassbreaking.forge.PaneImpacts;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Server-side: when a player runs into glass, hand Better Glass Breaking a small grid of fracture
 * impacts across the player's body so it carves its normal procedural, permanent, synced opening.
 */
public final class SprintImpactHandler {

    private static final Logger LOG = GlassBreakCompat.LOGGER;

    private static final class Track {
        double lastX, lastZ;
        boolean init;
        double peak;
        double dirX, dirZ;
        long nextTick;
    }

    private final Map<UUID, Track> tracks = new HashMap<>();

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer p)) return;
        if (!CompatConfig.SPRINT_BREAK.get()) return;

        Track t = tracks.computeIfAbsent(p.getUUID(), k -> new Track());
        double dx = p.getX() - t.lastX, dz = p.getZ() - t.lastZ;
        double speed = t.init ? Math.hypot(dx, dz) : 0.0;
        t.lastX = p.getX();
        t.lastZ = p.getZ();
        t.init = true;
        t.peak = Math.max(speed, t.peak * 0.85);
        if (speed > 0.08) {
            t.dirX = dx / speed;
            t.dirZ = dz / speed;
        }

        if (p.isSpectator() || p.isPassenger() || p.isFallFlying()) return;
        if (CompatConfig.REQUIRE_SPRINT.get() && !p.isSprinting()) return;
        if (t.peak < CompatConfig.MIN_SPEED.get()) return;

        ServerLevel level = p.serverLevel();
        long now = level.getGameTime();
        if (now < t.nextTick) return;

        Vec3 dir = new Vec3(t.dirX, 0, t.dirZ);
        if (dir.lengthSqr() < 1.0e-4) {
            Vec3 look = p.getLookAngle();
            dir = new Vec3(look.x, 0, look.z);
            if (dir.lengthSqr() < 1.0e-4) return;
        }
        dir = dir.normalize();

        double lead = Mth.clamp(t.peak * CompatConfig.LOOKAHEAD_TICKS.get(), 0.7, 2.2);
        List<BlockPos> targets = findTargets(level, p, dir, lead);
        if (targets.isEmpty()) return;

        boolean any = false;
        int accepted = 0;
        List<BlockPos> procedural = new ArrayList<>();
        for (BlockPos pos : targets) {
            GlassImpacts.Kind kind = GlassImpacts.classify(level, pos, level.getBlockState(pos));
            boolean ok;
            if (kind == GlassImpacts.Kind.BGB) {
                ok = impact(level, p, pos, dir, Math.max(t.peak, 0.28));
                if (ok) procedural.add(pos);
            } else {
                ok = GlassImpacts.normalBreak(level, pos, actor(p)); // glass Better Glass Breaking cannot model
            }
            any |= ok;
            if (ok) accepted++;
        }
        if (!procedural.isEmpty() && CompatConfig.NEIGHBOR_CRACKS.get()) {
            crackNeighbors(level, p, procedural, dir);
        }
        if (CompatConfig.DEBUG.get()) {
            LOG.info("[glassbreakcompat] sprint impact: player={} targets={} accepted={} peakSpeed={}",
                    p.getName().getString(), targets, accepted, String.format("%.3f", t.peak));
        }
        if (any) t.nextTick = now + CompatConfig.COOLDOWN_TICKS.get();
    }

    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
        tracks.remove(e.getEntity().getUUID());
    }

    /** Glass blocks that the player is pressing against, i.e. in front of them but not already overlapping. */
    private static List<BlockPos> findTargets(ServerLevel level, ServerPlayer p, Vec3 dir, double lead) {
        AABB body = p.getBoundingBox();
        AABB probe = body.expandTowards(dir.x * lead, 0, dir.z * lead);
        VoxelShape probeShape = Shapes.create(probe);
        VoxelShape bodyShape = Shapes.create(body.deflate(0.001));

        List<BlockPos> out = new ArrayList<>();
        BlockPos min = BlockPos.containing(probe.minX, probe.minY, probe.minZ);
        BlockPos max = BlockPos.containing(probe.maxX, probe.maxY - 0.01, probe.maxZ);
        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            BlockState state = level.getBlockState(pos);
            if (GlassImpacts.classify(level, pos, state) == GlassImpacts.Kind.NONE) continue;

            VoxelShape shape = state.getCollisionShape(level, pos)
                    .move(pos.getX(), pos.getY(), pos.getZ());
            if (shape.isEmpty()) continue;
            if (!Shapes.joinIsNotEmpty(shape, probeShape, BooleanOp.AND)) continue;
            if (Shapes.joinIsNotEmpty(shape, bodyShape, BooleanOp.AND)) continue;

            out.add(pos.immutable());
            if (out.size() >= 8) break;
        }
        return out;
    }

    /**
     * The entity to hand Better Glass Breaking as the impact source. It rejects players who may not build
     * (adventure mode), so those impacts are sent without a player source.
     */
    public static Entity actor(Entity e) {
        if (e instanceof ServerPlayer sp && !sp.getAbilities().mayBuild && CompatConfig.ALLOW_ADVENTURE.get()) {
            return null;
        }
        return e;
    }

    /** True when Better Glass Breaking can break this block (intact glass or already-damaged glass). */
    public static boolean handledByBgb(ServerLevel level, BlockPos pos, BlockState state) {
        if (state.is(BetterGlassBreaking.PANE.get()) || state.is(BetterGlassBreaking.CUBE.get())) {
            return true; // already-damaged glass: keep breaking what's left of it
        }
        return PaneImpacts.eligibleAt(level, pos);
    }

    private static boolean impact(ServerLevel level, ServerPlayer p, BlockPos pos, Vec3 dir, double speed) {
        GameplayConfig cfg = withArrows(GlassConfig.current());
        SourceFacts facts = new SourceFacts(Source.ARROW, speed, CompatConfig.IMPACT_ENERGY.get(), 0);

        Vec3 n = new Vec3(-dir.z, 0, dir.x);
        Vec3 center = Vec3.atCenterOf(pos);
        double lateral = clamp(p.position().subtract(center).dot(n), -0.25, 0.25);
        int cols = CompatConfig.IMPACT_COLUMNS.get();
        int rows = CompatConfig.IMPACT_ROWS.get();
        double colStep = cols <= 1 ? 0.0 : cols == 2 ? 0.30 : 0.25;
        Vec3 motion = dir.scale(1.8);

        boolean accepted = false;
        for (int r = 0; r < rows; r++) {
            double frac = rows == 1 ? 0.5 : 0.15 + 0.70 * r / (rows - 1.0);
            double y = pos.getY() + frac;
            for (int c = 0; c < cols; c++) {
                double off = lateral + (c - (cols - 1) / 2.0) * colStep;
                Vec3 start = new Vec3(center.x, y, center.z).add(n.scale(off)).subtract(dir.scale(0.9));
                accepted |= PaneImpacts.enqueue(level, pos, start, motion, facts, cfg, actor(p),
                        ItemStack.EMPTY, null, false, false);
            }
        }
        return accepted;
    }

    /** Light impacts on intact glass touching the broken blocks: cracks only, never a break. */
    public static void crackNeighbors(ServerLevel level, ServerPlayer p, List<BlockPos> targets, Vec3 dir) {
        double power = CompatConfig.NEIGHBOR_POWER.get();
        if (power <= 0) return;
        GameplayConfig cfg = withArrows(GlassConfig.current());
        SourceFacts facts = new SourceFacts(Source.ARROW, 0.3, power, 0);
        Vec3 motion = dir.scale(1.8);
        Vec3 n = new Vec3(-dir.z, 0, dir.x);

        Set<BlockPos> seen = new HashSet<>(targets);
        for (BlockPos t : targets) {
            for (Direction d : Direction.values()) {
                BlockPos np = t.relative(d);
                if (!seen.add(np)) continue;
                BlockState st = level.getBlockState(np);
                if (st.isAir() || st.is(BetterGlassBreaking.PANE.get()) || st.is(BetterGlassBreaking.CUBE.get())) continue;
                if (!PaneImpacts.eligibleAt(level, np)) continue;

                Vec3 towardBrokenSide = new Vec3(-d.getStepX(), -d.getStepY(), -d.getStepZ()).scale(0.35);
                Vec3 center = Vec3.atCenterOf(np).add(towardBrokenSide);
                for (int i = 0; i < 2; i++) {
                    double side = i == 0 ? -0.12 : 0.12;
                    double up = i == 0 ? 0.10 : -0.10;
                    Vec3 aim = center.add(n.scale(side)).add(0, up, 0);
                    Vec3 start = aim.subtract(dir.scale(0.9));
                    PaneImpacts.enqueue(level, np, start, motion, facts, cfg, actor(p),
                            ItemStack.EMPTY, null, false, false);
                }
            }
        }
    }

    /** Sprint impacts must work even if the server turned arrow damage off in BGB's config. */
    public static GameplayConfig withArrows(GameplayConfig c) {
        if (c.arrows()) return c;
        return new GameplayConfig(true, c.projectiles(), c.explosions(), c.melee(), c.strength(), c.holeSize(),
                c.drops(), c.healing(), c.healDelayTicks(), c.healIntervalTicks(), c.maxWebs(), c.propagation(),
                c.policy(), c.shatterThreshold(), c.taczCompat(), c.taczDamageMultiplier(), c.bulletGlassToughness());
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
