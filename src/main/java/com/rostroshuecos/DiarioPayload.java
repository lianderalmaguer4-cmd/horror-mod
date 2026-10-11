package com.rostroshuecos;

import java.util.List;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

/** Servidor -> cliente: abre el diario con estas paginas. */
public record DiarioPayload(List<String> pages) implements CustomPayload {
    public static final CustomPayload.Id<DiarioPayload> ID = new CustomPayload.Id<>(RostrosHuecos.id("diario"));
    public static final PacketCodec<RegistryByteBuf, DiarioPayload> CODEC =
            PacketCodec.tuple(PacketCodecs.STRING.collect(PacketCodecs.toList()), DiarioPayload::pages, DiarioPayload::new);

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
