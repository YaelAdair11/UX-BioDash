package com.uxbiodash.backend.web.dto;

import com.uxbiodash.backend.domain.EstadoProyecto;
import com.uxbiodash.backend.domain.FormaGeometrica;
import com.uxbiodash.backend.metricas.CalculadoraMetricas.ResumenSesion;
import com.uxbiodash.backend.metricas.CoordenadasAoi;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** Contratos JSON de la API REST de UX-BioDash (ver docs/API_CONTRACT.md del frontend). */
public final class ApiDtos {

    private ApiDtos() {
    }

    // ------------------------------------------------------------ Proyectos

    public record ProyectoRequest(
            @NotBlank(message = "El nombre es obligatorio") @Size(max = 120) String nombre,
            String descripcion,
            String dispositivo) {
    }

    public record ProyectoResponse(Long id, String nombre, String descripcion, LocalDateTime creadoEn,
                                   LocalDateTime modificadoEn, EstadoProyecto estado, String dispositivo,
                                   int participantes, int aoisConfiguradas, Integer duracionMediaMs,
                                   EstimuloResponse estimuloPrincipal) {
    }

    public record ResumenGlobal(long estudiosActivos, long enAnalisis, long sujetosEvaluados,
                                double promedioPorEstudio, long muestrasBiometricas,
                                List<String> hardwareSoportado) {
    }

    // ------------------------------------------------------------ Estimulos

    public record EstimuloResponse(Long id, Long proyectoId, String nombreArchivo, Integer anchoOriginalPx,
                                   Integer altoOriginalPx, String urlRecurso, String etiquetaDispositivo) {
    }

    // ------------------------------------------------------------- Sesiones

    public record SesionResponse(Long id, Long proyectoId, Long estimuloId, String codigoSujeto,
                                 String dispositivoOrigen, Integer duracionTotalMs, BigDecimal tasaMuestreoHz) {
    }

    public record FijacionResponse(Long id, Long sesionId, Integer ordenSecuencia, Integer inicioMs, Integer finMs,
                                   Integer duracionMs, Double xNormalizado, Double yNormalizado, Long aoiId) {
    }

    public record SacadaResponse(Long id, Long fijacionOrigenId, Long fijacionDestinoId, Double amplitudGrados,
                                 Double velocidadPicoGradosS) {
    }

    public record MuestraResponse(Integer tiempoMs, Double x, Double y, Double pupila) {
    }

    public record PaginaResponse<T>(List<T> contenido, int pagina, int tamano, long totalElementos,
                                    int totalPaginas) {
    }

    // ----------------------------------------------------------- Importacion

    public record ResumenSesionImportada(Long sesionId, String codigoSujeto, int registrosRecibidos,
                                         int registrosDescartados, int muestrasGuardadas, int fijaciones,
                                         int sacadas, int duracionTotalMs, Double tasaMuestreoHz) {
    }

    public record ResumenImportacion(Long estimuloId, String dispositivoDetectado, String nivelDatos,
                                     String delimitador, String unidadTiempo, String sistemaCoordenadas,
                                     int resolucionAnchoPx, int resolucionAltoPx, int filasLeidas,
                                     int filasMalformadas, List<ResumenSesionImportada> sesiones,
                                     List<String> advertencias) {
    }

    /** Respuesta 201 de POST /api/datasets. */
    public record ResumenIngesta(Long proyectoId, Long estimuloId, int sesionesCreadas, int filasLeidas,
                                 int filasDescartadas, int fijacionesCalculadas, List<String> advertencias) {
    }

    // ------------------------------------------------------------------ AOI

    public record AoiRequest(
            @NotBlank(message = "La etiqueta es obligatoria") @Size(max = 100) String etiqueta,
            @NotNull(message = "La forma geometrica es obligatoria") FormaGeometrica formaGeometrica,
            @NotNull(message = "Las coordenadas son obligatorias") @Valid CoordenadasAoi coordenadas,
            @Size(max = 20) String color) {
    }

    public record AoiResponse(Long id, Long estimuloId, String etiqueta, FormaGeometrica formaGeometrica,
                              CoordenadasAoi coordenadas, String color) {
    }

    // -------------------------------------------------- Metricas (contrato)

    /** Metricas de una zona para el proyecto. aoiId null = "Fuera de las AOIs". */
    public record MetricaAoiProyecto(Long aoiId, String etiqueta, Integer ttffMs, long dwellTimeMs, Double dwellPct,
                                     int conteoFijaciones, Integer revisitas, Double duracionMediaFijacionMs) {
    }

    public record MetricaParticipante(Long sesionId, String codigoSujeto, int conteoFijaciones,
                                      Double duracionMediaFijacionMs, Integer duracionTotalMs,
                                      Map<String, Integer> ttffPorAoi) {
    }

    public record MetricasProyecto(Long proyectoId, Long estimuloId, Long sesionId, int totalFijaciones,
                                   int participantes, int duracionMaximaMs, List<MetricaAoiProyecto> porAoi,
                                   List<MetricaParticipante> porParticipante) {
    }

    // ------------------------------------------------- Metricas (analisis)

    public record MetricaAoiResponse(Long sesionId, String codigoSujeto, Long aoiId, String etiqueta,
                                     Integer conteoFijaciones, Double duracionMediaFijacionMs, Integer dwellTimeMs,
                                     Double porcentajeDwell, Integer ttffMs, Integer numeroVisitas) {
    }

    public record ResumenAoiResponse(Long aoiId, String etiqueta, int participantes, int participantesQueLoVieron,
                                     Double porcentajeParticipantes, int conteoTotalFijaciones,
                                     Double dwellMedioMs, Double ttffMedioMs, Integer ttffMinimoMs,
                                     Double visitasMedias) {
    }

    public record ResumenParticipanteResponse(Long sesionId, String codigoSujeto, String dispositivoOrigen,
                                              Integer duracionTotalMs, ResumenSesion metricas) {
    }

    public record MetricasSesionResponse(SesionResponse sesion, ResumenSesion resumen,
                                         List<MetricaAoiResponse> aois) {
    }

    public record AoiEtiqueta(Long id, String etiqueta) {
    }

    public record TransicionesResponse(List<AoiEtiqueta> aois, int[][] conteos, double[][] probabilidades,
                                       int sesionesIncluidas) {
    }

    public record ComparativaEstimuloResponse(Long estimuloId, String nombreArchivo, int participantes,
                                              Double fijacionesMedias, Double duracionMediaFijacionMs,
                                              Double duracionMediaSesionMs) {
    }

    // -------------------------------------------------------- Visualizaciones

    public record HeatmapResponse(Long estimuloId, int columnas, int filas, double[][] valores,
                                  double maximoBrutoMs, int totalFijaciones, List<Long> sesionesIncluidas) {
    }

    public record PuntoFijacionResponse(Long sesionId, String codigoSujeto, Integer orden, Double x, Double y,
                                        Integer duracionMs, Long aoiId) {
    }

    public record ScanpathResponse(Long sesionId, String codigoSujeto, Long estimuloId,
                                   List<FijacionResponse> fijaciones, List<SacadaResponse> sacadas) {
    }

    // ---------------------------------------------------------------- Ayuda

    public record TarjetaAyuda(String clave, String titulo, String queRepresenta, List<String> datosUtilizados,
                               List<String> aspectosObservables, List<String> consideraciones) {
    }

    // --------------------------------------------------------------- Errores

    /** Forma de todo error 4xx/5xx, tal como la muestra el frontend. */
    public record ErrorApi(int status, String mensaje, List<String> detalles) {
    }
}
