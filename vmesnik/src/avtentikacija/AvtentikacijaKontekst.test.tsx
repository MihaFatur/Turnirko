// @vitest-environment jsdom
import { act } from 'react'
import { createRoot, type Root } from 'react-dom/client'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { api, nastaviPoverilnice } from '../api/odjemalec'
import { AvtentikacijaPonudnik, useAvtentikacija } from './AvtentikacijaKontekst'

;(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true

const KLJUC = 'turnirko-poverilnice'
const PROFIL = { id: 7, uporabniskoIme: 'organizator', vloga: 'ORGANIZATOR', status: 'POTRJEN', idIgralec: null }

/* Lažni strežnik: /auth/me in vse ostalo odgovori po tem, ali poverilnice (še)
   veljajo. Zapiše si glavo Authorization vsake zahteve. */
let veljavne: string | null
let glave: (string | undefined)[]

function namestiStreznik() {
  glave = []
  vi.stubGlobal(
    'fetch',
    vi.fn(async (pot: string, moznosti?: RequestInit) => {
      const avtorizacija = (moznosti?.headers as Record<string, string> | undefined)?.['Authorization']
      glave.push(avtorizacija)
      const zavrnjen = avtorizacija !== undefined && avtorizacija !== `Basic ${veljavne}`
      const zasciteno = pot.endsWith('/auth/me')
      if (zavrnjen || (zasciteno && avtorizacija === undefined)) {
        return { ok: false, status: 401, json: async () => ({ title: 'Potrebna je prijava' }) } as Response
      }
      return { ok: true, status: 200, json: async () => (zasciteno ? PROFIL : [])} as Response
    }),
  )
}

let stanje: ReturnType<typeof useAvtentikacija>
function Opazovalec() {
  stanje = useAvtentikacija()
  return null
}

let vsebnik: HTMLDivElement
let koren: Root
let poizvedbe: QueryClient

async function zazeni() {
  await act(async () => {
    koren.render(
      <QueryClientProvider client={poizvedbe}>
        <AvtentikacijaPonudnik>
          <Opazovalec />
        </AvtentikacijaPonudnik>
      </QueryClientProvider>,
    )
  })
}

beforeEach(() => {
  veljavne = 'dmVsamF2bmU='
  namestiStreznik()
  sessionStorage.clear()
  poizvedbe = new QueryClient()
  vsebnik = document.createElement('div')
  document.body.appendChild(vsebnik)
  koren = createRoot(vsebnik)
})

afterEach(() => {
  act(() => koren.unmount())
  vsebnik.remove()
  nastaviPoverilnice(null)
  vi.unstubAllGlobals()
})

describe('potek prijave v odprtem zavihku', () => {
  it('zavrnjene poverilnice prijavljenega: gost, počiščena shramba, obvestilo, osvežene poizvedbe', async () => {
    sessionStorage.setItem(KLJUC, 'dmVsamF2bmU=')
    await zazeni()
    expect(stanje.uporabnik?.uporabniskoIme).toBe('organizator')
    expect(stanje.prijavaPotekla).toBe(false)

    /* V drugem zavihku je bilo geslo spremenjeno. */
    veljavne = 'bm92bw=='
    const osvezitev = vi.spyOn(poizvedbe, 'invalidateQueries')
    await act(async () => {
      await api.vrni('/turnirji').catch(() => {})
    })

    expect(stanje.uporabnik).toBeNull()
    expect(stanje.jeOrganizator).toBe(false)
    expect(stanje.prijavaPotekla).toBe(true)
    expect(sessionStorage.getItem(KLJUC)).toBeNull()
    expect(osvezitev).toHaveBeenCalledTimes(1)
  })

  it('po poteku gredo zahteve brez glave: z zavrnjenimi poverilnicami gre ena sama', async () => {
    sessionStorage.setItem(KLJUC, 'dmVsamF2bmU=')
    await zazeni()
    veljavne = 'bm92bw=='
    glave.length = 0

    await act(async () => {
      /* Javno branje se po zavrnitvi samo ponovi brez glave in uspe. */
      await expect(api.vrni('/turnirji')).resolves.toEqual([])
      await api.vrni('/lige')
    })

    expect(glave).toEqual(['Basic dmVsamF2bmU=', undefined, undefined])
  })

  it('obvestilo ugasne z gumbom in z novo prijavo', async () => {
    sessionStorage.setItem(KLJUC, 'dmVsamF2bmU=')
    await zazeni()
    veljavne = 'bm92bzpnZXNsbw=='
    await act(async () => {
      await api.vrni('/turnirji').catch(() => {})
    })
    expect(stanje.prijavaPotekla).toBe(true)

    act(() => stanje.zapriObvestiloPoteka())
    expect(stanje.prijavaPotekla).toBe(false)

    await act(async () => {
      await api.vrni('/turnirji')
    })
    /* Gost brez poverilnic obvestila ne dobi znova. */
    expect(stanje.prijavaPotekla).toBe(false)

    await act(async () => {
      await stanje.prijava('novo', 'geslo')
    })
    expect(stanje.uporabnik?.uporabniskoIme).toBe('organizator')
    expect(sessionStorage.getItem(KLJUC)).toBe('bm92bzpnZXNsbw==')
  })
})

describe('kar NI potek prijave', () => {
  it('napačno geslo ob prijavi: napaka gre klicatelju, obvestila ni', async () => {
    await zazeni()

    let napaka: unknown
    await act(async () => {
      await stanje.prijava('organizator', 'napacno').catch((e) => (napaka = e))
    })

    expect(napaka).toMatchObject({ stanje: 401 })
    expect(stanje.uporabnik).toBeNull()
    expect(stanje.prijavaPotekla).toBe(false)
    expect(sessionStorage.getItem(KLJUC)).toBeNull()
  })

  it('zastarele shranjene poverilnice ob zagonu se tiho počistijo', async () => {
    sessionStorage.setItem(KLJUC, 'c3RhcmU=')
    await zazeni()

    expect(stanje.uporabnik).toBeNull()
    expect(stanje.nalaganje).toBe(false)
    expect(stanje.prijavaPotekla).toBe(false)
    expect(sessionStorage.getItem(KLJUC)).toBeNull()
  })

  it('odjava obvestila ne pokaže', async () => {
    sessionStorage.setItem(KLJUC, 'dmVsamF2bmU=')
    await zazeni()

    act(() => stanje.odjava())

    expect(stanje.uporabnik).toBeNull()
    expect(stanje.prijavaPotekla).toBe(false)
  })
})
