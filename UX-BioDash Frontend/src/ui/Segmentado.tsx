import type { ReactNode } from 'react'
import { SegmentedControl, SegmentedControlIndicator, SegmentedControlItem } from '@ui-sdk/react/segmented-control'

export interface Segmento<T extends string> {
  valor: T
  etiqueta: ReactNode
  /** Nombre accesible para segmentos que solo muestran un icono. */
  nombre?: string
  icono?: ReactNode
}

interface Props<T extends string> {
  valor: T
  onCambio: (valor: T) => void
  segmentos: Segmento<T>[]
  etiqueta: string
  /** "segmentado": botones en cápsula; "pestanas": texto subrayado. */
  variante?: 'segmentado' | 'pestanas'
  className?: string
}

/** Segmented Control de ui-sdk: elegir una opción de unas pocas, todas visibles. */
export function Segmentado<T extends string>({ valor, onCambio, segmentos, etiqueta, variante = 'segmentado', className }: Props<T>) {
  return (
    <SegmentedControl
      value={valor}
      onValueChange={(v) => v !== null && onCambio(v as T)}
      aria-label={etiqueta}
      className={`${variante} ${className ?? ''}`}
    >
      {variante === 'segmentado' && <SegmentedControlIndicator />}
      {segmentos.map((s) => (
        <SegmentedControlItem key={s.valor} value={s.valor} aria-label={s.nombre}>
          {s.icono}
          {s.etiqueta}
        </SegmentedControlItem>
      ))}
    </SegmentedControl>
  )
}
