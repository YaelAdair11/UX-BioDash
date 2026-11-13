import { useEffect, useMemo, useRef, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import {
  Activity, BarChart3, Cpu, FolderPlus, ImageIcon, LayoutGrid, List, MoreHorizontal, Plus, SlidersHorizontal,
  Pencil, Trash2, Upload, Users, Layers, ArrowRight, Monitor, ArrowDownWideNarrow,
} from 'lucide-react'
import { AppHeader } from '../components/AppHeader'
import { Footer } from '../components/Footer'
import { api } from '../api/services'
import { toErrorApi } from '../api/client'
import type { EstadoProyecto, Proyecto } from '../api/types'
import { useAsync } from '../lib/useAsync'
import { ESTADO, compacto, ms, num, relativo } from '../lib/format'
import { dibujarHeatmap } from '../viz/render'
import { Menu, MenuContent, MenuItem, MenuItemIcon, MenuTrigger } from '@ui-sdk/react/menu'
import { Segmentado } from '../ui/Segmentado'
import { Selector } from '../ui/Selector'
import { confirmar } from '../ui/confirmar'
import './ProjectsPage.css'

type Filtro = 'TODOS' | 'ANALISIS' | 'COMPLETADO' | 'BORRADOR'
type Orden = 'modificado' | 'nombre' | 'participantes'

// Valor no vacío para la opción «Todos los equipos» (Select no admite '' como valor)
const TODOS_EQUIPOS = '__todos__'

const enAnalisis = (e: EstadoProyecto) => e === 'EN_ANALISIS' || e === 'EN_INGESTA'

export function ProjectsPage() {
  const proyectos = useAsync(() => api.listarProyectos(), [])
  const resumen = useAsync(() => api.resumen(), [])
  const [busqueda, setBusqueda] = useState('')
  const [filtro, setFiltro] = useState<Filtro>('TODOS')
  const [vista, setVista] = useState<'grid' | 'lista'>('grid')
  const [equipo, setEquipo] = useState('')
  const [orden, setOrden] = useState<Orden>('modificado')
  const [errorAccion, setErrorAccion] = useState<string | null>(null)

  const lista = useMemo(() => proyectos.data ?? [], [proyectos.data])
  const equipos = useMemo(() => [...new Set(lista.map((p) => p.dispositivo).filter(Boolean) as string[])], [lista])
  const conteo = {
    TODOS: lista.length,
    ANALISIS: lista.filter((p) => enAnalisis(p.estado)).length,
    COMPLETADO: lista.filter((p) => p.estado === 'COMPLETADO').length,
    BORRADOR: lista.filter((p) => p.estado === 'BORRADOR').length,
  }

  const visibles = useMemo(() => {
    const q = busqueda.trim().toLowerCase()
    return lista
      .filter((p) =>
        filtro === 'TODOS' ? true : filtro === 'ANALISIS' ? enAnalisis(p.estado) : p.estado === filtro,
      )
      .filter((p) => !equipo || p.dispositivo === equipo)
      .filter((p) => !q || [p.nombre, p.descripcion, p.dispositivo ?? ''].some((s) => s.toLowerCase().includes(q)))
      .sort((a, b) =>
        orden === 'nombre'
          ? a.nombre.localeCompare(b.nombre)
          : orden === 'participantes'
            ? b.participantes - a.participantes
            : (b.modificadoEn ?? b.creadoEn).localeCompare(a.modificadoEn ?? a.creadoEn),
      )
  }, [lista, filtro, equipo, busqueda, orden])

  const eliminar = async (p: Proyecto) => {
    const si = await confirmar(`¿Eliminar el proyecto «${p.nombre}»?`, 'Se borrarán sus sesiones, AOIs y métricas. Esta acción no se puede deshacer.', 'Eliminar proyecto')
    if (!si) return
    try {
      await api.eliminarProyecto(p.id)
      proyectos.setData(lista.filter((x) => x.id !== p.id))
      resumen.recargar()
    } catch (e) {
      setErrorAccion(toErrorApi(e).mensaje)
    }
  }

  const r = resumen.data
  return (
    <div className="page">
      <AppHeader busqueda={busqueda} onBuscar={setBusqueda} />
      <main className="projects">
        <div className="projects-head">
          <div>
            <h1>Estudios y Proyectos de Evaluación UX</h1>
            <p className="muted">Gestiona las sesiones de eye tracking, ingesta de datasets crudos y análisis de áreas de interés (AOI).</p>
          </div>
          <Link to="/proyectos/nuevo" className="btn btn-outline-accent">
            <FolderPlus size={16} /> Nuevo Proyecto de Evaluación
          </Link>
        </div>

        <section className="kpis" aria-label="Resumen">
          <Kpi icon={<Layers size={18} />} label="Estudios activos" valor={r ? num(r.estudiosActivos) : '—'} extra={r ? `${r.enAnalisis} en fase de análisis` : ''} />
          <Kpi icon={<Users size={18} />} label="Sujetos evaluados" valor={r ? `${num(r.sujetosEvaluados)} participantes` : '—'} extra={r ? `Promedio ${r.promedioPorEstudio} por estudio` : ''} />
          <Kpi icon={<Activity size={18} />} label="Muestras biométricas" valor={r ? `${compacto(r.muestrasBiometricas)} puntos` : '—'} extra="Normalizados en [0.0, 1.0]" />
          <div className="kpi card">
            <div className="kpi-label row"><Cpu size={14} /> Hardware soportado</div>
            <div className="row" style={{ flexWrap: 'wrap', gap: 6, marginTop: 8 }}>
              {(r?.hardwareSoportado ?? []).map((h) => <span key={h} className="badge">{h}</span>)}
            </div>
          </div>
        </section>

        <div className="toolbar">
          <Segmentado<Filtro>
            variante="pestanas"
            etiqueta="Filtrar por estado"
            valor={filtro}
            onCambio={setFiltro}
            segmentos={([
              ['TODOS', 'Todos'],
              ['ANALISIS', 'En análisis'],
              ['COMPLETADO', 'Completados'],
              ['BORRADOR', 'Borradores'],
            ] as [Filtro, string][]).map(([k, t]) => ({ valor: k, etiqueta: `${t} (${conteo[k]})` }))}
          />
          <div className="spacer" />
          <Segmentado<'grid' | 'lista'>
            etiqueta="Vista"
            valor={vista}
            onCambio={setVista}
            segmentos={[
              { valor: 'grid', nombre: 'Cuadrícula', icono: <LayoutGrid size={15} aria-hidden />, etiqueta: null },
              { valor: 'lista', nombre: 'Lista', icono: <List size={15} aria-hidden />, etiqueta: null },
            ]}
          />
          <Selector
            className="pp-filtro"
            etiqueta="Filtrar por equipo"
            icono={<Monitor size={14} />}
            valor={equipo || TODOS_EQUIPOS}
            onCambio={(v) => setEquipo(v === TODOS_EQUIPOS ? '' : v)}
            opciones={[{ valor: TODOS_EQUIPOS, etiqueta: 'Todos los equipos' }, ...equipos.map((e) => ({ valor: e, etiqueta: e }))]}
          />
          <Selector<Orden>
            className="pp-filtro"
            etiqueta="Ordenar proyectos"
            icono={<ArrowDownWideNarrow size={14} />}
            valor={orden}
            onCambio={setOrden}
            opciones={[
              { valor: 'modificado', etiqueta: 'Ordenar por: Modificado recientemente' },
              { valor: 'nombre', etiqueta: 'Ordenar por: Nombre' },
              { valor: 'participantes', etiqueta: 'Ordenar por: Participantes' },
            ]}
          />
        </div>

        {errorAccion && <div className="alert alert-error" style={{ marginBottom: 16 }}>{errorAccion}</div>}

        {proyectos.cargando ? (
          <div className="center-state"><div className="spinner" />Cargando proyectos…</div>
        ) : proyectos.error ? (
          <div className="center-state">
            <div className="alert alert-error">{proyectos.error.mensaje}</div>
            <button className="btn" onClick={proyectos.recargar}>Reintentar</button>
          </div>
        ) : (
          <div className={vista === 'grid' ? 'grid' : 'lista'}>
            {visibles.map((p) => <ProjectCard key={p.id} p={p} onEliminar={() => eliminar(p)} />)}
            {vista === 'grid' && (
              <Link to="/proyectos/nuevo" className="card new-card">
                <span className="new-icon"><Plus size={22} /></span>
                <strong>Crear un nuevo estudio de usabilidad</strong>
                <span className="muted">Configura estímulos visuales y sube datasets crudos de eye tracking</span>
                <span className="link-accent">Comenzar configuración <ArrowRight size={14} /></span>
              </Link>
            )}
            {!visibles.length && vista === 'lista' && <div className="center-state">No hay proyectos que coincidan con los filtros.</div>}
          </div>
        )}
      </main>
      <Footer />
    </div>
  )
}

function Kpi({ icon, label, valor, extra }: { icon: React.ReactNode; label: string; valor: string; extra: string }) {
  return (
    <div className="kpi card">
      <span className="kpi-icon">{icon}</span>
      <div className="kpi-text">
        <div className="kpi-label">{label}</div>
        <div className="kpi-value">{valor}</div>
        {extra && <div className="kpi-extra">{extra}</div>}
      </div>
    </div>
  )
}

function ProjectCard({ p, onEliminar }: { p: Proyecto; onEliminar: () => void }) {
  const nav = useNavigate()
  const listo = p.estado === 'COMPLETADO' || p.estado === 'EN_ANALISIS'
  const accion = listo
    ? { texto: 'Abrir Dashboard Analítico', icono: <BarChart3 size={15} />, ruta: `/proyectos/${p.id}/dashboard` }
    : p.estado === 'EN_INGESTA'
      ? { texto: 'Continuar Ingesta', icono: <Upload size={15} />, ruta: `/proyectos/${p.id}/ingesta` }
      : { texto: 'Completar Configuración', icono: <SlidersHorizontal size={15} />, ruta: `/proyectos/${p.id}/ingesta` }
  const est = p.estimuloPrincipal
  const fecha = p.modificadoEn ?? p.creadoEn
  const verbo = p.estado === 'BORRADOR' ? 'Creado' : 'Modificado'

  return (
    <article className="card project-card">
      <header className="pc-head">
        <h3 title={p.nombre}>{p.nombre}</h3>
        <Menu onAction={(accion) => {
          if (accion === 'editor') nav(`/proyectos/${p.id}/editor`)
          else if (accion === 'reemplazar') nav(`/proyectos/${p.id}/ingesta`)
          else if (accion === 'eliminar') onEliminar()
        }}>
          <MenuTrigger className="icon-btn" aria-label={`Más acciones de ${p.nombre}`}>
            <MoreHorizontal size={16} aria-hidden />
          </MenuTrigger>
          <MenuContent align="end" className="pc-menu">
            {est && <MenuItem value="editor"><MenuItemIcon><Pencil size={14} /></MenuItemIcon>Editor de AOI</MenuItem>}
            {listo && <MenuItem value="reemplazar"><MenuItemIcon><Upload size={14} /></MenuItemIcon>Reemplazar dataset</MenuItem>}
            <MenuItem value="eliminar" destructive><MenuItemIcon><Trash2 size={14} /></MenuItemIcon>Eliminar proyecto</MenuItem>
          </MenuContent>
        </Menu>
      </header>
      <div className="pc-thumb">
        {est ? (
          <>
            <Thumb proyectoId={p.id} url={est.urlRecurso} ancho={est.anchoOriginalPx} alto={est.altoOriginalPx} conHeatmap={listo || p.participantes > 0} estimuloId={est.id} />
            <span className="pc-res">{est.etiquetaDispositivo ?? 'Estímulo'} ({est.anchoOriginalPx}×{est.altoOriginalPx} px)</span>
          </>
        ) : (
          <div className="pc-empty"><ImageIcon size={22} /> Pendiente de cargar estímulo</div>
        )}
      </div>
      <div className="row" style={{ gap: 6 }}>
        <span className="badge">{p.dispositivo ?? 'Sin hardware asignado'}</span>
        <span className={`badge ${ESTADO[p.estado].clase}`}>{ESTADO[p.estado].texto}</span>
      </div>
      <div className="pc-stats">
        <div><strong>{num(p.participantes)}</strong><span>Participantes</span></div>
        <div><strong>{num(p.aoisConfiguradas)}</strong><span>AOIs configuradas</span></div>
        {p.duracionMediaMs > 0 && <div><strong>{ms(p.duracionMediaMs)}</strong><span>Duración media</span></div>}
      </div>
      <p className="subtle pc-date">{verbo} {relativo(fecha)}</p>
      <Link to={accion.ruta} className="btn btn-block pc-action">{accion.icono} {accion.texto}</Link>
    </article>
  )
}

/** Miniatura del estímulo con un mapa de calor ligero superpuesto. */
function Thumb({ url, ancho, alto, conHeatmap, estimuloId }: { proyectoId: number; url: string; ancho: number; alto: number; conHeatmap: boolean; estimuloId: number }) {
  const canvas = useRef<HTMLCanvasElement>(null)
  const caja = useRef<HTMLDivElement>(null)
  const [tam, setTam] = useState<{ w: number; h: number } | null>(null)

  useEffect(() => {
    const b = caja.current
    if (!b) return
    const medir = () => {
      const s = Math.min(b.clientWidth / ancho, b.clientHeight / alto)
      setTam({ w: Math.round(ancho * s), h: Math.round(alto * s) })
    }
    medir()
    const ro = new ResizeObserver(medir)
    ro.observe(b)
    return () => ro.disconnect()
  }, [ancho, alto])

  useEffect(() => {
    if (!conHeatmap || !tam) return
    let vivo = true
    api.listarFijaciones(estimuloId).then((fx) => {
      const c = canvas.current
      if (!vivo || !c) return
      c.width = tam.w; c.height = tam.h
      dibujarHeatmap(c.getContext('2d')!, fx, tam.w, tam.h, { sigmaPx: Math.max(5, 40 * (tam.w / ancho)), opacidad: 0.7 })
    }).catch(() => undefined)
    return () => { vivo = false }
  }, [conHeatmap, estimuloId, ancho, tam])

  return (
    <div className="thumb" ref={caja}>
      {tam && (
        <div className="thumb-inner" style={{ width: tam.w, height: tam.h }}>
          <img src={url} alt="" width={tam.w} height={tam.h} />
          <canvas ref={canvas} style={{ width: tam.w, height: tam.h }} />
        </div>
      )}
    </div>
  )
}
