package com.stalkermod.entity.ai;

import com.stalkermod.entity.StalkerEntity;
import com.stalkermod.entity.StalkerState;
import com.stalkermod.horror.Jumpscare;
import com.stalkermod.registry.ModSounds;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;

import java.util.EnumSet;

/**
 * PEEK: the Stalker stands motionless outside a window, face to the glass, staring at the player.
 * It taps on the glass now and then. If the player stares back, it slams on the window, maybe
 * jumpscares, and vanishes. If ignored for 20 s it just vanishes.
 */
public class PeekGoal extends Goal {
    private static final int WATCHED_LIMIT = 25;   // ~1.25 s of eye contact
    private static final int MAX_PEEK_TICKS = 400; // 20 s

    private final StalkerEntity stalker;
    private int watchedTicks;
    private int tapCooldown;
    private boolean reacting;
    private int reactTicks;
    private int pounds;

    public PeekGoal(StalkerEntity stalker) {
        this.stalker = stalker;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public boolean canUse() {
        return stalker.getState() == StalkerState.PEEK && stalker.hasValidFocus();
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public void start() {
        stalker.getNavigation().stop();
        watchedTicks = 0;
        tapCooldown = 30;
        reacting = false;
        reactTicks = 0;
        pounds = 0;
    }

    @Override
    public void tick() {
        Player p = stalker.getFocus();
        if (p == null) {
            return;
        }
        stalker.getNavigation().stop();
        stalker.getLookControl().setLookAt(p, 30.0F, 30.0F);

        if (reacting) {
            reactTicks++;
            if (reactTicks % 5 == 1 && pounds < 5) {
                pounds++;
                stalker.level().playSound(null, stalker.getX(), stalker.getEyeY(), stalker.getZ(),
                        ModSounds.POUND.get(), SoundSource.HOSTILE, 2.5F, 0.9F + stalker.getRandom().nextFloat() * 0.2F);
            }
            if (reactTicks == 10 && stalker.getRandom().nextFloat() < 0.5F && p instanceof ServerPlayer sp) {
                Jumpscare.showIfAllowed(sp);
            }
            if (reactTicks >= 30) {
                stalker.setState(StalkerState.DISAPPEAR);
            }
            return;
        }

        if (--tapCooldown <= 0) {
            tapCooldown = 30 + stalker.getRandom().nextInt(60);
            stalker.level().playSound(null, stalker.getX(), stalker.getEyeY(), stalker.getZ(),
                    ModSounds.GLASS_TAP.get(), SoundSource.HOSTILE, 1.5F, 0.95F + stalker.getRandom().nextFloat() * 0.1F);
        }

        if (stalker.isPlayerLookingAtMeThroughGlass(p)) {
            watchedTicks++;
        } else {
            watchedTicks = Math.max(0, watchedTicks - 1);
        }
        if (watchedTicks >= WATCHED_LIMIT) {
            reacting = true;
            reactTicks = 0;
            return;
        }
        if (stalker.getStateTicks() > MAX_PEEK_TICKS) {
            stalker.setState(StalkerState.DISAPPEAR);
        }
    }
}
