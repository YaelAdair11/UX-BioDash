package com.uxbiodash.backend.service;

import com.uxbiodash.backend.service.ImportacionService.OpcionesImportacion;
import com.uxbiodash.backend.web.dto.ApiDtos.EstimuloResponse;
import com.uxbiodash.backend.web.dto.ApiDtos.ResumenImportacion;
import com.uxbiodash.backend.web.dto.ApiDtos.ResumenIngesta;
import com.uxbiodash.backend.web.dto.ApiDtos.ResumenSesionImportada;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * POST /api/datasets: crea el estimulo con su captura y procesa el archivo de eye tracking.
 * Todo ocurre en una transaccion: si el archivo es invalido no queda la captura huerfana.
 */
@Service
public class DatasetService {

    private final ProyectoService proyectos;
    private final EstimuloService estimulos;
    private final ImportacionService importacion;

    public DatasetService(ProyectoService proyectos, EstimuloService estimulos, ImportacionService importacion) {
        this.proyectos = proyectos;
        this.estimulos = estimulos;
        this.importacion = importacion;
    }

    @Transactional
    public ResumenIngesta ingestar(Long proyectoId, MultipartFile archivo, MultipartFile imagen) {
        proyectos.buscar(proyectoId);
        EstimuloResponse estimulo = estimulos.crear(proyectoId, imagen, null);
        ResumenImportacion r = importacion.importar(estimulo.id(), archivo,
                new OpcionesImportacion(null, null, null, null, null, null, true, true));

        // EN_ANALISIS, o COMPLETADO si el estimulo ya tiene AOIs (ver ProyectoService.actualizarEstado).
        proyectos.actualizarEstado(proyectoId);

        int fijaciones = r.sesiones().stream().mapToInt(ResumenSesionImportada::fijaciones).sum();
        int descartadas = r.filasMalformadas()
                + r.sesiones().stream().mapToInt(ResumenSesionImportada::registrosDescartados).sum();
        return new ResumenIngesta(proyectoId, estimulo.id(), r.sesiones().size(), r.filasLeidas(), descartadas,
                fijaciones, r.advertencias());
    }
}
