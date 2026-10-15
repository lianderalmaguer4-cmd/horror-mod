package com.rostroshuecos;

import java.util.ArrayList;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Estado de la historia por jugador (etiquetas rhs_N):
 * 0 = aun no entra por la Puerta Corrupta. 1 = en la Pradera: los monstruos solo observan.
 * 2 = despertar: Tierra de Ceniza, los monstruos atacan y San Mudo manda los rituales. 5 = historia terminada.
 */
public final class Quest {
    private Quest() {}

    /** Estructura que alberga el ritual de cada monstruo (0 Sombra..5 Doble Boca). */
    private static final int[] STRUCTURE_OF = {4, 2, 1, 0, 3, 8};   // Cementerio, Arbol, Casa, Pozo Sellado, Capilla, Escuela

    public static int stage(ServerPlayerEntity p) {
        int s = 0;
        for (String t : p.getCommandTags()) {
            if (t.startsWith("rhs_")) {
                try {
                    s = Math.max(s, Integer.parseInt(t.substring(4)));
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return s;
    }

    public static void setStage(ServerPlayerEntity p, int stage) {
        for (String t : new ArrayList<>(p.getCommandTags())) {
            if (t.startsWith("rhs_")) p.removeCommandTag(t);
        }
        p.addCommandTag("rhs_" + stage);
    }

    /** Los monstruos solo atacan en la etapa 2 (antes solo te observan; despues del final ya no existen). */
    public static boolean aggressive(ServerPlayerEntity p) {
        return stage(p) == 2;
    }

    /** Primer ritual aun pendiente (0-5) o 6 si ya estan los seis. */
    public static int nextRitual() {
        for (int i = 0; i < 6; i++) {
            if (!StructureManager.isDefeated(i)) return i;
        }
        return 6;
    }

    public static int structureFor(int idx) {
        return STRUCTURE_OF[idx];
    }

    public static int ritualFor(int structureType) {
        for (int i = 0; i < STRUCTURE_OF.length; i++) {
            if (STRUCTURE_OF[i] == structureType) return i;
        }
        return -1;
    }

    public static int count(ServerPlayerEntity p, net.minecraft.item.Item item) {
        int n = 0;
        var inv = p.getInventory();
        for (int i = 0; i < inv.size(); i++) {
            if (inv.getStack(i).isOf(item)) n += inv.getStack(i).getCount();
        }
        return n;
    }

    /** Quita 'n' unidades del item del inventario. false (y no quita nada) si no hay suficientes. */
    public static boolean take(ServerPlayerEntity p, net.minecraft.item.Item item, int n) {
        if (count(p, item) < n) return false;
        var inv = p.getInventory();
        int left = n;
        for (int i = 0; i < inv.size() && left > 0; i++) {
            var st = inv.getStack(i);
            if (st.isOf(item)) {
                int k = Math.min(left, st.getCount());
                st.decrement(k);
                left -= k;
            }
        }
        return true;
    }
}
