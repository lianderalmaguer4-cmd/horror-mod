package com.rostroshuecos;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import com.rostroshuecos.entity.AcosadorEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Vec3d;

/** Sonidos ambientales de terror: a tu espalda, en cualquier direccion, y sustos raros a oscuras. */
public final class HorrorEvents {
    private HorrorEvents() {}

    private static final SoundEvent[] CREEPS = {
        SoundEvents.ENTITY_WARDEN_NEARBY_CLOSE,
        SoundEvents.ENTITY_WARDEN_HEARTBEAT,
        SoundEvents.BLOCK_WOODEN_DOOR_OPEN,
        SoundEvents.BLOCK_WOODEN_DOOR_CLOSE,
        SoundEvents.BLOCK_IRON_DOOR_CLOSE,
        SoundEvents.BLOCK_CHEST_CLOSE,
        SoundEvents.BLOCK_WOODEN_TRAPDOOR_CLOSE,
        SoundEvents.ENTITY_ENDERMAN_STARE,
        SoundEvents.ENTITY_ENDERMAN_AMBIENT,
        SoundEvents.ENTITY_GHAST_AMBIENT,
        SoundEvents.ENTITY_ZOMBIE_VILLAGER_AMBIENT,
        SoundEvents.ENTITY_PHANTOM_AMBIENT,
        SoundEvents.ENTITY_WITCH_AMBIENT,
        SoundEvents.ENTITY_VEX_AMBIENT,
        SoundEvents.ENTITY_CAT_HISS,
        SoundEvents.ENTITY_WOLF_GROWL,
        SoundEvents.BLOCK_BELL_USE
    };

    /** La mitad de las veces suena el ambiente propio de un monstruo; si no, un sonido vanilla inquietante. */
    private static SoundEvent pick(net.minecraft.util.math.random.Random r) {
        if (r.nextBoolean()) return ModSounds.AMBIENT[r.nextInt(ModSounds.AMBIENT.length - 1)];
        return CREEPS[r.nextInt(CREEPS.length)];
    }

    public static void register() {
        ServerTickEvents.END_WORLD_TICK.register(world -> {
            if (world.getTime() % 20 != 0) return;
            long t = world.getTimeOfDay() % 24000L;
            boolean night = t >= 13000 && t <= 23000;
            for (ServerPlayerEntity p : world.getPlayers()) {
                if (p.isSpectator()) continue;
                Diary.sweepNotes(p);   // las Notas Rotas se guardan solas en el diario

                // la Vela de Sal se gasta mientras haya monstruos cerca
                ItemStack candle = p.getMainHandStack().isOf(ModItems.VELA) ? p.getMainHandStack()
                        : (p.getOffHandStack().isOf(ModItems.VELA) ? p.getOffHandStack() : ItemStack.EMPTY);
                if (!candle.isEmpty() && !world.getEntitiesByClass(MobEntity.class, p.getBoundingBox().expand(8.0),
                        e -> e instanceof AcosadorEntity).isEmpty()) {
                    candle.setDamage(candle.getDamage() + 1);
                    world.spawnParticles(ParticleTypes.FLAME, p.getX(), p.getY() + 1.2, p.getZ(), 4, 0.2, 0.1, 0.2, 0.01);
                    if (candle.getDamage() >= candle.getMaxDamage()) {
                        candle.decrement(1);
                        p.sendMessage(Text.literal("La vela se consumió."), true);
                    }
                }
                boolean dark = world.getLightLevel(p.getBlockPos()) <= 4;
                boolean nightOutside = night && world.isSkyVisible(p.getBlockPos());
                int den = dark ? 45 : (nightOutside ? 70 : 0);
                if (HorrorUtil.isModDim(world) && den > 0) den = Math.max(15, den / 2);   // la Pradera y la Ceniza suenan mas
                if (den == 0) continue;

                // sonido raro: a tu espalda (60%) o en una direccion cualquiera
                if (world.getRandom().nextInt(den) == 0) {
                    Vec3d pos;
                    if (world.getRandom().nextInt(10) < 6) {
                        pos = p.getRotationVec(1.0F).multiply(-6.0).add(p.getPos());
                    } else {
                        double ang = world.getRandom().nextDouble() * Math.PI * 2;
                        pos = p.getPos().add(Math.cos(ang) * 10, 0, Math.sin(ang) * 10);
                    }
                    SoundEvent s = pick(world.getRandom());
                    world.playSound(null, pos.x, pos.y, pos.z, s, SoundCategory.AMBIENT,
                            1.3F, 0.5F + world.getRandom().nextFloat() * 0.5F);
                }
                // susto glitch muy raro a oscuras (~25 min); solo caras agresivas
                if (dark && world.getRandom().nextInt(1500) == 0) {
                    Jumpscare.send(p, 1);
                }
            }
        });
    }
}
