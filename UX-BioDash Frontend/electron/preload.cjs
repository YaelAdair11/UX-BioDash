/**
 * Preload de Electron (contexto aislado, sandbox activo).
 * Expone al frontend solo información de solo lectura; no da acceso a Node ni al sistema de archivos.
 */
const { contextBridge } = require('electron')

contextBridge.exposeInMainWorld('uxbiodash', {
  esEscritorio: true,
  plataforma: process.platform,
  versiones: {
    electron: process.versions.electron,
    chrome: process.versions.chrome,
    node: process.versions.node,
  },
})
