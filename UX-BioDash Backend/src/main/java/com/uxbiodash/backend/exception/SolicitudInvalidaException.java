package com.uxbiodash.backend.exception;

/** Parametros de la solicitud no validos. Se responde con HTTP 400. */
public class SolicitudInvalidaException extends RuntimeException {

    public SolicitudInvalidaException(String mensaje) {
        super(mensaje);
    }
}
