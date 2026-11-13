import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { Toolbar, ToolbarButton, ToolbarGroup, ToolbarSeparator } from '@ui-sdk/react/toolbar'
import { Toggle, ToggleIcon } from '@ui-sdk/react/toggle'
import { Switch, SwitchThumb } from '@ui-sdk/react/switch'
import { Field, FieldLabel } from '@ui-sdk/react/field'
import { Input } from '@ui-sdk/react/input'
import { Button } from '@ui-sdk/react/button'
import { Pista } from '../ui/Pista'
import { confirmar } from '../ui/confirmar'
import {
  AlertTriangle, ArrowRight, Check, ChevronLeft, ChevronRight, Eye, EyeOff, Grid3x3, Hand, Layers, Loader2, Lock,
  LockOpen, Maximize, Minus, Monitor, MousePointer2, MousePointerClick, Pencil, Plus, Square, Pentagon, Trash2, ZoomIn,
} from 'lucide-react'
import { Logo } from '../components/AppHeader'
import { ColorPicker } from '../components/ColorPicker'
import { PALETA_AOI } from '../lib/paleta'
import { ThemeMenu } from '../components/ThemeMenu'
import { PhaseSwitcher } from '../components/PhaseSwitcher'
import { api } from '../api/services'
import { USE_MOCK, toErrorApi } from '../api/client'
import type { AreaInteres, CoordenadasAoi, ErrorApi, Estimulo, Fijacion, FormaGeometrica, Proyecto } from '../api/types'
import { StimulusStage, type Herramienta, type Rejilla } from '../viz/StimulusStage'
import { dentro } from '../lib/geometry'
import { num } from '../lib/format'
import { TECLA_ELIMINAR } from '../lib/plataforma'
import './EditorPage.css'

/** Colores que se asignan por turno a las AOIs nuevas: los 5 del diseño y después el resto de la paleta. */
const PALETA_EDITOR = PALETA_AOI.slice(0, 15)
const PASO_REJILLA_PX = 40

type Guardado = 'guardado' | 'guardando' | 'error'

const NOMBRE_HERRAMIENTA: Record<Herramienta, { titulo: string; ayuda: string }> = {
  seleccionar: { titulo: 'Selección', ayuda: 'Clic para seleccionar · arrastra para mover · esquinas para redimensionar · vértices para editar polígonos' },
  rectangulo: { titulo: 'Rectángulo AOI', ayuda: 'Haz clic y arrastra para delimitar un área · Esc para cancelar' },
  poligono: { titulo: 'Polígono libre AOI', ayuda: 'Clic para añadir vértices · Enter, doble clic o clic en el primer vértice para cerrar · Retroceso quita el último · Esc cancela' },
  mano: { titulo: 'Mano', ayuda: 'Arrastra para desplazar el lienzo cuando el zoom es mayor que la ventana' },
}

const leerBloqueadas = (estimuloId: number) => {
  try {
    return new Set<number>(JSON.parse(localStorage.getItem(`uxbiodash:bloqueadas:${estimuloId}`) ?? '[]'))
  } catch {
    return new Set<number>()
  }
}

export function EditorPage() {
  const { id } = useParams()
  const proyectoId = Number(id)
  const nav = useNavigate()

  const [proyecto, setProyecto] = useState<Proyecto | null>(null)
  const [estimulos, setEstimulos] = useState<Estimulo[]>([])
  const [indice, setIndice] = useState(0)
  const [aois, setAois] = useState<AreaInteres[]>([])
  const [fijaciones, setFijaciones] = useState<Fijacion[]>([])
  const [cargando, setCargando] = useState(true)
  const [error, setError] = useState<ErrorApi | null>(null)
  const [aviso, setAviso] = useState<string | null>(null)

  const [herramienta, setHerramienta] = useState<Herramienta>('rectangulo')
  const [seleccionada, setSeleccionada] = useState<number | null>(null)
  const [ocultas, setOcultas] = useState<Set<number>>(new Set())
  const [bloqueadas, setBloqueadas] = useState<Set<number>>(new Set())
  const [verReferencia, setVerReferencia] = useState(true)
  const [verRejilla, setVerRejilla] = useState(false)
  const [zoom, setZoom] = useState(1)
  const [escala, setEscala] = useState(1)
  const [cursor, setCursor] = useState<{ x: number; y: number } | null>(null)
  const [guardado, setGuardado] = useState<Guardado>('guardado')

  // Borrador de propiedades de la AOI seleccionada (se confirma con "Guardar cambios")
  const [etiqueta, setEtiqueta] = useState('')
  const [color, setColor] = useState(PALETA_EDITOR[0])
  const campoEtiqueta = useRef<HTMLInputElement>(null)
  const enfocarEtiqueta = (ms: number) => setTimeout(() => campoEtiqueta.current?.focus(), ms)

  const estimulo = estimulos[indice] ?? null

  // Ajustes de estado al cambiar de proyecto o de estímulo (durante el render, no en efectos)
  const [proyectoPrevio, setProyectoPrevio] = useState(proyectoId)
  if (proyectoId !== proyectoPrevio) {
    setProyectoPrevio(proyectoId)
    setCargando(true)
  }
  const [estimuloPrevio, setEstimuloPrevio] = useState<number | null>(null)
  if (estimulo && estimulo.id !== estimuloPrevio) {
    setEstimuloPrevio(estimulo.id)
    setSeleccionada(null)
    setOcultas(new Set())
    setBloqueadas(leerBloqueadas(estimulo.id))
  }

  useEffect(() => {
    let vivo = true
    Promise.all([api.obtenerProyecto(proyectoId), api.listarEstimulos(proyectoId)])
      .then(([p, e]) => { if (vivo) { setProyecto(p); setEstimulos(e) } })
      .catch((err) => vivo && setError(toErrorApi(err)))
      .finally(() => vivo && setCargando(false))
    return () => { vivo = false }
  }, [proyectoId])

  useEffect(() => {
    if (!estimulo) return
    let vivo = true
    Promise.all([api.listarAois(estimulo.id), api.listarFijaciones(estimulo.id)])
      .then(([a, f]) => { if (vivo) { setAois(a); setFijaciones(f) } })
      .catch((err) => vivo && setAviso(toErrorApi(err).mensaje))
    return () => { vivo = false }
  }, [estimulo])

  useEffect(() => {
    if (!estimulo) return
    try { localStorage.setItem(`uxbiodash:bloqueadas:${estimulo.id}`, JSON.stringify([...bloqueadas])) } catch { /* sin almacenamiento */ }
  }, [bloqueadas, estimulo])

  const actual = aois.find((a) => a.id === seleccionada) ?? null
  // Al seleccionar otra AOI se carga su etiqueta y color en el borrador de propiedades
  const [actualPrevia, setActualPrevia] = useState<number | null>(null)
  if ((actual?.id ?? null) !== actualPrevia) {
    setActualPrevia(actual?.id ?? null)
    if (actual) { setEtiqueta(actual.etiqueta); setColor(actual.color ?? PALETA_EDITOR[0]) }
  }

  const colorDe = useCallback((a: AreaInteres, i: number) => a.color ?? PALETA_EDITOR[i % PALETA_EDITOR.length], [])
  const sucio = !!actual && (etiqueta.trim() !== actual.etiqueta
    || color.toLowerCase() !== colorDe(actual, aois.indexOf(actual)).toLowerCase())

  // AOIs con el color en borrador aplicado para previsualizar en el lienzo
  const aoisVista = useMemo(
    () => aois.map((a, i) => ({ ...a, color: a.id === seleccionada ? color : colorDe(a, i) })),
    [aois, seleccionada, color, colorDe],
  )

  const conteo = useMemo(() => {
    const m = new Map<number, number>()
    for (const a of aois) m.set(a.id, fijaciones.filter((f) => dentro(a.coordenadas, f.xNormalizado, f.yNormalizado)).length)
    return m
  }, [aois, fijaciones])

  const rejilla: Rejilla | null = estimulo && verRejilla
    ? { pasoX: PASO_REJILLA_PX / estimulo.anchoOriginalPx, pasoY: PASO_REJILLA_PX / estimulo.altoOriginalPx }
    : null

  // ───── Persistencia ─────
  const guardar = async <T,>(op: () => Promise<T>): Promise<T | null> => {
    setGuardado('guardando')
    try {
      const r = await op()
      setGuardado('guardado')
      return r
    } catch (err) {
      setGuardado('error')
      setAviso(toErrorApi(err).mensaje)
      return null
    }
  }

  const siguienteColor = PALETA_EDITOR[aois.length % PALETA_EDITOR.length]

  const crearAoi = async (c: CoordenadasAoi, forma: FormaGeometrica) => {
    if (!estimulo) return
    const nueva = await guardar(() =>
      api.crearAoi({ estimuloId: estimulo.id, etiqueta: `Nueva AOI ${aois.length + 1}`, formaGeometrica: forma, coordenadas: c, color: siguienteColor }),
    )
    if (nueva) {
      setAois((l) => [...l, nueva])
      setSeleccionada(nueva.id)
      setHerramienta('seleccionar')
      enfocarEtiqueta(50)
    }
  }

  const actualizarAoi = async (a: AreaInteres) => {
    const previa = aois
    setAois((l) => l.map((x) => (x.id === a.id ? a : x)))
    const r = await guardar(() => api.actualizarAoi(a))
    if (!r) setAois(previa)
  }

  const guardarPropiedades = () => {
    if (!actual || !sucio || !etiqueta.trim()) return
    actualizarAoi({ ...actual, etiqueta: etiqueta.trim(), color })
  }

  const eliminarAoi = async (a: AreaInteres) => {
    if (!(await confirmar(`¿Eliminar la AOI «${a.etiqueta}»?`, 'Se borra su geometría y sus métricas se recalculan. Esta acción no se puede deshacer.'))) return
    const ok = await guardar(() => api.eliminarAoi(a.id).then(() => true))
    if (ok) {
      setAois((l) => l.filter((x) => x.id !== a.id))
      if (seleccionada === a.id) setSeleccionada(null)
    }
  }

  const alternar = (set: React.Dispatch<React.SetStateAction<Set<number>>>, id: number) =>
    set((o) => { const n = new Set(o); if (n.has(id)) n.delete(id); else n.add(id); return n })

  const zoomA = (z: number) => setZoom(Math.min(4, Math.max(0.25, +z.toFixed(2))))

  // ───── Atajos ─────
  useEffect(() => {
    const k = (e: KeyboardEvent) => {
      const t = e.target as HTMLElement
      if (t.tagName === 'INPUT' || t.tagName === 'TEXTAREA' || t.tagName === 'SELECT' || e.ctrlKey || e.metaKey) return
      const tecla = e.key.toLowerCase()
      if (tecla === 'v') setHerramienta('seleccionar')
      else if (tecla === 'r') setHerramienta('rectangulo')
      else if (tecla === 'p') setHerramienta('poligono')
      else if (tecla === 'h') setHerramienta('mano')
      else if (tecla === 'g') setVerRejilla((v) => !v)
      else if (tecla === 'f') setVerReferencia((v) => !v)
      else if (e.key === '+' || e.key === '=') zoomA(zoom + 0.25)
      else if (e.key === '-') zoomA(zoom - 0.25)
      else if (e.key === '0') zoomA(1)
      else if (e.key === 'Escape') {
        if (herramienta !== 'seleccionar') setHerramienta('seleccionar')
        else setSeleccionada(null)
      } else if ((e.key === 'Delete' || e.key === 'Backspace') && actual) {
        e.preventDefault()
        eliminarAoi(actual)
      }
    }
    window.addEventListener('keydown', k)
    return () => window.removeEventListener('keydown', k)
  })

  if (cargando) return <div className="center-state" style={{ height: '100%' }}><div className="spinner" />Cargando editor…</div>
  if (error || !proyecto) {
    return (
      <div className="center-state" style={{ height: '100%' }}>
        <div className="alert alert-error">{error?.mensaje ?? 'Proyecto no encontrado.'}</div>
        <Link to="/" className="btn">Volver a proyectos</Link>
      </div>
    )
  }
  if (!estimulo) {
    return (
      <div className="center-state" style={{ height: '100%' }}>
        <p>Este proyecto aún no tiene una captura del estímulo para delimitar áreas de interés.</p>
        <Link to={`/proyectos/${proyectoId}/ingesta`} className="btn btn-primary">Cargar dataset</Link>
      </div>
    )
  }

  const totalFx = fijaciones.length
  const h = NOMBRE_HERRAMIENTA[herramienta]
  const iActual = actual ? aois.indexOf(actual) : -1
  const c = actual?.coordenadas
  const dentroActual = actual ? conteo.get(actual.id) ?? 0 : 0
  const formato = estimulo.etiquetaDispositivo ?? (estimulo.anchoOriginalPx < estimulo.altoOriginalPx ? 'Móvil' : 'Desktop')

  return (
    <div className="editor">
      <header className="ed-top">
        <div className="ed-brand">
          <Logo />
          <nav className="ed-crumbs" aria-label="Ruta">
            <Link to="/">Proyectos</Link><ChevronRight size={11} />
            <span title={proyecto.nombre}>{proyecto.nombre}</span><ChevronRight size={11} />
            <span className="current">Editor de AOI</span>
          </nav>
        </div>
        <PhaseSwitcher proyectoId={proyectoId} fase="editor" />
        <div className="ed-save" aria-live="polite">
          {guardado === 'guardando' ? <Loader2 size={14} className="spin" /> : guardado === 'error' ? <AlertTriangle size={14} color="var(--danger)" /> : <Check size={14} color="var(--ok)" />}
          <div>
            <div>{guardado === 'guardando' ? 'Guardando…' : guardado === 'error' ? 'Error al guardar' : USE_MOCK ? 'Guardado (modo simulado)' : 'Guardado en PostgreSQL'}</div>
            <div className="subtle">Coordenadas normalizadas [0.0, 1.0]</div>
          </div>
        </div>
        <ThemeMenu />
        <button className="btn btn-primary ed-continue" onClick={() => nav(`/proyectos/${proyectoId}/dashboard`)}>
          Continuar al análisis <ArrowRight size={16} />
        </button>
      </header>

      <div className="ed-main">
        <Toolbar orientation="vertical" className="ed-tools" aria-label="Herramientas de dibujo">
          <Herr nombre="Selección y edición de vértices" atajo="V" activo={herramienta === 'seleccionar'} onActivar={() => setHerramienta('seleccionar')}><MousePointer2 /></Herr>
          <Herr nombre="Rectángulo AOI" atajo="R" activo={herramienta === 'rectangulo'} onActivar={() => setHerramienta('rectangulo')}><Square /></Herr>
          <Herr nombre="Polígono libre AOI" atajo="P" activo={herramienta === 'poligono'} onActivar={() => setHerramienta('poligono')}><Pentagon /></Herr>
          <ToolbarSeparator />
          <Pista texto="Fijaciones de referencia" atajo="F">
            <Toggle className="ed-tool suave" aria-label="Fijaciones de referencia" pressed={verReferencia} onPressedChange={setVerReferencia}>
              <ToggleIcon>{verReferencia ? <Eye /> : <EyeOff />}</ToggleIcon>
            </Toggle>
          </Pista>
          <Pista texto="Cuadrícula y ajuste" atajo="G">
            <Toggle className="ed-tool suave" aria-label="Cuadrícula y ajuste" pressed={verRejilla} onPressedChange={setVerRejilla}>
              <ToggleIcon><Grid3x3 /></ToggleIcon>
            </Toggle>
          </Pista>
          <Herr nombre="Mano: desplazar lienzo" atajo="H" activo={herramienta === 'mano'} onActivar={() => setHerramienta('mano')}><Hand /></Herr>
          <ToolbarGroup className="ed-zoom" aria-label="Zoom">
            <Pista texto="Acercar" atajo="+">
              <ToolbarButton aria-label="Acercar" onClick={() => zoomA(zoom + 0.25)}><Plus size={16} aria-hidden /></ToolbarButton>
            </Pista>
            <Pista texto="Alejar" atajo="-">
              <ToolbarButton aria-label="Alejar" onClick={() => zoomA(zoom - 0.25)}><Minus size={16} aria-hidden /></ToolbarButton>
            </Pista>
            <Pista texto="Ajustar a pantalla" atajo="0">
              <ToolbarButton aria-label="Ajustar a pantalla" onClick={() => zoomA(1)}><Maximize size={15} aria-hidden /></ToolbarButton>
            </Pista>
          </ToolbarGroup>
          <span className="ed-zoom-label mono" aria-label={`Zoom ${Math.round(zoom * 100)}%`}>{Math.round(zoom * 100)}%</span>
        </Toolbar>

        <section className="ed-canvas">
          <div className="ed-head">
            <div>
              <div className="eyebrow">Editor de áreas de interés</div>
              <h1>Delimita lo que importa.</h1>
            </div>
            <div className="ed-stim">
              <div className="row" style={{ gap: 4 }}>
                {estimulos.length > 1 && (
                  <Pista texto="Estímulo anterior" lado="bottom">
                    <Button className="icon-btn" aria-label="Estímulo anterior" disabled={indice === 0} onClick={() => setIndice((i) => i - 1)}><ChevronLeft size={15} aria-hidden /></Button>
                  </Pista>
                )}
                <span>Estímulo {String(indice + 1).padStart(2, '0')} de {String(estimulos.length).padStart(2, '0')}</span>
                {estimulos.length > 1 && (
                  <Pista texto="Estímulo siguiente" lado="bottom">
                    <Button className="icon-btn" aria-label="Estímulo siguiente" disabled={indice === estimulos.length - 1} onClick={() => setIndice((i) => i + 1)}><ChevronRight size={15} aria-hidden /></Button>
                  </Pista>
                )}
              </div>
              <span className="subtle">{proyecto.nombre.replace(/^(Estudio|Prueba de Usabilidad -|Evaluación)\s*/i, '')} · {formato}</span>
            </div>
          </div>
          <div className="ed-file">
            <Monitor size={15} />
            <span className="mono">{estimulo.nombreArchivo}</span>
            <span className="spacer" />
            <span className="subtle mono">{estimulo.anchoOriginalPx} × {estimulo.altoOriginalPx} px</span>
            <span className="badge badge-ok"><i className="status-dot" style={{ background: 'var(--ok)' }} /> {aois.length} AOIs</span>
          </div>
          {aviso && (
            <div className="alert alert-warn ed-aviso" role="alert">
              <span>{aviso}</span><span className="spacer" />
              <button className="btn btn-ghost btn-sm" onClick={() => setAviso(null)}>Cerrar</button>
            </div>
          )}

          <StimulusStage
            variante="editor"
            urlImagen={estimulo.urlRecurso}
            anchoOriginal={estimulo.anchoOriginalPx}
            altoOriginal={estimulo.altoOriginalPx}
            fijaciones={[]}
            modo={null}
            colorDeSesion={() => '#eab308'}
            referencia={verReferencia ? fijaciones : null}
            aois={aoisVista}
            aoisOcultas={ocultas}
            aoisBloqueadas={bloqueadas}
            mostrarAois
            mostrarImagen
            aoiSeleccionada={seleccionada}
            herramienta={herramienta}
            rejilla={rejilla}
            zoom={zoom}
            sufijoSeleccion=" (Editando)"
            colorBorrador={siguienteColor}
            onSeleccionarAoi={setSeleccionada}
            onCrearAoi={crearAoi}
            onActualizarAoi={actualizarAoi}
            onEscala={setEscala}
            onCursor={setCursor}
          />

          <div className="ed-floatbar">
            <span className="row"><ZoomIn size={15} /> Zoom: {Math.round(escala * 100)}%</span>
            <label className="row ed-toggle">
              <Switch checked={verReferencia} onCheckedChange={setVerReferencia}><SwitchThumb /></Switch>
              Mostrar puntos de fijación crudos ({num(totalFx)} fijaciones)
            </label>
            <span className="spacer" />
            <span className="mono subtle">X: {cursor ? cursor.x.toFixed(3) : '—'} <span className="sep">|</span> Y: {cursor ? cursor.y.toFixed(3) : '—'}</span>
          </div>
          <div className="ed-status">
            <strong>{h.titulo}</strong>
            <span className="subtle">{h.ayuda}</span>
            {verRejilla && <span className="badge">Ajuste a cuadrícula · {PASO_REJILLA_PX} px</span>}
          </div>
        </section>

        <aside className="ed-inspector" aria-label="Gestión de áreas de interés">
          <div className="ed-insp-head">
            <Layers size={20} />
            <div>
              <h2>Gestión de Áreas de Interés ({aois.length})</h2>
              <span className="subtle">Define, organiza y ajusta tus AOIs.</span>
            </div>
          </div>

          <div className="ed-insp-body">
            {actual && c ? (
              <form className="ed-props" onSubmit={(e) => { e.preventDefault(); guardarPropiedades() }}>
                <div className="row">
                  <span className="eyebrow muted">Propiedades</span>
                  <span className="badge badge-accent">AOI {iActual + 1} · Seleccionada</span>
                  {bloqueadas.has(actual.id) && <span className="badge"><Lock size={11} /> Bloqueada</span>}
                </div>
                <Field className="ed-field">
                  <FieldLabel>Etiqueta</FieldLabel>
                  <Input ref={campoEtiqueta} className="input" maxLength={100} value={etiqueta} onValueChange={setEtiqueta} />
                </Field>
                <div className="ed-field">
                  <span className="ed-field-label" aria-hidden>Color identificador <span className="subtle mono">{color.toUpperCase()}</span></span>
                  <ColorPicker valor={color} onCambio={setColor} />
                </div>
                <div className="ed-geo">
                  <div><span className="link-accent">{actual.formaGeometrica === 'POLIGONO' ? `Polígono · ${c.puntos?.length ?? 0} vértices` : 'Rectángulo'}</span> <span className="subtle">· Coordenadas normalizadas</span></div>
                  <div className="mono">X: {c.x.toFixed(2)} – {(c.x + c.ancho).toFixed(2)} <span className="sep">|</span> Y: {c.y.toFixed(2)} – {(c.y + c.alto).toFixed(2)}</div>
                </div>
                <div className="ed-impact">
                  <strong>{num(dentroActual)} fijaciones capturadas</strong>
                  <span>dentro de esta geometría ({totalFx ? ((dentroActual / totalFx) * 100).toLocaleString('es-MX', { maximumFractionDigits: 1 }) : 0}% del total)</span>
                </div>
                <div className="row">
                  <button type="submit" className="btn btn-primary" style={{ flex: 1 }} disabled={!sucio || !etiqueta.trim()}><Check size={15} /> Guardar cambios</button>
                  <Pista texto="Eliminar AOI" atajo={TECLA_ELIMINAR} lado="top">
                    <Button className="btn btn-danger" aria-label="Eliminar AOI" onClick={() => eliminarAoi(actual)}><Trash2 size={15} aria-hidden /></Button>
                  </Pista>
                </div>
              </form>
            ) : (
              <div className="ed-props ed-props-empty">
                <MousePointerClick size={22} />
                <span>Selecciona una AOI en el lienzo o en la lista para editar su etiqueta, color y geometría, o traza una nueva.</span>
              </div>
            )}

            <h3 className="ed-list-title">AOIs definidas en este estímulo</h3>
            <div className="ed-list">
              {aois.map((a, i) => {
                const oculta = ocultas.has(a.id)
                const bloq = bloqueadas.has(a.id)
                return (
                  <div key={a.id} className={`ed-item ${a.id === seleccionada ? 'active' : ''} ${oculta ? 'dim' : ''}`}
                    onClick={() => { setSeleccionada(a.id); if (herramienta !== 'mano') setHerramienta('seleccionar') }}>
                    <div className="row">
                      <i className="ed-dot" style={{ background: aoisVista[i].color }} />
                      <span className="ed-item-name">AOI {i + 1}: {a.etiqueta}</span>
                    </div>
                    <div className="row">
                      <span className="subtle ed-item-sub">{a.formaGeometrica === 'POLIGONO' ? 'Polígono' : 'Rectángulo'} • {num(conteo.get(a.id) ?? 0)} fijaciones</span>
                      <span className="spacer" />
                      <span className="ed-item-actions" onClick={(e) => e.stopPropagation()}>
                        <Pista texto={bloq ? 'Desbloquear' : 'Bloquear (evita moverla por accidente)'} lado="top">
                          <Toggle className="ed-accion bloqueo" aria-label={`Bloquear AOI ${i + 1}`} pressed={bloq} onPressedChange={() => alternar(setBloqueadas, a.id)}>
                            <ToggleIcon>{bloq ? <Lock /> : <LockOpen />}</ToggleIcon>
                          </Toggle>
                        </Pista>
                        <Pista texto={oculta ? 'Mostrar en el lienzo' : 'Ocultar en el lienzo'} lado="top">
                          <Toggle className="ed-accion" aria-label={`Ocultar AOI ${i + 1}`} pressed={oculta} onPressedChange={() => alternar(setOcultas, a.id)}>
                            <ToggleIcon>{oculta ? <EyeOff /> : <Eye />}</ToggleIcon>
                          </Toggle>
                        </Pista>
                        <Pista texto="Editar propiedades" lado="top">
                          <Button className="ed-accion" aria-label={`Editar AOI ${i + 1}`} onClick={() => { setSeleccionada(a.id); setHerramienta('seleccionar'); enfocarEtiqueta(30) }}><Pencil aria-hidden /></Button>
                        </Pista>
                      </span>
                    </div>
                  </div>
                )
              })}
              {!aois.length && <p className="subtle" style={{ fontSize: 12.5 }}>Todavía no hay AOIs en este estímulo.</p>}
            </div>
            <button className="ed-new" onClick={() => { setSeleccionada(null); setHerramienta('rectangulo') }}>
              <Plus size={16} /> Trazar nueva área de interés
            </button>
            <p className="subtle ed-note">Las áreas se guardan automáticamente al dibujarlas, moverlas o redimensionarlas.</p>
          </div>
        </aside>
      </div>
    </div>
  )
}

/** Herramienta de dibujo excluyente: el estado vive en la página, así que solo se puede activar (no desactivar). */
function Herr({ nombre, atajo, activo, onActivar, children }: { nombre: string; atajo: string; activo: boolean; onActivar: () => void; children: React.ReactNode }) {
  return (
    <Pista texto={nombre} atajo={atajo}>
      <Toggle className="ed-tool" aria-label={nombre} pressed={activo} onPressedChange={(p) => { if (p) onActivar() }}>
        <ToggleIcon>{children}</ToggleIcon>
      </Toggle>
    </Pista>
  )
}
