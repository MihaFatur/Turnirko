/* Stran »Naročnina« za navijača (V58): brezplačen račun, nakup paketa Navijač
   (19,99 € na leto), preklic ob koncu obdobja in obnova.

   Navijač je edini paket, ki se kupi samo letno, zato ni izbire plačevanja ne
   preklopa cikla. Nakup gre prek Stripe Checkouta kot pri Premium (PlacilaStoritev):
   stran samo začne plačilo in preusmeri brskalnik; naročnina nastane šele prek
   webhooka, zato vrnitev nekajkrat povpraša znova (useVrnitevSPlacila).

   Vse številke obdobja in cene pove strežnik (NarocninaDto); cena ob nakupu
   je iz cenika v api/tipi.ts, ki je samo predogled — zaledje je zadnja beseda. */
import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'

import { opisNapake } from '../api/odjemalec'
import { CENA_NAVIJAC_LETNO, type NarocninaDto } from '../api/tipi'
import { narocninaApi, placilaApi } from '../api/zahteve'
import { oblikujCeno, oblikujDatum } from '../pomozno/oblikovanje'
import { zabelezi } from '../pomozno/statistika'
import { NapakaPoizvedbe } from './NapakaPoizvedbe'
import { DruzinaKartica } from './DruzinaKartica'
import { OsebniKoledarKartica } from './OsebniKoledarKartica'
import { PodporaKlubaKartica } from './PodporaKlubaKartica'
import { PotisnaObvestilaKartica } from './PotisnaObvestilaKartica'
import { PotrditvenoOkno } from './PotrditvenoOkno'
import { useVrnitevSPlacila } from './useVrnitevSPlacila'

/* Kaj paket doda: [ugodnost, ima jo brezplačen račun]. Isti seznam kot primerjava v predlogu
   paketa in v koraku »Paket« registracije — kar piše tu, mora držati zaledje. */
const PRIMERJAVA: [string, boolean][] = [
  ['Rezultati, lestvice, koledar, profili', true],
  ['Spremljanje lig in e-pošta po vsakem kolu', true],
  ['Spremljan igralec na domači strani (en)', true],
  ['Neomejeno spremljanih igralcev', false],
  ['E-pošta po turnirju za spremljane igralce', false],
  ['Osebni koledar v telefonu (.ics)', false],
  ['Napoved dvoboja za kateri koli par', false],
  ['Družina: zasebna statistika do dveh igralcev do 21 let', false],
  ['Potisna obvestila med turnirjem', false],
  ['Sezonski zapisnik igralca za tisk', false],
]

const uspehPoVrnitvi = (n: NarocninaDto) =>
  `Stripe je potrdil plačilo ${oblikujCeno(n.cena ?? CENA_NAVIJAC_LETNO)}. Paket Navijač je vklopljen.`

export function NarocninaNavijac() {
  const poizvedba = useQuery({ queryKey: ['narocnina'], queryFn: narocninaApi.pregled })
  const vrnitev = useVrnitevSPlacila({
    caka: 'Plačilo je prejeto. Paket Navijač se vklaplja …',
    uspeh: uspehPoVrnitvi,
  })

  if (poizvedba.error) return <NapakaPoizvedbe poizvedba={poizvedba} kaj="naročnine" />
  if (!poizvedba.data) return <p className="obvestilo">Nalagam naročnino …</p>
  return (
    <section className="narocnina-navijac">
      <Pogled narocnina={poizvedba.data} vrnitev={vrnitev} />
      <OsebniKoledarKartica />
      <PotisnaObvestilaKartica />
      <DruzinaKartica />
      <PodporaKlubaKartica />
    </section>
  )
}

function Pogled({
  narocnina: n,
  vrnitev,
}: {
  narocnina: NarocninaDto
  vrnitev: ReturnType<typeof useVrnitevSPlacila>
}) {
  const odjemalec = useQueryClient()
  const [vprasanjePreklic, nastaviVprasanje] = useState(false)
  const [kodaOdprta, nastaviKodaOdprta] = useState(false)
  const [kodaVnos, nastaviKodaVnos] = useState('')
  /* Po uspehu račun dobi vrstico naročnine in polje za kodo izgine, zato sporočilo drži Pogled. */
  const [uspehKode, nastaviUspehKode] = useState<string | null>(null)

  const jePreizkus = n.aktivna && n.status === 'PREIZKUS'
  const imaPaket = n.aktivna && n.paket === 'NAVIJAC'
  /* Preizkus (koda z meseci) velja brez Stripa: preklica ni, nakup pa je mogoč in plačano
     obdobje teče takoj. */
  const upravljiva = imaPaket && n.upravljiva && !jePreizkus
  const zapadla = n.status === 'ZAPADLA'

  const poNarocilu = (nova: NarocninaDto) => odjemalec.setQueryData(['narocnina'], nova)

  const nadgradi = useMutation({
    mutationFn: () => placilaApi.nadgradnja({ paket: 'NAVIJAC', ciklus: 'LETNO' }),
    onSuccess: (seja) => {
      zabelezi('navijac-nakup-zacetek', {})
      window.location.href = seja.url
    },
  })
  /* Koda popusta že prijavljenega računa: strežnik odloči, ali velja (samo koda za paket Navijač,
     samo račun brez naročnine), razlog zavrnitve pove sam. */
  const uporabiKodo = useMutation({
    mutationFn: (koda: string) => narocninaApi.uporabiKodo(koda),
    onSuccess: (nova) => {
      zabelezi('koda-popusta-narocnina', { vrsta: 'NAVIJAC_MESECI' })
      poNarocilu(nova)
      nastaviUspehKode(
        `Koda je uporabljena. Paket Navijač je vklopljen brez plačila do ${oblikujDatum(nova.obdobjeDo)}.`,
      )
      nastaviKodaOdprta(false)
      nastaviKodaVnos('')
      /* Paket odpre še spremljanje več igralcev, koledar in drugo, kar so druge poizvedbe že prebrale. */
      void odjemalec.invalidateQueries()
    },
  })
  const oddajKodo = () => {
    const vpisana = kodaVnos.trim()
    if (vpisana && !uporabiKodo.isPending) uporabiKodo.mutate(vpisana)
  }
  const preklici = useMutation({ mutationFn: narocninaApi.preklici, onSuccess: poNarocilu })
  const obnovi = useMutation({ mutationFn: () => narocninaApi.obnovi(), onSuccess: poNarocilu })
  const portal = useMutation({
    mutationFn: placilaApi.portal,
    onSuccess: (seja) => {
      window.location.href = seja.url
    },
  })

  const napaka = nadgradi.error ?? preklici.error ?? obnovi.error ?? portal.error

  return (
    <div>
      <div className="stran-glava stran-glava--ozka">
        <div>
          <h1 className="naslov-strani">
            <span className="naslov-strani__nad">Naročnina</span>
            <span className="naslov-strani__glavni">Navijač</span>
          </h1>
          <p className="namig">
            Brezplačno spremljaš lige in enega igralca. Z Navijačem spremljaš ljudi: vsi igralci, ki
            jih spremljaš, in vse o njih pride k tebi.
          </p>
        </div>
        <div className="kolofon">
          <div className="kolofon__vrstica">
            <span className="kolofon__oznaka">Paket</span>
            <span className="kolofon__vrednost">{imaPaket ? 'Navijač' : 'Brezplačno'}</span>
          </div>
          {imaPaket && n.obdobjeDo && (
            <div className="kolofon__vrstica">
              <span className="kolofon__oznaka">{n.preklicana ? 'Velja do' : jePreizkus ? 'Brez plačila do' : 'Obnova'}</span>
              <span className="kolofon__vrednost">{oblikujDatum(n.obdobjeDo)}</span>
            </div>
          )}
          <div className="kolofon__vrstica">
            <span className="kolofon__oznaka">Cena</span>
            <span className="kolofon__vrednost">
              {imaPaket && !jePreizkus
                ? `${oblikujCeno(n.cena ?? CENA_NAVIJAC_LETNO)} na leto`
                : jePreizkus ? 'brez plačila' : `${oblikujCeno(CENA_NAVIJAC_LETNO)} na leto`}
            </span>
          </div>
        </div>
      </div>

      {vrnitev && (
        <div className={vrnitev.uspeh ? 'narocnina-navijac__uspeh' : 'obvestilo'}>{vrnitev.besedilo}</div>
      )}
      {uspehKode && <div className="narocnina-navijac__uspeh" role="status">{uspehKode}</div>}
      {zapadla && (
        <div className="napaka" role="alert">
          Zadnje plačilo ni uspelo, zato paket ne velja. Posodobi kartico v Stripu: ko se plačilo
          izvede, se paket vklopi sam.
        </div>
      )}
      {n.preklicana && (
        <div className="obvestilo">
          Naročnina je preklicana: paket velja do {oblikujDatum(n.obdobjeDo)}, nato se račun vrne na
          brezplačen paket (en spremljan igralec).
        </div>
      )}

      <div className="naslovna-vrstica">
        <h2>Kaj dobiš</h2>
      </div>
      <div className="narocnina-navijac__primerjava">
        <div className="narocnina-navijac__vrstica narocnina-navijac__vrstica--glava">
          <span />
          <span>Brezplačno</span>
          <span>Navijač</span>
        </div>
        {PRIMERJAVA.map(([ugodnost, brezplacno]) => (
          <div className="narocnina-navijac__vrstica" key={ugodnost}>
            <span>{ugodnost}</span>
            <span aria-label={brezplacno ? 'da' : 'ne'}>{brezplacno ? 'DA' : '—'}</span>
            <span aria-label="da" className="narocnina-navijac__da">DA</span>
          </div>
        ))}
      </div>

      <div className="narocnina-navijac__dejanja">
        {(!imaPaket || jePreizkus) && (
          <button type="button" className="gumb gumb--glavni" disabled={nadgradi.isPending}
                  onClick={() => nadgradi.mutate()}>
            {nadgradi.isPending
              ? 'Preusmerjam na plačilo …'
              : `${jePreizkus ? 'Kupi paket' : 'Nadgradi na Navijača'} · ${oblikujCeno(CENA_NAVIJAC_LETNO)} na leto`}
          </button>
        )}
        {upravljiva && !n.preklicana && (
          <button type="button" className="gumb" onClick={() => nastaviVprasanje(true)}>
            Prekliči ob koncu obdobja
          </button>
        )}
        {upravljiva && n.preklicana && (
          <button type="button" className="gumb gumb--glavni" disabled={obnovi.isPending}
                  onClick={() => obnovi.mutate()}>
            Obnovi naročnino
          </button>
        )}
        {upravljiva && (
          <button type="button" className="gumb" disabled={portal.isPending}
                  onClick={() => portal.mutate()}>
            Plačilo in računi v Stripu
          </button>
        )}
      </div>
      {/* Koda velja le za račun, ki paketa še ni imel (brez vrstice naročnine); strežnik to preveri znova. */}
      {n.status === null && (
        <div className="narocnina-navijac__koda">
          {kodaOdprta ? (
            <div className="obrazec__polje">
              <span>Koda za popust</span>
              <div className="narocnina-navijac__koda-vnos">
                <input
                  aria-label="Koda za popust"
                  value={kodaVnos}
                  onChange={(d) => {
                    nastaviKodaVnos(d.target.value)
                    if (uporabiKodo.isError) uporabiKodo.reset()
                  }}
                  onKeyDown={(d) => {
                    if (d.key === 'Enter') {
                      d.preventDefault()
                      oddajKodo()
                    }
                  }}
                  autoComplete="off"
                  autoCapitalize="characters"
                  spellCheck={false}
                  autoFocus
                />
                <button type="button" className="gumb" disabled={!kodaVnos.trim() || uporabiKodo.isPending}
                        onClick={oddajKodo}>
                  {uporabiKodo.isPending ? 'Preverjam …' : 'Uporabi'}
                </button>
              </div>
              {uporabiKodo.error != null && <div className="napaka" role="alert">{opisNapake(uporabiKodo.error)}</div>}
              <button type="button" className="povezava-gumb"
                      onClick={() => {
                        nastaviKodaOdprta(false)
                        nastaviKodaVnos('')
                        uporabiKodo.reset()
                      }}>
                Nimam kode
              </button>
            </div>
          ) : (
            <button type="button" className="povezava-gumb" onClick={() => nastaviKodaOdprta(true)}>
              Imam kodo za popust
            </button>
          )}
        </div>
      )}
      {napaka != null && <div className="napaka" role="alert">{opisNapake(napaka)}</div>}
      <p className="namig">
        Plačilo prek Stripa, račun prejmeš po e-pošti. Naročnina se obnavlja vsako leto, dokler je ne
        prekličeš. Več v <a href="/pogoji">pogojih uporabe</a>.
      </p>

      {vprasanjePreklic && (
        <PotrditvenoOkno
          naslov="Prekliči naročnino"
          sporocilo={`Paket Navijač velja do ${oblikujDatum(n.obdobjeDo)}. Po tem se račun vrne na brezplačen paket: spremljaš enega igralca, koledar v telefonu se neha posodabljati. Do tedaj lahko naročnino obnoviš.`}
          besedaPotrditve="Prekliči"
          onPotrdi={() => preklici.mutate()}
          onZapri={() => nastaviVprasanje(false)}
        />
      )}
    </div>
  )
}
