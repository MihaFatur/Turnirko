/* Razvojni streznik Vite na vratih 5173.
   Klici na /api se posredujejo zaledju na vratih 8080, zato v razvoju
   ni tezav s CORS in vmesnik uporablja relativne poti. */
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': 'http://localhost:8080',
    },
  },
})
