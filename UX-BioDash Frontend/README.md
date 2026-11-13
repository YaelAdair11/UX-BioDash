# UX-BioDash — Frontend

Cliente de UX-BioDash (web y aplicación de escritorio): carga de datasets de eye tracking, editor de Áreas de Interés (AOI) sobre Canvas y dashboard con mapas de calor, scanpaths, distribución de fijaciones y métricas por AOI y por participante.

Stack: **React 19 + TypeScript 6 + Vite 8**, **Electron** para escritorio, React Router, Axios, Canvas 2D (sin librerías de gráficos), íconos `lucide-react`. Gestor de paquetes: **pnpm**.

## Inicio rápido

```bash
pnpm install
pnpm dev           # versión web en http://localhost:5173
```

`pnpm-workspace.yaml` autoriza explícitamente los scripts de instalación que se necesitan (`esbuild` y `electron`, que descarga su binario); pnpm bloquea los demás por seguridad.

## Aplicación de escritorio (Electron)

| Comando | Qué hace |
|---|---|
| `pnpm electron:dev` | Vite en el puerto 5180 + ventana de Electron con recarga en caliente y DevTools |
| `pnpm electron:start` | Compila y abre la app como en producción |
| `pnpm electron:pack` | Genera la app sin instalador en `release/win-unpacked/` |
| `pnpm electron:dist` | Genera el instalador (NSIS en Windows, DMG en macOS, AppImage en Linux) en `release/` |

- `electron/main.mjs` sirve `dist/` con el protocolo propio `app://` y reenvía `/api/*` al backend, así que el frontend usa las mismas rutas que en la web y no hay problemas de CORS. URL del backend: variable `UXBIODASH_BACKEND_URL` (por defecto `http://localhost:8080`).
- `electron/preload.cjs` expone solo `window.uxbiodash` (plataforma y versiones, de solo lectura). La ventana usa `contextIsolation`, `sandbox` y sin `nodeIntegration`; los enlaces externos se abren en el navegador del sistema.

Por defecto arranca en **modo simulado** (`VITE_USE_MOCK=true` en `.env.local`): los datos viven en memoria en el navegador y se reinician al recargar. Sirve para trabajar sin el backend.

Para conectar con el backend Spring Boot:

```bash
# .env.local
VITE_USE_MOCK=false
VITE_BACKEND_URL=http://localhost:8080   # el proxy de Vite reenvía /api a esta URL
```

Otros comandos: `pnpm build` (compilación de producción en `dist/`), `pnpm lint`.

## Pantallas

| Ruta | Pantalla |
|---|---|
| `/` | Estudios y proyectos: KPIs, filtros por estado/equipo, orden, vista cuadrícula/lista, miniaturas con mapa de calor |
| `/proyectos/nuevo` | Crear proyecto + subir CSV/TSV y captura PNG/JPG, con validación y vista previa de columnas |
| `/proyectos/:id/ingesta` | Subir o reemplazar el dataset de un proyecto existente |
| `/proyectos/:id/editor` | Editor de Áreas de Interés (fase 1): dibujo de AOIs rectangulares y poligonales, edición de vértices, cuadrícula con ajuste, herramienta mano, fijaciones de referencia, coordenadas del cursor, color, bloqueo y visibilidad por AOI |
| `/proyectos/:id/dashboard` | Dashboard analítico (wireframe "Dashboard") |

Dashboard: mapa de calor (opacidad y radio gaussiano ajustables), scanpath numerado con flechas, distribución de fijaciones, filtro por participante, línea de tiempo con reproducción, editor de AOIs rectangulares (dibujar, mover, redimensionar, renombrar, ocultar, eliminar), tabla de métricas (TTFF, dwell time, conteo, duración media, revisitas) por AOI y por participante, tarjetas de ayuda "?" junto a cada vista y métrica, y exportación de CSV + PNG.

Editor de AOIs (`/proyectos/:id/editor`, fase 1): pantalla de trabajo para definir las áreas de interés antes del análisis. Permite dibujar AOIs rectangulares o poligonales, editar sus vértices, activar una cuadrícula con ajuste y la herramienta mano para desplazar la vista, marcar fijaciones de referencia y ver las coordenadas del cursor. Cada AOI tiene color propio y puede bloquearse u ocultarse.

Atajos: `V` seleccionar · `R` dibujar AOI · `P` dibujar polígono (Enter o doble clic para cerrar, Retroceso quita el último vértice) · `H` mano · `G` cuadrícula · `Supr` eliminar AOI · `Espacio` reproducir · `Esc` cancelar.

## Estructura

```
src/
  api/        types.ts (modelo compartido), client.ts (Axios), services.ts (endpoints), mock.ts (backend simulado)
  lib/        tabular.ts (lectura/validación CSV-TSV), metrics.ts (métricas AOI, I-DT), ayuda.ts (textos de interpretación)
  viz/        render.ts (dibujo Canvas), StimulusStage.tsx (lienzo + interacción de AOIs)
  dashboard/  Inspector, Timeline, MetricsPanel
  pages/      ProjectsPage, IngestPage, DashboardPage
public/mock/  Estímulos de ejemplo para el modo simulado
docs/         API_CONTRACT.md — contrato REST que espera este frontend
```

## Integración con el backend

El contrato completo está en [docs/API_CONTRACT.md](docs/API_CONTRACT.md). Cubre proyectos, ingesta (`POST /api/datasets`), fijaciones, AOIs y métricas (`GET /api/metricas/{proyectoId}`), siguiendo el flujo de la documentación de arquitectura. La normalización, la detección de fijaciones y el cálculo de métricas son responsabilidad del servidor; el frontend solo las replica en modo simulado.
