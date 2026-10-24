package com.stalkermod;

import net.minecraftforge.common.ForgeConfigSpec;

public class StalkerConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.BooleanValue HORROR_EVENTS_ENABLED;
    public static final ForgeConfigSpec.BooleanValue STALKER_APPEARANCES;
    public static final ForgeConfigSpec.IntValue MIN_INTERVAL_SECONDS;
    public static final ForgeConfigSpec.IntValue MAX_INTERVAL_SECONDS;
    public static final ForgeConfigSpec.BooleanValue HOUSE_EVENTS_ENABLED;
    public static final ForgeConfigSpec.BooleanValue JUMPSCARE_ENABLED;
    public static final ForgeConfigSpec.IntValue JUMPSCARE_COOLDOWN_SECONDS;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.push("horror");
        HORROR_EVENTS_ENABLED = b.comment("Enable random night horror events (sounds, appearances).")
                .define("horrorEventsEnabled", true);
        STALKER_APPEARANCES = b.comment("Allow horror events to spawn The Stalker between trees.")
                .define("stalkerAppearances", true);
        MIN_INTERVAL_SECONDS = b.comment("Minimum seconds between horror events per player.")
                .defineInRange("minIntervalSeconds", 30, 5, 3600);
        MAX_INTERVAL_SECONDS = b.comment("Maximum seconds between horror events per player.")
                .defineInRange("maxIntervalSeconds", 90, 10, 7200);
        HOUSE_EVENTS_ENABLED = b.comment("At night, if you are indoors near a door: knocking, scratching, and The Stalker peering through your windows.")
                .define("houseEventsEnabled", true);
        JUMPSCARE_ENABLED = b.comment("Allow The Stalker to jumpscare (teleports in front of you, snaps your view, loud scream).")
                .define("jumpscareEnabled", true);
        JUMPSCARE_COOLDOWN_SECONDS = b.comment("Minimum seconds between jumpscares for the same player.")
                .defineInRange("jumpscareCooldownSeconds", 300, 10, 7200);
        b.pop();
        SPEC = b.build();
    }
}
