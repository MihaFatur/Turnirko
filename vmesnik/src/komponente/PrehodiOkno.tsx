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
   ve, da je pod A in da 3. igra kvalifikacije, še preden ima liga ekipe.

   Diagram je prenova po design_handoff_termini_prehodi (2c): od zgoraj navzdol
   višja liga → napredovanje → ta liga (pasovi lestvice) → izpad → nižje lige. */
import { useMemo, useState, type FormEvent } from 'react'
import { useMutation } from '@tanstack/react-query'

import { ligeApi } from '../api/zahteve'
import type { LigaDto, PrehodiVnos } from '../api/tipi'
import { IskalniIzbirnik, type MoznostIzbirnika } from './IskalniIzbirnik'
import { ModalnoOkno } from './ModalnoOkno'
import { SporociloNapake } from './SporociloNapake'
import { StevilskiKorak } from './StevilskiKorak'

interface Lastnosti {
  liga: LigaDto
  /* Vse lige (seznam s strani lige) — iz njih se sestavita oba izbirnika. */
  vse: LigaDto[]
  onZapri: () => void
  onShranjeno: () => void
}

/* Število ekip, ki ga prikazuje primer lestvice pod diagramom. Ni podatek lige:
   pasovi pokažejo, kje bi bila mesta, če bi bilo ekip toliko. */
const PRIMER_EKIP = 10

export function PrehodiOkno({ liga, vse, onZapri, onShranjeno }: Lastnosti) {
  const zacetni = useMemo(() => zacetniPrehodi(liga, vse), [liga, vse])
  const [stanje, nastaviStanje] = useState<PrehodiStanje>(() => zacetniPrehodi(liga, vse))
  /* Shrani je onemogočen, dokler ni spremembe — isto kot pri terminih kol. */
  const spremenjeno = JSON.stringify(stanje) !== JSON.stringify(zacetni)

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
    <ModalnoOkno srednje naslov="Prehodi in piramida"
      podnaslov={`${liga.ime} · ${liga.sezona ?? ''}`.trim()} onZapri={onZapri}>
      <form className="obrazec" onSubmit={obOddaji}>
        <p className="prehodi__uvod">
          Povezavo vpiši z ene strani, nasprotna se uredi sama. Prehode lahko urejaš tudi, ko liga že teče.
        </p>

        <PrehodiPolja
          stanje={stanje}
          nastavi={nastaviStanje}
          vse={vse}
          idLige={liga.id}
          sezona={liga.sezona}
          imeLige={liga.ime}
        />

        <SporociloNapake napaka={shranjevanje.error} />
        <div className="obrazec__noga">
          <span className={'obrazec__noga-stanje' + (spremenjeno ? ' obrazec__noga-stanje--spremembe' : '')}>
            {spremenjeno ? 'Neshranjene spremembe' : 'Ni neshranjenih sprememb'}
          </span>
          <div className="obrazec__noga-gumbi">
            <button type="button" className="gumb" onClick={onZapri}>Prekliči</button>
            <button type="submit" className="gumb gumb--glavni"
              disabled={!spremenjeno || shranjevanje.isPending}>
              Shrani prehode
            </button>
          </div>
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
  imeLige,
}: {
  stanje: PrehodiStanje
  nastavi: (s: PrehodiStanje) => void
  vse: LigaDto[]
  /* null pri novi ligi (te v seznamu še ni). */
  idLige: number | null
  sezona: string | null
  /* Ime »te« lige v črnem bloku diagrama; pri novi ligi ime iz obrazca. */
  imeLige: string
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
    [druge, nizje, idLige],
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
  const sezoneLig = new Set(
    [visja, ...izbraneNizje]
      .filter((l): l is LigaDto => l !== undefined)
      .map((l) => l.sezona ?? ''),
  )
  const razhajanjeSezon = sezoneLig.size > 0 && !(sezoneLig.size === 1 && sezoneLig.has(sezona ?? ''))

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

  /* Vsa prehodna mesta skupaj; več kot ima primer ekip, pasovi se prekrivajo. */
  const prehodnaMesta = stanje.napreduje + stanje.kvalGor + stanje.izpade + stanje.kvalDol

  return (
    <>
      <div className="prehodi">
        <div className="prehodi__sklop">
          <span className="oznaka-polja">Višja liga · kamor se napreduje</span>
          {visja ? (
            <div className="prehodi__liga">
              <div>
                <span className="prehodi__liga-ime">{visja.ime}</span>
                <span className="prehodi__liga-meta">{visja.sezona ?? ''}</span>
              </div>
              <button type="button" className="gumb gumb--majhen gumb--nevaren"
                onClick={() => popravi({ idVisja: null })}>
                Odstrani
              </button>
            </div>
          ) : (
            /* Oznaka je skrita: napis nad poljem (»Višja liga · kamor se napreduje«) to pove. */
            <IskalniIzbirnik
              oznaka="Višja liga"
              namig="Brez (najvišja liga) · vpiši ime"
              moznosti={moznostiVisje}
              naIzbiro={(id) => popravi({ idVisja: id })}
            />
          )}
        </div>

        <section className="prehodi__tok">
          <div className="prehodi__tok-glava">
            <span className="prehodi__tok-smer">↑ Napredovanje</span>
            <span className="prehodi__tok-kam">{visja ? `v ${visja.ime}` : 'višje lige ni'}</span>
          </div>
          <div className="prehodi__tok-polja">
            <div className="prehodi__polje">
              <span className="prehodi__polje-oznaka">
                <span className="prehodi__vzorec prehodi__vzorec--gor" aria-hidden="true" />
                Napreduje
              </span>
              <StevilskiKorak vrednost={stanje.napreduje}
                naSpremembo={(v) => popravi({ napreduje: v })}
                oznaka="Napreduje (ekip)" oznakaManj="Manj"
                oznakaVec="Več" />
              <span className="prehodi__polje-pod">ekip gre neposredno v višjo</span>
            </div>
            <div className="prehodi__polje">
              <span className="prehodi__polje-oznaka">
                <span className="prehodi__vzorec prehodi__vzorec--kval-gor" aria-hidden="true" />
                Kvalifikacije
              </span>
              <StevilskiKorak vrednost={stanje.kvalGor}
                naSpremembo={(v) => popravi({ kvalGor: v })}
                oznaka="Kvalifikacije za napredovanje (ekip)"
                oznakaManj="Manj"
                oznakaVec="Več" />
              <span className="prehodi__polje-pod">ekip igra za mesto v višji</span>
            </div>
          </div>
        </section>

        <section className="prehodi__ta">
          <div className="prehodi__ta-glava">
            <span className="prehodi__ta-ime">{imeLige}{sezona ? ` · ${sezona}` : ''}</span>
            <span className="prehodi__ta-oznaka">ta liga</span>
          </div>
          <div className="prehodi__pasovi">
            <div className="prehodi__pasovi-vrsta">
              {Array.from({ length: PRIMER_EKIP }, (_, i) => (
                <span key={i} className={'prehodi__pas' + razredPasu(i + 1, stanje)}>{i + 1}</span>
              ))}
            </div>
            <span className="prehodi__pasovi-opis">Mesta na lestvici · primer za {PRIMER_EKIP} ekip</span>
          </div>
        </section>

        <section className="prehodi__tok prehodi__tok--dol">
          <div className="prehodi__tok-glava">
            <span className="prehodi__tok-smer">↓ Izpad</span>
            <span className="prehodi__tok-kam">
              {izbraneNizje.length > 0
                ? `v ${izbraneNizje.map((l) => l.ime).join(', ')}`
                : 'nižjih lig ni'}
            </span>
          </div>
          <div className="prehodi__tok-polja">
            <div className="prehodi__polje">
              <span className="prehodi__polje-oznaka">
                <span className="prehodi__vzorec prehodi__vzorec--dol" aria-hidden="true" />
                Izpade
              </span>
              <StevilskiKorak vrednost={stanje.izpade}
                naSpremembo={(v) => popravi({ izpade: v })}
                oznaka="Izpade (ekip)" oznakaManj="Manj"
                oznakaVec="Več" />
              <span className="prehodi__polje-pod">ekip gre neposredno v nižjo</span>
            </div>
            <div className="prehodi__polje">
              <span className="prehodi__polje-oznaka">
                <span className="prehodi__vzorec prehodi__vzorec--kval-dol" aria-hidden="true" />
                Kvalifikacije
              </span>
              <StevilskiKorak vrednost={stanje.kvalDol}
                naSpremembo={(v) => popravi({ kvalDol: v })}
                oznaka="Kvalifikacije za obstanek (ekip)"
                oznakaManj="Manj"
                oznakaVec="Več" />
              <span className="prehodi__polje-pod">ekip igra za obstanek</span>
            </div>
          </div>
        </section>

        <div className="prehodi__sklop">
          <span className="oznaka-polja">Nižje lige · od koder se napreduje sem</span>
          {izbraneNizje.map((l) => (
            <div key={l.id} className="prehodi__liga">
              <div>
                <span className="prehodi__liga-ime">{l.ime}</span>
                <span className="prehodi__liga-meta">{l.sezona ?? ''}</span>
                {l.idVisjaLiga != null && l.idVisjaLiga !== idLige && (
                  <span className="prehodi__liga-premik">
                    zdaj pod: {l.visjaLigaIme}; ob shranitvi se premakne sem
                  </span>
                )}
              </div>
              <button type="button" className="gumb gumb--majhen gumb--nevaren"
                onClick={() => popravi({ nizje: nizje.filter((id) => id !== l.id) })}>
                Odstrani
              </button>
            </div>
          ))}
          {druge.length === 0 ? (
            <p className="namig">Drugih lig ni.</p>
          ) : (
            /* Nižjih lig je lahko več, zato je polje dejanje: izbrana liga gre v
               seznam nad njim, polje pa se izprazni za naslednjo. */
            <IskalniIzbirnik
              oznaka="Dodaj nižjo ligo"
              namig="Dodaj nižjo ligo · vpiši ime"
              moznosti={moznostiNizje}
              naIzbiro={(id) => popravi({ nizje: [...nizje, id] })}
            />
          )}
        </div>
      </div>

      {neujemanja.length > 0 && (
        <p className="prehodi__opozorilo">
          Število kvalifikacijskih mest se ne ujema ({neujemanja.join('; ')}). Ena tekma ali serija
          zahteva enako ekip z obeh strani, sicer se kvalifikacije lahko igrajo le kot mala liga
          (vsak z vsakim).
        </p>
      )}
      {razhajanjeSezon && (
        <p className="prehodi__opozorilo">
          Povezane lige nimajo iste sezone kot ta ({sezona || 'brez sezone'}). Povezava bo delovala,
          piramida pa bo mešala sezone; če gre za isto sezono, jo poenoti.
        </p>
      )}
      {prehodnaMesta > PRIMER_EKIP && (
        <p className="prehodi__opozorilo">
          Skupaj je več prehodnih mest, kot ima primer ekip ({PRIMER_EKIP}); pasovi se prekrivajo.
        </p>
      )}
      <p className="prehodi__namig">
        Kvalifikacije igrajo ekipe tik pod mesti napredovanja oz. tik nad mesti izpada, s sosednjo
        ligo (npr. 9. iz A proti 3. iz B). Ko je redni del obeh lig odigran, jih organizator višje
        lige ustvari na strani lige.
      </p>
    </>
  )
}

/* Barva pasu lestvice po mestu r; prednost ima zgornje (napredovanje pred
   kvalifikacijami, kvalifikacije pred izpadom). */
function razredPasu(r: number, s: PrehodiStanje): string {
  if (r <= s.napreduje) return ' prehodi__pas--gor'
  if (r <= s.napreduje + s.kvalGor) return ' prehodi__pas--kval-gor'
  if (r > PRIMER_EKIP - s.izpade) return ' prehodi__pas--dol'
  if (r > PRIMER_EKIP - s.izpade - s.kvalDol) return ' prehodi__pas--kval-dol'
  return ''
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
