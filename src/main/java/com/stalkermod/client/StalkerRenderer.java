package com.stalkermod.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.stalkermod.StalkerMod;
import com.stalkermod.entity.StalkerEntity;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.ResourceLocation;

/** Temporary renderer: stretched vanilla humanoid model. Replace with GeckoLib in stage 5. */
public class StalkerRenderer extends HumanoidMobRenderer<StalkerEntity, HumanoidModel<StalkerEntity>> {
    private static final ResourceLocation TEXTURE =
            new ResourceLocation(StalkerMod.MOD_ID, "textures/entity/the_stalker.png");

    public StalkerRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new HumanoidModel<>(ctx.bakeLayer(ModelLayers.ZOMBIE)), 0.4F);
    }

    @Override
    protected void scale(StalkerEntity entity, PoseStack poseStack, float partialTick) {
        poseStack.scale(0.9F, 1.4F, 0.9F);
    }

    @Override
    public ResourceLocation getTextureLocation(StalkerEntity entity) {
        return TEXTURE;
    }
}
