package com.uxbiodash.backend.procesamiento;

/**
 * Convierte distancias en coordenadas normalizadas [0, 1] a grados de angulo visual, a partir del
 * tamano fisico del monitor y la distancia de visualizacion configurados.
 */
public class ConversorAnguloVisual {

    private final double distanciaCm;
    private final double anchoCm;
    private final double altoCm;

    public ConversorAnguloVisual(double distanciaCm, double anchoCm, double altoCm) {
        if (distanciaCm <= 0 || anchoCm <= 0 || altoCm <= 0) {
            throw new IllegalArgumentException("La geometria de visualizacion debe ser positiva");
        }
        this.distanciaCm = distanciaCm;
        this.anchoCm = anchoCm;
        this.altoCm = altoCm;
    }

    /** Angulo visual (grados) entre dos puntos expresados en coordenadas normalizadas. */
    public double grados(double x1, double y1, double x2, double y2) {
        double dxCm = (x2 - x1) * anchoCm;
        double dyCm = (y2 - y1) * altoCm;
        double distancia = Math.hypot(dxCm, dyCm);
        return Math.toDegrees(2.0 * Math.atan(distancia / (2.0 * distanciaCm)));
    }
}
