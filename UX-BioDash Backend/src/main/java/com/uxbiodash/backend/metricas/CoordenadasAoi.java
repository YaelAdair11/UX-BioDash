package com.uxbiodash.backend.metricas;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * Coordenadas normalizadas [0.0, 1.0] de un AOI, tal como se guardan en area_interes.coordenadas_json.
 * <ul>
 *   <li>RECTANGULO: {@code x, y} (esquina superior izquierda), {@code ancho, alto}.</li>
 *   <li>POLIGONO: {@code puntos} con al menos 3 vertices.</li>
 * </ul>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CoordenadasAoi(Double x, Double y, Double ancho, Double alto, List<Punto> puntos) {

    public record Punto(double x, double y) {
    }

    public static CoordenadasAoi rectangulo(double x, double y, double ancho, double alto) {
        return new CoordenadasAoi(x, y, ancho, alto, null);
    }

    public static CoordenadasAoi poligono(List<Punto> puntos) {
        return new CoordenadasAoi(null, null, null, null, puntos);
    }
}
