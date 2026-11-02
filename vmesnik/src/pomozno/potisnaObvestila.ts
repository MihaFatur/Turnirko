/* Potisna obvestila v brskalniku (V60): service worker (public/sw.js), narocnina na
   Push in vpis naprave na strezniku.

   Stanje je v brskalniku (ali je ta naprava narocena), ne na strezniku: ena oseba ima
   lahko vec naprav. Zato se ob prikazu povprasa registracija workerja in njena narocnina,
   vklop pa mora steci iz klika (dovoljenje za obvestila brskalnik odobri samo ob uporabnikovem
   dejanju). Na iPhonu Push deluje le, ce je stran dodana na zacetni zaslon - brez tega
   PushManager ne obstaja in stran to pove, namesto da bi gumb tiho ne naredil nic. */
import { useCallback, useEffect, useState } from 'react'
import { useQuery } from '@tanstack/react-query'

import { opisNapake } from '../api/odjemalec'
import { potisnaApi } from '../api/zahteve'

export type StanjePotisnih = 'nalaganje' | 'nepodprto' | 'zavrnjeno' | 'izklopljeno' | 'vklopljeno'

export interface PotisnaObvestila {
  stanje: StanjePotisnih
  /* Streznik ima kljuce VAPID (sicer funkcije ni). */
  naVoljo: boolean
  zaposlen: boolean
  napaka: string | null
  vklopi: () => Promise<void>
  izklopi: () => Promise<void>
  preizkus: () => Promise<number | null>
}

function podprto(): boolean {
  return typeof window !== 'undefined' && 'serviceWorker' in navigator && 'PushManager' in window
    && 'Notification' in window
}

/* base64url -> Uint8Array za applicationServerKey. */
function izBase64Url(niz: string): Uint8Array<ArrayBuffer> {
  const polnilo = '='.repeat((4 - (niz.length % 4)) % 4)
  const bin = atob((niz + polnilo).replace(/-/g, '+').replace(/_/g, '/'))
  const izhod = new Uint8Array(new ArrayBuffer(bin.length))
  for (let i = 0; i < bin.length; i++) izhod[i] = bin.charCodeAt(i)
  return izhod
}

export function usePotisnaObvestila(): PotisnaObvestila {
  const kljuc = useQuery({
    queryKey: ['push-kljuc'],
    queryFn: potisnaApi.kljuc,
    staleTime: Infinity,
  })
  const javniKljuc = kljuc.data?.kljuc ?? ''
  const [stanje, nastaviStanje] = useState<StanjePotisnih>(podprto() ? 'nalaganje' : 'nepodprto')
  const [zaposlen, nastaviZaposlen] = useState(false)
  const [napaka, nastaviNapako] = useState<string | null>(null)

  /* Ali je ta brskalnik ze narocen (worker se registrira ob prvem vklopu). */
  useEffect(() => {
    if (!podprto()) return
    let opusceno = false
    void (async () => {
      try {
        const registracija = await navigator.serviceWorker.getRegistration('/sw.js')
        const narocnina = await registracija?.pushManager.getSubscription()
        if (opusceno) return
        if (Notification.permission === 'denied') nastaviStanje('zavrnjeno')
        else nastaviStanje(narocnina ? 'vklopljeno' : 'izklopljeno')
      } catch {
        if (!opusceno) nastaviStanje('izklopljeno')
      }
    })()
    return () => {
      opusceno = true
    }
  }, [])

  const vklopi = useCallback(async () => {
    if (!podprto() || !javniKljuc) return
    nastaviZaposlen(true)
    nastaviNapako(null)
    try {
      const dovoljenje = await Notification.requestPermission()
      if (dovoljenje !== 'granted') {
        nastaviStanje(dovoljenje === 'denied' ? 'zavrnjeno' : 'izklopljeno')
        return
      }
      await navigator.serviceWorker.register('/sw.js')
      const registracija = await navigator.serviceWorker.ready
      const narocnina = await registracija.pushManager.subscribe({
        userVisibleOnly: true,
        applicationServerKey: izBase64Url(javniKljuc),
      })
      await potisnaApi.vpisi(narocnina.toJSON() as { endpoint: string; keys: { p256dh: string; auth: string } })
      nastaviStanje('vklopljeno')
    } catch (e) {
      nastaviNapako(opisNapake(e))
    } finally {
      nastaviZaposlen(false)
    }
  }, [javniKljuc])

  const izklopi = useCallback(async () => {
    if (!podprto()) return
    nastaviZaposlen(true)
    nastaviNapako(null)
    try {
      const registracija = await navigator.serviceWorker.getRegistration('/sw.js')
      const narocnina = await registracija?.pushManager.getSubscription()
      if (narocnina) {
        await potisnaApi.izpis(narocnina.endpoint)
        await narocnina.unsubscribe()
      }
      nastaviStanje('izklopljeno')
    } catch (e) {
      nastaviNapako(opisNapake(e))
    } finally {
      nastaviZaposlen(false)
    }
  }, [])

  const preizkus = useCallback(async () => {
    nastaviNapako(null)
    try {
      return (await potisnaApi.preizkus()).poslanih
    } catch (e) {
      nastaviNapako(opisNapake(e))
      return null
    }
  }, [])

  return { stanje, naVoljo: javniKljuc !== '', zaposlen, napaka, vklopi, izklopi, preizkus }
}
