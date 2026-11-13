import { useEffect, useRef, useState } from 'react'
import {
  AlertTriangle, Check, CloudCheck, Expand, Eye, EyeOff, Flame, Loader2, MousePointerClick, Pencil, Plus, Route,
  ScatterChart, Trash2, X,
} from 'lucide-react'
import type { AreaInteres, Estimulo, MetricaAoi } from '../api/types'
import type { ModoVista } from '../viz/StimulusStage'
import { colorAoi } from '../viz/render'
import { ms, num } from '../lib/format'
import { Deslizador } from '../ui/Deslizador'
import { Interruptor } from '../ui/Interruptor'
import { Pista } from '../ui/Pista'
import { TECLA_ELIMINAR } from '../lib/plataforma'

export type EstadoGuardado = 'guardado' | 'guardando' | 'error'
/** Cada botón de la barra lateral izquierda abre su propio panel. */
export type PanelId = 'seleccion' | 'aois' | 'estimulo' | 'capas' | 'vista'

const TITULOS: Record<PanelId, string> = {
  seleccion: 'Selección',
  aois: 'Áreas de interés',
  estimulo: 'Estímulo',
  capas: 'Capas y visualización',
  vista: 'Vista y zoom',
}

interface Props {
  panel: PanelId
  onCerrar: () => void
  modo: ModoVista
  estimulo: Estimulo
  sesiones: number
  dispositivo: string | null
  opacidad: number
  radio: number
  onOpacidad: (v: number) => void
  onRadio: (v: number) => void
  mostrarImagen: boolean
  onMostrarImagen: (v: boolean) => void
  mostrarAois: boolean
  onMostrarAois: (v: boolean) => void
  zoom: number
  escala: number
  onZoom: (v: number) => void
  onPantallaCompleta: () => void
  aois: AreaInteres[]
  metricasAoi: MetricaAoi[]
  seleccionada: number | null
  ocultas: Set<number>
  editando: number | null
  guardado: EstadoGuardado
  dibujando: boolean
  participantesLeyenda: { codigo: string; color: string }[]
  onSeleccionar: (id: number) => void
  onAlternarVisible: (id: number) => void
  onRenombrar: (a: AreaInteres, etiqueta: string) => void
  onEliminar: (a: AreaInteres) => void
  onEditar: (id: number | null) => void
  onNueva: () => void
}

export function Inspector(p: Props) {
  return (
    <aside className="inspector" aria-label={TITULOS[p.panel]}>
      <div className="insp-head">
        <span>{TITULOS[p.panel]}</span>
        <Pista texto="Cerrar panel" lado="left">
          <button type="button" className="icon-btn" onClick={p.onCerrar} aria-label="Cerrar panel"><X size={16} aria-hidden /></button>
        </Pista>
      </div>
      <div className="insp-body">
        {p.panel === 'seleccion' && <PanelSeleccion {...p} />}
        {p.panel === 'aois' && <PanelAois {...p} />}
        {p.panel === 'estimulo' && <PanelEstimulo {...p} />}
        {p.panel === 'capas' && <PanelCapas {...p} />}
        {p.panel === 'vista' && <PanelVista {...p} />}
      </div>
      <div className="insp-foot subtle">
        {p.guardado === 'guardando' ? <><Loader2 size={14} className="spin" /> Guardando cambios…</>
          : p.guardado === 'error' ? <span style={{ color: 'var(--danger-text)' }} className="row"><AlertTriangle size={14} /> No se pudo guardar el último cambio</span>
          : <><CloudCheck size={14} /> Todos los cambios guardados</>}
      </div>
    </aside>
  )
}

// ───────── Paneles ─────────

function PanelSeleccion(p: Props) {
  const i = p.aois.findIndex((a) => a.id === p.seleccionada)
  const a = p.aois[i]
  if (!a) {
    return (
      <section className="insp-section">
        <div className="empty-hint">
          <MousePointerClick size={22} />
          <span>Haz clic sobre una AOI en el lienzo, o elige una de la lista, para ver sus métricas y moverla o redimensionarla.</span>
        </div>
        <ListaAois {...p} compacta />
      </section>
    )
  }
  const m = p.metricasAoi.find((x) => x.aoiId === a.id)
  const { anchoOriginalPx: W, altoOriginalPx: H } = p.estimulo
  const c = a.coordenadas
  return (
    <>
      <section className="insp-section">
        <AoiItem {...itemProps(p, a, i)} />
        <dl className="kv">
          <dt>Posición</dt><dd className="mono">x {Math.round(c.x * W)} · y {Math.round(c.y * H)} px</dd>
          <dt>Tamaño</dt><dd className="mono">{Math.round(c.ancho * W)} × {Math.round(c.alto * H)} px</dd>
        </dl>
      </section>
      <section className="insp-section">
        <h4>Métricas de esta AOI</h4>
        <div className="mini-stats">
          <div><span>TTFF</span><strong>{ms(m?.ttffMs)}</strong></div>
          <div><span>Dwell time</span><strong>{ms(m?.dwellTimeMs)}</strong></div>
          <div><span>Fijaciones</span><strong>{num(m?.conteoFijaciones ?? 0)}</strong></div>
          <div><span>Revisitas</span><strong>{m?.revisitas == null ? '—' : num(m.revisitas)}</strong></div>
        </div>
        <p className="subtle" style={{ fontSize: 12 }}>Arrastra la AOI para moverla o sus esquinas para redimensionarla. <kbd>{TECLA_ELIMINAR}</kbd> la elimina.</p>
      </section>
    </>
  )
}

function PanelAois(p: Props) {
  return (
    <section className="insp-section insp-grow">
      <div className={`draw-hint ${p.dibujando ? 'on' : ''}`}>
        {p.dibujando
          ? 'Modo dibujo activo: arrastra sobre el estímulo para trazar un rectángulo. Esc para cancelar.'
          : 'Pulsa “Añadir nueva AOI” o la tecla R para dibujar un rectángulo sobre el estímulo.'}
      </div>
      <h4 className="row">Definidas <span className="spacer" /><span className="badge">{p.aois.length}</span></h4>
      <ListaAois {...p} />
      <button className={`btn btn-block ${p.dibujando ? 'btn-outline-accent' : ''}`} onClick={p.onNueva}>
        <Plus size={15} /> Añadir nueva AOI
      </button>
    </section>
  )
}

function PanelEstimulo(p: Props) {
  const e = p.estimulo
  return (
    <section className="insp-section">
      <Interruptor etiqueta="Mostrar captura del estímulo" detalle="Oculta la imagen para ver solo la mirada" activo={p.mostrarImagen} onCambio={p.onMostrarImagen} />
      <dl className="kv">
        <dt>Archivo</dt><dd title={e.nombreArchivo}>{e.nombreArchivo}</dd>
        <dt>Resolución</dt><dd className="mono">{e.anchoOriginalPx} × {e.altoOriginalPx} px</dd>
        <dt>Formato</dt><dd>{e.etiquetaDispositivo ?? '—'}</dd>
        <dt>Equipo</dt><dd>{p.dispositivo ?? '—'}</dd>
        <dt>Sesiones</dt><dd>{num(p.sesiones)}</dd>
      </dl>
    </section>
  )
}

function PanelCapas(p: Props) {
  return (
    <>
      <section className="insp-section">
        <h4>Capas</h4>
        <Interruptor etiqueta="Áreas de interés" detalle="Delimitadores y etiquetas de AOI" activo={p.mostrarAois} onCambio={p.onMostrarAois} />
        <Interruptor etiqueta="Captura del estímulo" detalle="Imagen de fondo" activo={p.mostrarImagen} onCambio={p.onMostrarImagen} />
      </section>
      <section className="insp-section">
        {p.modo === 'heatmap' ? (
          <>
            <h4><Flame size={13} /> Ajustes del mapa</h4>
            <Deslizador etiqueta="Opacidad" valor={Math.round(p.opacidad * 100)} sufijo="%" min={10} max={100} onCambio={(v) => p.onOpacidad(v / 100)} />
            <Deslizador etiqueta="Radio gaussiano" valor={p.radio} sufijo=" px" min={10} max={120} onCambio={p.onRadio} />
          </>
        ) : (
          <>
            <h4>{p.modo === 'scanpath' ? <Route size={13} /> : <ScatterChart size={13} />} Participantes</h4>
            <div className="legend-list">
              {p.participantesLeyenda.map((l) => (
                <span key={l.codigo} className="row legend-item"><i className="dot" style={{ background: l.color, borderRadius: '50%' }} />{l.codigo}</span>
              ))}
            </div>
            {p.modo === 'scanpath' && p.participantesLeyenda.length > 1 && (
              <p className="subtle" style={{ fontSize: 12 }}>Selecciona un solo participante arriba para leer la secuencia con claridad.</p>
            )}
          </>
        )}
      </section>
      <section className="insp-section">
        <h4>Normalización</h4>
        <Interruptor etiqueta="Normalización espacial" detalle="Coordenadas en [0.0, 1.0] · servidor" activo disabled title="Aplicada automáticamente por el servidor durante la ingesta" />
        <Interruptor etiqueta="Normalización temporal" detalle="Inicio alineado · t=0 ms · servidor" activo disabled title="Aplicada automáticamente por el servidor durante la ingesta" />
      </section>
    </>
  )
}

function PanelVista(p: Props) {
  return (
    <section className="insp-section">
      <Deslizador etiqueta="Zoom" valor={Math.round(p.zoom * 100)} sufijo="%" min={25} max={400} onCambio={(v) => p.onZoom(v / 100)} />
      <dl className="kv">
        <dt>Escala del estímulo</dt><dd className="mono">{(p.escala * 100).toLocaleString('es-MX', { maximumFractionDigits: 1 })}%</dd>
      </dl>
      <div className="row">
        <button className="btn" style={{ flex: 1 }} onClick={() => p.onZoom(1)}>Ajustar a la ventana</button>
        <button className="btn" style={{ flex: 1 }} onClick={() => p.onZoom(1 / Math.max(p.escala / p.zoom, 0.01))}>Tamaño real</button>
      </div>
      <button className="btn btn-block" onClick={p.onPantallaCompleta}><Expand size={15} /> Pantalla completa</button>
    </section>
  )
}

// ───────── Piezas ─────────

function itemProps(p: Props, a: AreaInteres, i: number) {
  return {
    a,
    indice: i,
    color: colorAoi(p.aois, a.id),
    metrica: p.metricasAoi.find((m) => m.aoiId === a.id),
    activa: p.seleccionada === a.id,
    oculta: p.ocultas.has(a.id),
    editando: p.editando === a.id,
    onSeleccionar: () => p.onSeleccionar(a.id),
    onAlternarVisible: () => p.onAlternarVisible(a.id),
    onRenombrar: (t: string) => p.onRenombrar(a, t),
    onEliminar: () => p.onEliminar(a),
    onEditar: (v: boolean) => p.onEditar(v ? a.id : null),
  }
}

function ListaAois(p: Props & { compacta?: boolean }) {
  return (
    <div className="aoi-list">
      {p.aois.map((a, i) => <AoiItem key={a.id} {...itemProps(p, a, i)} compacta={p.compacta} />)}
      {!p.aois.length && <p className="subtle" style={{ fontSize: 12.5 }}>Aún no hay AOIs. Dibuja rectángulos sobre botones, textos o imágenes clave para medir la atención en cada zona.</p>}
    </div>
  )
}

function AoiItem(p: {
  a: AreaInteres; indice: number; color: string; metrica?: MetricaAoi; activa: boolean; oculta: boolean; editando: boolean; compacta?: boolean
  onSeleccionar: () => void; onAlternarVisible: () => void; onRenombrar: (t: string) => void; onEliminar: () => void; onEditar: (v: boolean) => void
}) {
  const [texto, setTexto] = useState(p.a.etiqueta)
  const input = useRef<HTMLInputElement>(null)
  // Al entrar en modo edición se reinicia el texto con la etiqueta actual (ajuste durante el render)
  const [editandoPrevio, setEditandoPrevio] = useState(p.editando)
  if (p.editando !== editandoPrevio) {
    setEditandoPrevio(p.editando)
    if (p.editando) setTexto(p.a.etiqueta)
  }
  useEffect(() => {
    if (p.editando) setTimeout(() => input.current?.select(), 0)
  }, [p.editando])
  const confirmar = () => {
    const t = texto.trim()
    if (t && t !== p.a.etiqueta) p.onRenombrar(t)
    p.onEditar(false)
  }
  const pct = p.metrica?.dwellPct ?? 0
  return (
    <div className={`aoi-item ${p.activa ? 'active' : ''} ${p.oculta ? 'hidden' : ''}`} onClick={p.onSeleccionar}>
      <div className="row">
        <i className="aoi-swatch" style={{ borderColor: p.color }} />
        {p.editando ? (
          <form className="row" style={{ flex: 1, gap: 4 }} onSubmit={(e) => { e.preventDefault(); confirmar() }} onClick={(e) => e.stopPropagation()}>
            <input ref={input} className="input aoi-input" value={texto} maxLength={100} onChange={(e) => setTexto(e.target.value)} onBlur={confirmar}
              onKeyDown={(e) => e.key === 'Escape' && p.onEditar(false)} aria-label="Nombre de la AOI" />
            <button className="icon-btn" type="submit" aria-label="Confirmar"><Check size={14} /></button>
          </form>
        ) : (
          <span className="aoi-name" onDoubleClick={() => p.onEditar(true)} title="Doble clic para renombrar">AOI {p.indice + 1}: {p.a.etiqueta}</span>
        )}
        {!p.editando && !p.compacta && (
          <span className="aoi-actions" onClick={(e) => e.stopPropagation()}>
            <Pista texto="Renombrar" lado="top">
              <button type="button" className="icon-btn" aria-label="Renombrar" onClick={() => p.onEditar(true)}><Pencil size={13} aria-hidden /></button>
            </Pista>
            <Pista texto="Eliminar" lado="top">
              <button type="button" className="icon-btn" aria-label="Eliminar" onClick={p.onEliminar}><Trash2 size={13} aria-hidden /></button>
            </Pista>
            <Pista texto={p.oculta ? 'Mostrar' : 'Ocultar'} lado="top">
              <button type="button" className="icon-btn" aria-label={p.oculta ? 'Mostrar' : 'Ocultar'} onClick={p.onAlternarVisible}>{p.oculta ? <EyeOff size={14} aria-hidden /> : <Eye size={14} aria-hidden />}</button>
            </Pista>
          </span>
        )}
      </div>
      <div className="row aoi-meter">
        <span className="bar" style={{ flex: 1, width: 'auto' }}><i style={{ width: `${pct}%`, background: p.color }} /></span>
        <span className="subtle">{pct.toLocaleString('es-MX', { minimumFractionDigits: 1, maximumFractionDigits: 1 })}% atención</span>
      </div>
    </div>
  )
}
