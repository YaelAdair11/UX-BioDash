package com.uxbiodash.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Parametros del modulo de procesamiento (application.properties, prefijo uxbiodash.procesamiento).
 *
 * @param distanciaVisualCm                distancia ojo-pantalla usada para convertir a grados visuales
 * @param anchoPantallaCm                  ancho fisico del monitor de la prueba
 * @param altoPantallaCm                   alto fisico del monitor de la prueba
 * @param ivtUmbralVelocidadGradosS        umbral de velocidad del algoritmo I-VT
 * @param ivtDuracionMinimaFijacionMs      fijaciones mas cortas se descartan
 * @param ivtSeparacionMaximaMs            huecos mayores entre muestras cortan una fijacion
 * @param ivtFusionMaximaMs                tiempo maximo entre fijaciones para fusionarlas
 * @param ivtFusionMaximaGrados            angulo maximo entre fijaciones para fusionarlas
 * @param velocidadMaximaFisiologicaGradosS saltos por encima de este valor se descartan como ruido
 * @param tamanoLoteMuestras               tamano del lote JDBC al insertar muestras crudas
 */
@ConfigurationProperties(prefix = "uxbiodash.procesamiento")
public record ProcesamientoProperties(
        Double distanciaVisualCm,
        Double anchoPantallaCm,
        Double altoPantallaCm,
        Double ivtUmbralVelocidadGradosS,
        Integer ivtDuracionMinimaFijacionMs,
        Integer ivtSeparacionMaximaMs,
        Integer ivtFusionMaximaMs,
        Double ivtFusionMaximaGrados,
        Double velocidadMaximaFisiologicaGradosS,
        Integer tamanoLoteMuestras) {

    public ProcesamientoProperties {
        distanciaVisualCm = valor(distanciaVisualCm, 60.0);
        anchoPantallaCm = valor(anchoPantallaCm, 53.1);
        altoPantallaCm = valor(altoPantallaCm, 29.9);
        ivtUmbralVelocidadGradosS = valor(ivtUmbralVelocidadGradosS, 30.0);
        ivtDuracionMinimaFijacionMs = valor(ivtDuracionMinimaFijacionMs, 60);
        ivtSeparacionMaximaMs = valor(ivtSeparacionMaximaMs, 75);
        ivtFusionMaximaMs = valor(ivtFusionMaximaMs, 75);
        ivtFusionMaximaGrados = valor(ivtFusionMaximaGrados, 0.5);
        velocidadMaximaFisiologicaGradosS = valor(velocidadMaximaFisiologicaGradosS, 1000.0);
        tamanoLoteMuestras = valor(tamanoLoteMuestras, 5000);
    }

    /** Valores por defecto, utiles para pruebas unitarias. */
    public static ProcesamientoProperties porDefecto() {
        return new ProcesamientoProperties(null, null, null, null, null, null, null, null, null, null);
    }

    private static <T> T valor(T actual, T defecto) {
        return actual != null ? actual : defecto;
    }
}
