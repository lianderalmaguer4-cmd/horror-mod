package com.rostroshuecos;

/** Todos los textos de la historia. */
public final class Lore {
    private Lore() {}

    /** Las 10 Notas Rotas (la 10 es "la ultima pagina"). */
    public static final String[] PAGES = {
        "Diario de la maestra, 14 de marzo. Faltan tres niños. Siempre cerca del pozo. El doctor Mudo dice que fue la fiebre. Le llaman «San» Mudo porque cura sin decir palabra. Yo vi a los niños jugando ayer.",
        "Carta del alcalde. Que no se mande ni un niño más a la casa del doctor. Que el sacristán vigile el pozo de noche. No quiero oír más rumores en la plaza.",
        "Dibujo infantil, con lápiz rojo: un hombre muy alto con la boca abierta, de pie sobre un pozo. Debajo, con letra torcida: «El doctor dice que es un juego. Hay que quedarse muy quieto».",
        "Nota de la cocinera. Sobra pan en la cocina del doctor y él no come. Lo baja al pozo cada noche, en una canasta, para nadie. Ayer volvió vacía y con huellas pequeñas en el borde.",
        "Libreta del doctor, escrita a las prisas. Experimento 4: la Raíz tomó bien. Experimento 5: no responde, solo mira. Ella dijo «papá» desde la sombra, pero no era ella. Hay que seguir.",
        "Carta de la madre de Tomás. Mi hijo habla dormido con dos voces: una es la suya. La otra repite todo lo que decimos en la mesa. El doctor dice que es normal. Yo ya no duermo.",
        "Registro del sacristán. Seis niños desaparecidos, seis ataúdes vacíos en el cementerio. Las campanas de la capilla suenan solas siete veces cada noche. Nadie las toca.",
        "Aviso clavado en la plaza. Esta noche quemaremos la casa del doctor Mudo y sellaremos el pozo con piedra y cal. Que Dios nos perdone si nos equivocamos, y si no, también.",
        "Cartel quemado, apenas legible. SAN MUDO YA NO TIENE VOZ PORQUE SE LA DIO A ELLOS. El pueblo ardió esa noche. Solo quedó él, bajo la tierra, en su laboratorio, con algo que ya no era una disculpa.",
        "ÚLTIMA PÁGINA. No confíes en la voz del pozo. Quien te pida ayuda quiere tus manos, no tu piedad. Los fragmentos son las almas de los niños: no los entregues. Si estás leyendo esto, ya es tarde."
    };

    /** San Mudo en el altar del pozo (Pradera): se hace pasar por la victima. */
    public static final String[] ALTAR = {
        "Por fin... alguien llega hasta aquí abajo.",
        "Soy San Mudo. Así me llamaban porque curaba sin decir palabra. Fui el médico de este pueblo.",
        "Hace mucho cayó una maldición sobre este pozo. Los niños empezaron a perderse a su alrededor, uno tras otro.",
        "Se convirtieron en las criaturas que has visto: la Sombra, la Raíz, el Sudario, el Hueco, la Mandíbula y Doble Boca.",
        "El pueblo me culpó. Quemaron mi casa y me encerraron aquí, en mi laboratorio. Hace años que no veo el sol.",
        "Pero tú puedes ayudarme. Libera a cada niño con un ritual y sus almas descansarán. Yo te guiaré.",
        "Lee la última página de tu diario. Ahí está la promesa que te hago... y lo que te pido a cambio.",
        "Te sacaré de esta pradera antes de que ellos despierten. Confía en mí."
    };

    /** Primera indicacion de San Mudo al llegar a la Ceniza. */
    public static final String ARRIVAL =
        "Aquí estás a salvo... por ahora. Lo verde murió con la maldición. Haz lo que te pido y todo habrá terminado.";

    /** Indicaciones de cada ritual (0 Sombra .. 5 Doble Boca). */
    public static final String[] HINTS = {
        "Primero la Sombra: el niño que dejó de hablar. Duerme en el cementerio. Usa una Sal en su altar. Cuando aparezca encadenada, NO la mires: solo así se aquieta.",
        "Ahora la Raíz: el niño que enterraron bajo el roble. Ve al árbol del vecino y usa una Sal en el altar. Rompe las cinco raíces que la sostienen.",
        "El Sudario: la fiebre que se llevó a la tercera niña. Vive en la casa. Cuando salga, mantén encendidas las velas del círculo aunque te apague la luz.",
        "El Hueco: el que miró dentro del pozo. Está en el pozo sellado. No le quites los ojos de encima: solo una mirada firme le da paz.",
        "La Mandíbula: el que gritó hasta romperse. Está en la capilla. Toca la campana siete veces mientras te arrastra hacia ella.",
        "Doble Boca: los gemelos. Están en la escuela. Díselo en voz alta, en el chat, tres veces: «ya no hay niños»."
    };

    /** San Mudo tras vencer cada ritual. */
    public static final String[] DONE = {
        "Uno menos que llora. Gracias. Sigue con la Raíz.",
        "Bien... otro que descansa. Qué alivio.",
        "La luz vuelve a ellos. Ya falta poco.",
        "Cuatro. Solo quedan dos.",
        "Casi... ya casi puedo salir de aquí.",
        "Los seis. Ahora tráeme los fragmentos al altar de mi laboratorio, bajo el pozo. Date prisa."
    };

    /** San Mudo al mostrarse (final). */
    public static final String[] REVEAL = {
        "Gracias, hijo. Tardé tantos años en reunir seis almas...",
        "Nunca fueron ellos los monstruos.",
        "Cada niño que se perdió me dio algo: una voz, una mano, una sombra. Eran materiales.",
        "Mi hija murió en esa casa hace cuarenta años. Yo solo quería devolverle la voz.",
        "Ahora dame lo que falta.",
        "Corre."
    };

    /** Lo que susurra mientras te persigue en el final. */
    public static final String[] CHASE = {
        "ayúdame...",
        "acaba con mi sufrimiento...",
        "no puedo parar... por favor...",
        "hazlo... hazlo ya...",
        "me duele... todo me duele...",
        "no quiero hacerte daño... ayúdame...",
        "detenme... antes de que te alcance..."
    };

    /** Cierre. */
    public static final String[] END = {
        "Los niños... vienen por mí...",
        "No... ya no duele...",
        "Gracias. Ahora sí... puedo callar."
    };

    /** Frase que hay que decir a Doble Boca. */
    public static final String PHRASE = "ya no hay niños";

    public static final String[] WHISPERS = {
        "Te estoy viendo.",
        "No mires atrás.",
        "Repite tu nombre.",
        "Tenemos hambre de voces.",
        "Ya casi llego.",
        "¿Por qué corres?",
        "Abre la puerta.",
        "Mira debajo de la cama.",
        "No estás solo.",
        "Apaga la luz. Así te veo mejor."
    };
}
