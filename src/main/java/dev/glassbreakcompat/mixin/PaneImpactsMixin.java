package dev.glassbreakcompat.mixin;

import dev.betterglassbreaking.forge.PaneImpacts;
import dev.glassbreakcompat.GlassNames;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Widens Better Glass Breaking's idea of "glass" to every block whose name contains glass / pane / window.
 * Because arrows, TACZ bullets, explosions, melee and my sprint impacts all go through these checks,
 * one hook gives every source the effect.
 */
@Mixin(value = PaneImpacts.class, remap = false)
public abstract class PaneImpactsMixin {

    @Inject(method = "supported", at = @At("RETURN"), cancellable = true, remap = false)
    private static void glassbreakcompat$supported(BlockState state, CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValueZ() && GlassNames.matches(state)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "eligible", at = @At("RETURN"), cancellable = true, remap = false)
    private static void glassbreakcompat$eligible(BlockState state, CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValueZ() && GlassNames.matches(state)) {
            cir.setReturnValue(true);
        }
    }
}
