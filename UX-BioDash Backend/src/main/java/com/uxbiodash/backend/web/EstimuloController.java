package com.uxbiodash.backend.web;

import com.uxbiodash.backend.ingesta.SistemaCoordenadas;
import com.uxbiodash.backend.ingesta.UnidadTiempo;
import com.uxbiodash.backend.service.AoiService;
import com.uxbiodash.backend.service.EstimuloService;
import com.uxbiodash.backend.service.EstimuloService.ImagenEstimulo;
import com.uxbiodash.backend.service.ImportacionService;
import com.uxbiodash.backend.service.ImportacionService.OpcionesImportacion;
import com.uxbiodash.backend.service.MetricasService;
import com.uxbiodash.backend.service.SesionService;
import com.uxbiodash.backend.service.VisualizacionService;
import com.uxbiodash.backend.web.dto.ApiDtos.AoiRequest;
import com.uxbiodash.backend.web.dto.ApiDtos.AoiResponse;
import com.uxbiodash.backend.web.dto.ApiDtos.EstimuloResponse;
import com.uxbiodash.backend.web.dto.ApiDtos.FijacionResponse;
import com.uxbiodash.backend.web.dto.ApiDtos.HeatmapResponse;
import com.uxbiodash.backend.web.dto.ApiDtos.MetricaAoiResponse;
import com.uxbiodash.backend.web.dto.ApiDtos.PuntoFijacionResponse;
import com.uxbiodash.backend.web.dto.ApiDtos.ResumenAoiResponse;
import com.uxbiodash.backend.web.dto.ApiDtos.ResumenImportacion;
import com.uxbiodash.backend.web.dto.ApiDtos.ResumenParticipanteResponse;
import com.uxbiodash.backend.web.dto.ApiDtos.SesionResponse;
import com.uxbiodash.backend.web.dto.ApiDtos.TransicionesResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api")
public class EstimuloController {

    private final EstimuloService estimulos;
    private final ImportacionService importacion;
    private final SesionService sesiones;
    private final AoiService aois;
    private final MetricasService metricas;
    private final VisualizacionService visualizacion;

    public EstimuloController(EstimuloService estimulos, ImportacionService importacion, SesionService sesiones,
                              AoiService aois, MetricasService metricas, VisualizacionService visualizacion) {
        this.estimulos = estimulos;
        this.importacion = importacion;
        this.sesiones = sesiones;
        this.aois = aois;
        this.metricas = metricas;
        this.visualizacion = visualizacion;
    }

    // ------------------------------------------------------------- Estimulos

    @PostMapping(value = "/proyectos/{proyectoId}/estimulos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public EstimuloResponse crear(@PathVariable Long proyectoId, @RequestPart("imagen") MultipartFile imagen,
                                  @RequestParam(required = false) String nombre) {
        return estimulos.crear(proyectoId, imagen, nombre);
    }

    @GetMapping("/proyectos/{proyectoId}/estimulos")
    public List<EstimuloResponse> listar(@PathVariable Long proyectoId) {
        return estimulos.listar(proyectoId);
    }

    @GetMapping("/estimulos/{id}")
    public EstimuloResponse obtener(@PathVariable Long id) {
        return estimulos.obtener(id);
    }

    @GetMapping("/estimulos/{id}/imagen")
    public ResponseEntity<byte[]> imagen(@PathVariable Long id) {
        ImagenEstimulo img = estimulos.imagen(id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(img.tipoMime()))
                .cacheControl(CacheControl.maxAge(1, TimeUnit.HOURS))
                .body(img.contenido());
    }

    @DeleteMapping("/estimulos/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminar(@PathVariable Long id) {
        estimulos.eliminar(id);
    }

    // ----------------------------------------------------------- Importacion

    /** Carga un archivo CSV/TSV de eye tracking y lo une con la imagen del estimulo. */
    @PostMapping(value = "/estimulos/{id}/importaciones", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ResumenImportacion importar(@PathVariable Long id,
                                       @RequestPart("archivo") MultipartFile archivo,
                                       @RequestParam(required = false) String codigoSujeto,
                                       @RequestParam(required = false) Integer anchoPantallaPx,
                                       @RequestParam(required = false) Integer altoPantallaPx,
                                       @RequestParam(required = false) UnidadTiempo unidadTiempo,
                                       @RequestParam(required = false) SistemaCoordenadas sistemaCoordenadas,
                                       @RequestParam(required = false) String filtroEstimulo,
                                       @RequestParam(defaultValue = "true") boolean reemplazarExistentes,
                                       @RequestParam(defaultValue = "true") boolean guardarMuestras) {
        return importacion.importar(id, archivo, new OpcionesImportacion(codigoSujeto, anchoPantallaPx,
                altoPantallaPx, unidadTiempo, sistemaCoordenadas, filtroEstimulo, reemplazarExistentes,
                guardarMuestras));
    }

    @GetMapping("/estimulos/{id}/sesiones")
    public List<SesionResponse> sesiones(@PathVariable Long id) {
        return sesiones.listarPorEstimulo(id);
    }

    // ------------------------------------------------------------------ AOI

    @GetMapping("/estimulos/{id}/aois")
    public List<AoiResponse> aois(@PathVariable Long id) {
        return aois.listar(id);
    }

    @PostMapping("/estimulos/{id}/aois")
    @ResponseStatus(HttpStatus.CREATED)
    public AoiResponse crearAoi(@PathVariable Long id, @Valid @RequestBody AoiRequest req) {
        return aois.crear(id, req);
    }

    @PutMapping("/aois/{aoiId}")
    public AoiResponse actualizarAoi(@PathVariable Long aoiId, @Valid @RequestBody AoiRequest req) {
        return aois.actualizar(aoiId, req);
    }

    @DeleteMapping("/aois/{aoiId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminarAoi(@PathVariable Long aoiId) {
        aois.eliminar(aoiId);
    }

    // -------------------------------------------------------------- Metricas

    /** Tabla por participante y por zona (conteo, duracion media, dwell, TTFF, visitas). */
    @GetMapping("/estimulos/{id}/metricas/aoi")
    public List<MetricaAoiResponse> metricasAoi(@PathVariable Long id,
                                                @RequestParam(required = false) String codigoSujeto) {
        return metricas.tablaAoi(id, codigoSujeto);
    }

    @GetMapping("/estimulos/{id}/metricas/aoi/resumen")
    public List<ResumenAoiResponse> resumenAoi(@PathVariable Long id) {
        return metricas.resumenAoi(id);
    }

    @GetMapping("/estimulos/{id}/metricas/participantes")
    public List<ResumenParticipanteResponse> participantes(@PathVariable Long id) {
        return metricas.resumenParticipantes(id);
    }

    @GetMapping("/estimulos/{id}/transiciones")
    public TransicionesResponse transiciones(@PathVariable Long id,
                                             @RequestParam(required = false) List<Long> sesionIds) {
        return metricas.transiciones(id, sesionIds);
    }

    // -------------------------------------------------------- Visualizaciones

    @GetMapping("/estimulos/{id}/heatmap")
    public HeatmapResponse heatmap(@PathVariable Long id, @RequestParam(required = false) List<Long> sesionIds,
                                   @RequestParam(required = false) Integer columnas,
                                   @RequestParam(required = false) Double sigma) {
        return visualizacion.heatmap(id, sesionIds, columnas, sigma);
    }

    /** Fijaciones del estimulo (contrato del frontend). sesionId opcional. */
    @GetMapping("/estimulos/{id}/fijaciones")
    public List<FijacionResponse> fijaciones(@PathVariable Long id,
                                             @RequestParam(required = false) Long sesionId) {
        return sesiones.fijacionesDeEstimulo(id, sesionId);
    }

    /** Distribucion de fijaciones (puntos sin conectar) de varias sesiones. */
    @GetMapping("/estimulos/{id}/distribucion")
    public List<PuntoFijacionResponse> distribucion(@PathVariable Long id,
                                                    @RequestParam(required = false) List<Long> sesionIds) {
        return visualizacion.puntos(id, sesionIds);
    }
}
