package com.rostroshuecos;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.world.World;

/** Normalmente se guarda sola en el diario; si la usas, tambien. */
public class NotaRotaItem extends Item {
    public NotaRotaItem(Settings settings) {
        super(settings);
    }

    @Override
    public ActionResult use(World world, PlayerEntity user, Hand hand) {
        if (!world.isClient && user instanceof ServerPlayerEntity sp) {
            user.getStackInHand(hand).decrement(1);
            Diary.collectNext(sp);
        }
        return ActionResult.SUCCESS;
    }
}
