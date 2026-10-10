package com.rostroshuecos.entity;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * MANDIBULA CAIDA (agresiva). Abre su mandibula descolgada y SUCCIONA: cada 8 s, si estas entre
 * 3 y 12 bloques y te ve, te arrastra hacia ella con un rugido y te ralentiza.
 */
public class MandibulaEntity extends AcosadorEntity {
    private int pullCooldown = 80;

    public MandibulaEntity(EntityType<? extends HostileEntity> type, World world) {
        super(type, world);
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return baseAttributes(45.0, 0.31, 6.0);
    }

    @Override
    protected int chaseLimit() {
        return 900;
    }

    @Override
    public int mobIndex() {
        return 4;
    }

    @Override
    protected void abilityTick(ServerWorld w) {
        if (pullCooldown > 0) {
            pullCooldown--;
            return;
        }
        LivingEntity t = this.getTarget();
        if (t == null) return;
        double d2 = this.squaredDistanceTo(t);
        if (d2 > 9.0 && d2 < 144.0 && this.canSee(t)) {
            Vec3d dir = new Vec3d(this.getX() - t.getX(), 0.1, this.getZ() - t.getZ()).normalize().multiply(1.2);
            t.setVelocity(dir);
            if (t instanceof ServerPlayerEntity sp) {
                sp.networkHandler.sendPacket(new EntityVelocityUpdateS2CPacket(sp));
            }
            t.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 40, 1));
            w.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.ENTITY_RAVAGER_ROAR,
                    SoundCategory.HOSTILE, 2.0F, 0.6F);
            pullCooldown = 160;
        }
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.ENTITY_ZOMBIE_VILLAGER_AMBIENT;
    }
}
