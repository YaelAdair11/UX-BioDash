import { Link, NavLink } from 'react-router-dom'
import { Search } from 'lucide-react'
import { Input, InputGroup } from '@ui-sdk/react/input'
import { ThemeMenu } from './ThemeMenu'
import './AppHeader.css'

export function Logo() {
  return (
    <Link to="/" className="logo" aria-label="UX-BioDash, ir a proyectos">
      <img className="logo-mark" src="/logo.png" alt="" width={32} height={32} />
      <span className="logo-text">UX-BioDash</span>
    </Link>
  )
}

interface Props {
  busqueda?: string
  onBuscar?: (v: string) => void
}

export function AppHeader({ busqueda, onBuscar }: Props) {
  return (
    <header className="app-header">
      <Logo />
      <div className="workspace">
        <span className="workspace-label">Espacio de trabajo</span>
        <span className="workspace-name">Laboratorio de Usabilidad FEI - UV</span>
      </div>
      {onBuscar && (
        <InputGroup className="search">
          <Search size={15} aria-hidden />
          <Input
            type="search"
            aria-label="Buscar proyectos"
            value={busqueda ?? ''}
            onValueChange={onBuscar}
            placeholder="Buscar proyecto, dispositivo o descripción…"
          />
        </InputGroup>
      )}
      <nav className="app-nav">
        <NavLink to="/" end>Proyectos</NavLink>
      </nav>
      <ThemeMenu />
      <span className="avatar" title="Evaluador">YG</span>
    </header>
  )
}
