package dev.glassbreakcompat;

import dev.betterglassbreaking.core.GameplayRules.Source;
import dev.betterglassbreaking.core.GameplayRules.SourceFacts;
import dev.betterglassbreaking.forge.GlassConfig;
import dev.betterglassbreaking.forge.PaneImpacts;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** Shared helpers for every way of breaking glass: sprint, fall, melee, gun. */
public final class GlassImpacts {
    public enum Kind { NONE, BGB, NORMAL }

    private GlassImpacts() {}

    /** BGB = procedural via Better Glass Breaking, NORMAL = plain block break, NONE = not glass. */
    public static Kind classify(ServerLevel level, BlockPos pos, BlockState state) {
        if (state.isAir()) return Kind.NONE;
        if (SprintImpactHandler.handledByBgb(level, pos, state)) return Kind.BGB;
        if (GlassNames.isOtherGlass(state) && state.getDestroySpeed(level, pos) >= 0) return Kind.NORMAL;
        return Kind.NONE;
    }

    /** One fracture ray aimed at {@code aim}, travelling along {@code dir}, with the given power. */
    public static boolean impact(ServerLevel level, BlockPos pos, Vec3 aim, Vec3 dir, double power, Entity actor) {
        SourceFacts facts = new SourceFacts(Source.ARROW, 0.4, power, 0);
        Vec3 start = aim.subtract(dir.scale(0.9));
        return PaneImpacts.enqueue(level, pos, start, dir.scale(1.8), facts,
                SprintImpactHandler.withArrows(GlassConfig.current()), SprintImpactHandler.actor(actor),
                ItemStack.EMPTY, null, false, false);
    }

    /** Plain vanilla-style break (particles, sound, no drops) for glass Better Glass Breaking cannot model. */
    public static boolean normalBreak(ServerLevel level, BlockPos pos, Entity actor) {
        if (actor instanceof ServerPlayer sp) {
            if (!level.mayInteract(sp, pos)) return false;
            // Adventure-mode players (who cannot build) are allowed unless the config says otherwise.
            if (!sp.getAbilities().mayBuild && !CompatConfig.ALLOW_ADVENTURE.get()) return false;
        }
        return level.destroyBlock(pos, false, SprintImpactHandler.actor(actor));
    }
}
