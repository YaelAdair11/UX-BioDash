package com.uxbiodash.backend.procesamiento;

/**
 * Sacada entre dos fijaciones consecutivas.
 *
 * @param indiceOrigen         posicion (0-based) de la fijacion de partida en la lista de fijaciones
 * @param indiceDestino        posicion (0-based) de la fijacion de llegada
 * @param amplitudGrados       amplitud angular estimada
 * @param velocidadPicoGradosS velocidad maxima observada entre ambas fijaciones (null si no hay muestras)
 */
public record SacadaCalculada(int indiceOrigen, int indiceDestino, Double amplitudGrados,
                              Double velocidadPicoGradosS) {
}
