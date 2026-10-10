package com.rostroshuecos.entity;

import com.rostroshuecos.HorrorUtil;
import com.rostroshuecos.Jumpscare;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.ActiveTargetGoal;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.entity.ai.goal.RevengeGoal;
import net.minecraft.entity.ai.goal.SwimGoal;
import net.minecraft.entity.ai.goal.WanderAroundFarGoal;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.world.World;

/**
 * Base de todos los monstruos: inmortales, solo una de cada tipo, persecucion limitada
 * (despues desaparecen) y susto al alcanzar a su objetivo. Cada hijo aporta una habilidad propia.
 */
public abstract class AcosadorEntity extends HostileEntity {
    private int chaseTicks = 0;
    private int idleTicks = 0;
    protected int scareCooldown = 0;

    protected AcosadorEntity(EntityType<? extends HostileEntity> type, World world) {
        super(type, world);
        this.setInvulnerable(true);
    }

    /** Ticks de persecucion antes de desaparecer (20 = 1 s). */
    protected abstract int chaseLimit();

    /** Indice de la cara del susto (ver JumpscareClient). */
    public abstract int mobIndex();

    /** Habilidad unica; se llama en el servidor cada tick (no mientras es "espia"). */
    protected void abilityTick(ServerWorld world) {}

    public static DefaultAttributeContainer.Builder baseAttributes(double health, double speed, double damage) {
        return HostileEntity.createHostileAttributes()
                .add(EntityAttributes.MAX_HEALTH, health)
                .add(EntityAttributes.MOVEMENT_SPEED, speed)
                .add(EntityAttributes.ATTACK_DAMAGE, damage)
                .add(EntityAttributes.FOLLOW_RANGE, 40.0);
    }

    @Override
    protected void initGoals() {
        this.goalSelector.add(0, new SwimGoal(this));
        this.goalSelector.add(2, new MeleeAttackGoal(this, 1.1, false));
        this.goalSelector.add(7, new WanderAroundFarGoal(this, 0.8));
        this.goalSelector.add(8, new LookAtEntityGoal(this, PlayerEntity.class, 16.0F));
        this.goalSelector.add(8, new LookAroundGoal(this));
        this.targetSelector.add(1, new RevengeGoal(this));
        this.targetSelector.add(2, new ActiveTargetGoal<>(this, PlayerEntity.class, true));
    }

    @Override
    public void tick() {
        super.tick();
        if (!(this.getWorld() instanceof ServerWorld sw)) return;

        // solo puede haber una de cada tipo
        if (this.age == 2 && HorrorUtil.hasDuplicate(this)) {
            this.discard();
            return;
        }
        if (HorrorUtil.isSpy(this)) return;

        if (scareCooldown > 0) scareCooldown--;

        // la persecucion dura un rato y luego desaparece
        if (this.getTarget() != null) {
            idleTicks = 0;
            if (++chaseTicks > chaseLimit()) {
                HorrorUtil.poof(this);
                return;
            }
        } else if (++idleTicks > 3600) {
            HorrorUtil.poof(this);
            return;
        }

        // susto al alcanzar a su objetivo
        LivingEntity target = this.getTarget();
        if (target instanceof ServerPlayerEntity sp && scareCooldown == 0 && this.squaredDistanceTo(sp) < 6.25) {
            Jumpscare.send(sp, 0, mobIndex());
            scareCooldown = 600;
        }

        abilityTick(sw);
    }

    /** Inmortal: ningun golpe les hace dano (solo /kill las elimina). */
    public boolean damage(ServerWorld world, DamageSource source, float amount) {
        if (source.isIn(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            this.discard();
            return true;
        }
        return false;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.ENTITY_ENDERMAN_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.ENTITY_ENDERMAN_DEATH;
    }
}
