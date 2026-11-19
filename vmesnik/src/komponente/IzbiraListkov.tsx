/* Izbira, katere listke (ekipne zapisnike) tekem natisniti, in sled tiska.

   Organizator listke natisne ze med igro, rezultat pa se vnese sele pozneje.
   Zato Turnirko pomni, katere listke je organizator ze natisnil: privzeta
   izbira so samo se nenatisnjeni, natisnjeni pa so v seznamu skriti ali
   oznaceni, da jih ni treba iskati med ostalimi. Izbiro je mogoce zozati na
   sklop (skupina, tolazilni zreb ...) ali dopolniti s kljukicami.

   Brskalnik ne pove, ali je tisk res stekel (okno za tisk se da preklicati),
   zato se tekme oznacijo kot natisnjene sele, ko organizator po tisku to
   potrdi. Oznaka je samo opomnik: tekme ne spremeni, listek se da natisniti
   znova, oznako pa odstrani gumb »Odznaci«. */
import { useMemo, useState } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'

import { imeUdelezenca } from '../api/tipi'
import { dogodkiApi } from '../api/zahteve'
import {
  enakiIdji,
  idjiNenatisnjenih,
  jeNatisnjen,
  opisNatisa,
  type PripravljenListek,
  type SklopListkov,
} from '../pomozno/listki'
import { sklonListkov, sklonZapisnikov } from '../pomozno/oblikovanje'
import { SporociloNapake } from './SporociloNapake'
import { Namig } from '../pomoc/Namig'

/* Listek posamicne tekme ali ekipni zapisnik: razlikujeta se samo besedi. */
export type VrstaListka = 'listek' | 'zapisnik'

const BESEDILA: Record<VrstaListka, { sklon: (n: number) => string; mnozina: string }> = {
  listek: { sklon: sklonListkov, mnozina: 'listkov' },
  zapisnik: { sklon: sklonZapisnikov, mnozina: 'zapisnikov' },
}

export interface Izbira {
  /* Id-ji izbranih tekem (samo tiste, ki jih seznam se pozna). */
  izbrani: ReadonlySet<number>
  izbraniListki: PripravljenListek[]
  nastavi: (idji: number[]) => void
  /* Doda ali odstrani tekme iz izbire. */
  preklopi: (idji: number[], vklopljeno: boolean) => void
  /* Natisne izbrano in nato vpraša, ali je bil tisk res opravljen. */
  natisni: () => void
  /* Tekme, za katere cakamo potrditev tiska. */
  caka: number[] | null
  potrdi: (natisnil: boolean) => void
  odznaci: (idji: number[]) => void
  oznacuje: boolean
  napaka: Error | null
  smemOznacevati: boolean
}

export function useIzbiraListkov(
  idDogodka: number,
  listki: PripravljenListek[],
  /* Sled tiska smeta pisati samo organizator in admin; gost listke natisne brez nje. */
  smemOznacevati: boolean,
): Izbira {
  const odjemalec = useQueryClient()
  /* null = privzeto: vsi se nenatisnjeni. Ko organizator karkoli spremeni, je
     izbira izrecna. Tekma, ki je medtem odpadla (odigrana), iz izbire izgine. */
  const [izrecna, nastaviIzrecno] = useState<ReadonlySet<number> | null>(null)
  const [caka, nastaviCaka] = useState<number[] | null>(null)

  const izbrani = useMemo(() => {
    const veljavni = new Set(listki.map((l) => l.tekma.id))
    const osnova = izrecna ?? idjiNenatisnjenih(listki)
    return new Set([...osnova].filter((id) => veljavni.has(id)))
  }, [izrecna, listki])
  const izbraniListki = useMemo(() => listki.filter((l) => izbrani.has(l.tekma.id)), [listki, izbrani])

  const oznaci = useMutation({
    mutationFn: (v: { idji: number[]; natisnjeno: boolean }) =>
      dogodkiApi.oznaciListke(idDogodka, v.idji, v.natisnjeno),
    onSuccess: () => odjemalec.invalidateQueries({ queryKey: ['dogodek', idDogodka] }),
  })

  const nastavi = (idji: number[]) => nastaviIzrecno(new Set(idji))
  const preklopi = (idji: number[], vklopljeno: boolean) => {
    const nova = new Set(izbrani)
    for (const id of idji) {
      if (vklopljeno) nova.add(id)
      else nova.delete(id)
    }
    nastaviIzrecno(nova)
  }

  const natisni = () => {
    const idji = izbraniListki.map((l) => l.tekma.id)
    /* V vecini brskalnikov klic pocaka, da se okno za tisk zapre. */
    window.print()
    if (smemOznacevati && idji.length > 0) nastaviCaka(idji)
  }

  const potrdi = (natisnil: boolean) => {
    const idji = caka
    nastaviCaka(null)
    if (natisnil && idji) {
      oznaci.mutate({ idji, natisnjeno: true }, { onSuccess: () => nastaviIzrecno(null) })
    }
  }

  return {
    izbrani,
    izbraniListki,
    nastavi,
    preklopi,
    natisni,
    caka,
    potrdi,
    odznaci: (idji) => oznaci.mutate({ idji, natisnjeno: false }),
    oznacuje: oznaci.isPending,
    napaka: oznaci.error,
    smemOznacevati,
  }
}

/* Vprasanje po tisku: ali so bili listki res natisnjeni. Stoji v lepljivi
   orodni vrstici, da ga organizator vidi, ko se vrne iz okna za tisk. */
export function PotrditevTiska({ izbira, vrsta }: { izbira: Izbira; vrsta: VrstaListka }) {
  if (!izbira.caka) return null
  const { sklon } = BESEDILA[vrsta]
  const n = izbira.caka.length
  return (
    <div className="listki-potrditev" role="status">
      <p className="listki-potrditev__vprasanje">
        Si natisnil {n} {sklon(n)}?
      </p>
      <p className="listki-potrditev__pojasnilo">
        Če potrdiš, jih Turnirko označi kot natisnjene in jih pri naslednjem tisku preskoči.
      </p>
      <div className="listki-potrditev__gumbi">
        <button type="button" className="gumb gumb--glavni" onClick={() => izbira.potrdi(true)}>
          Da, natisnjeno
        </button>
        <button type="button" className="gumb" onClick={() => izbira.potrdi(false)}>
          Ne, tisk ni uspel
        </button>
      </div>
    </div>
  )
}

export function IzbiraListkov({
  sklopi,
  izbira,
  vrsta,
}: {
  sklopi: SklopListkov[]
  izbira: Izbira
  vrsta: VrstaListka
}) {
  const [pokaziNatisnjene, nastaviPokazi] = useState(false)
  const { sklon, mnozina } = BESEDILA[vrsta]

  const vsi = sklopi.flatMap((s) => s.listki)
  const natisnjenih = vsi.filter(jeNatisnjen).length
  const nenatisnjenih = idjiNenatisnjenih(vsi)

  /* Vrstice, ki so videti: natisnjene so privzeto skrite, da jih ni treba iskati. */
  const viden = (l: PripravljenListek) => pokaziNatisnjene || !jeNatisnjen(l)
  const vidniSklopi = sklopi
    .map((s) => ({ ...s, listki: s.listki.filter(viden) }))
    .filter((s) => s.listki.length > 0)

  return (
    <section className="listki-izbira zaslon-samo" aria-label={`Izbira ${mnozina}`}>
      <h2 className="listki-izbira__naslov">
        Kaj natisniti
        <Namig clanek="listki" />
      </h2>

      <div className="izbirnik listki-izbira__hitra" role="group" aria-label="Hitra izbira">
        <button
          type="button"
          className={
            'izbirnik__gumb' +
            (nenatisnjenih.length > 0 && enakiIdji(izbira.izbrani, nenatisnjenih) ? ' izbirnik__gumb--aktiven' : '')
          }
          disabled={nenatisnjenih.length === 0}
          aria-pressed={nenatisnjenih.length > 0 && enakiIdji(izbira.izbrani, nenatisnjenih)}
          onClick={() => izbira.nastavi(nenatisnjenih)}
        >
          Vsi še nenatisnjeni ({nenatisnjenih.length})
        </button>
        {sklopi.length > 1 &&
          sklopi.map((s) => {
            const idji = idjiNenatisnjenih(s.listki)
            if (idji.length === 0) return null
            const aktiven = enakiIdji(izbira.izbrani, idji)
            return (
              <button
                key={s.kljuc}
                type="button"
                className={'izbirnik__gumb' + (aktiven ? ' izbirnik__gumb--aktiven' : '')}
                aria-pressed={aktiven}
                onClick={() => izbira.nastavi(idji)}
              >
                {s.naslov} ({idji.length})
              </button>
            )
          })}
        <button
          type="button"
          className="izbirnik__gumb"
          disabled={izbira.izbrani.size === 0}
          onClick={() => izbira.nastavi([])}
        >
          Počisti izbiro
        </button>
      </div>

      <p className="listki-izbira__povzetek">
        Izbrano: {izbira.izbrani.size} {sklon(izbira.izbrani.size)}
        {natisnjenih > 0 && ` · že natisnjenih: ${natisnjenih}`}
      </p>

      {natisnjenih > 0 && (
        <label className="obrazec__polje obrazec__polje--stikalo">
          <input
            type="checkbox"
            checked={pokaziNatisnjene}
            onChange={(d) => nastaviPokazi(d.target.checked)}
          />
          <span>Pokaži tudi že natisnjene ({natisnjenih})</span>
        </label>
      )}

      <SporociloNapake napaka={izbira.napaka} />

      {vidniSklopi.length === 0 ? (
        <p className="obvestilo">
          Vsi {mnozina} so že natisnjeni. Za ponovni tisk vklopi »Pokaži tudi že natisnjene«.
        </p>
      ) : (
        <div className="listki-izbira__seznam">
          {vidniSklopi.map((s) => (
            <SklopVSeznamu key={s.kljuc} sklop={s} izbira={izbira} />
          ))}
        </div>
      )}
    </section>
  )
}

function SklopVSeznamu({ sklop, izbira }: { sklop: SklopListkov; izbira: Izbira }) {
  const idji = sklop.listki.map((l) => l.tekma.id)
  const izbranih = idji.filter((id) => izbira.izbrani.has(id)).length
  const vse = izbranih === idji.length

  return (
    <div className="listki-izbira__sklop">
      <label className="listki-izbira__glava">
        <input
          type="checkbox"
          checked={vse}
          ref={(el) => {
            if (el) el.indeterminate = izbranih > 0 && !vse
          }}
          onChange={() => izbira.preklopi(idji, !vse)}
        />
        <span className="listki-izbira__sklop-naslov">{sklop.naslov}</span>
        <span className="listki-izbira__sklop-stevilo">
          {izbranih} / {idji.length}
        </span>
      </label>

      {sklop.listki.map((l) => {
        const natisnjen = jeNatisnjen(l)
        const id = l.tekma.id
        return (
          <div key={id} className={'listki-izbira__vrstica' + (natisnjen ? ' listki-izbira__vrstica--natisnjena' : '')}>
            <label className="listki-izbira__tekma">
              <input
                type="checkbox"
                checked={izbira.izbrani.has(id)}
                onChange={(d) => izbira.preklopi([id], d.target.checked)}
              />
              <span className="listki-izbira__besedilo">
                <span className="listki-izbira__imena">
                  {imeUdelezenca(l.tekma.udelezenec1) ?? '—'} : {imeUdelezenca(l.tekma.udelezenec2) ?? '—'}
                </span>
                <span className="listki-izbira__meta">
                  {l.faza}
                  {l.tekma.miza !== null && ` · miza ${l.tekma.miza}`}
                  {natisnjen && l.tekma.listekNatisnjenOb && ` · natisnjeno ${opisNatisa(l.tekma.listekNatisnjenOb)}`}
                </span>
              </span>
            </label>
            {natisnjen && izbira.smemOznacevati && (
              <button
                type="button"
                className="gumb gumb--majhen"
                disabled={izbira.oznacuje}
                onClick={() => izbira.odznaci([id])}
              >
                Odznači
              </button>
            )}
          </div>
        )
      })}
    </div>
  )
}
