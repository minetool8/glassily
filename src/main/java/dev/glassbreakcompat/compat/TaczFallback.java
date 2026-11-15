package dev.glassbreakcompat.compat;

import com.tacz.guns.api.event.server.AmmoHitBlockEvent;
import com.tacz.guns.entity.EntityKineticBullet;
import dev.betterglassbreaking.core.GameplayConfig;
import dev.betterglassbreaking.core.GameplayRules;
import dev.betterglassbreaking.core.GameplayRules.Source;
import dev.betterglassbreaking.core.GameplayRules.SourceFacts;
import dev.betterglassbreaking.forge.GlassConfig;
import dev.betterglassbreaking.forge.PaneImpacts;
import dev.betterglassbreaking.forge.compat.TaczCompat;
import dev.glassbreakcompat.CompatConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.EventPriority;

/**
 * Only used when TACZ is installed. Better Glass Breaking ships its own TACZ adapter; this is a safety net
 * for when that adapter switches itself off (for example after a TACZ update changes internals).
 */
public final class TaczFallback {
    private TaczFallback() {}

    public static void register() {
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGH, false, AmmoHitBlockEvent.class, TaczFallback::onHit);
    }

    private static void onHit(AmmoHitBlockEvent e) {
        if (!CompatConfig.TACZ_FALLBACK.get()) return;
        if (TaczCompat.active()) return; // BGB's native adapter is handling bullets
        if (!(e.getLevel() instanceof ServerLevel level)) return;

        BlockHitResult hit = e.getHitResult();
        BlockPos pos = hit.getBlockPos();
        if (!PaneImpacts.eligibleAt(level, pos)) return;

        EntityKineticBullet bullet = e.getAmmo();
        Vec3 motion = bullet.getDeltaMovement();
        if (motion.lengthSqr() < 1.0e-6) return;

        GameplayConfig cfg = GlassConfig.current();
        if (!cfg.taczCompat() || !GameplayRules.enabled(Source.BULLET, cfg)) return;

        Vec3 dir = motion.normalize();
        double damage = bullet.getDamage(hit.getLocation()) * cfg.taczDamageMultiplier();
        SourceFacts facts = new SourceFacts(Source.BULLET, motion.length(), damage, 0);
        Vec3 start = hit.getLocation().subtract(dir.scale(0.3));

        boolean accepted = PaneImpacts.enqueue(level, pos, start, dir.scale(1.6), facts, cfg,
                bullet.getOwner(), ItemStack.EMPTY, null, false, false);
        if (accepted && e.isCancelable()) {
            e.setCanceled(true); // stop TACZ's own glass deletion from also firing
        }
    }
}
