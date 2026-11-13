import { useEffect, useLayoutEffect, useRef } from 'react'
import { Pause, Play, RotateCcw } from 'lucide-react'
import type { Fijacion } from '../api/types'
import { num, reloj } from '../lib/format'
import { Slider, SliderControl, SliderIndicator, SliderThumb, SliderTrack } from '@ui-sdk/react/slider'
import { Selector } from '../ui/Selector'
import { Pista } from '../ui/Pista'

interface Props {
  tiempo: number
  duracion: number
  reproduciendo: boolean
  velocidad: number
  fijaciones: Fijacion[]
  onTiempo: (t: number) => void
  onReproducir: (v: boolean) => void
  onVelocidad: (v: number) => void
}

/** Línea de tiempo: reproduce la aparición de fijaciones desde t=0 ms. */
export function Timeline(p: Props) {
  const { reproduciendo, velocidad, duracion, onTiempo, onReproducir } = p
  // Último tiempo conocido para el bucle de animación (se actualiza tras cada render, no durante)
  const tiempoRef = useRef(p.tiempo)
  useLayoutEffect(() => { tiempoRef.current = p.tiempo })

  useEffect(() => {
    if (!reproduciendo) return
    let raf = 0
    let previo = performance.now()
    const paso = (ahora: number) => {
      const t = tiempoRef.current + (ahora - previo) * velocidad
      previo = ahora
      if (t >= duracion) {
        onTiempo(duracion)
        onReproducir(false)
        return
      }
      onTiempo(t)
      raf = requestAnimationFrame(paso)
    }
    raf = requestAnimationFrame(paso)
    return () => cancelAnimationFrame(raf)
  }, [reproduciendo, velocidad, duracion, onTiempo, onReproducir])

  const alternar = () => {
    if (!reproduciendo && p.tiempo >= duracion - 1) onTiempo(0)
    onReproducir(!reproduciendo)
  }

  const marcas = Math.max(1, Math.ceil(duracion / 2000))
  const paso = duracion > 0 ? Math.ceil(duracion / marcas / 1000) * 1000 : 1000
  const etiquetas: number[] = []
  for (let t = 0; t <= duracion; t += paso) etiquetas.push(t)

  return (
    <div className="timeline">
      <Pista texto={reproduciendo ? 'Pausar' : 'Reproducir'} atajo="Space" lado="top">
        <button type="button" className="play" onClick={alternar} aria-label={reproduciendo ? 'Pausar' : 'Reproducir'}>
          {reproduciendo ? <Pause size={18} aria-hidden /> : <Play size={18} aria-hidden />}
        </button>
      </Pista>
      <div className="tl-clock">
        <div className="mono">{reloj(p.tiempo)} / {reloj(duracion)} ms</div>
        <div className="subtle" style={{ fontSize: 12 }}>de {num(Math.round(duracion))} ms</div>
      </div>
      <div className="tl-track">
        <div className="tl-labels" aria-hidden>
          {etiquetas.map((t) => (
            <span key={t} style={{ left: `${(t / duracion) * 100}%` }}>{t === 0 ? '0 ms' : num(t)}</span>
          ))}
        </div>
        <Slider
          className="tl-slider"
          value={Math.min(p.tiempo, duracion)}
          min={0}
          max={duracion}
          step={10}
          largeStep={500}
          format={{ style: 'unit', unit: 'millisecond', unitDisplay: 'short', maximumFractionDigits: 0 }}
          onValueChange={(v) => {
            onReproducir(false)
            onTiempo(Array.isArray(v) ? v[0] : v)
          }}
        >
          <SliderControl>
            <SliderTrack>
              <SliderIndicator />
              <span className="tl-ticks" aria-hidden>
                {p.fijaciones.map((f) => (
                  <i key={f.id} className={f.inicioMs <= p.tiempo ? 'on' : ''} style={{ left: `${(f.inicioMs / duracion) * 100}%` }} />
                ))}
              </span>
              <SliderThumb aria-label="Tiempo de la prueba" />
            </SliderTrack>
          </SliderControl>
        </Slider>
      </div>
      <Selector
        className="tl-speed"
        etiqueta="Velocidad de reproducción"
        valor={String(velocidad)}
        onCambio={(v) => p.onVelocidad(Number(v))}
        opciones={VELOCIDADES.map((v) => ({ valor: String(v), etiqueta: `${v}×` }))}
      />
      <Pista texto="Mostrar todas las fijaciones" lado="top">
        <button type="button" className="icon-btn" aria-label="Mostrar todas las fijaciones" onClick={() => { onReproducir(false); onTiempo(duracion) }}>
          <RotateCcw size={15} aria-hidden />
        </button>
      </Pista>
    </div>
  )
}

const VELOCIDADES = [0.25, 0.5, 1, 2, 4]
