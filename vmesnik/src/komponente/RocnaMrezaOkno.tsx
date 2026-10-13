/* Rocna izlocilna mreza: organizator vpise, kdo stoji na katerem mestu (od vrha navzdol).
   Sosednja mesta (1.-2., 3.-4. ...) igrajo med seboj v prvem kolu; prosto mesto pomeni prosti
   prehod. Izhodisce je racunalniski predlog - isti zreb, ki bi ga naredil dogodek -,
   gumb "Naključno" ga naredi znova.

   Izbira igralca, ki ze stoji drugje, ga zamenja (pri polni mrezi je vsak igralec ze nekje).
   Ponovni vpis pred prvo tekmo izlocilnega dela je popravek. Strežnik vse preveri se enkrat. */
import { useEffect, useMemo, useState } from 'react'
import { useMutation } from '@tanstack/react-query'

import type { IzlocilniPredlogDto } from '../api/tipi'
import { ModalnoOkno } from './ModalnoOkno'
import { SporociloNapake } from './SporociloNapake'

export interface Udelezenec {
  ime: string
  /* Mesto v skupini, npr. »A 1.«; prazno pri izbiri, ki skupin nima. */
  uvrstitev: string
  klub: string | null
}

interface Lastnosti {
  naslov: string
  podnaslov: string
  /* Podatki o igralcih po id-ju prijave. */
  poId: Map<number, Udelezenec>
  predlog: () => Promise<IzlocilniPredlogDto>
  shrani: (idjiPoMestih: (number | null)[]) => Promise<unknown>
  onZapri: () => void
  onShranjeno: () => void
}

export function RocnaMrezaOkno({
  naslov,
  podnaslov,
  poId,
  predlog,
  shrani,
  onZapri,
  onShranjeno,
}: Lastnosti) {
  const [udelezenci, nastaviUdelezence] = useState<number[]>([])
  const [mesta, nastaviMesta] = useState<(number | null)[] | null>(null)
  /* Prvi predlog - »Trenutno« se vrne na to razporeditev. */
  const [izhodisce, nastaviIzhodisce] = useState<(number | null)[] | null>(null)

  const predlogZahteva = useMutation({
    mutationFn: predlog,
    onSuccess: (p) => {
      nastaviUdelezence(p.udelezenci)
      nastaviMesta(p.idjiPoMestih)
      nastaviIzhodisce((prej) => prej ?? p.idjiPoMestih)
    },
  })

  useEffect(() => {
    predlogZahteva.mutate()
    // predlog se pridobi enkrat ob odprtju okna
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const shranjevanje = useMutation({
    mutationFn: () => shrani(mesta ?? []),
    onSuccess: () => {
      onShranjeno()
      onZapri()
    },
  })

  const prostih = mesta ? mesta.filter((m) => m === null).length : 0
  const spremenjeno = mesta !== null && izhodisce !== null
    && mesta.some((m, i) => m !== izhodisce[i])

  /* Dve prosti mesti v istem paru bi pomenili dvoboj brez igralcev - strežnik to zavrne. */
  const napaka = useMemo(() => {
    if (!mesta) return null
    for (let i = 0; i < mesta.length; i += 2) {
      if (mesta[i] === null && mesta[i + 1] === null) {
        return 'Dve sosednji prosti mesti ne smeta biti v istem paru - v prvem kolu bi ne igral nihče.'
      }
    }
    return null
  }, [mesta])

  /* Igralca z drugega mesta zamenja; prosto mesto vzame prvo prosto. */
  function izberi(mesto: number, id: number | null) {
    if (!mesta) return
    const novo = [...mesta]
    const prejsnji = novo[mesto]
    if (id === prejsnji) return
    const drugo = novo.findIndex((m, i) => i !== mesto && m === id)
    if (drugo === -1) return
    novo[mesto] = id
    novo[drugo] = prejsnji
    nastaviMesta(novo)
  }

  const pari: number[] = []
  if (mesta) {
    for (let i = 0; i < mesta.length; i += 2) pari.push(i)
  }

  return (
    <ModalnoOkno naslov={naslov} podnaslov={podnaslov} onZapri={onZapri} siroko>
      <p className="obvestilo">
        Mesta so od vrha mreže navzdol; sosednja dva igrata med seboj v prvem kolu. Če izbereš
        igralca, ki že stoji drugje, se igralca zamenjata.
        {prostih > 0 &&
          ` Prosto mesto pomeni prosti prehod nasprotnika (prostih mest: ${prostih}).`}
      </p>

      <div className="zreb__krmila">
        <button
          type="button"
          className="gumb gumb--majhen"
          disabled={predlogZahteva.isPending}
          onClick={() => predlogZahteva.mutate()}
        >
          {predlogZahteva.isPending ? 'Žrebam …' : 'Naključno'}
        </button>
        <button
          type="button"
          className="gumb gumb--majhen"
          disabled={!spremenjeno}
          onClick={() => nastaviMesta(izhodisce)}
        >
          Trenutno
        </button>
      </div>
      <SporociloNapake napaka={predlogZahteva.error} />

      {mesta && (
        <div className="zreb">
          <section className="zreb__kolo">
            <div className="zreb__vrstice">
              {pari.map((mesto, indeks) => (
                <div className="zreb__vrstica" key={mesto}>
                  <IzbirnikMesta
                    oznaka={`${indeks + 1}. par, zgornje mesto`}
                    kratka="zgoraj"
                    vrednost={mesta[mesto]}
                    udelezenci={udelezenci}
                    poId={poId}
                    prosto={prostih > 0 || mesta[mesto] === null}
                    onIzbor={(id) => izberi(mesto, id)}
                  />
                  <span className="zreb__vrstica-vez">proti</span>
                  <IzbirnikMesta
                    oznaka={`${indeks + 1}. par, spodnje mesto`}
                    kratka="spodaj"
                    vrednost={mesta[mesto + 1]}
                    udelezenci={udelezenci}
                    poId={poId}
                    prosto={prostih > 0 || mesta[mesto + 1] === null}
                    onIzbor={(id) => izberi(mesto + 1, id)}
                  />
                  <span />
                </div>
              ))}
            </div>
          </section>
        </div>
      )}

      {napaka && (
        <div className="zreb__napake">
          <ul className="zreb__seznam">
            <li>{napaka}</li>
          </ul>
        </div>
      )}

      <SporociloNapake napaka={shranjevanje.error} />
      <div className="obrazec__gumbi">
        <button type="button" className="gumb" onClick={onZapri}>
          Prekliči
        </button>
        <button
          type="button"
          className="gumb gumb--glavni"
          disabled={!mesta || napaka !== null || shranjevanje.isPending}
          onClick={() => shranjevanje.mutate()}
        >
          {shranjevanje.isPending ? 'Shranjujem …' : 'Shrani mrežo'}
        </button>
      </div>
    </ModalnoOkno>
  )
}

function IzbirnikMesta({
  oznaka,
  kratka,
  vrednost,
  udelezenci,
  poId,
  prosto,
  onIzbor,
}: {
  oznaka: string
  kratka: string
  vrednost: number | null
  udelezenci: number[]
  poId: Map<number, Udelezenec>
  /* Ali je med možnostmi prosto mesto (samo, če v mreži kakšno sploh je). */
  prosto: boolean
  onIzbor: (id: number | null) => void
}) {
  return (
    <label className="zreb__polje">
      <span className="zreb__polje-oznaka" aria-hidden="true">{kratka}</span>
      <select
        aria-label={oznaka}
        value={vrednost ?? ''}
        onChange={(d) => onIzbor(d.target.value ? Number(d.target.value) : null)}
      >
        {prosto && <option value="">— prosto —</option>}
        {udelezenci.map((id) => {
          const p = poId.get(id)
          return (
            <option key={id} value={id}>
              {p ? `${p.ime}${p.uvrstitev ? ` · ${p.uvrstitev}` : ''}` : `Prijava ${id}`}
            </option>
          )
        })}
      </select>
    </label>
  )
}
