package com.rostroshuecos;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

/** Paquete servidor -> cliente que dispara el susto. type 0 = fuerte (entidad), 1 = glitch (ambiente). */
public record JumpscarePayload(int type) implements CustomPayload {
    public static final CustomPayload.Id<JumpscarePayload> ID = new CustomPayload.Id<>(RostrosHuecos.id("jumpscare"));
    public static final PacketCodec<RegistryByteBuf, JumpscarePayload> CODEC =
            PacketCodec.tuple(PacketCodecs.INTEGER, JumpscarePayload::type, JumpscarePayload::new);

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
