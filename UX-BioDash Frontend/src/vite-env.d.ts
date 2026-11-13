/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_USE_MOCK?: string
  readonly VITE_API_URL?: string
}
interface ImportMeta {
  readonly env: ImportMetaEnv
}

/** API de solo lectura expuesta por electron/preload.cjs (solo existe en la app de escritorio). */
interface UxBioDashEscritorio {
  readonly esEscritorio: true
  readonly plataforma: string
  readonly versiones: { readonly electron: string; readonly chrome: string; readonly node: string }
}
interface Window {
  readonly uxbiodash?: UxBioDashEscritorio
}
