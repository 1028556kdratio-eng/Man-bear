package com.example.stalkermod.world;

import com.example.stalkermod.data.StalkerData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Scatters "chopped down" trees through the world: whole trees (trunk + canopy) are reduced to
 * a stump, sometimes with a felled log lying next to it.
 *
 * It runs lazily on chunks near players, once per chunk (remembered in {@link StalkerData}), and
 * works from the outer ring inward so changes happen at the edge of render distance, out of sight.
 */
public final class TreeChopper {
    /** Chance that a chunk containing trees gets any chopped. */
    private static final float CHUNK_CHANCE = 0.65F;
    private static final int PLAYER_RADIUS = 6;

    private TreeChopper() {}

    public static void process(ServerPlayer p, ServerLevel level, StalkerData data) {
        ChunkPos c = p.chunkPosition();
        int budget = 2;
        for (int ring = PLAYER_RADIUS; ring >= 0 && budget > 0; ring--) {
            for (int dx = -ring; dx <= ring && budget > 0; dx++) {
                for (int dz = -ring; dz <= ring && budget > 0; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) continue;
                    int cx = c.x + dx, cz = c.z + dz;
                    long key = ChunkPos.asLong(cx, cz);
                    if (data.processedChunks().contains(key)) continue;
                    LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                    if (chunk == null) continue;   // not fully generated yet: try again later
                    data.processedChunks().add(key);
                    budget--;
                    chopChunk(level, chunk, RandomSource.create(key ^ level.getSeed()));
                }
            }
        }
    }

    private static void chopChunk(ServerLevel level, LevelChunk chunk, RandomSource rnd) {
        if (rnd.nextFloat() > CHUNK_CHANCE) return;
        int minX = chunk.getPos().getMinBlockX();
        int minZ = chunk.getPos().getMinBlockZ();

        List<BlockPos> trunks = new ArrayList<>();
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int wx = minX + x, wz = minZ + z;
                int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, wx, wz) - 1;
                BlockPos top = new BlockPos(wx, y, wz);
                if (!level.getBlockState(top).is(BlockTags.LOGS)) continue;
                BlockPos base = top;
                while (level.getBlockState(base.below()).is(BlockTags.LOGS)) base = base.below();
                if (!level.getBlockState(base.below()).is(BlockTags.DIRT)) continue;
                trunks.add(base);
            }
        }
        if (trunks.isEmpty()) return;

        int n = Math.min(trunks.size(), 1 + rnd.nextInt(3));
        for (int i = 0; i < n && !trunks.isEmpty(); i++) {
            BlockPos base = trunks.remove(rnd.nextInt(trunks.size()));
            if (!level.getBlockState(base).is(BlockTags.LOGS)) continue;   // already felled with a neighbour
            chopTree(level, base, rnd);
        }
    }

    private static void chopTree(ServerLevel level, BlockPos base, RandomSource rnd) {
        BlockState logState = level.getBlockState(base);
        int stump = 1 + rnd.nextInt(2);

        // flood-fill the tree (logs + natural leaves), bounded in size
        Set<BlockPos> seen = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        seen.add(base);
        queue.add(base);
        while (!queue.isEmpty() && seen.size() < 900) {
            BlockPos cur = queue.poll();
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0) continue;
                        BlockPos n = cur.offset(dx, dy, dz);
                        if (seen.contains(n)) continue;
                        if (Math.abs(n.getX() - base.getX()) > 9 || Math.abs(n.getZ() - base.getZ()) > 9) continue;
                        if (n.getY() < base.getY() || n.getY() > base.getY() + 40) continue;
                        if (!level.hasChunkAt(n)) continue;
                        BlockState st = level.getBlockState(n);
                        boolean log = st.is(BlockTags.LOGS);
                        boolean leaf = st.getBlock() instanceof LeavesBlock && !st.getValue(LeavesBlock.PERSISTENT);
                        if (!log && !leaf) continue;
                        seen.add(n);
                        queue.add(n);
                    }
                }
            }
        }

        for (BlockPos pos : seen) {
            BlockState st = level.getBlockState(pos);
            boolean keep = st.is(BlockTags.LOGS) && pos.getY() < base.getY() + stump;
            if (!keep) level.setBlock(pos, Blocks.AIR.defaultBlockState(), 18);
        }

        // sometimes leave the felled trunk lying on the ground
        if (rnd.nextFloat() < 0.35F && logState.hasProperty(RotatedPillarBlock.AXIS)) {
            Direction.Axis axis = rnd.nextBoolean() ? Direction.Axis.X : Direction.Axis.Z;
            Direction dir = Direction.fromAxisAndDirection(axis,
                    rnd.nextBoolean() ? Direction.AxisDirection.POSITIVE : Direction.AxisDirection.NEGATIVE);
            int len = 2 + rnd.nextInt(2);
            BlockPos start = base.relative(dir, 2);
            for (int i = 0; i < len; i++) {
                BlockPos bp = start.relative(dir, i);
                if (!level.hasChunkAt(bp)) break;
                int gy = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bp.getX(), bp.getZ());
                BlockPos at = new BlockPos(bp.getX(), gy, bp.getZ());
                if (level.getBlockState(at).isAir() && level.getBlockState(at.below()).is(BlockTags.DIRT)) {
                    level.setBlock(at, logState.setValue(RotatedPillarBlock.AXIS, axis), 18);
                } else {
                    break;
                }
            }
        }
    }
}
