package com.rostroshuecos.entity;

import com.rostroshuecos.FinalEvent;
import com.rostroshuecos.HorrorUtil;
import com.rostroshuecos.Jumpscare;
import com.rostroshuecos.Quest;
import com.rostroshuecos.ModSounds;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.ActiveTargetGoal;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.entity.ai.goal.RevengeGoal;
import net.minecraft.entity.ai.goal.SwimGoal;
import net.minecraft.entity.ai.goal.WanderAroundFarGoal;
import net.minecraft.entity.ai.pathing.MobNavigation;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Base de todos los monstruos: inmortales, solo una de cada tipo, persecucion limitada (despues desaparecen
 * y dejan caer su fragmento), susto al alcanzar a su objetivo, abren puertas, pierden a quien se agacha lejos
 * y respetan la Vela de Sal y el Sello. Cada hijo aporta una habilidad propia.
 */
public abstract class AcosadorEntity extends HostileEntity {
    private int chaseTicks = 0;
    private int idleTicks = 0;
    protected int scareCooldown = 0;

    protected AcosadorEntity(EntityType<? extends HostileEntity> type, World world) {
        super(type, world);
        this.setInvulnerable(true);
        if (this.getNavigation() instanceof MobNavigation nav) {
            nav.setCanPathThroughDoors(true);
        }
    }

    /** Ticks de persecucion antes de desaparecer (20 = 1 s). */
    protected abstract int chaseLimit();

    /** Indice de la cara del susto (ver JumpscareClient). */
    public abstract int mobIndex();

    /** Habilidad unica; se llama en el servidor cada tick (no mientras es "espia"). */
    protected void abilityTick(ServerWorld world) {}

    /** El Hueco vuela y no puede abrir puertas. */
    protected boolean canOpenDoors() {
        return true;
    }

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

        boolean chained = HorrorUtil.isChained(this);   // invocado por un ritual
        // un monstruo vencido con su ritual no vuelve a aparecer jamas
        if (!chained && HorrorUtil.isDefeatedEntity(this)) {
            this.discard();
            return;
        }
        // fase de acecho: hasta el despertar solo te observan (los maneja StalkerDirector como "espias")
        if (!chained && !FinalEvent.active) {
            PlayerEntity np = sw.getClosestPlayer(this, 80.0);
            if (np instanceof ServerPlayerEntity sp0 && !Quest.aggressive(sp0)) {
                this.setTarget(null);
                this.addCommandTag(HorrorUtil.TAG_ESPIA);
                this.setAiDisabled(true);
                return;
            }
        }

        if (scareCooldown > 0) scareCooldown--;

        // la Vela de Sal y el Sello los ahuyentan: pierden tu rastro a menos de 8 bloques
        if (this.age % 5 == 0) {
            PlayerEntity near = sw.getClosestPlayer(this, 8.0);
            if (near != null && HorrorUtil.holdsRepeller(near) && !FinalEvent.active && !chained) {
                this.setTarget(null);
            }
        }

        LivingEntity target = this.getTarget();

        // sigilo: si te agachas y estas lejos, te pierden
        if (!FinalEvent.active && !chained && target instanceof PlayerEntity pl && pl.isSneaking()
                && this.squaredDistanceTo(pl) > 100.0 && !this.canSee(pl)) {
            this.setTarget(null);
            target = null;
        }

        // la persecucion dura un rato; si sobrevives, deja caer su fragmento y desaparece
        if (target != null) {
            idleTicks = 0;
            if (++chaseTicks > chaseLimit() * (HorrorUtil.isAsh(sw) ? 2 : 1) && !FinalEvent.active && !chained) {
                HorrorUtil.poof(this);
                return;
            }
        } else if (++idleTicks > 3600 && !FinalEvent.active && !chained) {
            HorrorUtil.poof(this);
            return;
        }

        // abre las puertas de madera que le cierran el paso
        if (target != null && canOpenDoors() && this.age % 8 == 0) {
            openDoorAhead(sw);
        }

        // susto al alcanzar a su objetivo
        if (target instanceof ServerPlayerEntity sp && scareCooldown == 0 && this.squaredDistanceTo(sp) < 6.25) {
            Jumpscare.send(sp, 0, mobIndex());
            scareCooldown = 600;
        }

        // la habilidad propia no funciona si te protege la Vela o el Sello
        PlayerEntity close = sw.getClosestPlayer(this, 8.0);
        if (close != null && HorrorUtil.holdsRepeller(close) && !FinalEvent.active && !chained) return;
        abilityTick(sw);
    }

    /** Abre (sin romper) la puerta de madera que tiene justo delante, a la altura de sus pies o de su cabeza. */
    private void openDoorAhead(ServerWorld w) {
        BlockPos front = this.getBlockPos().offset(this.getHorizontalFacing());
        for (int i = 0; i <= 1; i++) {
            BlockPos pos = front.up(i);
            BlockState st = w.getBlockState(pos);
            if (!(st.getBlock() instanceof DoorBlock) || !st.isIn(BlockTags.WOODEN_DOORS) || st.isOf(Blocks.CRIMSON_DOOR)) continue;
            BlockPos low = (st.get(DoorBlock.HALF) == DoubleBlockHalf.UPPER) ? pos.down() : pos;
            BlockState ls = w.getBlockState(low);
            if (!(ls.getBlock() instanceof DoorBlock) || ls.get(DoorBlock.OPEN)) return;
            w.setBlockState(low, ls.with(DoorBlock.OPEN, true), Block.NOTIFY_ALL);
            BlockPos up = low.up();
            BlockState us = w.getBlockState(up);
            if (us.getBlock() instanceof DoorBlock) w.setBlockState(up, us.with(DoorBlock.OPEN, true), Block.NOTIFY_ALL);
            w.playSound(null, low, net.minecraft.sound.SoundEvents.BLOCK_WOODEN_DOOR_OPEN, SoundCategory.BLOCKS, 1.2F, 0.6F);
            return;
        }
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
    protected SoundEvent getAmbientSound() {
        return ModSounds.AMBIENT[mobIndex()];
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
