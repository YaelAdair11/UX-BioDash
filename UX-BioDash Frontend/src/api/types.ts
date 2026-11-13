/**
 * Tipos del dominio compartidos con el backend (Spring Boot).
 * Reflejan el modelo de persistencia definido en "Estructura de Datos Común":
 * proyecto, estimulo, sesion_evaluacion, area_interes, fijacion y métricas por AOI.
 * Los nombres de campo están en camelCase (serialización JSON por defecto de Jackson).
 * Todas las coordenadas vienen normalizadas en el intervalo [0.0, 1.0] y los tiempos en ms con t=0.
 */

export type EstadoProyecto = 'BORRADOR' | 'EN_INGESTA' | 'EN_ANALISIS' | 'COMPLETADO'

export interface Proyecto {
  id: number
  nombre: string
  descripcion: string
  creadoEn: string // ISO-8601
  modificadoEn?: string
  estado: EstadoProyecto
  dispositivo: string | null // Tobii Pro TX300, EyeLink 1000 Plus, Gazepoint GP3...
  participantes: number
  aoisConfiguradas: number
  duracionMediaMs: number
  estimuloPrincipal?: Estimulo | null
}

export interface ResumenGlobal {
  estudiosActivos: number
  enAnalisis: number
  sujetosEvaluados: number
  promedioPorEstudio: number
  muestrasBiometricas: number
  hardwareSoportado: string[]
}

export interface Estimulo {
  id: number
  proyectoId: number
  nombreArchivo: string
  anchoOriginalPx: number
  altoOriginalPx: number
  urlRecurso: string
  etiquetaDispositivo?: string // "Escritorio", "Móvil"
}

export interface SesionEvaluacion {
  id: number
  proyectoId: number
  estimuloId: number
  codigoSujeto: string // P01, P02...
  dispositivoOrigen: string
  duracionTotalMs: number
  tasaMuestreoHz: number
}

export type FormaGeometrica = 'RECTANGULO' | 'POLIGONO'

/** Rectángulo normalizado: esquina superior izquierda + tamaño, todo en [0,1]. */
export interface RectNormalizado {
  x: number
  y: number
  ancho: number
  alto: number
}

export interface PuntoNormalizado {
  x: number
  y: number
}

/**
 * Geometría de una AOI. Siempre incluye la caja envolvente (x, y, ancho, alto);
 * si formaGeometrica = 'POLIGONO', `puntos` contiene los vértices en orden.
 */
export interface CoordenadasAoi extends RectNormalizado {
  puntos?: PuntoNormalizado[]
}

export interface AreaInteres {
  id: number
  estimuloId: number
  etiqueta: string
  formaGeometrica: FormaGeometrica
  coordenadas: CoordenadasAoi
  color?: string
}

export type AreaInteresNueva = Omit<AreaInteres, 'id'>

export interface Fijacion {
  id: number
  sesionId: number
  ordenSecuencia: number
  inicioMs: number
  finMs: number
  duracionMs: number
  xNormalizado: number
  yNormalizado: number
  aoiId: number | null
}

export interface MetricaAoi {
  aoiId: number | null // null = fuera de las AOIs
  etiqueta: string
  ttffMs: number | null
  dwellTimeMs: number
  dwellPct: number // 0..100
  conteoFijaciones: number
  revisitas: number | null
  duracionMediaFijacionMs: number
}

export interface MetricaParticipante {
  sesionId: number
  codigoSujeto: string
  conteoFijaciones: number
  duracionMediaFijacionMs: number
  duracionTotalMs: number
  /** TTFF por AOI (aoiId -> ms o null si nunca la miró). */
  ttffPorAoi: Record<string, number | null>
}

export interface MetricasProyecto {
  proyectoId: number
  estimuloId: number
  sesionId: number | null // null = todas las sesiones
  totalFijaciones: number
  participantes: number
  duracionMaximaMs: number
  porAoi: MetricaAoi[]
  porParticipante: MetricaParticipante[]
}

export interface ProyectoNuevo {
  nombre: string
  descripcion: string
  dispositivo: string | null
}

/** Respuesta 201 de POST /api/datasets */
export interface ResumenIngesta {
  proyectoId: number
  estimuloId: number
  sesionesCreadas: number
  filasLeidas: number
  filasDescartadas: number
  fijacionesCalculadas: number
  advertencias: string[]
}

/** Error estándar devuelto por el backend. */
export interface ErrorApi {
  status: number
  mensaje: string
  detalles?: string[]
}
