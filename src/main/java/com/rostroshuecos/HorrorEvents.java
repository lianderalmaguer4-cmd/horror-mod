package com.rostroshuecos;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Vec3d;

/** Eventos ambientales: sonidos a tu espalda y sustos raros cuando estas a oscuras. */
public final class HorrorEvents {
    private HorrorEvents() {}

    private static final SoundEvent[] CREEPS = {
        SoundEvents.ENTITY_WARDEN_NEARBY_CLOSE,
        SoundEvents.ENTITY_WARDEN_HEARTBEAT,
        SoundEvents.BLOCK_WOODEN_DOOR_OPEN,
        SoundEvents.ENTITY_ENDERMAN_STARE,
        SoundEvents.ENTITY_GHAST_AMBIENT,
        SoundEvents.ENTITY_ZOMBIE_VILLAGER_AMBIENT
    };

    public static void register() {
        ServerTickEvents.END_WORLD_TICK.register(world -> {
            if (world.getTime() % 20 != 0) return;
            for (ServerPlayerEntity p : world.getPlayers()) {
                if (p.isCreative() || p.isSpectator()) continue;
                boolean dark = world.getLightLevel(p.getBlockPos()) <= 4;
                if (!dark) continue;
                // ~ cada 90 s a oscuras: un sonido detras de ti
                if (world.getRandom().nextInt(90) == 0) {
                    Vec3d pos = p.getRotationVec(1.0F).multiply(-6.0).add(p.getPos());
                    SoundEvent s = CREEPS[world.getRandom().nextInt(CREEPS.length)];
                    world.playSound(null, pos.x, pos.y, pos.z, s, SoundCategory.AMBIENT,
                            1.2F, 0.6F + world.getRandom().nextFloat() * 0.4F);
                }
                // ~ cada 15 min a oscuras: susto glitch
                if (world.getRandom().nextInt(900) == 0) {
                    Jumpscare.send(p, 1);
                }
            }
        });
    }
}
