import { useCallback, useEffect, useLayoutEffect, useRef, useState } from 'react'
import type { AreaInteres, CoordenadasAoi, Fijacion, FormaGeometrica, PuntoNormalizado, RectNormalizado } from '../api/types'
import {
  dibujarAois, dibujarFijaciones, dibujarHeatmap, dibujarReferencia, dibujarRejilla, dibujarScanpath, esquinas, rectPantalla,
} from './render'
import { ajustar, area, dentro, escalarA, moverA, moverVertice, poligono } from '../lib/geometry'
import './StimulusStage.css'

export type ModoVista = 'heatmap' | 'scanpath' | 'fijaciones'
export type Herramienta = 'seleccionar' | 'rectangulo' | 'poligono' | 'mano'

export interface Rejilla {
  pasoX: number
  pasoY: number
}

interface Props {
  urlImagen: string
  anchoOriginal: number
  altoOriginal: number
  fijaciones: Fijacion[]
  /** null = sin capa analítica (p. ej. en el editor de AOIs). */
  modo: ModoVista | null
  colorDeSesion: (sesionId: number) => string
  /** Fijaciones de referencia dibujadas como puntos tenues. */
  referencia?: Fijacion[] | null
  aois: AreaInteres[]
  aoisOcultas: Set<number>
  aoisBloqueadas?: Set<number>
  mostrarAois: boolean
  mostrarImagen: boolean
  aoiSeleccionada: number | null
  herramienta: Herramienta
  rejilla?: Rejilla | null
  opacidad?: number
  radioPx?: number
  zoom: number
  sufijoSeleccion?: string
  colorBorrador?: string
  variante?: 'normal' | 'editor'
  onSeleccionarAoi: (id: number | null) => void
  onCrearAoi: (c: CoordenadasAoi, forma: FormaGeometrica) => void
  onActualizarAoi: (a: AreaInteres) => void
  onCancelarDibujo?: () => void
  onEscala?: (escala: number) => void
  onCursor?: (p: PuntoNormalizado | null) => void
  canvasRef?: React.RefObject<HTMLCanvasElement | null>
}

type Arrastre =
  | { tipo: 'nuevo'; x0: number; y0: number; actual: RectNormalizado }
  | { tipo: 'mover'; id: number; dx: number; dy: number; actual: CoordenadasAoi }
  | { tipo: 'redimensionar'; id: number; ancla: [number, number]; original: CoordenadasAoi; actual: CoordenadasAoi }
  | { tipo: 'vertice'; id: number; indice: number; actual: CoordenadasAoi }
  | { tipo: 'paneo'; cx: number; cy: number; sl: number; st: number }

const clamp01 = (v: number) => Math.min(1, Math.max(0, v))
const rectDesdePuntos = (ax: number, ay: number, bx: number, by: number): RectNormalizado => ({
  x: clamp01(Math.min(ax, bx)),
  y: clamp01(Math.min(ay, by)),
  ancho: Math.abs(bx - ax),
  alto: Math.abs(by - ay),
})

function prepararCanvas(c: HTMLCanvasElement, w: number, h: number) {
  const dpr = window.devicePixelRatio || 1
  c.width = Math.round(w * dpr)
  c.height = Math.round(h * dpr)
  c.style.width = `${w}px`
  c.style.height = `${h}px`
  const ctx = c.getContext('2d')!
  ctx.setTransform(dpr, 0, 0, dpr, 0, 0)
  ctx.clearRect(0, 0, w, h)
  return ctx
}

const RADIO_TIRADOR = 7

/**
 * Lienzo del estímulo: captura de la interfaz + capa de visualización + capa interactiva de AOIs.
 * Herramientas: seleccionar (mover, redimensionar, editar vértices), rectángulo, polígono y mano (paneo).
 */
export function StimulusStage(p: Props) {
  const contenedor = useRef<HTMLDivElement>(null)
  const vizInterna = useRef<HTMLCanvasElement>(null)
  const viz = p.canvasRef ?? vizInterna
  const capaAoi = useRef<HTMLCanvasElement>(null)
  const [ajuste, setAjuste] = useState(0)
  const [arrastre, setArrastre] = useState<Arrastre | null>(null)
  const [poli, setPoli] = useState<PuntoNormalizado[]>([])
  const [cursorPt, setCursorPt] = useState<PuntoNormalizado | null>(null)
  const [cursor, setCursor] = useState('default')
  const bloqueadas = p.aoisBloqueadas ?? new Set<number>()

  useLayoutEffect(() => {
    const el = contenedor.current
    if (!el) return
    const medir = () => {
      const pad = 48
      setAjuste(Math.max(0.05, Math.min((el.clientWidth - pad) / p.anchoOriginal, (el.clientHeight - pad) / p.altoOriginal)))
    }
    medir()
    const ro = new ResizeObserver(medir)
    ro.observe(el)
    return () => ro.disconnect()
  }, [p.anchoOriginal, p.altoOriginal])

  const escala = ajuste * p.zoom
  const w = Math.round(p.anchoOriginal * escala)
  const h = Math.round(p.altoOriginal * escala)
  const { onEscala } = p
  useEffect(() => { if (ajuste) onEscala?.(escala) }, [escala, ajuste, onEscala])

  // Al cambiar de herramienta se descarta el polígono en curso
  const [herramientaPrevia, setHerramientaPrevia] = useState(p.herramienta)
  if (p.herramienta !== herramientaPrevia) {
    setHerramientaPrevia(p.herramienta)
    setPoli([])
  }

  const paso = p.rejilla ?? null
  const snap = useCallback(
    (pt: PuntoNormalizado): PuntoNormalizado => (paso ? { x: ajustar(pt.x, paso.pasoX), y: ajustar(pt.y, paso.pasoY) } : pt),
    [paso],
  )

  const cerrarPoligono = useCallback(
    (pts: PuntoNormalizado[]) => {
      // Un doble clic añade dos veces el último punto: se eliminan duplicados consecutivos
      const limpios = pts.filter((q, i) => i === 0 || Math.hypot(q.x - pts[i - 1].x, q.y - pts[i - 1].y) > 1e-4)
      if (limpios.length >= 3) {
        const c = poligono(limpios)
        if (c.ancho > 0.003 && c.alto > 0.003) p.onCrearAoi(c, 'POLIGONO')
      }
      setPoli([])
    },
    [p],
  )

  // Teclado del polígono en curso (fase de captura para adelantarse a los atajos de la página)
  useEffect(() => {
    if (p.herramienta !== 'poligono' || !poli.length) return
    const k = (e: KeyboardEvent) => {
      const t = e.target as HTMLElement
      if (t.tagName === 'INPUT' || t.tagName === 'TEXTAREA') return
      if (e.key === 'Enter') { e.preventDefault(); e.stopImmediatePropagation(); cerrarPoligono(poli) }
      else if (e.key === 'Backspace' || e.key === 'Delete') { e.preventDefault(); e.stopImmediatePropagation(); setPoli((l) => l.slice(0, -1)) }
      else if (e.key === 'Escape') { e.stopImmediatePropagation(); setPoli([]) }
    }
    window.addEventListener('keydown', k, true)
    return () => window.removeEventListener('keydown', k, true)
  }, [p.herramienta, poli, cerrarPoligono])

  // Capa de visualización
  useEffect(() => {
    const c = viz.current
    if (!c || !w) return
    const ctx = prepararCanvas(c, w, h)
    if (p.modo === 'heatmap') dibujarHeatmap(ctx, p.fijaciones, w, h, { sigmaPx: (p.radioPx ?? 35) * escala, opacidad: p.opacidad ?? 0.75 })
    else if (p.modo === 'scanpath') dibujarScanpath(ctx, p.fijaciones, w, h, p.colorDeSesion, escala)
    else if (p.modo === 'fijaciones') dibujarFijaciones(ctx, p.fijaciones, w, h, p.colorDeSesion)
    if (p.referencia?.length) dibujarReferencia(ctx, p.referencia, w, h)
  }, [viz, w, h, escala, p.modo, p.fijaciones, p.opacidad, p.radioPx, p.colorDeSesion, p.referencia])

  const enEdicion = arrastre && arrastre.tipo !== 'nuevo' && arrastre.tipo !== 'paneo' ? arrastre : null
  const aoisVisibles = enEdicion ? p.aois.map((a) => (a.id === enEdicion.id ? { ...a, coordenadas: enEdicion.actual } : a)) : p.aois

  const primerVertice = poli[0]
  const cerrable = !!(primerVertice && cursorPt && poli.length >= 3 &&
    Math.hypot((cursorPt.x - primerVertice.x) * w, (cursorPt.y - primerVertice.y) * h) <= RADIO_TIRADOR + 3)

  useEffect(() => {
    const c = capaAoi.current
    if (!c || !w) return
    const ctx = prepararCanvas(c, w, h)
    if (paso) dibujarRejilla(ctx, w, h, paso.pasoX, paso.pasoY)
    if (p.mostrarAois || arrastre?.tipo === 'nuevo' || poli.length) {
      dibujarAois(ctx, p.mostrarAois ? aoisVisibles : [], w, h, {
        seleccionada: p.aoiSeleccionada,
        ocultas: p.aoisOcultas,
        sufijoSeleccion: p.sufijoSeleccion,
        colorBorrador: p.colorBorrador,
        borrador: {
          rect: arrastre?.tipo === 'nuevo' ? arrastre.actual : null,
          puntos: poli,
          cursor: poli.length ? cursorPt : null,
          cerrable,
        },
      })
    }
  }, [w, h, aoisVisibles, p.aoiSeleccionada, p.aoisOcultas, p.mostrarAois, arrastre, poli, cursorPt, cerrable, paso, p.sufijoSeleccion, p.colorBorrador])

  const posicion = useCallback((e: React.PointerEvent | React.MouseEvent) => {
    const r = capaAoi.current!.getBoundingClientRect()
    return { x: clamp01((e.clientX - r.left) / r.width), y: clamp01((e.clientY - r.top) / r.height) }
  }, [])

  const seleccionada = p.aois.find((a) => a.id === p.aoiSeleccionada && !p.aoisOcultas.has(a.id))
  const editable = (a?: AreaInteres) => !!a && p.mostrarAois && !bloqueadas.has(a.id)

  const verticeEn = (x: number, y: number) => {
    const pts = seleccionada?.coordenadas.puntos
    if (!editable(seleccionada) || !pts) return -1
    return pts.findIndex((q) => Math.hypot((q.x - x) * w, (q.y - y) * h) <= RADIO_TIRADOR)
  }

  const esquinaEn = (x: number, y: number) => {
    if (!editable(seleccionada)) return null
    const pts = esquinas(rectPantalla(seleccionada!.coordenadas, w, h))
    const i = pts.findIndex(([hx, hy]) => Math.abs(hx - x * w) <= RADIO_TIRADOR && Math.abs(hy - y * h) <= RADIO_TIRADOR)
    if (i < 0) return null
    const [ox, oy] = pts[3 - i]
    return { aoi: seleccionada!, ancla: [ox / w, oy / h] as [number, number] }
  }

  const aoiEn = (x: number, y: number) =>
    [...p.aois]
      .filter((a) => !p.aoisOcultas.has(a.id) && dentro(a.coordenadas, x, y))
      .sort((a, b) => area(a.coordenadas) - area(b.coordenadas))[0]

  const alPresionar = (e: React.PointerEvent) => {
    if (e.button !== 0) return
    const pt = posicion(e)
    capaAoi.current!.setPointerCapture(e.pointerId)

    if (p.herramienta === 'mano') {
      const el = contenedor.current!
      setArrastre({ tipo: 'paneo', cx: e.clientX, cy: e.clientY, sl: el.scrollLeft, st: el.scrollTop })
      return
    }
    if (p.herramienta === 'rectangulo') {
      const s = snap(pt)
      setArrastre({ tipo: 'nuevo', x0: s.x, y0: s.y, actual: { x: s.x, y: s.y, ancho: 0, alto: 0 } })
      return
    }
    if (p.herramienta === 'poligono') {
      if (cerrable) return cerrarPoligono(poli)
      setPoli((l) => [...l, snap(pt)])
      return
    }
    if (!p.mostrarAois) return
    const v = verticeEn(pt.x, pt.y)
    if (v >= 0) {
      setArrastre({ tipo: 'vertice', id: seleccionada!.id, indice: v, actual: seleccionada!.coordenadas })
      return
    }
    const t = esquinaEn(pt.x, pt.y)
    if (t) {
      setArrastre({ tipo: 'redimensionar', id: t.aoi.id, ancla: t.ancla, original: t.aoi.coordenadas, actual: t.aoi.coordenadas })
      return
    }
    const a = aoiEn(pt.x, pt.y)
    p.onSeleccionarAoi(a?.id ?? null)
    if (a && !bloqueadas.has(a.id)) {
      setArrastre({ tipo: 'mover', id: a.id, dx: pt.x - a.coordenadas.x, dy: pt.y - a.coordenadas.y, actual: a.coordenadas })
    }
  }

  const alMover = (e: React.PointerEvent) => {
    const pt = posicion(e)
    setCursorPt(pt)
    p.onCursor?.(pt)
    if (!arrastre) {
      if (p.herramienta === 'mano') setCursor('grab')
      else if (p.herramienta !== 'seleccionar') setCursor(cerrable ? 'pointer' : 'crosshair')
      else if (verticeEn(pt.x, pt.y) >= 0) setCursor('move')
      else if (esquinaEn(pt.x, pt.y)) setCursor('nwse-resize')
      else {
        const a = p.mostrarAois ? aoiEn(pt.x, pt.y) : undefined
        setCursor(a ? (bloqueadas.has(a.id) ? 'not-allowed' : 'move') : 'default')
      }
      return
    }
    switch (arrastre.tipo) {
      case 'paneo': {
        const el = contenedor.current!
        el.scrollLeft = arrastre.sl - (e.clientX - arrastre.cx)
        el.scrollTop = arrastre.st - (e.clientY - arrastre.cy)
        setCursor('grabbing')
        break
      }
      case 'nuevo': {
        const s = snap(pt)
        setArrastre({ ...arrastre, actual: rectDesdePuntos(arrastre.x0, arrastre.y0, s.x, s.y) })
        break
      }
      case 'mover': {
        const s = snap({ x: pt.x - arrastre.dx, y: pt.y - arrastre.dy })
        setArrastre({ ...arrastre, actual: moverA(arrastre.actual, s.x, s.y) })
        break
      }
      case 'redimensionar': {
        const s = snap(pt)
        setArrastre({ ...arrastre, actual: escalarA(arrastre.original, rectDesdePuntos(arrastre.ancla[0], arrastre.ancla[1], s.x, s.y)) })
        break
      }
      case 'vertice':
        setArrastre({ ...arrastre, actual: moverVertice(arrastre.actual, arrastre.indice, snap(pt)) })
        break
    }
  }

  const alSoltar = () => {
    if (!arrastre) return
    const minimo = 12 / Math.max(w, 1)
    if (arrastre.tipo === 'nuevo') {
      if (arrastre.actual.ancho > minimo && arrastre.actual.alto > minimo) p.onCrearAoi(arrastre.actual, 'RECTANGULO')
    } else if (arrastre.tipo !== 'paneo') {
      const original = p.aois.find((a) => a.id === arrastre.id)
      const cambio = original && JSON.stringify(original.coordenadas) !== JSON.stringify(arrastre.actual)
      if (original && cambio && arrastre.actual.ancho > minimo / 2 && arrastre.actual.alto > minimo / 2) {
        p.onActualizarAoi({ ...original, coordenadas: arrastre.actual })
      }
    }
    setArrastre(null)
  }

  return (
    <div className={`stage ${p.variante === 'editor' ? 'stage-editor' : ''}`} ref={contenedor}>
      {ajuste > 0 && (
        <div className="stage-frame" style={{ width: w, height: h }}>
          <img
            src={p.urlImagen}
            alt="Captura del estímulo evaluado"
            draggable={false}
            style={{ width: w, height: h, opacity: p.mostrarImagen ? 1 : 0 }}
          />
          <canvas ref={viz} className="stage-layer" aria-hidden />
          <canvas
            ref={capaAoi}
            className="stage-layer stage-interactive"
            style={{ cursor }}
            onPointerDown={alPresionar}
            onPointerMove={alMover}
            onPointerUp={alSoltar}
            onPointerLeave={() => { setCursorPt(null); p.onCursor?.(null) }}
            onPointerCancel={() => setArrastre(null)}
            onDoubleClick={() => p.herramienta === 'poligono' && poli.length >= 3 && cerrarPoligono(poli)}
            aria-label="Lienzo de áreas de interés"
          />
        </div>
      )}
    </div>
  )
}
