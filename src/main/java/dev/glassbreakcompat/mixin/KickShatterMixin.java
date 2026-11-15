package dev.glassbreakcompat.mixin;

import dev.glassbreakcompat.CompatConfig;
import dev.glassbreakcompat.SprintImpactHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Kick & Shatter's smashGlassInFront() handles BOTH glass and doors for sprinting players.
 * Only its glass-smash call is redirected here, so door opening keeps working.
 * Glass that Better Glass Breaking can model is left to SprintImpactHandler; anything else
 * (glass BGB does not support) is still destroyed the old way.
 */
@Pseudo
@Mixin(targets = "kickshatter.WindowBreaker", remap = false)
public abstract class KickShatterMixin {

    @Redirect(method = "smashGlassInFront",
            at = @At(value = "INVOKE",
                    target = "Lkickshatter/WindowBreaker;smash(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;)V",
                    remap = false),
            remap = false, require = 0)
    private void glassbreakcompat$smash(ServerLevel level, BlockPos pos) {
        if (CompatConfig.REPLACE_KICKSHATTER.get()
                && SprintImpactHandler.handledByBgb(level, pos, level.getBlockState(pos))) {
            return; // procedural break is done by SprintImpactHandler
        }
        level.destroyBlock(pos, false);
    }
}
