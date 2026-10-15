package com.rostroshuecos.entity;

import com.rostroshuecos.FinalEvent;
import com.rostroshuecos.HorrorUtil;
import com.rostroshuecos.Lore;
import com.rostroshuecos.SanMudo;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;

/**
 * SAN MUDO, ya corrompido. Persigue sin descanso mientras susurra: pide ayuda y que acabes con su sufrimiento.
 * Solo aparece en el final. Una Vela de Sal o el Sello en la mano lo ralentizan si esta cerca.
 */
public class SanMudoEntity extends AcosadorEntity {
    public SanMudoEntity(EntityType<? extends HostileEntity> type, World world) {
        super(type, world);
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return baseAttributes(300.0, 0.27, 9.0);
    }

    @Override
    protected int chaseLimit() {
        return Integer.MAX_VALUE / 4;   // no se rinde: el final lo gestiona FinalEvent
    }

    @Override
    public int mobIndex() {
        return 6;
    }

    @Override
    protected void abilityTick(ServerWorld w) {
        PlayerEntity near = w.getClosestPlayer(this, 40.0);
        if (!(near instanceof ServerPlayerEntity sp)) return;
        // susurros suplicantes mientras te persigue
        if (this.age % 160 == 80) {
            SanMudo.say(sp, Lore.CHASE[w.getRandom().nextInt(Lore.CHASE.length)]);
        }
        // la vela o el Sello lo frenan
        if (this.squaredDistanceTo(sp) < 36.0 && HorrorUtil.holdsRepeller(sp) && this.age % 20 == 0) {
            this.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 50, 2, false, false));
        }
        // su cercania marea
        if (this.squaredDistanceTo(sp) < 100.0 && this.age % 60 == 0) {
            sp.addStatusEffect(new StatusEffectInstance(StatusEffects.NAUSEA, 120, 0, false, false));
        }
        if (!FinalEvent.active) this.discard();   // por seguridad: fuera del final no existe
    }
}
