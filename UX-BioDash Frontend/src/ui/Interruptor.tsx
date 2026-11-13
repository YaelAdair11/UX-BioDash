import { Switch, SwitchThumb } from '@ui-sdk/react/switch'

interface Props {
  etiqueta: string
  detalle?: string
  activo: boolean
  onCambio?: (activo: boolean) => void
  disabled?: boolean
  title?: string
}

/** Switch de ui-sdk dentro de su etiqueta, con un texto secundario opcional. */
export function Interruptor({ etiqueta, detalle, activo, onCambio, disabled, title }: Props) {
  return (
    <label className="interruptor" title={title}>
      <span className="interruptor-texto">
        <span className="muted">{etiqueta}</span>
        {detalle && <small>{detalle}</small>}
      </span>
      <Switch checked={activo} onCheckedChange={(v) => onCambio?.(v)} disabled={disabled}>
        <SwitchThumb />
      </Switch>
    </label>
  )
}
