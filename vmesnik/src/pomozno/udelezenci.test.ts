import { describe, expect, it } from 'vitest'

import { mreza, prijava, tekma } from '../testi/tovarna'
import {
  fazaUdelezencev,
  metaUdelezencev,
  moznostiKluba,
  moznostiRazvrstitve,
  naslovUdelezencev,
  odstotekPalice,
  opombaUdelezencev,
  sestaviPrikaz,
  udelezenciDogodka,
  vrstaUdelezencev,
} from './udelezenci'

const stanja = (podatki: ReturnType<typeof mreza>) =>
  Object.fromEntries(udelezenciDogodka(podatki).vrstice.map((v) => [v.id, v.stanje]))

describe('faza in vrsta', () => {
  it('faza gleda DEJANSKI status: žreb pred datumom je gledalcu »V pripravi«, a že velja', () => {
    expect(fazaUdelezencev(mreza({ status: 'PRIPRAVA', dejanskiStatus: 'V_TEKU' }, []))).toBe('v_teku')
    expect(fazaUdelezencev(mreza({ status: 'PRIPRAVA', dejanskiStatus: 'PRIPRAVA' }, []))).toBe('priprava')
    expect(fazaUdelezencev(mreza({ status: 'ZAKLJUCEN', dejanskiStatus: 'ZAKLJUCEN' }, []))).toBe('zakljucen')
  })

  it('vrsta po disciplini', () => {
    expect(vrstaUdelezencev(mreza({ disciplina: 'POSAMICNO' }, []))).toBe('posamezniki')
    expect(vrstaUdelezencev(mreza({ disciplina: 'DVOJICE' }, []))).toBe('dvojice')
    expect(vrstaUdelezencev(mreza({ disciplina: 'EKIPNO' }, []))).toBe('ekipe')
  })

  it('naslov pogleda', () => {
    expect(naslovUdelezencev('ekipe', 'v_teku')).toBe('Ekipe')
    expect(naslovUdelezencev('dvojice', 'priprava')).toBe('Pari')
    expect(naslovUdelezencev('dvojice', 'v_teku')).toBe('Udeleženci')
  })
})

describe('palica ratinga', () => {
  it('je omejena na 0–100 in prazna brez ratinga', () => {
    expect(odstotekPalice(null)).toBe(0)
    expect(odstotekPalice(1000)).toBe(0)
    expect(odstotekPalice(1400)).toBe(0)
    expect(odstotekPalice(1800)).toBe(50)
    expect(odstotekPalice(2200)).toBe(100)
    expect(odstotekPalice(3000)).toBe(100)
  })
})

describe('stanje med turnirjem (izločilna mreža)', () => {
  const stirje = () => [1, 2, 3, 4].map((id) => prijava(id, { stNosilca: id }))

  it('poraženec je izpadel, zmagovalec in čakajoči so v igri', () => {
    const tekme = [
      tekma(1, 1, 4),
      tekma(2, 2, 3, { status: 'CAKA', idZmagovalcaPrijave: null }),
      tekma(3, 1, null, { kolo: 2, status: 'CAKA', idZmagovalcaPrijave: null }),
    ]
    const dogodek = udelezenciDogodka(mreza({}, stirje(), tekme))
    expect(dogodek.imaIzpad).toBe(true)
    expect(stanja(mreza({}, stirje(), tekme))).toEqual({ 1: 'v_igri', 2: 'v_igri', 3: 'v_igri', 4: 'izpadel' })
  })

  it('prvak po finalu ostane »v igri« do zaključka dogodka', () => {
    const tekme = [tekma(1, 1, 4), tekma(2, 2, 3), tekma(3, 1, 2, { kolo: 2 })]
    expect(stanja(mreza({}, stirje(), tekme))).toEqual({ 1: 'v_igri', 2: 'izpadel', 3: 'izpadel', 4: 'izpadel' })
  })

  it('poraženec polfinala, ki ga čaka tekma za 3. mesto, je še v igri', () => {
    const tekme = [
      tekma(1, 1, 4),
      tekma(2, 2, 3),
      tekma(3, 1, 2, { kolo: 2, status: 'CAKA', idZmagovalcaPrijave: null }),
      tekma(4, 4, 3, { kolo: 2, faza: 'TOLAZILNI', status: 'CAKA', idZmagovalcaPrijave: null }),
    ]
    expect(stanja(mreza({}, stirje(), tekme))).toEqual({ 1: 'v_igri', 2: 'v_igri', 3: 'v_igri', 4: 'v_igri' })
  })

  it('prosti prehod ni poraz', () => {
    const prijave = [1, 2, 3].map((id) => prijava(id, { stNosilca: id }))
    const tekme = [
      tekma(1, 1, null, { izidTip: 'PROSTO' }),
      tekma(2, 2, 3, { status: 'CAKA', idZmagovalcaPrijave: null }),
    ]
    expect(stanja(mreza({}, prijave, tekme))[1]).toBe('v_igri')
  })

  it('odstop in izključitev prevladata nad tekmami', () => {
    const prijave = [
      prijava(1, { stNosilca: 1 }),
      prijava(2, { stNosilca: 2, status: 'ODSTOPIL' }),
      prijava(3, { stNosilca: 3, status: 'DISKVALIFICIRAN' }),
    ]
    const tekme = [tekma(1, 2, 3, { status: 'CAKA', idZmagovalcaPrijave: null })]
    expect(stanja(mreza({}, prijave, tekme))).toMatchObject({ 2: 'odstopil', 3: 'izpadel' })
  })
})

describe('stanje pri sistemih brez izpada', () => {
  it('krožni sistem stanj ne pozna', () => {
    const dogodek = udelezenciDogodka(
      mreza({ sistemTekmovanja: 'KROZNI' }, [prijava(1), prijava(2)], [tekma(1, 1, 2, { faza: 'SKUPINA' })]),
    )
    expect(dogodek.imaIzpad).toBe(false)
    expect(dogodek.vrstice.map((v) => v.stanje)).toEqual([null, null])
  })

  it('skupine z izločilnim delom: izpad velja šele, ko mreža obstaja', () => {
    const prijave = [prijava(1), prijava(2), prijava(3)]
    const skupine = [tekma(1, 1, 3, { faza: 'SKUPINA' })]
    const brezMreze = udelezenciDogodka(mreza({ sistemTekmovanja: 'SKUPINE_IZLOCILNI' }, prijave, skupine))
    expect(brezMreze.imaIzpad).toBe(false)

    const zMrezo = mreza({ sistemTekmovanja: 'SKUPINE_IZLOCILNI' }, prijave, [
      ...skupine,
      tekma(2, 1, 2, { status: 'CAKA', idZmagovalcaPrijave: null }),
    ])
    /* Kdor po skupinah v mrežo ni prišel, je izpadel. */
    expect(stanja(zMrezo)).toEqual({ 1: 'v_igri', 2: 'v_igri', 3: 'izpadel' })
  })
})

describe('jakostni vrstni red', () => {
  it('po žrebu velja strežnikova številka nosilca', () => {
    const prijave = [prijava(1, { stNosilca: 3 }), prijava(2, { stNosilca: 1 }), prijava(3, { stNosilca: 2 })]
    const vrstice = udelezenciDogodka(mreza({ sistemTekmovanja: 'KROZNI' }, prijave)).vrstice
    expect(vrstice.map((v) => [v.id, v.mesto])).toEqual([[2, 1], [3, 2], [1, 3]])
  })

  it('brez nosilcev po ratingu ob žrebu, sicer trenutnem; brez ratinga na dnu, nato abeceda', () => {
    const prijave = [
      prijava(1, { polnoIme: 'Žiga Zajc' }),
      prijava(2, { polnoIme: 'Ana Čuk' }),
      prijava(3, { rating: 1500 }),
      prijava(4, { rating: 1900, ratingObZrebu: 1200 }),
    ]
    const vrstice = udelezenciDogodka(mreza({ sistemTekmovanja: 'KROZNI' }, prijave)).vrstice
    expect(vrstice.map((v) => v.id)).toEqual([3, 4, 2, 1])
    expect(vrstice.map((v) => v.mesto)).toEqual([1, 2, 3, 4])
  })

  it('v pripravi odstopljeni in rezerve niso med udeleženci', () => {
    const prijave = [prijava(1), prijava(2, { status: 'ODJAVLJEN' }), prijava(3, { status: 'REZERVA' })]
    const dogodek = udelezenciDogodka(mreza({ dejanskiStatus: 'PRIPRAVA' }, prijave))
    expect(dogodek.vrstice.map((v) => v.id)).toEqual([1])
  })
})

describe('dvojice', () => {
  const par = (id: number, polja = {}) =>
    prijava(id, { polnoIme2: `Soigralec ${id}`, ime2: 'Soigralec', priimek2: String(id), ...polja })

  it('v pripravi gre v žreb samo sestavljen par, ostali so v dodatku', () => {
    const podatki = mreza({ disciplina: 'DVOJICE', dejanskiStatus: 'PRIPRAVA' }, [par(1), prijava(2)])
    const dogodek = udelezenciDogodka(podatki)
    expect(dogodek.vrstice.map((v) => v.id)).toEqual([1])
    expect(dogodek.dodatek?.naslov).toBe('Brez soigralca (1)')
  })

  it('rating para je povprečje; z enim neznanim ga ni', () => {
    const podatki = mreza({ disciplina: 'DVOJICE', sistemTekmovanja: 'KROZNI' }, [
      par(1, { rating: 1500, rating2: 1700 }),
      par(2, { rating: 1900, rating2: null }),
    ])
    const [prvi, drugi] = udelezenciDogodka(podatki).vrstice
    expect(prvi).toMatchObject({ id: 1, rating: '1500 / 1700', ratingStevilo: 1600 })
    expect(drugi).toMatchObject({ id: 2, rating: '1900 / —', ratingStevilo: null })
  })

  it('klub para brez ponovitve', () => {
    const podatki = mreza({ disciplina: 'DVOJICE', sistemTekmovanja: 'KROZNI' }, [
      par(1, { klub: 'NTK Žalec', klub2: 'NTK Žalec' }),
      par(2, { klub: 'NTK Žalec', klub2: null }),
    ])
    const vrstice = udelezenciDogodka(podatki).vrstice
    expect(vrstice.find((v) => v.id === 1)?.klub).toBe('NTK Žalec')
    expect(vrstice.find((v) => v.id === 2)?.klub).toBe('NTK Žalec · brez kluba')
  })
})

describe('zaključen dogodek', () => {
  it('vrstica nosi končno mesto z oznako razpona', () => {
    const prijave = [
      prijava(1, { stNosilca: 1, koncnoMesto: 2 }),
      prijava(2, { stNosilca: 2, koncnoMesto: 1 }),
      prijava(3, { stNosilca: 3, koncnoMesto: 3 }),
      prijava(4, { stNosilca: 4, koncnoMesto: 3 }),
    ]
    const dogodek = udelezenciDogodka(mreza({ dejanskiStatus: 'ZAKLJUCEN' }, prijave))
    /* Razporeditev ostane po nosilcih. */
    expect(dogodek.vrstice.map((v) => [v.mesto, v.koncnaOznaka])).toEqual([
      [1, '2.'], [2, '1.'], [3, '3.–4.'], [4, '3.–4.'],
    ])
  })
})

describe('besedila in meniji', () => {
  const vrstice = udelezenciDogodka(
    mreza({ sistemTekmovanja: 'KROZNI' }, [
      prijava(1, { klub: 'NTK Žalec' }),
      prijava(2, { klub: 'NTK Celje' }),
      prijava(3, { klub: 'NTK Žalec' }),
    ]),
  ).vrstice

  it('meta: število prijavljenih in klubov', () => {
    expect(metaUdelezencev('posamezniki', vrstice)).toBe('3 prijavljeni · 2 kluba')
    expect(metaUdelezencev('ekipe', vrstice.slice(0, 1))).toBe('1 ekipa · 1 klub')
  })

  it('meni klubov po abecedi s števci', () => {
    expect(moznostiKluba(vrstice)).toEqual([
      { vrednost: null, ime: 'Vsi klubi', stevilo: '3' },
      { vrednost: 'NTK Celje', ime: 'NTK Celje', stevilo: '1' },
      { vrednost: 'NTK Žalec', ime: 'NTK Žalec', stevilo: '2' },
    ])
  })

  it('razvrstitev po uvrstitvi obstaja samo pri zaključenem dogodku', () => {
    expect(moznostiRazvrstitve('v_teku').map((m) => m.vrednost)).toEqual(['jakost', 'priimek', 'klub'])
    expect(moznostiRazvrstitve('zakljucen').map((m) => m.vrednost)).toEqual([
      'jakost', 'uvrstitev', 'priimek', 'klub',
    ])
  })

  it('opomba pove, koliko jih je še v igri', () => {
    expect(opombaUdelezencev('v_teku', vrstice, false)).toContain('Številka je nosilec')
    const zIzpadom = vrstice.map((v, i) => ({ ...v, stanje: i === 0 ? ('v_igri' as const) : ('izpadel' as const) }))
    expect(opombaUdelezencev('v_teku', zIzpadom, true)).toBe('V igri 1 od 3. Izpadli so zbledeli.')
  })
})

describe('prikaz: kartice, skupine nosilcev in filtri', () => {
  const vrstice = (n: number) =>
    udelezenciDogodka(
      mreza(
        { sistemTekmovanja: 'KROZNI' },
        Array.from({ length: n }, (_, i) =>
          prijava(i + 1, {
            stNosilca: i + 1,
            priimek: `Priimek${String(n - i).padStart(2, '0')}`,
            klub: i % 2 === 0 ? 'NTK Žalec' : 'NTK Celje',
            rating: 2000 - i * 10,
          }),
        ),
      ),
    ).vrstice
  const brezFiltrov = { iskanje: '', klub: null, razvrstitev: 'jakost' as const }

  it('cel seznam po jakosti: prvi štirje so kartice, ostali skupine nosilcev', () => {
    const prikaz = sestaviPrikaz(vrstice(20), brezFiltrov, false)
    expect(prikaz.karte.map((k) => [k.naslov, k.vrstice.length])).toEqual([
      ['Nosilca 1 in 2', 2],
      ['Nosilca 3 in 4', 2],
    ])
    expect(prikaz.skupine.map((s) => [s.naslov, s.stolpci.map((stolpec) => stolpec.length)])).toEqual([
      ['Nosilci 5–8', [2, 2]],
      ['Nosilci 9–16', [4, 4]],
      ['Nosilci od 17 naprej', [2, 2]],
    ])
    expect(prikaz.skupine[0].razpon).toBe('rating 1960 – 1930')
    expect(prikaz.prikazanih).toBe(20)
  })

  it('naslov skupine se skrajša na število udeležencev', () => {
    expect(sestaviPrikaz(vrstice(6), brezFiltrov, false).skupine.map((s) => s.naslov)).toEqual(['Nosilci 5–6'])
    expect(sestaviPrikaz(vrstice(5), brezFiltrov, false).skupine.map((s) => s.naslov)).toEqual(['Nosilec 5'])
    expect(sestaviPrikaz(vrstice(3), brezFiltrov, false).karte.map((k) => k.naslov)).toEqual([
      'Nosilca 1 in 2',
      'Nosilec 3',
    ])
  })

  it('ob filtru kartic ni: četverica bi lagala', () => {
    const prikaz = sestaviPrikaz(vrstice(20), { ...brezFiltrov, klub: 'NTK Žalec' }, false)
    expect(prikaz.karte).toEqual([])
    expect(prikaz.prikazanih).toBe(10)
    expect(prikaz.skupine[0].naslov).toBe('Nosilca 1 in 2')
  })

  it('iskanje po imenu ne loči velikih črk', () => {
    const prikaz = sestaviPrikaz(vrstice(20), { ...brezFiltrov, iskanje: '  IGRALEC 7 ' }, false)
    expect(prikaz.prikazanih).toBe(1)
  })

  it('razvrstitev po priimku je ena tabela brez naslova', () => {
    const prikaz = sestaviPrikaz(vrstice(6), { ...brezFiltrov, razvrstitev: 'priimek' }, false)
    expect(prikaz.karte).toEqual([])
    expect(prikaz.skupine).toHaveLength(1)
    expect(prikaz.skupine[0].naslov).toBeNull()
    expect(prikaz.skupine[0].stolpci[0].map((v) => v.id)).toEqual([6, 5, 4, 3, 2, 1])
  })

  it('nad 24 vrsticami se tabela razdeli v dva stolpca', () => {
    const po = (n: number) =>
      sestaviPrikaz(vrstice(n), { ...brezFiltrov, razvrstitev: 'klub' }, false).skupine[0].stolpci.map(
        (stolpec) => stolpec.length,
      )
    expect(po(24)).toEqual([24])
    expect(po(25)).toEqual([13, 12])
  })

  it('»V igri 1 / 4« namesto razpona ratinga', () => {
    const zIzpadom = vrstice(8).map((v) => ({ ...v, stanje: v.mesto === 5 ? ('v_igri' as const) : ('izpadel' as const) }))
    expect(sestaviPrikaz(zIzpadom, brezFiltrov, true).skupine[0].razpon).toBe('V igri 1 / 4')
  })

  it('prazen seznam nima skupin', () => {
    expect(sestaviPrikaz([], { ...brezFiltrov, razvrstitev: 'priimek' }, false)).toEqual({
      karte: [],
      skupine: [],
      prikazanih: 0,
    })
  })
})
