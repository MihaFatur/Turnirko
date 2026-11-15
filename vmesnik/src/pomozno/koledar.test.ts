import { afterEach, describe, expect, it, vi } from 'vitest'

import { vnosKoledarja } from '../testi/tovarna'
import {
  celiceMeseca,
  danesIso,
  danVTednu,
  dneviVnosa,
  imeMeseca,
  jeVikend,
  kljucTekmovanja,
  mesecIzIso,
  naslednjiDan,
  oblikujDanPolno,
  poDnevih,
  premakniMesec,
  prihajajoci,
  prviDanMeseca,
  stevilkaDneva,
  tonVnosa,
  trakoviTedna,
  zadnjiDanMeseca,
} from './koledar'

afterEach(() => {
  vi.useRealTimers()
})

/* Teden od ponedeljka 5. 10. do nedelje 11. 10. 2026. */
const TEDEN = [
  '2026-10-05', '2026-10-06', '2026-10-07', '2026-10-08', '2026-10-09', '2026-10-10', '2026-10-11',
]

describe('dnevi in meseci', () => {
  it('teden se začne s ponedeljkom', () => {
    expect(danVTednu('2026-10-05')).toBe(0)
    expect(danVTednu('2026-10-11')).toBe(6)
    expect(jeVikend('2026-10-10')).toBe(true)
    expect(jeVikend('2026-10-09')).toBe(false)
  })

  it('dan prehoda ure je še vedno nedelja', () => {
    expect(danVTednu('2026-03-29')).toBe(6)
    expect(danVTednu('2026-10-25')).toBe(6)
  })

  it('naslednji dan čez prehod ure, mesec, leto in prestopni dan', () => {
    expect(naslednjiDan('2026-03-28')).toBe('2026-03-29')
    expect(naslednjiDan('2026-10-25')).toBe('2026-10-26')
    expect(naslednjiDan('2026-12-31')).toBe('2027-01-01')
    expect(naslednjiDan('2024-02-28')).toBe('2024-02-29')
    expect(naslednjiDan('2026-02-28')).toBe('2026-03-01')
  })

  it('premik meseca čez leto v obe smeri', () => {
    expect(premakniMesec({ leto: 2026, mesec: 12 }, 1)).toEqual({ leto: 2027, mesec: 1 })
    expect(premakniMesec({ leto: 2026, mesec: 1 }, -1)).toEqual({ leto: 2025, mesec: 12 })
    expect(premakniMesec({ leto: 2026, mesec: 5 }, -17)).toEqual({ leto: 2024, mesec: 12 })
    expect(premakniMesec({ leto: 2026, mesec: 5 }, 0)).toEqual({ leto: 2026, mesec: 5 })
  })

  it('prvi in zadnji dan meseca, tudi februar prestopnega leta', () => {
    expect(prviDanMeseca({ leto: 2026, mesec: 10 })).toBe('2026-10-01')
    expect(zadnjiDanMeseca({ leto: 2026, mesec: 10 })).toBe('2026-10-31')
    expect(zadnjiDanMeseca({ leto: 2024, mesec: 2 })).toBe('2024-02-29')
    expect(zadnjiDanMeseca({ leto: 2026, mesec: 2 })).toBe('2026-02-28')
  })

  it('imena in izpis dneva', () => {
    expect(imeMeseca({ leto: 2026, mesec: 10 })).toBe('Oktober 2026')
    expect(mesecIzIso('2026-10-04')).toEqual({ leto: 2026, mesec: 10 })
    expect(stevilkaDneva('2026-10-04')).toBe(4)
    expect(oblikujDanPolno('2026-10-03')).toBe('sobota, 3. oktobra 2026')
    expect(oblikujDanPolno('ni-datum')).toBe('ni-datum')
  })

  it('današnji dan je po uri naprave', () => {
    /* 00.30 v Ljubljani je v UTC še prejšnji dan. */
    vi.useFakeTimers()
    vi.setSystemTime(new Date(2026, 9, 4, 0, 30))
    expect(danesIso()).toBe('2026-10-04')
  })
})

describe('mreža meseca', () => {
  it('vodilne prazne celice do prvega dne in dopolnitev zadnje vrste', () => {
    /* 1. 10. 2026 je četrtek. */
    const celice = celiceMeseca({ leto: 2026, mesec: 10 })
    expect(celice).toHaveLength(35)
    expect(celice.slice(0, 4)).toEqual([null, null, null, '2026-10-01'])
    expect(celice[33]).toBe('2026-10-31')
    expect(celice[34]).toBeNull()
  })

  it('mesec, ki se začne s ponedeljkom in ima štiri tedne, nima praznih', () => {
    /* Februar 2027: 1. 2. je ponedeljek, 28 dni. */
    const celice = celiceMeseca({ leto: 2027, mesec: 2 })
    expect(celice).toHaveLength(28)
    expect(celice.includes(null)).toBe(false)
  })
})

describe('dnevi vnosa', () => {
  it('večdnevni turnir zasede vsak svoj dan', () => {
    expect(dneviVnosa(vnosKoledarja('A', '2026-08-13', '2026-08-14'))).toEqual(['2026-08-13', '2026-08-14'])
    expect(dneviVnosa(vnosKoledarja('A', '2026-08-13'))).toEqual(['2026-08-13'])
  })

  it('pokvarjen razpon (konec pred začetkom) da samo dan začetka', () => {
    expect(dneviVnosa(vnosKoledarja('A', '2026-08-13', '2026-08-01'))).toEqual(['2026-08-13'])
  })

  it('varovalo: največ 60 dni', () => {
    const dnevi = dneviVnosa(vnosKoledarja('A', '2026-01-01', '2026-12-31'))
    expect(dnevi).toHaveLength(60)
    expect(dnevi[59]).toBe('2026-03-01')
  })

  it('po dnevih: vrstni red strežnika ostane', () => {
    const a = vnosKoledarja('A', '2026-10-10', '2026-10-11')
    const b = vnosKoledarja('B', '2026-10-11', '2026-10-11', { id: 2 })
    const zbir = poDnevih([a, b])
    expect(zbir.get('2026-10-10')).toEqual([a])
    expect(zbir.get('2026-10-11')).toEqual([a, b])
    expect(zbir.has('2026-10-12')).toBe(false)
  })

  it('ton je vrsta tekmovanja, ključ loči turnir in ligo z istim id-jem', () => {
    const turnir = vnosKoledarja('T', '2026-10-10')
    const liga = vnosKoledarja('L', '2026-10-10', '2026-10-10', { vrsta: 'LIGA' })
    expect(tonVnosa(turnir)).toBe(1)
    expect(tonVnosa(liga)).toBe(2)
    expect(kljucTekmovanja(turnir)).not.toBe(kljucTekmovanja(liga))
  })
})

describe('trakovi tedna', () => {
  it('večdnevni turnir je en trak čez svoje dni', () => {
    const a = vnosKoledarja('A', '2026-10-10', '2026-10-11')
    const { trakovi, skriti } = trakoviTedna(TEDEN, [a], 3)
    expect(trakovi).toEqual([{ vnos: a, stolpec: 6, razpon: 2, pas: 0 }])
    expect(skriti.size).toBe(0)
  })

  it('daljši trak gre v zgornji pas ne glede na vrstni red vnosov', () => {
    const kratek = vnosKoledarja('Kratek', '2026-10-10')
    const dolg = vnosKoledarja('Dolg', '2026-10-09', '2026-10-11', { id: 2 })
    const { trakovi } = trakoviTedna(TEDEN, [kratek, dolg], 3)
    expect(trakovi.map((t) => [t.vnos.ime, t.pas])).toEqual([['Dolg', 0], ['Kratek', 1]])
  })

  it('vnosa, ki se ne prekrivata, si delita pas', () => {
    const a = vnosKoledarja('A', '2026-10-05')
    const b = vnosKoledarja('B', '2026-10-07', '2026-10-07', { id: 2 })
    const { trakovi } = trakoviTedna(TEDEN, [a, b], 3)
    expect(trakovi.map((t) => t.pas)).toEqual([0, 0])
  })

  it('kar ne gre v pasove, se prešteje za vsak dan posebej in ne izgine', () => {
    const a = vnosKoledarja('A', '2026-10-10', '2026-10-11')
    const b = vnosKoledarja('B', '2026-10-10', '2026-10-11', { id: 2 })
    const c = vnosKoledarja('C', '2026-10-10', '2026-10-10', { id: 3 })
    const { trakovi, skriti } = trakoviTedna(TEDEN, [a, b, c], 1)
    expect(trakovi.map((t) => t.vnos.ime)).toEqual(['A'])
    expect([...skriti.entries()].sort()).toEqual([['2026-10-10', 2], ['2026-10-11', 1]])
  })

  it('vnos čez nedeljo se v naslednjem tednu začne znova od ponedeljka', () => {
    const a = vnosKoledarja('A', '2026-10-11', '2026-10-13')
    expect(trakoviTedna(TEDEN, [a], 3).trakovi[0]).toMatchObject({ stolpec: 7, razpon: 1 })
    const naslednji = TEDEN.map((dan) => `2026-10-${Number(dan.slice(8)) + 7}`)
    expect(trakoviTedna(naslednji, [a], 3).trakovi[0]).toMatchObject({ stolpec: 1, razpon: 2 })
  })

  it('teden s praznimi celicami: vnos iz prejšnjega meseca se začne pri prvem dnevu', () => {
    const prviTeden = [null, null, null, '2026-10-01', '2026-10-02', '2026-10-03', '2026-10-04']
    const a = vnosKoledarja('A', '2026-09-30', '2026-10-02')
    expect(trakoviTedna(prviTeden, [a], 3).trakovi[0]).toMatchObject({ stolpec: 4, razpon: 2 })
  })

  it('vnos zunaj tedna in prazen teden ne dasta ničesar', () => {
    const a = vnosKoledarja('A', '2026-10-20')
    expect(trakoviTedna(TEDEN, [a], 3).trakovi).toEqual([])
    expect(trakoviTedna([null, null, null, null, null, null, null], [a], 3).trakovi).toEqual([])
  })
})

describe('prihajajoči vnosi', () => {
  it('merilo je konec, ne začetek: turnir v teku ni mimo', () => {
    const vTeku = vnosKoledarja('V teku', '2026-10-09', '2026-10-10')
    const mimo = vnosKoledarja('Mimo', '2026-10-08', '2026-10-09', { id: 2 })
    const jutri = vnosKoledarja('Jutri', '2026-10-11', '2026-10-11', { id: 3 })
    expect(prihajajoci([jutri, mimo, vTeku], '2026-10-10').map((v) => v.ime)).toEqual(['V teku', 'Jutri'])
  })

  it('isti dan po slovenski abecedi', () => {
    const vnosi = ['Žalec', 'Celje', 'Čatež'].map((ime, i) => vnosKoledarja(ime, '2026-10-10', '2026-10-10', { id: i }))
    expect(prihajajoci(vnosi, '2026-10-01').map((v) => v.ime)).toEqual(['Celje', 'Čatež', 'Žalec'])
  })
})
