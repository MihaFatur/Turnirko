/* Razvojni streznik Vite na vratih 5173.
   Klici na /api se posredujejo zaledju na vratih 8080, zato v razvoju
   ni tezav s CORS in vmesnik uporablja relativne poti. */
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],
  server: {
    // PORT: vec vzporednih Claude Code sej lahko na istem stroju pozene
    // vec razvojnih streznikov hkrati (glej .claude/launch.json, "autoPort") -
    // brez tega bi druga seja trcila ob ze zasedena vrata 5173.
    port: Number(process.env.PORT) || 5173,
    proxy: {
      '/api': `http://localhost:${Number(process.env.API_PORT) || 8080}`,
    },
  },
})
