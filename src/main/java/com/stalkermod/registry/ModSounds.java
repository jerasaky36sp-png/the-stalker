package com.stalkermod.registry;

import com.stalkermod.StalkerMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUND_EVENTS =
            DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, StalkerMod.MOD_ID);

    public static final RegistryObject<SoundEvent> FOOTSTEP_BEHIND = register("footstep_behind");
    public static final RegistryObject<SoundEvent> WHISPER = register("whisper");
    public static final RegistryObject<SoundEvent> BRANCH_SNAP = register("branch_snap");
    public static final RegistryObject<SoundEvent> CHASE_START = register("chase_start");
    public static final RegistryObject<SoundEvent> VANISH = register("vanish");

    private static RegistryObject<SoundEvent> register(String name) {
        return SOUND_EVENTS.register(name,
                () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(StalkerMod.MOD_ID, name)));
    }
}
