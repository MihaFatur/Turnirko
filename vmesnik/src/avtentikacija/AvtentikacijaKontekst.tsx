/* Avtentikacija: administrator (sodnik) ali igralec.

   Aplikacija je uporabna tudi brez prijave (gost, samo branje). Ob prijavi
   se poverilnice zakodirajo za HTTP Basic in shranijo v sessionStorage, da
   prijava preživi osvežitev strani (do zaprtja zavihka). Ob zagonu se
   morebitne shranjene poverilnice preverijo prek /auth/me.

   Igralec se prijavi z e-pošto. Dokler njegovega računa administrator ne
   potrdi, je `status` CAKA in `idIgralec` prazen — takrat še nima dostopa
   do svojega profila. */
import { createContext, useContext, useEffect, useState, type ReactNode } from 'react'
import { useQueryClient } from '@tanstack/react-query'

import { nastaviPoverilnice } from '../api/odjemalec'
import { authApi } from '../api/zahteve'
import type { UporabnikDto } from '../api/tipi'

const KLJUC_SHRAMBE = 'turnirko-poverilnice'

interface Avtentikacija {
  uporabnik: UporabnikDto | null
  jeAdmin: boolean
  /* Prijavljen igralec s potrjenim in povezanim računom. */
  jeIgralec: boolean
  /* Id igralca, čigar profil je "moj"; null za admina in nepotrjene račune. */
  mojIdIgralec: number | null
  /* Med začetnim preverjanjem shranjenih poverilnic. */
  nalaganje: boolean
  prijava: (uporabniskoIme: string, geslo: string) => Promise<void>
  odjava: () => void
}

const Kontekst = createContext<Avtentikacija | null>(null)

/* base64 kodiranje z varno obravnavo ne-ASCII znakov (npr. šumniki v geslu). */
function vBase64(niz: string): string {
  return btoa(String.fromCharCode(...new TextEncoder().encode(niz)))
}

export function AvtentikacijaPonudnik({ children }: { children: ReactNode }) {
  const [uporabnik, nastaviUporabnika] = useState<UporabnikDto | null>(null)
  const [nalaganje, nastaviNalaganje] = useState(true)
  const odjemalec = useQueryClient()

  /* Ob zagonu preveri shranjene poverilnice. */
  useEffect(() => {
    const shranjene = sessionStorage.getItem(KLJUC_SHRAMBE)
    if (!shranjene) {
      nastaviNalaganje(false)
      return
    }
    nastaviPoverilnice(shranjene)
    authApi
      .jaz()
      .then((profil) => nastaviUporabnika(profil))
      .catch(() => {
        nastaviPoverilnice(null)
        sessionStorage.removeItem(KLJUC_SHRAMBE)
      })
      .finally(() => nastaviNalaganje(false))
  }, [])

  async function prijava(uporabniskoIme: string, geslo: string) {
    const osnova = vBase64(`${uporabniskoIme}:${geslo}`)
    nastaviPoverilnice(osnova)
    try {
      const profil = await authApi.jaz()
      nastaviUporabnika(profil)
      sessionStorage.setItem(KLJUC_SHRAMBE, osnova)
      // po prijavi osveži vse poglede (nekateri prikažejo dodatne možnosti)
      odjemalec.invalidateQueries()
    } catch (napaka) {
      nastaviPoverilnice(null)
      sessionStorage.removeItem(KLJUC_SHRAMBE)
      throw napaka
    }
  }

  function odjava() {
    nastaviPoverilnice(null)
    sessionStorage.removeItem(KLJUC_SHRAMBE)
    nastaviUporabnika(null)
    odjemalec.invalidateQueries()
  }

  const jeIgralec = uporabnik?.vloga === 'IGRALEC'
    && uporabnik.status === 'POTRJEN'
    && uporabnik.idIgralec !== null

  const vrednost: Avtentikacija = {
    uporabnik,
    jeAdmin: uporabnik?.vloga === 'ADMIN',
    jeIgralec,
    mojIdIgralec: jeIgralec ? uporabnik!.idIgralec : null,
    nalaganje,
    prijava,
    odjava,
  }

  return <Kontekst.Provider value={vrednost}>{children}</Kontekst.Provider>
}

export function useAvtentikacija(): Avtentikacija {
  const vrednost = useContext(Kontekst)
  if (!vrednost) {
    throw new Error('useAvtentikacija je uporabljen izven AvtentikacijaPonudnik.')
  }
  return vrednost
}
