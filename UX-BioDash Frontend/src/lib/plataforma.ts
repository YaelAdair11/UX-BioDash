/**
 * Detección del sistema operativo para mostrar los atajos de teclado con los nombres correctos.
 * En la app de escritorio usa lo que expone el preload; en la web, el agente de usuario.
 */
export const esMac: boolean =
  window.uxbiodash?.plataforma === 'darwin' ||
  /Mac|iPhone|iPad/i.test(navigator.platform || navigator.userAgent)

/** Nombre de la tecla para eliminar: en los teclados de Mac no existe "Supr" (se usa ⌫ / delete). */
export const TECLA_ELIMINAR = esMac ? '⌫' : 'Supr'
