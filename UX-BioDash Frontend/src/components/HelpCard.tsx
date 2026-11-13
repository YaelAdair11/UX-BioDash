import { HelpCircle, X } from 'lucide-react'
import { Popover, PopoverClose, PopoverContent, PopoverTitle, PopoverTrigger } from '@ui-sdk/react/popover'
import { AYUDA } from '../lib/ayuda'
import './HelpCard.css'

/** Botón "?" que abre una tarjeta breve de interpretación (Popover de ui-sdk) junto a una gráfica o métrica. */
export function HelpCard({ tema, align = 'left' }: { tema: keyof typeof AYUDA; align?: 'left' | 'right' }) {
  const a = AYUDA[tema]
  return (
    <Popover>
      <PopoverTrigger className="help-trigger" aria-label={`¿Qué significa ${a.titulo}?`} onClick={(e) => e.stopPropagation()}>
        <HelpCircle size={14} aria-hidden />
      </PopoverTrigger>
      <PopoverContent className="help-pop" side="bottom" align={align === 'right' ? 'end' : 'start'} sideOffset={8} collisionPadding={12}>
        <div className="help-head">
          <PopoverTitle>{a.titulo}</PopoverTitle>
          <PopoverClose className="icon-btn" aria-label="Cerrar">
            <X size={14} aria-hidden />
          </PopoverClose>
        </div>
        <dl>
          <dt>Qué representa</dt>
          <dd>{a.representa}</dd>
          <dt>Qué datos utiliza</dt>
          <dd>{a.datos}</dd>
          <dt>Qué puedes observar</dt>
          <dd>{a.observar}</dd>
          <dt>Ten en cuenta</dt>
          <dd>{a.consideraciones}</dd>
        </dl>
      </PopoverContent>
    </Popover>
  )
}
