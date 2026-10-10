package com.rostroshuecos.entity;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * EL SUDARIO (agresivo). Una figura envuelta en oscuridad que APAGA LA LUZ: rompe antorchas y
 * linternas cercanas y llena de Oscuridad a los jugadores que tiene a menos de 12 bloques.
 */
public class SudarioEntity extends AcosadorEntity {
    public SudarioEntity(EntityType<? extends HostileEntity> type, World world) {
        super(type, world);
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return baseAttributes(40.0, 0.30, 6.0);
    }

    @Override
    protected int chaseLimit() {
        return 900;
    }

    @Override
    public int mobIndex() {
        return 2;
    }

    private static boolean isLight(BlockState s) {
        return s.isOf(Blocks.TORCH) || s.isOf(Blocks.WALL_TORCH)
                || s.isOf(Blocks.SOUL_TORCH) || s.isOf(Blocks.SOUL_WALL_TORCH)
                || s.isOf(Blocks.LANTERN) || s.isOf(Blocks.SOUL_LANTERN);
    }

    @Override
    protected void abilityTick(ServerWorld w) {
        if (this.age % 20 == 0) {
            BlockPos c = this.getBlockPos();
            for (BlockPos q : BlockPos.iterate(c.add(-6, -3, -6), c.add(6, 3, 6))) {
                if (isLight(w.getBlockState(q))) {
                    BlockPos pos = q.toImmutable();
                    w.breakBlock(pos, true);
                    w.playSound(null, pos, SoundEvents.BLOCK_FIRE_EXTINGUISH, SoundCategory.BLOCKS, 1.0F, 0.8F);
                    break;   // una por segundo
                }
            }
        }
        if (this.age % 40 == 0) {
            for (ServerPlayerEntity p : w.getPlayers()) {
                if (!p.isSpectator() && this.squaredDistanceTo(p) < 144.0) {
                    p.addStatusEffect(new StatusEffectInstance(StatusEffects.DARKNESS, 100, 0, false, false));
                }
            }
        }
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.ENTITY_GHAST_AMBIENT;
    }
}
