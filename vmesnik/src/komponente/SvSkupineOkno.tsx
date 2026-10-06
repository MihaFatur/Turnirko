/* Ročni vpis skupin po nivojih (SV regija).

   Skupine nastanejo po jakostnih pasovih (naključni žreb) ali jih vpiše
   organizator: tu stoji predlog (isti žreb, kot bi ga izvedel dogodek, ali
   trenutne skupine), organizator pa igralce prestavlja med skupinami in
   nivoji ter po potrebi dodaja nove skupine in nivoje. Shranjeno ostane vse,
   kar zapiše žreb sam - tekme »vsak z vsakim«, žreba nivojev, napredovanje -,
   zato ročni vpis ne more delovati drugače kot žreb.

   Prestavljanje je z izbirnikom ob imenu (zanesljivo na dotik in s tipkovnico,
   kot pri jakostnem vrstnem redu): igralec gre na konec izbrane skupine.
   Prazne skupine ob shranjevanju odpadejo, skupina z enim igralcem pa je
   napaka, ki jo okno pove že prej, kot jo zavrne strežnik. */
import { useEffect, useMemo, useState } from 'react'
import { useMutation } from '@tanstack/react-query'

import { svApi } from '../api/zahteve'
import type { MrezaDto, SvClanDto, SvPredlogDto } from '../api/tipi'
import { sklonIgralcev, sklonSkupin } from '../pomozno/oblikovanje'
import { ModalnoOkno } from './ModalnoOkno'
import { SporociloNapake } from './SporociloNapake'

type Skupine = SvClanDto[][]
type Nivoji = Skupine[]

interface Lastnosti {
  idDogodka: number
  podatki: MrezaDto
  /* »predlog«: okno začne z naključnim žrebom; »trenutno«: s skupinami, ki že
     obstajajo (popravek po žrebu). */
  izhodisce: 'predlog' | 'trenutno'
  onZapri: () => void
  onShranjeno: () => void
}

/* Skupine, ki že obstajajo: po nivojih, po oznaki, z igralci po jakostnem mestu. */
function izTrenutnih(podatki: MrezaDto): Nivoji {
  const predtekmovalne = podatki.skupine
    .filter((s) => s.stopnja === 1)
    .sort((a, b) => a.nivo - b.nivo || a.oznaka.localeCompare(b.oznaka, 'sl'))
  const nivoji = new Map<number, Skupine>()
  for (const skupina of predtekmovalne) {
    const clani = podatki.prijave
      .filter((p) => p.idSkupina === skupina.id)
      .sort((a, b) => (a.stNosilca ?? 1e9) - (b.stNosilca ?? 1e9))
      .map<SvClanDto>((p) => ({
        idPrijave: p.id,
        polnoIme: p.polnoIme,
        klub: p.klub,
        rating: p.rating,
        stNosilca: p.stNosilca,
      }))
    nivoji.set(skupina.nivo, [...(nivoji.get(skupina.nivo) ?? []), clani])
  }
  return [...nivoji.entries()].sort((a, b) => a[0] - b[0]).map(([, skupine]) => skupine)
}

function izPredloga(predlog: SvPredlogDto): Nivoji {
  return predlog.nivoji.map((n) => n.skupine.map((s) => s.clani))
}

/* »1A«, »1B«, »2A« ... - ista oznaka, kot jo da strežnik. */
function oznaka(nivo: number, skupina: number): string {
  return `${nivo + 1}${String.fromCharCode(65 + skupina)}`
}

export function SvSkupineOkno({ idDogodka, podatki, izhodisce, onZapri, onShranjeno }: Lastnosti) {
  const [nivoji, nastaviNivoje] = useState<Nivoji | null>(
    izhodisce === 'trenutno' ? izTrenutnih(podatki) : null,
  )

  const predlog = useMutation({
    mutationFn: () => svApi.predlog(idDogodka),
    onSuccess: (p) => nastaviNivoje(izPredloga(p)),
  })

  /* Okno, ki začne s predlogom, ga ob odprtju zahteva enkrat. */
  useEffect(() => {
    if (izhodisce === 'predlog') predlog.mutate()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const shranjevanje = useMutation({
    mutationFn: (vnos: number[][][]) =>
      svApi.shraniSkupine(idDogodka, { nivoji: vnos.map((skupine) => ({ skupine })) }),
    onSuccess: () => {
      onShranjeno()
      onZapri()
    },
  })

  /* Vse skupine v enem seznamu: cilji izbirnika. */
  const cilji = useMemo(
    () =>
      (nivoji ?? []).flatMap((skupine, n) =>
        skupine.map((_, j) => ({ n, j, oznaka: oznaka(n, j) })),
      ),
    [nivoji],
  )

  function premakni(idPrijave: number, ciljOznaka: string) {
    if (!nivoji) return
    const cilj = cilji.find((c) => c.oznaka === ciljOznaka)
    if (!cilj) return
    const clan = nivoji.flat(2).find((c) => c.idPrijave === idPrijave)
    if (!clan) return
    const brez = nivoji.map((skupine) =>
      skupine.map((clani) => clani.filter((c) => c.idPrijave !== idPrijave)),
    )
    brez[cilj.n][cilj.j] = [...brez[cilj.n][cilj.j], clan]
    nastaviNivoje(brez)
  }

  function dodajSkupino(n: number) {
    if (!nivoji) return
    nastaviNivoje(nivoji.map((skupine, i) => (i === n ? [...skupine, []] : skupine)))
  }

  function odstraniSkupino(n: number, j: number) {
    if (!nivoji) return
    nastaviNivoje(nivoji.map((skupine, i) => (i === n ? skupine.filter((_, k) => k !== j) : skupine)))
  }

  function dodajNivo() {
    if (!nivoji) return
    nastaviNivoje([...nivoji, [[], []]])
  }

  /* Vrstice, ki jih strežnik ne bi sprejel: skupina z enim igralcem. Prazne
     skupine in nivoji odpadejo, zato niso napaka. */
  const opozorila = useMemo(() => {
    const seznam: string[] = []
    ;(nivoji ?? []).forEach((skupine, n) =>
      skupine.forEach((clani, j) => {
        if (clani.length === 1) seznam.push(`Skupina ${oznaka(n, j)} ima samo enega igralca.`)
      }),
    )
    return seznam
  }, [nivoji])

  function shrani() {
    if (!nivoji) return
    const vnos = nivoji
      .map((skupine) => skupine.filter((c) => c.length > 0).map((c) => c.map((x) => x.idPrijave)))
      .filter((skupine) => skupine.length > 0)
    shranjevanje.mutate(vnos)
  }

  const igralcev = (nivoji ?? []).flat(2).length

  return (
    <ModalnoOkno
      naslov="Skupine po nivojih"
      podnaslov={nivoji ? `${igralcev} ${sklonIgralcev(igralcev)}` : undefined}
      onZapri={onZapri}
      siroko
    >
      <p className="obvestilo">
        Nivoji si sledijo od najmočnejšega. Igralca prestaviš z izbirnikom ob imenu; dodaš lahko
        skupino ali nivo. Skupine igrajo vsak z vsakim, njihove tekme in žrebi nastanejo ob shranjevanju.
      </p>

      {izhodisce === 'predlog' && (
        <div className="zreb__krmila">
          <button
            type="button"
            className="gumb gumb--majhen"
            disabled={predlog.isPending}
            onClick={() => predlog.mutate()}
          >
            {predlog.isPending ? 'Žrebam …' : 'Naključno'}
          </button>
          <span className="sekcija__meta">nov naključni žreb po jakostnih pasovih</span>
        </div>
      )}
      <SporociloNapake napaka={predlog.error} />

      {!nivoji ? (
        <p className="obvestilo">Nalaganje …</p>
      ) : (
        nivoji.map((skupine, n) => {
          const velikost = skupine.reduce((vsota, c) => vsota + c.length, 0)
          return (
            <section className="sv-urejanje__nivo" key={n}>
              <div className="naslovna-vrstica">
                <h3>Nivo {n + 1}</h3>
                <span className="sekcija__meta">
                  {velikost} {sklonIgralcev(velikost)} · {skupine.length} {sklonSkupin(skupine.length)}
                </span>
              </div>
              <div className="sv-urejanje__skupine">
                {skupine.map((clani, j) => (
                  <div className="sv-urejanje__skupina" key={j}>
                    <div className="sv-urejanje__glava">
                      <span className="sv-urejanje__oznaka">{oznaka(n, j)}</span>
                      <span className="sekcija__meta">
                        {clani.length} {sklonIgralcev(clani.length)}
                      </span>
                      {clani.length === 0 && (
                        <button
                          type="button"
                          className="gumb gumb--majhen"
                          onClick={() => odstraniSkupino(n, j)}
                        >
                          Odstrani
                        </button>
                      )}
                    </div>
                    <ul className="sv-urejanje__clani">
                      {clani.map((clan) => (
                        <li key={clan.idPrijave}>
                          <span className="sv-urejanje__ime">
                            {clan.polnoIme}
                            <span className="sv-urejanje__podrobnost">
                              {[clan.klub ?? 'brez kluba', clan.rating]
                                .filter((d) => d !== null)
                                .join(' · ')}
                            </span>
                          </span>
                          <select
                            aria-label={`Premakni igralca ${clan.polnoIme}`}
                            value={oznaka(n, j)}
                            onChange={(d) => premakni(clan.idPrijave, d.target.value)}
                          >
                            {cilji.map((cilj) => (
                              <option key={cilj.oznaka} value={cilj.oznaka}>
                                {cilj.oznaka}
                              </option>
                            ))}
                          </select>
                        </li>
                      ))}
                    </ul>
                  </div>
                ))}
              </div>
              <div className="zreb__krmila">
                <button type="button" className="gumb gumb--majhen" onClick={() => dodajSkupino(n)}>
                  Dodaj skupino
                </button>
              </div>
            </section>
          )
        })
      )}

      {nivoji && (
        <div className="zreb__krmila">
          <button type="button" className="gumb gumb--majhen" onClick={dodajNivo}>
            Dodaj nivo
          </button>
        </div>
      )}

      {opozorila.length > 0 && (
        <div className="zreb__napake">
          <p className="podnaslov-sekcije">Skupine niso pripravljene</p>
          <ul className="zreb__seznam">
            {opozorila.map((o) => (
              <li key={o}>{o}</li>
            ))}
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
          disabled={!nivoji || opozorila.length > 0 || shranjevanje.isPending}
          onClick={shrani}
        >
          {shranjevanje.isPending ? 'Shranjujem …' : 'Shrani skupine in začni'}
        </button>
      </div>
    </ModalnoOkno>
  )
}
