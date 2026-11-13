package com.uxbiodash.backend.web;

import com.uxbiodash.backend.service.MetricasProyectoService;
import com.uxbiodash.backend.web.dto.ApiDtos.MetricasProyecto;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class MetricasController {

    private final MetricasProyectoService metricas;

    public MetricasController(MetricasProyectoService metricas) {
        this.metricas = metricas;
    }

    /** Metricas por zona y por participante del estimulo principal del proyecto (sesionId opcional). */
    @GetMapping("/metricas/{proyectoId}")
    public MetricasProyecto metricas(@PathVariable Long proyectoId,
                                     @RequestParam(required = false) Long sesionId) {
        return metricas.calcular(proyectoId, sesionId);
    }
}
