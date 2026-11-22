import { describe, expect, it } from 'vitest'

import type { SkupinaDto, SvNivoDto } from '../api/tipi'
import { mreza, prijava, tekma } from '../testi/tovarna'
import {
  enakiIdji,
  idjiNenatisnjenih,
  jeNatisnjen,
  mestaHrbta,
  NAJVEC_PRAZNIH_LISTKOV,
  nenatisnjeni,
  opisNatisa,
  pripraviListke,
  sklopiListkov,
  steviloPraznih,
} from './listki'

const prijave = [1, 2, 3, 4, 5, 6, 7, 8].map((id) => prijava(id))
const PRIPRAVLJENA = { status: 'PRIPRAVLJENA' as const }

describe('pripraviListke', () => {
  it('vzame samo pripravljene tekme z znanima udeležencema', () => {
    const podatki = mreza({}, prijave, [
      tekma(1, 1, 2, PRIPRAVLJENA),
      tekma(2, 3, 4, { status: 'V_IGRI' }),
      tekma(3, 5, 6), // končana
      tekma(4, 7, null, PRIPRAVLJENA), // nasprotnik še ni znan
      tekma(5, null, null, { status: 'CAKA' }),
    ])
    expect(pripraviListke(podatki).map((l) => l.tekma.id)).toEqual([1, 2])
  })

  it('skupine pridejo pred glavno mrežo, ta pred tolažilnimi', () => {
    const podatki = mreza({}, prijave, [
      tekma(1, 1, 2, { ...PRIPRAVLJENA, faza: 'TOLAZILNI' }),
      tekma(2, 3, 4, { ...PRIPRAVLJENA, faza: 'GLAVNI' }),
      tekma(3, 5, 6, { ...PRIPRAVLJENA, faza: 'SKUPINA', idSkupina: 1 }),
    ], { skupine: [skupina(1, 'A')] })
    expect(pripraviListke(podatki).map((l) => l.tekma.id)).toEqual([3, 2, 1])
  })

  it('brez podatkov ni listkov', () => {
    expect(pripraviListke(undefined)).toEqual([])
  })
})

describe('sklopi', () => {
  it('skupine, glavni in tolažilni del so ločeni sklopi', () => {
    const podatki = mreza({}, prijave, [
      tekma(1, 1, 2, { ...PRIPRAVLJENA, faza: 'SKUPINA', idSkupina: 1 }),
      tekma(2, 3, 4, { ...PRIPRAVLJENA, faza: 'SKUPINA', idSkupina: 1, kolo: 2 }),
      tekma(3, 5, 6, { ...PRIPRAVLJENA, faza: 'SKUPINA', idSkupina: 2 }),
      tekma(4, 1, 3, { ...PRIPRAVLJENA, faza: 'GLAVNI' }),
      tekma(5, 2, 4, { ...PRIPRAVLJENA, faza: 'TOLAZILNI' }),
    ], { skupine: [skupina(1, 'A'), skupina(2, 'B')] })

    const sklopi = sklopiListkov(pripraviListke(podatki))
    expect(sklopi.map((s) => s.naslov)).toEqual([
      'Skupina A', 'Skupina B', 'Glavni žreb', 'Tekma za 3. mesto',
    ])
    expect(sklopi[0].listki.map((l) => l.tekma.id)).toEqual([1, 2])
  })

  it('krožni sistem brez skupin se deli po kolih', () => {
    const podatki = mreza({}, prijave, [
      tekma(1, 1, 2, { ...PRIPRAVLJENA, faza: 'SKUPINA', kolo: 1 }),
      tekma(2, 3, 4, { ...PRIPRAVLJENA, faza: 'SKUPINA', kolo: 2 }),
    ])
    expect(sklopiListkov(pripraviListke(podatki)).map((s) => s.naslov)).toEqual(['1. kolo', '2. kolo'])
  })

  it('žreb SV regije nosi ime ravni in žreba', () => {
    const podatki = mreza({}, prijave, [
      tekma(1, 1, 2, { ...PRIPRAVLJENA, idZreb: 10, razponOd: 1, razponDo: 8 }),
      tekma(2, 3, 4, { ...PRIPRAVLJENA, idZreb: 11, razponOd: 9, razponDo: 16 }),
    ], {
      svRegija: {
        nivoji: [svNivo(2, [
          { id: 10, indeks: 0, ime: 'Glavni žreb' },
          { id: 11, indeks: 1, ime: 'Tolažilni žreb' },
        ])],
      } as never,
    })
    expect(sklopiListkov(pripraviListke(podatki)).map((s) => s.naslov)).toEqual([
      'Skupina 2 · Glavni žreb', 'Skupina 2 · Tolažilni žreb',
    ])
  })
})

describe('natisnjeni listki', () => {
  const podatki = mreza({}, prijave, [
    tekma(1, 1, 2, { ...PRIPRAVLJENA, listekNatisnjenOb: '2026-11-16T14:32:00' }),
    tekma(2, 3, 4, PRIPRAVLJENA),
    tekma(3, 5, 6, { ...PRIPRAVLJENA, faza: 'TOLAZILNI' }),
  ])
  const listki = pripraviListke(podatki)

  it('loči natisnjene od še nenatisnjenih', () => {
    expect(listki.filter(jeNatisnjen).map((l) => l.tekma.id)).toEqual([1])
    expect(nenatisnjeni(listki).map((l) => l.tekma.id)).toEqual([2, 3])
  })

  it('hitra izbira vzame nenatisnjene, po želji samo iz sklopa', () => {
    expect(idjiNenatisnjenih(listki)).toEqual([2, 3])
    expect(idjiNenatisnjenih(listki, 'tolazilni')).toEqual([3])
    expect(idjiNenatisnjenih(listki, 'ne-obstaja')).toEqual([])
  })

  it('izbira je enaka hitri izbiri samo, če vsebuje natanko iste tekme', () => {
    expect(enakiIdji(new Set([3, 2]), [2, 3])).toBe(true)
    expect(enakiIdji(new Set([2]), [2, 3])).toBe(false)
    expect(enakiIdji(new Set([1, 2, 3]), [2, 3])).toBe(false)
  })
})

describe('mestaHrbta', () => {
  it('poln list ima kodo na vseh šestih hrbtih', () => {
    expect(mestaHrbta(6)).toEqual([true, true, true, true, true, true])
  })

  it('en listek (zgoraj levo) je pokrit pri obračanju po dolgem in po kratkem robu', () => {
    // po dolgem: hrbet zgoraj desno; po kratkem: hrbet spodaj levo
    expect(mestaHrbta(1)).toEqual([false, true, false, false, true, false])
  })

  it('vsak listek ima kodo za sabo v obeh načinih obojestranskega tiska', () => {
    for (let listkov = 1; listkov <= 6; listkov++) {
      const mesta = mestaHrbta(listkov)
      for (let i = 0; i < listkov; i++) {
        const vrstica = Math.floor(i / 2)
        const stolpec = i % 2
        expect(mesta[vrstica * 2 + (1 - stolpec)]).toBe(true)
        expect(mesta[(2 - vrstica) * 2 + stolpec]).toBe(true)
      }
    }
  })
})

describe('steviloPraznih', () => {
  it('sprejme celo število, sicer privzetek', () => {
    expect(steviloPraznih('12')).toBe(12)
    expect(steviloPraznih('7,9')).toBe(6) // ni število: privzetek
    expect(steviloPraznih('4.9')).toBe(4)
    expect(steviloPraznih('')).toBe(6)
    expect(steviloPraznih('0')).toBe(6)
    expect(steviloPraznih('-3')).toBe(6)
  })

  it('ima zgornjo mejo', () => {
    expect(steviloPraznih('99999')).toBe(NAJVEC_PRAZNIH_LISTKOV)
  })
})

describe('opisNatisa', () => {
  it('isti dan samo ura, sicer tudi datum', () => {
    const zdaj = new Date(2026, 10, 16, 18, 0)
    expect(opisNatisa('2026-11-16T14:32:10', zdaj)).toMatch(/^ob 14[:.]32$/)
    expect(opisNatisa('2026-11-15T09:05:00', zdaj)).toMatch(/^15\. 11\. ob 09[:.]05$/)
  })

  it('neveljaven čas da prazen opis', () => {
    expect(opisNatisa('ni čas')).toBe('')
  })
})

function skupina(id: number, oznaka: string): SkupinaDto {
  return { id, oznaka, stopnja: 1, ime: null } as SkupinaDto
}

function svNivo(nivo: number, zrebi: { id: number; indeks: number; ime: string }[]): SvNivoDto {
  return {
    nivo,
    zrebi: zrebi.map((z) => ({ ...z, faza: 'GLAVNI', prvoMesto: 1, zadnjeMesto: 8 })),
  } as unknown as SvNivoDto
}
