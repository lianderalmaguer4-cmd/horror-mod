package com.rostroshuecos.client;

import com.rostroshuecos.RostrosHuecos;
import net.minecraft.client.render.entity.BipedEntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.render.entity.state.BipedEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.util.Identifier;

/** Renderer comun: modelo humanoide vanilla + textura propia + escala. */
public class HorrorBipedRenderer<T extends MobEntity>
        extends BipedEntityRenderer<T, BipedEntityRenderState, BipedEntityModel<BipedEntityRenderState>> {

    private final Identifier texture;
    private final float scale;

    public HorrorBipedRenderer(EntityRendererFactory.Context ctx, String textureName, float scale, float shadow) {
        super(ctx, new BipedEntityModel<>(ctx.getPart(EntityModelLayers.ZOMBIE)), shadow);
        this.texture = RostrosHuecos.id("textures/entity/" + textureName + ".png");
        this.scale = scale;
    }

    @Override
    public BipedEntityRenderState createRenderState() {
        return new BipedEntityRenderState();
    }

    @Override
    public Identifier getTexture(BipedEntityRenderState state) {
        return texture;
    }

    @Override
    protected void scale(BipedEntityRenderState state, MatrixStack matrices) {
        matrices.scale(scale, scale, scale);
    }
}
