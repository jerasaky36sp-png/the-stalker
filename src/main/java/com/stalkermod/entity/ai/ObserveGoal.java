package com.stalkermod.entity.ai;

import com.stalkermod.entity.StalkerEntity;
import com.stalkermod.entity.StalkerState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;

import java.util.EnumSet;

/** OBSERVE: walk to a hiding spot far from the player, then stand and watch. Never attacks. */
public class ObserveGoal extends Goal {
    private static final int OBSERVE_DURATION = 200;   // 10 s
    private static final int SPOTTED_LIMIT = 60;       // 3 s of being stared at
    private static final double PANIC_DISTANCE = 10.0D;

    private final StalkerEntity stalker;
    private int seenTicks;

    public ObserveGoal(StalkerEntity stalker) {
        this.stalker = stalker;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public boolean canUse() {
        return stalker.getState() == StalkerState.OBSERVE && stalker.hasValidFocus();
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public void start() {
        seenTicks = 0;
        Player p = stalker.getFocus();
        if (p != null) {
            BlockPos spot = StalkerSpots.findCoverSpot(stalker, p, 24.0D, 34.0D, false);
            if (spot != null) {
                stalker.getNavigation().moveTo(spot.getX() + 0.5D, spot.getY(), spot.getZ() + 0.5D, 0.8D);
            }
        }
    }

    @Override
    public void tick() {
        Player p = stalker.getFocus();
        if (p == null) {
            return;
        }
        stalker.getLookControl().setLookAt(p, 30.0F, 30.0F);

        if (stalker.distanceTo(p) < PANIC_DISTANCE) {
            stalker.setState(StalkerState.CHASE);
            return;
        }
        if (stalker.isPlayerLookingAtMe(p)) {
            seenTicks++;
        } else {
            seenTicks = Math.max(0, seenTicks - 1);
        }
        if (seenTicks > SPOTTED_LIMIT) {
            stalker.setState(StalkerState.DISAPPEAR);
            return;
        }
        if (stalker.getStateTicks() > OBSERVE_DURATION) {
            stalker.setState(StalkerState.STALK);
        }
    }

    @Override
    public void stop() {
        stalker.getNavigation().stop();
    }
}
