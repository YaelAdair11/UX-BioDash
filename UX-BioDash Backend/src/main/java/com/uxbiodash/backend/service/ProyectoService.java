package com.uxbiodash.backend.service;

import com.uxbiodash.backend.domain.EstadoProyecto;
import com.uxbiodash.backend.domain.Estimulo;
import com.uxbiodash.backend.domain.Proyecto;
import com.uxbiodash.backend.exception.RecursoNoEncontradoException;
import com.uxbiodash.backend.repository.AreaInteresRepository;
import com.uxbiodash.backend.repository.EstimuloRepository;
import com.uxbiodash.backend.repository.PuntoGazeMuestraRepository;
import com.uxbiodash.backend.repository.ProyectoRepository;
import com.uxbiodash.backend.repository.SesionEvaluacionRepository;
import com.uxbiodash.backend.web.dto.ApiDtos.ProyectoRequest;
import com.uxbiodash.backend.web.dto.ApiDtos.ProyectoResponse;
import com.uxbiodash.backend.web.dto.ApiDtos.ResumenGlobal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProyectoService {

    private static final List<String> HARDWARE = List.of("Tobii Pro", "EyeLink 1000", "Gazepoint GP3");

    private final ProyectoRepository repositorio;
    private final EstimuloRepository estimulos;
    private final AreaInteresRepository aois;
    private final SesionEvaluacionRepository sesiones;
    private final PuntoGazeMuestraRepository muestras;

    public ProyectoService(ProyectoRepository repositorio, EstimuloRepository estimulos, AreaInteresRepository aois,
                           SesionEvaluacionRepository sesiones, PuntoGazeMuestraRepository muestras) {
        this.repositorio = repositorio;
        this.estimulos = estimulos;
        this.aois = aois;
        this.sesiones = sesiones;
        this.muestras = muestras;
    }

    @Transactional(readOnly = true)
    public List<ProyectoResponse> listar() {
        return repositorio.findAllByOrderByCreadoEnDesc().stream().map(this::respuesta).toList();
    }

    @Transactional(readOnly = true)
    public ProyectoResponse obtener(Long id) {
        return respuesta(buscar(id));
    }

    @Transactional
    public ProyectoResponse crear(ProyectoRequest req) {
        Proyecto p = new Proyecto();
        aplicar(p, req);
        return respuesta(repositorio.save(p));
    }

    @Transactional
    public ProyectoResponse actualizar(Long id, ProyectoRequest req) {
        Proyecto p = buscar(id);
        aplicar(p, req);
        return respuesta(p);
    }

    @Transactional
    public void eliminar(Long id) {
        repositorio.delete(buscar(id));
    }

    /** KPIs de la pantalla de inicio (GET /api/proyectos/resumen). */
    @Transactional(readOnly = true)
    public ResumenGlobal resumen() {
        List<Proyecto> todos = repositorio.findAll();
        long activos = todos.size();
        long enAnalisis = todos.stream().filter(p -> p.getEstado() == EstadoProyecto.EN_ANALISIS
                || p.getEstado() == EstadoProyecto.EN_INGESTA).count();
        long sujetos = todos.stream().mapToLong(p -> sesiones.contarSujetos(p.getId())).sum();
        double promedio = activos > 0 ? (double) sujetos / activos : 0.0;
        return new ResumenGlobal(activos, enAnalisis, sujetos, promedio, muestras.contarTodas(), HARDWARE);
    }

    /**
     * Ciclo de vida del proyecto, derivado de sus datos (no hay boton manual):
     * <ul>
     *   <li>BORRADOR: todavia sin sesiones de eye tracking.</li>
     *   <li>EN_ANALISIS: hay datos procesados pero el estimulo principal aun no tiene AOIs.</li>
     *   <li>COMPLETADO: hay datos y al menos una AOI, asi que las metricas por zona ya estan calculadas.</li>
     * </ul>
     * Se llama tras cada carga de datos y tras crear o eliminar una AOI.
     */
    @Transactional
    public void actualizarEstado(Long proyectoId) {
        Proyecto p = buscar(proyectoId);
        EstadoProyecto nuevo;
        if (sesiones.contarSujetos(proyectoId) == 0) {
            nuevo = EstadoProyecto.BORRADOR;
        } else {
            boolean conAois = estimulos.findFirstByProyectoIdOrderByIdDesc(proyectoId)
                    .map(e -> !aois.findByEstimuloIdOrderByIdAsc(e.getId()).isEmpty())
                    .orElse(false);
            nuevo = conAois ? EstadoProyecto.COMPLETADO : EstadoProyecto.EN_ANALISIS;
        }
        p.setEstado(nuevo);
        p.setModificadoEn(LocalDateTime.now());
    }

    Proyecto buscar(Long id) {
        return repositorio.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("Proyecto", id));
    }

    private void aplicar(Proyecto p, ProyectoRequest req) {
        p.setNombre(req.nombre().trim());
        p.setDescripcion(req.descripcion());
        p.setDispositivo(req.dispositivo() == null || req.dispositivo().isBlank() ? null : req.dispositivo().trim());
        p.setModificadoEn(LocalDateTime.now());
    }

    /** Proyecto con los agregados que muestra el frontend (participantes, AOIs, duracion media). */
    ProyectoResponse respuesta(Proyecto p) {
        Optional<Estimulo> principal = estimulos.findFirstByProyectoIdOrderByIdDesc(p.getId());
        int aoisConfiguradas = principal.map(e -> aois.findByEstimuloIdOrderByIdAsc(e.getId()).size()).orElse(0);
        int participantes = (int) sesiones.contarSujetos(p.getId());
        Double media = sesiones.duracionMediaProyecto(p.getId());
        Integer mediaMs = media == null ? null : (int) Math.round(media);
        return Mapeador.proyecto(p, participantes, aoisConfiguradas, mediaMs,
                principal.map(Mapeador::estimulo).orElse(null));
    }
}
