/* Stanje uvodne animacije (komponente/UvodnaAnimacija.tsx) za tiste, ki
   morajo počakati nanjo: maskota in oglas Premium se med uvodom ne smeta
   pokazati (DESIGN.md 5e), urnik maskote pa začne šteti šele po koncu.

   Uvod je enkrat na sejo zavihka (`sessionStorage`) in samo ob prvem
   nalaganju aplikacije, ne ob premiku med stranmi. Odločitev pade enkrat na
   nalaganje, ob prvem branju — prej, kot se karkoli izriše, zato je ne vidi
   nihče drug kot »še ni odločeno«. */
import { useSyncExternalStore } from 'react'

export const KLJUC_UVODA = 'turnirko-uvod-predvajan'

type Stanje = 'neodloceno' | 'tece' | 'koncano'

let stanje: Stanje = 'neodloceno'
const poslusalci = new Set<() => void>()

function odloci(): boolean {
  if (typeof window === 'undefined') return false
  /* Predogled brez nove seje (kot `?maskota` in `?oglas`). */
  if (new URLSearchParams(window.location.search).has('uvod')) return true
  try {
    if (sessionStorage.getItem(KLJUC_UVODA) === '1') return false
  } catch {
    /* Brez shrambe (zasebno okno v nekaterih brskalnikih) se uvod predvaja ob
       vsakem nalaganju - ključa si ne more zapomniti. */
  }
  /* React Router v zgodovino zapiše zaporedno številko vnosa: 0 je prvo
     nalaganje zavihka, večja pomeni, da je gledalec sem prišel z druge strani
     aplikacije (npr. s strani za tisk, ki je zunaj skupne postavitve). */
  const idx = (window.history.state as { idx?: number } | null)?.idx ?? 0
  return idx === 0
}

function preberi(): Stanje {
  if (stanje === 'neodloceno') stanje = odloci() ? 'tece' : 'koncano'
  return stanje
}

function obvesti() {
  poslusalci.forEach((poslusalec) => poslusalec())
}

/* Ali se uvod ob tem nalaganju predvaja (ali se je že). */
export function smeUvod(): boolean {
  return preberi() === 'tece'
}

export function zacniUvod() {
  if (stanje === 'tece') return
  stanje = 'tece'
  obvesti()
}

export function koncajUvod() {
  if (stanje === 'koncano') return
  stanje = 'koncano'
  obvesti()
}

function narocise(poslusalec: () => void) {
  poslusalci.add(poslusalec)
  return () => {
    poslusalci.delete(poslusalec)
  }
}

/* true, ko uvoda ni ali je končan. */
export function useUvodKoncan(): boolean {
  return useSyncExternalStore(
    narocise,
    () => preberi() === 'koncano',
    () => true,
  )
}
