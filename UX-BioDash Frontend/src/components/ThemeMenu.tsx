import { Palette } from 'lucide-react'
import {
  Menu, MenuContent, MenuGroupLabel, MenuItemIndicator, MenuRadioGroup, MenuRadioItem, MenuTrigger,
} from '@ui-sdk/react/menu'
import { TEMAS, useTema } from '../lib/theme'
import './ThemeMenu.css'

/** Menú de ui-sdk para elegir el tema de la aplicación. La elección se recuerda en el navegador. */
export function ThemeMenu() {
  const [tema, setTema] = useTema()

  return (
    <Menu>
      <MenuTrigger className="icon-btn theme-trigger" aria-label="Tema de la aplicación">
        <Palette size={17} aria-hidden />
      </MenuTrigger>
      <MenuContent align="end" className="theme-pop">
        <MenuRadioGroup value={tema} onValueChange={(v) => setTema(v as typeof tema)}>
          <MenuGroupLabel className="theme-title">Tema</MenuGroupLabel>
          {TEMAS.map((t) => (
            <MenuRadioItem key={t.id} value={t.id} label={t.nombre} className="theme-opt">
              <span className="theme-sample" aria-hidden>
                {t.muestra.map((c, i) => <i key={i} style={{ background: c }} />)}
              </span>
              <span className="theme-text">
                <strong>{t.nombre}</strong>
                <small>{t.descripcion}</small>
              </span>
              <MenuItemIndicator className="theme-check" />
            </MenuRadioItem>
          ))}
        </MenuRadioGroup>
      </MenuContent>
    </Menu>
  )
}
