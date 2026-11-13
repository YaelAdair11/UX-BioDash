/**
 * Textos de apoyo a la interpretación (sección 2.3 de "Necesidades del Investigador de UX").
 * Cada vista y cada métrica explica qué representa, qué datos usa, qué se puede observar
 * y qué consideraciones tener para no malinterpretarla. El sistema NO emite conclusiones.
 */
export interface Ayuda {
  titulo: string
  representa: string
  datos: string
  observar: string
  consideraciones: string
}

export const AYUDA: Record<string, Ayuda> = {
  heatmap: {
    titulo: 'Mapa de calor',
    representa: 'Manchas de color que marcan las zonas donde los participantes miraron más tiempo. Rojo = mayor concentración; azul = poca atención.',
    datos: 'Coordenadas X/Y normalizadas de cada fijación, ponderadas por su duración (ms), sobre la captura del estímulo.',
    observar: '¿Qué elementos llaman la atención de un vistazo? ¿Hay botones o mensajes clave que pasan desapercibidos?',
    consideraciones: 'Agrega a todos los participantes seleccionados: no muestra el orden de lectura. Un radio gaussiano grande suaviza y puede exagerar zonas.',
  },
  scanpath: {
    titulo: 'Ruta de mirada (Scanpath)',
    representa: 'Círculos en cada punto donde se detuvo la vista, numerados y unidos con flechas en orden cronológico. El tamaño indica la duración.',
    datos: 'Tiempo de inicio, coordenadas X/Y y duración de cada fijación de un participante.',
    observar: 'El orden de lectura y las regresiones (regresos de la mirada a zonas ya vistas), que pueden indicar confusión o comparación.',
    consideraciones: 'Es más legible con un solo participante. Usa la línea de tiempo para reproducir la secuencia paso a paso.',
  },
  fijaciones: {
    titulo: 'Distribución de fijaciones',
    representa: 'Puntos limpios en los lugares donde se posó la mirada, sin líneas ni manchas de calor.',
    datos: 'Coordenadas X/Y de las muestras clasificadas como fijación.',
    observar: 'Con precisión, si la persona miró dentro o fuera de un botón o área de interés.',
    consideraciones: 'La precisión del equipo (típicamente 0.5°–1°) implica que puntos muy cercanos al borde de una AOI pueden pertenecer a la zona vecina.',
  },
  ttff: {
    titulo: 'TTFF · Tiempo hasta la primera fijación',
    representa: 'Milisegundos transcurridos desde el inicio de la prueba hasta que la mirada entró por primera vez en el área.',
    datos: 'Tiempo de inicio de la primera fijación que cae dentro de la AOI, promediado entre los participantes que la vieron.',
    observar: '¿Qué elemento atrapó primero la atención? ¿El botón de compra se vio antes o después del banner?',
    consideraciones: 'Un TTFF bajo no implica comprensión. Si un participante nunca miró la AOI no entra en el promedio (revisa la tabla por participante).',
  },
  dwell: {
    titulo: 'Dwell time · Tiempo de permanencia',
    representa: 'Tiempo acumulado que la mirada permaneció dentro del área y su porcentaje respecto al total.',
    datos: 'Suma de las duraciones de todas las fijaciones dentro de la AOI.',
    observar: 'Qué secciones concentran la exploración y cuáles se ignoraron por completo.',
    consideraciones: 'Mirar mucho tiempo algo puede significar interés, pero también confusión o dificultad de lectura.',
  },
  conteo: {
    titulo: 'Conteo de fijaciones',
    representa: 'Número de veces que la mirada se detuvo dentro del área.',
    datos: 'Fijaciones cuya coordenada cae dentro de los límites de la AOI.',
    observar: 'Cuánto trabajo costó explorar la zona. Muchas fijaciones pueden delatar búsqueda o desorientación.',
    consideraciones: 'Depende del tamaño del área: AOIs más grandes acumulan más fijaciones de forma natural.',
  },
  revisitas: {
    titulo: 'Revisitas',
    representa: 'Cuántas veces la mirada salió del área y volvió a entrar.',
    datos: 'Orden de las fijaciones y su AOI; se cuentan las entradas adicionales a la primera por participante.',
    observar: 'Muchas revisitas a un mismo párrafo indican que el usuario tuvo que releerlo o compararlo.',
    consideraciones: 'También pueden reflejar una tarea que exige consultar la zona varias veces (p. ej. verificar un total).',
  },
  duracionMedia: {
    titulo: 'Duración media de fijación',
    representa: 'Promedio en milisegundos de cuánto dura cada detención de la mirada.',
    datos: 'Duración (fin − inicio) de cada fijación.',
    observar: 'Esfuerzo de procesamiento. Fijaciones muy largas sobre un texto pueden indicar redacción poco clara.',
    consideraciones: 'Varía con el tipo de contenido (imagen vs. texto) y con el algoritmo de detección de fijaciones.',
  },
}
