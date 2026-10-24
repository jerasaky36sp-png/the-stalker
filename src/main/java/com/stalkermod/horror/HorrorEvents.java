package com.stalkermod.horror;

import com.stalkermod.StalkerConfig;
import com.stalkermod.StalkerMod;
import com.stalkermod.entity.StalkerEntity;
import com.stalkermod.entity.ai.StalkerSpots;
import com.stalkermod.registry.ModEntities;
import com.stalkermod.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Server-side random night horror events. Cheap: each player is checked once per second. */
@Mod.EventBusSubscriber(modid = StalkerMod.MOD_ID)
public class HorrorEvents {

    private record PendingSound(UUID playerId, long fireTick, SoundEvent sound, float volume,
                                float pitch, double dist, boolean behind, double angle) {}

    private static final Map<UUID, Long> NEXT_EVENT = new HashMap<>();
    private static final List<PendingSound> PENDING = new ArrayList<>();

    // ---------- scheduler ----------

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.side != LogicalSide.SERVER) {
            return;
        }
        if (!(event.player instanceof ServerPlayer player) || player.tickCount % 20 != 0) {
            return;
        }
        if (!StalkerConfig.HORROR_EVENTS_ENABLED.get()) {
            return;
        }
        ServerLevel level = player.serverLevel();
        if (level.dimension() != Level.OVERWORLD || player.isSpectator() || !player.isAlive()) {
            return;
        }
        long time = level.getDayTime() % 24000L;
        if (time < 13000L || time > 23000L) {
            return;
        }
        BlockPos pos = player.blockPosition();
        boolean forest = level.getBiome(pos).is(BiomeTags.IS_FOREST);
        boolean dark = level.getMaxLocalRawBrightness(pos) <= 7;
        if (!forest && !dark) {
            return;
        }

        long now = level.getGameTime();
        Long next = NEXT_EVENT.get(player.getUUID());
        if (next == null) {
            NEXT_EVENT.put(player.getUUID(), now + randomInterval(level.random));
            return;
        }
        if (now < next) {
            return;
        }
        NEXT_EVENT.put(player.getUUID(), now + randomInterval(level.random));
        runRandomEvent(level, player);
    }

    private static long randomInterval(RandomSource rand) {
        int min = StalkerConfig.MIN_INTERVAL_SECONDS.get() * 20;
        int max = Math.max(min + 1, StalkerConfig.MAX_INTERVAL_SECONDS.get() * 20);
        return min + rand.nextInt(max - min);
    }

    private static void runRandomEvent(ServerLevel level, ServerPlayer player) {
        int roll = level.random.nextInt(100);
        if (roll < 30) {
            footsteps(level, player);
        } else if (roll < 55) {
            whisper(level, player);
        } else if (roll < 80) {
            branchSnap(level, player);
        } else if (!appear(level, player)) {
            branchSnap(level, player);
        }
    }

    /** Used by /stalker event <type>. Ignores night/biome checks. */
    public static boolean trigger(ServerLevel level, ServerPlayer player, String type) {
        switch (type) {
            case "footsteps" -> footsteps(level, player);
            case "whisper" -> whisper(level, player);
            case "branch" -> branchSnap(level, player);
            case "appear" -> {
                return appear(level, player);
            }
            case "jumpscare" -> {
                return jumpscareNow(level, player);
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    // ---------- events ----------

    private static void footsteps(ServerLevel level, ServerPlayer player) {
        for (int i = 0; i < 4; i++) {
            schedule(level, player, ModSounds.FOOTSTEP_BEHIND.get(), 1.0F,
                    0.9F + level.random.nextFloat() * 0.2F, 9.0D - i, true, 0.0D, i * 10);
        }
    }

    private static void whisper(ServerLevel level, ServerPlayer player) {
        double angle = level.random.nextDouble() * Math.PI * 2.0D;
        double dist = 20.0D + level.random.nextDouble() * 10.0D;
        schedule(level, player, ModSounds.WHISPER.get(), 2.0F,
                0.8F + level.random.nextFloat() * 0.4F, dist, false, angle, 0);
    }

    private static void branchSnap(ServerLevel level, ServerPlayer player) {
        double angle = level.random.nextDouble() * Math.PI * 2.0D;
        double dist = 10.0D + level.random.nextDouble() * 10.0D;
        schedule(level, player, ModSounds.BRANCH_SNAP.get(), 1.5F, 1.0F, dist, false, angle, 0);
        if (level.random.nextBoolean()) {
            schedule(level, player, ModSounds.BRANCH_SNAP.get(), 1.5F, 0.9F, dist, false, angle, 12);
        }
    }

    private static boolean appear(ServerLevel level, ServerPlayer player) {
        if (!StalkerConfig.STALKER_APPEARANCES.get()) {
            return false;
        }
        if (findNearbyStalker(level, player) != null) {
            return false; // never more than one near this player
        }
        return spawnStalker(level, player) != null;
    }

    @Nullable
    private static StalkerEntity findNearbyStalker(ServerLevel level, ServerPlayer player) {
        List<StalkerEntity> list = level.getEntitiesOfClass(StalkerEntity.class,
                new AABB(player.blockPosition()).inflate(96.0D));
        return list.isEmpty() ? null : list.get(0);
    }

    @Nullable
    private static StalkerEntity spawnStalker(ServerLevel level, ServerPlayer player) {
        BlockPos spot = StalkerSpots.findTreeSpawnSpot(level, level.random, player, 24.0D, 34.0D);
        if (spot == null) {
            return null;
        }
        StalkerEntity stalker = ModEntities.THE_STALKER.get().create(level);
        if (stalker == null) {
            return null;
        }
        stalker.moveTo(spot.getX() + 0.5D, spot.getY(), spot.getZ() + 0.5D, level.random.nextFloat() * 360.0F, 0.0F);
        stalker.finalizeSpawn(level, level.getCurrentDifficultyAt(spot), MobSpawnType.EVENT, null, null);
        level.addFreshEntity(stalker);
        return stalker;
    }

    /** Test command: uses a nearby Stalker (or spawns one) and jumpscares immediately. */
    private static boolean jumpscareNow(ServerLevel level, ServerPlayer player) {
        StalkerEntity stalker = findNearbyStalker(level, player);
        if (stalker == null) {
            stalker = spawnStalker(level, player);
        }
        return stalker != null && Jumpscare.trigger(stalker, player, true);
    }

    // ---------- delayed sounds ----------

    private static void schedule(ServerLevel level, ServerPlayer player, SoundEvent sound, float volume,
                                 float pitch, double dist, boolean behind, double angle, int delayTicks) {
        long fire = level.getServer().getTickCount() + delayTicks;
        PENDING.add(new PendingSound(player.getUUID(), fire, sound, volume, pitch, dist, behind, angle));
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || PENDING.isEmpty()) {
            return;
        }
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            PENDING.clear();
            return;
        }
        long now = server.getTickCount();
        Iterator<PendingSound> it = PENDING.iterator();
        while (it.hasNext()) {
            PendingSound ps = it.next();
            if (now < ps.fireTick()) {
                continue;
            }
            it.remove();
            ServerPlayer p = server.getPlayerList().getPlayer(ps.playerId());
            if (p == null || !p.isAlive()) {
                continue;
            }
            double angle = ps.behind() ? Math.toRadians(p.getYRot()) + Math.PI : ps.angle();
            double x = p.getX() - Math.sin(angle) * ps.dist();
            double z = p.getZ() + Math.cos(angle) * ps.dist();
            p.serverLevel().playSound(null, x, p.getY(), z, ps.sound(), SoundSource.AMBIENT,
                    ps.volume(), ps.pitch());
        }
    }

    // ---------- cleanup ----------

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID id = event.getEntity().getUUID();
        NEXT_EVENT.remove(id);
        Jumpscare.clear(id);
        PENDING.removeIf(ps -> ps.playerId().equals(id));
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        NEXT_EVENT.clear();
        Jumpscare.clearAll();
        PENDING.clear();
    }
}
