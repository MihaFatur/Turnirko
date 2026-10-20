/* Natisljivi listki (zapisniki) tekem enega turnirskega dogodka.

   Sodniki jih izpolnijo z roko za mizo, glavni sodnik pa rezultate pozneje
   vnese prek običajnega vnosa. Zato je listek le POGLED na podatke, ki jih
   dogodek že ima — brez zaledja in brez nove sheme; tiska ga kar brskalnik
   (window.print). Stran je namenoma zunaj skupne postavitve (brez navigacije),
   da je natis čist.

   Natisnejo se VSE pripravljene tekme dogodka: tiste, kjer sta znana oba
   igralca in še niso odigrane (status PRIPRAVLJENA ali V_IGRI). Prosti prehodi
   in še nedoločene tekme odpadejo sami, ker nimajo obeh udeležencev.

   Listki so zloženi na liste A4 po šest (2 × 3, brez robov): vsak list je
   natančno 210 × 297 mm, listki 105 × 99 mm, zato ga režejo samo trije rezi po
   črtkanih črtah. Zadnji list se dopolni s praznimi mesti, da so rezi povsod
   enaki.

   Ekipni dogodek ima namesto listkov uradni ekipni zapisnik za vsako tekmo
   (ZapisnikiEkipnegaDogodka): iste pripravljene tekme, v istem vrstnem redu,
   le da je vsaka srečanje s postavo in mrežo posamičnih tekem. */
import { useEffect, useMemo, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'

import { dogodkiApi, turnirjiApi } from '../api/zahteve'
import type { FazaTekme, MrezaDto, TekmaDto } from '../api/tipi'
import { OZNAKE_SPOL_KATEGORIJA, imeUdelezenca } from '../api/tipi'
import { imeKola, sklonListkov } from '../pomozno/oblikovanje'
import { imeTekmeZreba } from '../pomozno/svRegija'
import { oznakaKategorije } from '../pomozno/zrebSkupin'
import { Listek } from '../komponente/Listek'
import { SporociloNapake } from '../komponente/SporociloNapake'
import { ZapisnikiEkipnegaDogodka } from '../komponente/ZapisnikiEkipnegaDogodka'

/* Skupine se natisnejo prve (predtekmovanje), nato glavna mreža, nato
   tolažilne tekme (npr. za 3. mesto). */
const RANG_FAZE: Record<FazaTekme, number> = { SKUPINA: 0, GLAVNI: 1, TOLAZILNI: 2 }

/* Koliko listkov gre na list A4 (2 stolpca × 3 vrstice). */
const LISTKOV_NA_LIST = 6

/* Širina lista A4 na zaslonu (210 mm pri 96 dpi): ožji zaslon ga pomanjša, da
   je cel list viden in se ne drsi vodoravno. */
const SIRINA_LISTA_PX = (210 / 25.4) * 96

/* Tekma, za katero ima smisel natisniti listek. */
function jePripravljena(t: TekmaDto): boolean {
  return (
    (t.status === 'PRIPRAVLJENA' || t.status === 'V_IGRI') &&
    t.udelezenec1 !== null &&
    t.udelezenec2 !== null
  )
}

export function ListkiStran() {
  const { id } = useParams()
  const idDogodka = Number(id)

  const mreza = useQuery({
    queryKey: ['dogodek', idDogodka],
    queryFn: () => dogodkiApi.mreza(idDogodka),
  })

  /* Ime turnirja za glavo natisa; dogodek pozna le id turnirja. */
  const idTurnir = mreza.data?.dogodek.idTurnir
  const turnir = useQuery({
    queryKey: ['turnir', idTurnir],
    queryFn: () => turnirjiApi.najdi(idTurnir!),
    enabled: idTurnir !== undefined,
  })

  const listki = useMemo(() => pripraviListke(mreza.data), [mreza.data])
  const listi = useMemo(() => razdeliNaListe(listki), [listki])

  /* Ožji zaslon list pomanjša (CSS zoom); pri tisku se pomanjšanje izključi. */
  const [okvir, nastaviOkvir] = useState<HTMLDivElement | null>(null)
  const [merilo, nastaviMerilo] = useState(1)
  useEffect(() => {
    if (!okvir) return
    const izmeri = () => nastaviMerilo(Math.min(1, okvir.clientWidth / SIRINA_LISTA_PX))
    izmeri()
    const opazovalec = new ResizeObserver(izmeri)
    opazovalec.observe(okvir)
    return () => opazovalec.disconnect()
  }, [okvir])

  if (mreza.isPending) return <p className="obvestilo">Nalaganje …</p>
  if (mreza.error) return <SporociloNapake napaka={mreza.error} />

  const dogodek = mreza.data!.dogodek

  /* Ekipna tekma nima listka, ampak zapisnik srečanja. Tekma brez srečanja
     (npr. prenesen izid finalne skupine) zapisnika nima, zato odpade. */
  if (dogodek.disciplina === 'EKIPNO') {
    return (
      <ZapisnikiEkipnegaDogodka
        dogodek={dogodek}
        imeTurnirja={turnir.data?.ime ?? null}
        idjiSrecanj={listki.flatMap(({ tekma }) => (tekma.idSrecanje !== null ? [tekma.idSrecanje] : []))}
      />
    )
  }

  return (
    <>
      <div className="listki-orodja zaslon-samo">
        <Link to={`/dogodki/${idDogodka}`} className="povezava-nazaj">
          ← Nazaj na dogodek
        </Link>
        <span className="listki-orodja__stevec">
          {listki.length} {sklonListkov(listki.length)}
        </span>
        <button
          className="gumb gumb--glavni"
          disabled={listki.length === 0}
          onClick={() => window.print()}
        >
          Natisni
        </button>
      </div>

      <div className="listki-stran listki-stran--listki">
        <header className="listki-naslov zaslon-samo">
          <h1>
            {turnir.data ? `${turnir.data.ime} — ` : ''}
            {dogodek.ime}
          </h1>
          <p>
            {OZNAKE_SPOL_KATEGORIJA[dogodek.spolKategorija]}
            {dogodek.starostnaKategorija && ` · ${dogodek.starostnaKategorija}`}
          </p>
          <p className="listki-naslov__navodilo">
            Natisni na A4 v merilu 100 %, brez robov in brez glave ter noge. Vsak list ima šest
            listkov; razreži ga po treh črtkanih črtah (ena navpična, dve vodoravni).
          </p>
        </header>

        {listki.length === 0 ? (
          <p className="obvestilo zaslon-samo">
            Ni pripravljenih tekem za tiskanje. Listek se ustvari za tekmo, kjer
            sta znana oba igralca in še ni odigrana.
          </p>
        ) : (
          <div className="listki-listi" ref={nastaviOkvir}>
            {listi.map((list, i) => (
              <section
                key={i}
                className="listki-list"
                aria-label={`List ${i + 1} od ${listi.length}`}
                style={merilo < 1 ? { zoom: merilo } : undefined}
              >
                {list.map(({ tekma, skupina, faza }) => (
                  <Listek
                    key={tekma.id}
                    podatki={{
                      dogodek: turnir.data?.ime ?? null,
                      kategorija: oznakaKategorije(dogodek),
                      skupina,
                      faza,
                      steviloNizov: tekma.steviloNizov,
                      miza: tekma.miza,
                      stran1: { ime: imeUdelezenca(tekma.udelezenec1) ?? '—' },
                      stran2: { ime: imeUdelezenca(tekma.udelezenec2) ?? '—' },
                    }}
                  />
                ))}
                {/* Prazna mesta na zadnjem listu: rezi so povsod isti. */}
                {Array.from({ length: LISTKOV_NA_LIST - list.length }, (_, j) => (
                  <div key={`prazno-${j}`} className="listek listek--prazen" aria-hidden="true" />
                ))}
              </section>
            ))}
          </div>
        )}
      </div>
    </>
  )
}

interface PripravljenListek {
  tekma: TekmaDto
  /* Oznaka skupine (»A«); prazna pri izločilni mreži. */
  skupina: string | null
  /* Kolo skupine ali krog mreže. */
  faza: string
}

function razdeliNaListe(listki: PripravljenListek[]): PripravljenListek[][] {
  const listi: PripravljenListek[][] = []
  for (let i = 0; i < listki.length; i += LISTKOV_NA_LIST) {
    listi.push(listki.slice(i, i + LISTKOV_NA_LIST))
  }
  return listi
}

/* Pripravi in razvrsti pripravljene tekme, vsaki doda človeku berljivo
   oznako skupine in kola/faze. Ločeno od izrisa, da je logika lahko
   testljiva. */
function pripraviListke(podatki: MrezaDto | undefined): PripravljenListek[] {
  if (!podatki) return []

  const glavne = podatki.tekme.filter((t) => t.faza === 'GLAVNI')
  const zadnjeKolo = glavne.length > 0 ? Math.max(...glavne.map((t) => t.kolo)) : 1

  return podatki.tekme
    .filter(jePripravljena)
    .sort(
      (a, b) =>
        RANG_FAZE[a.faza] - RANG_FAZE[b.faza] ||
        (a.idSkupina ?? 0) - (b.idSkupina ?? 0) ||
        (a.idZreb ?? 0) - (b.idZreb ?? 0) ||
        a.kolo - b.kolo ||
        a.pozicija - b.pozicija,
    )
    .map((tekma) => ({
      tekma,
      skupina:
        tekma.faza === 'SKUPINA'
          ? (podatki.skupine.find((s) => s.id === tekma.idSkupina)?.oznaka ?? null)
          : null,
      faza: oznakaFaze(tekma, zadnjeKolo, podatki),
    }))
}

function oznakaFaze(tekma: TekmaDto, zadnjeKolo: number, podatki: MrezaDto): string {
  if (tekma.faza === 'SKUPINA') return `${tekma.kolo}. kolo`
  /* SV regija: tekma žreba nosi razpon mest, ki ga odloča (»Nivo 2 · Za 21. mesto«). */
  if (tekma.idZreb !== null && tekma.razponOd !== null && tekma.razponDo !== null) {
    for (const nivo of podatki.svRegija?.nivoji ?? []) {
      const zreb = nivo.zrebi.find((z) => z.id === tekma.idZreb)
      if (zreb) {
        return `Nivo ${nivo.nivo} · ${imeTekmeZreba(
          tekma.razponOd, tekma.razponDo, zreb.prvoMesto, zreb.indeks === 0,
        )}`
      }
    }
  }
  if (tekma.faza === 'TOLAZILNI') return 'Za 3. mesto'
  return imeKola(tekma.kolo, zadnjeKolo)
}
