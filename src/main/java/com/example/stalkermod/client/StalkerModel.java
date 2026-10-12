package com.example.stalkermod.client;

import com.example.stalkermod.StalkerMod;
import com.example.stalkermod.entity.StalkerEntity;
import com.example.stalkermod.entity.StalkerEntity.State;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * A hunched, long-armed, digitigrade bear standing on two legs. Built from cubes in code so the
 * mod has no asset dependencies. Swap it for a Blockbench/GeckoLib model for a more realistic look.
 */
public class StalkerModel extends EntityModel<StalkerEntity> {
    public static final ModelLayerLocation LAYER =
            new ModelLayerLocation(new ResourceLocation(StalkerMod.MODID, "stalker"), "main");

    private final ModelPart root, body, head, jaw;
    private final ModelPart armL, armR, foreL, foreR;
    private final ModelPart legL, legR, shinL, shinR, footL, footR;

    public StalkerModel(ModelPart root) {
        this.root = root;
        this.body = root.getChild("body");
        this.head = body.getChild("head");
        this.jaw = head.getChild("jaw");
        this.armL = body.getChild("left_arm");
        this.armR = body.getChild("right_arm");
        this.foreL = armL.getChild("left_forearm");
        this.foreR = armR.getChild("right_forearm");
        this.legL = root.getChild("left_leg");
        this.legR = root.getChild("right_leg");
        this.shinL = legL.getChild("left_shin");
        this.shinR = legR.getChild("right_shin");
        this.footL = shinL.getChild("left_foot");
        this.footR = shinR.getChild("right_foot");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        // torso + shoulder hump (pivot at the hips)
        PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create()
                        .texOffs(0, 20).addBox(-7F, -17F, -5F, 14F, 17F, 10F)
                        .texOffs(50, 20).addBox(-6F, -20F, -4F, 12F, 5F, 8F),
                PartPose.offset(0F, 10F, 0F));

        // head
        PartDefinition head = body.addOrReplaceChild("head", CubeListBuilder.create()
                        .texOffs(0, 0).addBox(-5F, -9F, -8F, 10F, 9F, 9F),
                PartPose.offset(0F, -16F, -2F));
        head.addOrReplaceChild("left_ear", CubeListBuilder.create()
                .texOffs(84, 0).addBox(-1.5F, -1.5F, -0.5F, 3F, 3F, 1F), PartPose.offset(4F, -9F, -3F));
        head.addOrReplaceChild("right_ear", CubeListBuilder.create()
                .texOffs(84, 0).addBox(-1.5F, -1.5F, -0.5F, 3F, 3F, 1F), PartPose.offset(-4F, -9F, -3F));
        PartDefinition snout = head.addOrReplaceChild("snout", CubeListBuilder.create()
                .texOffs(40, 0).addBox(-2.5F, -4.5F, -4F, 5F, 4F, 4F), PartPose.offset(0F, 0F, -8F));
        snout.addOrReplaceChild("nose", CubeListBuilder.create()
                .texOffs(92, 0).addBox(-1.5F, -4.5F, -5F, 3F, 2F, 1F), PartPose.ZERO);
        head.addOrReplaceChild("jaw", CubeListBuilder.create()
                .texOffs(56, 0).addBox(-2.5F, 0F, -9F, 5F, 2F, 9F), PartPose.offset(0F, -0.5F, -1F));

        // long arms with claws
        addArm(body, "right", -9F);
        addArm(body, "left", 9F);

        // digitigrade legs: thigh forward, shin back, foot flat
        addLeg(root, "right", -4.5F);
        addLeg(root, "left", 4.5F);

        return LayerDefinition.create(mesh, 128, 128);
    }

    private static void addArm(PartDefinition body, String side, float x) {
        PartDefinition arm = body.addOrReplaceChild(side + "_arm", CubeListBuilder.create()
                .texOffs(0, 50).addBox(-2.5F, -1F, -2.5F, 5F, 10F, 5F), PartPose.offset(x, -14F, 0F));
        PartDefinition fore = arm.addOrReplaceChild(side + "_forearm", CubeListBuilder.create()
                .texOffs(22, 50).addBox(-2.5F, 0F, -2.5F, 5F, 10F, 5F), PartPose.offset(0F, 9F, 0F));
        fore.addOrReplaceChild(side + "_paw", CubeListBuilder.create()
                .texOffs(44, 50).addBox(-3F, 0F, -3F, 6F, 3F, 6F)
                .texOffs(70, 50).addBox(-2.5F, 3F, -3.5F, 1F, 3F, 1F)
                .texOffs(70, 50).addBox(-0.5F, 3F, -3.5F, 1F, 3F, 1F)
                .texOffs(70, 50).addBox(1.5F, 3F, -3.5F, 1F, 3F, 1F), PartPose.offset(0F, 10F, 0F));
    }

    private static void addLeg(PartDefinition root, String side, float x) {
        PartDefinition leg = root.addOrReplaceChild(side + "_leg", CubeListBuilder.create()
                .texOffs(0, 70).addBox(-2.5F, 0F, -3F, 5F, 7F, 6F), PartPose.offset(x, 10F, 1F));
        PartDefinition shin = leg.addOrReplaceChild(side + "_shin", CubeListBuilder.create()
                .texOffs(24, 70).addBox(-2F, 0F, -2F, 4F, 7F, 4F), PartPose.offset(0F, 6.5F, 1F));
        shin.addOrReplaceChild(side + "_foot", CubeListBuilder.create()
                .texOffs(42, 70).addBox(-2.5F, 0F, -5F, 5F, 2F, 7F), PartPose.offset(0F, 7F, 0F));
    }

    @Override
    public void setupAnim(StalkerEntity e, float limbSwing, float limbSwingAmount, float age,
                          float headYaw, float headPitch) {
        State s = e.getState();
        boolean frozen = e.isFrozenPose();
        boolean sprint = s == State.CHASE || s == State.FAKE_RUSH || s == State.RETREAT;
        boolean blend = s == State.BLEND;
        int peek = e.getPeekSide();

        float amt = frozen ? 0F : Math.min(1F, limbSwingAmount * 1.6F);
        float ph = limbSwing * (sprint ? 0.45F : 0.55F);
        float swing = Mth.cos(ph) * (sprint ? 1.25F : 0.75F) * amt;
        float kneeL = Math.max(0F, Mth.sin(ph)) * 0.9F * amt;
        float kneeR = Math.max(0F, -Mth.sin(ph)) * 0.9F * amt;

        // ---- body: hunched forward, lunging when running, ramrod-straight when imitating a tree ----
        body.xRot = blend ? 0.03F : (sprint ? 0.65F : 0.30F);
        body.yRot = Mth.cos(ph) * 0.08F * amt;
        body.zRot = peek != 0 ? -peek * 0.42F : 0F;
        body.y = 10F + Mth.sin(age * 0.07F) * 0.35F;   // slow breathing

        // ---- legs ----
        legL.xRot = -0.5F + swing;
        legR.xRot = -0.5F - swing;
        shinL.xRot = 0.9F + kneeL;
        shinR.xRot = 0.9F + kneeR;
        footL.xRot = -(legL.xRot + shinL.xRot);
        footR.xRot = -(legR.xRot + shinR.xRot);

        // ---- arms ----
        if (blend) {
            armL.xRot = armR.xRot = 0F;
            armL.zRot = armR.zRot = 0F;
            foreL.xRot = foreR.xRot = 0F;
        } else if (sprint) {
            armR.xRot = -1.0F - swing * 0.4F;
            armL.xRot = -1.0F + swing * 0.4F;
            armR.zRot = 0.3F;
            armL.zRot = -0.3F;
            foreR.xRot = foreL.xRot = -0.5F;
        } else {
            float twitch = frozen ? Mth.sin(age * 0.05F) * 0.03F : 0F;
            armR.xRot = 0.12F - swing * 0.6F + twitch;
            armL.xRot = 0.12F + swing * 0.6F - twitch;
            armR.zRot = 0.10F;
            armL.zRot = -0.10F;
            foreR.xRot = foreL.xRot = -0.3F;
        }

        // ---- head: tracks the player; cocked at an unsettling angle while frozen in the open ----
        head.yRot = Mth.clamp(headYaw, -70F, 70F) * Mth.DEG_TO_RAD;
        head.xRot = headPitch * Mth.DEG_TO_RAD - body.xRot * 0.8F;
        if (peek != 0) {
            head.zRot = peek * 0.35F;           // keeps the head level while the body leans out
        } else if (frozen && !blend) {
            head.zRot = 0.28F;
        } else {
            head.zRot = 0F;
        }

        // ---- jaw: hangs open when charging ----
        jaw.xRot = (s == State.CHASE || s == State.FAKE_RUSH)
                ? 0.55F + Mth.sin(age * 0.8F) * 0.12F
                : 0.04F;
    }

    @Override
    public void renderToBuffer(PoseStack ps, VertexConsumer vc, int light, int overlay,
                               float r, float g, float b, float a) {
        root.render(ps, vc, light, overlay, r, g, b, a);
    }
}
