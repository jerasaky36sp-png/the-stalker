package com.stalkermod.horror;

import com.stalkermod.StalkerConfig;
import com.stalkermod.StalkerMod;
import com.stalkermod.entity.StalkerEntity;
import com.stalkermod.entity.StalkerState;
import com.stalkermod.entity.ai.StalkerSpots;
import com.stalkermod.registry.ModEntities;
import com.stalkermod.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.Tags;
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
import java.util.function.Consumer;

/**
 * Server-side random night horror events. Cheap: each player is checked once per second, and the
 * block scans for doors/windows only run when an event actually fires.
 *
 * Outdoors/dark: footsteps behind you, whispers, branch snaps, the Stalker appearing between trees.
 * Indoors near a door: a multi-stage haunting - footsteps outside, slow knocks, whispers at the door,
 * scratching, a creaking door, the Stalker peering through a window, then furious pounding.
 */
@Mod.EventBusSubscriber(modid = StalkerMod.MOD_ID)
public class HorrorEvents {

    private record Task(UUID playerId, long fireTick, Consumer<ServerPlayer> action) {}

    private record WindowSpot(Vec3 pos, BlockPos feet, float yaw) {}

    private static final Map<UUID, Long> NEXT_EVENT = new HashMap<>();
    private static final Map<UUID, Long> HAUNT_UNTIL = new HashMap<>();
    private static final List<Task> TASKS = new ArrayList<>();

    // ====================== scheduler ======================

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
        boolean covered = !level.canSeeSky(pos.above(2));
        if (!forest && !dark && !covered) {
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
        runRandomEvent(level, player, covered);
    }

    private static long randomInterval(RandomSource rand) {
        int min = StalkerConfig.MIN_INTERVAL_SECONDS.get() * 20;
        int max = Math.max(min + 1, StalkerConfig.MAX_INTERVAL_SECONDS.get() * 20);
        return min + rand.nextInt(max - min);
    }

    private static void runRandomEvent(ServerLevel level, ServerPlayer player, boolean covered) {
        // indoors next to a door -> house events
        if (covered && StalkerConfig.HOUSE_EVENTS_ENABLED.get()) {
            BlockPos door = findDoor(level, player);
            if (door != null) {
                int r = level.random.nextInt(100);
                if (r < 40 && startHaunt(level, player, door, false)) {
                    return;
                }
                if (r < 62) {
                    knock(level, player, door, 3 + level.random.nextInt(3), 0, 20);
                } else if (r < 82 && peek(level, player, false)) {
                    // done
                } else {
                    whisperAtDoor(level, player, door);
                }
                return;
            }
        }
        // outdoors / dark
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
            case "haunt" -> {
                BlockPos door = findDoor(level, player);
                return door != null && startHaunt(level, player, door, true);
            }
            case "knock" -> {
                BlockPos door = findDoor(level, player);
                if (door == null) {
                    return false;
                }
                knock(level, player, door, 4, 0, 20);
                pound(level, player, door, 6, 100, 4, 2.6F);
                return true;
            }
            case "peek" -> {
                return peek(level, player, true);
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    // ====================== outdoor events ======================

    private static void footsteps(ServerLevel level, ServerPlayer player) {
        for (int i = 0; i < 4; i++) {
            float pitch = 0.9F + level.random.nextFloat() * 0.2F;
            double dist = 9.0D - i;
            later(level, player, i * 10, pl ->
                    playRelative(pl, ModSounds.FOOTSTEP_BEHIND.get(), 1.0F, pitch, dist, true, 0.0D));
        }
    }

    private static void whisper(ServerLevel level, ServerPlayer player) {
        double angle = level.random.nextDouble() * Math.PI * 2.0D;
        double dist = 20.0D + level.random.nextDouble() * 10.0D;
        playRelative(player, ModSounds.WHISPER.get(), 2.0F, 0.8F + level.random.nextFloat() * 0.4F, dist, false, angle);
    }

    private static void branchSnap(ServerLevel level, ServerPlayer player) {
        double angle = level.random.nextDouble() * Math.PI * 2.0D;
        double dist = 10.0D + level.random.nextDouble() * 10.0D;
        playRelative(player, ModSounds.BRANCH_SNAP.get(), 1.5F, 1.0F, dist, false, angle);
        if (level.random.nextBoolean()) {
            later(level, player, 12, pl ->
                    playRelative(pl, ModSounds.BRANCH_SNAP.get(), 1.5F, 0.9F, dist, false, angle));
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

    /** Test command: if a Stalker is near it chases afterwards, otherwise just the picture + scream. */
    private static boolean jumpscareNow(ServerLevel level, ServerPlayer player) {
        StalkerEntity stalker = findNearbyStalker(level, player);
        if (stalker != null) {
            return Jumpscare.trigger(stalker, player, true);
        }
        Jumpscare.show(player);
        return true;
    }

    // ====================== house haunting ======================

    /** The multi-stage haunting (about 35 seconds). */
    private static boolean startHaunt(ServerLevel level, ServerPlayer player, BlockPos door, boolean force) {
        long now = level.getGameTime();
        Long until = HAUNT_UNTIL.get(player.getUUID());
        if (!force && until != null && now < until) {
            return false;
        }
        HAUNT_UNTIL.put(player.getUUID(), now + 800L);
        NEXT_EVENT.put(player.getUUID(), now + 800L + randomInterval(level.random));

        Direction out = outsideDirection(level, door, player);

        // 1. footsteps approaching the door from outside
        for (int i = 0; i < 4; i++) {
            double dist = 7.0D - i * 1.3D;
            Vec3 at = Vec3.atCenterOf(door).add(out.getStepX() * dist, -0.5D, out.getStepZ() * dist);
            later(level, player, i * 12, pl ->
                    playAt(pl.serverLevel(), at, ModSounds.FOOTSTEP_BEHIND.get(), 1.4F, 0.85F));
        }
        // 2. three slow knocks
        knock(level, player, door, 3, 90, 22);
        // 3. a whisper right behind the door
        later(level, player, 260, pl -> whisperAtDoor(pl.serverLevel(), pl, door));
        // 4. scratching
        later(level, player, 330, pl ->
                playAt(pl.serverLevel(), doorPos(door, out), ModSounds.SCRATCH.get(), 1.5F, 1.0F));
        // 5. the door creaks
        later(level, player, 430, pl ->
                playAt(pl.serverLevel(), doorPos(door, out), ModSounds.DOOR_CREAK.get(), 1.6F, 1.0F));
        // 6. it shows its face at a window (or pounds if there is no window)
        later(level, player, 500, pl -> {
            if (!peek(pl.serverLevel(), pl, false)) {
                pound(pl.serverLevel(), pl, door, 4, 0, 5, 2.2F);
            }
        });
        // 7. finale: furious pounding, then maybe the jumpscare
        pound(level, player, door, 8, 640, 4, 2.8F);
        later(level, player, 690, pl -> {
            if (level.random.nextFloat() < 0.5F) {
                Jumpscare.showIfAllowed(pl);
            }
        });
        return true;
    }

    private static void knock(ServerLevel level, ServerPlayer player, BlockPos door,
                              int count, int startDelay, int interval) {
        Direction out = outsideDirection(level, door, player);
        Vec3 at = doorPos(door, out);
        for (int i = 0; i < count; i++) {
            float pitch = 0.9F + level.random.nextFloat() * 0.2F;
            later(level, player, startDelay + i * interval, pl ->
                    playAt(pl.serverLevel(), at, ModSounds.KNOCK.get(), 1.8F, pitch));
        }
    }

    private static void pound(ServerLevel level, ServerPlayer player, BlockPos door,
                              int count, int startDelay, int interval, float volume) {
        Direction out = outsideDirection(level, door, player);
        Vec3 at = doorPos(door, out);
        for (int i = 0; i < count; i++) {
            float pitch = 0.85F + level.random.nextFloat() * 0.25F;
            later(level, player, startDelay + i * interval, pl ->
                    playAt(pl.serverLevel(), at, ModSounds.POUND.get(), volume, pitch));
        }
    }

    private static void whisperAtDoor(ServerLevel level, ServerPlayer player, BlockPos door) {
        Direction out = outsideDirection(level, door, player);
        playAt(level, doorPos(door, out), ModSounds.WHISPER.get(), 1.8F, 0.85F);
    }

    /** The Stalker stands outside a window and stares in. Returns false if no suitable window. */
    private static boolean peek(ServerLevel level, ServerPlayer player, boolean force) {
        StalkerEntity existing = findNearbyStalker(level, player);
        if (existing != null) {
            if (!force) {
                return false;
            }
            existing.discard(); // test command: replace it
        }
        WindowSpot ws = findWindow(level, player);
        if (ws == null) {
            return false;
        }
        StalkerEntity stalker = ModEntities.THE_STALKER.get().create(level);
        if (stalker == null) {
            return false;
        }
        stalker.moveTo(ws.pos().x, ws.feet().getY(), ws.pos().z, ws.yaw(), 0.0F);
        stalker.setYHeadRot(ws.yaw());
        stalker.setYBodyRot(ws.yaw());
        stalker.finalizeSpawn(level, level.getCurrentDifficultyAt(ws.feet()), MobSpawnType.EVENT, null, null);
        stalker.setState(StalkerState.PEEK);
        level.addFreshEntity(stalker);
        return true;
    }

    // ---------- block scans (only run when an event fires) ----------

    @Nullable
    private static BlockPos findDoor(ServerLevel level, ServerPlayer player) {
        BlockPos c = player.blockPosition();
        BlockPos best = null;
        double bestD = Double.MAX_VALUE;
        for (BlockPos p : BlockPos.betweenClosed(c.offset(-12, -3, -12), c.offset(12, 4, 12))) {
            BlockState st = level.getBlockState(p);
            if (!st.is(BlockTags.DOORS) || !st.hasProperty(DoorBlock.HALF)
                    || st.getValue(DoorBlock.HALF) != DoubleBlockHalf.LOWER) {
                continue;
            }
            double d = p.distSqr(c);
            if (d < bestD) {
                bestD = d;
                best = p.immutable();
            }
        }
        return best;
    }

    /** Which side of the door is "outside": the side farther from the player. */
    private static Direction outsideDirection(ServerLevel level, BlockPos door, ServerPlayer player) {
        BlockState st = level.getBlockState(door);
        Direction facing = st.hasProperty(DoorBlock.FACING) ? st.getValue(DoorBlock.FACING) : Direction.NORTH;
        double a = player.distanceToSqr(Vec3.atCenterOf(door.relative(facing)));
        double b = player.distanceToSqr(Vec3.atCenterOf(door.relative(facing.getOpposite())));
        return a > b ? facing : facing.getOpposite();
    }

    private static Vec3 doorPos(BlockPos door, Direction out) {
        return Vec3.atCenterOf(door).add(out.getStepX() * 0.6D, 0.7D, out.getStepZ() * 0.6D);
    }

    /**
     * Finds a glass block near the player with free ground outside it, so that a 2.7 block tall Stalker
     * standing there has its eyes at window height (glass block = 2 above the outside ground).
     */
    @Nullable
    private static WindowSpot findWindow(ServerLevel level, ServerPlayer player) {
        BlockPos c = player.blockPosition();
        WindowSpot best = null;
        double bestD = Double.MAX_VALUE;
        for (BlockPos p : BlockPos.betweenClosed(c.offset(-9, -3, -9), c.offset(9, 4, 9))) {
            BlockState st = level.getBlockState(p);
            if (!st.is(Tags.Blocks.GLASS) && !st.is(Tags.Blocks.GLASS_PANES)) {
                continue;
            }
            double dist = player.distanceToSqr(p.getX() + 0.5D, p.getY() + 0.5D, p.getZ() + 0.5D);
            if (dist < 4.0D || dist >= bestD) {
                continue;
            }
            for (Direction d : Direction.Plane.HORIZONTAL) {
                BlockPos outside = p.relative(d);
                BlockPos inside = p.relative(d.getOpposite());
                if (!level.getBlockState(outside).isAir() || !level.getBlockState(inside).isAir()) {
                    continue;
                }
                BlockPos feet = outside.below(2);
                if (!level.getBlockState(feet).isAir() || !level.getBlockState(feet.above()).isAir()) {
                    continue;
                }
                BlockPos floor = feet.below();
                if (!level.getBlockState(floor).isFaceSturdy(level, floor, Direction.UP)) {
                    continue;
                }
                Vec3 pos = new Vec3(outside.getX() + 0.5D - d.getStepX() * 0.15D, feet.getY(),
                        outside.getZ() + 0.5D - d.getStepZ() * 0.15D);
                best = new WindowSpot(pos, feet.immutable(), d.getOpposite().toYRot());
                bestD = dist;
                break;
            }
        }
        return best;
    }

    // ====================== sound + task helpers ======================

    private static void playAt(ServerLevel level, Vec3 pos, SoundEvent sound, float volume, float pitch) {
        level.playSound(null, pos.x, pos.y, pos.z, sound, SoundSource.AMBIENT, volume, pitch);
    }

    private static void playRelative(ServerPlayer p, SoundEvent sound, float volume, float pitch,
                                     double dist, boolean behind, double angle) {
        double a = behind ? Math.toRadians(p.getYRot()) + Math.PI : angle;
        double x = p.getX() - Math.sin(a) * dist;
        double z = p.getZ() + Math.cos(a) * dist;
        p.serverLevel().playSound(null, x, p.getY(), z, sound, SoundSource.AMBIENT, volume, pitch);
    }

    private static void later(ServerLevel level, ServerPlayer player, int delayTicks, Consumer<ServerPlayer> action) {
        TASKS.add(new Task(player.getUUID(), level.getServer().getTickCount() + delayTicks, action));
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || TASKS.isEmpty()) {
            return;
        }
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            TASKS.clear();
            return;
        }
        long now = server.getTickCount();
        List<Task> due = new ArrayList<>();
        Iterator<Task> it = TASKS.iterator();
        while (it.hasNext()) {
            Task t = it.next();
            if (now >= t.fireTick()) {
                due.add(t);
                it.remove();
            }
        }
        // run after removal: actions may schedule new tasks
        for (Task t : due) {
            ServerPlayer p = server.getPlayerList().getPlayer(t.playerId());
            if (p == null || !p.isAlive()) {
                continue;
            }
            try {
                t.action().accept(p);
            } catch (Exception e) {
                StalkerMod.LOGGER.warn("Horror task failed", e);
            }
        }
    }

    // ====================== cleanup ======================

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID id = event.getEntity().getUUID();
        NEXT_EVENT.remove(id);
        HAUNT_UNTIL.remove(id);
        Jumpscare.clear(id);
        TASKS.removeIf(t -> t.playerId().equals(id));
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        NEXT_EVENT.clear();
        HAUNT_UNTIL.clear();
        TASKS.clear();
        Jumpscare.clearAll();
    }
}
