/* Mesto lige v piramidi: katera liga je nad njo, katere so pod njo in koliko
   ekip se ob koncu sezone premakne.

   Zakaj svoje okno in ne del pravil lige: pravila (format, nizi, točkovanje) se
   po generiranju razporeda zaklenejo, ker bi popravek razveljavil odigrano —
   povezave med ligami pa na razpored ne vplivajo in jih je treba smeti
   popraviti tudi sredi sezone (nova nižja liga nastane šele takrat).

   Zakaj obe smeri: v bazi je povezava ena sama (nižja liga kaže na višjo), a
   pisati jo je treba z obeh strani. Sicer bi bilo treba za vsako nižjo ligo
   odpirati njen obrazec — in če ta ni več v pripravi, sploh ne bi šlo. */
import { useMemo, useState, type FormEvent } from 'react'
import { useMutation } from '@tanstack/react-query'

import { ligeApi } from '../api/zahteve'
import type { LigaDto } from '../api/tipi'
import { IskalniIzbirnik, type MoznostIzbirnika } from './IskalniIzbirnik'
import { ModalnoOkno } from './ModalnoOkno'
import { SporociloNapake } from './SporociloNapake'

interface Lastnosti {
  liga: LigaDto
  /* Vse lige (seznam s strani lige) — iz njih se sestavita oba izbirnika. */
  vse: LigaDto[]
  onZapri: () => void
  onShranjeno: () => void
}

export function PrehodiOkno({ liga, vse, onZapri, onShranjeno }: Lastnosti) {
  const [idVisja, nastaviVisjo] = useState<number | null>(liga.idVisjaLiga)
  const [nizje, nastaviNizje] = useState<number[]>(() =>
    vse.filter((l) => l.idVisjaLiga === liga.id).map((l) => l.id),
  )
  const [napreduje, nastaviNapreduje] = useState(liga.stNapreduje)
  const [izpade, nastaviIzpade] = useState(liga.stIzpade)

  const shranjevanje = useMutation({
    mutationFn: () =>
      ligeApi.prehodi(liga.id, {
        idVisjaLiga: idVisja,
        idNizjeLige: nizje,
        stNapreduje: napreduje,
        stIzpade: izpade,
      }),
    onSuccess: () => {
      onShranjeno()
      onZapri()
    },
  })

  function obOddaji(dogodek: FormEvent) {
    dogodek.preventDefault()
    shranjevanje.mutate()
  }

  const druge = useMemo(
    () => vse.filter((l) => l.id !== liga.id).sort((a, b) => a.ime.localeCompare(b.ime, 'sl')),
    [vse, liga.id],
  )
  /* Liga, izbrana za višjo, ne more biti hkrati nižja (in obratno) — krog
     strežnik zavrne, zato je iz ponudbe drugega polja raje ni. */
  const moznostiVisje = useMemo(
    () => druge.filter((l) => !nizje.includes(l.id)).map((l) => moznostLige(l, null)),
    [druge, nizje],
  )
  const moznostiNizje = useMemo(
    () =>
      druge
        .filter((l) => l.id !== idVisja && !nizje.includes(l.id))
        .map((l) => moznostLige(l, liga.id)),
    [druge, idVisja, nizje, liga.id],
  )
  const izbraneNizje = nizje
    .map((id) => vse.find((l) => l.id === id))
    .filter((l): l is LigaDto => l !== undefined)

  /* Piramida veže lige ene sezone; drugačna sezona je skoraj vedno spregled
     (npr. »8. Sezona« proti »25/26«), zato nanjo opozorimo, a je ne prepovemo. */
  const sezone = new Set(
    [idVisja !== null ? vse.find((l) => l.id === idVisja) : undefined, ...izbraneNizje]
      .filter((l): l is LigaDto => l !== undefined)
      .map((l) => l.sezona ?? ''),
  )
  const razhajanjeSezon = sezone.size > 0 && !(sezone.size === 1 && sezone.has(liga.sezona ?? ''))

  return (
    <ModalnoOkno naslov="Prehodi in piramida" onZapri={onZapri}>
      <form className="obrazec" onSubmit={obOddaji}>
        <p className="obvestilo">
          Povezavo je dovolj vpisati z ene strani — nasprotna se uredi sama.
          Prehode je mogoče urejati tudi, ko liga že teče.
        </p>

        <IskalniIzbirnik
          vObrazcu
          oznaka="Višja liga (kamor se napreduje)"
          namig="Brez (najvišja liga) · vpiši ime"
          moznosti={moznostiVisje}
          izbrano={idVisja}
          naIzbiro={nastaviVisjo}
          naPraznjenje={() => nastaviVisjo(null)}
        />

        {/* Nižjih lig je lahko več, zato je polje dejanje: izbrana liga gre v
            seznam pod njim, polje pa se izprazni za naslednjo. */}
        <fieldset className="obrazec__skupina">
          <legend>Nižje lige (od koder se napreduje sem)</legend>
          {izbraneNizje.length > 0 && (
            <ul className="izbrane-postavke">
              {izbraneNizje.map((l) => (
                <li key={l.id}>
                  <span>
                    {opis(l)}
                    {l.idVisjaLiga != null && l.idVisjaLiga !== liga.id && (
                      <span className="izbrane-postavke__podrobnost">
                        zdaj pod: {l.visjaLigaIme} — ob shranitvi se premakne sem
                      </span>
                    )}
                  </span>
                  <button
                    type="button"
                    className="gumb gumb--majhen gumb--nevaren"
                    onClick={() => nastaviNizje((prej) => prej.filter((id) => id !== l.id))}
                  >
                    Odstrani
                  </button>
                </li>
              ))}
            </ul>
          )}
          {druge.length === 0 ? (
            <p className="namig">Drugih lig ni.</p>
          ) : (
            <IskalniIzbirnik
              oznaka="Dodaj nižjo ligo"
              namig="Dodaj nižjo ligo · vpiši ime"
              moznosti={moznostiNizje}
              naIzbiro={(id) => nastaviNizje((prej) => [...prej, id])}
            />
          )}
        </fieldset>

        <div className="obrazec__vrstica">
          <label className="obrazec__polje">
            <span>Napreduje (ekip)</span>
            <input type="number" min={0} value={napreduje}
              onChange={(d) => nastaviNapreduje(Number(d.target.value))} />
          </label>
          <label className="obrazec__polje">
            <span>Izpade (ekip)</span>
            <input type="number" min={0} value={izpade}
              onChange={(d) => nastaviIzpade(Number(d.target.value))} />
          </label>
        </div>

        {razhajanjeSezon && (
          <p className="namig">
            Povezane lige nimajo iste sezone kot ta ({liga.sezona || 'brez sezone'}).
            Povezava bo delovala, piramida pa bo mešala sezone — če gre za isto
            sezono, sezone poenoti.
          </p>
        )}

        <SporociloNapake napaka={shranjevanje.error} />
        <div className="obrazec__gumbi">
          <button type="button" className="gumb" onClick={onZapri}>Prekliči</button>
          <button type="submit" className="gumb gumb--glavni" disabled={shranjevanje.isPending}>
            Shrani prehode
          </button>
        </div>
      </form>
    </ModalnoOkno>
  )
}

/* Sezona je del imena lige v izbirniku: brez nje sta »Savinja liga B« dveh
   sezon nerazločljivi. */
function opis(l: LigaDto): string {
  return l.sezona ? `${l.ime} · ${l.sezona}` : l.ime
}

/* Liga kot predlog. Pri nižji ligi podrobnost pove, pod katero ligo je zdaj
   — z izbiro se bo premaknila sem. */
function moznostLige(l: LigaDto, idTe: number | null): MoznostIzbirnika {
  const drugje = idTe !== null && l.idVisjaLiga != null && l.idVisjaLiga !== idTe
  return {
    id: l.id,
    ime: opis(l),
    podrobnost: drugje ? `zdaj pod: ${l.visjaLigaIme}` : null,
  }
}
