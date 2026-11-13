import { useEffect, useMemo, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { ArrowLeft, BarChart3, PenSquare, CheckCircle2, Download, FileSpreadsheet, ImageIcon, UploadCloud, X } from 'lucide-react'
import { AppHeader } from '../components/AppHeader'
import { api } from '../api/services'
import { toErrorApi } from '../api/client'
import type { ErrorApi, Proyecto, ResumenIngesta } from '../api/types'
import { COLUMNAS_REQUERIDAS, leerTabla, validarTabla, type ResultadoValidacion, type TablaLeida } from '../lib/tabular'
import { DISPOSITIVOS, descargar, num } from '../lib/format'
import { Field, FieldLabel } from '@ui-sdk/react/field'
import { FileInput, FileInputDropzone, FileInputTrigger } from '@ui-sdk/react/file-input'
import { Input } from '@ui-sdk/react/input'
import { Textarea } from '@ui-sdk/react/textarea'
import { Selector } from '../ui/Selector'
import './IngestPage.css'

const TAM_MAX_MB = 200

export function IngestPage() {
  const { id } = useParams()
  const proyectoId = id ? Number(id) : null
  const nav = useNavigate()

  const [proyecto, setProyecto] = useState<Proyecto | null>(null)
  const [nombre, setNombre] = useState('')
  const [descripcion, setDescripcion] = useState('')
  const [dispositivo, setDispositivo] = useState(DISPOSITIVOS[0])

  const [archivo, setArchivo] = useState<File | null>(null)
  const [tabla, setTabla] = useState<TablaLeida | null>(null)
  const [validacion, setValidacion] = useState<ResultadoValidacion | null>(null)
  const [imagen, setImagen] = useState<File | null>(null)
  const [imagenInfo, setImagenInfo] = useState<{ url: string; ancho: number; alto: number } | null>(null)
  const [errorArchivo, setErrorArchivo] = useState<string | null>(null)

  const [enviando, setEnviando] = useState(false)
  const [progreso, setProgreso] = useState(0)
  const [error, setError] = useState<ErrorApi | null>(null)
  const [resultado, setResultado] = useState<ResumenIngesta | null>(null)

  useEffect(() => {
    if (!proyectoId) return
    api.obtenerProyecto(proyectoId).then(setProyecto).catch((e) => setError(toErrorApi(e)))
  }, [proyectoId])

  const elegirArchivo = async (f: File | null) => {
    setErrorArchivo(null)
    setArchivo(null); setTabla(null); setValidacion(null)
    if (!f) return
    if (!/\.(csv|tsv|txt)$/i.test(f.name)) return setErrorArchivo('Formato no soportado. Sube un archivo de texto .csv o .tsv exportado por tu equipo.')
    if (f.size > TAM_MAX_MB * 1024 * 1024) return setErrorArchivo(`El archivo supera ${TAM_MAX_MB} MB.`)
    // Para la vista previa solo leemos los primeros ~2 MB
    const texto = await f.slice(0, 2 * 1024 * 1024).text()
    const t = leerTabla(texto)
    setArchivo(f); setTabla(t); setValidacion(validarTabla(t))
  }

  const elegirImagen = (f: File | null) => {
    setErrorArchivo(null)
    if (imagenInfo) URL.revokeObjectURL(imagenInfo.url)
    setImagen(null); setImagenInfo(null)
    if (!f) return
    if (!/^image\/(png|jpe?g)$/.test(f.type)) return setErrorArchivo('La captura del estímulo debe ser PNG o JPG.')
    const url = URL.createObjectURL(f)
    const img = new Image()
    img.onload = () => { setImagen(f); setImagenInfo({ url, ancho: img.naturalWidth, alto: img.naturalHeight }) }
    img.onerror = () => setErrorArchivo('No se pudo leer la imagen.')
    img.src = url
  }

  const esNuevo = !proyectoId
  const puedeEnviar = !enviando && archivo && imagen && validacion?.valido && (!esNuevo || nombre.trim().length > 0)

  const enviar = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!puedeEnviar) return
    setEnviando(true); setError(null); setProgreso(0)
    try {
      let pid = proyectoId
      if (esNuevo) {
        const p = await api.crearProyecto({ nombre: nombre.trim(), descripcion: descripcion.trim(), dispositivo })
        pid = p.id
        setProyecto(p)
      }
      const r = await api.subirDataset(pid!, archivo!, imagen!, setProgreso)
      setResultado(r)
    } catch (err) {
      setError(toErrorApi(err))
    } finally {
      setEnviando(false)
    }
  }

  const guardarSinDatos = async () => {
    if (!nombre.trim()) return setError({ status: 400, mensaje: 'El nombre del proyecto es obligatorio.' })
    try {
      await api.crearProyecto({ nombre: nombre.trim(), descripcion: descripcion.trim(), dispositivo })
      nav('/')
    } catch (err) {
      setError(toErrorApi(err))
    }
  }

  const pid = proyecto?.id ?? proyectoId

  return (
    <div className="page">
      <AppHeader />
      <main className="ingest">
        <Link to="/" className="back"><ArrowLeft size={15} /> Volver a proyectos</Link>
        <h1>{esNuevo ? 'Nuevo proyecto de evaluación' : proyecto ? proyecto.nombre : 'Cargando…'}</h1>
        <p className="muted" style={{ marginBottom: 22 }}>
          Sube el archivo exportado por el eye tracker (CSV o TSV) y la captura de la interfaz evaluada (PNG o JPG).
          El servidor valida, normaliza tiempos a t=0 ms y coordenadas a [0, 1], y calcula las fijaciones.
        </p>

        {resultado ? (
          <Resultado r={resultado} proyectoId={pid!} />
        ) : (
          <form onSubmit={enviar} className="ingest-grid">
            {esNuevo && (
              <section className="card step">
                <StepTitle n={1} titulo="Datos del estudio" />
                <Field name="nombre" className="ing-field">
                  <FieldLabel>Nombre del proyecto *</FieldLabel>
                  <Input maxLength={120} value={nombre} onValueChange={setNombre} placeholder="Ej. Estudio Checkout E-Commerce" required />
                </Field>
                <Field name="descripcion" className="ing-field">
                  <FieldLabel>Descripción y objetivos</FieldLabel>
                  <Textarea value={descripcion} onValueChange={setDescripcion} placeholder="¿Qué tarea realizaron los participantes? ¿Qué quieres averiguar?" />
                </Field>
                <Field className="ing-field">
                  <FieldLabel>Equipo de eye tracking</FieldLabel>
                  <Selector className="ing-selector" valor={dispositivo} onCambio={setDispositivo} opciones={DISPOSITIVOS.map((d) => ({ valor: d, etiqueta: d }))} />
                </Field>
              </section>
            )}

            <section className="card step">
              <StepTitle n={esNuevo ? 2 : 1} titulo="Archivo de eye tracking" extra={
                <button type="button" className="btn btn-ghost btn-sm" onClick={descargarPlantilla}><Download size={14} /> Plantilla CSV</button>
              } />
              <Dropzone
                accept=".csv,.tsv,.txt,text/csv,text/tab-separated-values"
                icono={<FileSpreadsheet size={26} />}
                texto="Arrastra aquí tu archivo .csv o .tsv"
                archivo={archivo}
                onArchivo={elegirArchivo}
                onRechazo={setErrorArchivo}
                mensajeRechazo="Formato no soportado. Sube un archivo de texto .csv o .tsv exportado por tu equipo."
              />
              <p className="subtle hint">Columnas mínimas: <span className="mono">{COLUMNAS_REQUERIDAS.join(', ')}</span>. Opcionales: <span className="mono">pantalla, duracion</span>. Tiempos en ms; coordenadas en píxeles o ya normalizadas.</p>
              {validacion && tabla && <Validacion v={validacion} t={tabla} />}
            </section>

            <section className="card step">
              <StepTitle n={esNuevo ? 3 : 2} titulo="Captura del estímulo" />
              {imagenInfo ? (
                <div className="img-preview">
                  <img src={imagenInfo.url} alt="Vista previa del estímulo" />
                  <div className="row img-meta">
                    <ImageIcon size={14} /> <span>{imagen!.name}</span>
                    <span className="badge">{imagenInfo.ancho} × {imagenInfo.alto} px</span>
                    <span className="spacer" />
                    <button type="button" className="icon-btn" aria-label="Quitar imagen" onClick={() => elegirImagen(null)}><X size={15} /></button>
                  </div>
                </div>
              ) : (
                <Dropzone
                  accept="image/png,image/jpeg"
                  icono={<ImageIcon size={26} />}
                  texto="Arrastra aquí la captura PNG o JPG"
                  archivo={null}
                  onArchivo={elegirImagen}
                  onRechazo={setErrorArchivo}
                  mensajeRechazo="La captura del estímulo debe ser PNG o JPG."
                />
              )}
              <p className="subtle hint">Usa la captura a la misma resolución con la que se presentó a los participantes para que la mirada quede alineada.</p>
            </section>

            <div className="ingest-actions">
              {errorArchivo && <div className="alert alert-error">{errorArchivo}</div>}
              {error && (
                <div className="alert alert-error" role="alert">
                  <div>
                    <strong>{error.mensaje}</strong>
                    {error.detalles?.length ? <ul>{error.detalles.map((d) => <li key={d}>{d}</li>)}</ul> : null}
                  </div>
                </div>
              )}
              {enviando && (
                <div className="progress" aria-label="Progreso de carga">
                  <div className="row"><span>{progreso < 100 ? 'Subiendo archivos…' : 'Procesando: validación, normalización y cálculo de fijaciones…'}</span><span className="spacer" /><span className="mono">{progreso}%</span></div>
                  <div className="bar" style={{ width: '100%' }}><i style={{ width: `${progreso}%`, background: 'var(--accent)' }} /></div>
                </div>
              )}
              <div className="row" style={{ justifyContent: 'flex-end' }}>
                {esNuevo && <button type="button" className="btn btn-ghost" onClick={guardarSinDatos} disabled={enviando}>Guardar como borrador</button>}
                <button type="submit" className="btn btn-primary" disabled={!puedeEnviar}>
                  <UploadCloud size={16} /> {enviando ? 'Procesando…' : 'Subir y procesar dataset'}
                </button>
              </div>
            </div>
          </form>
        )}
      </main>
    </div>
  )
}

function StepTitle({ n, titulo, extra }: { n: number; titulo: string; extra?: React.ReactNode }) {
  return (
    <div className="step-title">
      <span className="step-n">{n}</span>
      <h2>{titulo}</h2>
      <span className="spacer" />
      {extra}
    </div>
  )
}

/** Zona de carga de un solo archivo sobre FileInput de ui-sdk: arrastrar, pegar o elegir con el botón. */
function Dropzone({ accept, icono, texto, archivo, onArchivo, onRechazo, mensajeRechazo }: {
  accept: string; icono: React.ReactNode; texto: string; archivo: File | null; onArchivo: (f: File | null) => void
  onRechazo: (mensaje: string) => void; mensajeRechazo: string
}) {
  if (archivo) {
    return (
      <div className="file-chip">
        <FileSpreadsheet size={18} />
        <div style={{ minWidth: 0 }}>
          <div className="file-name">{archivo.name}</div>
          <div className="subtle" style={{ fontSize: 12 }}>{(archivo.size / 1024).toLocaleString('es-MX', { maximumFractionDigits: 1 })} KB</div>
        </div>
        <span className="spacer" />
        <button type="button" className="icon-btn" aria-label="Quitar archivo" onClick={() => onArchivo(null)}><X size={15} /></button>
      </div>
    )
  }
  return (
    <FileInput
      accept={accept}
      value={[]}
      onValueChange={(files) => onArchivo(files[0] ?? null)}
      onReject={() => onRechazo(mensajeRechazo)}
    >
      <FileInputDropzone className="dropzone">
        {icono}
        <strong>{texto}</strong>
        <span className="subtle">o</span>
        <FileInputTrigger className="btn btn-sm">Elegir archivo</FileInputTrigger>
      </FileInputDropzone>
    </FileInput>
  )
}

function Validacion({ v, t }: { v: ResultadoValidacion; t: TablaLeida }) {
  const columnaDe = useMemo(() => {
    const m = new Map<number, string>()
    for (const [k, i] of Object.entries(v.mapeo)) m.set(i as number, k)
    return m
  }, [v])
  return (
    <div className="validation">
      <div className="row" style={{ flexWrap: 'wrap', gap: 6 }}>
        {v.valido ? <span className="badge badge-ok"><CheckCircle2 size={12} /> Estructura válida</span> : <span className="badge" style={{ color: 'var(--danger-text)' }}>Estructura inválida</span>}
        <span className="badge">Separador: {t.delimitador === '\t' ? 'tabulador' : `"${t.delimitador}"`}</span>
        <span className="badge">{num(t.filas.length)} filas leídas (vista previa)</span>
        {v.participantes.length > 0 && <span className="badge">{v.participantes.length} participantes</span>}
      </div>
      {v.errores.length > 0 && <div className="alert alert-error"><ul>{v.errores.map((e) => <li key={e}>{e}</li>)}</ul></div>}
      {v.advertencias.length > 0 && <div className="alert alert-warn"><ul>{v.advertencias.map((e) => <li key={e}>{e}</li>)}</ul></div>}
      <div className="preview-wrap">
        <table className="table preview">
          <thead>
            <tr>
              {t.cabeceras.map((c, i) => (
                <th key={i}>
                  {c}
                  {columnaDe.has(i) && <span className="col-tag">{columnaDe.get(i)}</span>}
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {t.filas.slice(0, 5).map((f, i) => <tr key={i}>{t.cabeceras.map((_, j) => <td key={j} className="mono">{f[j] ?? ''}</td>)}</tr>)}
          </tbody>
        </table>
      </div>
    </div>
  )
}

function Resultado({ r, proyectoId }: { r: ResumenIngesta; proyectoId: number }) {
  return (
    <div className="card step" style={{ maxWidth: 720 }}>
      <div className="row" style={{ gap: 10 }}>
        <CheckCircle2 size={26} color="var(--ok)" />
        <h2 style={{ fontSize: 18 }}>Dataset procesado correctamente</h2>
      </div>
      <div className="result-grid">
        <div><strong>{num(r.sesionesCreadas)}</strong><span>Sesiones / participantes</span></div>
        <div><strong>{num(r.filasLeidas)}</strong><span>Filas leídas</span></div>
        <div><strong>{num(r.filasDescartadas)}</strong><span>Filas descartadas</span></div>
        <div><strong>{num(r.fijacionesCalculadas)}</strong><span>Fijaciones calculadas</span></div>
      </div>
      {r.advertencias.length > 0 && <div className="alert alert-info"><ul>{r.advertencias.map((a) => <li key={a}>{a}</li>)}</ul></div>}
      <p className="muted">Siguiente paso: abre el dashboard y dibuja las Áreas de Interés (AOI) sobre los elementos clave de la interfaz.</p>
      <div className="row" style={{ justifyContent: 'flex-end' }}>
        <Link to="/" className="btn btn-ghost">Volver a proyectos</Link>
        <Link to={`/proyectos/${proyectoId}/dashboard`} className="btn"><BarChart3 size={16} /> Ir al dashboard</Link>
        <Link to={`/proyectos/${proyectoId}/editor`} className="btn btn-primary"><PenSquare size={16} /> Delimitar áreas de interés</Link>
      </div>
    </div>
  )
}

/** Plantilla de ejemplo con el formato mínimo esperado (participante, tiempo, x, y). */
function descargarPlantilla() {
  const filas = ['pantalla,participante,tiempo_ms,x,y']
  let semilla = 42
  const rnd = () => ((semilla = (semilla * 16807) % 2147483647) / 2147483647)
  const objetivos = [[540, 280], [1150, 300], [380, 650], [1520, 890], [1500, 560]]
  for (const p of ['P01', 'P02', 'P03']) {
    let t = 1_000_000 + Math.round(rnd() * 5000)
    for (let k = 0; k < 18; k++) {
      const [ox, oy] = objetivos[Math.floor(rnd() * objetivos.length)]
      const cx = ox + (rnd() - 0.5) * 160, cy = oy + (rnd() - 0.5) * 100
      const n = 8 + Math.floor(rnd() * 12)
      for (let s = 0; s < n; s++) {
        filas.push(`checkout.png,${p},${t},${(cx + (rnd() - 0.5) * 14).toFixed(1)},${(cy + (rnd() - 0.5) * 14).toFixed(1)}`)
        t += 17
      }
      t += 40
    }
  }
  descargar('plantilla_eye_tracking.csv', filas.join('\n'), 'text/csv')
}
