package com.stalkermod.entity.ai;

import com.stalkermod.entity.StalkerEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

import javax.annotation.Nullable;

/** Finds hiding spots near trees around a player. Call rarely (it tests paths). */
public final class StalkerSpots {
    private StalkerSpots() {}

    @Nullable
    public static BlockPos findCoverSpot(StalkerEntity stalker, Player player,
                                         double minDist, double maxDist, boolean behindBias) {
        Level level = stalker.level();
        RandomSource rand = stalker.getRandom();
        BlockPos best = null;
        int bestScore = -1;

        for (int i = 0; i < 8; i++) {
            double angle = behindBias
                    ? Math.toRadians(player.getYRot()) + Math.PI + (rand.nextDouble() - 0.5D) * 2.6D
                    : rand.nextDouble() * Math.PI * 2.0D;
            double dist = minDist + rand.nextDouble() * (maxDist - minDist);
            int x = Mth.floor(player.getX() - Math.sin(angle) * dist);
            int z = Mth.floor(player.getZ() + Math.cos(angle) * dist);

            if (!level.hasChunkAt(new BlockPos(x, 64, z))) {
                continue;
            }
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            if (Math.abs(y - player.getBlockY()) > 8) {
                continue;
            }
            BlockPos pos = new BlockPos(x, y, z);
            int score = coverScore(level, pos);
            if (score <= bestScore) {
                continue;
            }
            if (stalker.getNavigation().createPath(pos, 0) == null) {
                continue;
            }
            best = pos;
            bestScore = score;
        }
        return best;
    }

    private static int coverScore(Level level, BlockPos pos) {
        int score = 0;
        for (BlockPos p : BlockPos.betweenClosed(pos.offset(-2, 0, -2), pos.offset(2, 3, 2))) {
            BlockState st = level.getBlockState(p);
            if (st.is(BlockTags.LOGS) || st.is(BlockTags.LEAVES)) {
                score++;
            }
        }
        return score;
    }
}
