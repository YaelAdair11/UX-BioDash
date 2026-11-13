import { Link } from 'react-router-dom'
import './PhaseSwitcher.css'

/** Selector de fase del espacio de trabajo: 1. Editor de AOI → 2. Dashboard analítico. */
export function PhaseSwitcher({ proyectoId, fase }: { proyectoId: number; fase: 'editor' | 'dashboard' }) {
  return (
    <nav className="phases" aria-label="Fase del espacio de trabajo">
      <Link to={`/proyectos/${proyectoId}/editor`} className={fase === 'editor' ? 'on' : ''} aria-current={fase === 'editor' ? 'page' : undefined}>
        1. Editor de AOI
      </Link>
      <Link to={`/proyectos/${proyectoId}/dashboard`} className={fase === 'dashboard' ? 'on' : ''} aria-current={fase === 'dashboard' ? 'page' : undefined}>
        2. Dashboard analítico <small>Heatmap / Scanpath</small>
      </Link>
    </nav>
  )
}
