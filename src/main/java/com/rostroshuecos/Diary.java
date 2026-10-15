package com.rostroshuecos;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * El Diario de San Mudo. Se divide en SECCIONES (indice a la izquierda, texto con scroll a la derecha).
 * El progreso (notas, fragmentos, etapa de la historia) se guarda como etiquetas del jugador: persiste al cerrar el mundo y al morir.
 */
public final class Diary {
    private Diary() {}

    private static final String TAG_GIVEN = "rh_diario";

    private static String noteTag(int i) {
        return "rh_nota_" + i;
    }

    public static int collected(ServerPlayerEntity p) {
        int n = 0;
        for (int i = 0; i < Lore.PAGES.length; i++) {
            if (p.getCommandTags().contains(noteTag(i))) n++;
        }
        return n;
    }

    /** Anade la siguiente nota que aun no tengas. false si ya las tienes todas. */
    public static boolean collectNext(ServerPlayerEntity p) {
        for (int i = 0; i < Lore.PAGES.length; i++) {
            if (!p.getCommandTags().contains(noteTag(i))) {
                p.addCommandTag(noteTag(i));
                p.sendMessage(Text.literal("Nota " + (i + 1) + " añadida a tu diario.")
                        .formatted(Formatting.GRAY, Formatting.ITALIC), true);
                p.getServerWorld().playSound(null, p.getX(), p.getY(), p.getZ(),
                        SoundEvents.ITEM_BOOK_PAGE_TURN, SoundCategory.PLAYERS, 1.0F, 0.6F);
                if (collected(p) >= Lore.PAGES.length) {
                    p.addCommandTag("rh_final_pending");   // se abre el Pozo de San Mudo en la Pradera
                    p.sendMessage(Text.literal("Has reunido todas las notas. Algo se abre en la tierra...")
                            .formatted(Formatting.DARK_RED, Formatting.ITALIC), false);
                }
                return true;
            }
        }
        p.sendMessage(Text.literal("Ya tienes todas las notas en tu diario.").formatted(Formatting.GRAY), true);
        return false;
    }

    /** Las Notas Rotas del inventario se guardan solas en el diario y desaparecen. */
    public static void sweepNotes(ServerPlayerEntity p) {
        PlayerInventory inv = p.getInventory();
        for (int i = 0; i < inv.size(); i++) {
            ItemStack st = inv.getStack(i);
            if (st.isOf(ModItems.NOTA_ROTA)) {
                int n = st.getCount();
                inv.setStack(i, ItemStack.EMPTY);
                for (int k = 0; k < n; k++) collectNext(p);
            }
        }
    }

    /** Se entrega una sola vez, al entrar por primera vez al mundo. */
    public static void giveIfNeeded(ServerPlayerEntity p) {
        if (p.getCommandTags().contains(TAG_GIVEN)) return;
        p.addCommandTag(TAG_GIVEN);
        p.giveItemStack(new ItemStack(ModItems.DIARIO));
        p.sendMessage(Text.literal("Encuentras un diario viejo en tu bolsillo. Clic derecho para abrirlo.")
                .formatted(Formatting.DARK_GRAY, Formatting.ITALIC), false);
    }

    // ------------------------------------------------------------------ contenido
    private static final String[] RITUAL_NAMES = {"Sombra Hueca (Cementerio)", "La Raíz (Árbol del Vecino)",
            "El Sudario (Casa de San Mudo)", "El Hueco (Pozo Sellado)", "Mandíbula Caída (Capilla)", "Doble Boca (Escuela)"};

    private static void add(List<String> out, String title, String text) {
        out.add(title);
        out.add(text);
    }

    /** Devuelve [titulo, texto, titulo, texto, ...]. */
    public static List<String> buildPages(ServerPlayerEntity p) {
        List<String> out = new ArrayList<>();
        int stage = Quest.stage(p);
        int notes = collected(p);
        int[] door = StructureManager.doorCoords();

        add(out, "Inicio", "DIARIO DE SAN MUDO\n\nAquí se anota todo lo que descubras sobre los Rostros Huecos, el pozo y el pueblo que ya no existe.\n\n"
                + "Elige una sección en la lista de la izquierda (clic o flechas arriba/abajo) y baja el texto con la rueda del ratón.\n\n"
                + "Las notas que encuentres se guardan solas aquí.");

        // ---- Mision (cambia con la historia)
        StringBuilder m = new StringBuilder();
        if (stage == 0) {
            m.append("Busca la PUERTA CORRUPTA en el Overworld.\n\n");
            m.append(door == null ? "Aún no la has visto: explora sobre el terreno." : "Se alza en x=" + door[0] + " z=" + door[1] + ".");
            m.append("\n\nCuidado: al cruzarla, no hay vuelta atrás sin un ritual.");
        } else if (stage == 1) {
            m.append("Estás en la PRADERA. Aquí los monstruos solo te observan: te siguen, golpean, se asoman... pero no pueden dañarte. Todavía.\n\n");
            m.append("• Explora las ruinas y reúne las 10 notas (").append(notes).append("/10).\n");
            if (notes >= Lore.PAGES.length) {
                int[] w = StructureManager.finalCoords(1);
                m.append("• ¡Completas! El pozo se abrió");
                m.append(w == null ? ": explora la pradera hasta verlo." : " en x=" + w[0] + " z=" + w[1] + ".");
                m.append("\n• Baja al fondo y usa el altar de obsidiana llorosa: una voz te hablará.\n");
            } else {
                m.append("• Con las 10 notas se abre el Pozo de San Mudo.\n");
            }
            m.append("\nSalir de aquí exige el ritual de salida (5 Sal de San Mudo en la puerta carmesí de tu casa).");
        } else if (stage == 2) {
            int next = Quest.nextRitual();
            m.append("TIERRA DE CENIZA. Ahora los monstruos atacan. San Mudo te guía.\n\nRITUALES (necesitan 1 Sal cada uno):\n");
            for (int i = 0; i < 6; i++) {
                m.append(StructureManager.isDefeated(i) ? "[x] " : "[ ] ").append(RITUAL_NAMES[i]).append('\n');
            }
            m.append("\nSal que llevas: ").append(Quest.count(p, ModItems.SAL)).append('\n');
            if (next < 6) {
                m.append("\nAhora: ").append(Lore.HINTS[next]);
            } else {
                int[] c = StructureManager.finalCoords(2);
                m.append("\nLos seis descansan. Lleva los fragmentos (").append(FinalEvent.placedCount(p)).append("/6 colocados) al altar bajo el pozo");
                m.append(c == null ? "." : ": x=" + c[0] + " z=" + c[1] + ".");
            }
        } else {
            m.append("La historia terminó. El pozo está sellado y San Mudo descansa.\n\nGuardas el Sello: sostenlo y nada te verá.");
        }
        add(out, "Misión", m.toString());

        add(out, "Controles", "• Diario: clic derecho.\n• Esconderte: Shift + clic derecho en una cama bajo techo. La cámara baja al suelo, bajo la cama; quedas inmóvil e invisible. Clic derecho para salir. Si aguantas 10 s, los monstruos se rinden; si sales antes, te atacan.\n• Notas Rotas: se guardan solas.\n• Sal: se usa en los altares y para salir de la dimensión.");
        add(out, "Peligros", "• Puertas: alguien llama y espera afuera. Si abres, el monstruo agresivo te ataca (y solo si ya despertó la historia).\n• Ventanas: algo sube desde abajo y golpea 3 veces; a veces rompe el cristal.\n• Dormir: rara vez algo sale de debajo de la cama. Levántate a tiempo.\n• Árboles y cuevas: figuras que te siguen a distancia. Se esfuman si las miras o te acercas.\n• Sigilo: si te agachas lejos, pueden perderte.\n• Hasta el despertar, todo esto solo asusta.");

        add(out, "Sombra Hueca", "NEUTRAL.\n\nAlta y delgada, de ojos blancos. Ignora a quien no la mira. Si la miras o la golpeas te persigue y te da Oscuridad.\n\nRITUAL (Cementerio): invócala encadenada y NO la mires hasta completar el 100%. Cada vez que la miras, te golpea.");
        add(out, "La Raíz", "AGRESIVA, lenta.\n\nSe alza en los bosques. A menos de 7 bloques te ENREDA y casi no puedes moverte.\n\nRITUAL (Árbol del Vecino): rompe las 5 raíces que la sostienen mientras te ataca por rachas.");
        add(out, "El Sudario", "AGRESIVO.\n\nApaga la luz: rompe antorchas y linternas cercanas y da Oscuridad a menos de 12 bloques.\n\nRITUAL (Casa de San Mudo): mantén encendidas al menos 3 de las 4 velas del círculo (usa el encendedor). Él las apaga una a una.");
        add(out, "El Hueco", "AGRESIVO, vuela.\n\nUn busto flotante de ojos enormes. Si lo miras a menos de 14 bloques te provoca Nausea. No abre puertas.\n\nRITUAL (Pozo Sellado): al revés que la Sombra: debes MIRARLO sin apartar la vista hasta el 100%. Si apartas la vista, te golpea.");
        add(out, "Mandíbula Caída", "AGRESIVA.\n\nSu boca descolgada SUCCIONA: cada 8 s, si estás entre 3 y 12 bloques y te ve, te arrastra y te ralentiza.\n\nRITUAL (Capilla): toca la campana 7 veces mientras te arrastra. Si tardas, el ritual retrocede.");
        add(out, "Doble Boca", "AGRESIVA.\n\nSonríe y camina erguida. Se congela si la miras y corre si no. Repite en el chat lo que escribes.\n\nRITUAL (Escuela): escribe en el chat, tal cual, «" + Lore.PHRASE + "» tres veces.");
        add(out, "San Mudo", stage >= 5
                ? "Anselmo Mudo, médico del pueblo. Experimentó con los niños junto al pozo para devolverle la voz a su hija. El pueblo ardió. Atrapado bajo la tierra, la maldición lo convirtió en algo peor que sus criaturas.\n\nLas almas de los niños lo reclamaron al final."
                : (stage >= 2 ? "Una voz bajo el pozo. Dice que fue una víctima y que te ayudará a liberar a los niños.\n\n¿Y si no es toda la verdad?" : "???\n\nUna voz que aún no ha hablado."));

        add(out, "Rituales", "Cada monstruo se vence con un ritual propio, en el altar (magnetita sobre una placa de ladrillo negro) de su estructura, con 1 Sal. Lo invoca ENCADENADO: no se aleja del altar y ataca por rachas.\n\nAl completarlo suelta su fragmento (su alma) y no vuelve a aparecer jamás.\n\nRITUAL DE SALIDA (distinto del final): en la puerta carmesí de tu casa, con 5 Sal. Es la única forma de volver al Overworld antes de acabar la historia. Si mueres aquí, reapareces en tu casa.");
        add(out, "Estructuras", "Aparecen al explorar, muy seguidas en la Pradera y la Ceniza:\n• Pozo Sellado (ritual del Hueco).\n• Casa de San Mudo (ritual del Sudario).\n• Árbol del Vecino y su tumba (ritual de la Raíz).\n• Capilla con campana (ritual de la Mandíbula).\n• Cementerio (ritual de la Sombra).\n• Escuela (ritual de Doble Boca).\nSus cofres guardan Sal, Notas Rotas y Velas.\n\nOtras: la Puerta Corrupta (Overworld), tu casa inicial y el Pozo de San Mudo.");
        add(out, "Sal y fragmentos", "SAL DE SAN MUDO: se encuentra en los cofres. Hace falta 1 por ritual y 5 para el ritual de salida.\n\nFRAGMENTOS: cada ritual completado suelta el fragmento del monstruo. Hay 6; se entregan en el altar bajo el pozo.\nColocados: " + FinalEvent.placedCount(p) + "/6\n\n"
                + frag(p, 0, "Sombra") + frag(p, 1, "Raíz") + frag(p, 2, "Sudario") + frag(p, 3, "Hueco") + frag(p, 4, "Mandíbula") + frag(p, 5, "Doble Boca"));
        add(out, "Vela y Sello", "VELA DE SAL: panal + hilo + polvo de hueso (da 2). Sostenla: los monstruos a menos de 8 bloques pierden tu rastro y sus poderes. Se gasta (~60 s de uso). Los rituales la ignoran.\n\nSELLO DE SAN MUDO: es el premio del final. Sostenlo y nada te verá, sin límite.");

        // ---- Notas (todas juntas, con scroll)
        StringBuilder nb = new StringBuilder("Has reunido " + notes + " de " + Lore.PAGES.length + ".\n");
        for (int i = 0; i < Lore.PAGES.length; i++) {
            nb.append("\n— Nota ").append(i + 1).append(" —\n");
            nb.append(p.getCommandTags().contains(noteTag(i)) ? Lore.PAGES[i] : "???  (aún no la has encontrado)").append('\n');
        }
        add(out, "Notas (" + notes + "/10)", nb.toString());

        add(out, "El Final", stage >= 2 && Quest.nextRitual() >= 6
                ? "Con los seis fragmentos, baja al altar bajo el pozo (Ceniza) y colócalos.\n\nSan Mudo se revelará. Tendrás 20 segundos para huir; después te cazará durante 2 minutos mientras suplica. Resiste: las almas de los niños lo están reteniendo.\n\nEn el pozo hay velas: ralentizan a San Mudo."
                : "???\n\nPrimero deben descansar los seis.");
        add(out, "Comandos", "/rostros diario: otro diario\n\nCon trucos:\n/rostros etapa 0-5\n/rostros sal, notas, fragmentos\n/rostros evento, susto\n/rostros puerta, pradera, ceniza\n/rostros estructura, final\n/rostros estructuras: lista");
        return out;
    }

    private static String frag(ServerPlayerEntity p, int i, String name) {
        return (p.getCommandTags().contains("rh_frag_" + i) ? "[x] " : "[ ] ") + name + "\n";
    }
}
