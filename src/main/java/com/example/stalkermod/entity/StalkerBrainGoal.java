package com.example.stalkermod.entity;

import com.example.stalkermod.data.PlayerProfile;
import com.example.stalkermod.data.StalkerData;
import com.example.stalkermod.entity.StalkerEntity.State;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;

/**
 * The Stalker's whole brain, as one state machine.
 *
 *  STALK     circle the player at a slowly shrinking radius; freeze whenever the player is looking at it
 *  HIDE      sneak to a spot that is out of the player's line of sight (behind trees / walls / hills)
 *  BLEND     stand perfectly upright and motionless behind cover, imitating a tree trunk
 *  PEEK      lean out from behind the cover so part of it is visible, then duck back
 *  FAKE_RUSH sprint at the player, roar, and stop dead short - then watch how the player reacts
 *  CHASE     a real attack
 *  RETREAT   back off into the distance and start over
 *
 * What it learns about the player lives in {@link PlayerProfile}.
 */
public class StalkerBrainGoal extends Goal {

    // Multipliers on the movement speed attribute. Tune these to taste.
    private static final double SPEED_CREEP = 0.6;
    private static final double SPEED_WALK = 0.85;
    private static final double SPEED_RUN = 1.25;
    private static final double SPEED_SPRINT = 1.5;
    /** cos of half the player's "I can see that" cone (~53 degrees). */
    private static final double FOV_COS = 0.6;

    private final StalkerEntity mob;
    private Player target;
    private StalkerData data;
    private PlayerProfile profile;

    private boolean watched;
    private int stateTicks, watchTicks, stalkTicks, repath, hideRecalc, attackCooldown, twigCooldown;
    private int peekHold, peekLimit, peekWatch, peekCount, blendDuration, chaseTicks, stuckTicks, haltTicks;
    private double stalkRadius = 36;
    private int circleDir = 1;
    private int peekSideSign = 1;
    private boolean pendingHide, rushHalted, hitThisChase;
    private double rushStopDist;
    private Vec3 hideSpot, peekSpot, rushStart, retreatSpot;
    private BlockPos cover;

    public StalkerBrainGoal(StalkerEntity mob) {
        this.mob = mob;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (mob.level().isClientSide || mob.level().getServer() == null) return false;
        Player p = mob.resolveTarget();
        if (p == null) return false;
        target = p;
        return true;
    }

    @Override
    public boolean canContinueToUse() { return canUse(); }

    @Override
    public boolean requiresUpdateEveryTick() { return true; }

    @Override
    public void start() {
        refreshProfile();
        enter(State.STALK);
    }

    @Override
    public void stop() {
        mob.getNavigation().stop();
        mob.setFrozenPose(false);
        mob.setPeekSide(0);
    }

    private void refreshProfile() {
        data = StalkerData.get(mob.level().getServer());
        profile = data.profile(target.getUUID());
        if (profile.firstDay < 0) profile.firstDay = mob.level().getDayTime() / 24000.0;
    }

    // =====================================================================================
    //  main tick
    // =====================================================================================
    @Override
    public void tick() {
        if (target == null) return;
        if (profile == null || mob.tickCount % 40 == 0) refreshProfile();
        if (mob.tickCount % 100 == 0) data.setDirty();

        Level level = mob.level();
        stateTicks++;
        if (attackCooldown > 0) attackCooldown--;
        if (twigCooldown > 0) twigCooldown--;

        double dist = mob.distanceTo(target);
        watched = playerCanSee();
        watchTicks = watched ? watchTicks + 1 : 0;
        if (watched && watchTicks % 20 == 0) profile.onStareSecond();
        double bold = profile.effBoldness(level.getDayTime() / 24000.0);

        State s = mob.getState();

        // got hit: bold stalkers answer with violence, cautious ones back off
        if (mob.tickCount - mob.lastHurtTick <= 1 && s != State.CHASE && s != State.RETREAT) {
            enter(bold > 0.6 || mob.getRandom().nextFloat() < 0.35F ? State.CHASE : State.RETREAT);
            return;
        }
        // badly hurt: run and recover
        if (s != State.RETREAT && mob.getHealth() < mob.getMaxHealth() * 0.3F) {
            enter(State.RETREAT);
            return;
        }

        switch (s) {
            case STALK -> tickStalk(dist, bold);
            case HIDE -> tickHide(dist, bold);
            case BLEND -> tickBlend(dist, bold);
            case PEEK -> tickPeek(dist, bold);
            case FAKE_RUSH -> tickFakeRush(dist);
            case CHASE -> tickChase(bold);
            case RETREAT -> tickRetreat(dist);
        }
    }

    // =====================================================================================
    //  state transitions
    // =====================================================================================
    private void enter(State s) {
        mob.setState(s);
        stateTicks = 0;
        repath = 0;
        mob.getNavigation().stop();
        mob.setPeekSide(0);
        switch (s) {
            case STALK -> {
                resetRadius();
                stalkTicks = 0;
                peekCount = 0;
                pendingHide = false;
                mob.setFrozenPose(false);
            }
            case HIDE -> {
                hideRecalc = 0;
                hideSpot = null;
                mob.setFrozenPose(false);
            }
            case BLEND -> {
                blendDuration = (int) ((80 + mob.getRandom().nextInt(160)) * profile.patience);
                mob.setFrozenPose(true);
            }
            case PEEK -> {
                peekSpot = null;
                peekHold = 0;
                peekWatch = 0;
                peekLimit = 60 + mob.getRandom().nextInt(80);
                mob.setFrozenPose(false);
            }
            case FAKE_RUSH -> {
                rushStart = target.position();
                rushStopDist = 6.0 + mob.getRandom().nextDouble() * 3.0;
                rushHalted = false;
                haltTicks = 0;
                mob.setFrozenPose(false);
                mob.playSound(SoundEvents.POLAR_BEAR_WARNING, 3.0F, 0.5F);
            }
            case CHASE -> {
                chaseTicks = 0;
                stuckTicks = 0;
                hitThisChase = false;
                mob.setFrozenPose(false);
                mob.playSound(SoundEvents.RAVAGER_ROAR, 3.0F, 0.6F);
            }
            case RETREAT -> {
                retreatSpot = null;
                mob.setFrozenPose(false);
            }
        }
    }

    private void resetRadius() {
        double bold = profile.effBoldness(mob.level().getDayTime() / 24000.0);
        stalkRadius = 34 + mob.getRandom().nextDouble() * 8 - 16 * bold;
    }

    private void decideAttack(double bold) {
        profile.fakeRushRate = Mth.clamp(profile.fakeRushRate, 0.1F, 0.9F);
        if (mob.getRandom().nextFloat() < 0.06 + 0.6 * bold) {
            enter(State.CHASE);
        } else if (mob.getRandom().nextFloat() < profile.fakeRushRate) {
            enter(State.FAKE_RUSH);
        } else {
            enter(State.RETREAT);
        }
    }

    // =====================================================================================
    //  STALK
    // =====================================================================================
    private void tickStalk(double dist, double bold) {
        stalkTicks++;
        if (stalkTicks % 20 == 0) stalkRadius -= 0.4 + 1.1 * bold;
        double attackRadius = Math.max(9.0, 15.0 - 6.0 * bold);
        if (mob.getRandom().nextInt(500) == 0) circleDir = -circleDir;
        mob.getLookControl().setLookAt(target, 30.0F, 30.0F);

        if (freezeIfWatched(dist)) {
            // the stalking rule: it stops dead while you look at it
            faceTarget();
            int stareLimit = (int) (200 * profile.patience * Math.max(0.3, 1.4 - bold));
            if (watchTicks > stareLimit) {
                if (profile.cunning > 0.4 && mob.getRandom().nextFloat() < profile.cunning) {
                    pendingHide = true;   // can't stare it away forever: it will vanish into cover when you blink
                    watchTicks = 0;
                } else {
                    decideAttack(bold);
                }
            }
            return;
        }

        if (pendingHide || (stalkTicks % 60 == 0 && mob.getRandom().nextFloat() < 0.1 + 0.4 * profile.cunning)) {
            enter(State.HIDE);
            return;
        }
        if (stalkRadius <= attackRadius && stalkTicks > 300 && dist < attackRadius + 5) {
            decideAttack(bold);
            return;
        }
        if (twigCooldown <= 0 && mob.getRandom().nextInt(200) == 0) {
            twig();
            twigCooldown = 300;
        }
        circleMove(dist, bold);
    }

    private void circleMove(double dist, double bold) {
        if (--repath > 0 && !mob.getNavigation().isDone()) return;
        repath = 10;
        Vec3 pp = target.position();
        Vec3 ep = mob.position();
        double ang = Math.atan2(ep.z - pp.z, ep.x - pp.x);
        double r = dist + Mth.clamp(stalkRadius - dist, -4.0, 4.0);
        double dAng = circleDir * 8.0 / Math.max(r, 8.0);
        double tx = pp.x + Math.cos(ang + dAng) * r;
        double tz = pp.z + Math.sin(ang + dAng) * r;
        double speed = SPEED_WALK + 0.2 * bold;
        Vec3 g = groundAt(tx, tz, mob.getBlockY() + 4);
        boolean ok = g != null && mob.getNavigation().moveTo(g.x, g.y, g.z, speed);
        if (!ok) {
            Vec3 alt = DefaultRandomPos.getPosTowards(mob, 12, 6, pp, Math.PI / 2.0);
            if (alt != null) mob.getNavigation().moveTo(alt.x, alt.y, alt.z, speed);
        }
    }

    // =====================================================================================
    //  HIDE / BLEND / PEEK
    // =====================================================================================
    private void tickHide(double dist, double bold) {
        mob.getLookControl().setLookAt(target, 30.0F, 30.0F);
        if (--hideRecalc <= 0) {
            if (!findCover()) {
                enter(State.STALK);
                return;
            }
            hideRecalc = 80;
        }
        if (freezeIfWatched(dist)) {
            faceTarget();
            return;
        }
        if (--repath <= 0) {
            mob.getNavigation().moveTo(hideSpot.x, hideSpot.y, hideSpot.z, SPEED_WALK + 0.15 * bold);
            repath = 10;
        }
        if (mob.position().distanceToSqr(hideSpot) < 2.5) {
            enter(State.BLEND);
        } else if (stateTicks > 320) {
            enter(State.STALK);
        }
    }

    private void tickBlend(double dist, double bold) {
        mob.getNavigation().stop();
        mob.setFrozenPose(true);
        mob.getLookControl().setLookAt(target, 30.0F, 30.0F);
        faceTarget();

        // you walked right past the "tree"... ambush
        if (dist < 9 && mob.getRandom().nextFloat() < 0.002 + 0.02 * bold) {
            enter(State.CHASE);
            return;
        }
        // you can see through its cover and you keep staring: it breaks cover
        if (watched && watchTicks > 140 && dist < 22) {
            enter(bold > 0.5 ? State.FAKE_RUSH : State.RETREAT);
            return;
        }
        if (stateTicks > blendDuration) {
            if (cover != null && mob.getRandom().nextFloat() < 0.5 + 0.45 * profile.cunning) {
                enter(State.PEEK);
            } else {
                enter(State.STALK);
            }
        }
    }

    private void tickPeek(double dist, double bold) {
        mob.getLookControl().setLookAt(target, 30.0F, 30.0F);
        if (peekSpot == null && !computePeekSpot()) {
            enter(State.STALK);
            return;
        }
        if (dist < 7) {
            enter(bold > 0.4 ? State.CHASE : State.RETREAT);
            return;
        }
        boolean atSpot = mob.position().distanceToSqr(peekSpot) < 1.8;
        if (!atSpot) {
            if (freezeIfWatched(dist)) {
                faceTarget();
                return;
            }
            if (--repath <= 0) {
                mob.getNavigation().moveTo(peekSpot.x, peekSpot.y, peekSpot.z, SPEED_CREEP);
                repath = 8;
            }
            if (stateTicks > 200) enter(State.HIDE);
            return;
        }

        // at the edge of the cover: lean out and stare
        mob.getNavigation().stop();
        mob.setFrozenPose(true);
        mob.setPeekSide(peekSideSign);
        faceTarget();
        peekHold++;
        if (watched) peekWatch++;
        if (peekWatch > 35) {            // noticed -> duck back behind cover
            enter(State.HIDE);
            return;
        }
        if (peekHold > peekLimit) {
            peekCount++;
            if (peekCount >= 2 && bold > 0.3 && mob.getRandom().nextFloat() < 0.5F) {
                enter(State.FAKE_RUSH);
            } else if (mob.getRandom().nextBoolean()) {
                enter(State.HIDE);
            } else {
                enter(State.STALK);
            }
        }
    }

    // =====================================================================================
    //  FAKE RUSH / CHASE / RETREAT
    // =====================================================================================
    private void tickFakeRush(double dist) {
        mob.getLookControl().setLookAt(target, 60.0F, 60.0F);
        if (!rushHalted) {
            if (--repath <= 0) {
                mob.getNavigation().moveTo(target, SPEED_SPRINT);
                repath = 5;
            }
            if (dist <= rushStopDist || stateTicks > 100) {
                rushHalted = true;
                haltTicks = 0;
                mob.getNavigation().stop();
                mob.setFrozenPose(true);   // slams to a halt
            }
            return;
        }
        faceTarget();
        if (++haltTicks > 25) {
            boolean flinched = target.position().distanceTo(rushStart) > 3.0;
            profile.onFakeRush(flinched);
            enter(State.RETREAT);
        }
    }

    private void tickChase(double bold) {
        mob.setFrozenPose(false);
        mob.getLookControl().setLookAt(target, 40.0F, 40.0F);
        chaseTicks++;
        if (--repath <= 0) {
            boolean ok = mob.getNavigation().moveTo(target, SPEED_SPRINT);
            stuckTicks = ok ? 0 : stuckTicks + 5;
            repath = 5;
        }
        if (attackCooldown <= 0 && mob.getBoundingBox().inflate(0.8, 0.3, 0.8).intersects(target.getBoundingBox())) {
            mob.swing(InteractionHand.MAIN_HAND);
            mob.doHurtTarget(target);
            attackCooldown = 25;
            hitThisChase = true;
            profile.onHitPlayer();
            // hit-and-run is more likely from a timid stalker
            if (!target.isAlive() || mob.getRandom().nextFloat() < 0.55 - 0.4 * bold) {
                enter(State.RETREAT);
                return;
            }
        }
        if (chaseTicks > 500 || stuckTicks > 80) {
            if (!hitThisChase) profile.onEscaped();
            enter(State.RETREAT);
        }
    }

    private void tickRetreat(double dist) {
        mob.setFrozenPose(false);
        mob.getLookControl().setLookAt(target, 20.0F, 20.0F);
        if (retreatSpot == null || (mob.getNavigation().isDone() && stateTicks % 20 == 0)) {
            retreatSpot = DefaultRandomPos.getPosAway(mob, 24, 8, target.position());
        }
        if (--repath <= 0 && retreatSpot != null) {
            mob.getNavigation().moveTo(retreatSpot.x, retreatSpot.y, retreatSpot.z, SPEED_RUN);
            repath = 10;
        }
        if (dist > 30 || stateTicks > 240) enter(State.STALK);
    }

    // =====================================================================================
    //  helpers: sight, cover search, positions
    // =====================================================================================
    private boolean freezeIfWatched(double dist) {
        if (watched && dist > 5.0) {
            mob.getNavigation().stop();
            mob.setFrozenPose(true);
            return true;
        }
        mob.setFrozenPose(false);
        return false;
    }

    private void faceTarget() {
        double dx = target.getX() - mob.getX();
        double dz = target.getZ() - mob.getZ();
        float yaw = (float) (Math.atan2(dz, dx) * 180.0 / Math.PI) - 90.0F;
        mob.setYRot(Mth.rotLerp(0.25F, mob.getYRot(), yaw));
        mob.yBodyRot = mob.getYRot();
        mob.getLookControl().setLookAt(target, 40.0F, 40.0F);
    }

    /** Is the player actually looking at (and able to see) the stalker? */
    private boolean playerCanSee() {
        Vec3 eye = target.getEyePosition();
        Vec3 look = target.getViewVector(1.0F);
        for (double h : new double[]{1.0, 2.6}) {
            Vec3 p = mob.position().add(0, h, 0);
            Vec3 to = p.subtract(eye);
            double d = to.length();
            if (d < 1.0) return true;
            if (look.dot(to.scale(1.0 / d)) > FOV_COS && !rayBlocked(eye, p)) return true;
        }
        return false;
    }

    private boolean rayBlocked(Vec3 from, Vec3 to) {
        return mob.level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, mob)).getType() != HitResult.Type.MISS;
    }

    /** Fully hidden: body, head and both shoulders are blocked from the player's eyes. */
    private boolean hiddenFrom(Vec3 eye, Vec3 pos) {
        Vec3 dir = new Vec3(pos.x - eye.x, 0, pos.z - eye.z);
        if (dir.lengthSqr() < 1.0E-4) return false;
        dir = dir.normalize();
        Vec3 side = new Vec3(-dir.z, 0, dir.x).scale(0.45);
        return rayBlocked(eye, pos.add(0, 1.0, 0))
                && rayBlocked(eye, pos.add(0, 2.2, 0))
                && rayBlocked(eye, pos.add(side).add(0, 1.6, 0))
                && rayBlocked(eye, pos.subtract(side).add(0, 1.6, 0));
    }

    private boolean standable(Vec3 g) {
        AABB box = new AABB(g.x - 0.7, g.y + 0.1, g.z - 0.7, g.x + 0.7, g.y + 2.4, g.z + 0.7);
        return mob.level().noCollision(box);
    }

    /** First walkable floor at (x,z) scanning downward from fromY. */
    private Vec3 groundAt(double x, double z, int fromY) {
        Level level = mob.level();
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        int bx = Mth.floor(x), bz = Mth.floor(z);
        if (!level.hasChunkAt(m.set(bx, fromY, bz))) return null;
        for (int y = fromY; y > fromY - 12; y--) {
            m.set(bx, y - 1, bz);
            BlockState below = level.getBlockState(m);
            if (below.getCollisionShape(level, m).isEmpty() || !level.getFluidState(m).isEmpty()) continue;
            m.set(bx, y, bz);
            BlockState here = level.getBlockState(m);
            if (here.getCollisionShape(level, m).isEmpty()) return new Vec3(x, y, z);
        }
        return null;
    }

    /** Looks for a block (tree trunk, leaves, wall, hillside) the stalker can fully hide behind. */
    private boolean findCover() {
        Level level = mob.level();
        Vec3 pe = target.getEyePosition();
        BlockPos o = mob.blockPosition();
        List<BlockPos> cands = new ArrayList<>();
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        final int R = 14;
        for (int dx = -R; dx <= R; dx++) {
            for (int dz = -R; dz <= R; dz++) {
                int d2 = dx * dx + dz * dz;
                if (d2 < 9 || d2 > R * R) continue;
                for (int dy = 1; dy <= 3; dy++) {
                    m.set(o.getX() + dx, o.getY() + dy, o.getZ() + dz);
                    if (!level.hasChunkAt(m)) break;
                    BlockState st = level.getBlockState(m);
                    if (st.isAir()) continue;
                    if (st.isCollisionShapeFullBlock(level, m)) {
                        cands.add(m.immutable());
                        break;
                    }
                }
            }
        }
        Collections.shuffle(cands, new java.util.Random(mob.getRandom().nextLong()));

        double best = Double.MAX_VALUE;
        Vec3 bestSpot = null;
        BlockPos bestCover = null;
        int tested = 0;
        for (BlockPos c : cands) {
            if (tested++ > 40) break;
            Vec3 cc = Vec3.atCenterOf(c);
            Vec3 away = new Vec3(cc.x - target.getX(), 0, cc.z - target.getZ());
            if (away.lengthSqr() < 1.0E-4) continue;
            away = away.normalize();
            Vec3 h = cc.add(away.scale(1.4));
            Vec3 g = groundAt(h.x, h.z, c.getY() + 1);
            if (g == null || !standable(g)) continue;
            double dp = Math.sqrt((g.x - target.getX()) * (g.x - target.getX())
                    + (g.z - target.getZ()) * (g.z - target.getZ()));
            if (dp < 8.0) continue;
            if (!hiddenFrom(pe, g)) continue;
            double score = mob.position().distanceTo(g) + Math.abs(dp - stalkRadius) * 0.7;
            if (level.getBlockState(c).is(BlockTags.LOGS)) score -= 4.0;   // trees are its favourite cover
            if (score < best) {
                best = score;
                bestSpot = g;
                bestCover = c;
            }
        }
        if (bestSpot == null) return false;
        hideSpot = bestSpot;
        cover = bestCover;
        return true;
    }

    /** A spot just beside the cover where the head/body is partly visible. */
    private boolean computePeekSpot() {
        if (cover == null || hideSpot == null) return false;
        Vec3 cc = Vec3.atCenterOf(cover);
        Vec3 away = new Vec3(cc.x - target.getX(), 0, cc.z - target.getZ());
        if (away.lengthSqr() < 1.0E-4) return false;
        away = away.normalize();
        Vec3 perp = new Vec3(-away.z, 0, away.x);
        int first = mob.getRandom().nextBoolean() ? 1 : -1;
        Vec3 eye = target.getEyePosition();
        for (int i = 0; i < 4; i++) {
            int sgn = (i % 2 == 0) ? first : -first;
            double off = i < 2 ? 1.5 : 2.1;
            Vec3 cand = hideSpot.add(perp.scale(sgn * off));
            Vec3 g = groundAt(cand.x, cand.z, (int) hideSpot.y + 2);
            if (g == null || !standable(g)) continue;
            if (rayBlocked(eye, g.add(0, 2.6, 0))) continue;   // want the head to be visible from here
            peekSpot = g;
            Vec3 f = new Vec3(target.getX() - hideSpot.x, 0, target.getZ() - hideSpot.z).normalize();
            Vec3 d = peekSpot.subtract(hideSpot);
            double cross = f.x * d.z - f.z * d.x;   // > 0: the spot is on the stalker's right
            peekSideSign = cross > 0 ? 1 : -1;
            return true;
        }
        return false;
    }

    private void twig() {
        mob.level().playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.WOOD_BREAK,
                SoundSource.HOSTILE, 3.0F, 0.7F + mob.getRandom().nextFloat() * 0.4F);
    }
}
