package com.uxbiodash.backend.exception;

import java.util.List;

/** El archivo cargado esta danado, incompleto o no tiene la estructura minima. Se responde con HTTP 400. */
public class ArchivoInvalidoException extends RuntimeException {

    private final List<String> detalles;

    public ArchivoInvalidoException(String mensaje) {
        this(mensaje, List.of());
    }

    public ArchivoInvalidoException(String mensaje, List<String> detalles) {
        super(mensaje);
        this.detalles = List.copyOf(detalles);
    }

    public List<String> getDetalles() {
        return detalles;
    }
}
