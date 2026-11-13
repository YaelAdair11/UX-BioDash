import { http, USE_MOCK } from './client'
import * as mock from './mock'
import type {
  AreaInteres,
  AreaInteresNueva,
  Estimulo,
  Fijacion,
  MetricasProyecto,
  Proyecto,
  ProyectoNuevo,
  ResumenGlobal,
  ResumenIngesta,
  SesionEvaluacion,
} from './types'

/**
 * Capa de servicios: cada función corresponde a un endpoint REST documentado en docs/API_CONTRACT.md.
 * Si VITE_USE_MOCK=true se resuelve con datos simulados en memoria para poder trabajar sin backend.
 */

export const api = {
  async resumen(): Promise<ResumenGlobal> {
    if (USE_MOCK) return mock.resumen()
    return (await http.get<ResumenGlobal>('/proyectos/resumen')).data
  },

  async listarProyectos(): Promise<Proyecto[]> {
    if (USE_MOCK) return mock.listarProyectos()
    return (await http.get<Proyecto[]>('/proyectos')).data
  },

  async obtenerProyecto(id: number): Promise<Proyecto> {
    if (USE_MOCK) return mock.obtenerProyecto(id)
    return (await http.get<Proyecto>(`/proyectos/${id}`)).data
  },

  async crearProyecto(datos: ProyectoNuevo): Promise<Proyecto> {
    if (USE_MOCK) return mock.crearProyecto(datos)
    return (await http.post<Proyecto>('/proyectos', datos)).data
  },

  async eliminarProyecto(id: number): Promise<void> {
    if (USE_MOCK) return mock.eliminarProyecto(id)
    await http.delete(`/proyectos/${id}`)
  },

  async listarEstimulos(proyectoId: number): Promise<Estimulo[]> {
    if (USE_MOCK) return mock.listarEstimulos(proyectoId)
    return (await http.get<Estimulo[]>(`/proyectos/${proyectoId}/estimulos`)).data
  },

  async listarSesiones(proyectoId: number): Promise<SesionEvaluacion[]> {
    if (USE_MOCK) return mock.listarSesiones(proyectoId)
    return (await http.get<SesionEvaluacion[]>(`/proyectos/${proyectoId}/sesiones`)).data
  },

  /** Sube el archivo de eye tracking (CSV/TSV) y la captura del estímulo (PNG/JPG). */
  async subirDataset(
    proyectoId: number,
    archivo: File,
    imagen: File,
    onProgress?: (pct: number) => void,
  ): Promise<ResumenIngesta> {
    if (USE_MOCK) return mock.subirDataset(proyectoId, archivo, imagen, onProgress)
    const form = new FormData()
    form.append('proyectoId', String(proyectoId))
    form.append('archivo', archivo)
    form.append('imagen', imagen)
    const res = await http.post<ResumenIngesta>('/datasets', form, {
      onUploadProgress: (e) => onProgress?.(e.total ? Math.round((e.loaded / e.total) * 100) : 0),
    })
    return res.data
  },

  async listarFijaciones(estimuloId: number, sesionId?: number | null): Promise<Fijacion[]> {
    if (USE_MOCK) return mock.listarFijaciones(estimuloId, sesionId)
    return (
      await http.get<Fijacion[]>(`/estimulos/${estimuloId}/fijaciones`, {
        params: sesionId ? { sesionId } : undefined,
      })
    ).data
  },

  async listarAois(estimuloId: number): Promise<AreaInteres[]> {
    if (USE_MOCK) return mock.listarAois(estimuloId)
    return (await http.get<AreaInteres[]>(`/estimulos/${estimuloId}/aois`)).data
  },

  async crearAoi(aoi: AreaInteresNueva): Promise<AreaInteres> {
    if (USE_MOCK) return mock.crearAoi(aoi)
    return (await http.post<AreaInteres>(`/estimulos/${aoi.estimuloId}/aois`, aoi)).data
  },

  async actualizarAoi(aoi: AreaInteres): Promise<AreaInteres> {
    if (USE_MOCK) return mock.actualizarAoi(aoi)
    return (await http.put<AreaInteres>(`/aois/${aoi.id}`, aoi)).data
  },

  async eliminarAoi(id: number): Promise<void> {
    if (USE_MOCK) return mock.eliminarAoi(id)
    await http.delete(`/aois/${id}`)
  },

  async metricas(proyectoId: number, sesionId?: number | null): Promise<MetricasProyecto> {
    if (USE_MOCK) return mock.metricas(proyectoId, sesionId)
    return (
      await http.get<MetricasProyecto>(`/metricas/${proyectoId}`, {
        params: sesionId ? { sesionId } : undefined,
      })
    ).data
  },
}
