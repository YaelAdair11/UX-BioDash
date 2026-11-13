import { USE_MOCK } from '../api/client'

export function Footer() {
  return (
    <footer className="app-footer">
      <span>UX-BioDash v1.0.0-BETA | Sistema de Visualización y Análisis Biométrico</span>
      {window.uxbiodash && <span className="badge" title={`Electron ${window.uxbiodash.versiones.electron}`}>Aplicación de escritorio</span>}
      <span className="hide-sm">Facultad de Estadística e Informática - Universidad Veracruzana</span>
      <span className="row" style={{ gap: 6 }}>
        <i className="status-dot" style={{ background: USE_MOCK ? 'var(--warn)' : 'var(--ok)' }} />
        {USE_MOCK ? 'Modo simulado (sin backend)' : 'API REST conectada'}
      </span>
    </footer>
  )
}
