package com.uxbiodash.backend.exception;

/** El recurso solicitado no existe. Se responde con HTTP 404. */
public class RecursoNoEncontradoException extends RuntimeException {

    public RecursoNoEncontradoException(String recurso, Object id) {
        super(recurso + " con id " + id + " no existe.");
    }
}
