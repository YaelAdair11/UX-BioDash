package com.uxbiodash.backend.ingesta;

import com.uxbiodash.backend.exception.ArchivoInvalidoException;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Lectura en streaming de archivos CSV/TSV de eye tracking. Detecta el delimitador y el fabricante,
 * valida cabeceras (HTTP 400 si faltan) y convierte cada fila al DTO canonico.
 */
public final class LectorArchivoEyeTracking {

    /** Participante asignado cuando el archivo no trae columna de sujeto y se indico uno por parametro. */
    private static final String SIN_PARTICIPANTE = "\u0000";

    private LectorArchivoEyeTracking() {
    }

    public record OpcionesLectura(String participantePorDefecto, UnidadTiempo unidadForzada,
                                  SistemaCoordenadas coordenadasForzadas) {
        public static OpcionesLectura ninguna() {
            return new OpcionesLectura(null, null, null);
        }
    }

    public record ResultadoLectura(
            MapeoColumnas mapeo,
            char delimitador,
            List<RawGazeSampleDTO> muestras,
            List<RawFixationEventDTO> eventos,
            int filasLeidas,
            int filasMalformadas,
            List<String> advertencias) {
    }

    public static ResultadoLectura leer(InputStream entrada, OpcionesLectura opciones) {
        try (BufferedReader lector = new BufferedReader(new InputStreamReader(entrada, StandardCharsets.UTF_8))) {
            return leer(lector, opciones);
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo leer el archivo cargado", e);
        }
    }

    private static ResultadoLectura leer(BufferedReader lector, OpcionesLectura opciones) throws IOException {
        String cabecera = siguienteLineaUtil(lector);
        if (cabecera == null) {
            throw new ArchivoInvalidoException("El archivo esta vacio.");
        }
        char delimitador = DetectorFormato.detectarDelimitador(cabecera);
        List<String> columnas = dividir(cabecera, delimitador);
        boolean participanteOpcional = opciones.participantePorDefecto() != null
                && !opciones.participantePorDefecto().isBlank();
        MapeoColumnas mapeo = DetectorFormato.detectar(columnas, participanteOpcional);
        if (opciones.unidadForzada() != null) {
            mapeo = mapeo.conUnidadTiempo(opciones.unidadForzada());
        }
        if (opciones.coordenadasForzadas() != null) {
            mapeo = mapeo.conCoordenadas(opciones.coordenadasForzadas());
        }
        boolean comaDecimal = delimitador != ',';

        List<RawGazeSampleDTO> muestras = new ArrayList<>();
        Map<String, AcumuladorEvento> eventos = new LinkedHashMap<>();
        int leidas = 0;
        int malformadas = 0;
        int minimoColumnas = mapeo.indiceMaximoObligatorio() + 1;

        String linea;
        while ((linea = lector.readLine()) != null) {
            if (linea.isBlank() || linea.startsWith("#")) {
                continue;
            }
            leidas++;
            List<String> campos = dividir(linea, delimitador);
            if (campos.size() < minimoColumnas) {
                malformadas++;
                continue;
            }
            String participante = mapeo.participante() >= 0 ? texto(campos, mapeo.participante()) : SIN_PARTICIPANTE;
            if (participante == null || participante.isEmpty()) {
                malformadas++;
                continue;
            }
            String estimulo = mapeo.estimulo() >= 0 ? texto(campos, mapeo.estimulo()) : null;
            Double x = numero(campos, mapeo.x(), comaDecimal);
            Double y = numero(campos, mapeo.y(), comaDecimal);

            if (mapeo.nivel() == NivelDatos.MUESTRAS) {
                Double ts = numero(campos, mapeo.timestamp(), comaDecimal);
                if (ts == null) {
                    malformadas++;
                    continue;
                }
                muestras.add(new RawGazeSampleDTO(participante, estimulo, ts, x, y,
                        pupila(campos, mapeo, comaDecimal), esValido(campos, mapeo, comaDecimal)));
            } else {
                if (mapeo.tipoEvento() >= 0) {
                    String tipo = texto(campos, mapeo.tipoEvento());
                    if (tipo == null || !tipo.toLowerCase(Locale.ROOT).startsWith("fixation")) {
                        continue; // sacadas / no clasificados: se reconstruyen a partir de las fijaciones
                    }
                }
                Double inicio = numero(campos, mapeo.inicio(), comaDecimal);
                Double tsFila = mapeo.timestamp() >= 0 ? numero(campos, mapeo.timestamp(), comaDecimal) : null;
                if (inicio == null) {
                    inicio = tsFila;
                }
                if (inicio == null) {
                    malformadas++;
                    continue;
                }
                Double fin = numero(campos, mapeo.fin(), comaDecimal);
                Double duracion = numero(campos, mapeo.duracion(), comaDecimal);
                String id = mapeo.idEvento() >= 0 ? texto(campos, mapeo.idEvento()) : null;
                String clave = participante + "|" + (id != null && !id.isEmpty() ? id : "#" + leidas);
                boolean valido = esValido(campos, mapeo, comaDecimal);
                double finFila = tsFila != null ? Math.max(tsFila, inicio) : inicio;
                eventos.computeIfAbsent(clave, k -> new AcumuladorEvento(participante, estimulo, x, y, valido))
                        .acumular(inicio, fin, finFila, duracion);
            }
        }

        if (leidas == 0) {
            throw new ArchivoInvalidoException("El archivo solo contiene la cabecera; no hay registros de mirada.");
        }
        if (malformadas == leidas) {
            throw new ArchivoInvalidoException("El archivo parece danado: ninguna fila contiene datos legibles "
                    + "de participante, tiempo y coordenadas.",
                    List.of("Filas leidas: " + leidas, "Separador detectado: " + nombreDelimitador(delimitador)));
        }

        List<String> advertencias = new ArrayList<>();
        if (malformadas > 0) {
            advertencias.add(malformadas + " filas incompletas o con formato incorrecto fueron descartadas.");
        }
        List<RawFixationEventDTO> listaEventos = eventos.values().stream().map(AcumuladorEvento::aDto).toList();
        if (mapeo.nivel() == NivelDatos.EVENTOS && listaEventos.isEmpty()) {
            throw new ArchivoInvalidoException("El archivo de eventos no contiene ninguna fijacion.");
        }

        if (mapeo.unidadTiempo() == null) {
            UnidadTiempo inferida = inferirUnidad(muestras);
            mapeo = mapeo.conUnidadTiempo(inferida);
            advertencias.add("Unidad de tiempo inferida a partir de los datos: " + inferida + ".");
        }
        if (mapeo.coordenadas() == null) {
            SistemaCoordenadas inferido = inferirCoordenadas(muestras, listaEventos);
            mapeo = mapeo.conCoordenadas(inferido);
            advertencias.add("Sistema de coordenadas inferido a partir de los datos: " + inferido + ".");
        }

        if (mapeo.participante() < 0) {
            String porDefecto = opciones.participantePorDefecto().trim();
            muestras = muestras.stream().map(m -> new RawGazeSampleDTO(porDefecto, m.estimulo(), m.timestampRaw(),
                    m.xRaw(), m.yRaw(), m.pupila(), m.valido())).toList();
            listaEventos = listaEventos.stream().map(e -> new RawFixationEventDTO(porDefecto, e.estimulo(),
                    e.inicioRaw(), e.finRaw(), e.duracionMs(), e.xRaw(), e.yRaw(), e.valido())).toList();
        }

        return new ResultadoLectura(mapeo, delimitador, muestras, listaEventos, leidas, malformadas, advertencias);
    }

    // --------------------------------------------------------------- Inferencias

    /**
     * Infiere la unidad a partir del intervalo mediano entre muestras: los dispositivos trabajan entre
     * 30 y 2000 Hz, es decir 0.5 a 33 ms entre muestras.
     */
    static UnidadTiempo inferirUnidad(List<RawGazeSampleDTO> muestras) {
        double[] ts = muestras.stream().limit(5000).mapToDouble(RawGazeSampleDTO::timestampRaw).sorted().toArray();
        List<Double> deltas = new ArrayList<>();
        for (int i = 1; i < ts.length; i++) {
            double d = ts[i] - ts[i - 1];
            if (d > 0) {
                deltas.add(d);
            }
        }
        if (deltas.isEmpty()) {
            return UnidadTiempo.MILISEGUNDOS;
        }
        double mediana = deltas.stream().sorted().toList().get(deltas.size() / 2);
        if (mediana < 0.25) {
            return UnidadTiempo.SEGUNDOS;
        }
        if (mediana > 250) {
            return UnidadTiempo.MICROSEGUNDOS;
        }
        return UnidadTiempo.MILISEGUNDOS;
    }

    static SistemaCoordenadas inferirCoordenadas(List<RawGazeSampleDTO> muestras, List<RawFixationEventDTO> eventos) {
        double maximo = 0;
        for (RawGazeSampleDTO m : muestras) {
            maximo = Math.max(maximo, Math.max(valorAbs(m.xRaw()), valorAbs(m.yRaw())));
        }
        for (RawFixationEventDTO e : eventos) {
            maximo = Math.max(maximo, Math.max(valorAbs(e.xRaw()), valorAbs(e.yRaw())));
        }
        return maximo <= 1.5 ? SistemaCoordenadas.NORMALIZADO : SistemaCoordenadas.PIXELES;
    }

    private static double valorAbs(Double v) {
        return v == null || v.isNaN() ? 0 : Math.abs(v);
    }

    // ---------------------------------------------------------- Lectura de campos

    private static Double pupila(List<String> campos, MapeoColumnas mapeo, boolean comaDecimal) {
        double suma = 0;
        int n = 0;
        for (int indice : mapeo.pupila()) {
            Double v = numero(campos, indice, comaDecimal);
            if (v != null && v > 0) {
                suma += v;
                n++;
            }
        }
        return n == 0 ? null : suma / n;
    }

    /** Valido si al menos una de las columnas de validez (p. ej. ojo izquierdo o derecho) lo indica. */
    private static boolean esValido(List<String> campos, MapeoColumnas mapeo, boolean comaDecimal) {
        if (mapeo.validez().isEmpty()) {
            return true;
        }
        boolean hayDato = false;
        for (MapeoColumnas.ColumnaValidez col : mapeo.validez()) {
            String valor = texto(campos, col.indice());
            if (valor == null || valor.isEmpty()) {
                continue;
            }
            hayDato = true;
            if (interpretarValidez(valor, col.tipo(), comaDecimal)) {
                return true;
            }
        }
        return !hayDato;
    }

    static boolean interpretarValidez(String valor, MapeoColumnas.TipoValidez tipo, boolean comaDecimal) {
        String v = valor.trim().toLowerCase(Locale.ROOT);
        return switch (tipo) {
            case BANDERA_VALIDO -> v.equals("1") || v.equals("true") || v.equals("valid") || v.equals("valido")
                    || v.equals("si") || v.equals("1.0");
            case BANDERA_DESCARTE -> !(v.equals("1") || v.equals("true") || v.equals("1.0"));
            case CODIGO_TOBII -> {
                if (v.equals("valid")) {
                    yield true;
                }
                if (v.equals("invalid")) {
                    yield false;
                }
                Double codigo = convertir(v, comaDecimal);
                yield codigo != null && codigo <= 1;
            }
        };
    }

    private static String texto(List<String> campos, int indice) {
        if (indice < 0 || indice >= campos.size()) {
            return null;
        }
        return campos.get(indice).trim();
    }

    private static Double numero(List<String> campos, int indice, boolean comaDecimal) {
        return convertir(texto(campos, indice), comaDecimal);
    }

    static Double convertir(String valor, boolean comaDecimal) {
        if (valor == null) {
            return null;
        }
        String v = valor.trim();
        if (v.isEmpty() || v.equals(".") || v.equals("-") || v.equalsIgnoreCase("nan") || v.equalsIgnoreCase("null")
                || v.equalsIgnoreCase("na")) {
            return null;
        }
        if (comaDecimal && v.indexOf(',') >= 0 && v.indexOf('.') < 0) {
            v = v.replace(',', '.');
        }
        try {
            double d = Double.parseDouble(v);
            return Double.isNaN(d) || Double.isInfinite(d) ? null : d;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Divide una linea respetando campos entre comillas dobles. */
    static List<String> dividir(String linea, char delimitador) {
        if (linea.indexOf('"') < 0) {
            return Arrays.asList(linea.split(java.util.regex.Pattern.quote(String.valueOf(delimitador)), -1));
        }
        List<String> campos = new ArrayList<>();
        StringBuilder actual = new StringBuilder();
        boolean comillas = false;
        for (int i = 0; i < linea.length(); i++) {
            char c = linea.charAt(i);
            if (c == '"') {
                if (comillas && i + 1 < linea.length() && linea.charAt(i + 1) == '"') {
                    actual.append('"');
                    i++;
                } else {
                    comillas = !comillas;
                }
            } else if (c == delimitador && !comillas) {
                campos.add(actual.toString());
                actual.setLength(0);
            } else {
                actual.append(c);
            }
        }
        campos.add(actual.toString());
        return campos;
    }

    private static String siguienteLineaUtil(BufferedReader lector) throws IOException {
        String linea;
        while ((linea = lector.readLine()) != null) {
            if (!linea.isBlank() && !linea.startsWith("#")) {
                return linea.replace("﻿", "");
            }
        }
        return null;
    }

    private static String nombreDelimitador(char d) {
        return switch (d) {
            case '\t' -> "tabulador";
            case ';' -> "punto y coma";
            default -> "coma";
        };
    }

    /** Agrupa filas de un mismo evento (Tobii Pro Lab repite el evento en cada muestra). */
    private static final class AcumuladorEvento {
        private final String participante;
        private final String estimulo;
        private final Double x;
        private final Double y;
        private final boolean valido;
        private double inicio = Double.MAX_VALUE;
        private double finMaximo = -Double.MAX_VALUE;
        private Double finDeclarado;
        private Double duracion;

        AcumuladorEvento(String participante, String estimulo, Double x, Double y, boolean valido) {
            this.participante = participante;
            this.estimulo = estimulo;
            this.x = x;
            this.y = y;
            this.valido = valido;
        }

        void acumular(double inicioFila, Double finFila, double finObservado, Double duracionFila) {
            inicio = Math.min(inicio, inicioFila);
            finMaximo = Math.max(finMaximo, finObservado);
            if (finFila != null) {
                finDeclarado = finDeclarado == null ? finFila : Math.max(finDeclarado, finFila);
            }
            if (duracionFila != null && duracion == null) {
                duracion = duracionFila;
            }
        }

        RawFixationEventDTO aDto() {
            Double fin = finDeclarado;
            if (fin == null && duracion == null && finMaximo > inicio) {
                fin = finMaximo;
            }
            return new RawFixationEventDTO(participante, estimulo, inicio, fin, duracion, x, y, valido);
        }
    }
}
