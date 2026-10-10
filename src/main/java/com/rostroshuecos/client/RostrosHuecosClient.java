package com.rostroshuecos.client;

import com.rostroshuecos.JumpscarePayload;
import com.rostroshuecos.ModEntities;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;

public class RostrosHuecosClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        // nombre de la imagen, alto en bloques, proporcion ancho/alto de la imagen
        EntityRendererRegistry.register(ModEntities.CHICA_ROTA,
                ctx -> new SpriteEntityRenderer<>(ctx, "chica_rota", 2.3F, 0.625F));
        EntityRendererRegistry.register(ModEntities.SOMBRA_HUECA,
                ctx -> new SpriteEntityRenderer<>(ctx, "sombra_hueca", 3.0F, 0.266F));
        EntityRendererRegistry.register(ModEntities.NINO_GRIS,
                ctx -> new SpriteEntityRenderer<>(ctx, "nino_gris", 1.5F, 0.35F));
        EntityRendererRegistry.register(ModEntities.MUNECO_PERDIDO,
                ctx -> new SpriteEntityRenderer<>(ctx, "muneco_perdido", 1.1F, 0.473F));

        ClientPlayNetworking.registerGlobalReceiver(JumpscarePayload.ID,
                (payload, context) -> JumpscareClient.trigger(payload.type(), payload.mob()));
        ClientTickEvents.END_CLIENT_TICK.register(client -> JumpscareClient.tick());
        HudRenderCallback.EVENT.register(JumpscareClient::render);
    }
}
