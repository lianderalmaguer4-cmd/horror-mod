package com.rostroshuecos.entity;

import com.rostroshuecos.Lore;
import com.rostroshuecos.ModItems;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ai.goal.EscapeDangerGoal;
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
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.world.World;

/** PACIFICA. Te observa en silencio. Si le haces clic derecho te susurra un fragmento de lore, te da una nota y desaparece. */
public class NinoGrisEntity extends PathAwareEntity {
    public NinoGrisEntity(EntityType<? extends PathAwareEntity> type, World world) {
        super(type, world);
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return MobEntity.createMobAttributes()
                .add(EntityAttributes.MAX_HEALTH, 12.0)
                .add(EntityAttributes.MOVEMENT_SPEED, 0.22);
    }

    @Override
    protected void initGoals() {
        this.goalSelector.add(0, new SwimGoal(this));
        this.goalSelector.add(1, new EscapeDangerGoal(this, 1.6));
        this.goalSelector.add(5, new WanderAroundFarGoal(this, 0.5));
        this.goalSelector.add(6, new LookAtEntityGoal(this, PlayerEntity.class, 24.0F));
        this.goalSelector.add(7, new LookAroundGoal(this));
    }

    @Override
    protected ActionResult interactMob(PlayerEntity player, Hand hand) {
        if (this.getWorld() instanceof ServerWorld sw) {
            int page = this.random.nextInt(Lore.PAGES.length);
            player.sendMessage(Text.literal("<Niño Gris> ").formatted(Formatting.DARK_GRAY)
                    .append(Text.literal(Lore.PAGES[page]).formatted(Formatting.GRAY, Formatting.ITALIC)), false);
            player.giveItemStack(new ItemStack(ModItems.NOTA_ROTA));
            sw.spawnParticles(ParticleTypes.SMOKE, this.getX(), this.getY() + 0.8, this.getZ(), 25, 0.3, 0.5, 0.3, 0.02);
            this.playSound(SoundEvents.ENTITY_VEX_DEATH, 1.0F, 0.5F);
            this.discard();
        }
        return ActionResult.SUCCESS;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.ENTITY_VEX_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.ENTITY_VEX_DEATH;
    }
}
