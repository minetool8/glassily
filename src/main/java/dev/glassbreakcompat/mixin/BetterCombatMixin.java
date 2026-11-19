package dev.glassbreakcompat.mixin;

import dev.glassbreakcompat.MeleeGlass;
import net.bettercombat.api.AttackHand;
import net.bettercombat.api.WeaponAttributes;
import net.bettercombat.network.Packets;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Better Combat runs lambda$initializeHandlers$5 on the server for every accepted attack request, with or
 * without entity targets. Target and handler are both static, with BC 1.9.0's exact descriptor.
 */
@Pseudo
@Mixin(targets = "net.bettercombat.network.ServerNetwork", remap = false)
public abstract class BetterCombatMixin {

    @Inject(method = "lambda$initializeHandlers$5", at = @At("HEAD"), remap = false, require = 0)
    private static void glassbreakcompat$swing(ServerPlayer player, Packets.C2S_AttackRequest request,
                                               WeaponAttributes weapon, WeaponAttributes.Attack attack,
                                               AttackHand hand, ServerLevel level, boolean flag,
                                               ServerGamePacketListenerImpl listener, CallbackInfo ci) {
        MeleeGlass.swing(player, level, weapon.attackRange(), attack.hitbox(), attack.damageMultiplier());
    }
}
