package dev.glassbreakcompat;

import net.minecraftforge.common.ForgeConfigSpec;

import java.util.List;

public final class CompatConfig {
    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.BooleanValue SPRINT_BREAK;
    public static final ForgeConfigSpec.BooleanValue REQUIRE_SPRINT;
    public static final ForgeConfigSpec.DoubleValue MIN_SPEED;
    public static final ForgeConfigSpec.IntValue COOLDOWN_TICKS;
    public static final ForgeConfigSpec.DoubleValue IMPACT_ENERGY;
    public static final ForgeConfigSpec.IntValue IMPACT_COLUMNS;
    public static final ForgeConfigSpec.IntValue IMPACT_ROWS;
    public static final ForgeConfigSpec.BooleanValue NEIGHBOR_CRACKS;
    public static final ForgeConfigSpec.DoubleValue NEIGHBOR_POWER;
    public static final ForgeConfigSpec.BooleanValue ALLOW_ADVENTURE;
    public static final ForgeConfigSpec.BooleanValue REPLACE_KICKSHATTER;
    public static final ForgeConfigSpec.BooleanValue TACZ_FALLBACK;
    public static final ForgeConfigSpec.BooleanValue DEBUG;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> NAME_KEYWORDS;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> NAME_EXCLUDES;

    public static final List<String> DEFAULT_KEYWORDS = List.of("glass", "pane", "window");

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();

        DEBUG = b.comment("Log what the sprint-impact code is doing (turn off once it works).").define("debug", true);

        b.push("sprint");
        SPRINT_BREAK = b.comment("Sprinting into glass fractures it with Better Glass Breaking's procedural system.")
                .define("enabled", true);
        REQUIRE_SPRINT = b.comment("Only break glass while sprinting.")
                .define("requireSprint", true);
        MIN_SPEED = b.comment("Minimum recent horizontal speed (blocks/tick) needed to break glass. Sprinting is ~0.28.")
                .defineInRange("minSpeed", 0.12, 0.0, 2.0);
        COOLDOWN_TICKS = b.comment("Ticks before the same player can trigger another sprint impact.")
                .defineInRange("cooldownTicks", 8, 0, 200);
        IMPACT_ENERGY = b.comment("Power of each fracture point, fed to Better Glass Breaking's arrow rules (not linear: 8 only cracks, 20 breaks most of a pane, 40 shatters it). Raise for bigger breaks.")
                .defineInRange("impactPower", 40.0, 1.0, 200.0);
        IMPACT_COLUMNS = b.comment("Fracture points across the width of each glass block. 2 gives a player-wide opening.")
                .defineInRange("impactColumns", 2, 1, 3);
        IMPACT_ROWS = b.comment("Fracture points up the height of each glass block.")
                .defineInRange("impactRows", 3, 1, 4);
        NEIGHBOR_CRACKS = b.comment("Crack (but do not break) intact glass touching the pane or block that broke.")
                .define("neighborCracks", true);
        NEIGHBOR_POWER = b.comment("Power of the crack impacts on neighbouring glass. About 6 only cracks; raise it and neighbours start to chip or break. 0 turns it off.")
                .defineInRange("neighborCrackPower", 6.0, 0.0, 40.0);
        b.pop();

        b.push("permissions");
        ALLOW_ADVENTURE = b.comment("Let players in adventure mode break glass by sprinting or shooting. "
                        + "Better Glass Breaking normally refuses impacts from players who cannot build.")
                .define("allowAdventureMode", true);
        b.pop();

        b.push("kickshatter");
        REPLACE_KICKSHATTER = b.comment("Stop Kick & Shatter from instantly deleting glass when a player sprints into it "
                        + "(this mod handles that case instead). Zombie behaviour and doors are untouched.")
                .define("replacePlayerGlassBreak", true);
        b.pop();

        b.push("tacz");
        TACZ_FALLBACK = b.comment("If Better Glass Breaking's own TACZ adapter is not active, route TACZ bullet hits "
                        + "on glass into Better Glass Breaking from here.")
                .define("fallbackWhenAdapterInactive", true);
        b.pop();

        b.push("matching");
        NAME_KEYWORDS = b.comment("Any block whose registry path contains one of these words is treated as breakable glass, "
                        + "as long as it is a full cube or a pane-style block.")
                .defineListAllowEmpty("keywords", DEFAULT_KEYWORDS, o -> o instanceof String);
        NAME_EXCLUDES = b.comment("Registry paths containing any of these words are never treated as glass.")
                .defineListAllowEmpty("excludeKeywords", List.of(), o -> o instanceof String);
        b.pop();

        SPEC = b.build();
    }

    private CompatConfig() {}
}
