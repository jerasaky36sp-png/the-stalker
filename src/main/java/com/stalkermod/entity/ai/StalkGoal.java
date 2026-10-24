package com.stalkermod.entity.ai;

import com.stalkermod.entity.StalkerEntity;
import com.stalkermod.entity.StalkerState;
import com.stalkermod.horror.Jumpscare;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;

import java.util.EnumSet;

/**
 * STALK: keep relocating between tree-cover spots (biased to behind the player),
 * freeze when watched, and sometimes end in a chase or a vanish.
 */
public class StalkGoal extends Goal {
    private static final double CHASE_TRIGGER_DISTANCE = 12.0D;
    private static final int WATCHED_LIMIT = 50;       // 2.5 s watched in range -> react

    private final StalkerEntity stalker;
    private int repathTicks;
    private int seenTicks;
    private int duration;

    public StalkGoal(StalkerEntity stalker) {
        this.stalker = stalker;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public boolean canUse() {
        return stalker.getState() == StalkerState.STALK && stalker.hasValidFocus();
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public void start() {
        repathTicks = 0;
        seenTicks = 0;
        duration = 600 + stalker.getRandom().nextInt(600); // 30-60 s
    }

    @Override
    public void tick() {
        Player p = stalker.getFocus();
        if (p == null) {
            return;
        }
        double dist = stalker.distanceTo(p);

        if (dist < CHASE_TRIGGER_DISTANCE) {
            stalker.setState(StalkerState.CHASE);
            return;
        }

        if (dist < 40.0D && stalker.isPlayerLookingAtMe(p)) {
            // watched: freeze and stare back
            stalker.getNavigation().stop();
            stalker.getLookControl().setLookAt(p, 30.0F, 30.0F);
            seenTicks++;
            if (seenTicks > WATCHED_LIMIT) {
                float roll = stalker.getRandom().nextFloat();
                if (roll < 0.30F && p instanceof ServerPlayer sp && Jumpscare.trigger(stalker, sp, false)) {
                    return; // jumpscare succeeded (it also starts the chase)
                }
                stalker.setState(roll < 0.55F ? StalkerState.CHASE : StalkerState.DISAPPEAR);
            }
            return;
        }
        seenTicks = Math.max(0, seenTicks - 2);

        if (--repathTicks <= 0) {
            repathTicks = 40 + stalker.getRandom().nextInt(40);
            BlockPos spot = StalkerSpots.findCoverSpot(stalker, p, 16.0D, 26.0D, true);
            if (spot != null) {
                stalker.getNavigation().moveTo(spot.getX() + 0.5D, spot.getY(), spot.getZ() + 0.5D, 0.9D);
            }
        }

        if (stalker.getStateTicks() > duration) {
            stalker.setState(stalker.getRandom().nextFloat() < 0.5F
                    ? StalkerState.CHASE : StalkerState.DISAPPEAR);
        }
    }

    @Override
    public void stop() {
        stalker.getNavigation().stop();
    }
}
