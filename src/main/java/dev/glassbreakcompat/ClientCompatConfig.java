package dev.glassbreakcompat;

import net.minecraftforge.common.ForgeConfigSpec;

public final class ClientCompatConfig {
    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.BooleanValue TUNE_SHARDS;
    public static final ForgeConfigSpec.DoubleValue SHARD_GRAVITY;
    public static final ForgeConfigSpec.IntValue SHARD_LIFETIME;
    public static final ForgeConfigSpec.BooleanValue WALK_SOUND;
    public static final ForgeConfigSpec.DoubleValue WALK_VOLUME;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.push("shards");
        TUNE_SHARDS = b.comment("Adjust Better Glass Breaking's shard particles (fall speed and lifetime).")
                .define("enabled", true);
        SHARD_GRAVITY = b.comment("Gravity multiplier for shards. 1.0 = Better Glass Breaking default; higher falls faster.")
                .defineInRange("gravityMultiplier", 2.2, 0.5, 10.0);
        SHARD_LIFETIME = b.comment("Total shard lifetime in ticks (20 per second). 700 = about 30 seconds lying on the floor after the fall.")
                .defineInRange("lifetimeTicks", 700, 20, 12000);
        b.pop();
        b.push("walkSounds");
        WALK_SOUND = b.comment("Play glass-crunch sounds when a player walks over shards lying on the ground.")
                .define("enabled", true);
        WALK_VOLUME = b.comment("Volume of the crunch sound.")
                .defineInRange("volume", 0.45, 0.0, 2.0);
        b.pop();
        SPEC = b.build();
    }

    private ClientCompatConfig() {}
}
