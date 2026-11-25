/**
 * Lectura ligera de archivos CSV/TSV en el navegador.
 * Se usa para la vista previa y validación de columnas antes de subir el archivo
 * (la validación definitiva la realiza el backend en el módulo de ingesta).
 */

export type ColumnaRequerida = 'participante' | 'tiempo' | 'x' | 'y'
export type ColumnaOpcional = 'pantalla' | 'duracion'
export type Columna = ColumnaRequerida | ColumnaOpcional

export const COLUMNAS_REQUERIDAS: ColumnaRequerida[] = ['participante', 'tiempo', 'x', 'y']

// Mismos alias que reconoce el backend (DetectorFormato) para Tobii, EyeLink, Gazepoint y el formato genérico,
// escritos sin espacios porque `normalizar` los elimina. Si se agrega un alias en el backend, agregarlo aquí también.
const SINONIMOS: Record<Columna, string[]> = {
  participante: ['participante', 'participant', 'participant_id', 'participantid', 'sujeto', 'codigo_sujeto', 'usuario', 'user', 'user_id',
    'subject', 'subject_id', 'recording', 'recordingname', 'participantname', 'id_participante',
    'recording_session_label'],
  // Incluye el inicio de cada fijación (archivos de fijaciones).
  tiempo: ['tiempo', 'tiempo_ms', 'timestamp', 'time', 'time_ms', 'ms', 't', 'recordingtimestamp', 'eyetrackertimestamp', 'marca_tiempo',
    'inicio_ms', 'inicio', 'start_ms', 'start', 'start_time', 'timestamp_ms',
    'starttimestamp', 'eyemovementstarttimestamp', 'current_fix_start'],
  x: ['x', 'gaze_x', 'gazex', 'x_px', 'gazepointx', 'fpogx', 'bpogx', 'x_mirada', 'mirada_x', 'fixationpointx',
    'average_gaze_x', 'gx', 'right_gaze_x', 'left_gaze_x', 'current_fix_x',
    'avg_x', 'coord_x', 'x_raw', 'x_normalizado', 'fixation_x', 'fijacion_x', 'pos_x'],
  y: ['y', 'gaze_y', 'gazey', 'y_px', 'gazepointy', 'fpogy', 'bpogy', 'y_mirada', 'mirada_y', 'fixationpointy',
    'average_gaze_y', 'gy', 'right_gaze_y', 'left_gaze_y', 'current_fix_y',
    'avg_y', 'coord_y', 'y_raw', 'y_normalizado', 'fixation_y', 'fijacion_y', 'pos_y'],
  pantalla: ['pantalla', 'estimulo', 'screen', 'screen_id', 'stimulus', 'presentedstimulusname', 'media', 'medianame', 'media_name', 'media_id',
    'image', 'trial_label'],
  duracion: ['duracion', 'duracion_ms', 'duration', 'duration_ms', 'gazeeventduration', 'eyemovementduration', 'fixation_duration', 'fpogd',
    'current_fix_duration'],
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

// Quita acentos, comillas, espacios y la unidad entre corchetes o paréntesis, como el backend
// (p. ej. "Recording timestamp [μs]" -> "recordingtimestamp", "Gaze point X (MCSnorm)" -> "gazepointx").
const normalizar = (s: string) =>
  s.trim().toLowerCase().normalize('NFD').replace(/[̀-ͯ]/g, '').replace(/\[[^\]]*\]|\([^)]*\)/g, '').replace(/[\s"']/g, '')

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
