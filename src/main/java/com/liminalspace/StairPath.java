package com.liminalspace;

import java.util.ArrayList;
import java.util.List;

/**
 * Calcula una escalera helicoidal que rodea el pilar y termina justo delante de la casa.
 * No usa clases de Minecraft para poder comprobar la geometria por separado.
 */
public final class StairPath {
    public static final int BASE_Y = 4;

    /** Un bloque de la escalera. kind: 0 = escalon (stairs), 1 = bloque macizo de union. */
    public record Cell(int x, int y, int z, int dx, int dz, int kind) {}

    private StairPath() {}

    /** Radio del pilar a la altura y (debe coincidir con el usado al construirlo). */
    public static int pillarRadius(int y, int pillarTop) {
        double t = (y - BASE_Y) / (double) (pillarTop - BASE_Y);
        return (int) Math.max(8, 12 - t * 4);
    }

    private static double radiusAt(int y, int pillarTop) {
        return pillarRadius(y, pillarTop) + 5.0;
    }

    private static double[] rawAngles(int firstY, int lastY, int pillarTop, double offset) {
        int n = lastY - firstY + 1;
        double[] a = new double[n];
        int px = Integer.MIN_VALUE, pz = Integer.MIN_VALUE;
        double cum = offset;
        for (int k = 0; k < n; k++) {
            double r = radiusAt(firstY + k, pillarTop);
            if (k > 0) cum += 0.95 / r;
            while (true) {
                int x = (int) Math.round(r * Math.cos(cum));
                int z = (int) Math.round(r * Math.sin(cum));
                if (x != px || z != pz) { px = x; pz = z; break; }
                cum += 0.05 / r;
            }
            a[k] = cum;
        }
        return a;
    }

    private static double[] angles(int firstY, int lastY, int pillarTop, double endAngle) {
        int n = lastY - firstY + 1;
        // Primera pasada para saber cuanto gira en total; la segunda ya parte del desfase correcto,
        // de modo que los pasos repetidos se corrigen con el angulo definitivo.
        double[] first = rawAngles(firstY, lastY, pillarTop, 0.0);
        double offset = endAngle - first[n - 1];
        return rawAngles(firstY, lastY, pillarTop, offset);
    }

    /** Lista ordenada de celdas, desde el suelo hasta la cima. */
    public static List<Cell> compute(int firstY, int lastY, int pillarTop, double endAngle) {
        int n = lastY - firstY + 1;
        double[] a = angles(firstY, lastY, pillarTop, endAngle);
        int[] xs = new int[n];
        int[] zs = new int[n];
        for (int k = 0; k < n; k++) {
            double r = radiusAt(firstY + k, pillarTop);
            xs[k] = (int) Math.round(r * Math.cos(a[k]));
            zs[k] = (int) Math.round(r * Math.sin(a[k]));
        }
        List<Cell> out = new ArrayList<>();
        int lastDx = 0, lastDz = 1;
        for (int k = 0; k < n; k++) {
            int y = firstY + k;
            if (k > 0 && xs[k] != xs[k - 1] && zs[k] != zs[k - 1]) {
                // Paso en diagonal: se anade un bloque macizo para que el camino sea continuo.
                out.add(new Cell(xs[k], y - 1, zs[k - 1], 0, 0, 1));
            }
            int dx, dz;
            if (k + 1 < n) {
                int ddx = xs[k + 1] - xs[k];
                int ddz = zs[k + 1] - zs[k];
                if (ddx != 0 && ddz != 0) {
                    // Diagonal: el siguiente tramo entra primero por el eje x (ver bloque de union).
                    dx = 0; dz = ddz > 0 ? 1 : -1;
                    // El bloque de union esta en (x[k+1], z[k]); se llega a el moviendose en x.
                    dx = ddx > 0 ? 1 : -1; dz = 0;
                } else {
                    dx = Integer.signum(ddx);
                    dz = Integer.signum(ddz);
                }
                lastDx = dx; lastDz = dz;
            } else {
                dx = lastDx; dz = lastDz;
            }
            out.add(new Cell(xs[k], y, zs[k], dx, dz, 0));
        }
        return out;
    }

    /** Punto de aparicion {x, z, yawGrados}: fuera del pilar, junto al primer escalon, mirando al pilar. */
    public static double[] spawn(int firstY, int lastY, int pillarTop, double endAngle) {
        double[] a = angles(firstY, lastY, pillarTop, endAngle);
        double r = radiusAt(firstY, pillarTop) + 3.5;
        double x = r * Math.cos(a[0]);
        double z = r * Math.sin(a[0]);
        double yaw = Math.toDegrees(Math.atan2(Math.cos(a[0]), -Math.sin(a[0])));
        return new double[]{x + 0.5, z + 0.5, yaw};
    }
}
