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
 * El Diario de San Mudo. El progreso (que notas tienes) se guarda como etiquetas del jugador,
 * que persisten al cerrar el mundo y al morir.
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
                    p.addCommandTag("rh_final_pending");   // se abre el Pozo de San Mudo (StructureManager lo coloca)
                    p.sendMessage(Text.literal("Has reunido todas las notas. Algo despierta lejos de aquí…")
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

    public static List<String> buildPages(ServerPlayerEntity p) {
        List<String> pg = new ArrayList<>();
        pg.add("DIARIO DE SAN MUDO\n\nAquí se anota todo lo que descubras sobre los Rostros Huecos y el pozo sellado.\n\nPasa las páginas con las flechas del teclado o haciendo clic en los bordes de la página.");
        pg.add("CONTROLES\n\n• Diario: clic derecho para abrirlo.\n• Esconderte: Shift + clic derecho en una cama, bajo techo. La cámara baja al suelo, bajo la cama; quedas inmóvil e invisible. Clic derecho en cualquier bloque para salir.\n• Notas Rotas: se guardan solas en este diario.");
        pg.add("EVENTOS\n\n• Golpes en la puerta: alguien espera afuera. Si abres, el agresivo ataca; la Sombra deja una carta.\n• Ventanas: algo sube desde abajo y golpea el vidrio 3 veces. A veces, mientras duermes, te obliga a mirar.\n• Árboles y cuevas: figuras que te espían y desaparecen si te acercas.");
        pg.add("PELIGROS\n\n• Dormir: rara vez algo sale despacio de debajo de la cama. Levántate a tiempo o morirás.\n• A veces el monstruo rompe la ventana o abre la puerta por sí mismo para entrar.\n• Escondite: si aguantas 10 segundos bajo la cama, los monstruos se rinden y desaparecen. Si sales antes de tiempo, te atacan.\n• Si te agachas lejos, pueden perderte.");
        pg.add("COMANDOS\n\n/rostros diario: otro diario\n(con trucos)\n/rostros evento, susto\n/rostros estructura: ruina al azar\n/rostros final, puerta: crear\n/rostros pradera, ceniza: viajar\n/rostros fragmentos, notas\n/rostros estructuras: lista\n\nLos monstruos son inmortales y solo hay uno de cada tipo a la vez.");
        pg.add("SOMBRA HUECA (neutral)\n\nAlta y delgada, de ojos blancos. Ignora a quien no la mira. Si la miras o la golpeas, te persigue y te da Oscuridad.\nSi llama a tu puerta, abre: te deja una nota y su fragmento.\n\nConsejo: mira al suelo.");
        pg.add("LA RAÍZ (agresiva)\n\nSe alza en los bosques. Brazos de rama, falda de raíces. Es lenta, pero a menos de 7 bloques te ENREDA y casi no puedes moverte. Persigue unos 55 s.\n\nConsejo: corre en cuanto la veas, antes de que te alcance la vista.");
        pg.add("EL SUDARIO (agresivo)\n\nUna figura envuelta en oscuridad. APAGA la luz: rompe antorchas y linternas cercanas y te llena de Oscuridad a menos de 12 bloques. Persigue unos 45 s.\n\nConsejo: no confíes en tus antorchas.");
        pg.add("EL HUECO (agresivo, vuela)\n\nUn busto flotante de ojos enormes. Si lo miras a menos de 14 bloques te provoca NAUSEA. Persigue unos 45 s por el aire; no abre puertas.\n\nConsejo: no sostengas su mirada y encierra una puerta tras de ti.");
        pg.add("MANDÍBULA CAÍDA (agresiva)\n\nSu boca descolgada SUCCIONA: cada 8 s, si estás entre 3 y 12 bloques y te ve, te arrastra hacia ella y te ralentiza. Persigue unos 45 s.\n\nConsejo: rompe la línea de visión tras una pared.");
        pg.add("DOBLE BOCA (agresiva)\n\nSonríe y camina erguida. Se CONGELA si la miras y corre si no. Repite en el chat lo que escribes. Persigue unos 50 s.\n\nConsejo: retrocede sin quitarle la vista de encima.");
        pg.add("VELA DE SAL\n\nReceta sin forma: panal + hilo + polvo de hueso (da 2).\nSostenla en la mano: los monstruos a menos de 8 bloques pierden tu rastro y sus poderes. Se gasta (unos 60 s) mientras haya alguno cerca.\n\nHay velas en el cofre del pozo final.");
        int[] door = StructureManager.doorCoords();
        String doorTxt = door == null ? "Aún no la has visto: explora el Overworld." : "Una puerta corrupta se alza en x=" + door[0] + " z=" + door[1] + ".";
        pg.add("LA PUERTA CORRUPTA\n\n" + doorTxt + "\n\nAl cruzarla por primera vez, aparece una casa para ti en otro mundo: la PRADERA, un llano casi plano con pocas ruinas... y muchas. Con las diez notas, la puerta te lleva a la TIERRA DE CENIZA, oscura y más peligrosa. La puerta carmesi de tu casa vuelve al Overworld.");
        pg.add("ESTRUCTURAS\n\nMientras exploras aparecen ruinas de San Mudo:\n• El Pozo Sellado: tapa de piedra y escalera.\n• La Casa: cama, ventanas rotas.\n• El Árbol del Vecino y su tumba.\n• La Capilla: banco, altar y campana.\n• El Cementerio: tumbas con cofres enterrados.\nTodas guardan Notas Rotas.");
        pg.add("FRAGMENTOS\n\nCada monstruo deja caer su fragmento si sobrevives a su persecución completa (la Sombra lo deja junto a su carta).\nColocados en el altar: " + FinalEvent.placedCount(p) + "/6\n\n" + fragLine(p, 0, "Sombra") + fragLine(p, 1, "Raíz") + fragLine(p, 2, "Sudario") + fragLine(p, 3, "Hueco") + fragLine(p, 4, "Mandíbula") + fragLine(p, 5, "Doble Boca"));
        int n = collected(p);
        pg.add("NOTAS ROTAS\n\nHas reunido " + n + " de " + Lore.PAGES.length + ".\n\nCada nota que consigues se guarda sola aquí y desaparece de tu inventario. Con las diez, se abre el pozo verdadero.");
        for (int i = 0; i < Lore.PAGES.length; i++) {
            if (p.getCommandTags().contains(noteTag(i))) {
                pg.add("NOTA " + (i + 1) + "\n\n" + Lore.PAGES[i]);
            } else {
                pg.add("NOTA " + (i + 1) + "\n\n???\n\n(Aún no la has encontrado.)");
            }
        }
        if (n < Lore.PAGES.length) {
            pg.add("EL FINAL\n\n???\n\nReúne las diez notas para saber dónde está el pozo verdadero.");
        } else {
            int[] c = StructureManager.finalCoords();
            String where = c == null ? "El pozo aún se está abriendo. Explora la superficie." : "Una grieta se abrió en " + StructureManager.dimName(c[2]) + ": x=" + c[0] + " z=" + c[1] + ".";
            pg.add("EL FINAL\n\n" + where + "\n\nBaja hasta el fondo. Coloca los 6 fragmentos en el altar de obsidiana llorosa (clic derecho con el fragmento en la mano) y resiste 90 segundos mientras los seis te cazan.\n\nAbajo hay velas.");
        }
        if (FinalEvent.finished(p)) {
            pg.add("FIN\n\nSan Mudo descansa. El pozo está sellado de nuevo.\n\nGuardas el Sello: mientras lo sostengas, nada te verá.\n\nGracias por jugar.");
        }
        return pg;
    }

    private static String fragLine(ServerPlayerEntity p, int i, String name) {
        return (p.getCommandTags().contains("rh_frag_" + i) ? "[x] " : "[ ] ") + name + "\n";
    }
}
