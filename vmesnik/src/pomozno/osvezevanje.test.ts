import { describe, expect, it } from 'vitest'

import { NapakaStreznika } from '../api/odjemalec'
import {
  INTERVAL_V_ZIVO,
  intervalOsvezevanja,
  jeTrajnaNapaka,
  jeVZivo,
  osvezitevNiUspela,
  prikazStrani,
  uraOsvezitve,
  type StanjePoizvedbe,
} from './osvezevanje'

describe('statusi, ob katerih se podatki še spreminjajo', () => {
  it.each(['V_TEKU', 'POTEKA', 'V_IGRI', 'PRIPRAVLJENA'])('%s je v živo', (status) => {
    expect(jeVZivo(status)).toBe(true)
    expect(intervalOsvezevanja(status)).toBe(INTERVAL_V_ZIVO)
  })

  /* Priprava in zgodovina se ne osvežujeta: poizvedovanje bi v dvorani s slabim
     signalom po nepotrebnem jemalo baterijo in prenos. */
  it.each(['PRIPRAVA', 'ZAKLJUCEN', 'RAZPORED', 'KONCANO', 'CAKA', 'KONCANA', 'NEODIGRANA', ''])(
    '%s ni v živo',
    (status) => {
      expect(jeVZivo(status)).toBe(false)
      expect(intervalOsvezevanja(status)).toBe(false)
    },
  )

  it('brez statusa (podatki še niso naloženi) ni osveževanja', () => {
    expect(jeVZivo(null)).toBe(false)
    expect(jeVZivo(undefined)).toBe(false)
    expect(intervalOsvezevanja(undefined)).toBe(false)
  })
})

describe('ura zadnje osvežitve', () => {
  it('ura in minute z vodilno ničlo', () => {
    expect(uraOsvezitve(new Date(2026, 10, 13, 18, 42).getTime())).toBe('18:42')
    expect(uraOsvezitve(new Date(2026, 10, 13, 7, 5).getTime())).toBe('07:05')
  })

  it('brez uspešnega odgovora ure ni', () => {
    expect(uraOsvezitve(undefined)).toBeNull()
    expect(uraOsvezitve(0)).toBeNull()
  })
})

describe('kaj stran izriše glede na stanje poizvedbe', () => {
  const stanje = (polja: Partial<StanjePoizvedbe>): StanjePoizvedbe => ({
    data: undefined,
    error: null,
    isLoading: false,
    isPaused: false,
    ...polja,
  })
  const podatki = { dogodek: { ime: 'Člani' } }
  const izpad = new TypeError('Failed to fetch')

  it('prvo nalaganje, izpad povezave in napaka brez podatkov ostanejo celostranski', () => {
    expect(prikazStrani(stanje({ isLoading: true }))).toBe('nalaganje')
    expect(prikazStrani(stanje({ isPaused: true }))).toBe('brez-povezave')
    expect(prikazStrani(stanje({ error: izpad }))).toBe('napaka')
    expect(prikazStrani(stanje({ error: new NapakaStreznika(500, 'Napaka', 'x') }))).toBe('napaka')
  })

  it('neuspela osvežitev ne zamenja podatkov, ki so že na zaslonu', () => {
    expect(prikazStrani(stanje({ data: podatki, error: izpad }))).toBe('vsebina')
    expect(prikazStrani(stanje({ data: podatki, error: new NapakaStreznika(503, 'Napaka', 'x') }))).toBe('vsebina')
    expect(prikazStrani(stanje({ data: podatki, error: new NapakaStreznika(429, 'Napaka', 'x') }))).toBe('vsebina')
  })

  it('tudi izpad povezave med osveževanjem pusti zadnje stanje', () => {
    expect(prikazStrani(stanje({ data: podatki, isPaused: true }))).toBe('vsebina')
    expect(prikazStrani(stanje({ data: podatki, isPaused: true, error: izpad }))).toBe('vsebina')
  })

  it('trajna napaka (zapisa ni več, ni pravice) stran zamenja kljub starim podatkom', () => {
    for (const koda of [401, 403, 404, 410]) {
      expect(prikazStrani(stanje({ data: podatki, error: new NapakaStreznika(koda, 'Napaka', 'x') }))).toBe('napaka')
    }
  })

  it('odgovor brez zapisa prepusti strani (»Tega dogodka ni več«)', () => {
    expect(prikazStrani(stanje({}))).toBe('vsebina')
    expect(prikazStrani(stanje({ data: null }))).toBe('vsebina')
  })

  it('katere napake so trajne', () => {
    expect([400, 401, 403, 404, 409, 410].every((k) => jeTrajnaNapaka(new NapakaStreznika(k, 'N', 'x')))).toBe(true)
    expect([408, 429, 500, 502, 503].some((k) => jeTrajnaNapaka(new NapakaStreznika(k, 'N', 'x')))).toBe(false)
    expect(jeTrajnaNapaka(izpad)).toBe(false)
    expect(jeTrajnaNapaka(null)).toBe(false)
  })

  it('opozorilo o osvežitvi: samo ob prehodni napaki s podatki in povezavo', () => {
    expect(osvezitevNiUspela({ data: podatki, error: izpad, isPaused: false })).toBe(true)
    expect(osvezitevNiUspela({ data: podatki, error: null, isPaused: false })).toBe(false)
    expect(osvezitevNiUspela({ data: undefined, error: izpad, isPaused: false })).toBe(false)
    /* Brez povezave to že pove trak v postavitvi. */
    expect(osvezitevNiUspela({ data: podatki, error: izpad, isPaused: true })).toBe(false)
    expect(osvezitevNiUspela({ data: podatki, error: new NapakaStreznika(404, 'N', 'x'), isPaused: false })).toBe(false)
  })
})
