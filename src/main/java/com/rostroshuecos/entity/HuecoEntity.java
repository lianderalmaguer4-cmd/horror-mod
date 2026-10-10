package com.rostroshuecos.entity;

import com.rostroshuecos.HorrorUtil;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.control.FlightMoveControl;
import net.minecraft.entity.ai.pathing.BirdNavigation;
import net.minecraft.entity.ai.pathing.EntityNavigation;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.world.World;

/**
 * EL HUECO (agresivo, VUELA). Un busto flotante sin cuerpo con ojos enormes que no parpadean:
 * si lo miras a menos de 14 bloques te provoca Nausea (la mirada vacia te marea).
 */
public class HuecoEntity extends AcosadorEntity {
    private int stareCooldown = 0;

    public HuecoEntity(EntityType<? extends HostileEntity> type, World world) {
        super(type, world);
        this.moveControl = new FlightMoveControl(this, 20, true);
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return baseAttributes(50.0, 0.30, 7.0).add(EntityAttributes.FLYING_SPEED, 0.5);
    }

    @Override
    protected EntityNavigation createNavigation(World world) {
        return new BirdNavigation(this, world);
    }

    @Override
    protected int chaseLimit() {
        return 900;
    }

    @Override
    public int mobIndex() {
        return 3;
    }

    @Override
    protected void abilityTick(ServerWorld w) {
        if (stareCooldown > 0) {
            stareCooldown--;
            return;
        }
        LivingEntity t = this.getTarget();
        if (t instanceof ServerPlayerEntity sp && this.squaredDistanceTo(sp) < 196.0
                && HorrorUtil.isLookingAt(sp, this)) {
            sp.addStatusEffect(new StatusEffectInstance(StatusEffects.NAUSEA, 140));
            w.playSound(null, sp.getX(), sp.getY(), sp.getZ(), SoundEvents.ENTITY_WARDEN_HEARTBEAT,
                    SoundCategory.HOSTILE, 1.5F, 0.8F);
            stareCooldown = 120;
        }
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.ENTITY_VEX_AMBIENT;
    }
}
