package com.rostroshuecos;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;

public final class HorrorUtil {
    private HorrorUtil() {}

    public static final String TAG_ESPIA = "espia";
    public static final String TAG_ESPIA_CAMA = "espia_cama";

    /** Mismo calculo que el Enderman: true si el jugador mira directamente a la entidad. */
    public static boolean isLookingAt(PlayerEntity p, Entity e) {
        Vec3d look = p.getRotationVec(1.0F).normalize();
        Vec3d to = new Vec3d(e.getX() - p.getX(), e.getEyeY() - p.getEyeY(), e.getZ() - p.getZ());
        double d = to.length();
        if (d < 0.001) return true;
        return look.dotProduct(to.normalize()) > 1.0 - 0.025 / d && p.canSee(e);
    }

    /** Un "espia" es un monstruo quieto que solo te observa (lo controla StalkerDirector). */
    public static boolean isSpy(Entity e) {
        return e.getCommandTags().contains(TAG_ESPIA) || e.getCommandTags().contains(TAG_ESPIA_CAMA);
    }
}
