import type { EstadoProyecto } from '../api/types'

const nf = new Intl.NumberFormat('es-MX')
export const num = (n: number) => nf.format(n)
export const ms = (n: number | null | undefined) => (n == null ? '—' : `${nf.format(Math.round(n))} ms`)

/** 2450 → "00:02.450" */
export function reloj(msTotal: number) {
  const m = Math.floor(msTotal / 60000)
  const s = Math.floor((msTotal % 60000) / 1000)
  const r = Math.floor(msTotal % 1000)
  return `${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}.${String(r).padStart(3, '0')}`
}

export function compacto(n: number) {
  if (n >= 1e6) return `${(n / 1e6).toLocaleString('es-MX', { maximumFractionDigits: 2 })} M`
  if (n >= 1e3) return `${(n / 1e3).toLocaleString('es-MX', { maximumFractionDigits: 1 })} k`
  return num(n)
}

export function relativo(iso?: string) {
  if (!iso) return ''
  const d = new Date(iso)
  const diff = (Date.now() - d.getTime()) / 1000
  if (diff < 60) return 'hace unos segundos'
  if (diff < 3600) return `hace ${Math.round(diff / 60)} min`
  if (diff < 86400) return `hace ${Math.round(diff / 3600)} horas`
  return `el ${d.toLocaleDateString('es-MX', { day: 'numeric', month: 'long' })}`
}

export const ESTADO: Record<EstadoProyecto, { texto: string; clase: string }> = {
  BORRADOR: { texto: 'Borrador', clase: '' },
  EN_INGESTA: { texto: 'En proceso de ingesta', clase: 'badge-warn' },
  EN_ANALISIS: { texto: 'En análisis', clase: 'badge-accent' },
  COMPLETADO: { texto: 'Completado', clase: 'badge-ok' },
}

export const DISPOSITIVOS = ['Tobii Pro TX300', 'Tobii Pro Fusion', 'EyeLink 1000 Plus', 'Gazepoint GP3', 'Otro / CSV genérico']

export function descargar(nombre: string, contenido: BlobPart, tipo: string) {
  const url = URL.createObjectURL(new Blob([contenido], { type: tipo }))
  const a = document.createElement('a')
  a.href = url
  a.download = nombre
  a.click()
  setTimeout(() => URL.revokeObjectURL(url), 1000)
}
