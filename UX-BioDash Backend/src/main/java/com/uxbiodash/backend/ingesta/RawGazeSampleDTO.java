package com.uxbiodash.backend.ingesta;

/**
 * Objeto de transferencia canonico de una muestra cruda, independiente del fabricante.
 *
 * @param participante identificador del sujeto
 * @param estimulo     nombre del estimulo indicado en el archivo (puede ser null)
 * @param timestampRaw marca temporal en la unidad nativa del dispositivo
 * @param xRaw         coordenada X nativa (pixeles o proporcion)
 * @param yRaw         coordenada Y nativa (pixeles o proporcion)
 * @param pupila       diametro pupilar (unidad nativa, puede ser null)
 * @param valido       bandera de validez de captura (false = parpadeo / perdida de senal)
 */
public record RawGazeSampleDTO(
        String participante,
        String estimulo,
        double timestampRaw,
        Double xRaw,
        Double yRaw,
        Double pupila,
        boolean valido) {
}
