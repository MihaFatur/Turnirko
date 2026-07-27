/* Vstopna tocka: React + usmerjevalnik + TanStack Query.
   QueryClient skrbi za predpomnjenje in osvezevanje podatkov z API-ja. */
import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'

import { App } from './App'
import { AvtentikacijaPonudnik } from './avtentikacija/AvtentikacijaKontekst'
import './slog.css'

const odjemalecPoizvedb = new QueryClient({
  defaultOptions: {
    queries: {
      /* Napako pokazemo takoj, namesto da bi vmesnik dolgo "razmisljal". */
      retry: 1,
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
