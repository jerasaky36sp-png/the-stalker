package com.stalkermod.entity;

import com.stalkermod.StalkerMod;
import com.stalkermod.entity.ai.ChaseGoal;
import com.stalkermod.entity.ai.DisappearGoal;
import com.stalkermod.entity.ai.ObserveGoal;
import com.stalkermod.entity.ai.StalkGoal;
import com.stalkermod.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
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

import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.Animation;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

import javax.annotation.Nullable;

public class StalkerEntity extends Monster implements GeoEntity {

    public static final double CHASE_GIVE_UP_DISTANCE = 40.0D;

    /** Synced to clients so the renderer can animate by state. */
    private static final EntityDataAccessor<Integer> DATA_STATE =
            SynchedEntityData.defineId(StalkerEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_VANISH =
            SynchedEntityData.defineId(StalkerEntity.class, EntityDataSerializers.INT);

    private StalkerState state = StalkerState.OBSERVE;
    private int stateTicks;
    private int noPlayerTicks;
    private boolean vanishEffectsPlayed;
    private int heartbeatCooldown;
    private boolean skipNextChaseCue;
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
                .add(Attributes.ATTACK_DAMAGE, 8.0D)
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

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_STATE, 0);
        this.entityData.define(DATA_VANISH, 0);
    }

    /** Safe on both sides; clients read the synced value. */
    public StalkerState getSyncedState() {
        StalkerState[] all = StalkerState.values();
        return all[Mth.clamp(this.entityData.get(DATA_STATE), 0, all.length - 1)];
    }

    /** 0 normally, 1..20 while vanishing (drives the collapse animation). */
    public int getVanishTicks() {
        return this.entityData.get(DATA_VANISH);
    }

    // ---------- GeckoLib animation ----------

    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.the_stalker.idle");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop("animation.the_stalker.walk");
    private static final RawAnimation RUN = RawAnimation.begin().thenLoop("animation.the_stalker.run");
    private static final RawAnimation VANISH = RawAnimation.begin().thenPlayAndHold("animation.the_stalker.vanish");
    private static final RawAnimation ATTACK =
            RawAnimation.begin().then("animation.the_stalker.attack", Animation.LoopType.PLAY_ONCE);

    private final AnimatableInstanceCache animCache = GeckoLibUtil.createInstanceCache(this);

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return animCache;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<StalkerEntity>(this, "main", 5, this::mainPredicate));
        // registered last so the attack swing overrides the arms of the main animation
        controllers.add(new AnimationController<StalkerEntity>(this, "attack", 0, state -> PlayState.STOP)
                .triggerableAnim("attack", ATTACK));
    }

    private PlayState mainPredicate(AnimationState<StalkerEntity> state) {
        StalkerState synced = this.getSyncedState();
        if (synced == StalkerState.DISAPPEAR) {
            return state.setAndContinue(VANISH);
        }
        if (state.isMoving()) {
            return state.setAndContinue(synced == StalkerState.CHASE ? RUN : WALK);
        }
        return state.setAndContinue(IDLE);
    }

    /** Server side: tells all tracking clients to play the attack swing once. */
    public void playAttackAnimation() {
        if (!this.level().isClientSide) {
            this.triggerAnim("attack", "attack");
        }
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
            this.entityData.set(DATA_STATE, newState.ordinal());
            if (newState == StalkerState.CHASE && !this.level().isClientSide) {
                this.onChaseStart();
            }
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
        if (this.state == StalkerState.DISAPPEAR) {
            this.entityData.set(DATA_VANISH, Math.min(this.stateTicks, 20));
        }
        this.tickHeartbeat();

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

    /** Chase-start cue: a sound at the player and a short darkness pulse. */
    private void onChaseStart() {
        if (skipNextChaseCue) {
            skipNextChaseCue = false;
            return;
        }
        if (!hasValidFocus()) {
            return;
        }
        this.level().playSound(null, focus.getX(), focus.getY(), focus.getZ(),
                ModSounds.CHASE_START.get(), SoundSource.HOSTILE, 1.0F, 1.0F);
        focus.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 60, 0, false, false));
    }

    /** Plays the vanish sound + particles once, so a restarted goal cannot repeat them. */
    public void playVanishEffects() {
        if (vanishEffectsPlayed) {
            return;
        }
        vanishEffectsPlayed = true;
        this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
                ModSounds.VANISH.get(), SoundSource.HOSTILE, 1.0F, 0.5F);
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

    /** Used by the jumpscare, which plays its own sound instead of the chase cue. */
    public void setSkipNextChaseCue() {
        this.skipNextChaseCue = true;
    }

    /** Heartbeat for the targeted player: faster and louder the closer the Stalker is. */
    private void tickHeartbeat() {
        if (!hasValidFocus() || this.state == StalkerState.DISAPPEAR) {
            return;
        }
        if (--heartbeatCooldown > 0) {
            return;
        }
        double dist = this.distanceTo(focus);
        if (dist > 28.0D) {
            heartbeatCooldown = 20;
            return;
        }
        float closeness = (float) (1.0D - dist / 28.0D);
        this.level().playSound(null, focus.getX(), focus.getY(), focus.getZ(),
                ModSounds.HEARTBEAT.get(), SoundSource.HOSTILE, 0.5F + closeness, 0.9F + closeness * 0.4F);
        heartbeatCooldown = (int) Mth.clamp(dist * 3.0D, 22.0D, 80.0D);
    }

    @Nullable
    @Override
    protected SoundEvent getAmbientSound() {
        return (this.state == StalkerState.OBSERVE || this.state == StalkerState.STALK)
                ? ModSounds.BREATH.get() : null;
    }

    @Nullable
    @Override
    protected SoundEvent getDeathSound() {
        return ModSounds.VANISH.get();
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
        this.entityData.set(DATA_STATE, this.state.ordinal());
    }
}
