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
import dev.glassbreakcompat.GlassImpacts;
import dev.glassbreakcompat.SprintImpactHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.EventPriority;

/**
 * Only used when TACZ is installed.
 * - Glass Better Glass Breaking can model: its own TACZ adapter handles it; this is the safety net when
 *   that adapter is switched off.
 * - Glass-named blocks it cannot model (slabs, arches, vertical slabs...): broken normally here.
 */
public final class TaczFallback {
    private TaczFallback() {}

    public static void register() {
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGH, false, AmmoHitBlockEvent.class, TaczFallback::onHit);
    }

    private static void onHit(AmmoHitBlockEvent e) {
        if (!(e.getLevel() instanceof ServerLevel level)) return;

        BlockHitResult hit = e.getHitResult();
        BlockPos pos = hit.getBlockPos();
        EntityKineticBullet bullet = e.getAmmo();

        GlassImpacts.Kind kind = GlassImpacts.classify(level, pos, level.getBlockState(pos));
        if (kind == GlassImpacts.Kind.NONE) return;

        if (kind == GlassImpacts.Kind.NORMAL) {
            if (GlassImpacts.normalBreak(level, pos, SprintImpactHandler.actor(bullet.getOwner())) && e.isCancelable()) {
                e.setCanceled(true); // bullet carries on through the broken glass
            }
            return;
        }

        // Kind.BGB
        if (!CompatConfig.TACZ_FALLBACK.get()) return;
        if (TaczCompat.active()) return; // BGB's native adapter is handling bullets
        if (!PaneImpacts.eligibleAt(level, pos)) return;

        Vec3 motion = bullet.getDeltaMovement();
        if (motion.lengthSqr() < 1.0e-6) return;

        GameplayConfig cfg = GlassConfig.current();
        if (!cfg.taczCompat() || !GameplayRules.enabled(Source.BULLET, cfg)) return;

        Vec3 dir = motion.normalize();
        double damage = bullet.getDamage(hit.getLocation()) * cfg.taczDamageMultiplier();
        SourceFacts facts = new SourceFacts(Source.BULLET, motion.length(), damage, 0);
        Vec3 start = hit.getLocation().subtract(dir.scale(0.3));

        boolean accepted = PaneImpacts.enqueue(level, pos, start, dir.scale(1.6), facts, cfg,
                SprintImpactHandler.actor(bullet.getOwner()), ItemStack.EMPTY, null, false, false);
        if (accepted && e.isCancelable()) {
            e.setCanceled(true); // stop TACZ's own glass deletion from also firing
        }
    }
}
