package com.rostroshuecos;

import com.rostroshuecos.entity.AcosadorEntity;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;

public final class Jumpscare {
    private Jumpscare() {}

    /** Susto con la cara de un monstruo al azar. */
    public static void send(ServerPlayerEntity player, int type) {
        send(player, type, -1);
    }

    public static void send(ServerPlayerEntity player, int type, int mob) {
        ServerPlayNetworking.send(player, new JumpscarePayload(type, mob));
    }

    /** 0 Sombra, 1 Raiz, 2 Sudario, 3 Hueco, 4 Mandibula, 5 Doble Boca, -1 otro. */
    public static int mobOf(Entity e) {
        return e instanceof AcosadorEntity a ? a.mobIndex() : -1;
    }
}
