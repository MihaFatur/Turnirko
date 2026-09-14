/* Uvoz NTZS: tekmovanja zveze iz sistema Stupa (samo administrator).

   Potek za eno tekmovanje:
   1. PREDOGLED - strežnik naredi posnetek dogodka pri viru, ga poskusno zapiše
      in uskladi z virom tekmo za tekmo, nato zapis razveljavi. Admin vidi
      preverbe, odločitve o istovetnosti oseb, nove igralce in opozorila.
   2. ODLOČITVE - osebo, ki je strežnik ne zna varno povezati z registrom, admin
      poveže z igralcem ali jo zapiše kot novega igralca in vpiše, česar vir
      nima. Dovoljenje za uvoz velja samo za predogled z ISTIMI odločitvami, zato
      se po vsaki spremembi predogled ponovi (gumb Uvozi do takrat ugasne).
   3. UVOZ - nad istim posnetkom in z istimi odločitvami; po potrditvi strežnik
      preračuna rating od dneva tekmovanja.

   Uvoženo tekmovanje je v aplikaciji samo za branje - popravki gredo v Stupo
   in nato znova skozi uvoz. Datumi rojstva tu niso javni: pot /api/v1/uvoz/**
   je samo za administratorja. */
import { useEffect, useMemo, useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'

import { uvozApi } from '../api/zahteve'
import type {
  IzidUvozaDto,
  NovIgralecUvoza,
  OdlocitevIstovetnosti,
  OdlocitevUvoza,
  PorociloUvozaDto,
  PredogledUvozaDto,
  Spol,
  UgotovitevUvoza,
  UvozDogodekDto,
  UvozZagonDto,
} from '../api/tipi'
import { OZNAKE_IZIDA_UVOZA } from '../api/tipi'
import { NapakaPoizvedbe } from '../komponente/NapakaPoizvedbe'
import { SporociloNapake } from '../komponente/SporociloNapake'
import { oblikujDatum, oblikujObdobje } from '../pomozno/oblikovanje'

export function UvozStran() {
  const sezone = useQuery({ queryKey: ['uvoz-sezone'], queryFn: uvozApi.sezone, staleTime: 10 * 60_000 })
  const [idSezone, nastaviIdSezone] = useState<number | null>(null)
  const izbranaSezona = idSezone ?? sezone.data?.find((s) => s.tekoca)?.id ?? null
  const dogodki = useQuery({
    queryKey: ['uvoz-dogodki', izbranaSezona],
    queryFn: () => uvozApi.dogodki(izbranaSezona ?? undefined),
    enabled: sezone.isSuccess,
  })
  /* Odprt je vedno en dogodek: predogled je težak (posnetek pri viru in
     poskusni zapis), dva hkrati pa bi se v bazi prekrivala. */
  const [odprt, nastaviOdprt] = useState<number | null>(null)

  const seznam = dogodki.data ?? []
  const uvozenih = seznam.filter((d) => d.idTurnir !== null || d.idLiga !== null).length

  return (
    <section className="uvoz">
      <div className="stran-glava stran-glava--ozka">
        <div>
          <h1 className="naslov-strani">
            <span className="naslov-strani__nad">Administracija</span>
            <span className="naslov-strani__glavni">Uvoz NTZS</span>
          </h1>
          <p className="uvod">
            Turnirji in lige zveze iz sistema Stupa. Predogled tekmovanje poskusno zapiše in ga
            tekmo za tekmo uskladi z virom; uvoz ga potrdi in preračuna rating. Uvoženo
            tekmovanje je v aplikaciji samo za branje.
          </p>
        </div>
      </div>

      <div className="naslovna-vrstica">
        <h2>Tekmovanja</h2>
        <div className="naslovna-vrstica__desno">
          {dogodki.data && (
            <span className="sekcija__meta">
              {uvozenih} od {seznam.length} uvoženih
            </span>
          )}
          <label className="uvoz__sezona">
            <span className="samo-za-bralnik">Sezona</span>
            <select
              value={izbranaSezona ?? ''}
              disabled={!sezone.data}
              onChange={(d) => {
                nastaviIdSezone(Number(d.target.value))
                nastaviOdprt(null)
              }}
            >
              {sezone.data?.map((s) => (
                <option key={s.id} value={s.id}>
                  {s.ime ?? `Sezona ${s.id}`}
                  {s.tekoca ? ' (tekoča)' : ''}
                </option>
              ))}
            </select>
          </label>
        </div>
      </div>

      <NapakaPoizvedbe poizvedba={sezone} kaj="sezon" />
      <NapakaPoizvedbe poizvedba={dogodki} kaj="tekmovanj pri Stupi" />
      {(sezone.isPending || dogodki.isLoading) && <p className="obvestilo">Nalaganje tekmovanj …</p>}
      {dogodki.data && seznam.length === 0 && (
        <p className="obvestilo">V tej sezoni pri Stupi ni objavljenih tekmovanj.</p>
      )}

      <ul className="uvoz__dogodki">
        {seznam.map((d) => (
          <li key={d.id}>
            <VrsticaDogodka
              dogodek={d}
              odprt={odprt === d.id}
              onPreklop={() => nastaviOdprt(odprt === d.id ? null : d.id)}
            />
            {odprt === d.id && <DelUvoza dogodek={d} />}
          </li>
        ))}
      </ul>

      <Dnevnik />
    </section>
  )
}

/* ---------- Vrstica tekmovanja ---------- */

function VrsticaDogodka({
  dogodek,
  odprt,
  onPreklop,
}: {
  dogodek: UvozDogodekDto
  odprt: boolean
  onPreklop: () => void
}) {
  const z = dogodek.zadnjiUvoz
  const pot = dogodek.idTurnir !== null ? `/turnirji/${dogodek.idTurnir}`
    : dogodek.idLiga !== null ? `/lige/${dogodek.idLiga}` : null
  return (
    <div className={'uvoz__dogodek' + (odprt ? ' uvoz__dogodek--odprt' : '')}>
      <span className="uvoz__datum">{oblikujObdobje(dogodek.zacetek, dogodek.konec)}</span>
      <span className="uvoz__ime">
        {pot ? <Link to={pot}>{dogodek.ime}</Link> : dogodek.ime}
        <span className="uvoz__pod">
          {dogodek.vrsta === 'LIGA' ? 'Liga' : 'Turnir'} · Stupa {dogodek.id}
          {z && ` · zadnji uvoz ${oblikujCas(z.zacetekOb)}`}
        </span>
      </span>
      <span className="uvoz__stanje-dogodka">
        {z?.izid ? (
          <span className={'znacka' + razredIzida(z.izid)}>{OZNAKE_IZIDA_UVOZA[z.izid]}</span>
        ) : pot ? (
          <span className="znacka">uvoženo</span>
        ) : (
          <span className="znacka">ni uvoženo</span>
        )}
      </span>
      <button type="button" className={'gumb gumb--majhen' + (odprt ? '' : ' gumb--glavni')} onClick={onPreklop}>
        {odprt ? 'Zapri' : 'Predogled'}
      </button>
    </div>
  )
}

function razredIzida(izid: string | null): string {
  if (izid === 'USPEH') return ' znacka--uspeh'
  if (izid === 'NAPAKA' || izid === 'ZAVRNJENO') return ' znacka--opozorilo'
  return ''
}

/* "2026-09-13T08:40:10.224" -> "13. 9. 2026 · 08.40" */
function oblikujCas(iso: string | null): string {
  if (!iso) return ''
  const [datum, cas] = iso.split('T')
  return `${oblikujDatum(datum)}${cas ? ` · ${cas.slice(0, 5).replace(':', '.')}` : ''}`
}

/* ---------- Predogled, odločitve in uvoz enega tekmovanja ---------- */

/* Odločitve so urejene po osebi, da ima isti nabor vedno isti ključ - po njem
   se ve, ali je predogled še veljaven. */
function kljucOdlocitev(odlocitve: Record<number, OdlocitevUvoza>): string {
  return JSON.stringify(Object.values(odlocitve).sort((a, b) => a.idOsebe - b.idOsebe))
}

function DelUvoza({ dogodek }: { dogodek: UvozDogodekDto }) {
  const odjemalec = useQueryClient()
  const [odlocitve, nastaviOdlocitve] = useState<Record<number, OdlocitevUvoza>>({})
  const [predogled, nastaviPredogled] = useState<PredogledUvozaDto | null>(null)
  const [kljucPredogleda, nastaviKljucPredogleda] = useState('')
  const [vsili, nastaviVsili] = useState(false)
  const [izid, nastaviIzid] = useState<IzidUvozaDto | null>(null)

  const kljuc = kljucOdlocitev(odlocitve)
  const zastarel = predogled !== null && kljuc !== kljucPredogleda

  const predogledZahteva = useMutation({
    mutationFn: (vnos: { odlocitve: OdlocitevUvoza[]; kljuc: string }) =>
      uvozApi.predogled(dogodek.id, { posnetek: null, odlocitve: vnos.odlocitve, vsili: false }),
    onSuccess: (p, vnos) => {
      nastaviPredogled(p)
      nastaviKljucPredogleda(vnos.kljuc)
      nastaviIzid(null)
    },
  })
  const uvozZahteva = useMutation({
    mutationFn: () =>
      uvozApi.uvozi(dogodek.id, { posnetek: predogled!.posnetek, odlocitve: Object.values(odlocitve), vsili }),
    onSuccess: (r) => {
      nastaviIzid(r)
      // uvoz spremeni tekmovanja, igralce in rating - osveži se vse
      odjemalec.invalidateQueries()
    },
  })

  /* Predogled se zažene ob odprtju; zaščita pred dvojnim zagonom v razvojnem
     načinu (StrictMode požene učinek dvakrat), ker je vsak predogled nov
     posnetek pri viru. */
  const zagnan = useRef(false)
  useEffect(() => {
    if (zagnan.current) return
    zagnan.current = true
    predogledZahteva.mutate({ odlocitve: [], kljuc: kljucOdlocitev({}) })
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  function ponoviPredogled() {
    predogledZahteva.mutate({ odlocitve: Object.values(odlocitve), kljuc })
  }

  function nastaviOdlocitev(idOsebe: number, vrednost: OdlocitevUvoza | null) {
    nastaviOdlocitve((prej) => {
      const nove = { ...prej }
      if (vrednost === null) delete nove[idOsebe]
      else nove[idOsebe] = vrednost
      return nove
    })
  }

  const porocilo = predogled?.porocilo
  const neodlocene = porocilo?.odlocitve.filter((o) => !odlocitevPopolna(o, odlocitve[o.idOsebe])) ?? []
  const lahkoUvozim = predogled !== null && predogled.dovoljuje && !zastarel
    && (!predogled.enakKotZadnjic || vsili) && !uvozZahteva.isPending && izid?.izid !== 'USPEH'

  return (
    <div className="uvoz__del">
      {predogledZahteva.isPending && (
        <p className="obvestilo">
          Predogled teče: posnetek tekmovanja pri Stupi, poskusni zapis in uskladitev z virom. Pri
          velikem turnirju ali ligi traja nekaj sekund.
        </p>
      )}
      <SporociloNapake napaka={predogledZahteva.error} />

      {predogled && porocilo && (
        <>
          <StanjePredogleda predogled={predogled} zastarel={zastarel} />

          <Stevci stevci={porocilo.stevci} />

          <Preverbe porocilo={porocilo} />

          {porocilo.napake.length > 0 && (
            <Ugotovitve naslov="Napake" ugotovitve={porocilo.napake} napake />
          )}

          {porocilo.odlocitve.length > 0 && (
            <div className="uvoz__sklop">
              <div className="naslovna-vrstica naslovna-vrstica--manjsa">
                <h3>Odločitve o istovetnosti</h3>
                <span className="sekcija__meta">
                  {porocilo.odlocitve.length - neodlocene.length} od {porocilo.odlocitve.length} odločenih
                </span>
              </div>
              <p className="namig">
                Teh oseb strežnik ne zna varno povezati z registrom. Izberi igralca iz registra ali
                zapiši novega - in vpiši, česar vir nima. Nato ponovi predogled.
              </p>
              {porocilo.odlocitve.map((o) => (
                <OdlocitevOsebe
                  key={o.idOsebe}
                  oseba={o}
                  vrednost={odlocitve[o.idOsebe]}
                  onSprememba={(v) => nastaviOdlocitev(o.idOsebe, v)}
                />
              ))}
            </div>
          )}

          {porocilo.noviIgralci.length > 0 && (
            <NoviIgralci
              seznam={porocilo.noviIgralci}
              odlocitve={odlocitve}
              onPopravek={(idOsebe, v) => nastaviOdlocitev(idOsebe, v)}
            />
          )}

          {porocilo.opozorila.length > 0 && (
            <Ugotovitve naslov="Opozorila" ugotovitve={porocilo.opozorila} />
          )}

          <div className="uvoz__dejanja">
            <button
              type="button"
              className={'gumb' + (zastarel ? ' gumb--glavni' : '')}
              disabled={predogledZahteva.isPending}
              onClick={ponoviPredogled}
            >
              {predogledZahteva.isPending ? 'Predogled teče …' : 'Ponovi predogled'}
            </button>
            {predogled.enakKotZadnjic && (
              <label className="obrazec__potrditev">
                <input type="checkbox" checked={vsili} onChange={(d) => nastaviVsili(d.target.checked)} />
                <span>Uvozi znova, čeprav je posnetek enak zadnjemu uvozu</span>
              </label>
            )}
            <button
              type="button"
              className="gumb gumb--zreb"
              disabled={!lahkoUvozim}
              onClick={() => uvozZahteva.mutate()}
            >
              {uvozZahteva.isPending ? 'Uvažam in preračunavam rating …' : 'Uvozi'}
            </button>
          </div>
          {zastarel && (
            <p className="namig">Odločitve so se spremenile - pred uvozom ponovi predogled.</p>
          )}
          <SporociloNapake napaka={uvozZahteva.error} />
          {izid && <IzidUvoza izid={izid} />}
        </>
      )}
    </div>
  )
}

/* Ena vrstica, ki pove, ali je uvoz mogoč, in če ni, zakaj. */
function StanjePredogleda({ predogled, zastarel }: { predogled: PredogledUvozaDto; zastarel: boolean }) {
  const p = predogled.porocilo
  const neujemanj = p.preverbe.filter((x) => x.obvezna && !x.ujemanje).length
  let besedilo: string
  if (p.napake.length > 0) {
    besedilo = `Uvoz ni mogoč: ${p.napake.length} ${p.napake.length === 1 ? 'vrsta napake' : 'vrst napak'} pri preslikavi.`
  } else if (p.odlocitve.length > 0) {
    besedilo = `Čaka na odločitve: ${p.odlocitve.length} ${p.odlocitve.length === 1 ? 'oseba' : 'oseb'}.`
  } else if (neujemanj > 0) {
    besedilo = `Uvoz ni mogoč: ${neujemanj} obveznih preverb se z virom ne ujema.`
  } else if (p.preverbe.length === 0) {
    /* Brez preverb ni bilo česa primerjati (turnir se še ni začel) - »ujema
       se v vseh preverbah« bi bilo prazno zagotovilo. */
    besedilo = 'Pripravljeno za uvoz - pri viru še ni odigranih tekem, zato preverb ni.'
  } else {
    besedilo = 'Pripravljeno za uvoz - zapisano se v vseh obveznih preverbah ujema z virom.'
  }
  return (
    <div className={'uvoz__stanje' + (predogled.dovoljuje ? ' uvoz__stanje--ok' : ' uvoz__stanje--ne')}>
      <strong>{besedilo}</strong>
      <span className="uvoz__pod">
        Posnetek {predogled.posnetek}
        {predogled.enakKotZadnjic && ' · enak zadnjemu uspešnemu uvozu'}
        {zastarel && ' · odločitve so se od predogleda spremenile'}
      </span>
    </div>
  )
}

/* Števci zapisa: koliko česa bi uvoz zapisal. */
function Stevci({ stevci }: { stevci: Record<string, number> }) {
  const vnosi = Object.entries(stevci)
  if (vnosi.length === 0) return null
  return (
    <dl className="uvoz__stevci">
      {vnosi.map(([kaj, koliko]) => (
        <div key={kaj} className="uvoz__stevec">
          <dt>{kaj}</dt>
          <dd>{koliko}</dd>
        </div>
      ))}
    </dl>
  )
}

/* Preverbe uskladitve: obvezne neujemanje ustavijo uvoz, razlike (uradni
   vrstni red izenačenih) se samo pokažejo. Neujemanja stojijo na vrhu. */
function Preverbe({ porocilo }: { porocilo: PorociloUvozaDto }) {
  const urejene = [...porocilo.preverbe].sort((a, b) => utez(a) - utez(b))
  if (urejene.length === 0) {
    return porocilo.napake.length > 0 ? (
      <p className="namig">Uskladitve ni bilo - preslikava se je ustavila pri napakah.</p>
    ) : null
  }
  return (
    <div className="uvoz__sklop">
      <div className="naslovna-vrstica naslovna-vrstica--manjsa">
        <h3>Uskladitev z virom</h3>
        <span className="sekcija__meta">
          {urejene.filter((x) => x.ujemanje).length} od {urejene.length} se ujema
        </span>
      </div>
      <ul className="uvoz__preverbe">
        {urejene.map((x, i) => (
          <li key={i} className="uvoz__preverba">
            <span
              className={
                'uvoz__preverba-stanje ' +
                (x.ujemanje ? 'uvoz__preverba-stanje--ok' : x.obvezna ? 'uvoz__preverba-stanje--ne' : 'uvoz__preverba-stanje--razlika')
              }
            >
              {x.ujemanje ? 'ujema' : x.obvezna ? 'neujemanje' : 'razlika'}
            </span>
            <span>
              <span className="uvoz__preverba-opis">
                {x.podrocje}: {x.opis}
              </span>
              {x.podrobnosti && <span className="uvoz__pod">{x.podrobnosti}</span>}
            </span>
          </li>
        ))}
      </ul>
    </div>
  )
}

function utez(x: { ujemanje: boolean; obvezna: boolean }): number {
  if (!x.ujemanje && x.obvezna) return 0
  if (!x.ujemanje) return 1
  return 2
}

/* Napake ali opozorila: vrsta, kolikokrat in nekaj primerov. Opozorila so
   zaprta - pri velikem turnirju jih je deset vrst in bi prekrila preverbe. */
function Ugotovitve({
  naslov,
  ugotovitve,
  napake = false,
}: {
  naslov: string
  ugotovitve: UgotovitevUvoza[]
  napake?: boolean
}) {
  const [odprto, nastaviOdprto] = useState(napake)
  const skupaj = ugotovitve.reduce((s, u) => s + u.stevilo, 0)
  return (
    <div className="uvoz__sklop">
      <button
        type="button"
        className="zlozljiva__glava uvoz__zlozljiva"
        aria-expanded={odprto}
        onClick={() => nastaviOdprto(!odprto)}
      >
        <span className={'zlozljiva__naslov' + (napake ? ' uvoz__naslov-napak' : '')}>{naslov}</span>
        <span className="zlozljiva__meta">
          {ugotovitve.length} vrst · {skupaj}
          <span className="zlozljiva__znak" aria-hidden="true">{odprto ? '▴' : '▾'}</span>
        </span>
      </button>
      {odprto && (
        <ul className="uvoz__ugotovitve">
          {ugotovitve.map((u) => (
            <li key={u.vrsta}>
              <span className="uvoz__ugotovitev">
                {u.vrsta} <span className="uvoz__stevilo">×{u.stevilo}</span>
              </span>
              {u.primeri.length > 0 && (
                <ul className="uvoz__primeri">
                  {u.primeri.map((pr, i) => (
                    <li key={i}>{pr}</li>
                  ))}
                  {u.stevilo > u.primeri.length && <li>… in še {u.stevilo - u.primeri.length}</li>}
                </ul>
              )}
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}

/* ---------- Odločitve ---------- */

function odlocitevPopolna(o: OdlocitevIstovetnosti, v: OdlocitevUvoza | undefined): boolean {
  if (!v) return false
  if (v.idIgralec !== null) return true
  if (o.manjka.includes('IME') && !((v.ime?.trim().length ?? 0) >= 2 && (v.priimek?.trim().length ?? 0) >= 2)) {
    return false
  }
  if (o.manjka.includes('ROJSTVO') && !v.datumRojstva) return false
  if (o.manjka.includes('SPOL') && !v.spol) return false
  return true
}

function OdlocitevOsebe({
  oseba,
  vrednost,
  onSprememba,
}: {
  oseba: OdlocitevIstovetnosti
  vrednost: OdlocitevUvoza | undefined
  onSprememba: (v: OdlocitevUvoza | null) => void
}) {
  const ime = `odlocitev-${oseba.idOsebe}`
  const nov = vrednost !== undefined && vrednost.idIgralec === null
  const podrobnosti = [
    oseba.rojstvo ? `r. ${oblikujDatum(oseba.rojstvo)}` : 'brez datuma rojstva',
    oseba.spol === 'MOSKI' ? 'moški' : oseba.spol === 'ZENSKI' ? 'ženska' : null,
    oseba.licenca ? `licenca ${oseba.licenca}` : null,
    oseba.klub,
    `Stupa ${oseba.idOsebe}`,
  ].filter(Boolean).join(' · ')

  function popravi(polja: Partial<OdlocitevUvoza>) {
    onSprememba({ ...(vrednost as OdlocitevUvoza), ...polja })
  }

  return (
    <fieldset className={'uvoz__oseba' + (odlocitevPopolna(oseba, vrednost) ? ' uvoz__oseba--odlocena' : '')}>
      <legend className="uvoz__oseba-ime">{oseba.ime ?? '(brez imena)'}</legend>
      <p className="uvoz__pod">{podrobnosti}</p>
      <p className="uvoz__razlog">{oseba.razlog}</p>

      {oseba.kandidati.map((k) => (
        <label key={k.idIgralec} className="uvoz__moznost">
          <input
            type="radio"
            name={ime}
            checked={vrednost?.idIgralec === k.idIgralec}
            onChange={() =>
              onSprememba({ idOsebe: oseba.idOsebe, idIgralec: k.idIgralec, ime: null, priimek: null, datumRojstva: null, spol: null })
            }
          />
          <span>
            Poveži z <Link to={`/igralci/${k.idIgralec}/profil`}>{k.polnoIme}</Link>
            <span className="uvoz__pod">
              {[k.rojstvo ? `r. ${oblikujDatum(k.rojstvo)}` : null, k.licenca ? `licenca ${k.licenca}` : null, k.klub]
                .filter(Boolean).join(' · ') || 'brez podatkov'}
            </span>
          </span>
        </label>
      ))}
      {oseba.kandidati.length === 0 && (
        <p className="namig">V registru ni igralca, ki bi lahko bil ta oseba.</p>
      )}

      <label className="uvoz__moznost">
        <input
          type="radio"
          name={ime}
          checked={nov}
          onChange={() =>
            onSprememba({
              idOsebe: oseba.idOsebe,
              idIgralec: null,
              ime: oseba.predlogIme,
              priimek: oseba.predlogPriimek,
              datumRojstva: null,
              spol: null,
            })
          }
        />
        <span>Nov igralec</span>
      </label>

      {nov && vrednost && (
        <div className="obrazec__vrstica uvoz__nov">
          <label className="obrazec__polje">
            <span>Ime{oseba.manjka.includes('IME') ? ' *' : ''}</span>
            <input value={vrednost.ime ?? ''} maxLength={30} onChange={(d) => popravi({ ime: d.target.value || null })} />
          </label>
          <label className="obrazec__polje">
            <span>Priimek{oseba.manjka.includes('IME') ? ' *' : ''}</span>
            <input
              value={vrednost.priimek ?? ''}
              maxLength={40}
              onChange={(d) => popravi({ priimek: d.target.value || null })}
            />
          </label>
          {oseba.manjka.includes('ROJSTVO') && (
            <label className="obrazec__polje">
              <span>Datum rojstva *</span>
              <input
                type="date"
                value={vrednost.datumRojstva ?? ''}
                onChange={(d) => popravi({ datumRojstva: d.target.value || null })}
              />
            </label>
          )}
          {oseba.manjka.includes('SPOL') && (
            <label className="obrazec__polje">
              <span>Spol *</span>
              <select value={vrednost.spol ?? ''} onChange={(d) => popravi({ spol: (d.target.value || null) as Spol | null })}>
                <option value="">— izberi —</option>
                <option value="MOSKI">Moški</option>
                <option value="ZENSKI">Ženski</option>
              </select>
            </label>
          )}
        </div>
      )}
      {vrednost && (
        <button type="button" className="povezava-gumb" onClick={() => onSprememba(null)}>
          Počisti odločitev
        </button>
      )}
    </fieldset>
  )
}

/* ---------- Novi igralci ---------- */

/* Igralci, ki jih bo uvoz ustvaril. Razdelitev imena na ime in priimek je
   ugibanje ob znanju registra - nezanesljivo razdeljene so označene in jih je
   mogoče popraviti (popravek je odločitev "nov igralec" z vpisanim imenom). */
function NoviIgralci({
  seznam,
  odlocitve,
  onPopravek,
}: {
  seznam: NovIgralecUvoza[]
  odlocitve: Record<number, OdlocitevUvoza>
  onPopravek: (idOsebe: number, v: OdlocitevUvoza | null) => void
}) {
  const [urejan, nastaviUrejan] = useState<number | null>(null)
  const [ime, nastaviIme] = useState('')
  const [priimek, nastaviPriimek] = useState('')
  const nezanesljivih = useMemo(() => seznam.filter((n) => !n.zanesljivo).length, [seznam])
  // nezanesljive na vrh: tiste je vredno pregledati
  const urejeni = useMemo(
    () => [...seznam].sort((a, b) => Number(a.zanesljivo) - Number(b.zanesljivo) || a.priimek.localeCompare(b.priimek, 'sl')),
    [seznam],
  )

  return (
    <div className="uvoz__sklop">
      <div className="naslovna-vrstica naslovna-vrstica--manjsa">
        <h3>Novi igralci</h3>
        <span className="sekcija__meta">
          {seznam.length}
          {nezanesljivih > 0 && ` · ${nezanesljivih} za pregled imena`}
        </span>
      </div>
      <ul className="uvoz__novi">
        {urejeni.map((n) => {
          const popravek = odlocitve[n.idOsebe]
          return (
            <li key={n.idOsebe} className="uvoz__novi-vrstica">
              <span>
                <span className="uvoz__novi-ime">
                  {n.ime} <strong>{n.priimek}</strong>
                </span>
                {!n.zanesljivo && <span className="znacka znacka--opozorilo">preveri ime</span>}
                <span className="uvoz__pod">
                  {[`vir: ${n.polnoIme}`, n.rojstvo ? `r. ${oblikujDatum(n.rojstvo)}` : null, n.licenca, n.klub]
                    .filter(Boolean).join(' · ')}
                </span>
                {popravek && (
                  <span className="uvoz__pod">
                    popravek: {popravek.ime} {popravek.priimek} (velja ob naslednjem predogledu)
                  </span>
                )}
              </span>
              <span className="vrstica__dejanja">
                {popravek ? (
                  <button type="button" className="gumb gumb--majhen" onClick={() => onPopravek(n.idOsebe, null)}>
                    Razveljavi popravek
                  </button>
                ) : (
                  <button
                    type="button"
                    className="gumb gumb--majhen"
                    onClick={() => {
                      nastaviUrejan(n.idOsebe)
                      nastaviIme(n.ime)
                      nastaviPriimek(n.priimek)
                    }}
                  >
                    Popravi ime
                  </button>
                )}
              </span>
              {urejan === n.idOsebe && (
                <form
                  className="obrazec__vrstica uvoz__nov"
                  onSubmit={(d) => {
                    d.preventDefault()
                    if (ime.trim().length < 2 || priimek.trim().length < 2) return
                    onPopravek(n.idOsebe, {
                      idOsebe: n.idOsebe, idIgralec: null, ime: ime.trim(), priimek: priimek.trim(), datumRojstva: null, spol: null,
                    })
                    nastaviUrejan(null)
                  }}
                >
                  <label className="obrazec__polje">
                    <span>Ime</span>
                    <input value={ime} maxLength={30} onChange={(d) => nastaviIme(d.target.value)} />
                  </label>
                  <label className="obrazec__polje">
                    <span>Priimek</span>
                    <input value={priimek} maxLength={40} onChange={(d) => nastaviPriimek(d.target.value)} />
                  </label>
                  <button type="submit" className="gumb gumb--majhen gumb--glavni">Shrani</button>
                  <button type="button" className="gumb gumb--majhen" onClick={() => nastaviUrejan(null)}>Prekliči</button>
                </form>
              )}
            </li>
          )
        })}
      </ul>
    </div>
  )
}

/* ---------- Izid uvoza ---------- */

function IzidUvoza({ izid }: { izid: IzidUvozaDto }) {
  const pot = izid.idTurnir !== null ? `/turnirji/${izid.idTurnir}` : izid.idLiga !== null ? `/lige/${izid.idLiga}` : null
  return (
    <div className={'uvoz__stanje' + (izid.izid === 'USPEH' || izid.izid === 'BREZ_SPREMEMB' ? ' uvoz__stanje--ok' : ' uvoz__stanje--ne')}>
      <strong>
        {izid.izid === 'USPEH' && 'Uvoženo.'}
        {izid.izid === 'BREZ_SPREMEMB' && 'Posnetek je enak zadnjemu uvozu - nič se ni spremenilo.'}
        {izid.izid === 'ZAVRNJENO' && 'Uvoz je zavrnjen: ob zapisu se stanje z virom ni ujemalo. Ponovi predogled.'}
        {izid.izid === 'NAPAKA' && 'Uvoz se ni izvedel.'}
      </strong>
      <span className="uvoz__pod">
        {izid.preracunanihTekem !== null && `Rating preračunan (${izid.preracunanihTekem} tekem). `}
        {izid.napaka}
        {pot && (
          <>
            {' '}
            <Link to={pot}>Odpri tekmovanje</Link>
          </>
        )}
      </span>
    </div>
  )
}

/* ---------- Dnevnik ---------- */

interface PovzetekZagona {
  preverbe?: string[]
  napake?: string[]
  opozorila?: string[]
  napaka?: string
}

function beriPovzetek(z: UvozZagonDto): PovzetekZagona | null {
  if (!z.povzetek) return null
  try {
    return JSON.parse(z.povzetek) as PovzetekZagona
  } catch {
    return null
  }
}

/* Vsak predogled ostane brez sledi, vsak uvoz pa ima vrstico: kdo, kdaj, s
   katerim posnetkom in s kakšnim izidom. */
function Dnevnik() {
  const zagoni = useQuery({ queryKey: ['uvoz-zagoni'], queryFn: uvozApi.zagoni })
  const [odprt, nastaviOdprt] = useState<number | null>(null)
  const [vsi, nastaviVse] = useState(false)
  const seznam = zagoni.data ?? []
  const prikazani = vsi ? seznam : seznam.slice(0, 20)

  return (
    <div className="uvoz__dnevnik">
      <div className="naslovna-vrstica">
        <h2>Dnevnik uvozov</h2>
        <span className="sekcija__meta">{seznam.length} zagonov</span>
      </div>
      <NapakaPoizvedbe poizvedba={zagoni} kaj="dnevnika" />
      {zagoni.data && seznam.length === 0 && <p className="obvestilo">Ni še nobenega uvoza.</p>}
      {prikazani.length > 0 && (
        <div className="tabela-ovoj">
          <table className="tabela">
            <thead>
              <tr>
                <th scope="col">Čas</th>
                <th scope="col">Tekmovanje</th>
                <th scope="col">Izid</th>
                <th scope="col">Izvedel</th>
                <th scope="col" className="tabela__dejanja"></th>
              </tr>
            </thead>
            <tbody>
              {prikazani.map((z) => {
                const povzetek = beriPovzetek(z)
                return (
                  <FragmentZagona
                    key={z.id}
                    zagon={z}
                    povzetek={povzetek}
                    odprt={odprt === z.id}
                    onPreklop={() => nastaviOdprt(odprt === z.id ? null : z.id)}
                  />
                )
              })}
            </tbody>
          </table>
        </div>
      )}
      {!vsi && seznam.length > prikazani.length && (
        <button type="button" className="povezava-gumb" onClick={() => nastaviVse(true)}>
          Pokaži vseh {seznam.length}
        </button>
      )}
    </div>
  )
}

function FragmentZagona({
  zagon,
  povzetek,
  odprt,
  onPreklop,
}: {
  zagon: UvozZagonDto
  povzetek: PovzetekZagona | null
  odprt: boolean
  onPreklop: () => void
}) {
  const vrstice = [
    ...(povzetek?.napaka ? [`napaka: ${povzetek.napaka}`] : []),
    ...(povzetek?.napake ?? []).map((n) => `napaka: ${n}`),
    ...(povzetek?.preverbe ?? []),
  ]
  return (
    <>
      <tr>
        <td className="uvoz__mono">{oblikujCas(zagon.zacetekOb)}</td>
        <td>
          {zagon.ime}
          <span className="uvoz__pod">Stupa {zagon.zunanjiId}</span>
        </td>
        <td>
          {zagon.izid ? (
            <span className={'znacka' + razredIzida(zagon.izid)}>{OZNAKE_IZIDA_UVOZA[zagon.izid]}</span>
          ) : (
            <span className="znacka">teče</span>
          )}
        </td>
        <td>{zagon.izvedel ?? '—'}</td>
        <td className="tabela__dejanja">
          {vrstice.length > 0 && (
            <button type="button" className="gumb gumb--majhen" aria-expanded={odprt} onClick={onPreklop}>
              {odprt ? 'Skrij' : 'Podrobno'}
            </button>
          )}
        </td>
      </tr>
      {odprt && (
        <tr className="uvoz__podrobno">
          <td colSpan={5}>
            <ul className="uvoz__primeri">
              {vrstice.map((v, i) => (
                <li key={i}>{v}</li>
              ))}
            </ul>
          </td>
        </tr>
      )}
    </>
  )
}
