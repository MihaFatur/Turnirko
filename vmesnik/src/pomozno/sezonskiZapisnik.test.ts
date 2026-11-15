import { describe, expect, it } from 'vitest'

import type { ProfilDto, TekmaProfila, TekmovanjeProfila, TockaGrafa } from '../api/tipi'
import {
  oblikujMesto,
  oblikujSpremembo,
  sestaviSezonskiZapisnik,
  sezoneIgralca,
  tekemBrezDatuma,
} from './sezonskiZapisnik'

function tekma(idTekme: number, datum: string | null, polja: Partial<TekmaProfila> = {}): TekmaProfila {
  return {
    idTekme,
    ligaska: false,
    datum,
    tekmovanje: 'Odprto prvenstvo',
    del: 'Člani',
    niziZa: 3,
    niziProti: 1,
    zmaga: true,
    izidTip: 'IGRANO',
    spremembaRatinga: null,
    tekmovanjeKljuc: 'T-1',
    ...polja,
  } as TekmaProfila
}

function tocka(datum: string | null, vrednost: number, sprememba: number): TockaGrafa {
  return { datum, vrednost, sprememba } as TockaGrafa
}

function profil(
  tekme: TekmaProfila[],
  graf: TockaGrafa[] = [],
  tekmovanja: Partial<TekmovanjeProfila>[] = [],
): ProfilDto {
  return { tekme, graf, tekmovanja } as ProfilDto
}

describe('sezone igralca', () => {
  it('rez je 1. julij, najnovejša sezona je prva', () => {
    const p = profil([tekma(1, '2026-06-30'), tekma(2, '2026-07-01'), tekma(3, '2025-10-04')])
    expect(sezoneIgralca(p)).toEqual(['2026/27', '2025/26'])
  })

  it('neodigrana tekma (brez boja, prosto) sezone ne odpre', () => {
    const p = profil([
      tekma(1, '2024-10-01', { izidTip: 'BREZ_BOJA' }),
      tekma(2, '2023-10-01', { izidTip: 'PROSTO' }),
      tekma(3, '2025-10-01', { izidTip: 'PREDAJA' }),
      tekma(4, '2022-10-01', { izidTip: null }),
    ])
    expect(sezoneIgralca(p)).toEqual(['2025/26', '2022/23'])
  })

  it('tekme brez datuma ne sodijo v nobeno sezono, a se preštejejo', () => {
    const p = profil([
      tekma(1, null),
      tekma(2, null, { izidTip: 'BREZ_BOJA' }),
      tekma(3, '2025-10-01'),
    ])
    expect(sezoneIgralca(p)).toEqual(['2025/26'])
    expect(tekemBrezDatuma(p)).toBe(1)
  })
})

describe('sezonski zapisnik', () => {
  const p = profil(
    [
      /* V profilu so najnovejše prve; zapisnik jih obrne. */
      tekma(4, '2026-07-01', { tekmovanjeKljuc: 'T-3' }),
      tekma(3, '2026-03-14', { tekmovanjeKljuc: 'L-7', ligaska: true, tekmovanje: 'Liga', zmaga: false, niziZa: 1, niziProti: 3, spremembaRatinga: -8 }),
      tekma(2, '2025-10-04', { spremembaRatinga: 12 }),
      tekma(1, '2025-10-04', { spremembaRatinga: 5, niziProti: 0 }),
      tekma(9, '2025-10-04', { izidTip: 'BREZ_BOJA' }),
    ],
    [
      tocka('2025-06-01', 1000, 20),
      tocka('2025-10-04', 1005, 5),
      tocka('2025-10-04', 1017, 12),
      tocka('2026-03-14', 1009, -8),
      tocka('2026-07-01', 1030, 21),
    ],
    [{ kljuc: 'T-1', ime: 'Odprto prvenstvo Žalca', del: 'Člani', mesto: 3, mestoDo: 4, udelezencev: 32 }],
  )

  it('tekme sezone po datumu navzgor, na isti dan po id-ju', () => {
    const zapisnik = sestaviSezonskiZapisnik(p, '2025/26')
    expect(zapisnik.tekme.map((t) => t.idTekme)).toEqual([1, 2, 3])
    expect(zapisnik.tekem).toBe(3)
  })

  it('izkupiček, odstotek in nizi samo iz odigranih tekem sezone', () => {
    const zapisnik = sestaviSezonskiZapisnik(p, '2025/26')
    expect(zapisnik.zmag).toBe(2)
    expect(zapisnik.porazov).toBe(1)
    expect(zapisnik.odstotekZmag).toBe(67)
    expect(zapisnik.dobljeniNizi).toBe(7)
    expect(zapisnik.prejetiNizi).toBe(4)
  })

  it('rating: začetek je stanje pred prvo spremembo v sezoni', () => {
    const zapisnik = sestaviSezonskiZapisnik(p, '2025/26')
    expect(zapisnik.ratingZacetek).toBe(1000)
    expect(zapisnik.ratingKonec).toBe(1009)
    expect(zapisnik.ratingNajvisji).toBe(1017)
    expect(zapisnik.ratingNajnizji).toBe(1000)
    expect(zapisnik.potRatinga).toEqual([1000, 1005, 1017, 1009])
  })

  it('tekmovanja po prvi tekmi; mesto iz profila, sprememba seštevek tekem', () => {
    const zapisnik = sestaviSezonskiZapisnik(p, '2025/26')
    expect(zapisnik.tekmovanja.map((t) => t.kljuc)).toEqual(['T-1', 'L-7'])
    expect(zapisnik.tekmovanja[0]).toMatchObject({
      ime: 'Odprto prvenstvo Žalca',
      zmage: 2,
      porazi: 0,
      sprememba: 17,
      mesto: 3,
      mestoDo: 4,
      udelezencev: 32,
    })
    /* Tekmovanja ni med podatki profila: ime in del prideta iz tekme. */
    expect(zapisnik.tekmovanja[1]).toMatchObject({ ime: 'Liga', ligaska: true, zmage: 0, porazi: 1, sprememba: -8, mesto: null })
  })

  it('tekmovanje brez obračunanih tekem nima spremembe (ne 0)', () => {
    const zapisnik = sestaviSezonskiZapisnik(p, '2026/27')
    expect(zapisnik.tekmovanja[0].sprememba).toBeNull()
  })

  it('sezona brez tekem je prazna, ne napaka', () => {
    const zapisnik = sestaviSezonskiZapisnik(p, '2019/20')
    expect(zapisnik).toMatchObject({
      tekem: 0,
      zmag: 0,
      porazov: 0,
      odstotekZmag: null,
      ratingZacetek: null,
      ratingKonec: null,
      ratingNajvisji: null,
      ratingNajnizji: null,
      potRatinga: [],
      tekmovanja: [],
    })
  })
})

describe('izpis spremembe in mesta', () => {
  it('sprememba s pravim minusom', () => {
    expect(oblikujSpremembo(12)).toBe('+12')
    expect(oblikujSpremembo(-7)).toBe('−7')
    expect(oblikujSpremembo(0)).toBe('±0')
    expect(oblikujSpremembo(null)).toBe('')
  })

  it('mesto, razpon in število udeležencev', () => {
    expect(oblikujMesto({ mesto: null, mestoDo: null, udelezencev: 32 })).toBe('')
    expect(oblikujMesto({ mesto: 3, mestoDo: null, udelezencev: null })).toBe('3.')
    expect(oblikujMesto({ mesto: 3, mestoDo: 3, udelezencev: null })).toBe('3.')
    expect(oblikujMesto({ mesto: 3, mestoDo: 4, udelezencev: null })).toBe('3.–4.')
    expect(oblikujMesto({ mesto: 5, mestoDo: 8, udelezencev: 32 })).toBe('5.–8. od 32')
  })
})
