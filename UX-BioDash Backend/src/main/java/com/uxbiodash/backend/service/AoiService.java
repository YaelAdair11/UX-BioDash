package com.uxbiodash.backend.service;

import com.uxbiodash.backend.domain.AreaInteres;
import com.uxbiodash.backend.domain.Estimulo;
import com.uxbiodash.backend.domain.FormaGeometrica;
import com.uxbiodash.backend.metricas.CoordenadasAoi;
import com.uxbiodash.backend.exception.RecursoNoEncontradoException;
import com.uxbiodash.backend.metricas.GeometriaAoi;
import com.uxbiodash.backend.repository.AreaInteresRepository;
import com.uxbiodash.backend.web.dto.ApiDtos.AoiRequest;
import com.uxbiodash.backend.web.dto.ApiDtos.AoiResponse;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Areas de interes dibujadas por el evaluador. Cada alta, cambio o baja recalcula la asignacion de
 * fijaciones y las metricas AOI del estimulo, para que el panel consulte resultados ya persistidos.
 */
@Service
public class AoiService {

    private final AreaInteresRepository aois;
    private final EstimuloService estimulos;
    private final CodificadorAoi codificador;
    private final MetricasService metricas;
    private final ProyectoService proyectos;

    public AoiService(AreaInteresRepository aois, EstimuloService estimulos, CodificadorAoi codificador,
                      MetricasService metricas, ProyectoService proyectos) {
        this.aois = aois;
        this.estimulos = estimulos;
        this.codificador = codificador;
        this.metricas = metricas;
        this.proyectos = proyectos;
    }

    @Transactional(readOnly = true)
    public List<AoiResponse> listar(Long estimuloId) {
        estimulos.buscar(estimuloId);
        return aois.findByEstimuloIdOrderByIdAsc(estimuloId).stream().map(this::respuesta).toList();
    }

    @Transactional
    public AoiResponse crear(Long estimuloId, AoiRequest req) {
        Estimulo estimulo = estimulos.buscar(estimuloId);
        GeometriaAoi.de(req.formaGeometrica(), req.coordenadas()); // valida
        AreaInteres aoi = new AreaInteres();
        aoi.setEstimulo(estimulo);
        aplicar(aoi, req);
        aoi = aois.save(aoi);
        metricas.recalcularEstimulo(estimuloId);
        proyectos.actualizarEstado(estimulo.getProyecto().getId());
        return respuesta(aoi);
    }

    @Transactional
    public AoiResponse actualizar(Long id, AoiRequest req) {
        AreaInteres aoi = buscar(id);
        GeometriaAoi.de(req.formaGeometrica(), req.coordenadas());
        aplicar(aoi, req);
        aois.flush();
        metricas.recalcularEstimulo(aoi.getEstimulo().getId());
        proyectos.actualizarEstado(aoi.getEstimulo().getProyecto().getId());
        return respuesta(aoi);
    }

    @Transactional
    public void eliminar(Long id) {
        AreaInteres aoi = buscar(id);
        Long estimuloId = aoi.getEstimulo().getId();
        Long proyectoId = aoi.getEstimulo().getProyecto().getId();
        metricas.desasignarAoi(id);
        aois.delete(aoi);
        aois.flush();
        metricas.recalcularEstimulo(estimuloId);
        proyectos.actualizarEstado(proyectoId);
    }

    private void aplicar(AreaInteres aoi, AoiRequest req) {
        aoi.setEtiqueta(req.etiqueta().trim());
        aoi.setFormaGeometrica(req.formaGeometrica());
        aoi.setCoordenadasJson(codificador.aJson(conCajaEnvolvente(req)));
        aoi.setColor(req.color() == null || req.color().isBlank() ? "#3b82f6" : req.color().trim());
    }

    /**
     * Contrato del frontend: un POLIGONO siempre lleva su caja envolvente (x, y, ancho, alto) ademas de los
     * puntos. Se recalcula aqui a partir de los vertices para que nunca quede desalineada.
     */
    private static CoordenadasAoi conCajaEnvolvente(AoiRequest req) {
        CoordenadasAoi c = req.coordenadas();
        if (req.formaGeometrica() != FormaGeometrica.POLIGONO) {
            return CoordenadasAoi.rectangulo(c.x(), c.y(), c.ancho(), c.alto());
        }
        double minX = 1, minY = 1, maxX = 0, maxY = 0;
        for (CoordenadasAoi.Punto p : c.puntos()) {
            minX = Math.min(minX, p.x());
            minY = Math.min(minY, p.y());
            maxX = Math.max(maxX, p.x());
            maxY = Math.max(maxY, p.y());
        }
        return new CoordenadasAoi(minX, minY, maxX - minX, maxY - minY, List.copyOf(c.puntos()));
    }

    private AreaInteres buscar(Long id) {
        return aois.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("Area de interes", id));
    }

    private AoiResponse respuesta(AreaInteres a) {
        return new AoiResponse(a.getId(), a.getEstimulo().getId(), a.getEtiqueta(), a.getFormaGeometrica(),
                codificador.coordenadas(a), a.getColor());
    }
}
