package com.stalkermod;

import com.mojang.logging.LogUtils;
import com.stalkermod.registry.ModEntities;
import com.stalkermod.registry.ModSounds;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod(StalkerMod.MOD_ID)
public class StalkerMod {
    public static final String MOD_ID = "stalker";
    public static final Logger LOGGER = LogUtils.getLogger();

    public StalkerMod() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        ModEntities.ENTITIES.register(modBus);
        ModSounds.SOUND_EVENTS.register(modBus);
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, StalkerConfig.SPEC);
        LOGGER.info("The Stalker is watching...");
    }
}
