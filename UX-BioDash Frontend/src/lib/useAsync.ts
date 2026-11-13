import { useCallback, useEffect, useState } from 'react'
import { toErrorApi } from '../api/client'
import type { ErrorApi } from '../api/types'

/** Carga asíncrona con estados de carga/error y recarga manual. */
export function useAsync<T>(fn: () => Promise<T>, deps: unknown[]) {
  const [data, setData] = useState<T | null>(null)
  const [error, setError] = useState<ErrorApi | null>(null)
  const [cargando, setCargando] = useState(true)
  const [version, setVersion] = useState(0)

  // Cuando cambian las dependencias se marca como "cargando" durante el render, antes de lanzar la petición
  const clave = JSON.stringify([...deps, version])
  const [clavePrevia, setClavePrevia] = useState(clave)
  if (clave !== clavePrevia) {
    setClavePrevia(clave)
    setCargando(true)
    setError(null)
  }

  useEffect(() => {
    let vivo = true
    fn()
      .then((d) => vivo && setData(d))
      .catch((e) => vivo && setError(toErrorApi(e)))
      .finally(() => vivo && setCargando(false))
    return () => {
      vivo = false
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [...deps, version])

  const recargar = useCallback(() => setVersion((v) => v + 1), [])
  return { data, setData, error, cargando, recargar }
}
