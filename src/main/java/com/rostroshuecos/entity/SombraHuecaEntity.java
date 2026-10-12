package com.rostroshuecos.entity;

import com.rostroshuecos.HorrorUtil;
import com.rostroshuecos.Jumpscare;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.entity.ai.goal.RevengeGoal;
import net.minecraft.entity.ai.goal.SwimGoal;
import net.minecraft.entity.ai.goal.WanderAroundFarGoal;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.world.World;

/** NEUTRAL. Ignora al jugador hasta que lo mira directamente (o la golpea). Entonces lo persigue y lo ciega. */
public class SombraHuecaEntity extends AcosadorEntity {
    public SombraHuecaEntity(EntityType<? extends HostileEntity> type, World world) {
        super(type, world);
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return baseAttributes(30.0, 0.38, 8.0);
    }

    @Override
    protected int chaseLimit() {
        return 700;
    }

    @Override
    public int mobIndex() {
        return 0;
    }

    @Override
    protected void initGoals() {
        this.goalSelector.add(0, new SwimGoal(this));
        this.goalSelector.add(2, new MeleeAttackGoal(this, 1.0, false));
        this.goalSelector.add(7, new WanderAroundFarGoal(this, 0.6));
        this.goalSelector.add(8, new LookAroundGoal(this));
        this.targetSelector.add(1, new RevengeGoal(this));
        // sin ActiveTargetGoal: solo se enfada si la miras (ver abilityTick)
    }

    @Override
    protected void abilityTick(ServerWorld w) {
        if (this.getTarget() == null && this.age % 5 == 0) {
            PlayerEntity p = w.getClosestPlayer(this, 24.0);
            if (p != null && !p.isCreative() && !p.isSpectator() && HorrorUtil.isLookingAt(p, this)) {
                this.setTarget(p);
                this.playSound(SoundEvents.ENTITY_ENDERMAN_STARE, 1.5F, 0.5F);
                if (p instanceof ServerPlayerEntity sp && scareCooldown == 0) {
                    Jumpscare.send(sp, 1, mobIndex());
                    scareCooldown = 1200;
                }
            }
        }
        LivingEntity t = this.getTarget();
        if (t != null && this.squaredDistanceTo(t) < 9.0 && this.age % 40 == 0) {
            t.addStatusEffect(new StatusEffectInstance(StatusEffects.DARKNESS, 120));
        }
    }
}
