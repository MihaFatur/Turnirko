import { describe, expect, it } from 'vitest'

import type { KandidatMizeDto, MizaDto, TekmaMizeDto } from '../api/tipi'
import {
  MEJA_PREDOLGO_MIN,
  drugaMizaEkipnega,
  imenaTekme,
  jePredolgo,
  kratekDel,
  kratkoImenaTekme,
  minutOd,
  mizSklon,
  mizeTekme,
  najdljeIgra,
  oznakaMize,
  predMinutami,
  priimekNaMizi,
  privzetiSodnik,
  prosteMize,
  prviPredlogi,
  razlogiPredloga,
  razlogiZasedenosti,
  sirinaTraka,
  sprosceneMize,
  tekmeVIgri,
  uraIzCasa,
  zadnjaBeseda,
} from './mize'

function tekma(id: number, osebe: number[] = [1, 2], miza: number | null = null): TekmaMizeDto {
  return {
    tekma: {
      id,
      udelezenec1: { idPrijave: 10, polnoIme: 'Ana Novak', klub: null, polnoIme2: null, klub2: null, priimek: 'Novak', priimek2: null },
      udelezenec2: { idPrijave: 11, polnoIme: 'Bor Kovač', klub: null, polnoIme2: null, klub2: null, priimek: 'Kovač', priimek2: null },
    } as unknown as TekmaMizeDto['tekma'],
    idDogodka: 1,
    imeDogodka: 'Člani',
    del: 'Skupina B',
    ekipno: false,
    idSrecanje: null,
    miza,
    miza2: null,
    poklicanaOb: null,
    sodnik: null,
    osebe,
  }
}

function miza(stevilka: number, dodatno: Partial<MizaDto> = {}): MizaDto {
  return { stevilka, izklopljena: false, tekma: null, prostaOd: null, cakajociSodnik: null, predlogi: [], ...dodatno }
}

describe('minutOd', () => {
  it('šteje cele minute po uri strežnika', () => {
    expect(minutOd('2026-12-05T10:00:00', '2026-12-05T10:12:59')).toBe(12)
    expect(minutOd('2026-12-05T10:00:00', '2026-12-05T09:59:00')).toBe(0)
    expect(minutOd(null, '2026-12-05T10:00:00')).toBeNull()
  })
})

describe('prikaz tekme', () => {
  it('imena in mize', () => {
    expect(imenaTekme(tekma(1))).toBe('Ana Novak – Bor Kovač')
    expect(mizeTekme({ ...tekma(1), miza: 3, miza2: 4 })).toBe('3 in 4')
    expect(mizeTekme(tekma(1, [1, 2], 5))).toBe('5')
    expect(predMinutami(0)).toBe('pravkar')
    expect(predMinutami(4)).toBe('pred 4 min')
  })
})

describe('privzetiSodnik', () => {
  it('je poraženec zadnje tekme na mizi, razen če igra v tej tekmi', () => {
    const m = miza(2, { cakajociSodnik: { id: 7, ime: 'Cene Zajc' } })
    expect(privzetiSodnik(m, tekma(1, [1, 2]))).toEqual({ id: 7, ime: 'Cene Zajc' })
    expect(privzetiSodnik(m, tekma(1, [7, 2]))).toBeNull()
    expect(privzetiSodnik(miza(3), tekma(1))).toBeNull()
    expect(privzetiSodnik(undefined, tekma(1))).toBeNull()
  })
})

describe('proste in sproščene mize', () => {
  const zasedena = miza(1, { tekma: tekma(5, [1, 2], 1) })
  it('prosta je miza brez tekme, ki ni izklopljena', () => {
    expect(prosteMize([zasedena, miza(2), miza(3, { izklopljena: true })])).toEqual([2])
  })
  it('sproščena je miza, ki je bila zasedena in je zdaj prosta', () => {
    expect(sprosceneMize(undefined, [miza(1)])).toEqual([])
    expect(sprosceneMize([zasedena, miza(2)], [miza(1), miza(2)])).toEqual([1])
    // izklopljena miza ne piska
    expect(sprosceneMize([zasedena], [miza(1, { izklopljena: true })])).toEqual([])
  })
})

describe('prviPredlogi', () => {
  it('vzame prvi predlog vsake proste mize, zasedenih ne', () => {
    const k = (id: number, zaseden = false): KandidatMizeDto => ({ tekma: tekma(id), zaseden, razlogi: [] })
    const mize = [miza(1, { predlogi: [11, 12] }), miza(2, { predlogi: [12] }), miza(3, { tekma: tekma(9, [5, 6], 3), predlogi: [] })]
    const pari = prviPredlogi(mize, [k(11), k(12)])
    expect(pari.map(([m, kand]) => [m.stevilka, kand.tekma.tekma.id])).toEqual([[1, 11], [2, 12]])
    expect(prviPredlogi(mize, [k(11, true), k(12)]).map(([m]) => m.stevilka)).toEqual([2])
  })
})

describe('imena na tlorisu', () => {
  const u = (polnoIme: string, priimek: string, dodatno: object = {}) =>
    ({ idPrijave: 1, polnoIme, klub: null, polnoIme2: null, klub2: null, priimek, priimek2: null, ...dodatno }) as never

  it('je priimek tisto, kar pošlje strežnik; dvojica z » / «, ekipa z imenom', () => {
    expect(priimekNaMizi(u('Anže Vidmar', 'Vidmar'))).toBe('Vidmar')
    expect(priimekNaMizi(u('Ana Kokalj', 'Kokalj', { polnoIme2: 'Eva Logar', priimek2: 'Logar' }))).toBe('Kokalj / Logar')
    expect(priimekNaMizi(u('NTK Savinja', 'NTK Savinja'))).toBe('NTK Savinja')
  })

  it('brez priimka pade na zadnjo besedo imena, brez udeleženca je vprašaj', () => {
    expect(priimekNaMizi(u('Anže Vidmar', ''))).toBe('Vidmar')
    expect(priimekNaMizi(u('Ana Kokalj / Eva Logar', ''))).toBe('Kokalj / Logar')
    expect(priimekNaMizi(null)).toBe('?')
    expect(zadnjaBeseda('  Tadej  Mihelič ')).toBe('Mihelič')
  })

  it('priimka tekme in skrajšan del', () => {
    expect(kratkoImenaTekme(tekma(1))).toBe('Novak – Kovač')
    expect(kratekDel('Skupina 2')).toBe('sk. 2')
    expect(kratekDel('1/8 finala')).toBe('1/8 finala')
  })
})

describe('mize ekipnega srečanja', () => {
  it('oznaka mize', () => {
    expect(oznakaMize({ miza: 5, miza2: null })).toBe('5')
    expect(oznakaMize({ miza: 11, miza2: 12 })).toBe('11 + 12')
    expect(oznakaMize({ miza: 11, miza2: 12 }, '+')).toBe('11+12')
    expect(oznakaMize({ miza: null, miza2: null })).toBe('')
  })

  it('druga miza je sosednja prosta: najprej +1, nato −1, sicer prva prosta', () => {
    expect(drugaMizaEkipnega([3, 4, 9], 3)).toBe(4)
    expect(drugaMizaEkipnega([2, 3, 9], 3)).toBe(2)
    expect(drugaMizaEkipnega([3, 9, 15], 3)).toBe(9)
    expect(drugaMizaEkipnega([3], 3)).toBeNull()
    expect(drugaMizaEkipnega([], 3)).toBeNull()
  })
})

describe('tekme v igri in čas', () => {
  const ekipna = { ...tekma(9, [1, 2], 11), miza2: 12, ekipno: true, poklicanaOb: '2026-12-05T10:01:00' }
  const posamicna = { ...tekma(8, [3, 4], 5), poklicanaOb: '2026-12-05T10:08:00' }
  const mize = [
    miza(5, { tekma: posamicna }),
    miza(11, { tekma: ekipna }),
    miza(12, { tekma: ekipna }),
    miza(13),
  ]

  it('ekipno srečanje na dveh mizah je ena tekma', () => {
    expect(tekmeVIgri(mize).map((t) => t.tekma.id)).toEqual([8, 9])
  })

  it('najdlje igra tekma z največ minutami, miza ekipnega je »11 + 12«', () => {
    expect(najdljeIgra(mize, '2026-12-05T10:42:30')).toEqual({ oznaka: '11 + 12', minute: 41 })
    expect(najdljeIgra([miza(1)], '2026-12-05T10:42:30')).toBeNull()
  })

  it('predolgo velja od meje naprej in ne za ekipno srečanje', () => {
    expect(MEJA_PREDOLGO_MIN).toBe(30)
    expect(jePredolgo(posamicna, 29)).toBe(false)
    expect(jePredolgo(posamicna, 30)).toBe(true)
    expect(jePredolgo(ekipna, 41)).toBe(false)
    expect(jePredolgo(posamicna, null)).toBe(false)
  })

  it('trak časa raste do meje in se tam ustavi', () => {
    expect(sirinaTraka(12)).toBe(40)
    expect(sirinaTraka(8)).toBe(27)
    expect(sirinaTraka(34)).toBe(100)
  })

  it('ura iz časa strežnika', () => {
    expect(uraIzCasa('2026-11-24T10:42:07')).toBe('10:42')
    expect(uraIzCasa('2026-11-24T09:05')).toBe('09:05')
    expect(uraIzCasa(null)).toBe('')
  })
})

describe('razlogi kandidata', () => {
  const k = (razlogi: string[], zaseden = true): KandidatMizeDto => ({ tekma: tekma(1), zaseden, razlogi })

  it('zasedenost ločimo od razlogov za predlog', () => {
    const kand = k(['Kos igra na mizi 2', 'do konca skupine še 2 kola', 'Zajc naj sodi'])
    expect(razlogiZasedenosti(kand)).toEqual(['Kos igra na mizi 2'])
    expect(razlogiPredloga(kand)).toEqual(['do konca skupine še 2 kola', 'Zajc naj sodi'])
  })

  it('brez poimenovane zasedenosti ostanejo vsi razlogi', () => {
    const kand = k(['igralec je zaseden'])
    expect(razlogiZasedenosti(kand)).toEqual(['igralec je zaseden'])
    // razlog, ki ni zasedenost, je razlog za predlog (tudi kadar ga zasedenost ne izpodrine)
    expect(razlogiPredloga(kand)).toEqual(['igralec je zaseden'])
    expect(razlogiPredloga(k(['do konca skupine še 2 kola'], false))).toEqual(['do konca skupine še 2 kola'])
  })
})

describe('mizSklon', () => {
  it('sklanja po številu', () => {
    expect([1, 2, 3, 4, 5, 20, 21, 22, 101, 111].map(mizSklon)).toEqual([
      'miza', 'mizi', 'mize', 'mize', 'miz', 'miz', 'miz', 'miz', 'miza', 'miz',
    ])
  })
})
