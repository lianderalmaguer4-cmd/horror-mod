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
        pg.add("PELIGROS\n\n• Dormir: rara vez algo sale despacio de debajo de la cama. Levántate a tiempo o morirás.\n• A veces el monstruo rompe la ventana o abre la puerta por sí mismo para entrar.\n• Escondite: si aguantas 10 segundos bajo la cama, los monstruos se rinden y desaparecen. Si sales antes de tiempo, te atacan.");
        pg.add("COMANDOS\n\n/rostros diario: otro diario\n/rostros evento: lanza un evento (trucos)\n/rostros susto: prueba el susto\n/rostros estructura: crea una ruina aquí\n/rostros estructuras: lista las que existen\n\nLos monstruos son inmortales y solo hay uno de cada tipo a la vez.");
        pg.add("SOMBRA HUECA (neutral)\n\nAlta y delgada, de ojos blancos. Ignora a quien no la mira. Si la miras o la golpeas, te persigue y te da Oscuridad.\nSi llama a tu puerta, abre: te deja una nota.\n\nConsejo: mira al suelo.");
        pg.add("LA RAÍZ (agresiva)\n\nSe alza en los bosques. Brazos de rama, falda de raíces. Es lenta, pero a menos de 7 bloques te ENREDA y casi no puedes moverte. Persigue unos 55 s.\n\nConsejo: corre en cuanto la veas, antes de que te alcance la vista.");
        pg.add("EL SUDARIO (agresivo)\n\nUna figura envuelta en oscuridad. APAGA la luz: rompe antorchas y linternas cercanas y te llena de Oscuridad a menos de 12 bloques. Persigue unos 45 s.\n\nConsejo: no confíes en tus antorchas.");
        pg.add("EL HUECO (agresivo, vuela)\n\nUn busto flotante de ojos enormes. Si lo miras a menos de 14 bloques te provoca NAUSEA. Persigue unos 45 s por el aire.\n\nConsejo: no sostengas su mirada y busca un lugar cerrado.");
        pg.add("MANDÍBULA CAÍDA (agresiva)\n\nSu boca descolgada SUCCIONA: cada 8 s, si estás entre 3 y 12 bloques y te ve, te arrastra hacia ella y te ralentiza. Persigue unos 45 s.\n\nConsejo: rompe la línea de visión tras una pared.");
        pg.add("DOBLE BOCA (agresiva)\n\nSonríe y camina erguida. Se CONGELA si la miras y corre si no. Repite en el chat lo que escribes. Persigue unos 50 s.\n\nConsejo: retrocede sin quitarle la vista de encima.");
        pg.add("ESTRUCTURAS\n\nMientras exploras aparecen ruinas de San Mudo:\n• El Pozo Sellado: tapa de piedra y escalera hacia abajo.\n• La Casa de San Mudo: cama, ventanas rotas y un cofre.\n• El Árbol del Vecino: un roble muerto y una tumba.\nTodas guardan Notas Rotas.");
        int n = collected(p);
        pg.add("NOTAS ROTAS\n\nHas reunido " + n + " de " + Lore.PAGES.length + ".\n\nCada nota que consigues se guarda sola aquí y desaparece de tu inventario.");
        for (int i = 0; i < Lore.PAGES.length; i++) {
            if (p.getCommandTags().contains(noteTag(i))) {
                pg.add("NOTA " + (i + 1) + "\n\n" + Lore.PAGES[i]);
            } else {
                pg.add("NOTA " + (i + 1) + "\n\n???\n\n(Aún no la has encontrado.)");
            }
        }
        return pg;
    }
}
