/* Avtentikacija: administrator (sodnik) ali igralec.

   Aplikacija je uporabna tudi brez prijave (gost, samo branje). Ob prijavi
   se poverilnice zakodirajo za HTTP Basic in shranijo v sessionStorage, da
   prijava preživi osvežitev strani (do zaprtja zavihka). Ob zagonu se
   morebitne shranjene poverilnice preverijo prek /auth/me.

   Igralec se prijavi z e-pošto. Dokler naslova ne potrdi s kodo in dokler
   račun ni povezan z zapisom igralca (samodejno ali admin), je `status` CAKA
   in `idIgralec` prazen — takrat še nima dostopa do svojega profila. */
import { createContext, useContext, useEffect, useRef, useState, type ReactNode } from 'react'
import { useQueryClient } from '@tanstack/react-query'

import { nastaviPoverilnice, obNeveljavnihPoverilnicah } from '../api/odjemalec'
import { authApi } from '../api/zahteve'
import type { UporabnikDto } from '../api/tipi'
import { shrambaVrste } from '../pomozno/vrstaFotografij'

const KLJUC_SHRAMBE = 'turnirko-poverilnice'

/* Tekmovanje, o katerem se sprašuje smemUrejati: dodeljen dostop (V62) je vezan
   na id turnirja oz. lige in ga iz lastništva ni mogoče prebrati. */
export interface TekmovanjeDostopa {
  vrsta: 'liga' | 'turnir'
  id: number
}

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
  /* Ugodnosti, ki jih imata paket Navijač in Igralec Premium skupaj (V58): neomejeno
     spremljanih igralcev, koledar v telefonu, napoved dvoboja za kateri koli par. Strežnik
     je zadnja beseda (NarocninaStoritev.imaSirokoSpremljanje); admin ima vse. */
  imaSirokoSpremljanje: boolean
  /* Sme ustvarjati turnirje in lige (administrator ali organizator). */
  smeUstvarjati: boolean
  /* Id igralca, čigar profil je "moj"; null za admina in nepotrjene račune. */
  mojIdIgralec: number | null
  /* Ali prijavljeni sme urejati turnir/ligo z danim lastništvom: administrator
     vse, organizator svoje (idLastnik), od svojega kluba (idKlubLastnik) ali
     tisto, do katerega mu je admin dodelil dostop (tretji parameter pove, katero
     tekmovanje je to; brez njega dodelitev ne šteje). Strežnik je zadnja
     obramba - to le skrije dejanja, ki bi bila zavrnjena. */
  smemUrejati: (
    idLastnik: number | null,
    idKlubLastnik: number | null,
    tekmovanje?: TekmovanjeDostopa,
  ) => boolean
  /* Med začetnim preverjanjem shranjenih poverilnic. */
  nalaganje: boolean
  /* Strežnik je sredi dela zavrnil poverilnice prijavljenega (geslo spremenjeno,
     račun izklopljen): uporabnik je spet gost, postavitev mu to pove. Ugasne ga
     nova prijava ali `zapriObvestiloPoteka`. */
  prijavaPotekla: boolean
  zapriObvestiloPoteka: () => void
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
  const [prijavaPotekla, nastaviPrijavaPotekla] = useState(false)
  /* Ali je v tem zavihku kdo prijavljen (ref, ker ga bere poslušalec iz
     odjemalca in ne izris). */
  const prijavljen = useRef(false)
  /* Med poskusom prijave je 401 napačno geslo, ne potek prijave. */
  const poskusPrijave = useRef(false)

  function nastaviPrijavljenega(profil: UporabnikDto) {
    prijavljen.current = true
    nastaviUporabnika(profil)
  }

  /* Osrednja obravnava 401 (glej api/odjemalec.ts): poverilnice prijavljenega
     ne veljajo več. Odjemalec jih je že zavrgel; tu se počisti še shramba in
     stanje, poizvedbe pa se osvežijo kot gost - javne strani se tako vrnejo
     same, namesto da bi vsaka kazala »nimaš pravic«.

     Registriran je PRED preverbo ob zagonu (vrstni red učinkov). Zavrnjen
     poskus prijave in zavrnjene shranjene poverilnice ob zagonu NISTA potek
     prijave: takrat še nihče ni prijavljen in sporočilo pokaže klicatelj. */
  useEffect(() => {
    obNeveljavnihPoverilnicah(() => {
      if (!prijavljen.current || poskusPrijave.current) return
      prijavljen.current = false
      sessionStorage.removeItem(KLJUC_SHRAMBE)
      nastaviUporabnika(null)
      nastaviPrijavaPotekla(true)
      odjemalec.invalidateQueries()
    })
    return () => obNeveljavnihPoverilnicah(null)
  }, [odjemalec])

  /* Neposlane fotografije listkov, starejše od 24 ur, se izbrišejo ob vsakem
     odprtju aplikacije, ne šele na strani branja (seja brez odjave, F5.2). */
  useEffect(() => {
    void shrambaVrste.pocistiPretekle()
  }, [])

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
      .then((profil) => nastaviPrijavljenega(profil))
      .catch(() => {
        nastaviPoverilnice(null)
        sessionStorage.removeItem(KLJUC_SHRAMBE)
      })
      .finally(() => nastaviNalaganje(false))
  }, [])

  async function prijava(uporabniskoIme: string, geslo: string): Promise<UporabnikDto> {
    const osnova = vBase64(`${uporabniskoIme}:${geslo}`)
    nastaviPoverilnice(osnova)
    poskusPrijave.current = true
    try {
      const profil = await authApi.jaz()
      nastaviPrijavljenega(profil)
      nastaviPrijavaPotekla(false)
      sessionStorage.setItem(KLJUC_SHRAMBE, osnova)
      // po prijavi osveži vse poglede (nekateri prikažejo dodatne možnosti)
      odjemalec.invalidateQueries()
      return profil
    } catch (napaka) {
      nastaviPoverilnice(null)
      sessionStorage.removeItem(KLJUC_SHRAMBE)
      throw napaka
    } finally {
      poskusPrijave.current = false
    }
  }

  /* Po vpisu kode se stanje računa spremeni (naslov potrjen, morda že
     povezan) - profil se prebere znova, da meni in "Moj profil" to vidita. */
  async function osvezi() {
    if (!sessionStorage.getItem(KLJUC_SHRAMBE)) return
    try {
      const profil = await authApi.jaz()
      nastaviPrijavljenega(profil)
      odjemalec.invalidateQueries()
    } catch {
      /* Zavrnjene poverilnice (401) je že obravnaval poslušalec zgoraj; pri
         drugi napaki (izpad povezave) stanje ostane, kot je. */
    }
  }

  function odjava() {
    prijavljen.current = false
    nastaviPoverilnice(null)
    sessionStorage.removeItem(KLJUC_SHRAMBE)
    // neposlane fotografije listkov nosijo imena igralcev: ne ostanejo v brskalniku
    void shrambaVrste.izbrisiVse()
    nastaviUporabnika(null)
    nastaviPrijavaPotekla(false)
    odjemalec.invalidateQueries()
  }

  const jeIgralec = uporabnik?.vloga === 'IGRALEC'
    && uporabnik.status === 'POTRJEN'
    && uporabnik.idIgralec !== null

  const jeAdmin = uporabnik?.vloga === 'ADMIN'
  const jeOrganizator = uporabnik?.vloga === 'ORGANIZATOR' && uporabnik.status === 'POTRJEN'
  const jePremium = uporabnik?.paket === 'PREMIUM' && uporabnik.paketAktiven
  const imaSirokoSpremljanje = jeAdmin
    || ((uporabnik?.paket === 'PREMIUM' || uporabnik?.paket === 'NAVIJAC') && !!uporabnik.paketAktiven)

  /* Ali sme prijavljeni urejati turnir/ligo z danim lastništvom. */
  function smemUrejati(
    idLastnik: number | null,
    idKlubLastnik: number | null,
    tekmovanje?: TekmovanjeDostopa,
  ): boolean {
    if (jeAdmin) return true
    if (!jeOrganizator || !uporabnik) return false
    if (idLastnik !== null && idLastnik === uporabnik.id) return true
    if (idKlubLastnik !== null && uporabnik.idKlub !== null && idKlubLastnik === uporabnik.idKlub) {
      return true
    }
    if (tekmovanje) {
      const dodeljena = tekmovanje.vrsta === 'liga' ? uporabnik.dostopLige : uporabnik.dostopTurnirji
      return (dodeljena ?? []).includes(tekmovanje.id)
    }
    return false
  }

  const vrednost: Avtentikacija = {
    uporabnik,
    jeAdmin,
    jeOrganizator,
    jeIgralec,
    jePremium,
    imaSirokoSpremljanje,
    smeUstvarjati: jeAdmin || jeOrganizator,
    mojIdIgralec: jeIgralec ? uporabnik!.idIgralec : null,
    smemUrejati,
    nalaganje,
    prijavaPotekla,
    zapriObvestiloPoteka: () => nastaviPrijavaPotekla(false),
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
