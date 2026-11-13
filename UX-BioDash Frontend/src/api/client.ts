import axios, { AxiosError } from 'axios'
import type { ErrorApi } from './types'

/**
 * Cliente HTTP único para hablar con el backend REST.
 * En desarrollo las rutas /api se reenvían al backend mediante el proxy de Vite.
 * En producción se puede definir VITE_API_URL con la URL absoluta del backend.
 */
export const http = axios.create({
  baseURL: import.meta.env.VITE_API_URL ?? '/api',
  timeout: 60_000,
})

export const USE_MOCK = (import.meta.env.VITE_USE_MOCK ?? 'true') !== 'false'

/** Convierte cualquier error de red/servidor en un mensaje claro para el evaluador. */
export function toErrorApi(err: unknown): ErrorApi {
  if (err instanceof AxiosError) {
    const data = err.response?.data as Partial<ErrorApi> | undefined
    if (err.response) {
      return {
        status: err.response.status,
        mensaje: data?.mensaje ?? `El servidor respondió con el código ${err.response.status}.`,
        detalles: data?.detalles,
      }
    }
    return {
      status: 0,
      mensaje: 'No se pudo conectar con el servidor de UX-BioDash. Verifica que el backend esté en ejecución.',
    }
  }
  if (err && typeof err === 'object' && 'mensaje' in err) return err as ErrorApi
  return { status: 0, mensaje: err instanceof Error ? err.message : 'Error desconocido.' }
}
