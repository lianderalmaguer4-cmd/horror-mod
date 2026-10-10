package com.rostroshuecos;

import com.rostroshuecos.entity.ChicaRotaEntity;
import com.rostroshuecos.entity.MunecoPerdidoEntity;
import com.rostroshuecos.entity.NinoGrisEntity;
import com.rostroshuecos.entity.SombraHuecaEntity;
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

    /** 0 Pollita, 1 Sombra, 2 Niño, 3 Muñeco, -1 otro. */
    public static int mobOf(Entity e) {
        if (e instanceof ChicaRotaEntity) return 0;
        if (e instanceof SombraHuecaEntity) return 1;
        if (e instanceof NinoGrisEntity) return 2;
        if (e instanceof MunecoPerdidoEntity) return 3;
        return -1;
    }
}
