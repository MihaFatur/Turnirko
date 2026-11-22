import { describe, expect, it } from 'vitest'

import { NapakaStreznika } from '../api/odjemalec'
import {
  VELJA_MS,
  ZAMIK_NAJVEC_MS,
  ZAMIK_ZACETNI_MS,
  besediloCakajocih,
  jePretekel,
  novKljucPosiljanja,
  odzivNaNapako,
  shrambaVrste,
  zamikPonovitve,
  type ZapisVrste,
} from './vrstaFotografij'

const streznik = (stanje: number) => new NapakaStreznika(stanje, 'Napaka', 'sporočilo')

describe('odzivNaNapako', () => {
  it('omrežje, časovna meja in neberljiv odgovor se ponovijo', () => {
    expect(odzivNaNapako(new TypeError('Failed to fetch'))).toBe('ponovi')
    expect(odzivNaNapako(new DOMException('prekinjeno', 'AbortError'))).toBe('ponovi')
    expect(odzivNaNapako(new SyntaxError('JSON'))).toBe('ponovi')
  })

  it('5xx, 408 in 429 se ponovijo', () => {
    for (const s of [500, 502, 503, 504, 408, 429]) expect(odzivNaNapako(streznik(s))).toBe('ponovi')
  })

  it('401 in 403 ustavita vrsto (fotografije počakajo)', () => {
    expect(odzivNaNapako(streznik(401))).toBe('ustavi')
    expect(odzivNaNapako(streznik(403))).toBe('ustavi')
  })

  it('ostali 4xx se ne ponavljajo', () => {
    for (const s of [400, 404, 409, 413]) expect(odzivNaNapako(streznik(s))).toBe('zavrni')
  })
})

describe('zamikPonovitve', () => {
  it('se podvaja do meje', () => {
    expect(zamikPonovitve(1)).toBe(ZAMIK_ZACETNI_MS)
    expect(zamikPonovitve(2)).toBe(2 * ZAMIK_ZACETNI_MS)
    expect(zamikPonovitve(3)).toBe(4 * ZAMIK_ZACETNI_MS)
    expect(zamikPonovitve(20)).toBe(ZAMIK_NAJVEC_MS)
  })

  it('zaseden strežnik počaka vsaj 5 s (Retry-After)', () => {
    expect(zamikPonovitve(1, streznik(503))).toBe(5000)
    expect(zamikPonovitve(1, streznik(429))).toBe(5000)
    expect(zamikPonovitve(1, streznik(500))).toBe(ZAMIK_ZACETNI_MS)
    expect(zamikPonovitve(10, streznik(503))).toBe(ZAMIK_NAJVEC_MS)
  })
})

describe('jePretekel', () => {
  it('fotografija velja 24 ur', () => {
    const zdaj = 1_800_000_000_000
    expect(jePretekel({ ustvarjeno: zdaj - VELJA_MS }, zdaj)).toBe(false)
    expect(jePretekel({ ustvarjeno: zdaj - VELJA_MS - 1 }, zdaj)).toBe(true)
  })
})

describe('novKljucPosiljanja', () => {
  it('ustreza obliki, ki jo sprejme strežnik, in se ne ponovi', () => {
    const kljuci = new Set(Array.from({ length: 50 }, novKljucPosiljanja))
    expect(kljuci.size).toBe(50)
    for (const k of kljuci) expect(k).toMatch(/^[A-Za-z0-9-]{8,64}$/)
  })
})

describe('besediloCakajocih', () => {
  it('sklanja po številu', () => {
    expect(besediloCakajocih(1)).toBe('1 fotografija čaka na signal.')
    expect(besediloCakajocih(2)).toBe('2 fotografiji čakata na signal.')
    expect(besediloCakajocih(3)).toBe('3 fotografije čakajo na signal.')
    expect(besediloCakajocih(4)).toBe('4 fotografije čakajo na signal.')
    expect(besediloCakajocih(5)).toBe('5 fotografij čaka na signal.')
    expect(besediloCakajocih(11)).toBe('11 fotografij čaka na signal.')
    expect(besediloCakajocih(101)).toBe('101 fotografija čaka na signal.')
  })
})

/* jsdom nima IndexedDB: shramba tedaj živi v pomnilniku strani (zasebno okno
   starejšega brskalnika), vrsta pa deluje enako. */
describe('shrambaVrste brez IndexedDB', () => {
  const zapis = (kljuc: string): ZapisVrste => ({
    kljuc,
    idTurnir: 7,
    idUporabnika: 3,
    jpeg: new Blob(['x'], { type: 'image/jpeg' }),
    ustvarjeno: 1,
  })

  it('shrani, vrne, izbriše in ob odjavi izprazni', async () => {
    expect(await shrambaVrste.shrani(zapis('a'))).toBe(false)
    await shrambaVrste.shrani(zapis('b'))
    expect((await shrambaVrste.vsi()).map((z) => z.kljuc).sort()).toEqual(['a', 'b'])
    await shrambaVrste.izbrisi('a')
    expect((await shrambaVrste.vsi()).map((z) => z.kljuc)).toEqual(['b'])
    await shrambaVrste.izbrisiVse()
    expect(await shrambaVrste.vsi()).toEqual([])
  })
})

describe('shrambaVrste.pocistiPretekle', () => {
  it('izbriše samo fotografije, starejše od 24 ur', async () => {
    const zdaj = 1_800_000_000_000
    const z = (kljuc: string, ustvarjeno: number): ZapisVrste => ({
      kljuc, idTurnir: 1, idUporabnika: 1, jpeg: new Blob(['x']), ustvarjeno,
    })
    await shrambaVrste.shrani(z('staro', zdaj - VELJA_MS - 1))
    await shrambaVrste.shrani(z('sveze', zdaj - 1000))
    await shrambaVrste.pocistiPretekle(zdaj)
    expect((await shrambaVrste.vsi()).map((x) => x.kljuc)).toEqual(['sveze'])
    await shrambaVrste.izbrisiVse()
  })
})
