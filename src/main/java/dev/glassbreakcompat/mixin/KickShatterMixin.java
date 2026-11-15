package dev.glassbreakcompat.mixin;

import dev.glassbreakcompat.CompatConfig;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Kick & Shatter deletes glass outright when a sprinting player touches it. Skip that so
 * SprintImpactHandler can do the procedural version instead. Doors and zombie logic are untouched.
 */
@Pseudo
@Mixin(targets = "kickshatter.WindowBreaker", remap = false)
public abstract class KickShatterMixin {

    @Inject(method = "smashGlassInFront", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void glassbreakcompat$replacePlayerSmash(ServerLevel level, Player player, CallbackInfo ci) {
        if (CompatConfig.REPLACE_KICKSHATTER.get()) {
            ci.cancel();
        }
    }
}
