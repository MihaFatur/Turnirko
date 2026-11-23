/* Tabela listkov (prenova »Listki 1b«, 23. 11. 2026): skupine v vrsticah, kola v stolpcih,
   izločilni del pod njo; enota »Na vrsti« in dopolnitev lista s praznimi listki. */
import { describe, expect, it } from 'vitest'

import type { SkupinaDto } from '../api/tipi'
import { mreza, prijava, tekma } from '../testi/tovarna'
import {
  NAJVEC_KOL_V_TABELI,
  enoteListkov,
  enotaNaVrsti,
  idjiNaVrsti,
  kratekCasNatisa,
  praznihZaTisk,
  pripraviListke,
  prostaMestaNaListu,
  sklonListovTiska,
  tabelaListkov,
} from './listki'

const prijave = [1, 2, 3, 4, 5, 6, 7, 8].map((id) => prijava(id))
const PRIPRAVLJENA = { status: 'PRIPRAVLJENA' as const }
const skupina = (id: number, oznaka: string): SkupinaDto => ({ id, oznaka, stopnja: 1, ime: null }) as SkupinaDto
const s = (id: number, idSkupina: number, kolo: number, pozicija: number, polja = {}) =>
  tekma(id, 1, 2, { ...PRIPRAVLJENA, faza: 'SKUPINA', idSkupina, kolo, pozicija, ...polja })
const NATISNJENO = { listekNatisnjenOb: '2026-11-23T09:12:00' }

/* Dve skupini po štiri v treh kolih (po dve tekmi na kolo); skupina 2 je id 20, da vrstni red
   po id-ju ne ujame vrstnega reda po oznaki. */
function dveSkupini(natisnjena: number[] = []) {
  const ts = []
  let id = 1
  for (const [idSk] of [[10], [20]]) {
    for (const kolo of [1, 2, 3]) {
      for (const poz of [1, 2]) {
        ts.push(s(id, idSk, kolo, poz, natisnjena.includes(id) ? NATISNJENO : {}))
        id++
      }
    }
  }
  return mreza({}, prijave, ts, { skupine: [skupina(20, '2'), skupina(10, '1')] })
}

describe('tabelaListkov', () => {
  it('skupine so vrstice, kola stolpci, listki po celicah', () => {
    const t = tabelaListkov(pripraviListke(dveSkupini()))
    expect(t.kola).toEqual([1, 2, 3])
    expect(t.vrste.map((v) => v.naslov)).toEqual(['Sk. 1', 'Sk. 2'])
    expect(t.vrste[0].naslovDolgo).toBe('Skupina 1')
    expect(t.vrste[0].celice.map((c) => c.map((l) => l.tekma.id))).toEqual([[1, 2], [3, 4], [5, 6]])
    expect(t.vrste[1].celice.map((c) => c.map((l) => l.tekma.id))).toEqual([[7, 8], [9, 10], [11, 12]])
    expect(t.ostali).toEqual([])
  })

  it('vrstni red tiska na tabelo ne vpliva', () => {
    const po = (r: 'skupine' | 'kola') =>
      tabelaListkov(pripraviListke(dveSkupini(), r)).vrste.map((v) => v.celice.map((c) => c.length))
    expect(po('kola')).toEqual(po('skupine'))
  })

  it('skupine gredo po vrsti oznak (9 pred 10), ne po id-ju', () => {
    const m = mreza({}, prijave, [s(1, 1, 1, 1), s(2, 2, 1, 1), s(3, 3, 1, 1)], {
      skupine: [skupina(1, '10'), skupina(2, '9'), skupina(3, '1')],
    })
    expect(tabelaListkov(pripraviListke(m)).vrste.map((v) => v.naslov)).toEqual(['Sk. 1', 'Sk. 9', 'Sk. 10'])
  })

  it('kolo, v katerem ni nobene pripravljene tekme, ni stolpec', () => {
    const m = mreza({}, prijave, [s(1, 10, 2, 1), s(2, 10, 3, 1), s(3, 20, 2, 1)], {
      skupine: [skupina(10, '1'), skupina(20, '2')],
    })
    const t = tabelaListkov(pripraviListke(m))
    expect(t.kola).toEqual([2, 3])
    expect(t.vrste[1].celice.map((c) => c.length)).toEqual([1, 0])
  })

  it('izločilni del in tolažilni žreb so vrstice pod tabelo, vsaka z oznako faze', () => {
    const m = mreza({}, prijave, [
      s(1, 10, 1, 1),
      tekma(90, 3, 4, { ...PRIPRAVLJENA, faza: 'GLAVNI', kolo: 1 }),
      tekma(91, 5, 6, { ...PRIPRAVLJENA, faza: 'GLAVNI', kolo: 1 }),
      tekma(92, 1, 2, { ...PRIPRAVLJENA, faza: 'TOLAZILNI', kolo: 2 }),
    ], { skupine: [skupina(10, '1')] })
    const t = tabelaListkov(pripraviListke(m))
    expect(t.vrste).toHaveLength(1)
    expect(t.ostali.map((o) => o.naslov)).toEqual(['Mreža · Finale', 'Tolažilni'])
    expect(t.ostali[0].listki.map((l) => l.tekma.id)).toEqual([90, 91])
  })

  it('krožni sistem brez skupin ostane v vrsticah po kolih', () => {
    const m = mreza({}, prijave, [
      tekma(1, 1, 2, { ...PRIPRAVLJENA, faza: 'SKUPINA', kolo: 1 }),
      tekma(2, 3, 4, { ...PRIPRAVLJENA, faza: 'SKUPINA', kolo: 2 }),
    ])
    const t = tabelaListkov(pripraviListke(m))
    expect(t.kola).toEqual([])
    expect(t.vrste).toEqual([])
    expect(t.ostali.map((o) => o.naslov)).toEqual(['1. kolo', '2. kolo'])
  })

  it('pri preveč kolih tabela ne bi bila berljiva: kola gredo v vrstice', () => {
    const ts = Array.from({ length: NAJVEC_KOL_V_TABELI + 1 }, (_, i) => s(i + 1, 10, i + 1, 1))
    const m = mreza({}, prijave, ts, { skupine: [skupina(10, '1')] })
    const t = tabelaListkov(pripraviListke(m))
    expect(t.vrste).toEqual([])
    expect(t.ostali).toHaveLength(NAJVEC_KOL_V_TABELI + 1)
  })

  it('brez listkov je tabela prazna', () => {
    expect(tabelaListkov([])).toEqual({ kola: [], vrste: [], ostali: [] })
  })
})

describe('enota na vrsti (privzeta izbira)', () => {
  it('prvo kolo, ko ni nič natisnjeno', () => {
    const listki = pripraviListke(dveSkupini())
    const enote = enoteListkov(tabelaListkov(listki))
    expect(enote.map((e) => e.naslov)).toEqual(['1. kolo', '2. kolo', '3. kolo'])
    expect(enotaNaVrsti(enote)?.naslov).toBe('1. kolo')
    expect(idjiNaVrsti(listki)).toEqual([1, 2, 7, 8])
  })

  it('po natisnjenem 1. kolu je na vrsti 2. kolo, nenatisnjeni listki ostanejo izbrani', () => {
    /* tekma 1 in 2 (skupina 1) ter 7 in 8 (skupina 2) so 1. kolo; 3 je že 2. kolo */
    const listki = pripraviListke(dveSkupini([1, 2, 7, 8, 3]))
    expect(enotaNaVrsti(enoteListkov(tabelaListkov(listki)))?.naslov).toBe('2. kolo')
    expect(idjiNaVrsti(listki)).toEqual([4, 9, 10])
  })

  it('ko je skupinski del natisnjen, pride na vrsto izločilni del', () => {
    const m = mreza({}, prijave, [
      s(1, 10, 1, 1, NATISNJENO),
      tekma(90, 3, 4, { ...PRIPRAVLJENA, faza: 'GLAVNI', kolo: 1 }),
    ], { skupine: [skupina(10, '1')] })
    const listki = pripraviListke(m)
    expect(enotaNaVrsti(enoteListkov(tabelaListkov(listki)))?.naslov).toBe('Mreža · Finale')
    expect(idjiNaVrsti(listki)).toEqual([90])
  })

  it('ko je vse natisnjeno, ni nobene enote na vrsti in nič ni izbrano', () => {
    const listki = pripraviListke(dveSkupini([1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12]))
    expect(enotaNaVrsti(enoteListkov(tabelaListkov(listki)))).toBeNull()
    expect(idjiNaVrsti(listki)).toEqual([])
  })
})

describe('listi A4 in prazni listki', () => {
  it('prosta mesta na zadnjem listu', () => {
    expect([0, 1, 5, 6, 7, 14, 24].map(prostaMestaNaListu)).toEqual([0, 5, 1, 0, 5, 4, 0])
  })

  it('dopolnitev s praznimi do polnega lista, samo ob pravih listkih', () => {
    expect(praznihZaTisk(14, 0, false)).toBe(0)
    expect(praznihZaTisk(14, 0, true)).toBe(4)
    // 14 + 2 praznih = 16 listkov: do polnega lista (18) manjkata še 2
    expect(praznihZaTisk(14, 2, true)).toBe(4)
    expect(praznihZaTisk(14, 3, false)).toBe(3)
    // samo prazni listki se ne dopolnjujejo
    expect(praznihZaTisk(0, 5, true)).toBe(5)
    // poln list: nič za dopolniti
    expect(praznihZaTisk(24, 0, true)).toBe(0)
  })

  it('sklanjanje listov (list je moškega spola)', () => {
    expect([1, 2, 3, 4, 5, 11, 101].map(sklonListovTiska)).toEqual([
      'list', 'lista', 'listi', 'listi', 'listov', 'listov', 'list',
    ])
  })
})

describe('kratekCasNatisa', () => {
  const zdaj = new Date('2026-11-23T15:00:00')
  it('danes samo ura, starejši dan datum', () => {
    expect(kratekCasNatisa('2026-11-23T09:12:00', zdaj)).toBe('09:12')
    expect(kratekCasNatisa('2026-11-16T14:32:00', zdaj)).toBe('16. 11.')
    expect(kratekCasNatisa('nesmisel', zdaj)).toBe('')
  })
})
