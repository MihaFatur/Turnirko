/* Mesto lige v piramidi: katera liga je nad njo, katere so pod njo in koliko
   ekip se ob koncu sezone premakne — neposredno ali prek kvalifikacij.

   Zakaj svoje okno in ne del pravil lige: pravila (format, nizi, točkovanje) se
   po generiranju razporeda zaklenejo, ker bi popravek razveljavil odigrano —
   povezave med ligami pa na razpored ne vplivajo in jih je treba smeti
   popraviti tudi sredi sezone (nova nižja liga nastane šele takrat).

   Zakaj obe smeri: v bazi je povezava ena sama (nižja liga kaže na višjo), a
   pisati jo je treba z obeh strani. Sicer bi bilo treba za vsako nižjo ligo
   odpirati njen obrazec — in če ta ni več v pripravi, sploh ne bi šlo.

   Polja (PrehodiPolja) si deli z obrazcem NOVE lige: organizator Savinja lige B
   ve, da je pod A in da 3. igra kvalifikacije, še preden ima liga ekipe. */
import { useMemo, useState, type FormEvent } from 'react'
import { useMutation } from '@tanstack/react-query'

import { ligeApi } from '../api/zahteve'
import type { LigaDto, PrehodiVnos } from '../api/tipi'
import { IskalniIzbirnik, type MoznostIzbirnika } from './IskalniIzbirnik'
import { ModalnoOkno } from './ModalnoOkno'
import { StevilskoPolje } from './StevilskoPolje'
import { SporociloNapake } from './SporociloNapake'

interface Lastnosti {
  liga: LigaDto
  /* Vse lige (seznam s strani lige) — iz njih se sestavita oba izbirnika. */
  vse: LigaDto[]
  onZapri: () => void
  onShranjeno: () => void
}

export function PrehodiOkno({ liga, vse, onZapri, onShranjeno }: Lastnosti) {
  const [stanje, nastaviStanje] = useState<PrehodiStanje>(() => zacetniPrehodi(liga, vse))

  const shranjevanje = useMutation({
    mutationFn: () => ligeApi.prehodi(liga.id, vPrehodiVnos(stanje)),
    onSuccess: () => {
      onShranjeno()
      onZapri()
    },
  })

  function obOddaji(dogodek: FormEvent) {
    dogodek.preventDefault()
    shranjevanje.mutate()
  }

  return (
    <ModalnoOkno naslov="Prehodi in piramida" onZapri={onZapri}>
      <form className="obrazec" onSubmit={obOddaji}>
        <p className="obvestilo">
          Povezavo je dovolj vpisati z ene strani — nasprotna se uredi sama.
          Prehode je mogoče urejati tudi, ko liga že teče.
        </p>

        <PrehodiPolja
          stanje={stanje}
          nastavi={nastaviStanje}
          vse={vse}
          idLige={liga.id}
          sezona={liga.sezona}
        />

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

/* ---------- Polja prehodov (okno in obrazec nove lige) ---------- */

export interface PrehodiStanje {
  idVisja: number | null
  nizje: number[]
  napreduje: number
  izpade: number
  kvalGor: number
  kvalDol: number
}

/* Stanje polj za obstoječo ligo (iz njenih podatkov in nižjih lig, ki kažejo
   nanjo) oz. prazno za novo. */
export function zacetniPrehodi(liga: LigaDto | null, vse: LigaDto[]): PrehodiStanje {
  return {
    idVisja: liga?.idVisjaLiga ?? null,
    nizje: liga ? vse.filter((l) => l.idVisjaLiga === liga.id).map((l) => l.id) : [],
    napreduje: liga?.stNapreduje ?? 0,
    izpade: liga?.stIzpade ?? 0,
    kvalGor: liga?.stKvalifikacijeGor ?? 0,
    kvalDol: liga?.stKvalifikacijeDol ?? 0,
  }
}

export function vPrehodiVnos(s: PrehodiStanje): PrehodiVnos {
  return {
    idVisjaLiga: s.idVisja,
    idNizjeLige: s.nizje,
    stNapreduje: s.napreduje,
    stIzpade: s.izpade,
    stKvalifikacijeGor: s.kvalGor,
    stKvalifikacijeDol: s.kvalDol,
  }
}

/* Ali stanje sploh kaj pove — obrazec nove lige prazne prehode izpusti. */
export function prehodiPrazni(s: PrehodiStanje): boolean {
  return s.idVisja === null && s.nizje.length === 0
    && s.napreduje === 0 && s.izpade === 0 && s.kvalGor === 0 && s.kvalDol === 0
}

export function PrehodiPolja({
  stanje,
  nastavi,
  vse,
  idLige,
  sezona,
}: {
  stanje: PrehodiStanje
  nastavi: (s: PrehodiStanje) => void
  vse: LigaDto[]
  /* null pri novi ligi (te v seznamu še ni). */
  idLige: number | null
  sezona: string | null
}) {
  const popravi = (sprememba: Partial<PrehodiStanje>) => nastavi({ ...stanje, ...sprememba })
  const { idVisja, nizje } = stanje

  /* Kvalifikacije niso nivo piramide (strežnik jih kot višjo ali nižjo ligo
     zavrne), zato jih izbirnika ne ponudita. */
  const druge = useMemo(
    () =>
      vse
        .filter((l) => l.id !== idLige && l.idKvalifikacijeVisja == null)
        .sort((a, b) => a.ime.localeCompare(b.ime, 'sl')),
    [vse, idLige],
  )
  /* Liga, izbrana za višjo, ne more biti hkrati nižja (in obratno) — krog
     strežnik zavrne, zato je iz ponudbe drugega polja raje ni. */
  const moznostiVisje = useMemo(
    () => druge.filter((l) => !nizje.includes(l.id)).map((l) => moznostLige(l, false, idLige)),
    [druge, nizje],
  )
  const moznostiNizje = useMemo(
    () =>
      druge
        .filter((l) => l.id !== idVisja && !nizje.includes(l.id))
        .map((l) => moznostLige(l, true, idLige)),
    [druge, idVisja, nizje, idLige],
  )
  const visja = idVisja !== null ? vse.find((l) => l.id === idVisja) : undefined
  const izbraneNizje = nizje
    .map((id) => vse.find((l) => l.id === id))
    .filter((l): l is LigaDto => l !== undefined)

  /* Piramida veže lige ene sezone; drugačna sezona je skoraj vedno spregled
     (npr. »8. Sezona« proti »25/26«), zato nanjo opozorimo, a je ne prepovemo. */
  const sezone = new Set(
    [visja, ...izbraneNizje]
      .filter((l): l is LigaDto => l !== undefined)
      .map((l) => l.sezona ?? ''),
  )
  const razhajanjeSezon = sezone.size > 0 && !(sezone.size === 1 && sezone.has(sezona ?? ''))

  /* Po parih (ena tekma, serija) se kvalifikacije igrajo le, če obe ligi dasta
     enako ekip — razhajanje ni napaka (mala liga ga prenese), a je skoraj
     vedno spregled, zato ga povemo ob vpisu in ne šele ob koncu sezone. */
  const neujemanja: string[] = []
  if (visja && (stanje.kvalGor > 0 || visja.stKvalifikacijeDol > 0)
      && stanje.kvalGor !== visja.stKvalifikacijeDol) {
    neujemanja.push(
      `${visja.ime} ima za obstanek ${visja.stKvalifikacijeDol}, ta liga za napredovanje ${stanje.kvalGor}`,
    )
  }
  for (const n of izbraneNizje) {
    if ((stanje.kvalDol > 0 || n.stKvalifikacijeGor > 0) && stanje.kvalDol !== n.stKvalifikacijeGor) {
      neujemanja.push(
        `${n.ime} ima za napredovanje ${n.stKvalifikacijeGor}, ta liga za obstanek ${stanje.kvalDol}`,
      )
    }
  }

  return (
    <>
      <IskalniIzbirnik
        vObrazcu
        oznaka="Višja liga (kamor se napreduje)"
        namig="Brez (najvišja liga) · vpiši ime"
        moznosti={moznostiVisje}
        izbrano={idVisja}
        naIzbiro={(id) => popravi({ idVisja: id })}
        naPraznjenje={() => popravi({ idVisja: null })}
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
                  {l.idVisjaLiga != null && l.idVisjaLiga !== idLige && (
                    <span className="izbrane-postavke__podrobnost">
                      zdaj pod: {l.visjaLigaIme} — ob shranitvi se premakne sem
                    </span>
                  )}
                </span>
                <button
                  type="button"
                  className="gumb gumb--majhen gumb--nevaren"
                  onClick={() => popravi({ nizje: nizje.filter((id) => id !== l.id) })}
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
            naIzbiro={(id) => popravi({ nizje: [...nizje, id] })}
          />
        )}
      </fieldset>

      {/* Neposredni prehod in kvalifikacije zanj stojita v isti vrsti: na
          lestvici sta sosednja pasova (kvalifikacije tik pod napredovanjem
          oz. tik nad izpadom). */}
      <div className="obrazec__vrstica">
        <label className="obrazec__polje">
          <span>Napreduje (ekip)</span>
          <StevilskoPolje vrednost={String(stanje.napreduje)}
            naSpremembo={(v) => popravi({ napreduje: Number(v) })} />
        </label>
        <label className="obrazec__polje">
          <span>Kvalifikacije za napredovanje</span>
          <StevilskoPolje vrednost={String(stanje.kvalGor)}
            naSpremembo={(v) => popravi({ kvalGor: Number(v) })} />
        </label>
      </div>
      <div className="obrazec__vrstica">
        <label className="obrazec__polje">
          <span>Izpade (ekip)</span>
          <StevilskoPolje vrednost={String(stanje.izpade)}
            naSpremembo={(v) => popravi({ izpade: Number(v) })} />
        </label>
        <label className="obrazec__polje">
          <span>Kvalifikacije za obstanek</span>
          <StevilskoPolje vrednost={String(stanje.kvalDol)}
            naSpremembo={(v) => popravi({ kvalDol: Number(v) })} />
        </label>
      </div>
      <p className="namig">
        Kvalifikacije igrajo ekipe tik pod mesti napredovanja oz. tik nad mesti
        izpada — s sosednjo ligo, za mesto v višji (npr. 9. iz A proti 3. iz B).
        Ko je redni del obeh lig odigran, jih organizator višje lige ustvari na
        strani lige.
      </p>

      {neujemanja.length > 0 && (
        <p className="namig">
          Število kvalifikacijskih mest se ne ujema ({neujemanja.join('; ')}). Ena
          tekma ali serija zahteva enako ekip z obeh strani — sicer se
          kvalifikacije lahko igrajo le kot mala liga (vsak z vsakim).
        </p>
      )}

      {razhajanjeSezon && (
        <p className="namig">
          Povezane lige nimajo iste sezone kot ta ({sezona || 'brez sezone'}).
          Povezava bo delovala, piramida pa bo mešala sezone — če gre za isto
          sezono, sezone poenoti.
        </p>
      )}
    </>
  )
}

/* Sezona je del imena lige v izbirniku: brez nje sta »Savinja liga B« dveh
   sezon nerazločljivi. */
function opis(l: LigaDto): string {
  return l.sezona ? `${l.ime} · ${l.sezona}` : l.ime
}

/* Liga kot predlog. Pri nižji ligi podrobnost pove, pod katero ligo je zdaj
   — z izbiro se bo premaknila sem (idTe je null pri novi ligi). */
function moznostLige(l: LigaDto, kotNizja: boolean, idTe: number | null): MoznostIzbirnika {
  const drugje = kotNizja && l.idVisjaLiga != null && l.idVisjaLiga !== idTe
  return {
    id: l.id,
    ime: opis(l),
    podrobnost: drugje ? `zdaj pod: ${l.visjaLigaIme}` : null,
  }
}
