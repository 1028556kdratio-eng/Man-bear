package com.example.stalkermod;

import com.example.stalkermod.data.PlayerProfile;
import com.example.stalkermod.data.StalkerData;
import com.example.stalkermod.entity.StalkerEntity;
import com.example.stalkermod.world.StalkerSpawner;
import com.example.stalkermod.world.TreeChopper;
import com.mojang.brigadier.Command;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.server.ServerLifecycleHooks;

public class StalkerEvents {

    /** The Stalker shows up after the first sunset (day-time ticks). */
    private static final long FIRST_APPEARANCE = 13000L;

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null || server.getTickCount() % 20 != 0) return;

        StalkerData data = StalkerData.get(server);
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (!(p.level() instanceof ServerLevel level)) continue;
            if (level.dimension() != Level.OVERWORLD || p.isSpectator()) continue;

            TreeChopper.process(p, level, data);

            if (p.isCreative() || level.getDifficulty() == Difficulty.PEACEFUL) continue;
            PlayerProfile prof = data.profile(p.getUUID());
            prof.tickSecond();
            if (!level.canSeeSky(p.blockPosition())) prof.onShelterSecond();
            manageStalker(p, level, prof);
        }
        data.setDirty();
    }

    private void manageStalker(ServerPlayer p, ServerLevel level, PlayerProfile prof) {
        if (level.getDayTime() < FIRST_APPEARANCE && prof.firstDay < 0) return;

        Entity existing = prof.stalkerUuid == null ? null : level.getEntity(prof.stalkerUuid);
        if (existing instanceof StalkerEntity s && s.isAlive()) {
            if (s.distanceTo(p) > 110) StalkerSpawner.relocate(s, p);
            return;
        }
        double day = level.getDayTime() / 24000.0;
        if (day < prof.respawnDay) return;
        if (level.random.nextInt(6) != 0) return;
        StalkerSpawner.spawnFor(p, level, prof);
    }

    @SubscribeEvent
    public void onDeath(LivingDeathEvent e) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;

        if (e.getEntity() instanceof ServerPlayer p && e.getSource().getEntity() instanceof StalkerEntity) {
            StalkerData.get(server).profile(p.getUUID()).onPlayerKilled();
        }
        if (e.getEntity() instanceof StalkerEntity s && s.getTargetPlayerUuid() != null) {
            double day = s.level().getDayTime() / 24000.0;
            StalkerData.get(server).profile(s.getTargetPlayerUuid()).onStalkerKilled(day);
        }
    }

    // ---- debug / admin commands ----
    @SubscribeEvent
    public void onCommands(RegisterCommandsEvent e) {
        e.getDispatcher().register(Commands.literal("stalker")
                .requires(src -> src.hasPermission(2))
                .then(Commands.literal("summon").executes(ctx -> {
                    ServerPlayer p = ctx.getSource().getPlayerOrException();
                    PlayerProfile prof = StalkerData.get(ctx.getSource().getServer()).profile(p.getUUID());
                    StalkerEntity s = StalkerSpawner.spawnFor(p, p.serverLevel(), prof);
                    ctx.getSource().sendSuccess(() -> Component.literal(
                            s != null ? "Something is watching you now..." : "Couldn't find a place to spawn it."), true);
                    return Command.SINGLE_SUCCESS;
                }))
                .then(Commands.literal("info").executes(ctx -> {
                    ServerPlayer p = ctx.getSource().getPlayerOrException();
                    PlayerProfile pr = StalkerData.get(ctx.getSource().getServer()).profile(p.getUUID());
                    double day = p.level().getDayTime() / 24000.0;
                    String msg = String.format(
                            "Stalker profile: boldness %.2f (effective %.2f) | cunning %.2f | fake-rush rate %.2f | "
                                    + "wariness %.2f | patience %.2f | fake rushes %d (flinched %d) | escapes %d | "
                                    + "hits dealt %d / taken %d | stared %ds | sheltered %ds",
                            pr.boldness, pr.effBoldness(day), pr.cunning, pr.fakeRushRate, pr.wariness, pr.patience,
                            pr.fakeRushes, pr.flinches, pr.escapes, pr.hitsDealt, pr.hitsTaken,
                            pr.stareSeconds, pr.shelterSeconds);
                    ctx.getSource().sendSuccess(() -> Component.literal(msg), false);
                    return Command.SINGLE_SUCCESS;
                }))
                .then(Commands.literal("reset").executes(ctx -> {
                    ServerPlayer p = ctx.getSource().getPlayerOrException();
                    StalkerData data = StalkerData.get(ctx.getSource().getServer());
                    PlayerProfile pr = data.profile(p.getUUID());
                    Entity old = pr.stalkerUuid == null ? null : p.serverLevel().getEntity(pr.stalkerUuid);
                    if (old != null) old.discard();
                    data.reset(p.getUUID());
                    ctx.getSource().sendSuccess(() -> Component.literal("Stalker memory wiped."), true);
                    return Command.SINGLE_SUCCESS;
                })));
    }
}
