/* Vstopna tocka: React + usmerjevalnik + TanStack Query.
   QueryClient skrbi za predpomnjenje in osvezevanje podatkov z API-ja. */
import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import { QueryClient, QueryClientProvider, onlineManager } from '@tanstack/react-query'

import { App } from './App'
import { NapakaStreznika } from './api/odjemalec'
import { AvtentikacijaPonudnik } from './avtentikacija/AvtentikacijaKontekst'
/* Lokalno vgrajene pisave sistema (brez zunanjih klicev - deluje tudi brez
   interneta v dvorani; DESIGN.md nalaga druzine, ne nacina dostave).
   Bricolage Grotesque = display, Karla = telo, IBM Plex Mono = oznake. */
import '@fontsource-variable/bricolage-grotesque'
import '@fontsource-variable/karla'
import '@fontsource/ibm-plex-mono/400.css'
import '@fontsource/ibm-plex-mono/500.css'
import '@fontsource/ibm-plex-mono/600.css'
import '@fontsource/ibm-plex-mono/700.css'
import './slog.css'

/* TanStack Query ob izpadu povezave zaustavi poizvedbe (fetchStatus "paused").
   Svoje stanje povezave zgradi ob prvi narocnini, zato ga ob zagonu izrecno
   uskladimo z brskalnikom - sicer se lahko zgodi, da ostane "brez povezave",
   dogodek "online" pa ne pride nikoli (naprava je bila povezana ves cas) in
   vsaka neuspesna poizvedba obvisi v neskoncnem "Nalaganje ...". */
if (typeof navigator !== 'undefined') onlineManager.setOnline(navigator.onLine)

const odjemalecPoizvedb = new QueryClient({
  defaultOptions: {
    queries: {
      /* Ponovni poskus ima smisel samo pri motnji na poti (izpad Wi-Fi, 5xx).
         Odgovora 404 ali 403 ponavljanje ne spremeni - takoj pokazemo napako,
         namesto da bi vmesnik sekundo "razmisljal". */
      retry: (poskus, napaka) => {
        if (napaka instanceof NapakaStreznika && napaka.stanje < 500) return false
        return poskus < 1
      },
      refetchOnWindowFocus: false,
    },
  },
})

createRoot(document.getElementById('koren')!).render(
  <StrictMode>
    <QueryClientProvider client={odjemalecPoizvedb}>
      <BrowserRouter>
        <AvtentikacijaPonudnik>
          <App />
        </AvtentikacijaPonudnik>
      </BrowserRouter>
    </QueryClientProvider>
  </StrictMode>,
)
