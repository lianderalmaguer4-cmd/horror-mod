package com.rostroshuecos;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

/** Servidor -> cliente. type: 0 = fuerte, 1 = glitch. mob: 0 Sombra, 1 Raiz, 2 Sudario, 3 Hueco, 4 Mandibula, 5 Doble Boca, -1 al azar. */
public record JumpscarePayload(int type, int mob) implements CustomPayload {
    public static final CustomPayload.Id<JumpscarePayload> ID = new CustomPayload.Id<>(RostrosHuecos.id("jumpscare"));
    public static final PacketCodec<RegistryByteBuf, JumpscarePayload> CODEC =
            PacketCodec.tuple(PacketCodecs.INTEGER, JumpscarePayload::type,
                              PacketCodecs.INTEGER, JumpscarePayload::mob,
                              JumpscarePayload::new);

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
