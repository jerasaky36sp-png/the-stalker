package com.stalkermod.registry;

import com.stalkermod.StalkerMod;
import com.stalkermod.entity.StalkerEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, StalkerMod.MOD_ID);

    // ID: stalker:the_stalker  | hitbox 0.6 wide x 2.7 tall
    public static final RegistryObject<EntityType<StalkerEntity>> THE_STALKER =
            ENTITIES.register("the_stalker", () ->
                    EntityType.Builder.of(StalkerEntity::new, MobCategory.MONSTER)
                            .sized(0.6F, 2.7F)
                            .clientTrackingRange(10)
                            .build("the_stalker"));
}
