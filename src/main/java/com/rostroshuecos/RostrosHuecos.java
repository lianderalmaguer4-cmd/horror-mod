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
        PayloadTypeRegistry.playS2C().register(JumpscarePayload.ID, JumpscarePayload.CODEC);
        ModEntities.register();
        ModItems.register();
        HorrorEvents.register();
    }
}
