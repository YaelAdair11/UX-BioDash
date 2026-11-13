package com.uxbiodash.backend.ingesta;

import com.uxbiodash.backend.exception.ArchivoInvalidoException;
import com.uxbiodash.backend.ingesta.MapeoColumnas.ColumnaValidez;
import com.uxbiodash.backend.ingesta.MapeoColumnas.TipoValidez;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Etapas 1, 2 y 4 del procesamiento: deteccion de delimitador, validacion de cabeceras e
 * identificacion del fabricante / tipo de archivo. Mapea las columnas nativas de EyeLink, Tobii,
 * Gazepoint o de una tabla generica al objeto canonico {@link RawGazeSampleDTO}.
 */
public final class DetectorFormato {

    private static final Pattern UNIDAD = Pattern.compile("[\\[(]\\s*(µs|μs|us|ms|s)\\s*[\\])]");
    private static final Pattern ENTRE_CORCHETES = Pattern.compile("\\[[^]]*]|\\([^)]*\\)");

    private static final String[] PARTICIPANTE_GENERICO = {"participante", "participant", "participant_id",
            "participantid", "sujeto", "codigo_sujeto", "subject", "subject_id", "usuario", "user", "user_id",
            "id_participante", "participant name"};
    private static final String[] ESTIMULO_GENERICO = {"estimulo", "pantalla", "stimulus", "screen", "screen_id",
            "media name", "presented stimulus name"};

    private DetectorFormato() {
    }

    /** Elige entre coma, punto y coma o tabulador el separador mas frecuente en la cabecera. */
    public static char detectarDelimitador(String lineaCabecera) {
        char[] candidatos = {'\t', ';', ','};
        char mejor = ',';
        int maximo = 0;
        for (char c : candidatos) {
            int n = contarFueraDeComillas(lineaCabecera, c);
            if (n > maximo) {
                maximo = n;
                mejor = c;
            }
        }
        if (maximo == 0) {
            throw new ArchivoInvalidoException("No se pudo identificar el separador del archivo. "
                    + "Se aceptan archivos separados por comas, punto y coma o tabuladores.");
        }
        return mejor;
    }

    private static int contarFueraDeComillas(String linea, char c) {
        int n = 0;
        boolean comillas = false;
        for (int i = 0; i < linea.length(); i++) {
            char actual = linea.charAt(i);
            if (actual == '"') {
                comillas = !comillas;
            } else if (actual == c && !comillas) {
                n++;
            }
        }
        return n;
    }

    /** Normaliza un nombre de cabecera: minusculas, sin acentos, sin unidades entre corchetes. */
    static String normalizar(String cabecera) {
        String s = cabecera.replace("﻿", "").trim();
        if (s.length() >= 2 && s.startsWith("\"") && s.endsWith("\"")) {
            s = s.substring(1, s.length() - 1);
        }
        s = ENTRE_CORCHETES.matcher(s).replaceAll(" ");
        s = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return s.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }

    static UnidadTiempo pistaUnidad(String cabecera) {
        Matcher m = UNIDAD.matcher(cabecera.toLowerCase(Locale.ROOT));
        if (m.find()) {
            return switch (m.group(1)) {
                case "ms" -> UnidadTiempo.MILISEGUNDOS;
                case "s" -> UnidadTiempo.SEGUNDOS;
                default -> UnidadTiempo.MICROSEGUNDOS;
            };
        }
        String n = normalizar(cabecera);
        if (n.endsWith("_ms") || n.endsWith(" ms")) {
            return UnidadTiempo.MILISEGUNDOS;
        }
        if (n.endsWith("_us")) {
            return UnidadTiempo.MICROSEGUNDOS;
        }
        if (n.endsWith("_s") || n.endsWith("_seg")) {
            return UnidadTiempo.SEGUNDOS;
        }
        return null;
    }

    /**
     * Inspecciona la cabecera y devuelve el mapeo de columnas.
     *
     * @throws ArchivoInvalidoException si faltan las variables minimas (participante, tiempo, X, Y)
     */
    public static MapeoColumnas detectar(List<String> cabecerasOriginales, boolean participanteOpcional) {
        List<String> c = cabecerasOriginales.stream().map(DetectorFormato::normalizar).toList();

        if (contiene(c, "eye movement type", "fixation point x", "gaze point x", "recording timestamp",
                "eyetracker timestamp")) {
            return validar(tobii(c, cabecerasOriginales), cabecerasOriginales, participanteOpcional);
        }
        if (contiene(c, "bpogx", "fpogx")) {
            return validar(gazepoint(c), cabecerasOriginales, participanteOpcional);
        }
        if (contiene(c, "average_gaze_x", "recording_session_label", "current_fix_x", "right_gaze_x",
                "left_gaze_x", "gx")) {
            return validar(eyelink(c), cabecerasOriginales, participanteOpcional);
        }
        return validar(generico(c, cabecerasOriginales), cabecerasOriginales, participanteOpcional);
    }

    // ------------------------------------------------------------------ Tobii

    private static MapeoColumnas tobii(List<String> c, List<String> originales) {
        int participante = buscar(c, "participant", "participant name");
        int estimulo = buscar(c, "stimulus", "presented stimulus name", "media name");
        int tipo = buscar(c, "eye movement type");
        int fx = buscar(c, "fixation point x");
        int fy = buscar(c, "fixation point y");
        int ts = buscar(c, "recording timestamp", "eyetracker timestamp");
        UnidadTiempo unidad = ts >= 0 && pistaUnidad(originales.get(ts)) != null
                ? pistaUnidad(originales.get(ts)) : UnidadTiempo.MICROSEGUNDOS;

        if (tipo >= 0 && fx >= 0 && fy >= 0) {
            int inicio = buscar(c, "start timestamp", "eye movement start timestamp");
            int fin = buscar(c, "end timestamp", "eye movement end timestamp");
            int duracion = buscar(c, "duration", "gaze event duration", "eye movement duration");
            if (inicio < 0) {
                inicio = ts;
            }
            List<ColumnaValidez> validez = new ArrayList<>();
            agregarValidez(validez, buscar(c, "flagged eye movement"), TipoValidez.BANDERA_DESCARTE);
            return new MapeoColumnas(Dispositivo.TOBII, NivelDatos.EVENTOS, unidad, SistemaCoordenadas.PIXELES,
                    participante, estimulo, ts, fx, fy, List.of(), validez, tipo,
                    buscar(c, "eye movement id", "eye movement type index"), inicio, fin, duracion);
        }

        int gx = buscar(c, "gaze point x");
        int gy = buscar(c, "gaze point y");
        List<Integer> pupila = indices(c, "pupil diameter filtered");
        if (pupila.isEmpty()) {
            pupila = indices(c, "pupil diameter left", "pupil diameter right");
        }
        List<ColumnaValidez> validez = new ArrayList<>();
        agregarValidez(validez, buscar(c, "validity left"), TipoValidez.CODIGO_TOBII);
        agregarValidez(validez, buscar(c, "validity right"), TipoValidez.CODIGO_TOBII);
        agregarValidez(validez, buscar(c, "gaze point validity"), TipoValidez.CODIGO_TOBII);
        return new MapeoColumnas(Dispositivo.TOBII, NivelDatos.MUESTRAS, unidad, SistemaCoordenadas.PIXELES,
                participante, estimulo, ts, gx, gy, pupila, validez, -1, -1, -1, -1, -1);
    }

    // -------------------------------------------------------------- Gazepoint

    private static MapeoColumnas gazepoint(List<String> c) {
        int x = buscar(c, "bpogx", "fpogx");
        int y = buscar(c, "bpogy", "fpogy");
        boolean usaBpog = x >= 0 && c.get(x).equals("bpogx");
        List<ColumnaValidez> validez = new ArrayList<>();
        agregarValidez(validez, buscar(c, usaBpog ? "bpogv" : "fpogv"), TipoValidez.BANDERA_VALIDO);
        List<Integer> pupila = indices(c, "lpmm", "rpmm");
        if (pupila.isEmpty()) {
            pupila = indices(c, "lpd", "rpd");
        }
        int ts = buscar(c, "time");
        return new MapeoColumnas(Dispositivo.GAZEPOINT, NivelDatos.MUESTRAS, UnidadTiempo.SEGUNDOS,
                SistemaCoordenadas.NORMALIZADO, buscar(c, "user", "user_id", "participant"),
                buscar(c, "media_name", "media_id", "stimulus"), ts, x, y, pupila, validez, -1, -1, -1, -1, -1);
    }

    // ---------------------------------------------------------------- EyeLink

    private static MapeoColumnas eyelink(List<String> c) {
        int participante = buscar(c, "recording_session_label", "participant", "subject");
        int estimulo = buscar(c, "image", "stimulus", "trial_label");
        int fx = buscar(c, "current_fix_x");
        int fy = buscar(c, "current_fix_y");
        if (fx >= 0 && fy >= 0) {
            return new MapeoColumnas(Dispositivo.EYELINK, NivelDatos.EVENTOS, UnidadTiempo.MILISEGUNDOS,
                    SistemaCoordenadas.PIXELES, participante, estimulo, -1, fx, fy, List.of(), List.of(), -1,
                    buscar(c, "current_fix_index"), buscar(c, "current_fix_start"), buscar(c, "current_fix_end"),
                    buscar(c, "current_fix_duration"));
        }
        int x = buscar(c, "average_gaze_x", "gx", "right_gaze_x", "left_gaze_x", "gaze_x");
        int y = buscar(c, "average_gaze_y", "gy", "right_gaze_y", "left_gaze_y", "gaze_y");
        List<Integer> pupila = indices(c, "average_pupil_size");
        if (pupila.isEmpty()) {
            pupila = indices(c, "pa", "right_pupil_size", "left_pupil_size");
        }
        List<ColumnaValidez> validez = new ArrayList<>();
        agregarValidez(validez, buscar(c, "average_in_blink", "right_in_blink", "left_in_blink"),
                TipoValidez.BANDERA_DESCARTE);
        return new MapeoColumnas(Dispositivo.EYELINK, NivelDatos.MUESTRAS, UnidadTiempo.MILISEGUNDOS,
                SistemaCoordenadas.PIXELES, participante, estimulo, buscar(c, "timestamp", "time"), x, y, pupila,
                validez, -1, -1, -1, -1, -1);
    }

    // --------------------------------------------------------------- Generico

    private static MapeoColumnas generico(List<String> c, List<String> originales) {
        int participante = buscar(c, PARTICIPANTE_GENERICO);
        int estimulo = buscar(c, ESTIMULO_GENERICO);
        int x = buscar(c, "x", "gaze_x", "x_px", "avg_x", "mirada_x", "coord_x", "x_raw", "x_normalizado",
                "fixation_x", "fijacion_x", "pos_x");
        int y = buscar(c, "y", "gaze_y", "y_px", "avg_y", "mirada_y", "coord_y", "y_raw", "y_normalizado",
                "fixation_y", "fijacion_y", "pos_y");
        List<Integer> pupila = indices(c, "pupila", "pupil", "psize", "pupil_diameter", "diametro_pupila");
        List<ColumnaValidez> validez = new ArrayList<>();
        agregarValidez(validez, buscar(c, "validez", "validity", "valid", "validity_flag", "valido"),
                TipoValidez.BANDERA_VALIDO);

        int inicio = buscar(c, "inicio_ms", "inicio", "start_ms", "start", "start_time");
        int fin = buscar(c, "fin_ms", "fin", "end_ms", "end", "end_time");
        int duracion = buscar(c, "duracion_ms", "duracion", "duration_ms", "duration");
        if (inicio >= 0 && (fin >= 0 || duracion >= 0)) {
            UnidadTiempo unidad = pistaUnidad(originales.get(inicio));
            return new MapeoColumnas(Dispositivo.GENERICO, NivelDatos.EVENTOS,
                    unidad != null ? unidad : UnidadTiempo.MILISEGUNDOS, null, participante, estimulo, -1, x, y,
                    pupila, validez, -1, -1, inicio, fin, duracion);
        }
        int ts = buscar(c, "tiempo_ms", "timestamp_ms", "time_ms", "tiempo", "timestamp", "time", "t",
                "marca_tiempo");
        UnidadTiempo unidad = ts >= 0 ? pistaUnidad(originales.get(ts)) : null;
        // unidad == null -> se infiere a partir de los datos en el lector
        return new MapeoColumnas(Dispositivo.GENERICO, NivelDatos.MUESTRAS, unidad, null, participante, estimulo,
                ts, x, y, pupila, validez, -1, -1, -1, -1, -1);
    }

    // ---------------------------------------------------------------- Validacion

    private static MapeoColumnas validar(MapeoColumnas m, List<String> originales, boolean participanteOpcional) {
        List<String> faltantes = new ArrayList<>();
        if (m.participante() < 0 && !participanteOpcional) {
            faltantes.add("identificador del participante (ej. 'participante', 'Participant', 'USER')");
        }
        if (m.nivel() == NivelDatos.MUESTRAS && m.timestamp() < 0) {
            faltantes.add("marca de tiempo (ej. 'tiempo_ms', 'timestamp', 'Recording timestamp', 'TIME')");
        }
        if (m.nivel() == NivelDatos.EVENTOS && m.inicio() < 0) {
            faltantes.add("inicio de la fijacion (ej. 'Start timestamp', 'CURRENT_FIX_START', 'inicio_ms')");
        }
        if (m.x() < 0) {
            faltantes.add("coordenada X de la mirada (ej. 'x', 'Gaze point X', 'BPOGX', 'AVERAGE_GAZE_X')");
        }
        if (m.y() < 0) {
            faltantes.add("coordenada Y de la mirada (ej. 'y', 'Gaze point Y', 'BPOGY', 'AVERAGE_GAZE_Y')");
        }
        if (!faltantes.isEmpty()) {
            List<String> detalles = new ArrayList<>();
            faltantes.forEach(f -> detalles.add("Falta la columna: " + f));
            detalles.add("Columnas encontradas: " + String.join(", ", originales));
            throw new ArchivoInvalidoException("El archivo no contiene las columnas minimas requeridas "
                    + "(participante, tiempo y coordenadas X/Y de la mirada).", detalles);
        }
        return m;
    }

    // ---------------------------------------------------------------- Utilidades

    private static boolean contiene(List<String> c, String... nombres) {
        return buscar(c, nombres) >= 0;
    }

    private static int buscar(List<String> c, String... nombres) {
        for (String nombre : nombres) {
            int i = c.indexOf(nombre);
            if (i >= 0) {
                return i;
            }
        }
        return -1;
    }

    private static List<Integer> indices(List<String> c, String... nombres) {
        List<Integer> r = new ArrayList<>();
        for (String nombre : nombres) {
            int i = c.indexOf(nombre);
            if (i >= 0) {
                r.add(i);
            }
        }
        return r;
    }

    private static void agregarValidez(List<ColumnaValidez> lista, int indice, TipoValidez tipo) {
        if (indice >= 0) {
            lista.add(new ColumnaValidez(indice, tipo));
        }
    }
}
