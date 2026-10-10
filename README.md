# Rostros Huecos — mod de terror (Fabric 1.21.4)

## Contenido
- Pollita Rota (agresiva): se congela si la miras; susto al alcanzarte.
- Sombra Hueca (neutral): solo te ataca si la miras o la golpeas; da Oscuridad.
- Niño Gris (pacífico): clic derecho = lore + Nota Rota, y desaparece.
- Muñeco Perdido (pacífico): te sigue a distancia.
- Nota Rota: clic derecho = fragmento de lore (a veces, susto).
- Eventos ambientales a oscuras: sonidos a tu espalda y sustos glitch.
- Pestaña creativa "Rostros Huecos" con huevos generadores.

## Compilar sin instalar nada (GitHub)
1. Crea un repositorio en github.com y sube TODO el contenido de esta carpeta.
2. Pestaña Actions -> "Compilar mod" -> Run workflow (se ejecuta solo al subir).
3. Cuando termine, abre la ejecución y descarga el artefacto "RostrosHuecos-jar".
4. Usa el .jar `rostros-huecos-1.0.0.jar` (NO el `-sources`) en la carpeta mods.

## Compilar en tu PC
Java 21 + Gradle 8.11: `gradle build` -> `build/libs/rostros-huecos-1.0.0.jar`
(o abre la carpeta en IntelliJ IDEA y ejecuta la tarea build).

## Personalizar
- Imagen del susto: textures/gui/jumpscare.png (si cambias el tamaño, edita IMG_W/IMG_H en JumpscareClient.java).
- Lore: Lore.java. Texturas de entidades: textures/entity/*.png (64x64, formato skin).
- Si las versiones fallan, actualiza gradle.properties con las de https://fabricmc.net/develop
