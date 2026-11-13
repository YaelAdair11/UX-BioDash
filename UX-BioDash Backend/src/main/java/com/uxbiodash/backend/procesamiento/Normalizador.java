package com.uxbiodash.backend.procesamiento;

import com.uxbiodash.backend.ingesta.RawGazeSampleDTO;
import com.uxbiodash.backend.ingesta.SistemaCoordenadas;
import com.uxbiodash.backend.ingesta.UnidadTiempo;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Implementa el "Mecanismo de Estandarizacion y Normalizacion":
 * <ul>
 *   <li>3.1 Normalizacion espacial a [0.0, 1.0] con clamping.</li>
 *   <li>3.2 Normalizacion temporal relativa (t = 0 ms).</li>
 *   <li>3.3 Filtrado de ruido: nulos/NaN/negativos, parpadeos y saltos &gt; 1000 grados/s.</li>
 * </ul>
 */
public final class Normalizador {

    private Normalizador() {
    }

    public record Resolucion(int anchoPx, int altoPx) {
        public Resolucion {
            if (anchoPx <= 0 || altoPx <= 0) {
                throw new IllegalArgumentException("La resolucion debe ser positiva");
            }
        }
    }

    public record ResultadoNormalizacion(List<MuestraNormalizada> muestras, int descartadasInvalidas,
                                         int descartadasVelocidad) {
    }

    /** Normaliza una coordenada al intervalo unitario y aplica la regla de acotamiento. */
    public static double normalizarCoordenada(double valor, SistemaCoordenadas sistema, int resolucionPx) {
        double norm = sistema == SistemaCoordenadas.PIXELES ? valor / resolucionPx : valor;
        return limitar(norm);
    }

    public static double limitar(double v) {
        return Math.max(0.0, Math.min(1.0, v));
    }

    /** Una coordenada es invalida si es nula, NaN o un valor centinela negativo (ej. -1.0 en EyeLink). */
    public static boolean coordenadaInvalida(Double v) {
        return v == null || v.isNaN() || v.isInfinite() || v < 0;
    }

    /**
     * Algoritmo NormalizarDatasetEyeTracking aplicado a las muestras de un participante.
     */
    public static ResultadoNormalizacion normalizarMuestras(List<RawGazeSampleDTO> crudas, UnidadTiempo unidad,
                                                            SistemaCoordenadas sistema, Resolucion resolucion,
                                                            ConversorAnguloVisual conversor,
                                                            double velocidadMaximaGradosS) {
        List<RawGazeSampleDTO> ordenadas = new ArrayList<>(crudas);
        ordenadas.sort(Comparator.comparingDouble(RawGazeSampleDTO::timestampRaw));

        List<MuestraNormalizada> resultado = new ArrayList<>(ordenadas.size());
        int invalidas = 0;
        int porVelocidad = 0;
        Double t0 = null;
        MuestraNormalizada anterior = null;

        for (RawGazeSampleDTO m : ordenadas) {
            // 1. Validar integridad de senal
            if (!m.valido() || coordenadaInvalida(m.xRaw()) || coordenadaInvalida(m.yRaw())) {
                invalidas++;
                continue;
            }
            // 2. Normalizacion temporal (t = 0 ms)
            if (t0 == null) {
                t0 = m.timestampRaw();
            }
            double tiempoMs = unidad.aMilisegundos(m.timestampRaw() - t0);
            // 3. Normalizacion espacial [0, 1] + 4. clamping
            double x = normalizarCoordenada(m.xRaw(), sistema, resolucion.anchoPx());
            double y = normalizarCoordenada(m.yRaw(), sistema, resolucion.altoPx());
            MuestraNormalizada actual = new MuestraNormalizada(tiempoMs, x, y, m.pupila());

            // 3.3.3 Salto sacadico fisiologicamente imposible
            if (anterior != null) {
                double dt = actual.tiempoMs() - anterior.tiempoMs();
                if (dt <= 0) {
                    invalidas++; // marca temporal duplicada
                    continue;
                }
                double velocidad = conversor.grados(anterior.x(), anterior.y(), x, y) / (dt / 1000.0);
                if (velocidad > velocidadMaximaGradosS) {
                    porVelocidad++;
                    continue;
                }
            }
            resultado.add(actual);
            anterior = actual;
        }
        return new ResultadoNormalizacion(resultado, invalidas, porVelocidad);
    }
}
