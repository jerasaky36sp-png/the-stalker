package com.stalkermod.entity.ai;

import com.stalkermod.entity.StalkerEntity;
import com.stalkermod.entity.StalkerState;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;

import java.util.EnumSet;

/** CHASE: run at the player with vanilla pathfinding, melee when in reach, give up when far/late. */
public class ChaseGoal extends Goal {
    private static final double SPEED = 1.4D;
    private static final double REACH_SQR = 2.6D * 2.6D;
    private static final int MAX_CHASE_TICKS = 600; // 30 s

    private final StalkerEntity stalker;
    private int repathTicks;
    private int attackCooldown;

    public ChaseGoal(StalkerEntity stalker) {
        this.stalker = stalker;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public boolean canUse() {
        return stalker.getState() == StalkerState.CHASE && stalker.hasValidFocus();
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public void start() {
        repathTicks = 0;
        attackCooldown = 10;
        stalker.setTarget(stalker.getFocus());
    }

    @Override
    public void tick() {
        Player p = stalker.getFocus();
        if (p == null) {
            return;
        }
        stalker.getLookControl().setLookAt(p, 30.0F, 30.0F);

        if (stalker.distanceTo(p) > StalkerEntity.CHASE_GIVE_UP_DISTANCE
                || stalker.getStateTicks() > MAX_CHASE_TICKS) {
            stalker.setState(StalkerState.DISAPPEAR);
            return;
        }

        if (--repathTicks <= 0) {
            repathTicks = 4 + stalker.getRandom().nextInt(4);
            stalker.getNavigation().moveTo(p, SPEED);
        }

        if (attackCooldown > 0) {
            attackCooldown--;
        }
        if (attackCooldown == 0 && stalker.distanceToSqr(p) <= REACH_SQR) {
            stalker.swing(InteractionHand.MAIN_HAND);
            stalker.doHurtTarget(p);
            attackCooldown = 20;
        }
    }

    @Override
    public void stop() {
        stalker.setTarget(null);
        stalker.getNavigation().stop();
    }
}
