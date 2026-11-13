/**
 * Lectura ligera de archivos CSV/TSV en el navegador.
 * Se usa para la vista previa y validación de columnas antes de subir el archivo
 * (la validación definitiva la realiza el backend en el módulo de ingesta).
 */

export type ColumnaRequerida = 'participante' | 'tiempo' | 'x' | 'y'
export type ColumnaOpcional = 'pantalla' | 'duracion'
export type Columna = ColumnaRequerida | ColumnaOpcional

export const COLUMNAS_REQUERIDAS: ColumnaRequerida[] = ['participante', 'tiempo', 'x', 'y']

const SINONIMOS: Record<Columna, string[]> = {
  participante: ['participante', 'participant', 'sujeto', 'codigo_sujeto', 'usuario', 'user', 'subject', 'recording', 'recordingname', 'participantname', 'id_participante'],
  tiempo: ['tiempo', 'tiempo_ms', 'timestamp', 'time', 'time_ms', 'ms', 't', 'recordingtimestamp', 'eyetrackertimestamp', 'marca_tiempo'],
  x: ['x', 'gaze_x', 'gazex', 'x_px', 'gazepointx', 'gazepointx(mcsnorm)', 'fpogx', 'bpogx', 'x_mirada', 'mirada_x', 'fixationpointx', 'fixationpointx(mcsnorm)'],
  y: ['y', 'gaze_y', 'gazey', 'y_px', 'gazepointy', 'gazepointy(mcsnorm)', 'fpogy', 'bpogy', 'y_mirada', 'mirada_y', 'fixationpointy', 'fixationpointy(mcsnorm)'],
  pantalla: ['pantalla', 'estimulo', 'screen', 'stimulus', 'presentedstimulusname', 'media'],
  duracion: ['duracion', 'duracion_ms', 'duration', 'gazeeventduration', 'fixation_duration', 'fpogd'],
}

export interface TablaLeida {
  delimitador: ',' | '\t' | ';'
  cabeceras: string[]
  filas: string[][]
}

export interface ResultadoValidacion {
  valido: boolean
  mapeo: Partial<Record<Columna, number>>
  faltantes: ColumnaRequerida[]
  filasInvalidas: number
  participantes: string[]
  errores: string[]
  advertencias: string[]
}

const normalizar = (s: string) =>
  s.trim().toLowerCase().normalize('NFD').replace(/[̀-ͯ]/g, '').replace(/[\s"']/g, '')

export function leerTabla(texto: string): TablaLeida {
  const lineas = texto.replace(new RegExp('^' + String.fromCharCode(0xfeff)), '').split(/\r?\n/).filter((l) => l.trim().length > 0)
  const primera = lineas[0] ?? ''
  const conteo = { '\t': primera.split('\t').length, ',': primera.split(',').length, ';': primera.split(';').length }
  const delimitador = (Object.entries(conteo).sort((a, b) => b[1] - a[1])[0][0] as TablaLeida['delimitador'])
  const partir = (l: string) => l.split(delimitador).map((c) => c.trim().replace(/^"(.*)"$/, '$1'))
  return {
    delimitador,
    cabeceras: partir(primera),
    filas: lineas.slice(1).map(partir),
  }
}

export function validarTabla(tabla: TablaLeida): ResultadoValidacion {
  const mapeo: Partial<Record<Columna, number>> = {}
  const cab = tabla.cabeceras.map(normalizar)
  for (const col of Object.keys(SINONIMOS) as Columna[]) {
    const idx = cab.findIndex((c) => SINONIMOS[col].includes(c))
    if (idx >= 0) mapeo[col] = idx
  }
  const faltantes = COLUMNAS_REQUERIDAS.filter((c) => mapeo[c] === undefined)
  const errores: string[] = []
  const advertencias: string[] = []

  if (tabla.filas.length === 0) errores.push('El archivo no contiene filas de datos después de la cabecera.')
  if (faltantes.length) {
    errores.push(
      `Faltan columnas obligatorias: ${faltantes.join(', ')}. Se esperaba al menos: participante, tiempo (ms), x, y.`,
    )
  }

  let filasInvalidas = 0
  const participantes = new Set<string>()
  if (!faltantes.length) {
    for (const fila of tabla.filas) {
      const t = Number(fila[mapeo.tiempo!])
      const x = Number(fila[mapeo.x!])
      const y = Number(fila[mapeo.y!])
      const p = fila[mapeo.participante!]
      if (!p || !Number.isFinite(t) || !Number.isFinite(x) || !Number.isFinite(y) || fila[mapeo.x!] === '' || fila[mapeo.y!] === '') {
        filasInvalidas++
        continue
      }
      participantes.add(p)
    }
    if (filasInvalidas > 0) {
      advertencias.push(
        `${filasInvalidas.toLocaleString('es-MX')} filas tienen valores vacíos o no numéricos y serán descartadas.`,
      )
    }
    if (filasInvalidas === tabla.filas.length) errores.push('Ninguna fila contiene coordenadas y tiempos válidos.')
    if (mapeo.duracion === undefined) {
      advertencias.push('No se encontró columna de duración: las muestras se agruparán automáticamente en fijaciones.')
    }
  }

  return {
    valido: errores.length === 0,
    mapeo,
    faltantes,
    filasInvalidas,
    participantes: [...participantes].sort(),
    errores,
    advertencias,
  }
}
