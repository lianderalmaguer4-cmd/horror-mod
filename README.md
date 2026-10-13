# Rostros Huecos — mod de terror (Fabric 1.21.4)

Monstruos (imagenes planas que giran hacia ti, inmortales, solo uno de cada tipo): Sombra Hueca (neutral), La Raiz, El Sudario,
El Hueco (vuela), Mandibula Caida, Doble Boca. Abren puertas, te pierden si te agachas lejos y respetan la Vela de Sal y el Sello.

Diario de San Mudo (se entrega al empezar): controles, monstruos, estructuras, fragmentos, notas y el final.

Esconderse: Shift + clic derecho en una cama (bajo techo). La camara baja bajo la cama. 10 s escondido = se van.
Dormir: rara vez algo sale de debajo de la cama; levantate a tiempo.
Ventanas: sube despacio, golpea 3 veces; a veces rompe el cristal. Puertas: a veces se abre sola.

Estructuras (aparecen al explorar): Pozo Sellado, Casa, Arbol del Vecino, Capilla, Cementerio. Al reunir las 10 Notas Rotas se abre el
Pozo de San Mudo (final).

EL FINAL: 6 fragmentos (cada monstruo deja el suyo si sobrevives a su persecucion completa; la Sombra lo deja con su carta) ->
colocarlos en el altar de obsidiana llorosa del fondo del pozo -> resistir 90 s -> FIN y Sello de San Mudo. Despues los eventos se calman.

Vela de Sal: panal + hilo + polvo de hueso. Comandos: /rostros diario | (trucos) evento, susto, estructura, final, fragmentos, notas, estructuras.

Compilar sin instalar nada: sube el contenido a GitHub y deja que Actions compile (.github/workflows/build.yml).

## Dimensiones (v11)
- Puerta Corrupta (Overworld): arco de piedra negra con puerta carmesi. Clic derecho = viajar. Sin las 10 notas lleva a LA PRADERA
  (llano casi plano, bultos anchos y bajos, muy pocos arboles, muchas mas ruinas); con las 10 notas, a la TIERRA DE CENIZA
  (noche fija, ceniza cayendo, mas eventos y mas persecuciones). La primera vez se construye una casa inicial para el jugador;
  su puerta trasera carmesi vuelve al Overworld.
- Todo se define en data/rostroshuecos/dimension* (generador plano) y se esculpe en DimTerrain.java.
- Comandos de prueba: /rostros puerta | pradera | ceniza.
