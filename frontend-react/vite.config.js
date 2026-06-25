import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// https://vitejs.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      '/api': {
        // Ajustá el puerto (8080) y el nombre exacto de tu app en Tomcat si varían
        target: 'http://localhost/RazonamientoA-ProyectoFinal', /*Aqui adaptar al puerto de la DB*/
        changeOrigin: true,
        secure: false,
      }
    }
  }
})
