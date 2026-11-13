package com.uxbiodash.backend.service;

import com.uxbiodash.backend.domain.AreaInteres;
import com.uxbiodash.backend.domain.Estimulo;
import com.uxbiodash.backend.domain.Fijacion;
import com.uxbiodash.backend.domain.MetricaAoiResultado;
import com.uxbiodash.backend.domain.Sacada;
import com.uxbiodash.backend.domain.SesionEvaluacion;
import com.uxbiodash.backend.exception.RecursoNoEncontradoException;
import com.uxbiodash.backend.metricas.CalculadoraMetricas;
import com.uxbiodash.backend.metricas.CalculadoraMetricas.FijacionVista;
import com.uxbiodash.backend.metricas.CalculadoraMetricas.MetricasAoi;
import com.uxbiodash.backend.metricas.CalculadoraMetricas.ResumenSesion;
import com.uxbiodash.backend.metricas.GeometriaAoi;
import com.uxbiodash.backend.repository.AreaInteresRepository;
import com.uxbiodash.backend.repository.EstimuloRepository;
import com.uxbiodash.backend.repository.FijacionRepository;
import com.uxbiodash.backend.repository.MetricaAoiResultadoRepository;
import com.uxbiodash.backend.repository.SacadaRepository;
import com.uxbiodash.backend.repository.SesionEvaluacionRepository;
import com.uxbiodash.backend.web.dto.ApiDtos.AoiEtiqueta;
import com.uxbiodash.backend.web.dto.ApiDtos.ComparativaEstimuloResponse;
import com.uxbiodash.backend.web.dto.ApiDtos.MetricaAoiResponse;
import com.uxbiodash.backend.web.dto.ApiDtos.MetricasSesionResponse;
import com.uxbiodash.backend.web.dto.ApiDtos.ResumenAoiResponse;
import com.uxbiodash.backend.web.dto.ApiDtos.ResumenParticipanteResponse;
import com.uxbiodash.backend.web.dto.ApiDtos.TransicionesResponse;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalDouble;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Calculo y consulta de metricas. Las metricas por AOI se persisten en metrica_aoi_resultado para no
 * recalcular en cada vista del panel; los resumenes por sesion se derivan de las fijaciones guardadas.
 */
@Service
public class MetricasService {

    private final SesionEvaluacionRepository sesiones;
    private final EstimuloRepository estimulos;
    private final AreaInteresRepository aois;
    private final FijacionRepository fijaciones;
    private final SacadaRepository sacadas;
    private final MetricaAoiResultadoRepository resultados;
    private final CodificadorAoi codificador;

    public MetricasService(SesionEvaluacionRepository sesiones, EstimuloRepository estimulos,
                           AreaInteresRepository aois, FijacionRepository fijaciones, SacadaRepository sacadas,
                           MetricaAoiResultadoRepository resultados, CodificadorAoi codificador) {
        this.sesiones = sesiones;
        this.estimulos = estimulos;
        this.aois = aois;
        this.fijaciones = fijaciones;
        this.sacadas = sacadas;
        this.resultados = resultados;
        this.codificador = codificador;
    }

    // ================================================================ Recalculo

    @Transactional
    public void recalcularEstimulo(Long estimuloId) {
        recalcularSesiones(estimuloId, sesiones.findByEstimuloIdOrderByCodigoSujetoAscIdAsc(estimuloId));
    }

    @Transactional
    public void recalcularSesiones(Long estimuloId, List<SesionEvaluacion> lista) {
        if (lista.isEmpty()) {
            return;
        }
        List<AreaInteres> listaAois = aois.findByEstimuloIdOrderByIdAsc(estimuloId);
        Map<Long, GeometriaAoi> geometrias = codificador.geometrias(listaAois);
        Map<Long, AreaInteres> porId = listaAois.stream()
                .collect(Collectors.toMap(AreaInteres::getId, Function.identity()));

        List<Long> ids = lista.stream().map(SesionEvaluacion::getId).toList();
        resultados.borrarPorSesiones(ids);

        List<MetricaAoiResultado> nuevos = new ArrayList<>();
        for (SesionEvaluacion sesion : lista) {
            List<Fijacion> fs = fijaciones.findBySesionIdOrderByOrdenSecuenciaAsc(sesion.getId());
            for (Fijacion f : fs) {
                Long aoiId = CalculadoraMetricas.aoiDeFijacion(f.getXNormalizado(), f.getYNormalizado(), geometrias);
                f.setAoi(aoiId != null ? porId.get(aoiId) : null);
            }
            List<FijacionVista> vistas = fs.stream().map(Mapeador::vista).toList();
            int duracion = sesion.getDuracionTotalMs() != null ? sesion.getDuracionTotalMs() : 0;
            for (AreaInteres aoi : listaAois) {
                MetricasAoi m = CalculadoraMetricas.metricasAoi(vistas, geometrias.get(aoi.getId()), duracion);
                MetricaAoiResultado r = new MetricaAoiResultado();
                r.setAoi(aoi);
                r.setSesion(sesion);
                r.setConteoFijaciones(m.conteoFijaciones());
                r.setDuracionMediaFijacionMs(m.duracionMediaFijacionMs());
                r.setDwellTimeMs(m.dwellTimeMs());
                r.setPorcentajeDwell(m.porcentajeDwell());
                r.setTtffMs(m.ttffMs());
                r.setNumeroVisitas(m.numeroVisitas());
                nuevos.add(r);
            }
        }
        resultados.saveAll(nuevos);
    }

    @Transactional
    public void desasignarAoi(Long aoiId) {
        fijaciones.desasignarAoi(aoiId);
    }

    // ================================================================= Consultas

    /** Tabla por participante y por zona. */
    @Transactional(readOnly = true)
    public List<MetricaAoiResponse> tablaAoi(Long estimuloId, String codigoSujeto) {
        estimulo(estimuloId);
        return resultados.buscarPorEstimulo(estimuloId).stream()
                .filter(m -> codigoSujeto == null || codigoSujeto.isBlank()
                        || m.getSesion().getCodigoSujeto().equalsIgnoreCase(codigoSujeto.trim()))
                .map(Mapeador::metrica).toList();
    }

    /** Comparacion entre areas: agregados de cada AOI sobre todos los participantes. */
    @Transactional(readOnly = true)
    public List<ResumenAoiResponse> resumenAoi(Long estimuloId) {
        estimulo(estimuloId);
        Map<Long, List<MetricaAoiResultado>> porAoi = resultados.buscarPorEstimulo(estimuloId).stream()
                .collect(Collectors.groupingBy(m -> m.getAoi().getId()));
        List<ResumenAoiResponse> r = new ArrayList<>();
        for (AreaInteres aoi : aois.findByEstimuloIdOrderByIdAsc(estimuloId)) {
            List<MetricaAoiResultado> ms = porAoi.getOrDefault(aoi.getId(), List.of());
            int participantes = ms.size();
            List<Integer> ttffs = ms.stream().map(MetricaAoiResultado::getTtffMs).filter(Objects::nonNull).toList();
            r.add(new ResumenAoiResponse(aoi.getId(), aoi.getEtiqueta(), participantes, ttffs.size(),
                    participantes > 0 ? 100.0 * ttffs.size() / participantes : null,
                    ms.stream().mapToInt(MetricaAoiResultado::getConteoFijaciones).sum(),
                    media(ms.stream().mapToDouble(MetricaAoiResultado::getDwellTimeMs).average()),
                    media(ttffs.stream().mapToDouble(Integer::doubleValue).average()),
                    ttffs.stream().min(Integer::compare).orElse(null),
                    media(ms.stream().mapToDouble(MetricaAoiResultado::getNumeroVisitas).average())));
        }
        return r;
    }

    /** Comparacion entre participantes: resumen global de cada sesion del estimulo. */
    @Transactional(readOnly = true)
    public List<ResumenParticipanteResponse> resumenParticipantes(Long estimuloId) {
        estimulo(estimuloId);
        List<SesionEvaluacion> lista = sesiones.findByEstimuloIdOrderByCodigoSujetoAscIdAsc(estimuloId);
        Map<Long, ResumenSesion> resumenes = resumenes(lista);
        return lista.stream().map(s -> new ResumenParticipanteResponse(s.getId(), s.getCodigoSujeto(),
                s.getDispositivoOrigen(), s.getDuracionTotalMs(), resumenes.get(s.getId()))).toList();
    }

    @Transactional(readOnly = true)
    public MetricasSesionResponse metricasSesion(Long sesionId) {
        SesionEvaluacion s = sesiones.findById(sesionId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Sesion", sesionId));
        ResumenSesion resumen = resumenes(List.of(s)).get(s.getId());
        List<MetricaAoiResponse> ms = resultados.buscarPorSesion(sesionId).stream().map(Mapeador::metrica).toList();
        return new MetricasSesionResponse(Mapeador.sesion(s), resumen, ms);
    }

    /** Matriz de transiciones entre AOIs, sumada sobre las sesiones indicadas (o todas). */
    @Transactional(readOnly = true)
    public TransicionesResponse transiciones(Long estimuloId, List<Long> sesionIds) {
        estimulo(estimuloId);
        List<AreaInteres> listaAois = aois.findByEstimuloIdOrderByIdAsc(estimuloId);
        List<Long> aoiIds = listaAois.stream().map(AreaInteres::getId).toList();
        List<SesionEvaluacion> lista = filtrarSesiones(estimuloId, sesionIds);
        int k = aoiIds.size();
        int[][] total = new int[k][k];
        List<Long> ids = lista.stream().map(SesionEvaluacion::getId).toList();
        Map<Long, List<Fijacion>> porSesion = ids.isEmpty() ? Map.of()
                : fijaciones.findBySesionIdInOrderBySesionIdAscOrdenSecuenciaAsc(ids).stream()
                        .collect(Collectors.groupingBy(f -> f.getSesion().getId()));
        for (Long id : ids) {
            List<Long> secuencia = porSesion.getOrDefault(id, List.of()).stream()
                    .map(f -> f.getAoi() != null ? f.getAoi().getId() : null).toList();
            int[][] m = CalculadoraMetricas.matrizTransiciones(secuencia, aoiIds);
            for (int i = 0; i < k; i++) {
                for (int j = 0; j < k; j++) {
                    total[i][j] += m[i][j];
                }
            }
        }
        double[][] prob = new double[k][k];
        for (int i = 0; i < k; i++) {
            int fila = 0;
            for (int j = 0; j < k; j++) {
                fila += total[i][j];
            }
            for (int j = 0; j < k; j++) {
                prob[i][j] = fila > 0 ? (double) total[i][j] / fila : 0.0;
            }
        }
        return new TransicionesResponse(
                listaAois.stream().map(a -> new AoiEtiqueta(a.getId(), a.getEtiqueta())).toList(),
                total, prob, ids.size());
    }

    /** Comparacion entre condiciones (p. ej. version A vs B): un resumen por estimulo del proyecto. */
    @Transactional(readOnly = true)
    public List<ComparativaEstimuloResponse> comparativaProyecto(Long proyectoId) {
        List<ComparativaEstimuloResponse> r = new ArrayList<>();
        for (Estimulo e : estimulos.findByProyectoIdOrderByIdAsc(proyectoId)) {
            List<SesionEvaluacion> lista = sesiones.findByEstimuloIdOrderByCodigoSujetoAscIdAsc(e.getId());
            Collection<ResumenSesion> rs = resumenes(lista).values();
            r.add(new ComparativaEstimuloResponse(e.getId(), e.getNombreArchivo(), lista.size(),
                    media(rs.stream().mapToDouble(ResumenSesion::totalFijaciones).average()),
                    media(rs.stream().map(ResumenSesion::duracionMediaFijacionMs).filter(Objects::nonNull)
                            .mapToDouble(Double::doubleValue).average()),
                    media(lista.stream().map(SesionEvaluacion::getDuracionTotalMs).filter(Objects::nonNull)
                            .mapToDouble(Integer::doubleValue).average())));
        }
        return r;
    }

    // ================================================================ Utilidades

    List<SesionEvaluacion> filtrarSesiones(Long estimuloId, List<Long> sesionIds) {
        List<SesionEvaluacion> todas = sesiones.findByEstimuloIdOrderByCodigoSujetoAscIdAsc(estimuloId);
        if (sesionIds == null || sesionIds.isEmpty()) {
            return todas;
        }
        return todas.stream().filter(s -> sesionIds.contains(s.getId())).toList();
    }

    private Map<Long, ResumenSesion> resumenes(List<SesionEvaluacion> lista) {
        Map<Long, ResumenSesion> r = new HashMap<>();
        if (lista.isEmpty()) {
            return r;
        }
        List<Long> ids = lista.stream().map(SesionEvaluacion::getId).toList();
        Map<Long, List<Fijacion>> fs = fijaciones.findBySesionIdInOrderBySesionIdAscOrdenSecuenciaAsc(ids).stream()
                .collect(Collectors.groupingBy(f -> f.getSesion().getId()));
        Map<Long, Double> amplitudPorOrigen = sacadas.buscarPorSesiones(ids).stream()
                .filter(s -> s.getAmplitudGrados() != null)
                .collect(Collectors.toMap(s -> s.getFijacionOrigen().getId(), Sacada::getAmplitudGrados, (a, b) -> a));
        for (SesionEvaluacion s : lista) {
            List<Fijacion> propias = fs.getOrDefault(s.getId(), List.of());
            List<Double> amplitudes = new ArrayList<>();
            for (int i = 0; i + 1 < propias.size(); i++) {
                amplitudes.add(amplitudPorOrigen.get(propias.get(i).getId()));
            }
            int duracion = s.getDuracionTotalMs() != null ? s.getDuracionTotalMs() : 0;
            r.put(s.getId(), CalculadoraMetricas.resumenSesion(
                    propias.stream().map(Mapeador::vista).toList(), amplitudes, duracion));
        }
        return r;
    }

    private Estimulo estimulo(Long id) {
        return estimulos.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("Estimulo", id));
    }

    private static Double media(OptionalDouble v) {
        return v.isPresent() ? v.getAsDouble() : null;
    }
}
