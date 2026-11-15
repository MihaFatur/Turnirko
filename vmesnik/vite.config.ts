/* Razvojni streznik Vite na vratih 5173.
   Klici na /api se posredujejo zaledju na vratih 8080, zato v razvoju
   ni tezav s CORS in vmesnik uporablja relativne poti. */
import { defineConfig } from 'vitest/config'
import react from '@vitejs/plugin-react'

/* Testi (vitest) berejo isto nastavitev. Datumske funkcije v pomozno/ deloma
   racunajo po uri naprave, zato ima testni tek trden casovni pas: enak izid na
   razvojnem racunalniku (Ljubljana) in v CI (UTC). */
process.env.TZ = 'Europe/Ljubljana'

export default defineConfig({
  plugins: [react()],
  test: {
    include: ['src/**/*.test.{ts,tsx}'],
    // Ciste funkcije DOM-a ne rabijo; test komponente ga zahteva sam
    // (komentar @vitest-environment jsdom na vrhu datoteke).
    environment: 'node',
  },
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
