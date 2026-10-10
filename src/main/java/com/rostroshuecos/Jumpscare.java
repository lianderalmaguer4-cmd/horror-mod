package com.rostroshuecos;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;

public final class Jumpscare {
    private Jumpscare() {}

    public static void send(ServerPlayerEntity player, int type) {
        ServerPlayNetworking.send(player, new JumpscarePayload(type));
    }
}
