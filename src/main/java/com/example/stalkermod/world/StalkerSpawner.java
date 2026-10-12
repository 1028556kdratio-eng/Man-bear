package com.example.stalkermod.world;

import com.example.stalkermod.ModEntities;
import com.example.stalkermod.data.PlayerProfile;
import com.example.stalkermod.entity.StalkerEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class StalkerSpawner {
    private StalkerSpawner() {}

    /** Spawns a stalker 46-68 blocks away, preferably behind the player. */
    public static StalkerEntity spawnFor(ServerPlayer p, ServerLevel level, PlayerProfile profile) {
        Vec3 spot = findSpot(level, p);
        if (spot == null) return null;
        StalkerEntity e = ModEntities.STALKER.get().create(level);
        if (e == null) return null;
        e.moveTo(spot.x, spot.y, spot.z, level.random.nextFloat() * 360F, 0F);
        e.setTargetPlayer(p.getUUID());
        level.addFreshEntity(e);
        profile.stalkerUuid = e.getUUID();
        if (profile.firstDay < 0) profile.firstDay = level.getDayTime() / 24000.0;
        return e;
    }

    /** If the stalker got left far behind, bring it back to the edge of the player's world. */
    public static boolean relocate(StalkerEntity s, ServerPlayer p) {
        Vec3 spot = findSpot((ServerLevel) p.level(), p);
        if (spot == null) return false;
        s.getNavigation().stop();
        s.teleportTo(spot.x, spot.y, spot.z);
        return true;
    }

    private static Vec3 findSpot(ServerLevel level, ServerPlayer p) {
        RandomSource r = level.random;
        double yaw = Math.toRadians(p.getYRot());
        // direction directly behind the player
        double behind = Math.atan2(-Math.cos(yaw), Math.sin(yaw));
        for (int i = 0; i < 16; i++) {
            double a = behind + (r.nextDouble() - 0.5) * 2.4;
            double d = 46 + r.nextDouble() * 22;
            int x = Mth.floor(p.getX() + Math.cos(a) * d);
            int z = Mth.floor(p.getZ() + Math.sin(a) * d);
            if (!level.hasChunkAt(new BlockPos(x, p.getBlockY(), z))) continue;
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            BlockPos pos = new BlockPos(x, y, z);
            BlockPos below = pos.below();
            if (level.getBlockState(below).getCollisionShape(level, below).isEmpty()) continue;
            if (level.getBlockState(below).is(BlockTags.LOGS)) continue;   // that's the top of a tree trunk
            if (!level.getFluidState(pos).isEmpty() || !level.getFluidState(below).isEmpty()) continue;
            AABB box = new AABB(x + 0.5 - 0.8, y + 0.1, z + 0.5 - 0.8, x + 0.5 + 0.8, y + 2.5, z + 0.5 + 0.8);
            if (!level.noCollision(box)) continue;
            return new Vec3(x + 0.5, y, z + 0.5);
        }
        return null;
    }
}
