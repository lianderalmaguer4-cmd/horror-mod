package com.rostroshuecos;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.util.Identifier;

public class RostrosHuecos implements ModInitializer {
    public static final String MOD_ID = "rostroshuecos";

    public static Identifier id(String path) {
        return Identifier.of(MOD_ID, path);
    }

    @Override
    public void onInitialize() {
        ModSounds.init();
        PayloadTypeRegistry.playS2C().register(JumpscarePayload.ID, JumpscarePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(HidePayload.ID, HidePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(DiarioPayload.ID, DiarioPayload.CODEC);
        ModEntities.register();
        ModItems.register();
        HorrorEvents.register();
        StalkerDirector.register();
        StructureManager.register();
        FinalEvent.register();
        DimTerrain.register();
    }
}
