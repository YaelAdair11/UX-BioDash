import { Slider, SliderControl, SliderIndicator, SliderThumb, SliderTrack } from '@ui-sdk/react/slider'

interface Props {
  etiqueta: string
  valor: number
  onCambio: (valor: number) => void
  min: number
  max: number
  paso?: number
  sufijo?: string
}

/** Slider de ui-sdk de un solo valor, con su etiqueta y el valor visible a la derecha. */
export function Deslizador({ etiqueta, valor, onCambio, min, max, paso = 1, sufijo = '' }: Props) {
  return (
    <div className="deslizador">
      <div className="row">
        <span className="muted">{etiqueta}</span>
        <span className="value-box" aria-hidden>{valor}{sufijo}</span>
      </div>
      <Slider
        value={valor}
        min={min}
        max={max}
        step={paso}
        onValueChange={(v) => onCambio(Array.isArray(v) ? v[0] : v)}
      >
        <SliderControl>
          <SliderTrack>
            <SliderIndicator />
            <SliderThumb aria-label={etiqueta} />
          </SliderTrack>
        </SliderControl>
      </Slider>
    </div>
  )
}
