import type { AreaInteres, Fijacion, MetricaAoi, MetricaParticipante, SesionEvaluacion } from '../api/types'
import { dentro } from './geometry'

export { dentro }

/**
 * Cálculo de métricas por AOI.
 * La fuente de verdad es el backend; estas funciones se usan en el modo simulado
 * y para recalcular en vivo mientras el evaluador dibuja o mueve AOIs (antes de guardar).
 */


/** Asigna cada fijación a la AOI más pequeña que la contiene (AOIs anidadas). */
export function asignarAois(fijaciones: Fijacion[], aois: AreaInteres[]): Fijacion[] {
  const ordenadas = [...aois].sort(
    (a, b) => a.coordenadas.ancho * a.coordenadas.alto - b.coordenadas.ancho * b.coordenadas.alto,
  )
  return fijaciones.map((f) => ({
    ...f,
    aoiId: ordenadas.find((a) => dentro(a.coordenadas, f.xNormalizado, f.yNormalizado))?.id ?? null,
  }))
}

const media = (v: number[]) => (v.length ? v.reduce((s, n) => s + n, 0) / v.length : 0)

export function calcularMetricas(
  fijacionesCrudas: Fijacion[],
  aois: AreaInteres[],
  sesiones: SesionEvaluacion[],
): { porAoi: MetricaAoi[]; porParticipante: MetricaParticipante[] } {
  const fijaciones = asignarAois(fijacionesCrudas, aois)
  const totalDwell = fijaciones.reduce((s, f) => s + f.duracionMs, 0) || 1

  const porSesion = new Map<number, Fijacion[]>()
  for (const f of fijaciones) {
    if (!porSesion.has(f.sesionId)) porSesion.set(f.sesionId, [])
    porSesion.get(f.sesionId)!.push(f)
  }
  for (const lista of porSesion.values()) lista.sort((a, b) => a.ordenSecuencia - b.ordenSecuencia)

  const objetivos: (AreaInteres | null)[] = [...aois, null]
  const porAoi: MetricaAoi[] = objetivos.map((aoi) => {
    const id = aoi?.id ?? null
    const propias = fijaciones.filter((f) => f.aoiId === id)
    const dwell = propias.reduce((s, f) => s + f.duracionMs, 0)
    const ttffs: number[] = []
    let revisitas = 0
    for (const lista of porSesion.values()) {
      const primera = lista.find((f) => f.aoiId === id)
      if (primera) ttffs.push(primera.inicioMs)
      let visitas = 0
      let prev: number | null | undefined = undefined
      for (const f of lista) {
        if (f.aoiId === id && prev !== id) visitas++
        prev = f.aoiId
      }
      revisitas += Math.max(visitas - 1, 0)
    }
    return {
      aoiId: id,
      etiqueta: aoi?.etiqueta ?? 'Fuera de las AOIs',
      ttffMs: id === null || !ttffs.length ? null : Math.round(media(ttffs)),
      dwellTimeMs: dwell,
      dwellPct: Math.round((dwell / totalDwell) * 1000) / 10,
      conteoFijaciones: propias.length,
      revisitas: id === null ? null : revisitas,
      duracionMediaFijacionMs: Math.round(media(propias.map((f) => f.duracionMs))),
    }
  })

  const porParticipante: MetricaParticipante[] = sesiones
    .filter((s) => porSesion.has(s.id))
    .map((s) => {
      const lista = porSesion.get(s.id)!
      const ttffPorAoi: Record<string, number | null> = {}
      for (const a of aois) ttffPorAoi[a.id] = lista.find((f) => f.aoiId === a.id)?.inicioMs ?? null
      return {
        sesionId: s.id,
        codigoSujeto: s.codigoSujeto,
        conteoFijaciones: lista.length,
        duracionMediaFijacionMs: Math.round(media(lista.map((f) => f.duracionMs))),
        duracionTotalMs: s.duracionTotalMs,
        ttffPorAoi,
      }
    })

  return { porAoi, porParticipante }
}

/**
 * Algoritmo I-DT (umbral de dispersión) para agrupar muestras crudas en fijaciones.
 * Solo se usa en modo simulado; en producción lo ejecuta el backend.
 */
export function detectarFijacionesIDT(
  muestras: { t: number; x: number; y: number }[],
  dispersionMax = 0.035,
  duracionMinMs = 80,
): { inicioMs: number; finMs: number; x: number; y: number }[] {
  const res: { inicioMs: number; finMs: number; x: number; y: number }[] = []
  let i = 0
  const disp = (a: number, b: number) => {
    let minX = Infinity, maxX = -Infinity, minY = Infinity, maxY = -Infinity
    for (let k = a; k <= b; k++) {
      const m = muestras[k]
      minX = Math.min(minX, m.x); maxX = Math.max(maxX, m.x)
      minY = Math.min(minY, m.y); maxY = Math.max(maxY, m.y)
    }
    return maxX - minX + (maxY - minY)
  }
  while (i < muestras.length) {
    let j = i
    while (j < muestras.length && muestras[j].t - muestras[i].t < duracionMinMs) j++
    if (j >= muestras.length) break
    if (disp(i, j) <= dispersionMax) {
      while (j + 1 < muestras.length && disp(i, j + 1) <= dispersionMax) j++
      const grupo = muestras.slice(i, j + 1)
      res.push({
        inicioMs: grupo[0].t,
        finMs: grupo[grupo.length - 1].t,
        x: media(grupo.map((m) => m.x)),
        y: media(grupo.map((m) => m.y)),
      })
      i = j + 1
    } else {
      i++
    }
  }
  return res
}
