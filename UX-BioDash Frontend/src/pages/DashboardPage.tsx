import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import {
  ChevronRight, Download, Expand, Flame, HelpCircle, ImageIcon, Layers, Minus, Monitor, MousePointer2, Pentagon, Plus,
  Route, ScatterChart, Scan, Users, ZoomIn,
} from 'lucide-react'
import { Logo } from '../components/AppHeader'
import { ThemeMenu } from '../components/ThemeMenu'
import { HelpCard } from '../components/HelpCard'
import { PhaseSwitcher } from '../components/PhaseSwitcher'
import { api } from '../api/services'
import { toErrorApi } from '../api/client'
import type { AreaInteres, ErrorApi, Estimulo, Fijacion, CoordenadasAoi, FormaGeometrica, MetricasProyecto, Proyecto, SesionEvaluacion } from '../api/types'
import { StimulusStage, type Herramienta, type ModoVista } from '../viz/StimulusStage'
import { COLORES_AOI, COLORES_SESION, HEATMAP_CSS_GRADIENT, dibujarAois, dibujarFijaciones, dibujarHeatmap, dibujarScanpath } from '../viz/render'
import { Inspector, type EstadoGuardado, type PanelId } from '../dashboard/Inspector'
import { Timeline } from '../dashboard/Timeline'
import { MetricsPanel } from '../dashboard/MetricsPanel'
import { descargar, num } from '../lib/format'
import { Toolbar, ToolbarSeparator } from '@ui-sdk/react/toolbar'
import { Toggle, ToggleIcon } from '@ui-sdk/react/toggle'
import { Segmentado } from '../ui/Segmentado'
import { Selector } from '../ui/Selector'
import { Pista } from '../ui/Pista'
import { TECLA_ELIMINAR } from '../lib/plataforma'
import { confirmar } from '../ui/confirmar'
import './DashboardPage.css'

export function DashboardPage() {
  const { id } = useParams()
  const proyectoId = Number(id)

  const [proyecto, setProyecto] = useState<Proyecto | null>(null)
  const [estimulo, setEstimulo] = useState<Estimulo | null>(null)
  const [sesiones, setSesiones] = useState<SesionEvaluacion[]>([])
  const [fijaciones, setFijaciones] = useState<Fijacion[]>([])
  const [aois, setAois] = useState<AreaInteres[]>([])
  const [metricas, setMetricas] = useState<MetricasProyecto | null>(null)
  const [cargandoMetricas, setCargandoMetricas] = useState(false)
  const [error, setError] = useState<ErrorApi | null>(null)
  const [cargando, setCargando] = useState(true)

  const [modo, setModo] = useState<ModoVista>('heatmap')
  const [sesionId, setSesionId] = useState<number | null>(null)
  const [herramienta, setHerramienta] = useState<Herramienta>('seleccionar')
  const [seleccionada, setSeleccionada] = useState<number | null>(null)
  const [editando, setEditando] = useState<number | null>(null)
  const [ocultas, setOcultas] = useState<Set<number>>(new Set())
  const [mostrarAois, setMostrarAois] = useState(true)
  const [mostrarImagen, setMostrarImagen] = useState(true)
  const [opacidad, setOpacidad] = useState(0.75)
  const [radio, setRadio] = useState(35)
  const [zoom, setZoom] = useState(1)
  const [escala, setEscala] = useState(1)
  const [tiempo, setTiempo] = useState(0)
  const [reproduciendo, setReproduciendo] = useState(false)
  const [velocidad, setVelocidad] = useState(1)
  const [guardado, setGuardado] = useState<EstadoGuardado>('guardado')
  const [aviso, setAviso] = useState<string | null>(null)
  // El panel derecho permanece oculto hasta que se pulsa un botón de la barra izquierda
  const [panel, setPanel] = useState<PanelId | null>(null)
  const centro = useRef<HTMLDivElement>(null)

  /** Abre el panel del botón pulsado; si ya estaba abierto, lo cierra. */
  const alternarPanel = (id: PanelId) => setPanel((actual) => (actual === id ? null : id))

  // Carga inicial (al cambiar de proyecto se marca "cargando" durante el render)
  const [proyectoPrevio, setProyectoPrevio] = useState(proyectoId)
  if (proyectoId !== proyectoPrevio) {
    setProyectoPrevio(proyectoId)
    setCargando(true)
  }
  useEffect(() => {
    let vivo = true
    ;(async () => {
      try {
        const [p, ests, ses] = await Promise.all([api.obtenerProyecto(proyectoId), api.listarEstimulos(proyectoId), api.listarSesiones(proyectoId)])
        if (!vivo) return
        setProyecto(p)
        setSesiones(ses)
        const e = ests[0] ?? null
        setEstimulo(e)
        if (e) {
          const [fx, ar] = await Promise.all([api.listarFijaciones(e.id), api.listarAois(e.id)])
          if (!vivo) return
          setFijaciones(fx)
          setAois(ar)
          const dur = Math.max(0, ...ses.map((s) => s.duracionTotalMs))
          setTiempo(dur)
        }
      } catch (err) {
        if (vivo) setError(toErrorApi(err))
      } finally {
        if (vivo) setCargando(false)
      }
    })()
    return () => { vivo = false }
  }, [proyectoId])

  // Métricas: se recargan al cambiar de proyecto/participante o al incrementar la versión (tras guardar AOIs)
  const [versionMetricas, setVersionMetricas] = useState(0)
  const cargarMetricas = useCallback(() => setVersionMetricas((v) => v + 1), [])
  const claveMetricas = estimulo ? `${proyectoId}|${sesionId}|${versionMetricas}` : null
  const [claveMetricasPrevia, setClaveMetricasPrevia] = useState<string | null>(null)
  if (claveMetricas !== claveMetricasPrevia) {
    setClaveMetricasPrevia(claveMetricas)
    if (claveMetricas) setCargandoMetricas(true)
  }
  useEffect(() => {
    if (!estimulo) return
    let vivo = true
    api.metricas(proyectoId, sesionId)
      .then((m) => vivo && setMetricas(m))
      .catch((err) => vivo && setAviso(toErrorApi(err).mensaje))
      .finally(() => vivo && setCargandoMetricas(false))
    return () => { vivo = false }
  }, [estimulo, proyectoId, sesionId, versionMetricas])

  const sesionesVisibles = useMemo(() => (sesionId ? sesiones.filter((s) => s.id === sesionId) : sesiones), [sesiones, sesionId])
  const duracion = useMemo(() => Math.max(1, ...sesionesVisibles.map((s) => s.duracionTotalMs)), [sesionesVisibles])
  const fxSesion = useMemo(() => (sesionId ? fijaciones.filter((f) => f.sesionId === sesionId) : fijaciones), [fijaciones, sesionId])
  const fxVisibles = useMemo(() => fxSesion.filter((f) => f.inicioMs <= tiempo), [fxSesion, tiempo])

  const colorSesion = useMemo(() => {
    const m = new Map(sesiones.map((s, i) => [s.id, COLORES_SESION[i % COLORES_SESION.length]]))
    return (sid: number) => m.get(sid) ?? '#38bdf8'
  }, [sesiones])

  // Al cambiar de participante se muestran todas sus fijaciones
  const claveVista = `${sesionId}|${duracion}`
  const [claveVistaPrevia, setClaveVistaPrevia] = useState(claveVista)
  if (claveVista !== claveVistaPrevia) {
    setClaveVistaPrevia(claveVista)
    setReproduciendo(false)
    setTiempo(duracion)
  }

  // ───── AOIs ─────
  const guardar = async <T,>(op: () => Promise<T>): Promise<T | null> => {
    setGuardado('guardando')
    try {
      const r = await op()
      setGuardado('guardado')
      cargarMetricas()
      return r
    } catch (err) {
      setGuardado('error')
      setAviso(toErrorApi(err).mensaje)
      return null
    }
  }

  const crearAoi = async (r: CoordenadasAoi, forma: FormaGeometrica) => {
    if (!estimulo) return
    const color = COLORES_AOI[aois.length % COLORES_AOI.length]
    const nueva = await guardar(() =>
      api.crearAoi({ estimuloId: estimulo.id, etiqueta: `Nueva AOI ${aois.length + 1}`, formaGeometrica: forma, coordenadas: r, color }),
    )
    if (nueva) {
      setAois((l) => [...l, nueva])
      setSeleccionada(nueva.id)
      setEditando(nueva.id)
      setHerramienta('seleccionar')
      // Se muestra la lista de AOIs para poder nombrar la nueva área
      setPanel('aois')
    }
  }

  const actualizarAoi = async (a: AreaInteres) => {
    const previa = aois
    setAois((l) => l.map((x) => (x.id === a.id ? a : x)))
    const r = await guardar(() => api.actualizarAoi(a))
    if (!r) setAois(previa)
  }

  const eliminarAoi = async (a: AreaInteres) => {
    if (!(await confirmar(`¿Eliminar la AOI «${a.etiqueta}»?`, 'Se borrará su geometría y sus métricas se recalcularán. No se puede deshacer.'))) return
    const ok = await guardar(() => api.eliminarAoi(a.id).then(() => true))
    if (ok) {
      setAois((l) => l.filter((x) => x.id !== a.id))
      if (seleccionada === a.id) setSeleccionada(null)
    }
  }

  // Atajos de teclado
  useEffect(() => {
    const k = (e: KeyboardEvent) => {
      const t = e.target as HTMLElement
      if (t.tagName === 'INPUT' || t.tagName === 'TEXTAREA' || t.tagName === 'SELECT') return
      if (e.key === 'v' || e.key === 'V') setHerramienta('seleccionar')
      if (e.key === 'r' || e.key === 'R') setHerramienta('rectangulo')
      if (e.key === 'p' || e.key === 'P') { setHerramienta('poligono'); setPanel('aois') }
      if (e.key === 'Escape') {
        if (herramienta === 'rectangulo' || herramienta === 'poligono') setHerramienta('seleccionar')
        else if (seleccionada) setSeleccionada(null)
        else setPanel(null)
      }
      if (e.key === ' ') { e.preventDefault(); setReproduciendo((v) => { if (!v && tiempo >= duracion - 1) setTiempo(0); return !v }) }
      if ((e.key === 'Delete' || e.key === 'Backspace') && seleccionada) {
        const a = aois.find((x) => x.id === seleccionada)
        if (a) eliminarAoi(a)
      }
    }
    window.addEventListener('keydown', k)
    return () => window.removeEventListener('keydown', k)
  })

  // ───── Exportar ─────
  const exportar = async () => {
    if (!estimulo || !metricas || !proyecto) return
    const base = proyecto.nombre.normalize('NFD').replace(/[̀-ͯ]/g, '').replace(/[^\w]+/g, '_').toLowerCase()
    const sufijo = sesionId ? `_${sesiones.find((s) => s.id === sesionId)?.codigoSujeto}` : ''
    // CSV de métricas
    const lineas = ['seccion,aoi,ttff_ms,dwell_ms,dwell_pct,fijaciones,duracion_media_ms,revisitas']
    for (const m of metricas.porAoi) {
      lineas.push(['por_aoi', `"${m.etiqueta}"`, m.ttffMs ?? '', m.dwellTimeMs, m.dwellPct, m.conteoFijaciones, m.duracionMediaFijacionMs, m.revisitas ?? ''].join(','))
    }
    lineas.push('', ['participante', 'fijaciones', 'duracion_media_ms', 'duracion_sesion_ms', ...aois.map((a) => `"ttff_${a.etiqueta}"`)].join(','))
    for (const p of metricas.porParticipante) {
      lineas.push([p.codigoSujeto, p.conteoFijaciones, p.duracionMediaFijacionMs, p.duracionTotalMs, ...aois.map((a) => p.ttffPorAoi[a.id] ?? '')].join(','))
    }
    descargar(`${base}${sufijo}_metricas.csv`, String.fromCharCode(0xfeff) + lineas.join('\n'), 'text/csv;charset=utf-8')

    // PNG de la visualización a resolución original
    const img = new Image()
    img.crossOrigin = 'anonymous'
    img.src = estimulo.urlRecurso
    await img.decode().catch(() => undefined)
    const W = estimulo.anchoOriginalPx, H = estimulo.altoOriginalPx
    const c = document.createElement('canvas')
    c.width = W; c.height = H
    const ctx = c.getContext('2d')!
    ctx.fillStyle = '#fff'; ctx.fillRect(0, 0, W, H)
    if (mostrarImagen && img.complete && img.naturalWidth) ctx.drawImage(img, 0, 0, W, H)
    if (modo === 'heatmap') dibujarHeatmap(ctx, fxVisibles, W, H, { sigmaPx: radio, opacidad })
    else if (modo === 'scanpath') dibujarScanpath(ctx, fxVisibles, W, H, colorSesion, 1)
    else dibujarFijaciones(ctx, fxVisibles, W, H, colorSesion)
    if (mostrarAois) dibujarAois(ctx, aois, W, H, { seleccionada: null, ocultas })
    try {
      c.toBlob((b) => b && descargar(`${base}${sufijo}_${modo}.png`, b, 'image/png'))
    } catch {
      setAviso('No se pudo exportar la imagen (el estímulo se sirve desde otro dominio sin CORS). El CSV sí se descargó.')
    }
  }

  const pantallaCompleta = () => {
    if (document.fullscreenElement) document.exitFullscreen()
    else centro.current?.requestFullscreen?.()
  }

  if (cargando) return <div className="center-state" style={{ height: '100%' }}><div className="spinner" />Cargando dashboard…</div>
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
        <p>Este proyecto aún no tiene un estímulo ni datos de eye tracking.</p>
        <Link to={`/proyectos/${proyectoId}/ingesta`} className="btn btn-primary">Cargar dataset</Link>
      </div>
    )
  }

  const sesionActual = sesiones.find((s) => s.id === sesionId)

  return (
    <div className="dash">
      <header className="dash-top">
        <Logo />
        <nav className="crumbs" aria-label="Ruta">
          <Link to="/">Proyectos</Link><ChevronRight size={14} />
          <span className="crumb-mid" title={proyecto.nombre}>{proyecto.nombre}</span><ChevronRight size={14} />
          <strong>Dashboard Analítico</strong>
        </nav>
        <Segmentado<ModoVista>
          etiqueta="Tipo de visualización"
          valor={modo}
          onCambio={setModo}
          segmentos={[
            { valor: 'heatmap', etiqueta: 'Mapa de calor', icono: <Flame size={14} aria-hidden /> },
            { valor: 'scanpath', etiqueta: 'Scanpath', icono: <Route size={14} aria-hidden /> },
            { valor: 'fijaciones', etiqueta: 'Fijaciones', icono: <ScatterChart size={14} aria-hidden /> },
          ]}
        />
        <span className="spacer" />
        <Selector
          className="dash-participante"
          etiqueta="Filtrar por participante"
          icono={<Users size={14} />}
          valor={sesionId === null ? TODOS : String(sesionId)}
          onCambio={(v) => setSesionId(v === TODOS ? null : Number(v))}
          opciones={[
            { valor: TODOS, etiqueta: `Todos los participantes (N=${sesiones.length})` },
            ...sesiones.map((s) => ({ valor: String(s.id), etiqueta: `${s.codigoSujeto} · ${num(s.duracionTotalMs)} ms` })),
          ]}
        />
        <PhaseSwitcher proyectoId={proyectoId} fase="dashboard" />
        <ThemeMenu />
        <button className="btn btn-primary" onClick={exportar} disabled={!metricas}><Download size={15} /> Exportar reporte UX</button>
      </header>

      <div className={`dash-main ${panel ? 'con-panel' : ''}`}>
        <Toolbar className="toolrail" orientation="vertical" aria-label="Herramientas">
          <RailBtn titulo="Selección" atajo="V" activo={panel === 'seleccion'} herramienta={herramienta === 'seleccionar'}
            onClick={() => { setHerramienta('seleccionar'); alternarPanel('seleccion') }}><MousePointer2 size={18} /></RailBtn>
          <RailBtn titulo="Áreas de interés · dibujar" atajo="R" activo={panel === 'aois'} herramienta={herramienta === 'rectangulo'}
            onClick={() => {
              if (panel === 'aois') { setPanel(null); setHerramienta('seleccionar') }
              else { setPanel('aois'); setHerramienta('rectangulo'); setSeleccionada(null) }
            }}><Scan size={18} /></RailBtn>
          <RailBtn titulo="Polígono libre AOI" atajo="P" activo={false} herramienta={herramienta === 'poligono'}
            onClick={() => { setPanel('aois'); setHerramienta(herramienta === 'poligono' ? 'seleccionar' : 'poligono'); setSeleccionada(null) }}><Pentagon size={18} /></RailBtn>
          <ToolbarSeparator />
          <RailBtn titulo="Estímulo" activo={panel === 'estimulo'} onClick={() => alternarPanel('estimulo')}><ImageIcon size={18} /></RailBtn>
          <RailBtn titulo="Capas y visualización" activo={panel === 'capas'} onClick={() => alternarPanel('capas')}><Layers size={18} /></RailBtn>
          <RailBtn titulo="Vista y zoom" activo={panel === 'vista'} onClick={() => alternarPanel('vista')}><ZoomIn size={18} /></RailBtn>
          <span className="spacer" />
          <span className="rail-help"><HelpCard tema={modo} /></span>
          <span className="avatar" style={{ width: 30, height: 30, fontSize: 11 }}>YG</span>
        </Toolbar>

        <div className="center" ref={centro}>
          <div className="center-head">
            <Monitor size={16} className="muted" />
            <strong className="stim-name">{proyecto.nombre} · {estimulo.etiquetaDispositivo ?? 'Estímulo'}</strong>
            <span className="subtle">/ {estimulo.anchoOriginalPx} × {estimulo.altoOriginalPx} px</span>
            <HelpCard tema={modo} />
            <span className="spacer" />
            {herramienta === 'rectangulo' && <span className="badge badge-accent">Arrastra sobre el estímulo para dibujar una AOI · Esc para cancelar</span>}
            {herramienta === 'poligono' && <span className="badge badge-accent">Clic para añadir vértices · Enter, doble clic o clic en el primer vértice para cerrar · Esc para cancelar</span>}
            <span className="row subtle" style={{ fontSize: 12.5 }}><i className="status-dot" style={{ background: 'var(--ok)' }} />{sesionActual ? `Participante ${sesionActual.codigoSujeto}` : `${sesiones.length} sesiones procesadas`}</span>
          </div>
          {aviso && (
            <div className="alert alert-warn dash-aviso" role="alert">
              <span>{aviso}</span><span className="spacer" />
              <button className="btn btn-ghost btn-sm" onClick={() => setAviso(null)}>Cerrar</button>
            </div>
          )}
          <StimulusStage
            urlImagen={estimulo.urlRecurso}
            anchoOriginal={estimulo.anchoOriginalPx}
            altoOriginal={estimulo.altoOriginalPx}
            fijaciones={fxVisibles}
            modo={modo}
            colorDeSesion={colorSesion}
            aois={aois}
            aoisOcultas={ocultas}
            mostrarAois={mostrarAois}
            mostrarImagen={mostrarImagen}
            aoiSeleccionada={seleccionada}
            herramienta={herramienta}
            opacidad={opacidad}
            radioPx={radio}
            zoom={zoom}
            onSeleccionarAoi={setSeleccionada}
            onCrearAoi={crearAoi}
            onActualizarAoi={actualizarAoi}
            onEscala={setEscala}
          />
          <div className="center-foot">
            {modo === 'heatmap' ? (
              <span className="row subtle">
                Atención <span className="mono">0.0</span>
                <i className="legend-bar" style={{ background: HEATMAP_CSS_GRADIENT }} />
                <span className="mono">1.0</span> · Mapa al {Math.round(opacidad * 100)}%
              </span>
            ) : (
              <span className="subtle">{num(fxVisibles.length)} fijaciones visibles{modo === 'scanpath' ? ' · tamaño del círculo = duración' : ''}</span>
            )}
            <span className="spacer" />
            <span className="subtle">Escala del estímulo {(escala * 100).toLocaleString('es-MX', { maximumFractionDigits: 1 })}%</span>
            <div className="zoom">
              <Pista texto="Alejar" lado="top">
                <button type="button" className="icon-btn" aria-label="Alejar" onClick={() => setZoom((z) => Math.max(0.25, +(z - 0.25).toFixed(2)))}><Minus size={14} aria-hidden /></button>
              </Pista>
              <span className="mono">{Math.round(zoom * 100)}%</span>
              <Pista texto="Acercar" lado="top">
                <button type="button" className="icon-btn" aria-label="Acercar" onClick={() => setZoom((z) => Math.min(4, +(z + 0.25).toFixed(2)))}><Plus size={14} aria-hidden /></button>
              </Pista>
            </div>
            <Pista texto="Pantalla completa" lado="top">
              <button type="button" className="icon-btn" aria-label="Pantalla completa" onClick={pantallaCompleta}><Expand size={16} aria-hidden /></button>
            </Pista>
          </div>
        </div>

        {panel && <Inspector
          panel={panel}
          onCerrar={() => { setPanel(null); setHerramienta('seleccionar') }}
          estimulo={estimulo}
          sesiones={sesiones.length}
          dispositivo={proyecto.dispositivo}
          mostrarImagen={mostrarImagen}
          onMostrarImagen={setMostrarImagen}
          mostrarAois={mostrarAois}
          onMostrarAois={setMostrarAois}
          zoom={zoom}
          escala={escala}
          onZoom={(z) => setZoom(Math.min(4, Math.max(0.25, z)))}
          onPantallaCompleta={pantallaCompleta}
          dibujando={herramienta === 'rectangulo' || herramienta === 'poligono'}
          modo={modo}
          opacidad={opacidad}
          radio={radio}
          onOpacidad={setOpacidad}
          onRadio={setRadio}
          aois={aois}
          metricasAoi={metricas?.porAoi ?? []}
          seleccionada={seleccionada}
          ocultas={ocultas}
          editando={editando}
          guardado={guardado}
          participantesLeyenda={sesionesVisibles.map((s) => ({ codigo: s.codigoSujeto, color: colorSesion(s.id) }))}
          onSeleccionar={(i) => { setSeleccionada(i); setHerramienta('seleccionar') }}
          onAlternarVisible={(i) => setOcultas((o) => { const n = new Set(o); if (n.has(i)) n.delete(i); else n.add(i); return n })}
          onRenombrar={(a, etiqueta) => actualizarAoi({ ...a, etiqueta })}
          onEliminar={eliminarAoi}
          onEditar={setEditando}
          onNueva={() => { setHerramienta('rectangulo'); setSeleccionada(null) }}
        />}
      </div>

      <Timeline
        tiempo={tiempo}
        duracion={duracion}
        reproduciendo={reproduciendo}
        velocidad={velocidad}
        fijaciones={fxSesion.length > 400 ? fxSesion.filter((_, i) => i % Math.ceil(fxSesion.length / 400) === 0) : fxSesion}
        onTiempo={setTiempo}
        onReproducir={setReproduciendo}
        onVelocidad={setVelocidad}
      />

      <MetricsPanel metricas={metricas} aois={aois} cargando={cargandoMetricas} sesionSeleccionada={sesionId} />

      <footer className="dash-foot subtle">
        <HelpCircle size={13} /> Atajos: <kbd>V</kbd> seleccionar · <kbd>R</kbd> dibujar AOI · <kbd>{TECLA_ELIMINAR}</kbd> eliminar AOI · <kbd>Espacio</kbd> reproducir · doble clic en una AOI del inspector para renombrarla
      </footer>
    </div>
  )
}

const TODOS = '__todos__'

/**
 * Botón de la barra lateral (Toggle de ui-sdk dentro del Toolbar):
 * "activo" = su panel está abierto (pressed); "herramienta" = su herramienta de lienzo está en uso.
 */
function RailBtn({ titulo, atajo, activo, herramienta, onClick, children }: {
  titulo: string; atajo?: string; activo: boolean; herramienta?: boolean; onClick: () => void; children: React.ReactNode
}) {
  return (
    <Pista texto={titulo} atajo={atajo}>
      <Toggle
        className={`rail-btn ${herramienta && !activo ? 'tool' : ''}`}
        aria-label={titulo}
        pressed={activo}
        onPressedChange={() => onClick()}
      >
        <ToggleIcon>{children}</ToggleIcon>
      </Toggle>
    </Pista>
  )
}
