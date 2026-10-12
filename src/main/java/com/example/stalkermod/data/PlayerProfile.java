package com.example.stalkermod.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;

import java.util.UUID;

/**
 * What the Stalker has learned about one specific player. Persisted with the world.
 *
 * Learned traits (0..1):
 *  boldness     - willingness to commit to real attacks (also grows with each in-game day)
 *  cunning      - how much it relies on cover: hiding, blending, peeking
 *  fakeRushRate - how often it bluffs instead of attacking (tuned by how you react to bluffs)
 *  wariness     - fear of you, raised by hurting it; makes it keep its distance
 *  patience     - how long it is willing to wait/stare (raised by you turtling in shelters)
 */
public class PlayerProfile {
    public float boldness = 0F;
    public float cunning = 0.1F;
    public float fakeRushRate = 0.55F;
    public float wariness = 0F;
    public float patience = 1F;

    public double firstDay = -1;
    public double respawnDay = 0;
    public UUID stalkerUuid;

    public int fakeRushes, flinches, escapes, hitsDealt, hitsTaken, stareSeconds, shelterSeconds;

    /** Aggression after the passage of days and learning. */
    public double effBoldness(double day) {
        double since = firstDay < 0 ? 0 : Math.max(0, day - firstDay);
        return Mth.clamp(boldness + 0.045 * since - 0.3 * wariness, 0.0, 1.0);
    }

    /** Called each second the player is actively staring it down. Staring teaches it to use cover. */
    public void onStareSecond() {
        stareSeconds++;
        cunning = Math.min(1F, cunning + 0.004F);
    }

    /** Called each second the player has no sky above them (base, cave). Turtling makes it hungrier and more patient. */
    public void onShelterSecond() {
        shelterSeconds++;
        boldness = Math.min(0.6F, boldness + 0.0004F);
        patience = Math.min(2F, patience + 0.0004F);
    }

    public void tickSecond() {
        wariness = Math.max(0F, wariness - 0.0002F);
    }

    /** Bluff worked (you ran) -> bluff more. Bluff failed (you held your ground) -> stop bluffing, get bolder. */
    public void onFakeRush(boolean flinched) {
        fakeRushes++;
        if (flinched) {
            flinches++;
            fakeRushRate = Math.min(0.9F, fakeRushRate + 0.06F);
            cunning = Math.min(1F, cunning + 0.01F);
        } else {
            fakeRushRate = Math.max(0.1F, fakeRushRate - 0.10F);
            boldness = Math.min(1F, boldness + 0.05F);
        }
    }

    public void onHitStalker() {
        hitsTaken++;
        wariness = Math.min(1F, wariness + 0.05F);
        cunning = Math.min(1F, cunning + 0.02F);
    }

    public void onHitPlayer() {
        hitsDealt++;
        boldness = Math.min(1F, boldness + 0.02F);
    }

    /** You outran / out-lasted a real chase: it gets craftier and more patient, a bit less reckless. */
    public void onEscaped() {
        escapes++;
        boldness = Math.max(0F, boldness - 0.02F);
        cunning = Math.min(1F, cunning + 0.04F);
        patience = Math.min(2F, patience + 0.05F);
    }

    public void onPlayerKilled() {
        boldness = Math.min(1F, boldness + 0.1F);
    }

    public void onStalkerKilled(double day) {
        wariness = Math.min(1F, wariness + 0.25F);
        cunning = Math.min(1F, cunning + 0.1F);
        respawnDay = day + 1.0;
        stalkerUuid = null;
    }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putFloat("Boldness", boldness);
        t.putFloat("Cunning", cunning);
        t.putFloat("FakeRush", fakeRushRate);
        t.putFloat("Wariness", wariness);
        t.putFloat("Patience", patience);
        t.putDouble("FirstDay", firstDay);
        t.putDouble("RespawnDay", respawnDay);
        if (stalkerUuid != null) t.putUUID("Stalker", stalkerUuid);
        t.putInt("FakeRushes", fakeRushes);
        t.putInt("Flinches", flinches);
        t.putInt("Escapes", escapes);
        t.putInt("HitsDealt", hitsDealt);
        t.putInt("HitsTaken", hitsTaken);
        t.putInt("Stare", stareSeconds);
        t.putInt("Shelter", shelterSeconds);
        return t;
    }

    public static PlayerProfile load(CompoundTag t) {
        PlayerProfile p = new PlayerProfile();
        p.boldness = t.getFloat("Boldness");
        p.cunning = t.getFloat("Cunning");
        p.fakeRushRate = t.getFloat("FakeRush");
        p.wariness = t.getFloat("Wariness");
        p.patience = t.getFloat("Patience");
        p.firstDay = t.getDouble("FirstDay");
        p.respawnDay = t.getDouble("RespawnDay");
        if (t.hasUUID("Stalker")) p.stalkerUuid = t.getUUID("Stalker");
        p.fakeRushes = t.getInt("FakeRushes");
        p.flinches = t.getInt("Flinches");
        p.escapes = t.getInt("Escapes");
        p.hitsDealt = t.getInt("HitsDealt");
        p.hitsTaken = t.getInt("HitsTaken");
        p.stareSeconds = t.getInt("Stare");
        p.shelterSeconds = t.getInt("Shelter");
        return p;
    }
}
