package com.rostroshuecos.client;

import com.rostroshuecos.DiarioPayload;
import com.rostroshuecos.HidePayload;
import com.rostroshuecos.JumpscarePayload;
import com.rostroshuecos.ModEntities;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.MinecraftClient;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;

public class RostrosHuecosClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        // nombre de la imagen, alto en bloques, proporcion ancho/alto de la imagen
        EntityRendererRegistry.register(ModEntities.SOMBRA_HUECA,
                ctx -> new SpriteEntityRenderer<>(ctx, "sombra_hueca", 3.0F, 0.266F));
        EntityRendererRegistry.register(ModEntities.RAIZ,
                ctx -> new SpriteEntityRenderer<>(ctx, "raiz", 2.7F, 0.756F));
        EntityRendererRegistry.register(ModEntities.SUDARIO,
                ctx -> new SpriteEntityRenderer<>(ctx, "sudario", 2.9F, 0.305F));
        EntityRendererRegistry.register(ModEntities.HUECO,
                ctx -> new SpriteEntityRenderer<>(ctx, "hueco", 1.7F, 0.75F));
        EntityRendererRegistry.register(ModEntities.MANDIBULA,
                ctx -> new SpriteEntityRenderer<>(ctx, "mandibula", 2.3F, 0.334F));
        EntityRendererRegistry.register(ModEntities.DOBLE_BOCA,
                ctx -> new SpriteEntityRenderer<>(ctx, "doble_boca", 2.1F, 0.451F));

        ClientPlayNetworking.registerGlobalReceiver(JumpscarePayload.ID,
                (payload, context) -> JumpscareClient.trigger(payload.type(), payload.mob()));
        ClientPlayNetworking.registerGlobalReceiver(HidePayload.ID,
                (payload, context) -> HideClient.set(payload.hidden()));
        ClientPlayNetworking.registerGlobalReceiver(DiarioPayload.ID,
                (payload, context) -> MinecraftClient.getInstance().setScreen(new DiarioScreen(payload.pages())));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            JumpscareClient.tick();
            HideClient.tick();
        });
        HudRenderCallback.EVENT.register(JumpscareClient::render);
        HudRenderCallback.EVENT.register(HideClient::render);
    }
}
