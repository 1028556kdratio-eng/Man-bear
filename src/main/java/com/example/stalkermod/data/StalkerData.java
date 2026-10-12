package com.example.stalkermod.data;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** World-wide persistent data: per-player Stalker profiles + which chunks already had trees chopped. */
public class StalkerData extends SavedData {
    private static final String NAME = "stalkermod_data";

    private final Map<UUID, PlayerProfile> profiles = new HashMap<>();
    private final LongOpenHashSet chunks = new LongOpenHashSet();

    public static StalkerData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(StalkerData::load, StalkerData::new, NAME);
    }

    public PlayerProfile profile(UUID id) {
        return profiles.computeIfAbsent(id, k -> new PlayerProfile());
    }

    public LongSet processedChunks() {
        return chunks;
    }

    public void reset(UUID id) {
        profiles.remove(id);
        setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (Map.Entry<UUID, PlayerProfile> e : profiles.entrySet()) {
            CompoundTag t = e.getValue().save();
            t.putUUID("Id", e.getKey());
            list.add(t);
        }
        tag.put("Profiles", list);
        tag.putLongArray("Chunks", chunks.toLongArray());
        return tag;
    }

    public static StalkerData load(CompoundTag tag) {
        StalkerData d = new StalkerData();
        ListTag list = tag.getList("Profiles", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag t = list.getCompound(i);
            d.profiles.put(t.getUUID("Id"), PlayerProfile.load(t));
        }
        for (long l : tag.getLongArray("Chunks")) d.chunks.add(l);
        return d;
    }
}
