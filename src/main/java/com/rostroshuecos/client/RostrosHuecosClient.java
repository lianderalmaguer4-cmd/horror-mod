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
        EntityRendererRegistry.register(ModEntities.CHICA_ROTA,
                ctx -> new HorrorBipedRenderer<>(ctx, "chica_rota", 1.15F, 0.6F));
        EntityRendererRegistry.register(ModEntities.SOMBRA_HUECA,
                ctx -> new HorrorBipedRenderer<>(ctx, "sombra_hueca", 1.2F, 0.4F));
        EntityRendererRegistry.register(ModEntities.NINO_GRIS,
                ctx -> new HorrorBipedRenderer<>(ctx, "nino_gris", 0.75F, 0.3F));
        EntityRendererRegistry.register(ModEntities.MUNECO_PERDIDO,
                ctx -> new HorrorBipedRenderer<>(ctx, "muneco_perdido", 0.55F, 0.25F));

        ClientPlayNetworking.registerGlobalReceiver(JumpscarePayload.ID,
                (payload, context) -> JumpscareClient.trigger(payload.type()));
        ClientTickEvents.END_CLIENT_TICK.register(client -> JumpscareClient.tick());
        HudRenderCallback.EVENT.register(JumpscareClient::render);
    }
}
