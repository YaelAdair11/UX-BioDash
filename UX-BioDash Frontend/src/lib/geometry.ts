import type { CoordenadasAoi, PuntoNormalizado, RectNormalizado } from '../api/types'

/**
 * Geometría de AOIs en coordenadas normalizadas [0,1].
 * Rectángulos y polígonos comparten la caja envolvente; las transformaciones
 * (mover, redimensionar) se aplican a la caja y, si existen, a los vértices.
 */

const clamp01 = (v: number) => Math.min(1, Math.max(0, v))

/** Ray casting: ¿el punto está dentro del polígono? */
function dentroPoligono(pts: PuntoNormalizado[], x: number, y: number) {
  let dentro = false
  for (let i = 0, j = pts.length - 1; i < pts.length; j = i++) {
    const a = pts[i], b = pts[j]
    if (a.y > y !== b.y > y && x < ((b.x - a.x) * (y - a.y)) / (b.y - a.y) + a.x) dentro = !dentro
  }
  return dentro
}

export function dentro(c: CoordenadasAoi, x: number, y: number) {
  if (x < c.x || x > c.x + c.ancho || y < c.y || y > c.y + c.alto) return false
  return c.puntos && c.puntos.length >= 3 ? dentroPoligono(c.puntos, x, y) : true
}

export function cajaDePuntos(pts: PuntoNormalizado[]): RectNormalizado {
  const xs = pts.map((p) => p.x), ys = pts.map((p) => p.y)
  const x = Math.min(...xs), y = Math.min(...ys)
  return { x, y, ancho: Math.max(...xs) - x, alto: Math.max(...ys) - y }
}

export function poligono(pts: PuntoNormalizado[]): CoordenadasAoi {
  const limpios = pts.map((p) => ({ x: clamp01(p.x), y: clamp01(p.y) }))
  return { ...cajaDePuntos(limpios), puntos: limpios }
}

/** Mueve la AOI completa a una nueva esquina superior izquierda. */
export function moverA(c: CoordenadasAoi, nx: number, ny: number): CoordenadasAoi {
  const x = Math.min(1 - c.ancho, Math.max(0, nx))
  const y = Math.min(1 - c.alto, Math.max(0, ny))
  const dx = x - c.x, dy = y - c.y
  return { ...c, x, y, puntos: c.puntos?.map((p) => ({ x: p.x + dx, y: p.y + dy })) }
}

/** Ajusta la AOI a una nueva caja envolvente, escalando los vértices proporcionalmente. */
export function escalarA(c: CoordenadasAoi, r: RectNormalizado): CoordenadasAoi {
  if (!c.puntos) return { ...r }
  const sx = c.ancho ? r.ancho / c.ancho : 1
  const sy = c.alto ? r.alto / c.alto : 1
  return { ...r, puntos: c.puntos.map((p) => ({ x: r.x + (p.x - c.x) * sx, y: r.y + (p.y - c.y) * sy })) }
}

/** Mueve un vértice concreto y recalcula la caja envolvente. */
export function moverVertice(c: CoordenadasAoi, i: number, p: PuntoNormalizado): CoordenadasAoi {
  if (!c.puntos) return c
  return poligono(c.puntos.map((q, k) => (k === i ? p : q)))
}

/** Ajusta un valor normalizado a la rejilla (paso en coordenadas normalizadas). */
export const ajustar = (v: number, paso: number | null) => (paso ? clamp01(Math.round(v / paso) * paso) : v)

export const area = (c: RectNormalizado) => c.ancho * c.alto
