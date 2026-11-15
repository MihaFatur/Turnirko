import { afterEach, describe, expect, it, vi } from 'vitest'

import {
  danesIso,
  datumskiBlok,
  dniMed,
  imeKola,
  imeKolaKratko,
  imeLestvice,
  jeStarejsiOd21ZaCeno,
  kratekKlub,
  ligaVMestniku,
  ligaVTozilniku,
  mejaSvezine,
  oblikujCeno,
  oblikujDatum,
  oblikujObdobje,
  oblikujObdobjeKratko,
  oblikujStevilo,
  oblikujTermin,
  oblikujUro,
  oznakaMeseca,
  potekKol,
  prvaCrkaVelika,
  sezonaIzDatuma,
  sklonDni,
  sklonIgralcev,
  sklonListkov,
  sklonMesecih,
  sklonTekem,
  zVelikoZacetnico,
} from './oblikovanje'

afterEach(() => {
  vi.useRealTimers()
})

describe('sklanjanje po številu', () => {
  /* docs/pravila/vmesnik.md: posebno obliko imajo samo števila, ki se pri 100
     končajo na 1, 2, 3 ali 4. Do oktobra 2026 je »64« pisalo »64 igralci«. */
  it.each([
    [0, 'igralcev'],
    [1, 'igralec'],
    [2, 'igralca'],
    [3, 'igralci'],
    [4, 'igralci'],
    [5, 'igralcev'],
    [11, 'igralcev'],
    [12, 'igralcev'],
    [13, 'igralcev'],
    [14, 'igralcev'],
    [21, 'igralcev'],
    [22, 'igralcev'],
    [24, 'igralcev'],
    [64, 'igralcev'],
    [100, 'igralcev'],
    [101, 'igralec'],
    [102, 'igralca'],
    [103, 'igralci'],
    [104, 'igralci'],
    [111, 'igralcev'],
    [1001, 'igralec'],
  ])('%i → %s', (n, oblika) => {
    expect(sklonIgralcev(n)).toBe(oblika)
  })

  it('tekme in listki imajo svojo kopijo istega pravila', () => {
    expect([1, 2, 3, 5, 21, 64, 101].map(sklonTekem)).toEqual([
      'tekma', 'tekmi', 'tekme', 'tekem', 'tekem', 'tekem', 'tekma',
    ])
    expect([1, 2, 4, 5, 22, 102].map(sklonListkov)).toEqual([
      'listek', 'listka', 'listki', 'listkov', 'listkov', 'listka',
    ])
  })

  it('dnevi: edina izjema je natanko ena', () => {
    expect([1, 2, 5, 21, 31].map(sklonDni)).toEqual(['dan', 'dni', 'dni', 'dni', 'dni'])
  })

  it('mestnik mesecev', () => {
    expect([1, 2, 9, 101].map(sklonMesecih)).toEqual(['mesecu', 'mesecih', 'mesecih', 'mesecu'])
  })

  it('potek kol: glavni števnik, ne vrstilni', () => {
    expect(potekKol(1, 6)).toBe('odigrano 1 od 6 kol')
    expect(potekKol(0, 6)).toBe('odigrano 0 od 6 kol')
    expect(potekKol(1, 1)).toBe('odigrano 1 od 1 kola')
  })
})

describe('sezona iz datuma', () => {
  it('rez je 1. julij', () => {
    expect(sezonaIzDatuma('2025-10-04')).toBe('2025/26')
    expect(sezonaIzDatuma('2026-04-19')).toBe('2025/26')
    expect(sezonaIzDatuma('2026-06-30')).toBe('2025/26')
    expect(sezonaIzDatuma('2026-07-01')).toBe('2026/27')
  })

  it('prelom stoletja ohrani dve števki', () => {
    expect(sezonaIzDatuma('1999-09-01')).toBe('1999/00')
    expect(sezonaIzDatuma('2009-01-15')).toBe('2008/09')
  })

  it('brez berljivega datuma sezone ni', () => {
    expect(sezonaIzDatuma(null)).toBeNull()
    expect(sezonaIzDatuma(undefined)).toBeNull()
    expect(sezonaIzDatuma('')).toBeNull()
    expect(sezonaIzDatuma('ni-datum')).toBeNull()
  })
})

describe('meja svežine (tri mesece nazaj)', () => {
  it('isti dan tri mesece prej', () => {
    expect(mejaSvezine('2026-11-13')).toBe('2026-08-13')
  })

  it('če mesec tega dne nima, vzame njegov zadnji dan', () => {
    expect(mejaSvezine('2026-12-31')).toBe('2026-09-30')
    expect(mejaSvezine('2026-05-31')).toBe('2026-02-28')
    expect(mejaSvezine('2024-05-30')).toBe('2024-02-29')
  })

  it('seže v prejšnje leto', () => {
    expect(mejaSvezine('2026-02-15')).toBe('2025-11-15')
    expect(mejaSvezine('2026-03-10')).toBe('2025-12-10')
  })
})

describe('dnevi med datumoma', () => {
  it('prehod na poletni in zimski čas ne premakne dneva', () => {
    expect(dniMed('2026-03-28', '2026-03-30')).toBe(2)
    expect(dniMed('2026-10-24', '2026-10-26')).toBe(2)
  })

  it('čez novo leto, nazaj in isti dan', () => {
    expect(dniMed('2026-12-31', '2027-01-01')).toBe(1)
    expect(dniMed('2027-01-01', '2026-12-31')).toBe(-1)
    expect(dniMed('2026-05-05', '2026-05-05')).toBe(0)
  })

  it('neberljiv datum da null', () => {
    expect(dniMed(null, '2026-01-01')).toBeNull()
    expect(dniMed('2026-01-01', undefined)).toBeNull()
    expect(dniMed('2026-01-01', 'jutri')).toBeNull()
  })
})

describe('danes in starostni rez za ceno', () => {
  it('današnji dan je po uri naprave, ne po UTC', () => {
    /* 00.30 v Ljubljani je v UTC še prejšnji dan. */
    vi.useFakeTimers()
    vi.setSystemTime(new Date(2026, 8, 25, 0, 30))
    expect(danesIso()).toBe('2026-09-25')
  })

  it('starost se meri na 31. 12. leta, v katerem se sezona začne', () => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date(2026, 10, 13, 12))
    /* Sezona 2026/27: letnik 2005 dopolni 21 let v letu 2026. */
    expect(jeStarejsiOd21ZaCeno('2005-12-31')).toBe(true)
    expect(jeStarejsiOd21ZaCeno('2006-01-01')).toBe(false)
  })

  it('30. junija velja še prejšnja sezona, 1. julija nova', () => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date(2026, 5, 30, 12))
    expect(jeStarejsiOd21ZaCeno('2005-03-01')).toBe(false)
    expect(jeStarejsiOd21ZaCeno('2004-03-01')).toBe(true)
    vi.setSystemTime(new Date(2026, 6, 1, 12))
    expect(jeStarejsiOd21ZaCeno('2005-03-01')).toBe(true)
  })

  it('brez datuma rojstva ni odgovora', () => {
    expect(jeStarejsiOd21ZaCeno(null)).toBeNull()
    expect(jeStarejsiOd21ZaCeno('')).toBeNull()
  })
})

describe('datumi in termini', () => {
  it('datum brez vodilnih ničel', () => {
    expect(oblikujDatum('2026-04-12')).toBe('12. 4. 2026')
    expect(oblikujDatum(null)).toBe('')
    expect(oblikujDatum('abc')).toBe('abc')
  })

  it('termin: dan v tednu spredaj, ura 00:00 pomeni »ni vpisana«', () => {
    expect(oblikujTermin('2026-10-11T18:00:00')).toBe('ned, 11. okt · 18.00')
    expect(oblikujTermin('2026-10-11T00:00:00')).toBe('ned, 11. okt')
    expect(oblikujTermin('2026-10-11')).toBe('ned, 11. okt')
    expect(oblikujTermin(null)).toBe('')
    expect(oblikujTermin('ni-datum')).toBe('')
  })

  it('termin na dan prehoda ure ne zdrsne na soboto', () => {
    expect(oblikujTermin('2026-03-29T10:30:00')).toBe('ned, 29. mar · 10.30')
    expect(oblikujTermin('2026-10-25T10:30:00')).toBe('ned, 25. okt · 10.30')
  })

  it('ura v slovenskem zapisu', () => {
    expect(oblikujUro('18:30')).toBe('18.30')
    expect(oblikujUro('2026-01-01T07:05:00')).toBe('07.05')
    expect(oblikujUro('00:00')).toBe('')
    expect(oblikujUro('7:5')).toBe('')
    expect(oblikujUro(undefined)).toBe('')
  })

  it('obdobje: en datum, razpon ali nič', () => {
    expect(oblikujObdobje('2026-08-13', '2026-08-13')).toBe('13. 8. 2026')
    expect(oblikujObdobje('2026-08-13', '2026-08-14')).toBe('13. 8. 2026 – 14. 8. 2026')
    expect(oblikujObdobje(null, '2026-08-14')).toBe('14. 8. 2026')
    expect(oblikujObdobje(null, null)).toBe('')
  })

  it('kratko obdobje ponovi samo, kar se razlikuje', () => {
    expect(oblikujObdobjeKratko('2026-09-05', '2026-09-05')).toBe('5. 9.')
    expect(oblikujObdobjeKratko('2026-08-13', '2026-08-14')).toBe('13.–14. 8.')
    expect(oblikujObdobjeKratko('2026-08-30', '2026-09-01')).toBe('30. 8. – 1. 9.')
    expect(oblikujObdobjeKratko('2026-08-13', '2026-08-14', true)).toBe('13.–14. 8. 2026')
    expect(oblikujObdobjeKratko(null, '2026-09-05')).toBe('5. 9.')
    expect(oblikujObdobjeKratko(null, null)).toBe('')
  })

  it('kratko obdobje čez novo leto pove letnico prvega datuma', () => {
    expect(oblikujObdobjeKratko('2026-12-30', '2027-01-02')).toBe('30. 12. 2026 – 2. 1.')
    expect(oblikujObdobjeKratko('2026-12-30', '2027-01-02', true)).toBe('30. 12. 2026 – 2. 1. 2027')
  })

  it('datumski blok in oznaka meseca', () => {
    expect(datumskiBlok('2026-04-05')).toEqual({ dan: '05', mesec: 'APR' })
    expect(datumskiBlok(null)).toEqual({ dan: '—', mesec: '' })
    expect(oznakaMeseca('2026-04')).toBe('APR')
    expect(oznakaMeseca('neznano')).toBe('neznano')
  })
})

describe('imena kol izločilne mreže', () => {
  it('po številu tekem v kolu', () => {
    expect(imeKola(5, 5)).toBe('Finale')
    expect(imeKola(4, 5)).toBe('Polfinale')
    expect(imeKola(3, 5)).toBe('Četrtfinale')
    expect(imeKola(2, 5)).toBe('Osmina finala')
    expect(imeKola(1, 5)).toBe('1/16 finala')
  })

  it('kratke oznake za gumb krmarja', () => {
    expect([5, 4, 3, 2, 1].map((kolo) => imeKolaKratko(kolo, 5))).toEqual(['F', 'PF', 'ČF', '1/8', '1/16'])
  })
})

describe('števila in cene', () => {
  it('tisočice loči ozki nedeljivi presledek', () => {
    expect(oblikujStevilo(4812)).toBe('4 812')
    expect(oblikujStevilo(999)).toBe('999')
    expect(oblikujStevilo(1234567)).toBe('1 234 567')
  })

  it('cena z decimalno vejico in dvema decimalkama', () => {
    expect(oblikujCeno(3.99)).toBe('3,99 €')
    expect(oblikujCeno(19.9)).toBe('19,90 €')
  })
})

describe('imena', () => {
  it('ime lige dobi veliko samo prvo črko', () => {
    expect(prvaCrkaVelika('savinja liga')).toBe('Savinja liga')
    expect(prvaCrkaVelika(' šnl')).toBe(' Šnl')
    expect(prvaCrkaVelika('SNTL')).toBe('SNTL')
    expect(prvaCrkaVelika('')).toBe('')
  })

  it('ime osebe dobi veliko začetnico vsake besede, ostalega ne prepiše', () => {
    expect(zVelikoZacetnico('ana marija')).toBe('Ana Marija')
    expect(zVelikoZacetnico('kos-novak')).toBe('Kos-Novak')
    expect(zVelikoZacetnico('žiga')).toBe('Žiga')
    expect(zVelikoZacetnico('McDonald')).toBe('McDonald')
  })

  it('kratek klub odreže splošno predpono in umiri velike črke', () => {
    expect(kratekKlub('NAMIZNOTENIŠKI KLUB SOBOTA, MURSKA SOBOTA')).toBe('Sobota, Murska Sobota')
    expect(kratekKlub('TELESNO VZGOJNO DRUŠTVO PARTIZAN LJUTOMER')).toBe('Partizan Ljutomer')
    expect(kratekKlub('NAMIZNOTENIŠKI KLUB LOG PRI BREZOVICI')).toBe('Log pri Brezovici')
    expect(kratekKlub('Železničarski Namiznoteniški klub Maribor')).toBe('Železničarski Maribor')
  })

  it('kar se ne prepozna ali bi ostalo prekratko, ostane celo', () => {
    expect(kratekKlub('NTK Žalec')).toBe('NTK Žalec')
    expect(kratekKlub('Namiznoteniški klub AB')).toBe('Namiznoteniški klub AB')
  })

  it('pri imenu lige se sklanja samo beseda »liga«', () => {
    expect(ligaVTozilniku('Liga Savinja A')).toBe('Ligo Savinja A')
    expect(ligaVTozilniku('Savinja liga')).toBe('Savinja ligo')
    expect(ligaVMestniku('Liga Savinja A')).toBe('Ligi Savinja A')
    expect(ligaVTozilniku('1. SNTL')).toBe('1. SNTL')
    /* »liga« sredi druge besede ni beseda liga. */
    expect(ligaVTozilniku('Obliga ligaš')).toBe('Obliga ligaš')
  })

  it('ime lestvice z istimi besedami kot izbira na strani Lestvica', () => {
    expect(imeLestvice({ spol: 'ZENSKI', rekreativec: false })).toBe('Lestvica Ženske · Člani')
    expect(imeLestvice({ spol: 'MOSKI', rekreativec: true })).toBe('Lestvica Moški · Rekreativci')
    expect(imeLestvice({ spol: null, rekreativec: false })).toBe('Lestvica Člani')
  })
})
