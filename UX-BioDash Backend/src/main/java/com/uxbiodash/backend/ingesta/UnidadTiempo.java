package com.uxbiodash.backend.ingesta;

/** Unidad nativa de las marcas temporales y su factor de conversion a milisegundos. */
public enum UnidadTiempo {
    MICROSEGUNDOS(0.001),
    MILISEGUNDOS(1.0),
    SEGUNDOS(1000.0);

    private final double factorAMs;

    UnidadTiempo(double factorAMs) {
        this.factorAMs = factorAMs;
    }

    public double aMilisegundos(double valor) {
        return valor * factorAMs;
    }
}
