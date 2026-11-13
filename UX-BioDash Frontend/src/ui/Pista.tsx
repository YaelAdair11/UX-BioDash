import type { ReactElement } from 'react'
import { Tooltip, TooltipContent, TooltipShortcut, TooltipTrigger } from '@ui-sdk/react/tooltip'

interface Props {
  texto: string
  atajo?: string
  lado?: 'top' | 'right' | 'bottom' | 'left'
  /** El botón (u otro control) que recibe la pista; debe reenviar props y ref. */
  children: ReactElement<Record<string, unknown>>
}

/** Tooltip de ui-sdk que nombra un botón de solo icono (sustituye a title=""). */
export function Pista({ texto, atajo, lado = 'right', children }: Props) {
  // TooltipShortcut separa combinaciones por "+" ("Ctrl+K"); la tecla "+" sola rompe su análisis, así que va como texto
  const atajoSeguro = atajo && !/(^|\+)\+?$/.test(atajo) ? atajo : null
  return (
    <Tooltip>
      <TooltipTrigger render={children} />
      <TooltipContent side={lado}>
        {texto}
        {atajoSeguro ? <TooltipShortcut shortcut={atajoSeguro} /> : atajo && <kbd>{atajo}</kbd>}
      </TooltipContent>
    </Tooltip>
  )
}
