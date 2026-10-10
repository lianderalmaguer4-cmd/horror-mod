package com.rostroshuecos.entity;

import java.util.EnumSet;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ai.goal.EscapeDangerGoal;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.ai.goal.SwimGoal;
import net.minecraft.entity.ai.goal.WanderAroundFarGoal;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.world.World;

/** PACIFICA. Te sigue a distancia sin hacer nada, siempre mirandote. Inofensivo... pero inquietante. */
public class MunecoPerdidoEntity extends PathAwareEntity {
    public MunecoPerdidoEntity(EntityType<? extends PathAwareEntity> type, World world) {
        super(type, world);
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return MobEntity.createMobAttributes()
                .add(EntityAttributes.MAX_HEALTH, 8.0)
                .add(EntityAttributes.MOVEMENT_SPEED, 0.25);
    }

    @Override
    protected void initGoals() {
        this.goalSelector.add(0, new SwimGoal(this));
        this.goalSelector.add(1, new EscapeDangerGoal(this, 1.5));
        this.goalSelector.add(3, new FollowPlayerGoal(this));
        this.goalSelector.add(5, new WanderAroundFarGoal(this, 0.6));
        this.goalSelector.add(6, new LookAtEntityGoal(this, PlayerEntity.class, 24.0F));
        this.goalSelector.add(7, new LookAroundGoal(this));
    }

    @Override
    protected ActionResult interactMob(PlayerEntity player, Hand hand) {
        if (!this.getWorld().isClient) {
            player.sendMessage(Text.literal("<Muñeco Perdido> ").formatted(Formatting.DARK_GRAY)
                    .append(Text.literal("…quédate…").formatted(Formatting.RED, Formatting.ITALIC)), false);
            this.playSound(SoundEvents.ENTITY_VEX_AMBIENT, 1.0F, 0.5F);
        }
        return ActionResult.SUCCESS;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.ENTITY_VEX_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.ENTITY_VEX_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.ENTITY_VEX_DEATH;
    }

    /** Se acerca al jugador mas cercano hasta unos 4 bloques y se queda ahi. */
    static class FollowPlayerGoal extends Goal {
        private final MunecoPerdidoEntity mob;
        private PlayerEntity target;

        FollowPlayerGoal(MunecoPerdidoEntity mob) {
            this.mob = mob;
            this.setControls(EnumSet.of(Goal.Control.MOVE));
        }

        @Override
        public boolean canStart() {
            target = mob.getWorld().getClosestPlayer(mob, 14.0);
            return target != null && !target.isSpectator() && mob.squaredDistanceTo(target) > 16.0;
        }

        @Override
        public boolean shouldContinue() {
            return target != null && target.isAlive() && mob.squaredDistanceTo(target) > 9.0;
        }

        @Override
        public void start() {
            mob.getNavigation().startMovingTo(target, 1.0);
        }

        @Override
        public void stop() {
            mob.getNavigation().stop();
            target = null;
        }

        @Override
        public void tick() {
            if (mob.age % 10 == 0 && target != null) {
                mob.getNavigation().startMovingTo(target, 1.0);
            }
        }
    }
}
