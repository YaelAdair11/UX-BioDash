import { confirm } from '@ui-sdk/react/alert-dialog'

/**
 * Sustituye a window.confirm con un Alert Dialog de ui-sdk (accesible, con foco atrapado y tematizado).
 * Requiere <OverlayHost /> montado una vez en la raíz (App.tsx). Resuelve true si se confirma.
 */
export function confirmar(titulo: string, descripcion: string, etiquetaConfirmar = 'Eliminar') {
  return confirm({ title: titulo, description: descripcion, confirmLabel: etiquetaConfirmar, cancelLabel: 'Cancelar' })
}
