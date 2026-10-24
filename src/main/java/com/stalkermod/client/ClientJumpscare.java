package com.stalkermod.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.stalkermod.StalkerMod;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.gui.overlay.ForgeGui;

/**
 * Full-screen jumpscare picture (client only). Image: textures/gui/jumpscare.png (1024x576).
 * Timeline (ms): slam in + heavy shake, two quick blackouts (flicker), slow creeping zoom, fade out.
 */
public class ClientJumpscare {
    private static final ResourceLocation IMAGE =
            new ResourceLocation(StalkerMod.MOD_ID, "textures/gui/jumpscare.png");
    private static final int IMG_W = 1024;
    private static final int IMG_H = 576;
    private static final long DURATION_MS = 1800L;

    private static long startMs = -1L;

    public static void start() {
        startMs = Util.getMillis();
    }

    public static void render(ForgeGui gui, GuiGraphics g, float partialTick, int width, int height) {
        if (startMs < 0L) {
            return;
        }
        long t = Util.getMillis() - startMs;
        if (t >= DURATION_MS) {
            startMs = -1L;
            return;
        }

        float alpha = 1.0F;
        if ((t >= 380L && t < 430L) || (t >= 720L && t < 790L)) {
            alpha = 0.0F; // flicker
        } else if (t > 1300L) {
            alpha = (DURATION_MS - t) / 500.0F;
        }
        if (alpha <= 0.0F) {
            return;
        }

        float zoom = 1.0F + 0.18F * (t / (float) DURATION_MS);
        float scale = Math.max(width / (float) IMG_W, height / (float) IMG_H) * zoom;
        int drawW = Math.round(IMG_W * scale);
        int drawH = Math.round(IMG_H * scale);

        int shake = t < 500L ? 12 : 3;
        int ox = Math.round((float) ((Math.random() - 0.5D) * 2.0D * shake));
        int oy = Math.round((float) ((Math.random() - 0.5D) * 2.0D * shake));
        int x = (width - drawW) / 2 + ox;
        int y = (height - drawH) / 2 + oy;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        g.setColor(1.0F, 1.0F, 1.0F, alpha);
        g.blit(IMAGE, x, y, drawW, drawH, 0.0F, 0.0F, IMG_W, IMG_H, IMG_W, IMG_H);
        g.setColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.disableBlend();
    }
}
