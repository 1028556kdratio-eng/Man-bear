package com.example.stalkermod.client;

import com.example.stalkermod.StalkerMod;
import com.example.stalkermod.entity.StalkerEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

public class StalkerRenderer extends MobRenderer<StalkerEntity, StalkerModel> {
    private static final ResourceLocation TEXTURE =
            new ResourceLocation(StalkerMod.MODID, "textures/entity/stalker.png");

    public StalkerRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new StalkerModel(ctx.bakeLayer(StalkerModel.LAYER)), 0.9F);
        this.addLayer(new StalkerEyesLayer(this));
    }

    @Override
    public ResourceLocation getTextureLocation(StalkerEntity entity) {
        return TEXTURE;
    }

    @Override
    protected void scale(StalkerEntity entity, PoseStack poseStack, float partialTick) {
        poseStack.scale(1.45F, 1.45F, 1.45F);
    }
}
