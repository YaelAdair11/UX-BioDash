package com.uxbiodash.backend.web;

import com.uxbiodash.backend.service.MetricasService;
import com.uxbiodash.backend.service.ProyectoService;
import com.uxbiodash.backend.service.SesionService;
import com.uxbiodash.backend.web.dto.ApiDtos.ComparativaEstimuloResponse;
import com.uxbiodash.backend.web.dto.ApiDtos.ProyectoRequest;
import com.uxbiodash.backend.web.dto.ApiDtos.ResumenGlobal;
import com.uxbiodash.backend.web.dto.ApiDtos.ProyectoResponse;
import com.uxbiodash.backend.web.dto.ApiDtos.SesionResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/proyectos")
public class ProyectoController {

    private final ProyectoService proyectos;
    private final SesionService sesiones;
    private final MetricasService metricas;

    public ProyectoController(ProyectoService proyectos, SesionService sesiones, MetricasService metricas) {
        this.proyectos = proyectos;
        this.sesiones = sesiones;
        this.metricas = metricas;
    }

    @GetMapping
    public List<ProyectoResponse> listar() {
        return proyectos.listar();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProyectoResponse crear(@Valid @RequestBody ProyectoRequest req) {
        return proyectos.crear(req);
    }

    /** KPIs de la pantalla de inicio. Debe declararse antes de /{id} para que "resumen" no se tome como id. */
    @GetMapping("/resumen")
    public ResumenGlobal resumen() {
        return proyectos.resumen();
    }

    @GetMapping("/{id}")
    public ProyectoResponse obtener(@PathVariable Long id) {
        return proyectos.obtener(id);
    }

    @PutMapping("/{id}")
    public ProyectoResponse actualizar(@PathVariable Long id, @Valid @RequestBody ProyectoRequest req) {
        return proyectos.actualizar(id, req);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminar(@PathVariable Long id) {
        proyectos.eliminar(id);
    }

    @GetMapping("/{id}/sesiones")
    public List<SesionResponse> sesiones(@PathVariable Long id) {
        return sesiones.listarPorProyecto(id);
    }

    /** Comparacion entre condiciones: un resumen por cada estimulo (pantalla / version) del proyecto. */
    @GetMapping("/{id}/comparativa")
    public List<ComparativaEstimuloResponse> comparativa(@PathVariable Long id) {
        proyectos.obtener(id);
        return metricas.comparativaProyecto(id);
    }
}
