import type { AreaInteres, CoordenadasAoi, Fijacion, PuntoNormalizado, RectNormalizado } from '../api/types'

/**
 * Funciones de dibujo sobre Canvas 2D. Todas reciben coordenadas normalizadas [0,1]
 * y las proyectan al tamaño en pantalla (w × h) del estímulo.
 */

// Paleta clásica de mapa de calor: azul → cian → verde → amarillo → rojo
const STOPS: [number, [number, number, number]][] = [
  [0.0, [0, 60, 255]],
  [0.25, [0, 200, 255]],
  [0.5, [0, 230, 80]],
  [0.75, [255, 230, 0]],
  [1.0, [255, 30, 0]],
]

let lut: Uint8ClampedArray | null = null
function colorLut(): Uint8ClampedArray {
  if (lut) return lut
  lut = new Uint8ClampedArray(256 * 3)
  for (let i = 0; i < 256; i++) {
    const v = i / 255
    let k = 0
    while (k < STOPS.length - 2 && v > STOPS[k + 1][0]) k++
    const [v0, c0] = STOPS[k]
    const [v1, c1] = STOPS[k + 1]
    const t = Math.min(1, Math.max(0, (v - v0) / (v1 - v0)))
    for (let c = 0; c < 3; c++) lut[i * 3 + c] = c0[c] + (c1[c] - c0[c]) * t
  }
  return lut
}

/** Gradiente CSS equivalente, para la leyenda. */
export const HEATMAP_CSS_GRADIENT = `linear-gradient(90deg, ${STOPS.map(([v, c]) => `rgb(${c.join(',')}) ${v * 100}%`).join(', ')})`

export interface OpcionesHeatmap {
  /** Desviación del kernel gaussiano, en píxeles de pantalla. */
  sigmaPx: number
  /** Opacidad máxima 0..1 */
  opacidad: number
}

/**
 * Mapa de calor por acumulación de kernels gaussianos ponderados por duración.
 * Se calcula a 1/4 de resolución y se escala con suavizado para mantenerlo fluido.
 */
export function dibujarHeatmap(ctx: CanvasRenderingContext2D, fijaciones: Fijacion[], w: number, h: number, o: OpcionesHeatmap) {
  if (!fijaciones.length || w < 4 || h < 4) return
  const f = 4
  const gw = Math.ceil(w / f)
  const gh = Math.ceil(h / f)
  const grid = new Float32Array(gw * gh)
  const s = Math.max(1, o.sigmaPx / f)
  const r = Math.ceil(s * 3)
  const kernel = new Float32Array((2 * r + 1) ** 2)
  for (let dy = -r; dy <= r; dy++)
    for (let dx = -r; dx <= r; dx++)
      kernel[(dy + r) * (2 * r + 1) + dx + r] = Math.exp(-(dx * dx + dy * dy) / (2 * s * s))

  for (const fx of fijaciones) {
    const cx = Math.round((fx.xNormalizado * w) / f)
    const cy = Math.round((fx.yNormalizado * h) / f)
    const peso = fx.duracionMs
    for (let dy = -r; dy <= r; dy++) {
      const y = cy + dy
      if (y < 0 || y >= gh) continue
      for (let dx = -r; dx <= r; dx++) {
        const x = cx + dx
        if (x < 0 || x >= gw) continue
        grid[y * gw + x] += kernel[(dy + r) * (2 * r + 1) + dx + r] * peso
      }
    }
  }
  let max = 0
  for (let i = 0; i < grid.length; i++) if (grid[i] > max) max = grid[i]
  if (max <= 0) return

  const off = document.createElement('canvas')
  off.width = gw
  off.height = gh
  const octx = off.getContext('2d')!
  const img = octx.createImageData(gw, gh)
  const L = colorLut()
  for (let i = 0; i < grid.length; i++) {
    const v = grid[i] / max
    if (v < 0.02) continue
    const idx = Math.min(255, Math.round(v * 255))
    img.data[i * 4] = L[idx * 3]
    img.data[i * 4 + 1] = L[idx * 3 + 1]
    img.data[i * 4 + 2] = L[idx * 3 + 2]
    img.data[i * 4 + 3] = Math.round(Math.min(1, v * 2.2) * o.opacidad * 255)
  }
  octx.putImageData(img, 0, 0)
  ctx.save()
  ctx.imageSmoothingEnabled = true
  ctx.imageSmoothingQuality = 'high'
  ctx.drawImage(off, 0, 0, gw * f, gh * f)
  ctx.restore()
}

export const COLORES_SESION = ['#38bdf8', '#f472b6', '#facc15', '#4ade80', '#fb923c', '#a78bfa', '#2dd4bf', '#f87171', '#c084fc', '#a3e635', '#60a5fa', '#fbbf24', '#e879f9', '#34d399', '#f97316']

function flecha(ctx: CanvasRenderingContext2D, x1: number, y1: number, x2: number, y2: number, r1: number, r2: number) {
  const ang = Math.atan2(y2 - y1, x2 - x1)
  const sx = x1 + Math.cos(ang) * r1
  const sy = y1 + Math.sin(ang) * r1
  const ex = x2 - Math.cos(ang) * r2
  const ey = y2 - Math.sin(ang) * r2
  if (Math.hypot(ex - sx, ey - sy) < 4) return
  ctx.beginPath()
  ctx.moveTo(sx, sy)
  ctx.lineTo(ex, ey)
  ctx.stroke()
  const t = 7
  ctx.beginPath()
  ctx.moveTo(ex, ey)
  ctx.lineTo(ex - t * Math.cos(ang - 0.45), ey - t * Math.sin(ang - 0.45))
  ctx.lineTo(ex - t * Math.cos(ang + 0.45), ey - t * Math.sin(ang + 0.45))
  ctx.closePath()
  ctx.fill()
}

/** Ruta de mirada: círculos proporcionales a la duración, numerados y unidos con flechas. */
export function dibujarScanpath(
  ctx: CanvasRenderingContext2D,
  fijaciones: Fijacion[],
  w: number,
  h: number,
  colorDeSesion: (sesionId: number) => string,
  escala: number,
) {
  const porSesion = new Map<number, Fijacion[]>()
  for (const f of fijaciones) {
    if (!porSesion.has(f.sesionId)) porSesion.set(f.sesionId, [])
    porSesion.get(f.sesionId)!.push(f)
  }
  const varias = porSesion.size > 1
  const radio = (d: number) => Math.max(6, Math.min(34, Math.sqrt(d) * 1.1 * Math.max(escala, 0.45)))
  ctx.save()
  ctx.font = `600 ${Math.max(10, Math.round(11 * Math.max(escala, 0.8)))}px Inter, Segoe UI, sans-serif`
  ctx.textAlign = 'center'
  ctx.textBaseline = 'middle'
  for (const [sid, lista] of porSesion) {
    lista.sort((a, b) => a.ordenSecuencia - b.ordenSecuencia)
    const color = colorDeSesion(sid)
    ctx.globalAlpha = varias ? 0.75 : 1
    ctx.strokeStyle = color
    ctx.fillStyle = color
    ctx.lineWidth = 2
    for (let i = 1; i < lista.length; i++) {
      const a = lista[i - 1], b = lista[i]
      flecha(ctx, a.xNormalizado * w, a.yNormalizado * h, b.xNormalizado * w, b.yNormalizado * h, radio(a.duracionMs), radio(b.duracionMs))
    }
    for (const f of lista) {
      const x = f.xNormalizado * w, y = f.yNormalizado * h, r = radio(f.duracionMs)
      ctx.globalAlpha = varias ? 0.55 : 0.82
      ctx.fillStyle = color
      ctx.beginPath()
      ctx.arc(x, y, r, 0, Math.PI * 2)
      ctx.fill()
      ctx.globalAlpha = 1
      ctx.strokeStyle = 'rgba(15,23,42,.85)'
      ctx.lineWidth = 1.5
      ctx.stroke()
      if (r >= 8) {
        ctx.fillStyle = '#0b1220'
        ctx.fillText(String(f.ordenSecuencia), x, y + 0.5)
      }
      ctx.strokeStyle = color
      ctx.lineWidth = 2
    }
  }
  ctx.restore()
}

/** Distribución de fijaciones: puntos limpios, sin líneas ni gradientes. */
export function dibujarFijaciones(
  ctx: CanvasRenderingContext2D,
  fijaciones: Fijacion[],
  w: number,
  h: number,
  colorDeSesion: (sesionId: number) => string,
) {
  ctx.save()
  for (const f of fijaciones) {
    ctx.beginPath()
    ctx.arc(f.xNormalizado * w, f.yNormalizado * h, 4, 0, Math.PI * 2)
    ctx.fillStyle = colorDeSesion(f.sesionId)
    ctx.globalAlpha = 0.9
    ctx.fill()
    ctx.globalAlpha = 1
    ctx.strokeStyle = 'rgba(15,23,42,.9)'
    ctx.lineWidth = 1
    ctx.stroke()
  }
  ctx.restore()
}

export const COLORES_AOI = ['#ef4444', '#22c55e', '#3b82f6', '#f59e0b', '#a855f7', '#06b6d4', '#ec4899', '#84cc16']

export function colorAoi(aois: AreaInteres[], id: number | null) {
  if (id === null) return '#64748b'
  const i = aois.findIndex((a) => a.id === id)
  return aois[i]?.color ?? COLORES_AOI[i % COLORES_AOI.length]
}

export function rectPantalla(r: RectNormalizado, w: number, h: number) {
  return { x: r.x * w, y: r.y * h, w: r.ancho * w, h: r.alto * h }
}

/** Traza el contorno de la AOI (rectángulo o polígono) como path actual. */
function trazar(ctx: CanvasRenderingContext2D, c: CoordenadasAoi, w: number, h: number) {
  ctx.beginPath()
  if (c.puntos && c.puntos.length >= 3) {
    c.puntos.forEach((p, i) => (i ? ctx.lineTo(p.x * w, p.y * h) : ctx.moveTo(p.x * w, p.y * h)))
    ctx.closePath()
  } else {
    ctx.rect(c.x * w, c.y * h, c.ancho * w, c.alto * h)
  }
}

function tirador(ctx: CanvasRenderingContext2D, x: number, y: number, color: string, redondo = false) {
  ctx.fillStyle = '#fff'
  ctx.strokeStyle = color
  ctx.lineWidth = 1.5
  ctx.beginPath()
  if (redondo) ctx.arc(x, y, 4.5, 0, Math.PI * 2)
  else ctx.rect(x - 4, y - 4, 8, 8)
  ctx.fill()
  ctx.stroke()
}

/** Borrador en curso: rectángulo arrastrado o polígono con sus vértices y la línea hacia el cursor. */
export interface BorradorAoi {
  rect?: RectNormalizado | null
  puntos?: PuntoNormalizado[]
  cursor?: PuntoNormalizado | null
  /** El cursor está sobre el primer vértice: soltar cierra el polígono. */
  cerrable?: boolean
}

export interface OpcionesAois {
  seleccionada: number | null
  ocultas: Set<number>
  borrador?: BorradorAoi | null
  /** Sufijo para la etiqueta de la AOI seleccionada, p. ej. " (Editando)". */
  sufijoSeleccion?: string
  colorBorrador?: string
}

/** Delimitadores de AOI con etiqueta; la seleccionada muestra tiradores en esquinas y, si es polígono, en vértices. */
export function dibujarAois(ctx: CanvasRenderingContext2D, aois: AreaInteres[], w: number, h: number, o: OpcionesAois) {
  ctx.save()
  ctx.font = '600 12px Inter, Segoe UI, sans-serif'
  ctx.textBaseline = 'middle'
  aois.forEach((a, i) => {
    if (o.ocultas.has(a.id)) return
    const color = a.color ?? COLORES_AOI[i % COLORES_AOI.length]
    const c = a.coordenadas
    const r = rectPantalla(c, w, h)
    const sel = a.id === o.seleccionada
    trazar(ctx, c, w, h)
    ctx.fillStyle = color + (sel ? '26' : '1f')
    ctx.fill()
    ctx.strokeStyle = color
    ctx.lineWidth = 2
    ctx.stroke()
    const etiqueta = `AOI ${i + 1}: ${a.etiqueta}${sel && o.sufijoSeleccion ? o.sufijoSeleccion : ''}`
    const tw = ctx.measureText(etiqueta).width + 16
    const ly = r.y - 24 >= 0 ? r.y - 24 : r.y
    ctx.fillStyle = color
    ctx.beginPath()
    ctx.roundRect(r.x, ly, tw, 22, 3)
    ctx.fill()
    ctx.fillStyle = '#fff'
    ctx.fillText(etiqueta, r.x + 8, ly + 11.5)
    if (sel) {
      if (c.puntos) {
        ctx.setLineDash([4, 4])
        ctx.strokeStyle = color + '99'
        ctx.lineWidth = 1
        ctx.strokeRect(r.x, r.y, r.w, r.h)
        ctx.setLineDash([])
        for (const p of c.puntos) tirador(ctx, p.x * w, p.y * h, color, true)
      }
      for (const [hx, hy] of esquinas(r)) tirador(ctx, hx, hy, color)
    }
  })

  const b = o.borrador
  const cb = o.colorBorrador ?? '#38bdf8'
  if (b?.rect) {
    const r = rectPantalla(b.rect, w, h)
    ctx.setLineDash([6, 4])
    ctx.strokeStyle = cb
    ctx.lineWidth = 2
    ctx.fillStyle = cb + '1f'
    ctx.fillRect(r.x, r.y, r.w, r.h)
    ctx.strokeRect(r.x, r.y, r.w, r.h)
    ctx.setLineDash([])
  }
  if (b?.puntos?.length) {
    const pts = b.puntos
    ctx.beginPath()
    pts.forEach((p, i) => (i ? ctx.lineTo(p.x * w, p.y * h) : ctx.moveTo(p.x * w, p.y * h)))
    if (pts.length >= 3) {
      ctx.save()
      ctx.closePath()
      ctx.fillStyle = cb + '1f'
      ctx.fill()
      ctx.restore()
    }
    ctx.strokeStyle = cb
    ctx.lineWidth = 2
    ctx.stroke()
    if (b.cursor) {
      const u = pts[pts.length - 1]
      ctx.setLineDash([5, 4])
      ctx.beginPath()
      ctx.moveTo(u.x * w, u.y * h)
      ctx.lineTo(b.cursor.x * w, b.cursor.y * h)
      ctx.stroke()
      ctx.setLineDash([])
    }
    pts.forEach((p, i) => {
      if (i === 0 && b.cerrable) {
        ctx.beginPath()
        ctx.arc(p.x * w, p.y * h, 9, 0, Math.PI * 2)
        ctx.strokeStyle = cb
        ctx.lineWidth = 2
        ctx.stroke()
      }
      tirador(ctx, p.x * w, p.y * h, cb, true)
    })
  }
  ctx.restore()
}

export function esquinas(r: { x: number; y: number; w: number; h: number }): [number, number][] {
  return [
    [r.x, r.y],
    [r.x + r.w, r.y],
    [r.x, r.y + r.h],
    [r.x + r.w, r.y + r.h],
  ]
}

/** Fijaciones de referencia: puntos tenues que orientan al investigador sin competir con las AOIs. */
export function dibujarReferencia(ctx: CanvasRenderingContext2D, fijaciones: Fijacion[], w: number, h: number) {
  ctx.save()
  ctx.fillStyle = 'rgba(234, 179, 8, 0.55)'
  for (const f of fijaciones) {
    ctx.beginPath()
    ctx.arc(f.xNormalizado * w, f.yNormalizado * h, 2.6, 0, Math.PI * 2)
    ctx.fill()
  }
  ctx.restore()
}

/** Rejilla guía con el paso de ajuste (en coordenadas normalizadas). */
export function dibujarRejilla(ctx: CanvasRenderingContext2D, w: number, h: number, pasoX: number, pasoY: number) {
  ctx.save()
  ctx.strokeStyle = 'rgba(2, 132, 199, 0.18)'
  ctx.lineWidth = 1
  ctx.beginPath()
  for (let x = pasoX; x < 1; x += pasoX) {
    const px = Math.round(x * w) + 0.5
    ctx.moveTo(px, 0)
    ctx.lineTo(px, h)
  }
  for (let y = pasoY; y < 1; y += pasoY) {
    const py = Math.round(y * h) + 0.5
    ctx.moveTo(0, py)
    ctx.lineTo(w, py)
  }
  ctx.stroke()
  ctx.restore()
}
