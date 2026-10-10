package com.rostroshuecos.client;

import com.rostroshuecos.RostrosHuecos;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;

/** Dibuja al monstruo como una imagen PNG plana que siempre gira para mirarte (solo en horizontal). */
public class SpriteEntityRenderer<T extends MobEntity> extends EntityRenderer<T, EntityRenderState> {
    private final Identifier texture;
    private final float height;
    private final float width;

    public SpriteEntityRenderer(EntityRendererFactory.Context ctx, String name, float height, float aspect) {
        super(ctx);
        this.texture = RostrosHuecos.id("textures/entity/" + name + "_sprite.png");
        this.height = height;
        this.width = height * aspect;
    }

    @Override
    public EntityRenderState createRenderState() {
        return new EntityRenderState();
    }

    @Override
    public void render(EntityRenderState state, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light) {
        Camera camera = MinecraftClient.getInstance().gameRenderer.getCamera();

        // luz minima para que se vean incluso en la oscuridad total
        int block = Math.max(LightmapTextureManager.getBlockLightCoordinates(light), 4);
        int sky = LightmapTextureManager.getSkyLightCoordinates(light);
        int lit = LightmapTextureManager.pack(block, sky);

        matrices.push();
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180.0F - camera.getYaw()));

        VertexConsumer vc = vertexConsumers.getBuffer(RenderLayer.getEntityCutoutNoCull(texture));
        MatrixStack.Entry entry = matrices.peek();
        float hw = width / 2.0F;
        vertex(vc, entry, -hw, 0.0F, 0.0F, 1.0F, lit);
        vertex(vc, entry, hw, 0.0F, 1.0F, 1.0F, lit);
        vertex(vc, entry, hw, height, 1.0F, 0.0F, lit);
        vertex(vc, entry, -hw, height, 0.0F, 0.0F, lit);
        matrices.pop();

        super.render(state, matrices, vertexConsumers, light);
    }

    private static void vertex(VertexConsumer vc, MatrixStack.Entry entry, float x, float y, float u, float v, int light) {
        vc.vertex(entry, x, y, 0.0F)
                .color(255, 255, 255, 255)
                .texture(u, v)
                .overlay(OverlayTexture.DEFAULT_UV)
                .light(light)
                .normal(entry, 0.0F, 0.0F, 1.0F);
    }
}
