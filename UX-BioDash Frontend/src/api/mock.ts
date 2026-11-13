/**
 * Backend simulado en memoria. Permite desarrollar y demostrar el frontend
 * mientras la API Spring Boot no está disponible (VITE_USE_MOCK=true).
 * Los datos se reinician al recargar la página.
 */
import type {
  AreaInteres,
  AreaInteresNueva,
  Estimulo,
  Fijacion,
  MetricasProyecto,
  Proyecto,
  ProyectoNuevo,
  ResumenGlobal,
  ResumenIngesta,
  SesionEvaluacion,
} from './types'
import { asignarAois, calcularMetricas, detectarFijacionesIDT } from '../lib/metrics'
import { poligono } from '../lib/geometry'
import { leerTabla, validarTabla } from '../lib/tabular'

const espera = (ms = 180) => new Promise((r) => setTimeout(r, ms))
const clon = <T,>(v: T): T => structuredClone(v)

/** Generador pseudoaleatorio determinista (mulberry32) para datos reproducibles. */
function rng(semilla: number) {
  let a = semilla >>> 0
  return () => {
    a = (a + 0x6d2b79f5) >>> 0
    let t = a
    t = Math.imul(t ^ (t >>> 15), t | 1)
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61)
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296
  }
}
const gauss = (r: () => number) => {
  const u = Math.max(r(), 1e-9)
  return Math.sqrt(-2 * Math.log(u)) * Math.cos(2 * Math.PI * r())
}

interface PuntoCaliente { x: number; y: number; peso: number; sd: number }

let siguienteId = 1000
const nuevoId = () => ++siguienteId

const proyectos: Proyecto[] = []
const estimulos: Estimulo[] = []
const sesiones: SesionEvaluacion[] = []
const aois: AreaInteres[] = []
let fijaciones: Fijacion[] = []

function generarSesiones(
  proyecto: Proyecto,
  estimulo: Estimulo,
  n: number,
  duracionMediaMs: number,
  calientes: PuntoCaliente[],
  semilla: number,
) {
  const r = rng(semilla)
  const pesoTotal = calientes.reduce((s, c) => s + c.peso, 0)
  for (let p = 1; p <= n; p++) {
    const duracionTotal = Math.round(duracionMediaMs * (0.8 + r() * 0.4))
    const sesion: SesionEvaluacion = {
      id: nuevoId(),
      proyectoId: proyecto.id,
      estimuloId: estimulo.id,
      codigoSujeto: `P${String(p).padStart(2, '0')}`,
      dispositivoOrigen: proyecto.dispositivo ?? 'Desconocido',
      duracionTotalMs: duracionTotal,
      tasaMuestreoHz: proyecto.dispositivo?.includes('EyeLink') ? 1000 : proyecto.dispositivo?.includes('Gazepoint') ? 60 : 300,
    }
    sesiones.push(sesion)
    let t = Math.round(80 + r() * 150)
    let orden = 1
    while (t < duracionTotal) {
      let elegir = r() * pesoTotal
      let c = calientes[0]
      for (const cand of calientes) {
        elegir -= cand.peso
        if (elegir <= 0) { c = cand; break }
      }
      const duracion = Math.round(140 + Math.abs(gauss(r)) * 160 + r() * 80)
      const x = Math.min(0.995, Math.max(0.005, c.x + gauss(r) * c.sd))
      const y = Math.min(0.995, Math.max(0.005, c.y + gauss(r) * c.sd * 1.2))
      fijaciones.push({
        id: nuevoId(),
        sesionId: sesion.id,
        ordenSecuencia: orden++,
        inicioMs: t,
        finMs: t + duracion,
        duracionMs: duracion,
        xNormalizado: x,
        yNormalizado: y,
        aoiId: null,
      })
      t += duracion + Math.round(25 + r() * 45)
    }
  }
}

function reasignar(estimuloId: number) {
  const propias = aois.filter((a) => a.estimuloId === estimuloId)
  const sesIds = new Set(sesiones.filter((s) => s.estimuloId === estimuloId).map((s) => s.id))
  const asignadas = new Map(asignarAois(fijaciones.filter((f) => sesIds.has(f.sesionId)), propias).map((f) => [f.id, f]))
  fijaciones = fijaciones.map((f) => asignadas.get(f.id) ?? f)
  const est = estimulos.find((e) => e.id === estimuloId)
  const proy = proyectos.find((p) => p.id === est?.proyectoId)
  if (proy) proy.aoisConfiguradas = propias.length
}

function recalcularProyecto(p: Proyecto) {
  const s = sesiones.filter((x) => x.proyectoId === p.id)
  p.participantes = s.length
  p.duracionMediaMs = s.length ? Math.round(s.reduce((a, b) => a + b.duracionTotalMs, 0) / s.length) : 0
}

function sembrar() {
  const hace = (h: number) => new Date(Date.now() - h * 3600_000).toISOString()

  // 1. Checkout E-Commerce
  const p1: Proyecto = {
    id: 1, nombre: 'Estudio Checkout E-Commerce', descripcion: 'Evaluación del flujo de pago de la tienda NÓRD en escritorio. Objetivo: verificar si el botón de pago se localiza antes que el banner promocional.',
    creadoEn: hace(24 * 20), modificadoEn: hace(2), estado: 'COMPLETADO', dispositivo: 'Tobii Pro TX300',
    participantes: 0, aoisConfiguradas: 0, duracionMediaMs: 0,
  }
  const e1: Estimulo = { id: 11, proyectoId: 1, nombreArchivo: 'checkout.svg', anchoOriginalPx: 1920, altoOriginalPx: 1080, urlRecurso: '/mock/checkout.svg', etiquetaDispositivo: 'Escritorio' }
  aois.push(
    { id: 101, estimuloId: 11, etiqueta: 'Banner Hero', formaGeometrica: 'RECTANGULO', coordenadas: { x: 0.05, y: 0.18, ancho: 0.905, alto: 0.185 }, color: '#EF4444' },
    { id: 102, estimuloId: 11, etiqueta: 'Botón Pagar CTA', formaGeometrica: 'RECTANGULO', coordenadas: { x: 0.635, y: 0.775, ancho: 0.31, alto: 0.1 }, color: '#10B981' },
    { id: 103, estimuloId: 11, etiqueta: 'Resumen de Compra', formaGeometrica: 'POLIGONO', color: '#0284C7',
      coordenadas: poligono([{ x: 0.63, y: 0.415 }, { x: 0.95, y: 0.415 }, { x: 0.95, y: 0.765 }, { x: 0.79, y: 0.765 }, { x: 0.63, y: 0.69 }]) },
  )
  generarSesiones(p1, e1, 12, 8500, [
    { x: 0.27, y: 0.24, peso: 2.2, sd: 0.05 },
    { x: 0.6, y: 0.28, peso: 2.4, sd: 0.04 },
    { x: 0.2, y: 0.52, peso: 2.0, sd: 0.035 },
    { x: 0.79, y: 0.825, peso: 1.6, sd: 0.04 },
    { x: 0.79, y: 0.55, peso: 1.0, sd: 0.05 },
    { x: 0.3, y: 0.7, peso: 0.9, sd: 0.08 },
    { x: 0.5, y: 0.5, peso: 0.5, sd: 0.2 },
  ], 7)

  // 2. Landing B2B (en ingesta)
  const p2: Proyecto = {
    id: 2, nombre: 'Prueba de Usabilidad - Landing Page B2B', descripcion: 'Landing de captación para el producto nexus. Se evalúa la lectura del titular y la visibilidad del formulario de demo.',
    creadoEn: hace(24 * 25), modificadoEn: hace(24 * 17), estado: 'EN_INGESTA', dispositivo: 'EyeLink 1000 Plus',
    participantes: 0, aoisConfiguradas: 0, duracionMediaMs: 0,
  }
  const e2: Estimulo = { id: 21, proyectoId: 2, nombreArchivo: 'landing.svg', anchoOriginalPx: 1920, altoOriginalPx: 1080, urlRecurso: '/mock/landing.svg', etiquetaDispositivo: 'Escritorio' }
  aois.push({ id: 201, estimuloId: 21, etiqueta: 'Titular principal', formaGeometrica: 'RECTANGULO', coordenadas: { x: 0.06, y: 0.24, ancho: 0.42, alto: 0.2 }, color: '#3b82f6' })
  generarSesiones(p2, e2, 8, 14200, [
    { x: 0.22, y: 0.32, peso: 3, sd: 0.06 },
    { x: 0.13, y: 0.6, peso: 1.4, sd: 0.04 },
    { x: 0.72, y: 0.45, peso: 2, sd: 0.08 },
    { x: 0.5, y: 0.08, peso: 0.8, sd: 0.1 },
  ], 13)

  // 3. Menú móvil
  const p3: Proyecto = {
    id: 3, nombre: 'Evaluación Menú de Navegación Móvil', descripcion: 'Exploración del menú principal de la app móvil; se mide cuánto tarda el participante en encontrar el acceso a “Mis pedidos”.',
    creadoEn: hace(24 * 30), modificadoEn: hace(24 * 23), estado: 'COMPLETADO', dispositivo: 'Gazepoint GP3',
    participantes: 0, aoisConfiguradas: 0, duracionMediaMs: 0,
  }
  const e3: Estimulo = { id: 31, proyectoId: 3, nombreArchivo: 'mobile.svg', anchoOriginalPx: 390, altoOriginalPx: 844, urlRecurso: '/mock/mobile.svg', etiquetaDispositivo: 'Móvil' }
  aois.push(
    { id: 301, estimuloId: 31, etiqueta: 'Header / menú', formaGeometrica: 'RECTANGULO', coordenadas: { x: 0.0, y: 0.0, ancho: 1, alto: 0.1 }, color: '#f59e0b' },
    { id: 302, estimuloId: 31, etiqueta: 'Mis pedidos', formaGeometrica: 'RECTANGULO', coordenadas: { x: 0.06, y: 0.42, ancho: 0.88, alto: 0.08 }, color: '#a855f7' },
    { id: 303, estimuloId: 31, etiqueta: 'Barra inferior', formaGeometrica: 'RECTANGULO', coordenadas: { x: 0.0, y: 0.9, ancho: 1, alto: 0.1 }, color: '#06b6d4' },
  )
  generarSesiones(p3, e3, 15, 5100, [
    { x: 0.5, y: 0.05, peso: 2.5, sd: 0.08 },
    { x: 0.45, y: 0.3, peso: 1.6, sd: 0.08 },
    { x: 0.4, y: 0.46, peso: 1.4, sd: 0.05 },
    { x: 0.5, y: 0.95, peso: 0.8, sd: 0.1 },
    { x: 0.5, y: 0.7, peso: 0.6, sd: 0.15 },
  ], 21)

  // 4. Borrador
  const p4: Proyecto = {
    id: 4, nombre: 'Test A/B - Rediseño de Ficha de Producto', descripcion: 'Comparación entre la ficha de producto actual y el rediseño propuesto.',
    creadoEn: hace(24 * 15), modificadoEn: hace(24 * 15), estado: 'BORRADOR', dispositivo: null,
    participantes: 0, aoisConfiguradas: 0, duracionMediaMs: 0,
  }

  proyectos.push(p1, p2, p3, p4)
  estimulos.push(e1, e2, e3)
  for (const e of estimulos) reasignar(e.id)
  for (const p of proyectos) {
    recalcularProyecto(p)
    p.estimuloPrincipal = estimulos.find((e) => e.proyectoId === p.id) ?? null
  }
}
sembrar()

function noEncontrado(que: string): never {
  throw { status: 404, mensaje: `${que} no encontrado.` }
}

// ───────────── Endpoints simulados ─────────────

export async function resumen(): Promise<ResumenGlobal> {
  await espera()
  const activos = proyectos.filter((p) => p.estado !== 'BORRADOR')
  return {
    estudiosActivos: activos.length,
    enAnalisis: proyectos.filter((p) => p.estado === 'EN_ANALISIS' || p.estado === 'EN_INGESTA').length,
    sujetosEvaluados: sesiones.length,
    promedioPorEstudio: activos.length ? Math.round(sesiones.length / activos.length) : 0,
    muestrasBiometricas: sesiones.reduce((s, x) => s + Math.round((x.duracionTotalMs / 1000) * x.tasaMuestreoHz), 0),
    hardwareSoportado: ['Tobii Pro', 'EyeLink 1000', 'Gazepoint GP3'],
  }
}

export async function listarProyectos(): Promise<Proyecto[]> {
  await espera()
  return clon(proyectos).sort((a, b) => (b.modificadoEn ?? b.creadoEn).localeCompare(a.modificadoEn ?? a.creadoEn))
}

export async function obtenerProyecto(id: number): Promise<Proyecto> {
  await espera(80)
  return clon(proyectos.find((p) => p.id === id) ?? noEncontrado('Proyecto'))
}

export async function crearProyecto(d: ProyectoNuevo): Promise<Proyecto> {
  await espera()
  if (!d.nombre.trim()) throw { status: 400, mensaje: 'El nombre del proyecto es obligatorio.' }
  const ahora = new Date().toISOString()
  const p: Proyecto = {
    id: nuevoId(), nombre: d.nombre.trim(), descripcion: d.descripcion, dispositivo: d.dispositivo,
    creadoEn: ahora, modificadoEn: ahora, estado: 'BORRADOR', participantes: 0, aoisConfiguradas: 0, duracionMediaMs: 0,
    estimuloPrincipal: null,
  }
  proyectos.push(p)
  return clon(p)
}

export async function eliminarProyecto(id: number): Promise<void> {
  await espera()
  const i = proyectos.findIndex((p) => p.id === id)
  if (i < 0) noEncontrado('Proyecto')
  proyectos.splice(i, 1)
}

export async function listarEstimulos(proyectoId: number): Promise<Estimulo[]> {
  await espera(60)
  return clon(estimulos.filter((e) => e.proyectoId === proyectoId))
}

export async function listarSesiones(proyectoId: number): Promise<SesionEvaluacion[]> {
  await espera(60)
  return clon(sesiones.filter((s) => s.proyectoId === proyectoId))
}

export async function listarFijaciones(estimuloId: number, sesionId?: number | null): Promise<Fijacion[]> {
  await espera(120)
  const ids = new Set(sesiones.filter((s) => s.estimuloId === estimuloId && (!sesionId || s.id === sesionId)).map((s) => s.id))
  return clon(fijaciones.filter((f) => ids.has(f.sesionId)))
}

export async function listarAois(estimuloId: number): Promise<AreaInteres[]> {
  await espera(60)
  return clon(aois.filter((a) => a.estimuloId === estimuloId))
}

export async function crearAoi(a: AreaInteresNueva): Promise<AreaInteres> {
  await espera(100)
  const nueva = { ...clon(a), id: nuevoId() }
  aois.push(nueva)
  reasignar(a.estimuloId)
  return clon(nueva)
}

export async function actualizarAoi(a: AreaInteres): Promise<AreaInteres> {
  await espera(100)
  const i = aois.findIndex((x) => x.id === a.id)
  if (i < 0) noEncontrado('Área de interés')
  aois[i] = clon(a)
  reasignar(a.estimuloId)
  return clon(a)
}

export async function eliminarAoi(id: number): Promise<void> {
  await espera(100)
  const i = aois.findIndex((x) => x.id === id)
  if (i < 0) noEncontrado('Área de interés')
  const [eliminada] = aois.splice(i, 1)
  reasignar(eliminada.estimuloId)
}

export async function metricas(proyectoId: number, sesionId?: number | null): Promise<MetricasProyecto> {
  await espera(150)
  const est = estimulos.find((e) => e.proyectoId === proyectoId) ?? noEncontrado('Estímulo del proyecto')
  const ses = sesiones.filter((s) => s.estimuloId === est.id && (!sesionId || s.id === sesionId))
  const ids = new Set(ses.map((s) => s.id))
  const fx = fijaciones.filter((f) => ids.has(f.sesionId))
  const { porAoi, porParticipante } = calcularMetricas(fx, aois.filter((a) => a.estimuloId === est.id), ses)
  return {
    proyectoId,
    estimuloId: est.id,
    sesionId: sesionId ?? null,
    totalFijaciones: fx.length,
    participantes: ses.length,
    duracionMaximaMs: Math.max(0, ...ses.map((s) => s.duracionTotalMs)),
    porAoi,
    porParticipante,
  }
}

function leerImagen(archivo: File): Promise<{ url: string; ancho: number; alto: number }> {
  return new Promise((resolve, reject) => {
    const url = URL.createObjectURL(archivo)
    const img = new Image()
    img.onload = () => resolve({ url, ancho: img.naturalWidth, alto: img.naturalHeight })
    img.onerror = () => reject({ status: 400, mensaje: 'La imagen no se pudo leer. Usa un archivo PNG o JPG válido.' })
    img.src = url
  })
}

export async function subirDataset(
  proyectoId: number,
  archivo: File,
  imagen: File,
  onProgress?: (pct: number) => void,
): Promise<ResumenIngesta> {
  const proy = proyectos.find((p) => p.id === proyectoId) ?? noEncontrado('Proyecto')
  for (let pct = 0; pct <= 100; pct += 20) {
    onProgress?.(pct)
    await espera(90)
  }
  const tabla = leerTabla(await archivo.text())
  const val = validarTabla(tabla)
  if (!val.valido) throw { status: 422, mensaje: 'El archivo no tiene la estructura esperada.', detalles: val.errores }
  const img = await leerImagen(imagen)

  // Reemplaza datos previos del proyecto
  const previos = estimulos.filter((e) => e.proyectoId === proyectoId).map((e) => e.id)
  const sesPrevias = new Set(sesiones.filter((s) => s.proyectoId === proyectoId).map((s) => s.id))
  fijaciones = fijaciones.filter((f) => !sesPrevias.has(f.sesionId))
  for (let i = sesiones.length - 1; i >= 0; i--) if (sesPrevias.has(sesiones[i].id)) sesiones.splice(i, 1)
  for (let i = aois.length - 1; i >= 0; i--) if (previos.includes(aois[i].estimuloId)) aois.splice(i, 1)
  for (let i = estimulos.length - 1; i >= 0; i--) if (estimulos[i].proyectoId === proyectoId) estimulos.splice(i, 1)

  const est: Estimulo = {
    id: nuevoId(), proyectoId, nombreArchivo: imagen.name, anchoOriginalPx: img.ancho, altoOriginalPx: img.alto,
    urlRecurso: img.url, etiquetaDispositivo: img.ancho < img.alto ? 'Móvil' : 'Escritorio',
  }
  estimulos.push(est)

  const m = val.mapeo
  const grupos = new Map<string, { t: number; x: number; y: number; d?: number }[]>()
  let descartadas = 0
  for (const fila of tabla.filas) {
    const t = Number(fila[m.tiempo!]), x = Number(fila[m.x!]), y = Number(fila[m.y!])
    const p = fila[m.participante!]
    if (!p || !Number.isFinite(t) || !Number.isFinite(x) || !Number.isFinite(y) || fila[m.x!] === '' || fila[m.y!] === '') {
      descartadas++
      continue
    }
    const d = m.duracion !== undefined ? Number(fila[m.duracion]) : undefined
    if (!grupos.has(p)) grupos.set(p, [])
    grupos.get(p)!.push({ t, x, y, d: Number.isFinite(d) ? d : undefined })
  }
  // ¿Coordenadas ya normalizadas?
  let maxX = 0, maxY = 0
  for (const l of grupos.values()) for (const s of l) { maxX = Math.max(maxX, s.x); maxY = Math.max(maxY, s.y) }
  const yaNormalizadas = maxX <= 1.0001 && maxY <= 1.0001
  const advertencias = [...val.advertencias]
  if (!yaNormalizadas) advertencias.push(`Coordenadas en píxeles escaladas a [0,1] con la resolución de la imagen (${img.ancho}×${img.alto}).`)

  let total = 0
  for (const [codigo, muestras] of grupos) {
    muestras.sort((a, b) => a.t - b.t)
    const t0 = muestras[0].t
    const norm = muestras.map((s) => ({
      t: s.t - t0,
      x: yaNormalizadas ? s.x : s.x / img.ancho,
      y: yaNormalizadas ? s.y : s.y / img.alto,
      d: s.d,
    })).filter((s) => s.x >= 0 && s.x <= 1 && s.y >= 0 && s.y <= 1)
    if (!norm.length) continue
    const fx = m.duracion !== undefined
      ? norm.map((s) => ({ inicioMs: s.t, finMs: s.t + (s.d ?? 0), x: s.x, y: s.y }))
      : detectarFijacionesIDT(norm)
    const ultimo = norm[norm.length - 1]
    const sesion: SesionEvaluacion = {
      id: nuevoId(), proyectoId, estimuloId: est.id, codigoSujeto: codigo, dispositivoOrigen: proy.dispositivo ?? 'Desconocido',
      duracionTotalMs: Math.round(ultimo.t + (ultimo.d ?? 0)),
      tasaMuestreoHz: norm.length > 1 ? Math.round((norm.length / Math.max(ultimo.t, 1)) * 1000 * 100) / 100 : 0,
    }
    sesiones.push(sesion)
    fx.forEach((f, i) => {
      fijaciones.push({
        id: nuevoId(), sesionId: sesion.id, ordenSecuencia: i + 1, inicioMs: Math.round(f.inicioMs), finMs: Math.round(f.finMs),
        duracionMs: Math.round(f.finMs - f.inicioMs), xNormalizado: f.x, yNormalizado: f.y, aoiId: null,
      })
    })
    total += fx.length
  }

  proy.estado = 'COMPLETADO'
  proy.modificadoEn = new Date().toISOString()
  proy.estimuloPrincipal = est
  recalcularProyecto(proy)
  reasignar(est.id)

  return {
    proyectoId, estimuloId: est.id, sesionesCreadas: grupos.size, filasLeidas: tabla.filas.length,
    filasDescartadas: descartadas, fijacionesCalculadas: total, advertencias,
  }
}
