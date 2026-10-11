package com.rostroshuecos;

import com.rostroshuecos.entity.AcosadorEntity;
import com.rostroshuecos.entity.SombraHuecaEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.TypeFilter;
import net.minecraft.util.math.Vec3d;

public final class HorrorUtil {
    private HorrorUtil() {}

    public static final String TAG_ESPIA = "espia";
    public static final String TAG_ESPIA_CAMA = "espia_cama";
    public static final String TAG_PUERTA = "visita_puerta";
    public static final String TAG_VENTANA = "visita_ventana";
    public static final String TAG_BAJO_CAMA = "visita_cama";

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
        return e.getCommandTags().contains(TAG_ESPIA)
                || e.getCommandTags().contains(TAG_ESPIA_CAMA)
                || e.getCommandTags().contains(TAG_PUERTA)
                || e.getCommandTags().contains(TAG_VENTANA)
                || e.getCommandTags().contains(TAG_BAJO_CAMA);
    }

    /** Los 5 monstruos agresivos. La Sombra Hueca es neutral (no ataca a menos que la mires). */
    public static boolean isAggressive(Entity e) {
        return e instanceof AcosadorEntity && !(e instanceof SombraHuecaEntity);
    }

    /** Desaparece con humo y sonido. */
    public static void poof(MobEntity m) {
        if (m.getWorld() instanceof ServerWorld w) {
            w.spawnParticles(ParticleTypes.LARGE_SMOKE, m.getX(), m.getY() + m.getHeight() / 2, m.getZ(),
                    25, 0.3, 0.6, 0.3, 0.02);
            w.playSound(null, m.getX(), m.getY(), m.getZ(),
                    SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.HOSTILE, 1.0F, 0.5F);
        }
        m.discard();
    }

    /** ¿Ya existe en el mundo otra entidad de este mismo tipo? (solo puede haber una de cada) */
    @SuppressWarnings("unchecked")
    public static boolean hasDuplicate(MobEntity self) {
        if (!(self.getWorld() instanceof ServerWorld sw)) return false;
        Class<MobEntity> cls = (Class<MobEntity>) self.getClass();
        return !sw.getEntitiesByType(TypeFilter.instanceOf(cls),
                e -> e != self && e.isAlive() && !e.isRemoved()).isEmpty();
    }

    public static boolean exists(ServerWorld w, EntityType<? extends MobEntity> type) {
        return !w.getEntitiesByType(type, e -> e.isAlive()).isEmpty();
    }
}
