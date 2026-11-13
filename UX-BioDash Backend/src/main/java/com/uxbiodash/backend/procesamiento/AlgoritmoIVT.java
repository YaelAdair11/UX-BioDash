package com.uxbiodash.backend.procesamiento;

import java.util.ArrayList;
import java.util.List;

/**
 * Clasificador por umbral de velocidad (Velocity-Threshold Identification, I-VT).
 * <ol>
 *   <li>Calcula la velocidad angular punto a punto.</li>
 *   <li>Las muestras por debajo del umbral se etiquetan como fijacion; el resto como sacada.</li>
 *   <li>Muestras de fijacion consecutivas (sin huecos mayores a la separacion maxima) forman una fijacion.</li>
 *   <li>Se fusionan fijaciones adyacentes muy cercanas en tiempo y espacio.</li>
 *   <li>Se descartan fijaciones mas cortas que la duracion minima.</li>
 * </ol>
 */
public class AlgoritmoIVT {

    private final ConversorAnguloVisual conversor;
    private final double umbralGradosS;
    private final double duracionMinimaMs;
    private final double separacionMaximaMs;
    private final double fusionMaximaMs;
    private final double fusionMaximaGrados;

    public AlgoritmoIVT(ConversorAnguloVisual conversor, double umbralGradosS, double duracionMinimaMs,
                        double separacionMaximaMs, double fusionMaximaMs, double fusionMaximaGrados) {
        this.conversor = conversor;
        this.umbralGradosS = umbralGradosS;
        this.duracionMinimaMs = duracionMinimaMs;
        this.separacionMaximaMs = separacionMaximaMs;
        this.fusionMaximaMs = fusionMaximaMs;
        this.fusionMaximaGrados = fusionMaximaGrados;
    }

    public record Resultado(List<FijacionCalculada> fijaciones, List<SacadaCalculada> sacadas) {
    }

    /** Grupo de muestras contiguas [desde, hasta] clasificadas como fijacion. */
    private static final class Grupo {
        int desde;
        int hasta;
        double sumaX;
        double sumaY;
        int n;

        Grupo(int indice, MuestraNormalizada m) {
            desde = indice;
            hasta = indice;
            agregar(m);
        }

        void agregar(MuestraNormalizada m) {
            sumaX += m.x();
            sumaY += m.y();
            n++;
        }

        void fusionar(Grupo otro) {
            hasta = otro.hasta;
            sumaX += otro.sumaX;
            sumaY += otro.sumaY;
            n += otro.n;
        }

        double x() {
            return sumaX / n;
        }

        double y() {
            return sumaY / n;
        }
    }

    public Resultado clasificar(List<MuestraNormalizada> muestras) {
        int n = muestras.size();
        if (n < 2) {
            return new Resultado(List.of(), List.of());
        }
        double[] velocidad = new double[n];
        for (int i = 1; i < n; i++) {
            MuestraNormalizada a = muestras.get(i - 1);
            MuestraNormalizada b = muestras.get(i);
            double dt = (b.tiempoMs() - a.tiempoMs()) / 1000.0;
            velocidad[i] = dt > 0 ? conversor.grados(a.x(), a.y(), b.x(), b.y()) / dt : Double.MAX_VALUE;
        }
        velocidad[0] = velocidad[1];

        // 2-3. Etiquetado y agrupacion
        List<Grupo> grupos = new ArrayList<>();
        Grupo actual = null;
        for (int i = 0; i < n; i++) {
            MuestraNormalizada m = muestras.get(i);
            boolean esFijacion = velocidad[i] <= umbralGradosS;
            boolean continuo = actual != null && i == actual.hasta + 1
                    && m.tiempoMs() - muestras.get(actual.hasta).tiempoMs() <= separacionMaximaMs;
            if (esFijacion) {
                if (continuo) {
                    actual.hasta = i;
                    actual.agregar(m);
                } else {
                    actual = new Grupo(i, m);
                    grupos.add(actual);
                }
            } else {
                actual = null;
            }
        }

        // 4. Fusion de fijaciones adyacentes
        List<Grupo> fusionados = new ArrayList<>();
        for (Grupo g : grupos) {
            if (!fusionados.isEmpty()) {
                Grupo previo = fusionados.get(fusionados.size() - 1);
                double hueco = muestras.get(g.desde).tiempoMs() - muestras.get(previo.hasta).tiempoMs();
                double angulo = conversor.grados(previo.x(), previo.y(), g.x(), g.y());
                if (hueco <= fusionMaximaMs && angulo <= fusionMaximaGrados) {
                    previo.fusionar(g);
                    continue;
                }
            }
            fusionados.add(g);
        }

        // 5. Descarte por duracion minima
        List<Grupo> validos = new ArrayList<>();
        for (Grupo g : fusionados) {
            double duracion = muestras.get(g.hasta).tiempoMs() - muestras.get(g.desde).tiempoMs();
            if (duracion >= duracionMinimaMs) {
                validos.add(g);
            }
        }

        List<FijacionCalculada> fijaciones = new ArrayList<>(validos.size());
        for (int k = 0; k < validos.size(); k++) {
            Grupo g = validos.get(k);
            int inicio = (int) Math.round(muestras.get(g.desde).tiempoMs());
            int fin = (int) Math.round(muestras.get(g.hasta).tiempoMs());
            fijaciones.add(new FijacionCalculada(k + 1, inicio, fin, fin - inicio,
                    Normalizador.limitar(g.x()), Normalizador.limitar(g.y())));
        }

        List<SacadaCalculada> sacadas = new ArrayList<>();
        for (int k = 0; k + 1 < validos.size(); k++) {
            Grupo origen = validos.get(k);
            Grupo destino = validos.get(k + 1);
            double amplitud = conversor.grados(origen.x(), origen.y(), destino.x(), destino.y());
            Double pico = null;
            for (int i = origen.hasta + 1; i <= destino.desde; i++) {
                if (velocidad[i] != Double.MAX_VALUE) {
                    pico = pico == null ? velocidad[i] : Math.max(pico, velocidad[i]);
                }
            }
            sacadas.add(new SacadaCalculada(k, k + 1, amplitud, pico));
        }
        return new Resultado(fijaciones, sacadas);
    }
}
