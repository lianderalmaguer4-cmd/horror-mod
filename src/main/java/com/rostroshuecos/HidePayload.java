package com.rostroshuecos;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

/** Servidor -> cliente: el jugador se esconde (true) o sale del escondite (false). */
public record HidePayload(boolean hidden) implements CustomPayload {
    public static final CustomPayload.Id<HidePayload> ID = new CustomPayload.Id<>(RostrosHuecos.id("hide"));
    public static final PacketCodec<RegistryByteBuf, HidePayload> CODEC =
            PacketCodec.tuple(PacketCodecs.BOOLEAN, HidePayload::hidden, HidePayload::new);

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
