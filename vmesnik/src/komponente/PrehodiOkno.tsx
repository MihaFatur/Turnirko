/* Mesto lige v piramidi: katera liga je nad njo, katere so pod njo in koliko
   ekip se ob koncu sezone premakne.

   Zakaj svoje okno in ne del pravil lige: pravila (format, nizi, točkovanje) se
   po generiranju razporeda zaklenejo, ker bi popravek razveljavil odigrano —
   povezave med ligami pa na razpored ne vplivajo in jih je treba smeti
   popraviti tudi sredi sezone (nova nižja liga nastane šele takrat).

   Zakaj obe smeri: v bazi je povezava ena sama (nižja liga kaže na višjo), a
   pisati jo je treba z obeh strani. Sicer bi bilo treba za vsako nižjo ligo
   odpirati njen obrazec — in če ta ni več v pripravi, sploh ne bi šlo. */
import { useState, type FormEvent } from 'react'
import { useMutation } from '@tanstack/react-query'

import { ligeApi } from '../api/zahteve'
import type { LigaDto } from '../api/tipi'
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
  const [idVisja, nastaviVisjo] = useState(
    liga.idVisjaLiga != null ? String(liga.idVisjaLiga) : '',
  )
  const [nizje, nastaviNizje] = useState<number[]>(() =>
    vse.filter((l) => l.idVisjaLiga === liga.id).map((l) => l.id),
  )
  const [napreduje, nastaviNapreduje] = useState(liga.stNapreduje)
  const [izpade, nastaviIzpade] = useState(liga.stIzpade)

  const shranjevanje = useMutation({
    mutationFn: () =>
      ligeApi.prehodi(liga.id, {
        idVisjaLiga: idVisja ? Number(idVisja) : null,
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

  const druge = vse.filter((l) => l.id !== liga.id).sort((a, b) => a.ime.localeCompare(b.ime, 'sl'))
  /* Liga, izbrana za višjo, ne more biti hkrati nižja — krog strežnik zavrne,
     zato je iz seznama raje ni. */
  const mozneNizje = druge.filter((l) => String(l.id) !== idVisja)

  /* Piramida veže lige ene sezone; drugačna sezona je skoraj vedno spregled
     (npr. »8. Sezona« proti »25/26«), zato nanjo opozorimo, a je ne prepovemo. */
  const sezone = new Set(
    [
      idVisja ? vse.find((l) => l.id === Number(idVisja)) : undefined,
      ...nizje.map((id) => vse.find((l) => l.id === id)),
    ]
      .filter((l): l is LigaDto => l !== undefined)
      .map((l) => l.sezona ?? ''),
  )
  const razhajanjeSezon = sezone.size > 0 && !(sezone.size === 1 && sezone.has(liga.sezona ?? ''))

  function preklopiNizjo(id: number) {
    nastaviNizje((prej) => (prej.includes(id) ? prej.filter((x) => x !== id) : [...prej, id]))
  }

  return (
    <ModalnoOkno naslov="Prehodi in piramida" onZapri={onZapri}>
      <form className="obrazec" onSubmit={obOddaji}>
        <p className="obvestilo">
          Povezavo je dovolj vpisati z ene strani — nasprotna se uredi sama.
          Prehode je mogoče urejati tudi, ko liga že teče.
        </p>

        <label className="obrazec__polje">
          <span>Višja liga (kamor se napreduje)</span>
          <select value={idVisja} onChange={(d) => nastaviVisjo(d.target.value)}>
            <option value="">— brez (to je najvišja liga) —</option>
            {druge.map((l) => (
              <option key={l.id} value={l.id}>{opis(l)}</option>
            ))}
          </select>
        </label>

        <fieldset className="obrazec__skupina">
          <legend>Nižje lige (od koder se napreduje sem)</legend>
          {mozneNizje.length === 0 ? (
            <p className="namig">Drugih lig ni.</p>
          ) : (
            <div className="obrazec__radio-skupina obrazec__radio-skupina--drsna">
              {mozneNizje.map((l) => (
                <label key={l.id}>
                  <input
                    type="checkbox"
                    checked={nizje.includes(l.id)}
                    onChange={() => preklopiNizjo(l.id)}
                  />
                  <span>
                    {opis(l)}
                    {/* Liga je lahko že pod drugo — označba pove, kaj se bo
                        z odkljukanjem premaknilo. */}
                    {l.idVisjaLiga != null && l.idVisjaLiga !== liga.id && (
                      <span className="opombe"> — zdaj pod: {l.visjaLigaIme}</span>
                    )}
                  </span>
                </label>
              ))}
            </div>
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
