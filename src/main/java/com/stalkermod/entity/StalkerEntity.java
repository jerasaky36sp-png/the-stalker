package com.stalkermod.entity;

import com.stalkermod.StalkerMod;
import com.stalkermod.entity.ai.ChaseGoal;
import com.stalkermod.entity.ai.DisappearGoal;
import com.stalkermod.entity.ai.ObserveGoal;
import com.stalkermod.entity.ai.StalkGoal;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

public class StalkerEntity extends Monster {

    public static final double CHASE_GIVE_UP_DISTANCE = 40.0D;

    private StalkerState state = StalkerState.OBSERVE;
    private int stateTicks;
    private int noPlayerTicks;
    private boolean vanishEffectsPlayed;
    @Nullable
    private Player focus;

    public StalkerEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        this.xpReward = 20;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 40.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.28D)
                .add(Attributes.ATTACK_DAMAGE, 6.0D)
                .add(Attributes.FOLLOW_RANGE, 64.0D);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new DisappearGoal(this));
        this.goalSelector.addGoal(2, new ChaseGoal(this));
        this.goalSelector.addGoal(3, new StalkGoal(this));
        this.goalSelector.addGoal(4, new ObserveGoal(this));
    }

    // ---------- state ----------

    public StalkerState getState() {
        return state;
    }

    public void setState(StalkerState newState) {
        if (this.state != newState) {
            StalkerMod.LOGGER.debug("Stalker {} : {} -> {}", this.getId(), this.state, newState);
            this.state = newState;
            this.stateTicks = 0;
        }
    }

    public int getStateTicks() {
        return stateTicks;
    }

    @Nullable
    public Player getFocus() {
        return focus;
    }

    public boolean hasValidFocus() {
        return focus != null && focus.isAlive() && !focus.isRemoved();
    }

    /** Server side only. Runs every tick, but the player search only every 10 ticks. */
    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        this.stateTicks++;

        if (this.tickCount % 10 == 0) {
            this.focus = this.level().getNearestPlayer(
                    this.getX(), this.getY(), this.getZ(), 64.0D, EntitySelector.NO_SPECTATORS);
            if (this.focus == null) {
                this.noPlayerTicks += 10;
                if (this.noPlayerTicks >= 200 && this.state != StalkerState.DISAPPEAR) {
                    this.setState(StalkerState.DISAPPEAR);
                }
            } else {
                this.noPlayerTicks = 0;
            }
        }
    }

    /** True if the player is looking roughly at the stalker with a clear line of sight. */
    public boolean isPlayerLookingAtMe(Player player) {
        Vec3 view = player.getViewVector(1.0F).normalize();
        Vec3 toMe = new Vec3(this.getX() - player.getX(),
                this.getEyeY() - player.getEyeY(),
                this.getZ() - player.getZ()).normalize();
        return view.dot(toMe) > 0.96D && player.hasLineOfSight(this);
    }

    /** Plays the vanish sound + particles once, so a restarted goal cannot repeat them. */
    public void playVanishEffects() {
        if (vanishEffectsPlayed) {
            return;
        }
        vanishEffectsPlayed = true;
        // PLACEHOLDER sound (vanilla). Replace with a custom SoundEvent in the horror stage.
        this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
                SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 1.0F, 0.5F);
        if (this.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.LARGE_SMOKE,
                    this.getX(), this.getY() + 1.4D, this.getZ(), 40, 0.3D, 1.2D, 0.3D, 0.02D);
        }
    }

    // ---------- spawning ----------

    public static boolean checkStalkerSpawnRules(EntityType<StalkerEntity> type, ServerLevelAccessor level,
                                                 MobSpawnType reason, BlockPos pos, RandomSource random) {
        long time = level.getLevel().getDayTime() % 24000L;
        if (time < 13000L || time > 23000L) {
            return false; // night only
        }
        if (!Monster.isDarkEnoughToSpawn(level, pos, random)) {
            return false;
        }
        if (random.nextInt(4) != 0) {
            return false; // keep it rare
        }
        // at most one Stalker within 96 blocks
        if (!level.getLevel().getEntitiesOfClass(StalkerEntity.class, new AABB(pos).inflate(96.0D)).isEmpty()) {
            return false;
        }
        return Mob.checkMobSpawnRules(type, level, reason, pos, random);
    }

    // ---------- misc ----------

    @Override
    protected float getStandingEyeHeight(Pose pose, EntityDimensions dimensions) {
        return 2.45F;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString("StalkerState", this.state.name());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("StalkerState")) {
            try {
                this.state = StalkerState.valueOf(tag.getString("StalkerState"));
            } catch (IllegalArgumentException ignored) {
                this.state = StalkerState.OBSERVE;
            }
        }
    }
}
