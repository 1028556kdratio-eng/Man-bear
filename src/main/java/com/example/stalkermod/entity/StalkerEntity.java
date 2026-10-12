package com.example.stalkermod.entity;

import com.example.stalkermod.data.PlayerProfile;
import com.example.stalkermod.data.StalkerData;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

public class StalkerEntity extends PathfinderMob {

    /** Behaviour states. Synced to the client so the model can pose accordingly. */
    public enum State { STALK, HIDE, BLEND, PEEK, FAKE_RUSH, CHASE, RETREAT }

    private static final EntityDataAccessor<Integer> DATA_STATE =
            SynchedEntityData.defineId(StalkerEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_PEEK_SIDE =
            SynchedEntityData.defineId(StalkerEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_FROZEN =
            SynchedEntityData.defineId(StalkerEntity.class, EntityDataSerializers.BOOLEAN);

    private UUID targetId;
    public int lastHurtTick = -1000;

    public StalkerEntity(EntityType<? extends StalkerEntity> type, Level level) {
        super(type, level);
        this.setMaxUpStep(1.25F);
        this.xpReward = 60;
        this.setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 200.0)
                .add(Attributes.MOVEMENT_SPEED, 0.25)
                .add(Attributes.ATTACK_DAMAGE, 10.0)
                .add(Attributes.ATTACK_KNOCKBACK, 1.5)
                .add(Attributes.FOLLOW_RANGE, 128.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.8)
                .add(Attributes.ARMOR, 6.0);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new StalkerBrainGoal(this));
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_STATE, 0);
        this.entityData.define(DATA_PEEK_SIDE, 0);
        this.entityData.define(DATA_FROZEN, false);
    }

    // ---- synced state ----
    public State getState() {
        int i = Mth.clamp(this.entityData.get(DATA_STATE), 0, State.values().length - 1);
        return State.values()[i];
    }

    public void setState(State s) { this.entityData.set(DATA_STATE, s.ordinal()); }

    /** +1 = leaning out to its right, -1 = to its left, 0 = not peeking. */
    public int getPeekSide() { return this.entityData.get(DATA_PEEK_SIDE); }

    public void setPeekSide(int side) { this.entityData.set(DATA_PEEK_SIDE, side); }

    public boolean isFrozenPose() { return this.entityData.get(DATA_FROZEN); }

    public void setFrozenPose(boolean b) { this.entityData.set(DATA_FROZEN, b); }

    // ---- target handling ----
    public void setTargetPlayer(UUID id) { this.targetId = id; }

    public UUID getTargetPlayerUuid() { return targetId; }

    /** The player this stalker is hunting. Egg-spawned stalkers bind to the nearest player. */
    public Player resolveTarget() {
        Player p = null;
        if (targetId != null) {
            p = level().getPlayerByUUID(targetId);
            if (p != null && (p.isSpectator() || !p.isAlive())) p = null;
        } else {
            p = level().getNearestPlayer(this, 160.0);
            if (p != null && !level().isClientSide && level().getServer() != null) {
                targetId = p.getUUID();
                PlayerProfile pr = StalkerData.get(level().getServer()).profile(targetId);
                if (pr.stalkerUuid == null) pr.stalkerUuid = getUUID();
            }
        }
        return p;
    }

    // ---- behaviour hooks ----
    @Override
    public void aiStep() {
        super.aiStep();
        if (level().isClientSide) return;

        // slow regeneration when left alone
        if (tickCount % 20 == 0 && tickCount - lastHurtTick > 100 && getHealth() < getMaxHealth()) {
            heal(2.0F);
        }

        // only one stalker per player: older duplicates (e.g. from unloaded chunks) remove themselves
        if (tickCount % 200 == 0 && targetId != null && level().getServer() != null) {
            PlayerProfile pr = StalkerData.get(level().getServer()).profile(targetId);
            if (pr.stalkerUuid != null && !pr.stalkerUuid.equals(getUUID())) {
                discard();
            }
        }
    }

    @Override
    public void travel(Vec3 input) {
        // while "frozen" it is a statue: gravity only, no walking
        if (!level().isClientSide && isFrozenPose()) {
            super.travel(Vec3.ZERO);
            return;
        }
        super.travel(input);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        boolean r = super.hurt(source, amount);
        if (r && !level().isClientSide) {
            lastHurtTick = tickCount;
            if (source.getEntity() instanceof Player p && level().getServer() != null) {
                StalkerData.get(level().getServer()).profile(p.getUUID()).onHitStalker();
            }
        }
        return r;
    }

    // ---- misc ----
    @Override
    protected float getStandingEyeHeight(Pose pose, EntityDimensions dims) { return 3.0F; }

    @Override
    public boolean removeWhenFarAway(double dist) { return false; }

    @Override
    protected SoundEvent getAmbientSound() { return null; }

    @Override
    protected SoundEvent getHurtSound(DamageSource src) { return SoundEvents.POLAR_BEAR_HURT; }

    @Override
    protected SoundEvent getDeathSound() { return SoundEvents.POLAR_BEAR_DEATH; }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        if (!isFrozenPose()) this.playSound(SoundEvents.RAVAGER_STEP, 0.7F, 0.6F);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (targetId != null) tag.putUUID("Target", targetId);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.hasUUID("Target")) targetId = tag.getUUID("Target");
    }
}
