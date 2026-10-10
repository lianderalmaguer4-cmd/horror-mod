package com.rostroshuecos;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.world.World;

public class NotaRotaItem extends Item {
    public NotaRotaItem(Settings settings) {
        super(settings);
    }

    @Override
    public ActionResult use(World world, PlayerEntity user, Hand hand) {
        if (!world.isClient) {
            int page = world.getRandom().nextInt(Lore.PAGES.length);
            user.sendMessage(Text.literal("— Nota rota · página " + (page + 1) + " —")
                    .formatted(Formatting.DARK_RED, Formatting.ITALIC), false);
            user.sendMessage(Text.literal(Lore.PAGES[page]).formatted(Formatting.GRAY), false);
            world.playSound(null, user.getX(), user.getY(), user.getZ(),
                    SoundEvents.ITEM_BOOK_PAGE_TURN, SoundCategory.PLAYERS, 1.0F, 0.6F);
            // 1 de cada 8 lecturas: susto
            if (world.getRandom().nextInt(8) == 0 && user instanceof ServerPlayerEntity sp) {
                Jumpscare.send(sp, 1);
            }
        }
        return ActionResult.SUCCESS;
    }
}
