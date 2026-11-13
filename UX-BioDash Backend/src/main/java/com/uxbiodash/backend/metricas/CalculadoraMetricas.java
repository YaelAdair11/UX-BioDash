package com.uxbiodash.backend.metricas;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Calculo puro (sin dependencias de BD) de las metricas del "Catalogo de Metricas Clave de Eye Tracking".
 */
public final class CalculadoraMetricas {

    private CalculadoraMetricas() {
    }

    /** Vista minima de una fijacion necesaria para calcular metricas. */
    public record FijacionVista(int orden, int inicioMs, int finMs, int duracionMs, double x, double y) {
    }

    /**
     * Metricas de un AOI para una sesion.
     *
     * @param conteoFijaciones        numero de fijaciones dentro del AOI
     * @param duracionMediaFijacionMs duracion media de esas fijaciones (null si no hubo)
     * @param dwellTimeMs             tiempo de permanencia: suma de visitas (fijaciones + sacadas internas)
     * @param porcentajeDwell         dwell / duracion total de la sesion * 100
     * @param ttffMs                  tiempo hasta la primera fijacion (null si nunca se miro)
     * @param numeroVisitas           entradas de la mirada al AOI
     */
    public record MetricasAoi(int conteoFijaciones, Double duracionMediaFijacionMs, int dwellTimeMs,
                              Double porcentajeDwell, Integer ttffMs, int numeroVisitas) {
    }

    public record ResumenSesion(int totalFijaciones, Double duracionMediaFijacionMs, Double duracionMedianaFijacionMs,
                                Double desviacionDuracionMs, Double tasaFijacionesPorSegundo,
                                Double amplitudMediaSacadaGrados, Double coeficienteK) {
    }

    public static MetricasAoi metricasAoi(List<FijacionVista> fijaciones, GeometriaAoi aoi, int duracionTotalMs) {
        int conteo = 0;
        long sumaDuracion = 0;
        int dwell = 0;
        int visitas = 0;
        Integer ttff = null;
        Integer inicioVisita = null;
        int finVisita = 0;

        for (FijacionVista f : fijaciones) {
            if (aoi.contiene(f.x(), f.y())) {
                conteo++;
                sumaDuracion += f.duracionMs();
                if (ttff == null) {
                    ttff = f.inicioMs();
                }
                if (inicioVisita == null) {
                    inicioVisita = f.inicioMs();
                    visitas++;
                }
                finVisita = f.finMs();
            } else if (inicioVisita != null) {
                dwell += finVisita - inicioVisita;
                inicioVisita = null;
            }
        }
        if (inicioVisita != null) {
            dwell += finVisita - inicioVisita;
        }
        Double media = conteo > 0 ? (double) sumaDuracion / conteo : null;
        Double porcentaje = duracionTotalMs > 0 ? 100.0 * dwell / duracionTotalMs : null;
        return new MetricasAoi(conteo, media, dwell, porcentaje, ttff, visitas);
    }

    /** AOI asignado a una fijacion: el de menor area que la contiene (el mas especifico), o null. */
    public static Long aoiDeFijacion(double x, double y, Map<Long, GeometriaAoi> aois) {
        Long mejor = null;
        double menorArea = Double.MAX_VALUE;
        for (Map.Entry<Long, GeometriaAoi> e : aois.entrySet()) {
            if (e.getValue().contiene(x, y) && e.getValue().areaEnvolvente() < menorArea) {
                menorArea = e.getValue().areaEnvolvente();
                mejor = e.getKey();
            }
        }
        return mejor;
    }

    /**
     * @param fijaciones  fijaciones ordenadas
     * @param amplitudes  amplitud de la sacada que sigue a cada fijacion (tamano n-1)
     * @param duracionTotalMs duracion de la sesion
     */
    public static ResumenSesion resumenSesion(List<FijacionVista> fijaciones, List<Double> amplitudes,
                                              int duracionTotalMs) {
        int n = fijaciones.size();
        if (n == 0) {
            return new ResumenSesion(0, null, null, null, duracionTotalMs > 0 ? 0.0 : null, null, null);
        }
        double[] d = fijaciones.stream().mapToDouble(FijacionVista::duracionMs).toArray();
        double media = media(d);
        double[] ordenadas = d.clone();
        java.util.Arrays.sort(ordenadas);
        double mediana = n % 2 == 1 ? ordenadas[n / 2] : (ordenadas[n / 2 - 1] + ordenadas[n / 2]) / 2.0;
        double desviacion = desviacion(d, media);
        Double tasa = duracionTotalMs > 0 ? n / (duracionTotalMs / 1000.0) : null;
        List<Double> amps = amplitudes.stream().filter(a -> a != null && !a.isNaN()).toList();
        Double ampMedia = amps.isEmpty() ? null : amps.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        return new ResumenSesion(n, media, mediana, desviacion, tasa, ampMedia, coeficienteK(d, amplitudes));
    }

    /**
     * Coeficiente K (Krejtz et al., 2016): media de z(d_i) - z(a_{i+1}). K &gt; 0 atencion focal
     * (fijaciones largas, sacadas cortas); K &lt; 0 atencion ambiental.
     */
    static Double coeficienteK(double[] duraciones, List<Double> amplitudes) {
        int pares = Math.min(duraciones.length - 1, amplitudes.size());
        if (pares < 2) {
            return null;
        }
        double[] d = new double[pares];
        double[] a = new double[pares];
        for (int i = 0; i < pares; i++) {
            Double amp = amplitudes.get(i);
            if (amp == null) {
                return null;
            }
            d[i] = duraciones[i];
            a[i] = amp;
        }
        double md = media(d);
        double ma = media(a);
        double sd = desviacion(d, md);
        double sa = desviacion(a, ma);
        if (sd == 0 || sa == 0) {
            return null;
        }
        double suma = 0;
        for (int i = 0; i < pares; i++) {
            suma += (d[i] - md) / sd - (a[i] - ma) / sa;
        }
        return suma / pares;
    }

    /**
     * Matriz de transiciones entre AOIs. Se recorre la secuencia de AOIs visitados ignorando fijaciones
     * fuera de cualquier AOI y repeticiones consecutivas del mismo AOI.
     *
     * @param aoiPorFijacion AOI asignado a cada fijacion en orden (null = fuera de AOI)
     * @param aoiIds         orden de filas/columnas de la matriz
     */
    public static int[][] matrizTransiciones(List<Long> aoiPorFijacion, List<Long> aoiIds) {
        int[][] matriz = new int[aoiIds.size()][aoiIds.size()];
        List<Long> secuencia = new ArrayList<>();
        for (Long id : aoiPorFijacion) {
            if (id != null && (secuencia.isEmpty() || !secuencia.get(secuencia.size() - 1).equals(id))) {
                secuencia.add(id);
            }
        }
        for (int i = 0; i + 1 < secuencia.size(); i++) {
            int desde = aoiIds.indexOf(secuencia.get(i));
            int hacia = aoiIds.indexOf(secuencia.get(i + 1));
            if (desde >= 0 && hacia >= 0) {
                matriz[desde][hacia]++;
            }
        }
        return matriz;
    }

    private static double media(double[] v) {
        double s = 0;
        for (double x : v) {
            s += x;
        }
        return s / v.length;
    }

    private static double desviacion(double[] v, double media) {
        if (v.length < 2) {
            return 0;
        }
        double s = 0;
        for (double x : v) {
            s += (x - media) * (x - media);
        }
        return Math.sqrt(s / (v.length - 1));
    }
}
