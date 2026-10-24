package com.stalkermod.horror;

import com.stalkermod.StalkerConfig;
import com.stalkermod.entity.StalkerEntity;
import com.stalkermod.entity.StalkerState;
import com.stalkermod.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The jumpscare: the Stalker appears a few blocks in front of the player, the player's view is snapped
 * onto its face, a loud scream plays, the screen darkens, and the chase begins.
 * Server side only (view snap uses the vanilla teleport packet).
 */
public final class Jumpscare {
    private static final Map<UUID, Long> LAST = new HashMap<>();

    private Jumpscare() {}

    public static boolean canTrigger(ServerPlayer player) {
        if (!StalkerConfig.JUMPSCARE_ENABLED.get()) {
            return false;
        }
        Long last = LAST.get(player.getUUID());
        long cooldown = StalkerConfig.JUMPSCARE_COOLDOWN_SECONDS.get() * 20L;
        return last == null || player.serverLevel().getGameTime() - last >= cooldown;
    }

    /** @param force ignore config + cooldown (used by the test command) */
    public static boolean trigger(StalkerEntity stalker, ServerPlayer player, boolean force) {
        if (!force && !canTrigger(player)) {
            return false;
        }
        ServerLevel level = player.serverLevel();
        if (stalker.level() != level || !stalker.isAlive() || !player.isAlive()) {
            return false;
        }

        // 1. find free standing room right in front of the player
        double yawRad = Math.toRadians(player.getYRot());
        Vec3 spot = null;
        for (double dist : new double[] {2.6D, 3.5D, 4.5D}) {
            double x = player.getX() - Math.sin(yawRad) * dist;
            double z = player.getZ() + Math.cos(yawRad) * dist;
            double y = player.getY();
            AABB box = stalker.getDimensions(Pose.STANDING).makeBoundingBox(x, y, z);
            BlockPos below = BlockPos.containing(x, y - 0.5D, z);
            if (level.noCollision(stalker, box)
                    && level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)) {
                spot = new Vec3(x, y, z);
                break;
            }
        }
        if (spot == null) {
            return false;
        }

        // 2. put the Stalker there, facing the player
        float yawToPlayer = player.getYRot() + 180.0F;
        stalker.getNavigation().stop();
        stalker.moveTo(spot.x, spot.y, spot.z, yawToPlayer, 0.0F);
        stalker.setYHeadRot(yawToPlayer);
        stalker.setYBodyRot(yawToPlayer);

        // 3. snap the player's camera onto its head
        double dx = stalker.getX() - player.getX();
        double dy = stalker.getEyeY() - player.getEyeY();
        double dz = stalker.getZ() - player.getZ();
        float yaw = Mth.wrapDegrees((float) (Mth.atan2(dz, dx) * (180.0D / Math.PI)) - 90.0F);
        float pitch = Mth.wrapDegrees((float) (-(Mth.atan2(dy, Math.sqrt(dx * dx + dz * dz)) * (180.0D / Math.PI))));
        player.connection.teleport(player.getX(), player.getY(), player.getZ(), yaw, pitch);

        // 4. scream + darkness + brief slowdown
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                ModSounds.JUMPSCARE.get(), SoundSource.HOSTILE, 1.0F, 1.0F);
        player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 100, 0, false, false));
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30, 1, false, false));

        // 5. chase (without the normal chase cue, the jumpscare has its own sound)
        if (stalker.getState() != StalkerState.CHASE) {
            stalker.setSkipNextChaseCue();
            stalker.setState(StalkerState.CHASE);
        }
        LAST.put(player.getUUID(), level.getGameTime());
        return true;
    }

    public static void clear(UUID id) {
        LAST.remove(id);
    }

    public static void clearAll() {
        LAST.clear();
    }
}
