package com.example.stalkermod.client;

import com.example.stalkermod.StalkerMod;
import com.example.stalkermod.entity.StalkerEntity;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.resources.ResourceLocation;

/** Fullbright glowing eyes - visible in the dark long before the body is. */
public class StalkerEyesLayer extends EyesLayer<StalkerEntity, StalkerModel> {
    private static final RenderType EYES =
            RenderType.eyes(new ResourceLocation(StalkerMod.MODID, "textures/entity/stalker_eyes.png"));

    public StalkerEyesLayer(RenderLayerParent<StalkerEntity, StalkerModel> parent) {
        super(parent);
    }

    @Override
    public RenderType renderType() {
        return EYES;
    }
}
