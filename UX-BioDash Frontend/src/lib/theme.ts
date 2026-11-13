import { useEffect, useState } from 'react'

/** Temas disponibles. 'sistema' sigue la preferencia clara/oscura del sistema operativo. */
export type Tema = 'sistema' | 'oscuro' | 'pizarra' | 'claro' | 'contraste'

export const TEMAS: { id: Tema; nombre: string; descripcion: string; muestra: [string, string, string] }[] = [
  { id: 'sistema', nombre: 'Sistema', descripcion: 'Claro u oscuro según tu equipo', muestra: ['#f8fafc', '#0b1220', '#1a8fe3'] },
  { id: 'oscuro', nombre: 'Oscuro', descripcion: 'Azul noche analítico (predeterminado)', muestra: ['#0b1220', '#16233a', '#1a8fe3'] },
  { id: 'pizarra', nombre: 'Pizarra', descripcion: 'Gris pizarra del editor de AOIs', muestra: ['#0f172a', '#1e293b', '#0284c7'] },
  { id: 'claro', nombre: 'Claro', descripcion: 'Fondo blanco para espacios iluminados', muestra: ['#f1f5f9', '#ffffff', '#0369a1'] },
  { id: 'contraste', nombre: 'Alto contraste', descripcion: 'Negro puro y bordes marcados', muestra: ['#000000', '#0a0a0a', '#ffd400'] },
]

const CLAVE = 'uxbiodash:tema'
const consultaOscuro = () => window.matchMedia?.('(prefers-color-scheme: dark)')

export function leerTema(): Tema {
  try {
    const t = localStorage.getItem(CLAVE) as Tema | null
    if (t && TEMAS.some((x) => x.id === t)) return t
  } catch { /* sin almacenamiento */ }
  return 'oscuro'
}

/** Tema que se pinta realmente ('sistema' se resuelve a claro u oscuro). */
function resolver(t: Tema): Exclude<Tema, 'sistema'> {
  if (t !== 'sistema') return t
  return consultaOscuro()?.matches === false ? 'claro' : 'oscuro'
}

/** Aplica el tema al <html>. Se llama antes del primer render para evitar el destello de otro tema. */
export function aplicarTema(t: Tema) {
  document.documentElement.dataset.theme = resolver(t)
}

export function useTema() {
  const [tema, setTema] = useState<Tema>(leerTema)

  useEffect(() => {
    aplicarTema(tema)
    try { localStorage.setItem(CLAVE, tema) } catch { /* sin almacenamiento */ }
    window.dispatchEvent(new CustomEvent('uxbiodash:tema', { detail: tema }))
    if (tema !== 'sistema') return
    const mq = consultaOscuro()
    const cambio = () => aplicarTema('sistema')
    mq?.addEventListener('change', cambio)
    return () => mq?.removeEventListener('change', cambio)
  }, [tema])

  // Varias instancias del menú (encabezado, editor, dashboard) se mantienen sincronizadas.
  useEffect(() => {
    const sync = (e: Event) => setTema((e as CustomEvent<Tema>).detail)
    window.addEventListener('uxbiodash:tema', sync)
    return () => window.removeEventListener('uxbiodash:tema', sync)
  }, [])

  return [tema, setTema] as const
}
