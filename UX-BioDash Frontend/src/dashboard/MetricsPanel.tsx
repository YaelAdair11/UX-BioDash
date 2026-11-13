import { useMemo, useState } from 'react'
import { ArrowDown, ArrowUp, BarChart3 } from 'lucide-react'
import type { AreaInteres, MetricasProyecto } from '../api/types'
import { HelpCard } from '../components/HelpCard'
import { ms, num } from '../lib/format'
import { colorAoi } from '../viz/render'
import { Tabs, TabsList, TabsPanel, TabsTab } from '@ui-sdk/react/tabs'

type Vista = 'aoi' | 'participante'

interface Props {
  metricas: MetricasProyecto | null
  aois: AreaInteres[]
  cargando: boolean
  sesionSeleccionada: number | null
}

type Col = 'etiqueta' | 'ttffMs' | 'dwellTimeMs' | 'conteoFijaciones' | 'revisitas' | 'duracionMediaFijacionMs'


/** Tabla comparativa de métricas por AOI y por participante. */
export function MetricsPanel({ metricas, aois, cargando, sesionSeleccionada }: Props) {
  const [vista, setVista] = useState<Vista>('aoi')
  const [orden, setOrden] = useState<{ col: Col; desc: boolean }>({ col: 'dwellTimeMs', desc: true })

  const filasAoi = useMemo(() => {
    if (!metricas) return []
    const dentro = metricas.porAoi.filter((m) => m.aoiId !== null)
    const fuera = metricas.porAoi.filter((m) => m.aoiId === null)
    const v = (x: (typeof dentro)[number]) => x[orden.col] ?? (orden.desc ? -Infinity : Infinity)
    dentro.sort((a, b) => {
      const va = v(a), vb = v(b)
      const c = typeof va === 'string' ? va.localeCompare(vb as string) : (va as number) - (vb as number)
      return orden.desc ? -c : c
    })
    return [...dentro, ...fuera]
  }, [metricas, orden])

  const th = (col: Col, texto: string, ayuda?: Parameters<typeof HelpCard>[0]['tema']) => (
    <th className="sortable" onClick={() => setOrden((o) => ({ col, desc: o.col === col ? !o.desc : true }))} aria-sort={orden.col === col ? (orden.desc ? 'descending' : 'ascending') : 'none'}>
      <span className="row" style={{ gap: 4, display: 'inline-flex' }}>
        {texto}
        {orden.col === col && (orden.desc ? <ArrowDown size={12} /> : <ArrowUp size={12} />)}
        {ayuda && <span onClick={(e) => e.stopPropagation()}><HelpCard tema={ayuda} align={col === 'revisitas' || col === 'duracionMediaFijacionMs' ? 'right' : 'left'} /></span>}
      </span>
    </th>
  )

  const max = Math.max(1, ...filasAoi.map((f) => f.dwellPct))

  return (
    <section className="metrics" aria-label="Métricas UX">
      <Tabs value={vista} onValueChange={(v) => v && setVista(v as Vista)}>
        <div className="metrics-head">
          <BarChart3 size={17} color="var(--accent-strong)" />
          <h2>Métricas UX</h2>
          <TabsList className="metrics-tabs" aria-label="Agrupar métricas">
            <TabsTab value="aoi">Por AOI</TabsTab>
            <TabsTab value="participante">Por participante</TabsTab>
          </TabsList>
          <span className="spacer" />
          {metricas && (
            <span className="subtle" style={{ fontSize: 12.5 }}>
              {sesionSeleccionada ? 'Participante seleccionado' : 'Promedio por participante'} · {num(metricas.participantes)} participantes · {num(metricas.totalFijaciones)} fijaciones
              {cargando && ' · actualizando…'}
            </span>
          )}
        </div>
        <div className="metrics-body">
          {!metricas ? (
            <div className="center-state" style={{ padding: 30 }}><div className="spinner" /></div>
          ) : (
            <>
              <TabsPanel value="aoi">
                <table className="table">
                  <thead>
                    <tr>
                      {th('etiqueta', 'Área de interés (AOI)')}
                      {th('ttffMs', 'TTFF (1ª fijación)', 'ttff')}
                      {th('dwellTimeMs', 'Dwell time total', 'dwell')}
                      {th('conteoFijaciones', 'Conteo fijaciones', 'conteo')}
                      {th('duracionMediaFijacionMs', 'Duración media', 'duracionMedia')}
                      {th('revisitas', 'Revisitas', 'revisitas')}
                    </tr>
                  </thead>
                  <tbody>
                    {filasAoi.map((m) => {
                      const i = aois.findIndex((a) => a.id === m.aoiId)
                      const color = colorAoi(aois, m.aoiId)
                      return (
                        <tr key={m.aoiId ?? 'fuera'}>
                          <td><span className="row"><i className="dot" style={{ background: color }} />{m.aoiId === null ? m.etiqueta : `AOI ${i + 1}: ${m.etiqueta}`}</span></td>
                          <td>{ms(m.ttffMs)}</td>
                          <td>
                            <span className="row" style={{ gap: 10 }}>
                              <span style={{ minWidth: 76 }}>{ms(m.dwellTimeMs)}</span>
                              <span className="badge mono">{m.dwellPct.toLocaleString('es-MX', { minimumFractionDigits: 1, maximumFractionDigits: 1 })}%</span>
                              <span className="bar"><i style={{ width: `${(m.dwellPct / max) * 100}%`, background: color }} /></span>
                            </span>
                          </td>
                          <td>{m.aoiId === null ? num(m.conteoFijaciones) : `${num(m.conteoFijaciones)} fijaciones`}</td>
                          <td>{ms(m.duracionMediaFijacionMs || null)}</td>
                          <td>{m.revisitas == null ? '—' : `${num(m.revisitas)} revisitas`}</td>
                        </tr>
                      )
                    })}
                    {!aois.length && (
                      <tr><td colSpan={6} className="subtle">Aún no hay AOIs. Dibuja un rectángulo sobre el estímulo para empezar a medir.</td></tr>
                    )}
                  </tbody>
                </table>
              </TabsPanel>
              <TabsPanel value="participante">
                <table className="table">
                  <thead>
                    <tr>
                      <th>Participante</th>
                      <th>Fijaciones <HelpCard tema="conteo" /></th>
                      <th>Duración media <HelpCard tema="duracionMedia" /></th>
                      <th>Duración sesión</th>
                      {aois.map((a, i) => (
                        <th key={a.id}>
                          <span className="row" style={{ display: 'inline-flex', gap: 6 }}>
                            <i className="dot" style={{ background: colorAoi(aois, a.id) }} /> TTFF AOI {i + 1}
                            {i === aois.length - 1 && <HelpCard tema="ttff" align="right" />}
                          </span>
                        </th>
                      ))}
                    </tr>
                  </thead>
                  <tbody>
                    {metricas.porParticipante.map((p) => (
                      <tr key={p.sesionId}>
                        <td className="mono">{p.codigoSujeto}</td>
                        <td>{num(p.conteoFijaciones)}</td>
                        <td>{ms(p.duracionMediaFijacionMs)}</td>
                        <td>{ms(p.duracionTotalMs)}</td>
                        {aois.map((a) => {
                          const v = p.ttffPorAoi[a.id]
                          return <td key={a.id} className={v == null ? 'subtle' : ''}>{v == null ? 'No la miró' : ms(v)}</td>
                        })}
                      </tr>
                    ))}
                  </tbody>
                </table>
              </TabsPanel>
            </>
          )}
        </div>
      </Tabs>
    </section>
  )
}
