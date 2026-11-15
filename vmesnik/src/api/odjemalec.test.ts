import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { api, NapakaStreznika, nastaviPoverilnice, obNeveljavnihPoverilnicah } from './odjemalec'

type Odgovor = { status: number; telo?: unknown }

/* Lažni fetch: vsak klic dobi naslednji odgovor iz vrste in si zapomni glave. */
function namestiFetch(odgovori: Odgovor[]) {
  const klici: { pot: string; avtorizacija: string | undefined }[] = []
  const lazni = vi.fn(async (pot: string, moznosti?: RequestInit) => {
    klici.push({
      pot,
      avtorizacija: (moznosti?.headers as Record<string, string> | undefined)?.['Authorization'],
    })
    const { status, telo } = odgovori.shift() ?? { status: 200, telo: {} }
    return {
      ok: status >= 200 && status < 300,
      status,
      json: async () => telo ?? {},
    } as Response
  })
  vi.stubGlobal('fetch', lazni)
  return klici
}

const NI_PRIJAVE = { status: 401, telo: { title: 'Potrebna je prijava', detail: 'Za ta pogled je potrebna prijava.' } }

let poslusalec: ReturnType<typeof vi.fn<() => void>>

beforeEach(() => {
  poslusalec = vi.fn<() => void>()
  obNeveljavnihPoverilnicah(poslusalec)
})

afterEach(() => {
  nastaviPoverilnice(null)
  obNeveljavnihPoverilnicah(null)
  vi.unstubAllGlobals()
})

describe('osrednja obravnava 401', () => {
  it('zavrnjene poverilnice se zavržejo: branje se enkrat ponovi brez glave in uspe kot gost', async () => {
    const klici = namestiFetch([NI_PRIJAVE, { status: 200, telo: ['turnir'] }, { status: 200, telo: [] }])
    nastaviPoverilnice('c3RhcmU6Z2VzbG8=')

    await expect(api.vrni('/turnirji')).resolves.toEqual(['turnir'])
    await api.vrni('/lige')

    expect(klici.map((k) => k.avtorizacija)).toEqual(['Basic c3RhcmU6Z2VzbG8=', undefined, undefined])
    expect(poslusalec).toHaveBeenCalledTimes(1)
  })

  it('deset hkratnih zavrnitev pokliče poslušalca enkrat; z glavo gre vsaka zahteva samo enkrat', async () => {
    const klici = namestiFetch(Array.from({ length: 10 }, () => NI_PRIJAVE))
    nastaviPoverilnice('c3RhcmU6Z2VzbG8=')

    const izidi = await Promise.allSettled(Array.from({ length: 10 }, (_, i) => api.vrni(`/dogodki/${i}`)))

    expect(izidi.every((izid) => izid.status === 'fulfilled')).toBe(true)
    expect(klici.filter((k) => k.avtorizacija !== undefined)).toHaveLength(10)
    expect(klici.filter((k) => k.avtorizacija === undefined)).toHaveLength(10)
    expect(poslusalec).toHaveBeenCalledTimes(1)
  })

  it('zaščiteno branje po ponovitvi ostane 401 in se ne ponavlja naprej', async () => {
    const klici = namestiFetch([NI_PRIJAVE, NI_PRIJAVE, NI_PRIJAVE])
    nastaviPoverilnice('c3RhcmU6Z2VzbG8=')

    await expect(api.vrni('/auth/me')).rejects.toBeInstanceOf(NapakaStreznika)

    expect(klici.map((k) => k.avtorizacija)).toEqual(['Basic c3RhcmU6Z2VzbG8=', undefined])
  })

  it('pisanje se ne ponovi: brez prijave bi ga strežnik zavrnil', async () => {
    const klici = namestiFetch([NI_PRIJAVE, { status: 200, telo: {} }])
    nastaviPoverilnice('c3RhcmU6Z2VzbG8=')

    await expect(api.objavi('/tekme/1/rezultat', { nizi: [] })).rejects.toMatchObject({ stanje: 401 })

    expect(klici).toHaveLength(1)
    expect(poslusalec).toHaveBeenCalledTimes(1)
  })

  it('401 brez poslanih poverilnic je gost na zaščiteni poti, ne potek prijave', async () => {
    namestiFetch([NI_PRIJAVE])

    await expect(api.vrni('/racuni')).rejects.toMatchObject({ stanje: 401 })

    expect(poslusalec).not.toHaveBeenCalled()
  })

  it.each([403, 404, 409, 429, 500])('odgovor %i poverilnic ne zavrže', async (status) => {
    const klici = namestiFetch([{ status }, { status: 200, telo: {} }])
    nastaviPoverilnice('dmVsamF2bmU6Z2VzbG8=')

    await expect(api.vrni('/lige/1')).rejects.toMatchObject({ stanje: status })
    await api.vrni('/lige/1')

    expect(klici[1].avtorizacija).toBe('Basic dmVsamF2bmU6Z2VzbG8=')
    expect(poslusalec).not.toHaveBeenCalled()
  })

  it('pozen 401 stare zahteve ne odnese poverilnic nove prijave', async () => {
    /* Prva zahteva odide s starimi poverilnicami in čaka; vmes se uporabnik
       prijavi znova. Ko zavrnitev stare zahteve prispe, velja nova prijava. */
    let spusti!: () => void
    const zapora = new Promise<void>((razresi) => (spusti = razresi))
    const glave: (string | undefined)[] = []
    vi.stubGlobal(
      'fetch',
      vi.fn(async (_pot: string, moznosti?: RequestInit) => {
        const avtorizacija = (moznosti?.headers as Record<string, string> | undefined)?.['Authorization']
        glave.push(avtorizacija)
        if (avtorizacija === 'Basic c3RhcmU=') {
          await zapora
          return { ok: false, status: 401, json: async () => ({}) } as Response
        }
        return { ok: true, status: 200, json: async () => ({}) } as Response
      }),
    )

    nastaviPoverilnice('c3RhcmU=')
    const stara = api.vrni('/turnirji')
    nastaviPoverilnice('bm92ZQ==')
    spusti()
    await expect(stara).rejects.toMatchObject({ stanje: 401 })
    await api.vrni('/turnirji')

    expect(glave).toEqual(['Basic c3RhcmU=', 'Basic bm92ZQ=='])
    expect(poslusalec).not.toHaveBeenCalled()
  })
})

describe('napaka strežnika', () => {
  it('prevzame naslov in opis iz problem-detail', async () => {
    namestiFetch([{ status: 409, telo: { title: 'Navzkrižje', detail: 'Žreb je že izveden.' } }])

    await expect(api.objavi('/dogodki/1/zreb')).rejects.toMatchObject({
      stanje: 409,
      naslov: 'Navzkrižje',
      message: 'Žreb je že izveden.',
    })
  })

  it('204 nima telesa', async () => {
    namestiFetch([{ status: 204 }])
    await expect(api.izbrisi('/turnirji/1')).resolves.toBeUndefined()
  })
})
