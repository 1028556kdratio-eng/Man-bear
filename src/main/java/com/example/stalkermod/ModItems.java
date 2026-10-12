package com.example.stalkermod;

import net.minecraft.world.item.Item;
import net.minecraftforge.common.ForgeSpawnEggItem;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModItems {
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, StalkerMod.MODID);

    public static final RegistryObject<Item> STALKER_SPAWN_EGG = ITEMS.register("stalker_spawn_egg",
            () -> new ForgeSpawnEggItem(ModEntities.STALKER, 0x2B1D14, 0x8A1C1C, new Item.Properties()));
}
