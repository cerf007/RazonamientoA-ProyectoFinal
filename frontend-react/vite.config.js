import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// https://vitejs.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      '/api': {
        // Puerto 8080 es el default de OpenXava con AppServer.run()
        // El contexto debe coincidir con el <finalName> en pom.xml
        target: 'http://localhost:8080/Razonamiento',
        changeOrigin: true,
        secure: false,
      }
    }
  }
})
