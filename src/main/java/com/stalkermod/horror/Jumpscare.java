package com.stalkermod.horror;

import com.stalkermod.StalkerConfig;
import com.stalkermod.entity.StalkerEntity;
import com.stalkermod.entity.StalkerState;
import com.stalkermod.network.StalkerNetwork;
import com.stalkermod.registry.ModSounds;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The jumpscare: a full-screen picture of the Stalker slams onto the player's screen with a loud scream,
 * then the screen darkens and the Stalker starts chasing. The picture is drawn client-side
 * (see client/ClientJumpscare) after a network packet from the server.
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

    /** Jumpscare caused by a Stalker: show the picture, then the Stalker chases. */
    public static boolean trigger(StalkerEntity stalker, ServerPlayer player, boolean force) {
        if (!force && !canTrigger(player)) {
            return false;
        }
        if (stalker.level() != player.level() || !stalker.isAlive() || !player.isAlive()) {
            return false;
        }
        show(player);
        if (stalker.getState() != StalkerState.CHASE) {
            stalker.setSkipNextChaseCue(); // the scream replaces the normal chase cue
            stalker.setState(StalkerState.CHASE);
        }
        return true;
    }

    /** Shows the jumpscare only if the config + cooldown allow it. */
    public static boolean showIfAllowed(ServerPlayer player) {
        if (!canTrigger(player)) {
            return false;
        }
        show(player);
        return true;
    }

    /** Picture + scream + darkness for one player. Does not need a Stalker. */
    public static void show(ServerPlayer player) {
        StalkerNetwork.sendJumpscare(player);
        player.playNotifySound(ModSounds.JUMPSCARE.get(), SoundSource.MASTER, 1.0F, 1.0F);
        player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 100, 0, false, false));
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30, 1, false, false));
        LAST.put(player.getUUID(), player.serverLevel().getGameTime());
    }

    public static void clear(UUID id) {
        LAST.remove(id);
    }

    public static void clearAll() {
        LAST.clear();
    }
}
