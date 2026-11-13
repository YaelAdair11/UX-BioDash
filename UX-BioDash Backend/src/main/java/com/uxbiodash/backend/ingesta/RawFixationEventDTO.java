package com.uxbiodash.backend.ingesta;

/**
 * Fijacion ya clasificada por el software del fabricante.
 *
 * @param participante identificador del sujeto
 * @param estimulo     nombre del estimulo indicado en el archivo (puede ser null)
 * @param inicioRaw    inicio en la unidad nativa
 * @param finRaw       fin en la unidad nativa (puede ser null si solo hay duracion)
 * @param duracionMs   duracion reportada por el fabricante en milisegundos (puede ser null)
 * @param xRaw         coordenada X nativa
 * @param yRaw         coordenada Y nativa
 * @param valido       false si el fabricante marco el evento como anomalo
 */
public record RawFixationEventDTO(
        String participante,
        String estimulo,
        double inicioRaw,
        Double finRaw,
        Double duracionMs,
        Double xRaw,
        Double yRaw,
        boolean valido) {
}
