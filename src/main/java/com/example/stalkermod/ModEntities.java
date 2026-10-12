package com.example.stalkermod;

import com.example.stalkermod.entity.StalkerEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, StalkerMod.MODID);

    public static final RegistryObject<EntityType<StalkerEntity>> STALKER = ENTITIES.register("stalker",
            () -> EntityType.Builder.of(StalkerEntity::new, MobCategory.MONSTER)
                    .sized(1.6F, 3.5F)
                    .clientTrackingRange(16)
                    .build("stalker"));
}
