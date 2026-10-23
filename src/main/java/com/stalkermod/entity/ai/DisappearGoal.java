package com.stalkermod.entity.ai;

import com.stalkermod.entity.StalkerEntity;
import com.stalkermod.entity.StalkerState;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

/** DISAPPEAR: effects first, then remove the entity 1 s later. Never spawns anything. */
public class DisappearGoal extends Goal {
    private static final int VANISH_DELAY = 20;

    private final StalkerEntity stalker;

    public DisappearGoal(StalkerEntity stalker) {
        this.stalker = stalker;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public boolean canUse() {
        return stalker.getState() == StalkerState.DISAPPEAR;
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public void start() {
        stalker.getNavigation().stop();
        stalker.setTarget(null);
        stalker.playVanishEffects();
    }

    @Override
    public void tick() {
        if (stalker.getStateTicks() >= VANISH_DELAY && !stalker.isRemoved()) {
            stalker.discard();
        }
    }
}
