import { useState } from 'react'
import {
  ColorPicker as UiColorPicker,
  ColorPickerArea,
  ColorPickerAreaThumb,
  ColorPickerEyeDropper,
  ColorPickerInput,
  ColorPickerSlider,
  ColorPickerSliderThumb,
  ColorPickerSwatch,
  ColorPickerSwatches,
  ColorPickerSwatchItem,
} from '@ui-sdk/react/color-picker'
import { Popover, PopoverClose, PopoverContent, PopoverTitle, PopoverTrigger } from '@ui-sdk/react/popover'
import { Pipette, Plus } from 'lucide-react'
import { Pista } from '../ui/Pista'
import { PALETA_AOI } from '../lib/paleta'
import './ColorPicker.css'

const CLAVE_RECIENTES = 'uxbiodash:colores-recientes'
const MAX_RECIENTES = 8

function leerRecientes(): string[] {
  try { return JSON.parse(localStorage.getItem(CLAVE_RECIENTES) ?? '[]') } catch { return [] }
}

function guardarReciente(color: string) {
  const c = color.toUpperCase()
  const lista = [c, ...leerRecientes().filter((r) => r !== c)].slice(0, MAX_RECIENTES)
  try { localStorage.setItem(CLAVE_RECIENTES, JSON.stringify(lista)) } catch { /* sin almacenamiento */ }
  return lista
}

const igual = (a: string, b: string) => a.toLowerCase() === b.toLowerCase()

/** Normaliza cualquier salida hex del picker a #RRGGBB (sin alfa), que es lo que guarda el backend. */
const aHex6 = (v: string) => (/^#[0-9a-f]{6}/i.test(v) ? v.slice(0, 7) : v).toUpperCase()

interface Props {
  valor: string
  onCambio: (color: string) => void
  /** Colores rápidos; por defecto la paleta de AOIs. */
  paleta?: string[]
  etiqueta?: string
}

/**
 * Selector de color sobre ui-sdk: paleta predefinida y un color personalizado en un Popover
 * (área de saturación/brillo, matiz, HEX, cuentagotas y colores recientes).
 * Siempre entrega el color como #RRGGBB en mayúsculas.
 */
export function ColorPicker({ valor, onCambio, paleta = PALETA_AOI, etiqueta = 'Color identificador' }: Props) {
  const [recientes, setRecientes] = useState<string[]>(leerRecientes)
  const personalizado = !paleta.some((c) => igual(c, valor))

  return (
    <UiColorPicker
      className="cp"
      aria-label={etiqueta}
      space="hsv"
      format="hex"
      gamut="srgb"
      value={valor}
      onValueChange={(v) => onCambio(aHex6(v))}
    >
      <div className="cp-fila">
        <ColorPickerSwatches aria-label={etiqueta} className="cp-grid">
          {paleta.map((c) => <ColorPickerSwatchItem key={c} value={c} aria-label={c.toUpperCase()} />)}
        </ColorPickerSwatches>
        <Popover
          onOpenChange={(abierto) => {
            if (!abierto && !paleta.some((c) => igual(c, valor))) setRecientes(guardarReciente(valor))
          }}
        >
          <Pista texto="Color personalizado…" lado="top">
            <PopoverTrigger className="cp-custom" data-personalizado={personalizado || undefined} aria-label="Color personalizado">
              {personalizado ? <ColorPickerSwatch className="cp-custom-muestra" /> : <Plus size={13} aria-hidden />}
            </PopoverTrigger>
          </Pista>
          <PopoverContent className="cp-pop" side="bottom" align="start">
            <PopoverTitle className="cp-sub">Color personalizado</PopoverTitle>
            <ColorPickerArea className="cp-area">
              <ColorPickerAreaThumb />
            </ColorPickerArea>
            <ColorPickerSlider channel="hue" aria-label="Matiz">
              <ColorPickerSliderThumb />
            </ColorPickerSlider>
            <div className="cp-row">
              <ColorPickerSwatch className="cp-preview" />
              <ColorPickerInput className="cp-hex" aria-label="Código HEX" />
              <Pista texto="Tomar un color de la pantalla" lado="top">
                <ColorPickerEyeDropper className="cp-gotero" aria-label="Cuentagotas">
                  <Pipette size={15} aria-hidden />
                </ColorPickerEyeDropper>
              </Pista>
            </div>
            {recientes.length > 0 && (
              <>
                <span className="cp-sub" aria-hidden>Recientes</span>
                <ColorPickerSwatches aria-label="Colores recientes" className="cp-grid">
                  {recientes.map((c) => <ColorPickerSwatchItem key={c} value={c} aria-label={c} />)}
                </ColorPickerSwatches>
              </>
            )}
            <PopoverClose className="btn btn-sm btn-primary">Listo</PopoverClose>
          </PopoverContent>
        </Popover>
      </div>
    </UiColorPicker>
  )
}
