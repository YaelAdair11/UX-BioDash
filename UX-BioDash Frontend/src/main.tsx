// Primero, para que el CSS de ui-sdk preceda al de App y sus componentes en el bundle
import '@ui-sdk/core/web.css'
import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { App } from './App'
import { aplicarTema, leerTema } from './lib/theme'
import './styles/global.css'
import './styles/themes.css'
import './styles/ui-sdk.css'

// Antes del primer render, para no mostrar un instante el tema equivocado.
aplicarTema(leerTema())

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <App />
  </StrictMode>,
)
