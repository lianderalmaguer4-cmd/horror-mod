package com.rostroshuecos;

import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;

/** Sonidos propios (assets/rostroshuecos/sounds/*.ogg). El indice coincide con AcosadorEntity.mobIndex(). */
public final class ModSounds {
    private ModSounds() {}

    public static final String[] IDS = {"sombra", "raiz", "sudario", "hueco", "mandibula", "doble_boca"};
    public static final SoundEvent[] SCARE = new SoundEvent[IDS.length];
    public static final SoundEvent[] AMBIENT = new SoundEvent[IDS.length];
    public static final SoundEvent BREATH = reg("breath");
    public static final SoundEvent KNOCK = reg("knock");
    public static final SoundEvent GLASS_KNOCK = reg("glass_knock");
    public static final SoundEvent FINAL_START = reg("final.start");
    public static final SoundEvent FINAL_END = reg("final.end");

    static {
        for (int i = 0; i < IDS.length; i++) {
            SCARE[i] = reg("scare." + IDS[i]);
            AMBIENT[i] = reg("ambient." + IDS[i]);
        }
    }

    private static SoundEvent reg(String path) {
        Identifier id = RostrosHuecos.id(path);
        return Registry.register(Registries.SOUND_EVENT, id, SoundEvent.of(id));
    }

    /** Fuerza la carga de la clase (y el registro) al iniciar el mod. */
    public static void init() {}
}
