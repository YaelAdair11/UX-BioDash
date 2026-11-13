# AGENTS.md — UX-BioDash Frontend

Notas para agentes de código que trabajen en este repositorio.

## Proyecto

- React 19 + TypeScript + Vite, empaquetado también como app de escritorio con Electron (`electron/`).
- Gestor de paquetes: **pnpm** (no uses npm ni yarn). Comandos: `pnpm dev`, `pnpm build`, `pnpm lint`, `pnpm electron:dev`.
- Contrato con el backend: `docs/API_CONTRACT.md`. Interfaz y comentarios en español.

## ui-sdk

Antes de usar ui-sdk (`@ui-sdk/react`, `@ui-sdk/core`):

1. Sigue la skill **ui-sdk** si está instalada.
2. Lee `node_modules/@ui-sdk/react/docs/llms.txt`.
3. Para construir un componente nuevo, busca además por encabezado en `node_modules/@ui-sdk/react/docs/core.md` y lee `rendering.md`, que está en la misma carpeta.

Los estilos base se importan una sola vez en `src/main.tsx` (`import "@ui-sdk/core/web.css"`), antes de los estilos propios.
