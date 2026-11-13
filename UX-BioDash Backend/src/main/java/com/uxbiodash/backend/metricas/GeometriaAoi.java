package com.uxbiodash.backend.metricas;

import com.uxbiodash.backend.domain.FormaGeometrica;
import com.uxbiodash.backend.exception.SolicitudInvalidaException;
import com.uxbiodash.backend.metricas.CoordenadasAoi.Punto;
import java.util.List;

/** Geometria de un AOI en coordenadas normalizadas: prueba de pertenencia y area. */
public final class GeometriaAoi {

    private final FormaGeometrica forma;
    private final double x;
    private final double y;
    private final double ancho;
    private final double alto;
    private final List<Punto> puntos;

    private GeometriaAoi(FormaGeometrica forma, double x, double y, double ancho, double alto, List<Punto> puntos) {
        this.forma = forma;
        this.x = x;
        this.y = y;
        this.ancho = ancho;
        this.alto = alto;
        this.puntos = puntos;
    }

    /** Valida las coordenadas y construye la geometria. */
    public static GeometriaAoi de(FormaGeometrica forma, CoordenadasAoi c) {
        if (forma == null || c == null) {
            throw new SolicitudInvalidaException("El AOI debe indicar forma geometrica y coordenadas.");
        }
        if (forma == FormaGeometrica.RECTANGULO) {
            if (c.x() == null || c.y() == null || c.ancho() == null || c.alto() == null) {
                throw new SolicitudInvalidaException("Un RECTANGULO requiere x, y, ancho y alto.");
            }
            enRango(c.x(), "x");
            enRango(c.y(), "y");
            if (c.ancho() <= 0 || c.alto() <= 0) {
                throw new SolicitudInvalidaException("El ancho y alto del rectangulo deben ser mayores que 0.");
            }
            if (c.x() + c.ancho() > 1.0 + 1e-9 || c.y() + c.alto() > 1.0 + 1e-9) {
                throw new SolicitudInvalidaException("El rectangulo se sale de la imagen (coordenadas en [0, 1]).");
            }
            return new GeometriaAoi(forma, c.x(), c.y(), c.ancho(), c.alto(), List.of());
        }
        if (c.puntos() == null || c.puntos().size() < 3) {
            throw new SolicitudInvalidaException("Un POLIGONO requiere al menos 3 puntos.");
        }
        for (Punto p : c.puntos()) {
            enRango(p.x(), "puntos.x");
            enRango(p.y(), "puntos.y");
        }
        return new GeometriaAoi(forma, 0, 0, 0, 0, List.copyOf(c.puntos()));
    }

    private static void enRango(double v, String campo) {
        if (Double.isNaN(v) || v < 0.0 || v > 1.0) {
            throw new SolicitudInvalidaException("La coordenada '" + campo + "' debe estar normalizada en [0.0, 1.0].");
        }
    }

    public boolean contiene(double px, double py) {
        if (forma == FormaGeometrica.RECTANGULO) {
            return px >= x && px <= x + ancho && py >= y && py <= y + alto;
        }
        // Ray casting
        boolean dentro = false;
        for (int i = 0, j = puntos.size() - 1; i < puntos.size(); j = i++) {
            Punto a = puntos.get(i);
            Punto b = puntos.get(j);
            if ((a.y() > py) != (b.y() > py)
                    && px < (b.x() - a.x()) * (py - a.y()) / (b.y() - a.y()) + a.x()) {
                dentro = !dentro;
            }
        }
        return dentro;
    }

    public double area() {
        if (forma == FormaGeometrica.RECTANGULO) {
            return ancho * alto;
        }
        double suma = 0;
        for (int i = 0, j = puntos.size() - 1; i < puntos.size(); j = i++) {
            suma += (puntos.get(j).x() + puntos.get(i).x()) * (puntos.get(j).y() - puntos.get(i).y());
        }
        return Math.abs(suma / 2.0);
    }

    /**
     * Area de la caja envolvente (ancho x alto). Es la que decide la "AOI mas pequena" cuando una fijacion cae en
     * varias AOIs anidadas, igual que en el frontend (docs/API_CONTRACT.md).
     */
    public double areaEnvolvente() {
        if (forma == FormaGeometrica.RECTANGULO) {
            return ancho * alto;
        }
        double minX = 1, minY = 1, maxX = 0, maxY = 0;
        for (Punto p : puntos) {
            minX = Math.min(minX, p.x());
            minY = Math.min(minY, p.y());
            maxX = Math.max(maxX, p.x());
            maxY = Math.max(maxY, p.y());
        }
        return (maxX - minX) * (maxY - minY);
    }
}
