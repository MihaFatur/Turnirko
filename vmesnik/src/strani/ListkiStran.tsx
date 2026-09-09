/* Natisljivi listki (zapisniki) tekem enega turnirskega dogodka.

   Sodniki jih izpolnijo z roko za mizo, glavni sodnik pa rezultate pozneje
   vnese prek običajnega vnosa. Zato je listek le POGLED na podatke, ki jih
   dogodek že ima — brez zaledja in brez nove sheme; tiska ga kar brskalnik
   (window.print). Stran je namenoma zunaj skupne postavitve (brez navigacije),
   da je natis čist.

   Natisnejo se VSE pripravljene tekme dogodka: tiste, kjer sta znana oba
   igralca in še niso odigrane (status PRIPRAVLJENA ali V_IGRI). Prosti prehodi
   in še nedoločene tekme odpadejo sami, ker nimajo obeh udeležencev. */
import { useMemo } from 'react'
import { Link, useParams } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'

import { dogodkiApi, turnirjiApi } from '../api/zahteve'
import type { FazaTekme, MrezaDto, TekmaDto } from '../api/tipi'
import { OZNAKE_SPOL_KATEGORIJA, imeUdelezenca } from '../api/tipi'
import { imeKola, sklonListkov } from '../pomozno/oblikovanje'
import { Listek } from '../komponente/Listek'
import { SporociloNapake } from '../komponente/SporociloNapake'

/* Skupine se natisnejo prve (predtekmovanje), nato glavna mreža, nato
   tolažilne tekme (npr. za 3. mesto). */
const RANG_FAZE: Record<FazaTekme, number> = { SKUPINA: 0, GLAVNI: 1, TOLAZILNI: 2 }

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

  if (mreza.isPending) return <p className="obvestilo">Nalaganje …</p>
  if (mreza.error) return <SporociloNapake napaka={mreza.error} />

  const dogodek = mreza.data!.dogodek

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

      <div className="listki-stran">
        <header className="listki-naslov">
          <h1>
            {turnir.data ? `${turnir.data.ime} — ` : ''}
            {dogodek.ime}
          </h1>
          <p>
            {OZNAKE_SPOL_KATEGORIJA[dogodek.spolKategorija]}
            {dogodek.starostnaKategorija && ` · ${dogodek.starostnaKategorija}`}
          </p>
        </header>

        {listki.length === 0 ? (
          <p className="obvestilo zaslon-samo">
            Ni pripravljenih tekem za tiskanje. Listek se ustvari za tekmo, kjer
            sta znana oba igralca in še ni odigrana.
          </p>
        ) : (
          <div className="listki-mreza">
            {listki.map(({ tekma, oznaka }) => (
              <Listek
                key={tekma.id}
                podatki={{
                  oznaka,
                  steviloNizov: tekma.steviloNizov,
                  miza: tekma.miza,
                  stran1: {
                    ime: imeUdelezenca(tekma.udelezenec1) ?? '—',
                    podnaslov: tekma.udelezenec1?.klub ?? null,
                  },
                  stran2: {
                    ime: imeUdelezenca(tekma.udelezenec2) ?? '—',
                    podnaslov: tekma.udelezenec2?.klub ?? null,
                  },
                }}
              />
            ))}
          </div>
        )}
      </div>
    </>
  )
}

/* Pripravi in razvrsti pripravljene tekme, vsaki doda človeku berljivo
   oznako faze/kola. Ločeno od izrisa, da je logika lahko testljiva. */
function pripraviListke(podatki: MrezaDto | undefined): { tekma: TekmaDto; oznaka: string }[] {
  if (!podatki) return []

  const glavne = podatki.tekme.filter((t) => t.faza === 'GLAVNI')
  const zadnjeKolo = glavne.length > 0 ? Math.max(...glavne.map((t) => t.kolo)) : 1

  return podatki.tekme
    .filter(jePripravljena)
    .sort(
      (a, b) =>
        RANG_FAZE[a.faza] - RANG_FAZE[b.faza] ||
        (a.idSkupina ?? 0) - (b.idSkupina ?? 0) ||
        a.kolo - b.kolo ||
        a.pozicija - b.pozicija,
    )
    .map((tekma) => ({ tekma, oznaka: oznakaFaze(tekma, zadnjeKolo, podatki) }))
}

function oznakaFaze(tekma: TekmaDto, zadnjeKolo: number, podatki: MrezaDto): string {
  if (tekma.faza === 'SKUPINA') {
    const skupina = podatki.skupine.find((s) => s.id === tekma.idSkupina)
    return skupina ? `Skupina ${skupina.oznaka} · ${tekma.kolo}. kolo` : `${tekma.kolo}. kolo`
  }
  if (tekma.faza === 'TOLAZILNI') return 'Za 3. mesto'
  return imeKola(tekma.kolo, zadnjeKolo)
}
