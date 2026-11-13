package com.uxbiodash.backend.service;

import com.uxbiodash.backend.domain.PuntoGazeMuestra;
import com.uxbiodash.backend.domain.SesionEvaluacion;
import com.uxbiodash.backend.exception.RecursoNoEncontradoException;
import com.uxbiodash.backend.exception.SolicitudInvalidaException;
import com.uxbiodash.backend.repository.FijacionRepository;
import com.uxbiodash.backend.repository.PuntoGazeMuestraRepository;
import com.uxbiodash.backend.repository.SacadaRepository;
import com.uxbiodash.backend.repository.SesionEvaluacionRepository;
import com.uxbiodash.backend.web.dto.ApiDtos.FijacionResponse;
import com.uxbiodash.backend.web.dto.ApiDtos.MuestraResponse;
import com.uxbiodash.backend.web.dto.ApiDtos.PaginaResponse;
import com.uxbiodash.backend.web.dto.ApiDtos.SacadaResponse;
import com.uxbiodash.backend.web.dto.ApiDtos.SesionResponse;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SesionService {

    private final SesionEvaluacionRepository sesiones;
    private final FijacionRepository fijaciones;
    private final SacadaRepository sacadas;
    private final PuntoGazeMuestraRepository muestras;
    private final EstimuloService estimulos;
    private final ProyectoService proyectos;

    public SesionService(SesionEvaluacionRepository sesiones, FijacionRepository fijaciones,
                         SacadaRepository sacadas, PuntoGazeMuestraRepository muestras, EstimuloService estimulos,
                         ProyectoService proyectos) {
        this.sesiones = sesiones;
        this.fijaciones = fijaciones;
        this.sacadas = sacadas;
        this.muestras = muestras;
        this.estimulos = estimulos;
        this.proyectos = proyectos;
    }

    @Transactional(readOnly = true)
    public List<SesionResponse> listarPorEstimulo(Long estimuloId) {
        estimulos.buscar(estimuloId);
        return sesiones.findByEstimuloIdOrderByCodigoSujetoAscIdAsc(estimuloId).stream().map(Mapeador::sesion).toList();
    }

    @Transactional(readOnly = true)
    public List<SesionResponse> listarPorProyecto(Long proyectoId) {
        proyectos.buscar(proyectoId);
        return sesiones.findByProyectoIdOrderByCodigoSujetoAscIdAsc(proyectoId).stream().map(Mapeador::sesion).toList();
    }

    @Transactional(readOnly = true)
    public SesionResponse obtener(Long id) {
        return Mapeador.sesion(buscar(id));
    }

    @Transactional(readOnly = true)
    public List<FijacionResponse> fijaciones(Long id) {
        buscar(id);
        return fijaciones.findBySesionIdOrderByOrdenSecuenciaAsc(id).stream().map(Mapeador::fijacion).toList();
    }

    @Transactional(readOnly = true)
    public List<SacadaResponse> sacadas(Long id) {
        buscar(id);
        return sacadas.buscarPorSesion(id).stream().map(Mapeador::sacada).toList();
    }

    @Transactional(readOnly = true)
    public PaginaResponse<MuestraResponse> muestras(Long id, int pagina, int tamano) {
        buscar(id);
        if (pagina < 0 || tamano < 1 || tamano > 50_000) {
            throw new SolicitudInvalidaException("Paginacion invalida: pagina >= 0 y 1 <= tamano <= 50000.");
        }
        Page<PuntoGazeMuestra> p = muestras.findBySesionIdOrderByTiempoMsAsc(id, PageRequest.of(pagina, tamano));
        return new PaginaResponse<>(p.getContent().stream()
                .map(m -> new MuestraResponse(m.getTiempoMs(), m.getXNormalizado(), m.getYNormalizado(),
                        m.getDiametroPupila()))
                .toList(), p.getNumber(), p.getSize(), p.getTotalElements(), p.getTotalPages());
    }

    @Transactional
    public void eliminar(Long id) {
        sesiones.delete(buscar(id));
    }

    /** GET /estimulos/{id}/fijaciones: fijaciones del estimulo, o solo las de una sesion si se indica. */
    @Transactional(readOnly = true)
    public List<FijacionResponse> fijacionesDeEstimulo(Long estimuloId, Long sesionId) {
        estimulos.buscar(estimuloId);
        List<com.uxbiodash.backend.domain.Fijacion> lista = sesionId != null
                ? fijaciones.findBySesionIdOrderByOrdenSecuenciaAsc(sesionId)
                : fijaciones.findBySesionEstimuloIdOrderBySesionIdAscOrdenSecuenciaAsc(estimuloId);
        return lista.stream().map(Mapeador::fijacion).toList();
    }

    SesionEvaluacion buscar(Long id) {
        return sesiones.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("Sesion", id));
    }
}
