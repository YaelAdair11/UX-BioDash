package com.uxbiodash.backend.ingesta;

import java.util.List;

/**
 * Resultado de inspeccionar la cabecera: fabricante, nivel de datos y posicion de cada variable.
 * Un indice -1 significa que la columna no existe en el archivo.
 */
public record MapeoColumnas(
        Dispositivo dispositivo,
        NivelDatos nivel,
        UnidadTiempo unidadTiempo,
        SistemaCoordenadas coordenadas,
        int participante,
        int estimulo,
        int timestamp,
        int x,
        int y,
        List<Integer> pupila,
        List<ColumnaValidez> validez,
        int tipoEvento,
        int idEvento,
        int inicio,
        int fin,
        int duracion) {

    public static final int AUSENTE = -1;

    /** Tipo de semantica de una columna de validez. */
    public enum TipoValidez {
        /** 1 / true / valid = muestra valida (Gazepoint BPOGV, columnas genericas). */
        BANDERA_VALIDO,
        /** 1 / true = muestra descartada (Tobii "Flagged eye movement", EyeLink "IN_BLINK"). */
        BANDERA_DESCARTE,
        /** Tobii: "Valid"/"Invalid" o codigos 0-4 donde 0-1 es valido. */
        CODIGO_TOBII
    }

    public record ColumnaValidez(int indice, TipoValidez tipo) {
    }

    public MapeoColumnas conUnidadTiempo(UnidadTiempo nueva) {
        return new MapeoColumnas(dispositivo, nivel, nueva, coordenadas, participante, estimulo, timestamp, x, y,
                pupila, validez, tipoEvento, idEvento, inicio, fin, duracion);
    }

    public MapeoColumnas conCoordenadas(SistemaCoordenadas nuevas) {
        return new MapeoColumnas(dispositivo, nivel, unidadTiempo, nuevas, participante, estimulo, timestamp, x, y,
                pupila, validez, tipoEvento, idEvento, inicio, fin, duracion);
    }

    /** Numero minimo de columnas que debe tener una fila para poder leer las variables obligatorias. */
    public int indiceMaximoObligatorio() {
        int max = Math.max(x, y);
        max = Math.max(max, participante);
        if (nivel == NivelDatos.MUESTRAS) {
            max = Math.max(max, timestamp);
        } else {
            max = Math.max(max, inicio);
        }
        return max;
    }
}
