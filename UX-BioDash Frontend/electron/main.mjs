/**
 * Proceso principal de Electron para UX-BioDash.
 *
 * - Desarrollo: carga el servidor de Vite (VITE_DEV_SERVER_URL), que ya reenvía /api al backend.
 * - Producción: sirve la carpeta dist/ mediante el protocolo propio `app://`. Ese mismo protocolo
 *   reenvía las rutas /api/* al backend Spring Boot (UXBIODASH_BACKEND_URL, por defecto
 *   http://localhost:8080), así que el frontend usa las mismas rutas relativas que en la web,
 *   BrowserRouter funciona y no hay problemas de CORS.
 */
import { app, BrowserWindow, Menu, net, protocol, shell } from 'electron'
import { fileURLToPath, pathToFileURL } from 'node:url'
import path from 'node:path'
import fs from 'node:fs'

const __dirname = path.dirname(fileURLToPath(import.meta.url))
const DEV_URL = process.env.VITE_DEV_SERVER_URL
const BACKEND_URL = (process.env.UXBIODASH_BACKEND_URL ?? 'http://localhost:8080').replace(/\/$/, '')
const DIST = path.join(__dirname, '..', 'dist')
const ESQUEMA = 'app'
const ORIGEN = `${ESQUEMA}://uxbiodash`

// Debe registrarse antes de que la app esté lista
protocol.registerSchemesAsPrivileged([
  { scheme: ESQUEMA, privileges: { standard: true, secure: true, supportFetchAPI: true, corsEnabled: true, stream: true } },
])

/** Responde a app://uxbiodash/... con archivos de dist/ o reenvía /api/* al backend. */
function registrarProtocolo() {
  protocol.handle(ESQUEMA, async (request) => {
    const url = new URL(request.url)

    if (url.pathname.startsWith('/api/')) {
      const destino = `${BACKEND_URL}${url.pathname}${url.search}`
      try {
        return await net.fetch(destino, {
          method: request.method,
          headers: request.headers,
          body: ['GET', 'HEAD'].includes(request.method) ? undefined : request.body,
          duplex: 'half',
        })
      } catch {
        return new Response(
          JSON.stringify({ status: 503, mensaje: `No se pudo conectar con el backend en ${BACKEND_URL}. Verifica que esté en ejecución.` }),
          { status: 503, headers: { 'content-type': 'application/json' } },
        )
      }
    }

    // Archivos estáticos; cualquier ruta desconocida devuelve index.html (rutas del SPA)
    const relativa = decodeURIComponent(url.pathname).replace(/^\/+/, '')
    const archivo = path.normalize(path.join(DIST, relativa))
    const dentroDeDist = archivo.startsWith(DIST)
    const existe = dentroDeDist && relativa && fs.existsSync(archivo) && fs.statSync(archivo).isFile()
    return net.fetch(pathToFileURL(existe ? archivo : path.join(DIST, 'index.html')).toString())
  })
}

function crearVentana() {
  const ventana = new BrowserWindow({
    width: 1440,
    height: 900,
    minWidth: 1100,
    minHeight: 700,
    title: 'UX-BioDash',
    icon: path.join(__dirname, 'icono.png'),
    backgroundColor: '#0b1220',
    show: false,
    autoHideMenuBar: true,
    webPreferences: {
      preload: path.join(__dirname, 'preload.cjs'),
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: true,
    },
  })

  ventana.once('ready-to-show', () => ventana.show())

  // Los enlaces externos se abren en el navegador del sistema, nunca dentro de la app
  ventana.webContents.setWindowOpenHandler(({ url }) => {
    if (/^https?:\/\//.test(url)) shell.openExternal(url)
    return { action: 'deny' }
  })
  ventana.webContents.on('will-navigate', (evento, url) => {
    const permitido = url.startsWith(ORIGEN) || (DEV_URL && url.startsWith(DEV_URL))
    if (!permitido) {
      evento.preventDefault()
      if (/^https?:\/\//.test(url)) shell.openExternal(url)
    }
  })

  if (DEV_URL) {
    ventana.loadURL(DEV_URL)
    ventana.webContents.openDevTools({ mode: 'detach' })
  } else {
    ventana.loadURL(`${ORIGEN}/`)
  }
}

// Una sola instancia: si se abre otra vez, se enfoca la ventana existente
if (!app.requestSingleInstanceLock()) {
  app.quit()
} else {
  app.on('second-instance', () => {
    const [v] = BrowserWindow.getAllWindows()
    if (v) {
      if (v.isMinimized()) v.restore()
      v.focus()
    }
  })

  app.whenReady().then(() => {
    if (!DEV_URL) Menu.setApplicationMenu(null)
    registrarProtocolo()
    crearVentana()
    app.on('activate', () => {
      if (BrowserWindow.getAllWindows().length === 0) crearVentana()
    })
  })

  app.on('window-all-closed', () => {
    if (process.platform !== 'darwin') app.quit()
  })
}
