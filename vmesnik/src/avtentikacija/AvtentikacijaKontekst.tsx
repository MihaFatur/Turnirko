/* Avtentikacija: administrator (sodnik) ali igralec.

   Aplikacija je uporabna tudi brez prijave (gost, samo branje). Ob prijavi
   se poverilnice zakodirajo za HTTP Basic in shranijo v sessionStorage, da
   prijava preživi osvežitev strani (do zaprtja zavihka). Ob zagonu se
   morebitne shranjene poverilnice preverijo prek /auth/me.

   Igralec se prijavi z e-pošto. Dokler naslova ne potrdi s kodo in dokler
   račun ni povezan z zapisom igralca (samodejno ali admin), je `status` CAKA
   in `idIgralec` prazen — takrat še nima dostopa do svojega profila. */
import { createContext, useContext, useEffect, useState, type ReactNode } from 'react'
import { useQueryClient } from '@tanstack/react-query'

import { nastaviPoverilnice } from '../api/odjemalec'
import { authApi } from '../api/zahteve'
import type { UporabnikDto } from '../api/tipi'

const KLJUC_SHRAMBE = 'turnirko-poverilnice'

interface Avtentikacija {
  uporabnik: UporabnikDto | null
  jeAdmin: boolean
  /* Prijavljen organizator s potrjenim računom. */
  jeOrganizator: boolean
  /* Prijavljen igralec s potrjenim in povezanim računom. */
  jeIgralec: boolean
  /* Ali ima prijavljeni igralec Premium (zasebna statistika, spremljanje
     lig) - brez tega je (tudi lastniku) na voljo enako kot gostu. */
  jePremium: boolean
  /* Sme ustvarjati turnirje in lige (administrator ali organizator). */
  smeUstvarjati: boolean
  /* Id igralca, čigar profil je "moj"; null za admina in nepotrjene račune. */
  mojIdIgralec: number | null
  /* Ali prijavljeni sme urejati turnir/ligo z danim lastništvom: administrator
     vse, organizator svoje (idLastnik) ali od svojega kluba (idKlubLastnik).
     Streznik je zadnja obramba - to le skrije dejanja, ki bi bila zavrnjena. */
  smemUrejati: (idLastnik: number | null, idKlubLastnik: number | null) => boolean
  /* Med začetnim preverjanjem shranjenih poverilnic. */
  nalaganje: boolean
  /* Vrne profil, da okno za prijavo ve, ali naslov še čaka na kodo. */
  prijava: (uporabniskoIme: string, geslo: string) => Promise<UporabnikDto>
  /* Ponovno prebere profil prijavljenega (npr. po vpisu kode). */
  osvezi: () => Promise<void>
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

  async function prijava(uporabniskoIme: string, geslo: string): Promise<UporabnikDto> {
    const osnova = vBase64(`${uporabniskoIme}:${geslo}`)
    nastaviPoverilnice(osnova)
    try {
      const profil = await authApi.jaz()
      nastaviUporabnika(profil)
      sessionStorage.setItem(KLJUC_SHRAMBE, osnova)
      // po prijavi osveži vse poglede (nekateri prikažejo dodatne možnosti)
      odjemalec.invalidateQueries()
      return profil
    } catch (napaka) {
      nastaviPoverilnice(null)
      sessionStorage.removeItem(KLJUC_SHRAMBE)
      throw napaka
    }
  }

  /* Po vpisu kode se stanje računa spremeni (naslov potrjen, morda že
     povezan) - profil se prebere znova, da meni in "Moj profil" to vidita. */
  async function osvezi() {
    if (!sessionStorage.getItem(KLJUC_SHRAMBE)) return
    try {
      const profil = await authApi.jaz()
      nastaviUporabnika(profil)
      odjemalec.invalidateQueries()
    } catch {
      /* poverilnice ne veljajo več - stanje ostane, odjava jo počisti */
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

  const jeAdmin = uporabnik?.vloga === 'ADMIN'
  const jeOrganizator = uporabnik?.vloga === 'ORGANIZATOR' && uporabnik.status === 'POTRJEN'
  const jePremium = uporabnik?.paket === 'PREMIUM' && uporabnik.paketAktiven

  /* Ali sme prijavljeni urejati turnir/ligo z danim lastništvom. */
  function smemUrejati(idLastnik: number | null, idKlubLastnik: number | null): boolean {
    if (jeAdmin) return true
    if (!jeOrganizator || !uporabnik) return false
    if (idLastnik !== null && idLastnik === uporabnik.id) return true
    if (idKlubLastnik !== null && uporabnik.idKlub !== null && idKlubLastnik === uporabnik.idKlub) {
      return true
    }
    return false
  }

  const vrednost: Avtentikacija = {
    uporabnik,
    jeAdmin,
    jeOrganizator,
    jeIgralec,
    jePremium,
    smeUstvarjati: jeAdmin || jeOrganizator,
    mojIdIgralec: jeIgralec ? uporabnik!.idIgralec : null,
    smemUrejati,
    nalaganje,
    prijava,
    osvezi,
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
