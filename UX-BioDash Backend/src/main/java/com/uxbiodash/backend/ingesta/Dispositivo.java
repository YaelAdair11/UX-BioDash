package com.uxbiodash.backend.ingesta;

/** Fabricante / formato emisor detectado a partir de la cabecera del archivo. */
public enum Dispositivo {
    TOBII("Tobii"),
    EYELINK("EyeLink"),
    GAZEPOINT("Gazepoint"),
    GENERICO("Generico");

    private final String nombre;

    Dispositivo(String nombre) {
        this.nombre = nombre;
    }

    public String getNombre() {
        return nombre;
    }
}
