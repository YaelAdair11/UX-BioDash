package com.uxbiodash.backend.service;

import com.uxbiodash.backend.domain.Estimulo;
import com.uxbiodash.backend.domain.Fijacion;
import com.uxbiodash.backend.domain.SesionEvaluacion;
import com.uxbiodash.backend.exception.SolicitudInvalidaException;
import com.uxbiodash.backend.repository.FijacionRepository;
import com.uxbiodash.backend.repository.SacadaRepository;
import com.uxbiodash.backend.web.dto.ApiDtos.HeatmapResponse;
import com.uxbiodash.backend.web.dto.ApiDtos.PuntoFijacionResponse;
import com.uxbiodash.backend.web.dto.ApiDtos.ScanpathResponse;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Datos listos para dibujar en Canvas: mapa de calor, ruta de mirada y distribucion de fijaciones. */
@Service
public class VisualizacionService {

    private final EstimuloService estimulos;
    private final SesionService sesiones;
    private final MetricasService metricas;
    private final FijacionRepository fijaciones;
    private final SacadaRepository sacadas;

    public VisualizacionService(EstimuloService estimulos, SesionService sesiones, MetricasService metricas,
                                FijacionRepository fijaciones, SacadaRepository sacadas) {
        this.estimulos = estimulos;
        this.sesiones = sesiones;
        this.metricas = metricas;
        this.fijaciones = fijaciones;
        this.sacadas = sacadas;
    }

    /**
     * Rejilla de intensidad normalizada [0, 1] ponderada por duracion de fijacion y suavizada con un
     * nucleo gaussiano. El frontend la escala al tamano real del Canvas.
     */
    @Transactional(readOnly = true)
    public HeatmapResponse heatmap(Long estimuloId, List<Long> sesionIds, Integer columnas, Double sigma) {
        Estimulo e = estimulos.buscar(estimuloId);
        int cols = columnas != null ? columnas : 64;
        double s = sigma != null ? sigma : 1.5;
        if (cols < 4 || cols > 512) {
            throw new SolicitudInvalidaException("'columnas' debe estar entre 4 y 512.");
        }
        if (s < 0 || s > 20) {
            throw new SolicitudInvalidaException("'sigma' debe estar entre 0 y 20 celdas.");
        }
        int filas = Math.max(1, (int) Math.round(cols * (double) e.getAltoOriginalPx() / e.getAnchoOriginalPx()));

        List<SesionEvaluacion> lista = metricas.filtrarSesiones(estimuloId, sesionIds);
        List<Long> ids = lista.stream().map(SesionEvaluacion::getId).toList();
        List<Fijacion> fs = ids.isEmpty() ? List.of() : fijaciones.findBySesionIdInOrderBySesionIdAscOrdenSecuenciaAsc(ids);

        double[][] bruto = new double[filas][cols];
        for (Fijacion f : fs) {
            int c = Math.min(cols - 1, (int) (f.getXNormalizado() * cols));
            int r = Math.min(filas - 1, (int) (f.getYNormalizado() * filas));
            bruto[r][c] += f.getDuracionMs();
        }
        double[][] suave = s > 0 ? suavizar(bruto, s) : bruto;
        double maxBruto = 0;
        double max = 0;
        for (int r = 0; r < filas; r++) {
            for (int c = 0; c < cols; c++) {
                maxBruto = Math.max(maxBruto, bruto[r][c]);
                max = Math.max(max, suave[r][c]);
            }
        }
        if (max > 0) {
            for (int r = 0; r < filas; r++) {
                for (int c = 0; c < cols; c++) {
                    suave[r][c] = suave[r][c] / max;
                }
            }
        }
        return new HeatmapResponse(estimuloId, cols, filas, suave, maxBruto, fs.size(), ids);
    }

    /** Distribucion de fijaciones: puntos sin conectar de las sesiones seleccionadas. */
    @Transactional(readOnly = true)
    public List<PuntoFijacionResponse> puntos(Long estimuloId, List<Long> sesionIds) {
        estimulos.buscar(estimuloId);
        List<SesionEvaluacion> lista = metricas.filtrarSesiones(estimuloId, sesionIds);
        if (lista.isEmpty()) {
            return List.of();
        }
        Map<Long, SesionEvaluacion> porId = lista.stream()
                .collect(Collectors.toMap(SesionEvaluacion::getId, Function.identity()));
        return fijaciones.findBySesionIdInOrderBySesionIdAscOrdenSecuenciaAsc(porId.keySet()).stream()
                .map(f -> new PuntoFijacionResponse(f.getSesion().getId(),
                        porId.get(f.getSesion().getId()).getCodigoSujeto(), f.getOrdenSecuencia(),
                        f.getXNormalizado(), f.getYNormalizado(), f.getDuracionMs(),
                        f.getAoi() != null ? f.getAoi().getId() : null))
                .toList();
    }

    /** Ruta de la mirada: fijaciones numeradas en orden cronologico y las sacadas que las conectan. */
    @Transactional(readOnly = true)
    public ScanpathResponse scanpath(Long sesionId) {
        SesionEvaluacion s = sesiones.buscar(sesionId);
        return new ScanpathResponse(s.getId(), s.getCodigoSujeto(), s.getEstimulo().getId(),
                fijaciones.findBySesionIdOrderByOrdenSecuenciaAsc(sesionId).stream().map(Mapeador::fijacion).toList(),
                sacadas.buscarPorSesion(sesionId).stream().map(Mapeador::sacada).toList());
    }

    /** Convolucion gaussiana separable. */
    static double[][] suavizar(double[][] m, double sigma) {
        int radio = (int) Math.ceil(3 * sigma);
        double[] k = new double[2 * radio + 1];
        double suma = 0;
        for (int i = -radio; i <= radio; i++) {
            k[i + radio] = Math.exp(-(i * i) / (2 * sigma * sigma));
            suma += k[i + radio];
        }
        for (int i = 0; i < k.length; i++) {
            k[i] /= suma;
        }
        int filas = m.length;
        int cols = m[0].length;
        double[][] tmp = new double[filas][cols];
        for (int r = 0; r < filas; r++) {
            for (int c = 0; c < cols; c++) {
                double v = 0;
                for (int i = -radio; i <= radio; i++) {
                    int cc = c + i;
                    if (cc >= 0 && cc < cols) {
                        v += m[r][cc] * k[i + radio];
                    }
                }
                tmp[r][c] = v;
            }
        }
        double[][] out = new double[filas][cols];
        for (int r = 0; r < filas; r++) {
            for (int c = 0; c < cols; c++) {
                double v = 0;
                for (int i = -radio; i <= radio; i++) {
                    int rr = r + i;
                    if (rr >= 0 && rr < filas) {
                        v += tmp[rr][c] * k[i + radio];
                    }
                }
                out[r][c] = v;
            }
        }
        return out;
    }
}
