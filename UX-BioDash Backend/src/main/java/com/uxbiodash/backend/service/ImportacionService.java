package com.uxbiodash.backend.service;

import com.uxbiodash.backend.config.ProcesamientoProperties;
import com.uxbiodash.backend.domain.Estimulo;
import com.uxbiodash.backend.domain.Fijacion;
import com.uxbiodash.backend.domain.Sacada;
import com.uxbiodash.backend.domain.SesionEvaluacion;
import com.uxbiodash.backend.exception.ArchivoInvalidoException;
import com.uxbiodash.backend.exception.SolicitudInvalidaException;
import com.uxbiodash.backend.ingesta.LectorArchivoEyeTracking;
import com.uxbiodash.backend.ingesta.LectorArchivoEyeTracking.OpcionesLectura;
import com.uxbiodash.backend.ingesta.LectorArchivoEyeTracking.ResultadoLectura;
import com.uxbiodash.backend.ingesta.NivelDatos;
import com.uxbiodash.backend.ingesta.RawFixationEventDTO;
import com.uxbiodash.backend.ingesta.RawGazeSampleDTO;
import com.uxbiodash.backend.ingesta.SistemaCoordenadas;
import com.uxbiodash.backend.ingesta.UnidadTiempo;
import com.uxbiodash.backend.procesamiento.FijacionCalculada;
import com.uxbiodash.backend.procesamiento.Normalizador.Resolucion;
import com.uxbiodash.backend.procesamiento.ProcesadorSesiones;
import com.uxbiodash.backend.procesamiento.ProcesadorSesiones.SesionProcesada;
import com.uxbiodash.backend.procesamiento.SacadaCalculada;
import com.uxbiodash.backend.repository.FijacionRepository;
import com.uxbiodash.backend.repository.MuestraJdbcRepository;
import com.uxbiodash.backend.repository.SacadaRepository;
import com.uxbiodash.backend.repository.SesionEvaluacionRepository;
import com.uxbiodash.backend.web.dto.ApiDtos.ResumenImportacion;
import com.uxbiodash.backend.web.dto.ApiDtos.ResumenSesionImportada;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Flujo de datos de la arquitectura: (1) recepcion e ingesta, (2) procesamiento y normalizacion,
 * (3) persistencia de resultados en PostgreSQL.
 */
@Service
public class ImportacionService {

    private static final Set<String> EXTENSIONES = Set.of("csv", "tsv", "txt");

    /**
     * Parametros opcionales de la importacion.
     *
     * @param codigoSujeto          participante a usar si el archivo no trae columna de sujeto
     * @param anchoPantallaPx       resolucion horizontal de la pantalla de la prueba (por defecto, la de la imagen)
     * @param altoPantallaPx        resolucion vertical de la pantalla de la prueba (por defecto, la de la imagen)
     * @param unidadTiempo          fuerza la unidad de las marcas temporales
     * @param sistemaCoordenadas    fuerza pixeles o coordenadas normalizadas
     * @param filtroEstimulo        usa solo las filas cuyo estimulo coincida (archivos con varias pantallas)
     * @param reemplazarExistentes  borra sesiones previas del mismo sujeto en este estimulo
     * @param guardarMuestras       persiste las muestras crudas normalizadas
     */
    public record OpcionesImportacion(String codigoSujeto, Integer anchoPantallaPx, Integer altoPantallaPx,
                                      UnidadTiempo unidadTiempo, SistemaCoordenadas sistemaCoordenadas,
                                      String filtroEstimulo, boolean reemplazarExistentes,
                                      boolean guardarMuestras) {
    }

    private final EstimuloService estimulos;
    private final SesionEvaluacionRepository sesiones;
    private final FijacionRepository fijaciones;
    private final SacadaRepository sacadas;
    private final MuestraJdbcRepository muestrasJdbc;
    private final MetricasService metricas;
    private final ProyectoService proyectos;
    private final ProcesadorSesiones procesador;
    private final int tamanoLote;

    public ImportacionService(EstimuloService estimulos, SesionEvaluacionRepository sesiones,
                              FijacionRepository fijaciones, SacadaRepository sacadas,
                              MuestraJdbcRepository muestrasJdbc, MetricasService metricas,
                              ProyectoService proyectos, ProcesamientoProperties propiedades) {
        this.estimulos = estimulos;
        this.proyectos = proyectos;
        this.sesiones = sesiones;
        this.fijaciones = fijaciones;
        this.sacadas = sacadas;
        this.muestrasJdbc = muestrasJdbc;
        this.metricas = metricas;
        this.procesador = new ProcesadorSesiones(propiedades);
        this.tamanoLote = propiedades.tamanoLoteMuestras();
    }

    @Transactional
    public ResumenImportacion importar(Long estimuloId, MultipartFile archivo, OpcionesImportacion op) {
        Estimulo estimulo = estimulos.buscar(estimuloId);
        validarArchivo(archivo);

        // 1. Ingesta: delimitador, cabeceras, mapeo por fabricante
        ResultadoLectura lectura;
        try (InputStream in = archivo.getInputStream()) {
            lectura = LectorArchivoEyeTracking.leer(in,
                    new OpcionesLectura(op.codigoSujeto(), op.unidadTiempo(), op.sistemaCoordenadas()));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        List<String> advertencias = new ArrayList<>(lectura.advertencias());

        List<RawGazeSampleDTO> muestras = lectura.muestras();
        List<RawFixationEventDTO> eventos = lectura.eventos();
        String filtro = resolverFiltroEstimulo(op.filtroEstimulo(), estimulo, muestras, eventos, advertencias);
        if (filtro != null) {
            muestras = muestras.stream().filter(m -> filtro.equalsIgnoreCase(m.estimulo())).toList();
            eventos = eventos.stream().filter(e -> filtro.equalsIgnoreCase(e.estimulo())).toList();
        }

        Resolucion resolucion = resolucion(op, estimulo);

        // 2. Normalizacion y clasificacion (lectura directa de eventos o I-VT)
        List<SesionProcesada> procesadas = lectura.mapeo().nivel() == NivelDatos.EVENTOS
                ? procesador.procesarEventos(eventos, lectura.mapeo(), resolucion)
                : procesador.procesarMuestras(muestras, lectura.mapeo(), resolucion);

        List<SesionProcesada> utiles = new ArrayList<>();
        for (SesionProcesada p : procesadas) {
            if (p.fijaciones().isEmpty() && p.muestras().isEmpty()) {
                advertencias.add("El participante '" + p.codigoSujeto()
                        + "' no tiene registros validos tras el filtrado y fue omitido.");
            } else {
                utiles.add(p);
            }
        }
        if (utiles.isEmpty()) {
            throw new ArchivoInvalidoException("Ningun registro del archivo supero la validacion de senal "
                    + "(coordenadas nulas, negativas, parpadeos o saltos imposibles).", advertencias);
        }

        // 3. Persistencia
        if (op.reemplazarExistentes()) {
            int borradas = sesiones.borrarPorSujetos(estimuloId,
                    utiles.stream().map(p -> recortar(p.codigoSujeto(), 50)).toList());
            if (borradas > 0) {
                advertencias.add(borradas + " sesiones previas de los mismos participantes fueron reemplazadas.");
            }
            estimulo = estimulos.buscar(estimuloId);
        }

        List<SesionEvaluacion> guardadas = new ArrayList<>();
        List<ResumenSesionImportada> resumenes = new ArrayList<>();
        for (SesionProcesada p : utiles) {
            SesionEvaluacion s = new SesionEvaluacion();
            s.setProyecto(estimulo.getProyecto());
            s.setEstimulo(estimulo);
            s.setCodigoSujeto(recortar(p.codigoSujeto(), 50));
            s.setDispositivoOrigen(lectura.mapeo().dispositivo().getNombre());
            s.setDuracionTotalMs(p.duracionTotalMs());
            s.setTasaMuestreoHz(p.tasaMuestreoHz() == null ? null
                    : BigDecimal.valueOf(p.tasaMuestreoHz()).setScale(2, RoundingMode.HALF_UP));
            s = sesiones.save(s);
            guardadas.add(s);

            int muestrasGuardadas = 0;
            if (op.guardarMuestras() && !p.muestras().isEmpty()) {
                muestrasJdbc.insertarLote(s.getId(), p.muestras(), tamanoLote);
                muestrasGuardadas = p.muestras().size();
            }

            List<Fijacion> entidades = new ArrayList<>(p.fijaciones().size());
            for (FijacionCalculada fc : p.fijaciones()) {
                Fijacion f = new Fijacion();
                f.setSesion(s);
                f.setOrdenSecuencia(fc.orden());
                f.setInicioMs(fc.inicioMs());
                f.setFinMs(fc.finMs());
                f.setDuracionMs(fc.duracionMs());
                f.setXNormalizado(fc.x());
                f.setYNormalizado(fc.y());
                entidades.add(f);
            }
            entidades = fijaciones.saveAll(entidades);

            List<Sacada> listaSacadas = new ArrayList<>(p.sacadas().size());
            for (SacadaCalculada sc : p.sacadas()) {
                Sacada sa = new Sacada();
                sa.setSesion(s);
                sa.setFijacionOrigen(entidades.get(sc.indiceOrigen()));
                sa.setFijacionDestino(entidades.get(sc.indiceDestino()));
                sa.setAmplitudGrados(sc.amplitudGrados());
                sa.setVelocidadPicoGradosS(sc.velocidadPicoGradosS());
                listaSacadas.add(sa);
            }
            sacadas.saveAll(listaSacadas);

            resumenes.add(new ResumenSesionImportada(s.getId(), s.getCodigoSujeto(), p.registrosRecibidos(),
                    p.registrosDescartados(), muestrasGuardadas, entidades.size(), listaSacadas.size(),
                    p.duracionTotalMs(), p.tasaMuestreoHz()));
        }

        // Metricas AOI precalculadas (si el estimulo ya tiene zonas dibujadas)
        metricas.recalcularSesiones(estimuloId, guardadas);

        // Sin esto, una carga por /estimulos/{id}/importaciones dejaba el proyecto en BORRADOR.
        proyectos.actualizarEstado(estimulo.getProyecto().getId());

        return new ResumenImportacion(estimuloId, lectura.mapeo().dispositivo().getNombre(),
                lectura.mapeo().nivel().name(), nombreDelimitador(lectura.delimitador()),
                lectura.mapeo().unidadTiempo().name(), lectura.mapeo().coordenadas().name(),
                resolucion.anchoPx(), resolucion.altoPx(), lectura.filasLeidas(), lectura.filasMalformadas(),
                resumenes, advertencias);
    }

    private static void validarArchivo(MultipartFile archivo) {
        if (archivo == null || archivo.isEmpty()) {
            throw new ArchivoInvalidoException("Debe adjuntar el archivo de eye tracking (CSV o TSV).");
        }
        String nombre = archivo.getOriginalFilename();
        if (nombre != null && nombre.contains(".")) {
            String ext = nombre.substring(nombre.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
            if (!EXTENSIONES.contains(ext)) {
                throw new ArchivoInvalidoException("Formato '." + ext + "' no soportado. Exporte los datos a texto "
                        + "plano (CSV o TSV) antes de cargarlos; no se leen archivos binarios de fabricantes "
                        + "como .edf o .idf.");
            }
        }
    }

    private static Resolucion resolucion(OpcionesImportacion op, Estimulo estimulo) {
        if ((op.anchoPantallaPx() == null) != (op.altoPantallaPx() == null)) {
            throw new SolicitudInvalidaException("Indique ambos valores: anchoPantallaPx y altoPantallaPx.");
        }
        if (op.anchoPantallaPx() != null) {
            if (op.anchoPantallaPx() <= 0 || op.altoPantallaPx() <= 0) {
                throw new SolicitudInvalidaException("La resolucion de pantalla debe ser mayor que 0.");
            }
            return new Resolucion(op.anchoPantallaPx(), op.altoPantallaPx());
        }
        return new Resolucion(estimulo.getAnchoOriginalPx(), estimulo.getAltoOriginalPx());
    }

    /**
     * Si el archivo trae varias pantallas, usa el filtro indicado o intenta emparejar automaticamente con el
     * nombre de la imagen del estimulo.
     */
    private static String resolverFiltroEstimulo(String filtro, Estimulo estimulo, List<RawGazeSampleDTO> muestras,
                                                 List<RawFixationEventDTO> eventos, List<String> advertencias) {
        Set<String> presentes = new LinkedHashSet<>();
        muestras.stream().map(RawGazeSampleDTO::estimulo).filter(Objects::nonNull).filter(s -> !s.isBlank())
                .forEach(presentes::add);
        eventos.stream().map(RawFixationEventDTO::estimulo).filter(Objects::nonNull).filter(s -> !s.isBlank())
                .forEach(presentes::add);

        if (filtro != null && !filtro.isBlank()) {
            String f = filtro.trim();
            if (presentes.stream().noneMatch(f::equalsIgnoreCase)) {
                throw new SolicitudInvalidaException("El estimulo '" + f + "' no aparece en el archivo. "
                        + "Estimulos encontrados: " + (presentes.isEmpty() ? "(ninguno)" : String.join(", ", presentes)));
            }
            return f;
        }
        if (presentes.size() <= 1) {
            return null;
        }
        String base = sinExtension(estimulo.getNombreArchivo());
        for (String p : presentes) {
            if (p.equalsIgnoreCase(estimulo.getNombreArchivo()) || sinExtension(p).equalsIgnoreCase(base)) {
                advertencias.add("El archivo contiene varios estimulos; se usaron solo las filas de '" + p + "'.");
                return p;
            }
        }
        advertencias.add("El archivo contiene varios estimulos (" + String.join(", ", presentes)
                + ") y se combinaron todos. Use el parametro 'filtroEstimulo' para elegir uno.");
        return null;
    }

    private static String sinExtension(String nombre) {
        int i = nombre.lastIndexOf('.');
        return i > 0 ? nombre.substring(0, i) : nombre;
    }

    private static String recortar(String s, int max) {
        return s.length() > max ? s.substring(0, max) : s;
    }

    private static String nombreDelimitador(char d) {
        return switch (d) {
            case '\t' -> "TABULADOR";
            case ';' -> "PUNTO_Y_COMA";
            default -> "COMA";
        };
    }
}
