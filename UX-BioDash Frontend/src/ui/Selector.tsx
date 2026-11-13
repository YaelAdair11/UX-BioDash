import type { ReactNode } from 'react'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@ui-sdk/react/select'

export interface Opcion<T extends string = string> {
  valor: T
  etiqueta: string
}

interface Props<T extends string> {
  valor: T
  onCambio: (valor: T) => void
  opciones: Opcion<T>[]
  /** Nombre accesible cuando no hay etiqueta visible (filtros de barra). */
  etiqueta?: string
  icono?: ReactNode
  id?: string
  className?: string
  disabled?: boolean
}

/** Select de ui-sdk para listas de opciones de texto; nunca queda vacío. */
export function Selector<T extends string>({ valor, onCambio, opciones, etiqueta, icono, id, className, disabled }: Props<T>) {
  return (
    <span className={`selector ${icono ? 'con-icono' : ''} ${className ?? ''}`}>
      {icono && <span className="selector-icono" aria-hidden>{icono}</span>}
      <Select<T> value={valor} onValueChange={(v) => v !== null && onCambio(v)} disabled={disabled}>
        <SelectTrigger id={id} aria-label={etiqueta}>
          <SelectValue />
        </SelectTrigger>
        <SelectContent>
          {opciones.map((o) => (
            <SelectItem key={o.valor} value={o.valor}>{o.etiqueta}</SelectItem>
          ))}
        </SelectContent>
      </Select>
    </span>
  )
}
