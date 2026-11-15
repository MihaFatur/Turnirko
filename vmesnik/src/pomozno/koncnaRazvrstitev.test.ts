import { describe, expect, it } from 'vitest'

import type { SkupinaDto } from '../api/tipi'
import { mreza, prijava, tekma } from '../testi/tovarna'
import {
  bilancePrijav,
  listiOstalih,
  oznakaMesta,
  podnapisPrijave,
  razdeliNaStopnice,
  razdeliVNivoje,
  razvrsceniUdelezenci,
  steviloNaMestu,
  udelezenciRazvrstitve,
  type UdelezenecRazvrstitve,
} from './koncnaRazvrstitev'

function udelezenec(id: number, mesto: number, oznaka = `${mesto}.`): UdelezenecRazvrstitve {
  return { id, mesto, oznaka, ime: 'Igralec', priimek: String(id), klub: '', izid: '' }
}

const skupina = (id: number, oznaka: string, stopnja = 1) => ({ id, oznaka, stopnja }) as SkupinaDto

/* Izločilna mreža z 2^kol igralci, odigrana do konca; vedno zmaga nižji id.
   Kolo 1 je prvo, zadnje je finale. */
function odigranaMreza(igralcev: number) {
  const tekme = []
  let zivi = Array.from({ length: igralcev }, (_, i) => i + 1)
  let id = 1
  for (let kolo = 1; zivi.length > 1; kolo++) {
    const naprej: number[] = []
    for (let i = 0; i < zivi.length / 2; i++) {
      const [a, b] = [zivi[i], zivi[zivi.length - 1 - i]]
      tekme.push(tekma(id++, a, b, { kolo }))
      naprej.push(a)
    }
    zivi = naprej
  }
  return tekme
}

describe('oznaka mesta', () => {
  it('deljeno mesto je razpon', () => {
    const deljena = new Map([[3, 2], [5, 4]])
    expect(oznakaMesta(1, deljena, false)).toBe('1.')
    expect(oznakaMesta(3, deljena, false)).toBe('3.–4.')
    expect(oznakaMesta(5, deljena, false)).toBe('5.–8.')
    expect(oznakaMesta(5, deljena, true)).toBe('5–8')
  })

  it('štetje udeležencev na mestu izpusti prijave brez mesta', () => {
    const prijave = [prijava(1, { koncnoMesto: 3 }), prijava(2, { koncnoMesto: 3 }), prijava(3)]
    expect([...steviloNaMestu(prijave).entries()]).toEqual([[3, 2]])
  })
})

describe('stopničke', () => {
  it('s tekmo za tretje mesto so na vrhu trije', () => {
    const { vrh, ostali } = razdeliNaStopnice([1, 2, 3, 4, 5].map((m, i) => udelezenec(i, m)))
    expect(vrh.map((u) => u.mesto)).toEqual([1, 2, 3])
    expect(ostali.map((u) => u.mesto)).toEqual([4, 5])
  })

  it('brez nje sta poraženca polfinala oba tretja: štirje', () => {
    const { vrh, ostali } = razdeliNaStopnice([1, 2, 3, 3, 5, 5, 5, 5].map((m, i) => udelezenec(i, m)))
    expect(vrh).toHaveLength(4)
    expect(ostali).toHaveLength(4)
  })

  it('stara uvožena zgodovina (»3.–91.«) na stopničke postavi samo prva dva', () => {
    const mesta = [1, 2, ...Array.from({ length: 10 }, () => 3)]
    const { vrh, ostali } = razdeliNaStopnice(mesta.map((m, i) => udelezenec(i, m)))
    expect(vrh.map((u) => u.mesto)).toEqual([1, 2])
    expect(ostali).toHaveLength(10)
  })
})

describe('nivoji in listi za tisk', () => {
  const ostali = [
    udelezenec(1, 5, '5.–8.'),
    udelezenec(2, 5, '5.–8.'),
    udelezenec(3, 5, '5.–8.'),
    udelezenec(4, 5, '5.–8.'),
    udelezenec(5, 9, '9.'),
  ]

  it('zaporedni z istim mestom so en nivo', () => {
    const nivoji = razdeliVNivoje(ostali)
    expect(nivoji.map((n) => [n.oznaka, n.clani.length])).toEqual([['5.–8.', 4], ['9.', 1]])
    expect(razdeliVNivoje([])).toEqual([])
  })

  it('zadnji list je razpolovljen, levi stolpec ni krajši od desnega', () => {
    const [list] = listiOstalih(ostali, () => 10, 2)
    expect(list.map((stolpec) => stolpec.length)).toEqual([3, 2])
  })

  it('oznaka nivoja stoji tudi v prvi vrstici vsakega stolpca', () => {
    const [[levi, desni]] = listiOstalih(ostali, () => 10, 2)
    expect(levi.map((v) => v.oznaka)).toEqual(['5.–8.', '', ''])
    expect(levi.map((v) => v.rob)).toEqual(['brez', 'pika', 'pika'])
    expect(desni.map((v) => v.oznaka)).toEqual(['5.–8.', '9.'])
    expect(desni.map((v) => v.rob)).toEqual(['brez', 'nivo'])
  })

  it('poln list teče do dna stolpca, šele nato naslednji list', () => {
    const veliko = Array.from({ length: 25 }, (_, i) => udelezenec(i, i + 4))
    const listi = listiOstalih(veliko, () => 10, 2)
    expect(listi.map((list) => list.map((stolpec) => stolpec.length))).toEqual([[10, 10], [3, 2]])
    /* Zaporedje se bere levo navzdol, desno navzdol, naslednji list. */
    expect(listi.flat(2).map((v) => v.udelezenec.id)).toEqual(veliko.map((u) => u.id))
  })

  it('prazen seznam nima listov', () => {
    expect(listiOstalih([], () => 10, 2)).toEqual([])
  })
})

describe('razvrščeni udeleženci', () => {
  it('izločilni sistem: strežnik zapiše prve štiri, ostale izpelje kolo izpada', () => {
    const prijave = Array.from({ length: 16 }, (_, i) =>
      prijava(i + 1, { koncnoMesto: i < 2 ? i + 1 : i < 4 ? 3 : null, stNosilca: i + 1 }),
    )
    const razvrscene = razvrsceniUdelezenci(mreza({ status: 'ZAKLJUCEN' }, prijave, odigranaMreza(16)))
    expect(razvrscene.map((p) => p.koncnoMesto)).toEqual([1, 2, 3, 3, 5, 5, 5, 5, 9, 9, 9, 9, 9, 9, 9, 9])
    /* Izpadli v četrtfinalu (5.–8.) so igralci 5 do 8, znotraj razreda po nosilcu. */
    expect(razvrscene.slice(4, 8).map((p) => p.id)).toEqual([5, 6, 7, 8])
  })

  it('izpeljana mesta dobijo oznako razpona', () => {
    const prijave = Array.from({ length: 8 }, (_, i) =>
      prijava(i + 1, { koncnoMesto: i < 2 ? i + 1 : i < 4 ? 3 : null }),
    )
    const udelezenci = udelezenciRazvrstitve(mreza({ status: 'ZAKLJUCEN' }, prijave, odigranaMreza(8)))
    expect(udelezenci.map((u) => u.oznaka)).toEqual(['1.', '2.', '3.–4.', '3.–4.', '5.–8.', '5.–8.', '5.–8.', '5.–8.'])
    /* Izkupiček je samo pri krožnem sistemu. */
    expect(udelezenci.every((u) => u.izid === '')).toBe(true)
  })

  it('odjavljeni in rezerve niso nastopili; odstopljeni brez tekme je zadnji', () => {
    const prijave = [
      prijava(1, { koncnoMesto: 1 }),
      prijava(2, { koncnoMesto: 2 }),
      prijava(3, { status: 'ODJAVLJEN' }),
      prijava(4, { status: 'REZERVA' }),
      prijava(5, { status: 'ODSTOPIL' }),
    ]
    const razvrscene = razvrsceniUdelezenci(mreza({}, prijave, [tekma(1, 1, 2)]))
    expect(razvrscene.map((p) => [p.id, p.koncnoMesto])).toEqual([[1, 1], [2, 2], [5, 3]])
  })

  it('zapisano mesto je zadnja beseda, tudi pri odjavljenem iz uvoza', () => {
    const prijave = [prijava(1, { koncnoMesto: 2 }), prijava(2, { koncnoMesto: 1, status: 'ODJAVLJEN' })]
    expect(razvrsceniUdelezenci(mreza({}, prijave)).map((p) => p.id)).toEqual([2, 1])
  })

  it('SV regija izpeljave ne potrebuje: brez zapisanega mesta prijave ni v razvrstitvi', () => {
    const prijave = [prijava(1, { koncnoMesto: 2 }), prijava(2, { koncnoMesto: 1 }), prijava(3)]
    const razvrscene = razvrsceniUdelezenci(mreza({ sistemTekmovanja: 'SV_REGIJA' }, prijave))
    expect(razvrscene.map((p) => p.id)).toEqual([2, 1])
  })

  it('format TOP: skupine so rangi (A pred B), v skupini mesto v skupini', () => {
    const prijave = [
      prijava(1, { idSkupina: 20, mestoVSkupini: 1 }),
      prijava(2, { idSkupina: 10, mestoVSkupini: 2 }),
      prijava(3, { idSkupina: 10, mestoVSkupini: 1 }),
      prijava(4, { idSkupina: 20, mestoVSkupini: 2 }),
    ]
    const podatki = mreza({ sistemTekmovanja: 'SKUPINE' }, prijave, [], {
      skupine: [skupina(20, 'B'), skupina(10, 'A')],
    })
    expect(razvrsceniUdelezenci(podatki).map((p) => [p.id, p.koncnoMesto])).toEqual([[3, 1], [2, 2], [1, 3], [4, 4]])
  })

  it('izpad v skupini pred izločilnim delom: isto mesto v skupini si deli razpon', () => {
    const prijave = [
      prijava(1, { koncnoMesto: 1 }),
      prijava(2, { koncnoMesto: 2 }),
      prijava(3, { idSkupina: 10, mestoVSkupini: 3 }),
      prijava(4, { idSkupina: 20, mestoVSkupini: 3 }),
      prijava(5, { idSkupina: 10, mestoVSkupini: 4 }),
    ]
    const podatki = mreza({ sistemTekmovanja: 'SKUPINE_IZLOCILNI' }, prijave, [tekma(1, 1, 2)])
    expect(razvrsceniUdelezenci(podatki).map((p) => [p.id, p.koncnoMesto])).toEqual([
      [1, 1], [2, 2], [3, 3], [4, 3], [5, 5],
    ])
  })
})

describe('bilanca prijav', () => {
  it('zmage, porazi in vsota sprememb ratinga', () => {
    const tekme = [
      tekma(1, 1, 2, { spremembaElo1: 10, spremembaElo2: -10 }),
      tekma(2, 1, 3, { idZmagovalcaPrijave: 3, spremembaElo1: -4, spremembaElo2: 4 }),
    ]
    const bilance = bilancePrijav(tekme)
    expect(bilance.get(1)).toEqual({ zmage: 1, porazi: 1, rating: 6 })
    expect(bilance.get(2)).toEqual({ zmage: 0, porazi: 1, rating: -10 })
    expect(bilance.get(3)).toEqual({ zmage: 1, porazi: 0, rating: 4 })
  })

  it('prosti prehod in neodigrana tekma ne štejeta', () => {
    const tekme = [
      tekma(1, 1, null, { izidTip: 'PROSTO' }),
      tekma(2, 1, 2, { status: 'CAKA', idZmagovalcaPrijave: null }),
    ]
    expect(bilancePrijav(tekme).size).toBe(0)
  })

  it('brez obračuna ratinga je sprememba null, ne 0', () => {
    expect(bilancePrijav([tekma(1, 1, 2)]).get(1)).toEqual({ zmage: 1, porazi: 0, rating: null })
  })

  it('krožni sistem izpiše izkupiček, brez tekem pomišljaj', () => {
    const prijave = [prijava(1, { koncnoMesto: 1 }), prijava(2, { koncnoMesto: 2 }), prijava(3, { koncnoMesto: 3 })]
    const udelezenci = udelezenciRazvrstitve(mreza({ sistemTekmovanja: 'KROZNI' }, prijave, [tekma(1, 1, 2)]))
    expect(udelezenci.map((u) => u.izid)).toEqual(['1 – 0', '0 – 1', '—'])
  })
})

describe('podnapis prijave', () => {
  it('igralec: kratek klub ali »brez kluba«', () => {
    expect(podnapisPrijave(prijava(1, { klub: 'NAMIZNOTENIŠKI KLUB ŽALEC' }), false)).toBe('Žalec')
    expect(podnapisPrijave(prijava(1), false)).toBe('brez kluba')
  })

  it('ekipa: klub samo, kadar ga ime ne pove že samo', () => {
    expect(podnapisPrijave(prijava(1, { polnoIme: 'NTK Žalec 2', klub: 'NTK Žalec' }), true)).toBeNull()
    expect(podnapisPrijave(prijava(1, { polnoIme: 'Veterani', klub: 'NTK Žalec' }), true)).toBe('NTK Žalec')
    expect(podnapisPrijave(prijava(1, { polnoIme: 'Prijatelji' }), true)).toBe('prosta ekipa')
  })
})
