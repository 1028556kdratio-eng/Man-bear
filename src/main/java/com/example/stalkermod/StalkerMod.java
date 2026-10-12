package com.example.stalkermod;

import com.example.stalkermod.entity.StalkerEntity;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(StalkerMod.MODID)
public class StalkerMod {
    public static final String MODID = "stalkermod";

    public StalkerMod() {
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        ModEntities.ENTITIES.register(bus);
        ModItems.ITEMS.register(bus);
        bus.addListener(this::registerAttributes);
        bus.addListener(this::addCreative);
        MinecraftForge.EVENT_BUS.register(new StalkerEvents());
    }

    private void registerAttributes(EntityAttributeCreationEvent e) {
        e.put(ModEntities.STALKER.get(), StalkerEntity.createAttributes().build());
    }

    private void addCreative(BuildCreativeModeTabContentsEvent e) {
        if (e.getTabKey() == CreativeModeTabs.SPAWN_EGGS) {
            e.accept(ModItems.STALKER_SPAWN_EGG);
        }
    }
}
