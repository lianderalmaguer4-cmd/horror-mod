package com.rostroshuecos.entity;

import com.rostroshuecos.HorrorUtil;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.world.World;

/**
 * DOBLE BOCA (agresiva). Sonrie y camina erguida: se QUEDA CONGELADA mientras la miras y avanza
 * rapido cuando le das la espalda. Ademas, repite en el chat todo lo que escribes (ver StalkerDirector).
 */
public class DobleBocaEntity extends AcosadorEntity {
    public DobleBocaEntity(EntityType<? extends HostileEntity> type, World world) {
        super(type, world);
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return baseAttributes(50.0, 0.37, 6.0);
    }

    @Override
    protected int chaseLimit() {
        return 1000;
    }

    @Override
    public int mobIndex() {
        return 5;
    }

    @Override
    protected void abilityTick(ServerWorld w) {
        PlayerEntity near = w.getClosestPlayer(this, 40.0);
        boolean seen = near != null && !near.isCreative() && !near.isSpectator()
                && HorrorUtil.isLookingAt(near, this);
        if (this.isAiDisabled() != seen) {
            this.setAiDisabled(seen);
        }
    }

}
