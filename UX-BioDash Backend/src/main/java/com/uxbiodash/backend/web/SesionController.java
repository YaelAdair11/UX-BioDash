package com.uxbiodash.backend.web;

import com.uxbiodash.backend.service.AyudaService;
import com.uxbiodash.backend.service.MetricasService;
import com.uxbiodash.backend.service.SesionService;
import com.uxbiodash.backend.service.VisualizacionService;
import com.uxbiodash.backend.web.dto.ApiDtos.FijacionResponse;
import com.uxbiodash.backend.web.dto.ApiDtos.MetricasSesionResponse;
import com.uxbiodash.backend.web.dto.ApiDtos.MuestraResponse;
import com.uxbiodash.backend.web.dto.ApiDtos.PaginaResponse;
import com.uxbiodash.backend.web.dto.ApiDtos.SacadaResponse;
import com.uxbiodash.backend.web.dto.ApiDtos.ScanpathResponse;
import com.uxbiodash.backend.web.dto.ApiDtos.SesionResponse;
import com.uxbiodash.backend.web.dto.ApiDtos.TarjetaAyuda;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class SesionController {

    private final SesionService sesiones;
    private final MetricasService metricas;
    private final VisualizacionService visualizacion;
    private final AyudaService ayuda;

    public SesionController(SesionService sesiones, MetricasService metricas, VisualizacionService visualizacion,
                            AyudaService ayuda) {
        this.sesiones = sesiones;
        this.metricas = metricas;
        this.visualizacion = visualizacion;
        this.ayuda = ayuda;
    }

    @GetMapping("/sesiones/{id}")
    public SesionResponse obtener(@PathVariable Long id) {
        return sesiones.obtener(id);
    }

    @DeleteMapping("/sesiones/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminar(@PathVariable Long id) {
        sesiones.eliminar(id);
    }

    @GetMapping("/sesiones/{id}/fijaciones")
    public List<FijacionResponse> fijaciones(@PathVariable Long id) {
        return sesiones.fijaciones(id);
    }

    @GetMapping("/sesiones/{id}/sacadas")
    public List<SacadaResponse> sacadas(@PathVariable Long id) {
        return sesiones.sacadas(id);
    }

    @GetMapping("/sesiones/{id}/muestras")
    public PaginaResponse<MuestraResponse> muestras(@PathVariable Long id,
                                                    @RequestParam(defaultValue = "0") int pagina,
                                                    @RequestParam(defaultValue = "5000") int tamano) {
        return sesiones.muestras(id, pagina, tamano);
    }

    @GetMapping("/sesiones/{id}/scanpath")
    public ScanpathResponse scanpath(@PathVariable Long id) {
        return visualizacion.scanpath(id);
    }

    @GetMapping("/sesiones/{id}/metricas")
    public MetricasSesionResponse metricas(@PathVariable Long id) {
        return metricas.metricasSesion(id);
    }

    // ---------------------------------------------------------------- Ayuda

    @GetMapping("/ayuda/visualizaciones")
    public List<TarjetaAyuda> tarjetas() {
        return ayuda.listar();
    }

    @GetMapping("/ayuda/visualizaciones/{clave}")
    public TarjetaAyuda tarjeta(@PathVariable String clave) {
        return ayuda.obtener(clave);
    }
}
