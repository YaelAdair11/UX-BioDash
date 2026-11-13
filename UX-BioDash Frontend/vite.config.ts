import { defineConfig, loadEnv } from 'vite'
import react from '@vitejs/plugin-react'

// El proxy reenvía /api al backend Spring Boot durante el desarrollo,
// evitando problemas de CORS. Cambia VITE_BACKEND_URL en .env.local si el puerto difiere.
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  return {
    plugins: [react()],
    server: {
      port: 5173,
      proxy: {
        '/api': {
          target: env.VITE_BACKEND_URL || 'http://localhost:8080',
          changeOrigin: true,
        },
      },
    },
  }
})
