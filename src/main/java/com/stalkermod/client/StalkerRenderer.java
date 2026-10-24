package com.stalkermod.client;

import com.stalkermod.StalkerMod;
import com.stalkermod.entity.StalkerEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/**
 * GeckoLib renderer. Asset paths (derived from the id "the_stalker"):
 *   geo/entity/the_stalker.geo.json
 *   animations/entity/the_stalker.animation.json
 *   textures/entity/the_stalker.png
 *   textures/entity/the_stalker_glowmask.png  (glowing eyes, via AutoGlowingGeoLayer)
 */
public class StalkerRenderer extends GeoEntityRenderer<StalkerEntity> {
    public StalkerRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new DefaultedEntityGeoModel<>(new ResourceLocation(StalkerMod.MOD_ID, "the_stalker"), true));
        this.addRenderLayer(new AutoGlowingGeoLayer<>(this));
        this.shadowRadius = 0.4F;
    }
}
