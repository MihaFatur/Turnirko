import { describe, expect, it } from 'vitest'

import type { BranjeListkaDto, NapakeMrezeDto, PredlogIzidaDto } from '../api/tipi'
import { veljavenNiz } from '../komponente/TockeNizov'
import {
  casPregleda,
  celiceZaPregled,
  dobljeniIzTock,
  odstotek,
  povzetekNizov,
  razdeliVrsto,
  trajanje,
  vnosIzPredloga,
  vrsticeIzPredloga,
  izrezCelice,
  jeZanesljiv,
  slogIzreza,
  delez,
  prejPotem,
  stanjeRazlicice,
  delezOd,
  zamenjaveBesedilo,
  brezStevke,
} from './branjeListkov'

function predlog(polja: Partial<PredlogIzidaDto> = {}): PredlogIzidaDto {
  return {
    izidTip: 'IGRANO',
    dobljeniNizi1: 3,
    dobljeniNizi2: 1,
    zmagovalecStran: null,
    nizi: [
      { tocke1: 11, tocke2: 7, negotovo1: false, negotovo2: false, neveljaven: false },
      { tocke1: 9, tocke2: 11, negotovo1: false, negotovo2: true, neveljaven: false },
      { tocke1: 11, tocke2: null, negotovo1: false, negotovo2: false, neveljaven: false },
      { tocke1: 11, tocke2: 10, negotovo1: false, negotovo2: false, neveljaven: true },
    ],
    opozorila: [],
    pripravljen: false,
    ...polja,
  }
}

describe('branje listkov', () => {
  it('predlog napolni vrstice, neprebrana celica ostane prazna', () => {
    expect(vrsticeIzPredloga(predlog())[2]).toEqual({ tocke1: '11', tocke2: '' })
  })

  it('za pregled so negotove, neprebrane in celice neveljavnega niza', () => {
    expect([...celiceZaPregled(predlog())].sort((a, b) => a - b)).toEqual([3, 5, 6, 7])
  })

  it('vnos po predlogu je navaden vnos rezultata', () => {
    const p = predlog({
      nizi: [{ tocke1: 11, tocke2: 7, negotovo1: false, negotovo2: false, neveljaven: false }],
      dobljeniNizi1: 1,
      dobljeniNizi2: 0,
    })
    expect(vnosIzPredloga(p)).toEqual({
      izidTip: null,
      dobljeniNizi1: 1,
      dobljeniNizi2: 0,
      zmagovalecStran: null,
      nizi: [{ tocke1: 11, tocke2: 7 }],
      steviloNizov: null,
    })
    expect(vnosIzPredloga(predlog({ izidTip: 'BREZ_BOJA', nizi: [], zmagovalecStran: 2 }))).toMatchObject({
      izidTip: 'BREZ_BOJA',
      zmagovalecStran: 2,
      nizi: null,
    })
  })

  it('povzetek nizov', () => {
    expect(povzetekNizov(predlog())).toBe('11:7 9:11 11:? 11:10')
  })

  it('dobljeni nizi iz točk samo pri veljavnih vrsticah', () => {
    expect(dobljeniIzTock([{ tocke1: '11', tocke2: '7' }, { tocke1: '5', tocke2: '11' }], veljavenNiz)).toEqual({
      prvi: 1,
      drugi: 1,
    })
    expect(dobljeniIzTock([{ tocke1: '11', tocke2: '10' }], veljavenNiz)).toBeNull()
    expect(dobljeniIzTock([{ tocke1: '11', tocke2: '' }], veljavenNiz)).toBeNull()
    expect(dobljeniIzTock([], veljavenNiz)).toBeNull()
  })

  it('vrsta: najprej za potrditev po času fotografiranja', () => {
    const b = (id: number, stanje: BranjeListkaDto['stanje'], ob: string) =>
      ({ id, stanje, ustvarjenoOb: ob }) as BranjeListkaDto
    const { zaPotrditev, berem, zakljucena } = razdeliVrsto([
      b(4, 'PREBRANO', '2026-11-19T10:04:00'),
      b(3, 'V_VRSTI', '2026-11-19T10:03:00'),
      b(2, 'NAPAKA', '2026-11-19T10:02:00'),
      b(1, 'POTRJENO', '2026-11-19T10:01:00'),
    ])
    expect(zaPotrditev.map((x) => x.id)).toEqual([2, 4])
    expect(berem.map((x) => x.id)).toEqual([3])
    expect(zakljucena.map((x) => x.id)).toEqual([1])
  })
})

describe('izrez celic in vrstni red (F4)', () => {
  const celice = {
    sirina: 1050,
    visina: 990,
    nizi: [[220, 400, 560, 470, 630, 400, 970, 470], [220, 480, 560, 550, 630, 480, 970, 550]],
    rezultat: [220, 800, 560, 880, 630, 800, 970, 880],
  }

  it('izrez gre v stolpec listka, pri obrnjenem listku zamenjano', () => {
    expect(izrezCelice(celice, 0, false)).toEqual({ x0: 220, y0: 400, x1: 560, y1: 470 })
    expect(izrezCelice(celice, 3, false)).toEqual({ x0: 630, y0: 480, x1: 970, y1: 550 })
    expect(izrezCelice(celice, 0, true)).toEqual({ x0: 630, y0: 400, x1: 970, y1: 470 })
    expect(izrezCelice(celice, 4, false)).toBeNull()
  })

  it('ozadje pokaže samo izrez z robom', () => {
    const slog = slogIzreza({ x0: 106, y0: 106, x1: 193, y1: 143 }, 1000, 1000)
    // 100 × 50 px z robom 6: slika je 10- in 20-krat večja od izreza
    expect(slog.backgroundSize).toBe('1000% 2000%')
    expect(slog.aspectRatio).toBe('100 / 50')
    expect(slog.backgroundPosition).toBe(`${(100 / 900) * 100}% ${(100 / 950) * 100}%`)
  })

  it('zanesljivi listki so na vrhu, sicer po času', () => {
    const b = (id: number, ob: string, pripravljen: boolean | null) =>
      ({
        id,
        stanje: 'PREBRANO',
        ustvarjenoOb: ob,
        tekma: pripravljen === null ? null : {},
        predlog: pripravljen === null ? null : { pripravljen },
      }) as unknown as BranjeListkaDto
    const vrsta = razdeliVrsto([
      b(1, '2026-11-21T10:01:00', false),
      b(2, '2026-11-21T10:02:00', true),
      b(3, '2026-11-21T10:03:00', null),
      b(4, '2026-11-21T10:04:00', true),
    ])
    expect(vrsta.zaPotrditev.map((x) => x.id)).toEqual([2, 4, 1, 3])
    expect(vrsta.zaPotrditev.filter(jeZanesljiv).map((x) => x.id)).toEqual([2, 4])
  })
})

describe('sled pregleda (V69)', () => {
  it('čas teče od prikaza ali od zadnje potrditve, kar je pozneje', () => {
    expect(casPregleda(1000, null, 5200)).toBe(4200)
    // organizator je med tem potrdil drug listek: čakanje ni šteto
    expect(casPregleda(1000, 4000, 5200)).toBe(1200)
    // predlog se je pokazal po zadnji potrditvi
    expect(casPregleda(4500, 4000, 5200)).toBe(700)
  })

  it('brez prikaza ni meritve, negativen čas je nič', () => {
    expect(casPregleda(undefined, 4000, 5200)).toBeNull()
    expect(casPregleda(6000, null, 5200)).toBe(0)
  })
})

describe('oblika meritev', () => {
  it('odstotek', () => {
    expect(odstotek(8, 10)).toBe('80 %')
    expect(odstotek(1, 200)).toBe('0,5 %')
    expect(odstotek(0, 5)).toBe('0 %')
    expect(odstotek(3, 0)).toBe('–')
  })

  it('trajanje', () => {
    expect(trajanje(4200)).toBe('4,2 s')
    expect(trajanje(125_000)).toBe('2 min 5 s')
    expect(trajanje(119_600)).toBe('2 min 0 s')
    expect(trajanje(59_960)).toBe('1 min 0 s')
    expect(trajanje(null)).toBe('–')
  })
})

describe('mreža števk (F6)', () => {
  it('deleže zapiše z vejico, manjkajoče s pomišljajem', () => {
    expect(delez(0.9743)).toBe('97,4 %')
    expect(delez(null)).toBe('–')
    expect(prejPotem(0.95, 0.9612)).toBe('95,0 % → 96,1 %')
    expect(prejPotem(0.95, null)).toBe('–')
  })

  it('stanje različice: prekinjena, zavrnjena, umaknjena, v uporabi', () => {
    const r = { id: 3, sprejeta: true, prekinjena: false, umaknjenaOb: null }
    expect(stanjeRazlicice(r, 3)).toBe('v-uporabi')
    expect(stanjeRazlicice(r, 5)).toBe('sprejeta')
    expect(stanjeRazlicice(r, null)).toBe('sprejeta')
    expect(stanjeRazlicice({ ...r, umaknjenaOb: '2026-11-22T10:00:00' }, null)).toBe('umaknjena')
    expect(stanjeRazlicice({ ...r, sprejeta: false }, 3)).toBe('zavrnjena')
    expect(stanjeRazlicice({ ...r, sprejeta: false, prekinjena: true }, null)).toBe('prekinjena')
  })
})

describe('napake mreže (F6.6)', () => {
  it('delež od števila in zamenjave števke', () => {
    expect(delezOd(666, 693)).toBe('96,1 %')
    expect(delezOd(0, 0)).toBe('–')
    expect(zamenjaveBesedilo([{ prebrano: 1, krat: 4 }, { prebrano: 9, krat: 2 }])).toBe('1 ×4, 9 ×2')
    expect(zamenjaveBesedilo([])).toBe('–')
  })

  it('izločena števka izgine s seznama, v katerem je, in zmanjša njegov števec', () => {
    const stevka = (id: number) => ({
      id, vrednost: 7, prebrano: 1, verjetnost: 0.95, slika: '', idVadnica: 5, listek: false,
    })
    const p: NapakeMrezeDto = {
      vUporabi: 1, preizkusnih: 10, ucnih: 40, poStevkah: [],
      napakVseh: 3, najvecNaStevko: 60, napake: [stevka(1), stevka(2)],
      drugacnihUcnih: 5, pragSumljive: 0.9, sumljive: [stevka(3)],
    }
    const brezNapake = brezStevke(p, 2)
    expect(brezNapake.napake.map((s) => s.id)).toEqual([1])
    expect(brezNapake.napakVseh).toBe(2)
    expect(brezNapake.drugacnihUcnih).toBe(5)
    const brezSumljive = brezStevke(p, 3)
    expect(brezSumljive.sumljive).toEqual([])
    expect(brezSumljive.drugacnihUcnih).toBe(4)
    expect(brezSumljive.napakVseh).toBe(3)
    expect(brezStevke(p, 99)).toEqual(p)
  })
})
