package com.rostroshuecos.entity;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.world.World;

/**
 * LA RAIZ (agresiva, lenta). Sus brazos son ramas y su falda son raices: aparece en bosques y,
 * si te alcanza la vista a menos de 7 bloques, te ENREDA con raices (lentitud extrema).
 */
public class RaizEntity extends AcosadorEntity {
    private int rootCooldown = 100;

    public RaizEntity(EntityType<? extends HostileEntity> type, World world) {
        super(type, world);
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return baseAttributes(60.0, 0.21, 7.0);
    }

    @Override
    protected int chaseLimit() {
        return 1100;   // es lenta: persigue mas tiempo
    }

    @Override
    public int mobIndex() {
        return 1;
    }

    @Override
    protected void abilityTick(ServerWorld w) {
        if (rootCooldown > 0) {
            rootCooldown--;
            return;
        }
        LivingEntity t = this.getTarget();
        if (t != null && this.squaredDistanceTo(t) < 49.0 && this.canSee(t)) {
            t.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 70, 3));
            w.spawnParticles(ParticleTypes.HAPPY_VILLAGER, t.getX(), t.getY() + 0.2, t.getZ(),
                    25, 0.4, 0.1, 0.4, 0.02);
            w.playSound(null, t.getX(), t.getY(), t.getZ(), SoundEvents.BLOCK_WOOD_BREAK,
                    SoundCategory.HOSTILE, 1.5F, 0.5F);
            rootCooldown = 200;
        }
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.ENTITY_PHANTOM_AMBIENT;
    }
}
