package com.uxbiodash.backend.ingesta;

/** Nivel de procesamiento del dataset. */
public enum NivelDatos {
    /** Eventos ya clasificados por el fabricante (fijaciones etiquetadas). Se leen directamente. */
    EVENTOS,
    /** Muestras crudas continuas punto a punto. Se agrupan en fijaciones con I-VT. */
    MUESTRAS
}
