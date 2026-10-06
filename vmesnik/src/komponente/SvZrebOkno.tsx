/* Ročna razporeditev mest v mreži enega žreba (SV regija).

   Mreža je seznam mest od vrha navzdol; sosednji mesti (1.–2., 3.–4. ...) igrata
   med seboj v prvem kolu. Udeležence prinesejo skupine, razporeditev jih le
   premesti - nikogar ne doda in ne odvzame.

   IZBIRA IGRALCA, KI ŽE STOJI NA DRUGEM MESTU, IGRALCA ZAMENJA (kot pri ročnem
   žrebu lige): pri polni mreži je vsak igralec že nekje, ugasnjene možnosti pa
   bi mrežo zaklenile - premakniti bi se dalo samo prosto mesto. Zamenjava
   števila igralcev ne spremeni, zato razporeditev nikoli ni nepopolna in
   strežnik napake zavrne šele kot zadnjo varovalko.

   Izhodišče je trenutna razporeditev žreba, ne naključni predlog: sicer bi
   vsako odprtje okna zavrglo prejšnjo ročno razporeditev. »Naključno« in
   »Trenutno« sta zato gumba, ne privzetek. */
import { useMemo, useState } from 'react'
import { useMutation } from '@tanstack/react-query'

import { svApi } from '../api/zahteve'
import type { MrezaDto, SvZrebDto } from '../api/tipi'
import { ModalnoOkno } from './ModalnoOkno'
import { SporociloNapake } from './SporociloNapake'
import { crkaSkupine, opisRazpona } from '../pomozno/svRegija'

interface Lastnosti {
  zreb: SvZrebDto
  podatki: MrezaDto
  onZapri: () => void
  onShranjeno: () => void
}

export function SvZrebOkno({ zreb, podatki, onZapri, onShranjeno }: Lastnosti) {
  const [mesta, nastaviMesta] = useState<(number | null)[]>(zreb.razpored)

  /* Igralec po id-ju prijave, z mestom v skupini - tako organizator vidi, kdo
     je prišel kot zmagovalec skupine in kdo kot drugi. */
  const poId = useMemo(() => {
    const skupinaPoId = new Map(podatki.skupine.map((s) => [s.id, s]))
    return new Map(
      podatki.prijave.map((p) => {
        const skupina = p.idSkupina !== null ? skupinaPoId.get(p.idSkupina) : undefined
        const uvrstitev =
          skupina && p.mestoVSkupini !== null
            ? `${crkaSkupine(skupina.oznaka)} ${p.mestoVSkupini}.`
            : (skupina ? crkaSkupine(skupina.oznaka) : '')
        return [p.id, { ime: p.polnoIme, uvrstitev, klub: p.klub }]
      }),
    )
  }, [podatki.prijave, podatki.skupine])

  const udelezenci = useMemo(
    () => zreb.razpored.filter((id): id is number => id !== null),
    [zreb.razpored],
  )
  const prostih = mesta.filter((m) => m === null).length

  const spremenjeno = mesta.some((m, i) => m !== zreb.razpored[i])

  const predlog = useMutation({
    mutationFn: () => svApi.predlogZreba(zreb.id as number),
    onSuccess: (p) => nastaviMesta(p.mesta.map((c) => (c === null ? null : c.idPrijave))),
  })

  const shranjevanje = useMutation({
    mutationFn: () => svApi.shraniMesta(zreb.id as number, mesta),
    onSuccess: () => {
      onShranjeno()
      onZapri()
    },
  })

  /* Igralca z drugega mesta zamenja, prosto mesto vzame prvo prosto. */
  function izberi(mesto: number, id: number | null) {
    const novo = [...mesta]
    const prejsnji = novo[mesto]
    if (id === prejsnji) return
    const drugo = novo.findIndex((m, i) => i !== mesto && m === id)
    if (drugo === -1) return // id ni v mreži ali (pri prostem mestu) ni drugega prostega
    novo[mesto] = id
    novo[drugo] = prejsnji
    nastaviMesta(novo)
  }

  const pari: number[] = []
  for (let i = 0; i < mesta.length; i += 2) pari.push(i)

  return (
    <ModalnoOkno
      naslov={`Razporeditev: ${zreb.ime.toLowerCase()}`}
      podnaslov={`${opisRazpona(zreb.prvoMesto, zreb.zadnjeMesto)} · ${udelezenci.length} igralcev`}
      onZapri={onZapri}
      siroko
    >
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
          disabled={predlog.isPending}
          onClick={() => predlog.mutate()}
        >
          {predlog.isPending ? 'Žrebam …' : 'Naključno'}
        </button>
        <button
          type="button"
          className="gumb gumb--majhen"
          disabled={!spremenjeno}
          onClick={() => nastaviMesta(zreb.razpored)}
        >
          Trenutno
        </button>
      </div>
      <SporociloNapake napaka={predlog.error} />

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

      <SporociloNapake napaka={shranjevanje.error} />
      <div className="obrazec__gumbi">
        <button type="button" className="gumb" onClick={onZapri}>
          Prekliči
        </button>
        <button
          type="button"
          className="gumb gumb--glavni"
          disabled={!spremenjeno || shranjevanje.isPending}
          onClick={() => shranjevanje.mutate()}
        >
          {shranjevanje.isPending ? 'Shranjujem …' : 'Shrani razporeditev'}
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
  poId: Map<number, { ime: string; uvrstitev: string; klub: string | null }>
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
