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
