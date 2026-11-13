package com.uxbiodash.backend.service;

import com.uxbiodash.backend.domain.AreaInteres;
import com.uxbiodash.backend.domain.Estimulo;
import com.uxbiodash.backend.domain.Fijacion;
import com.uxbiodash.backend.domain.SesionEvaluacion;
import com.uxbiodash.backend.exception.RecursoNoEncontradoException;
import com.uxbiodash.backend.repository.AreaInteresRepository;
import com.uxbiodash.backend.repository.EstimuloRepository;
import com.uxbiodash.backend.repository.FijacionRepository;
import com.uxbiodash.backend.repository.ProyectoRepository;
import com.uxbiodash.backend.repository.SesionEvaluacionRepository;
import com.uxbiodash.backend.web.dto.ApiDtos.MetricaAoiProyecto;
import com.uxbiodash.backend.web.dto.ApiDtos.MetricaParticipante;
import com.uxbiodash.backend.web.dto.ApiDtos.MetricasProyecto;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Metricas del contrato del frontend (GET /api/metricas/{proyectoId}). Se calculan sobre las fijaciones
 * del estimulo principal del proyecto, con las definiciones de docs/API_CONTRACT.md:
 * TTFF promediado entre participantes, dwell = suma de duraciones de fijaciones dentro del AOI,
 * revisitas = entradas - 1 por sesion, y una fijacion pertenece al AOI mas pequeno que la contiene.
 */
@Service
public class MetricasProyectoService {

    private static final String FUERA = "Fuera de las AOIs";

    private final ProyectoRepository proyectos;
    private final EstimuloRepository estimulos;
    private final AreaInteresRepository aois;
    private final SesionEvaluacionRepository sesiones;
    private final FijacionRepository fijaciones;

    public MetricasProyectoService(ProyectoRepository proyectos, EstimuloRepository estimulos,
                                   AreaInteresRepository aois, SesionEvaluacionRepository sesiones,
                                   FijacionRepository fijaciones) {
        this.proyectos = proyectos;
        this.estimulos = estimulos;
        this.aois = aois;
        this.sesiones = sesiones;
        this.fijaciones = fijaciones;
    }

    @Transactional(readOnly = true)
    public MetricasProyecto calcular(Long proyectoId, Long sesionId) {
        if (!proyectos.existsById(proyectoId)) {
            throw new RecursoNoEncontradoException("Proyecto", proyectoId);
        }
        Optional<Estimulo> principal = estimulos.findFirstByProyectoIdOrderByIdDesc(proyectoId);
        if (principal.isEmpty()) {
            return new MetricasProyecto(proyectoId, null, sesionId, 0, 0, 0, List.of(), List.of());
        }
        Long estimuloId = principal.get().getId();
        List<SesionEvaluacion> lista = sesiones.findByEstimuloIdOrderByCodigoSujetoAscIdAsc(estimuloId).stream()
                .filter(s -> sesionId == null || s.getId().equals(sesionId))
                .toList();
        List<AreaInteres> listaAois = aois.findByEstimuloIdOrderByIdAsc(estimuloId);

        Map<Long, List<Fijacion>> porSesion = new LinkedHashMap<>();
        for (SesionEvaluacion s : lista) {
            porSesion.put(s.getId(), new ArrayList<>());
        }
        if (!porSesion.isEmpty()) {
            for (Fijacion f : fijaciones.findBySesionIdInOrderBySesionIdAscOrdenSecuenciaAsc(porSesion.keySet())) {
                porSesion.get(f.getSesion().getId()).add(f);
            }
        }

        long duracionTotalFijaciones = 0;
        int totalFijaciones = 0;
        for (List<Fijacion> fs : porSesion.values()) {
            totalFijaciones += fs.size();
            for (Fijacion f : fs) {
                duracionTotalFijaciones += f.getDuracionMs();
            }
        }
        int duracionMaxima = lista.stream()
                .mapToInt(s -> s.getDuracionTotalMs() == null ? 0 : s.getDuracionTotalMs())
                .max().orElse(0);

        List<MetricaAoiProyecto> porAoi = new ArrayList<>();
        for (AreaInteres a : listaAois) {
            porAoi.add(metricaAoi(a.getId(), a.getEtiqueta(), porSesion, duracionTotalFijaciones));
        }
        porAoi.add(metricaAoi(null, FUERA, porSesion, duracionTotalFijaciones));

        List<MetricaParticipante> porParticipante = new ArrayList<>();
        for (SesionEvaluacion s : lista) {
            porParticipante.add(participante(s, porSesion.get(s.getId()), listaAois));
        }
        return new MetricasProyecto(proyectoId, estimuloId, sesionId, totalFijaciones, lista.size(), duracionMaxima,
                porAoi, porParticipante);
    }

    private static boolean dentro(Fijacion f, Long aoiId) {
        if (aoiId == null) {
            return f.getAoi() == null;
        }
        return f.getAoi() != null && f.getAoi().getId().equals(aoiId);
    }

    private static MetricaAoiProyecto metricaAoi(Long aoiId, String etiqueta, Map<Long, List<Fijacion>> porSesion,
                                                 long duracionTotal) {
        long conteo = 0;
        long dwell = 0;
        int revisitas = 0;
        List<Integer> ttffs = new ArrayList<>();
        for (List<Fijacion> fs : porSesion.values()) {
            int entradas = 0;
            Integer primera = null;
            boolean estabaDentro = false;
            for (Fijacion f : fs) {
                boolean d = dentro(f, aoiId);
                if (d) {
                    conteo++;
                    dwell += f.getDuracionMs();
                    if (primera == null) {
                        primera = f.getInicioMs();
                    }
                    if (!estabaDentro) {
                        entradas++;
                    }
                }
                estabaDentro = d;
            }
            if (primera != null) {
                ttffs.add(primera);
            }
            revisitas += Math.max(entradas - 1, 0);
        }
        Integer ttff = ttffs.isEmpty() ? null
                : (int) Math.round(ttffs.stream().mapToInt(Integer::intValue).average().orElse(0));
        Double dwellPct = duracionTotal > 0 ? 100.0 * dwell / duracionTotal : null;
        Double duracionMedia = conteo > 0 ? (double) dwell / conteo : null;
        if (aoiId == null) {
            return new MetricaAoiProyecto(null, etiqueta, null, dwell, dwellPct, (int) conteo, null, duracionMedia);
        }
        return new MetricaAoiProyecto(aoiId, etiqueta, ttff, dwell, dwellPct, (int) conteo, revisitas, duracionMedia);
    }

    private static MetricaParticipante participante(SesionEvaluacion s, List<Fijacion> fs,
                                                    List<AreaInteres> listaAois) {
        List<Fijacion> propias = fs == null ? List.of() : fs;
        Double media = propias.isEmpty() ? null
                : propias.stream().mapToInt(Fijacion::getDuracionMs).average().orElse(0);
        Map<String, Integer> ttffPorAoi = new LinkedHashMap<>();
        for (AreaInteres a : listaAois) {
            Integer t = propias.stream()
                    .filter(f -> dentro(f, a.getId()))
                    .map(Fijacion::getInicioMs)
                    .findFirst()
                    .orElse(null);
            ttffPorAoi.put(String.valueOf(a.getId()), t);
        }
        return new MetricaParticipante(s.getId(), s.getCodigoSujeto(), propias.size(), media,
                s.getDuracionTotalMs(), ttffPorAoi);
    }
}
