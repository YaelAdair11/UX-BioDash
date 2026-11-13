import { lazy, Suspense } from 'react'
import { BrowserRouter, Link, Route, Routes, useLocation } from 'react-router-dom'
import { OverlayHost } from '@ui-sdk/react/overlays'
import { LimiteError } from './components/LimiteError'

// Cada pantalla se carga en su propio fragmento: el bundle inicial solo trae la que se abre
const ProjectsPage = lazy(() => import('./pages/ProjectsPage').then((m) => ({ default: m.ProjectsPage })))
const IngestPage = lazy(() => import('./pages/IngestPage').then((m) => ({ default: m.IngestPage })))
const DashboardPage = lazy(() => import('./pages/DashboardPage').then((m) => ({ default: m.DashboardPage })))
const EditorPage = lazy(() => import('./pages/EditorPage').then((m) => ({ default: m.EditorPage })))

export function App() {
  return (
    <BrowserRouter>
      <LimiteRuta>
        <Suspense fallback={<div className="center-state" style={{ height: '100%' }}><div className="spinner" /></div>}>
          <Routes>
            <Route path="/" element={<ProjectsPage />} />
            <Route path="/proyectos/nuevo" element={<IngestPage />} />
            <Route path="/proyectos/:id/ingesta" element={<IngestPage />} />
            <Route path="/proyectos/:id/editor" element={<EditorPage />} />
            <Route path="/proyectos/:id/dashboard" element={<DashboardPage />} />
            <Route
              path="*"
              element={
                <div className="center-state" style={{ height: '100%' }}>
                  <h2>Página no encontrada</h2>
                  <Link to="/" className="btn">Ir a proyectos</Link>
                </div>
              }
            />
          </Routes>
        </Suspense>
      </LimiteRuta>
      {/* Muestra los diálogos de confirmar() de ui-sdk */}
      <OverlayHost />
    </BrowserRouter>
  )
}

/** Reinicia el límite de error al cambiar de ruta. */
function LimiteRuta({ children }: { children: React.ReactNode }) {
  const { pathname } = useLocation()
  return <LimiteError clave={pathname}>{children}</LimiteError>
}
