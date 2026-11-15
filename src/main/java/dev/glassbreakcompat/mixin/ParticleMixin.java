package dev.glassbreakcompat.mixin;

import dev.betterglassbreaking.forge.client.ShardParticle;
import dev.glassbreakcompat.ClientCompatConfig;
import dev.glassbreakcompat.ShardWalkSounds;
import net.minecraft.client.particle.Particle;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Better Glass Breaking's ShardParticle calls Particle.tick(), so hooking Particle lets us retune
 * only those shards: faster fall, long lifetime, and register resting shards for walk-over sounds.
 */
@Mixin(Particle.class)
public abstract class ParticleMixin {
    @Shadow protected float gravity;
    @Shadow protected int lifetime;
    @Shadow protected int age;
    @Shadow protected boolean onGround;
    @Shadow protected double x;
    @Shadow protected double y;
    @Shadow protected double z;
    @Shadow protected double yd;

    @Unique private boolean glassbreakcompat$tuned;
    @Unique private boolean glassbreakcompat$resting;
    @Unique private long glassbreakcompat$key;

    @Inject(method = "tick", at = @At("HEAD"))
    private void glassbreakcompat$head(CallbackInfo ci) {
        if (glassbreakcompat$tuned || !((Object) this instanceof ShardParticle)) return;
        glassbreakcompat$tuned = true;
        if (ClientCompatConfig.TUNE_SHARDS.get()) {
            this.gravity *= ClientCompatConfig.SHARD_GRAVITY.get().floatValue();
            this.lifetime = ClientCompatConfig.SHARD_LIFETIME.get() + (int) (Math.random() * 15);
        }
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void glassbreakcompat$tail(CallbackInfo ci) {
        if (glassbreakcompat$resting || !this.onGround || Math.abs(this.yd) > 0.02) return;
        if (!((Object) this instanceof ShardParticle)) return;
        glassbreakcompat$resting = true;
        glassbreakcompat$key = BlockPos.asLong((int) Math.floor(this.x), (int) Math.floor(this.y + 0.01),
                (int) Math.floor(this.z));
        ShardWalkSounds.add(glassbreakcompat$key);
    }

    @Inject(method = "remove", at = @At("HEAD"))
    private void glassbreakcompat$remove(CallbackInfo ci) {
        if (glassbreakcompat$resting) {
            glassbreakcompat$resting = false;
            ShardWalkSounds.remove(glassbreakcompat$key);
        }
    }
}
