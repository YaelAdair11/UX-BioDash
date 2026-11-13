package com.uxbiodash.backend.procesamiento;

/** Fijacion consolidada (lista para persistir). Tiempos relativos en ms y coordenadas en [0, 1]. */
public record FijacionCalculada(int orden, int inicioMs, int finMs, int duracionMs, double x, double y) {
}
