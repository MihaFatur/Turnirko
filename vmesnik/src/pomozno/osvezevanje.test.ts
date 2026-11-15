import { describe, expect, it } from 'vitest'

import { INTERVAL_V_ZIVO, intervalOsvezevanja, jeVZivo, uraOsvezitve } from './osvezevanje'

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
