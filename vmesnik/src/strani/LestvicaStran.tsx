/* Globalna lestvica igralcev po klubskem ELO ratingu, z razmerjem
   zmag in porazov prek vseh dogodkov. Vidna vsem (tudi gostom). */
import { useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'

import { statistikaApi } from '../api/zahteve'
import type { KategorijaIgralca, LestvicaIgralcaDto } from '../api/tipi'
import { NapakaPoizvedbe } from '../komponente/NapakaPoizvedbe'
import { useAvtentikacija } from '../avtentikacija/AvtentikacijaKontekst'

type Merilo = 'rating' | 'uspesnost' | 'odigrane'
type IzbranaKategorija = KategorijaIgralca | 'VSI'

const MERILA: { kljuc: Merilo; oznaka: string }[] = [
  { kljuc: 'rating', oznaka: 'Rating' },
  { kljuc: 'uspesnost', oznaka: 'Uspešnost' },
  { kljuc: 'odigrane', oznaka: 'Odigrane' },
]

/* Napisi in vrstni red gumbov kategorij. Katere se pokažejo, določijo podatki
   (spodaj) — tu je samo, kako se berejo in v kakšnem zaporedju stojijo. */
const KATEGORIJE: { kljuc: KategorijaIgralca; oznaka: string }[] = [
  { kljuc: 'CLANI', oznaka: 'Člani' },
  { kljuc: 'CLANICE', oznaka: 'Članice' },
  { kljuc: 'U19', oznaka: 'U19' },
  { kljuc: 'VETERANI', oznaka: 'Veterani' },
]

export function LestvicaStran() {
  const lestvica = useQuery({ queryKey: ['lestvica'], queryFn: statistikaApi.lestvica })
  const { mojIdIgralec } = useAvtentikacija()
  const [iskanje, nastaviIskanje] = useState('')
  const [kategorija, nastaviKategorijo] = useState<IzbranaKategorija>('VSI')
  const [merilo, nastaviMerilo] = useState<Merilo>('rating')

  /* Mesto pripnemo pred filtriranjem, da ostane pravo tudi v zoženem seznamu
     (v kategoriji "Članice" so mesta 4, 9, 13 in ne 1, 2, 3). Pri drugih
     merilih mesto po ratingu ne pomeni nič, zato se takrat prešteva znova. */
  const prikazani = useMemo(() => {
    const vse = (lestvica.data ?? []).map((igralec, indeks) => ({ igralec, mesto: indeks + 1 }))
    const iskano = iskanje.trim().toLowerCase()
    const izbrani = vse
      .filter(({ igralec }) => kategorija === 'VSI' || igralec.kategorija === kategorija)
      .filter(
        ({ igralec }) =>
          !iskano || `${igralec.polnoIme} ${igralec.klub ?? ''}`.toLowerCase().includes(iskano),
      )
    if (merilo === 'rating') return izbrani
    return izbrani
      .slice()
      .sort((a, b) =>
        merilo === 'uspesnost'
          ? uspesnost(b.igralec) - uspesnost(a.igralec)
          : b.igralec.odigrane - a.igralec.odigrane,
      )
      .map(({ igralec }, indeks) => ({ igralec, mesto: indeks + 1 }))
  }, [lestvica.data, iskanje, kategorija, merilo])

  const vseh = lestvica.data?.length ?? 0
  const klubov = useMemo(
    () => new Set((lestvica.data ?? []).map((v) => v.klub).filter((v) => v !== null)).size,
    [lestvica.data],
  )

  /* Ponujene so samo kategorije, ki v podatkih res obstajajo; če jih ni
     nobene, pas gumbov odpade v celoti. */
  const ponujeneKategorije = useMemo(() => {
    const najdene = new Set((lestvica.data ?? []).map((v) => v.kategorija))
    return KATEGORIJE.filter((k) => najdene.has(k.kljuc))
  }, [lestvica.data])

  const imeKategorije = KATEGORIJE.find((k) => k.kljuc === kategorija)?.oznaka
  const opisTabele =
    'Lestvica igralcev po klubskem ratingu ELO' +
    (imeKategorije ? ` — kategorija ${imeKategorije}` : '')

  return (
    <section>
      <div className="stran-glava stran-glava--dno">
        <div>
          <h1 className="naslov-strani">
            <span className="naslov-strani__nad">Klubski ELO</span>
            <span className="naslov-strani__glavni">Lestvica</span>
          </h1>
          <p className="uvod">
            Razmerje zmag in porazov prek vseh turnirjev in ligaških srečanj. Rating se
            preračuna po vsaki obračunani tekmi.
          </p>
        </div>
        <div>
          <label className="obrazec__polje">
            <span>Išči</span>
            <input
              className="iskalnik"
              placeholder="Išči po imenu ali klubu …"
              value={iskanje}
              onChange={(d) => nastaviIskanje(d.target.value)}
            />
          </label>
          {lestvica.data && (
            <div className="stevci">
              <span className="stevci__postavka">{vseh} {sklonIgralcev(vseh)}</span>
              <span className="stevci__postavka">{klubov} {sklonKlubov(klubov)}</span>
            </div>
          )}
        </div>
      </div>

      <div>
        <div className="naslovna-vrstica">
          <h2>Razvrstitev</h2>
          {lestvica.data && (
            <span className="sekcija__meta">
              Prikazanih {prikazani.length} od {vseh}
            </span>
          )}
        </div>

        <NapakaPoizvedbe poizvedba={lestvica} kaj="lestvice" />
        {lestvica.isPending && <p className="obvestilo">Nalaganje …</p>}

        {vseh > 0 && (
          <div className="lestvica__filtri">
            {ponujeneKategorije.length > 0 && (
              <div className="lestvica__filter">
                <span className="lestvica__filter-oznaka">Kategorija</span>
                <div className="izbirnik" role="group" aria-label="Kategorija">
                  <FilterGumb
                    oznaka="Vsi"
                    aktiven={kategorija === 'VSI'}
                    naKlik={() => nastaviKategorijo('VSI')}
                  />
                  {ponujeneKategorije.map((k) => (
                    <FilterGumb
                      key={k.kljuc}
                      oznaka={k.oznaka}
                      aktiven={kategorija === k.kljuc}
                      naKlik={() => nastaviKategorijo(k.kljuc)}
                    />
                  ))}
                </div>
              </div>
            )}
            <div className="lestvica__filter lestvica__filter--merilo">
              <span className="lestvica__filter-oznaka">Razvrsti po</span>
              <div className="izbirnik" role="group" aria-label="Razvrsti po">
                {MERILA.map((m) => (
                  <FilterGumb
                    key={m.kljuc}
                    oznaka={m.oznaka}
                    aktiven={merilo === m.kljuc}
                    naKlik={() => nastaviMerilo(m.kljuc)}
                  />
                ))}
              </div>
            </div>
          </div>
        )}

        {lestvica.data && lestvica.data.length === 0 && (
          <p className="obvestilo">Še ni igralcev.</p>
        )}

        {vseh > 0 && prikazani.length === 0 && (
          <p className="obvestilo">
            {iskanje.trim()
              ? 'Iskanju ne ustreza noben igralec.'
              : 'Nobenega igralca v tej kategoriji.'}
          </p>
        )}

        {prikazani.length > 0 && (
          <>
            <div className="tabela-ovoj lestvica-ovoj">
              <table className="tabela lestvica--globalna">
                <caption className="samo-za-bralnik">{opisTabele}</caption>
                {/* Fiksne širine stolpcev: dolgo ime in dolg klub se odrežeta,
                    namesto da bi prelomila vrstico ali potisnila številke. */}
                <colgroup>
                  <col className="lestvica__stolpec--mesto" />
                  <col className="lestvica__stolpec--gibanje" />
                  <col className="lestvica__stolpec--igralec" />
                  <col className="lestvica__stolpec--klub" />
                  <col className="lestvica__stolpec--izid" />
                  <col className="lestvica__stolpec--rating" />
                  <col className="lestvica__stolpec--delta" />
                </colgroup>
                <thead>
                  <tr>
                    <th scope="col" className="lestvica__mesto">#</th>
                    <th scope="col" className="lestvica__gibanje">Gib.</th>
                    <th scope="col">Igralec</th>
                    <th scope="col">Klub</th>
                    <th scope="col" className="lestvica__stevilka">Z – P</th>
                    <th scope="col" className="lestvica__rating">Rating</th>
                    <th scope="col" className="lestvica__delta">Δ 30 dni</th>
                  </tr>
                </thead>
                <tbody>
                  {prikazani.map(({ igralec, mesto }) => {
                    const gib = gibanje(igralec.premik)
                    const delta = spremembaRatinga(igralec.spremembaRatinga)
                    return (
                      <tr
                        key={igralec.idIgralca}
                        className={
                          igralec.idIgralca === mojIdIgralec ? 'lestvica__vrstica--jaz' : undefined
                        }
                      >
                        {/* Prva tri mesta so modra - edini poudarek v stolpcu mest. */}
                        <td
                          className={
                            'lestvica__mesto' + (mesto <= 3 ? ' lestvica__mesto--vrh' : '')
                          }
                        >
                          {mesto}
                        </td>
                        {/* Puščica ni edini nosilec pomena: barvo podvoji opis. */}
                        <td className={`lestvica__gibanje lestvica__gibanje--${gib.smer}`}>
                          <span aria-label={gib.opis}>{gib.zapis}</span>
                        </td>
                        <td className="lestvica__igralec">
                          <Link to={`/igralci/${igralec.idIgralca}/profil`} className="lestvica__ime">
                            {igralec.polnoIme}
                          </Link>
                        </td>
                        <td className="lestvica__klub">{igralec.klub ?? '—'}</td>
                        <td className="lestvica__stevilka">
                          <span className="lestvica__zmage">{igralec.zmage}</span>
                          <span className="lestvica__locilo"> – </span>
                          <span className="lestvica__porazi">{igralec.porazi}</span>
                        </td>
                        <td className="lestvica__rating">{igralec.rating ?? '—'}</td>
                        <td className={`lestvica__delta lestvica__delta--${delta.smer}`}>
                          {delta.zapis}
                        </td>
                      </tr>
                    )
                  })}
                </tbody>
              </table>
            </div>

            <p className="lestvica__opomba">Gib. in Δ: primerjava s stanjem pred 30 dnevi</p>
          </>
        )}

        <p className="namig">
          Igralci brez obračunane tekme še niso na lestvici. Ime igralca vodi na profil s
          statistiko.
        </p>
      </div>
    </section>
  )
}

/* Gumb pasu filtrov: aria-pressed pove bralniku zaslona, kaj je izbrano —
   črna ploskev je za to samo vidni znak. */
function FilterGumb({
  oznaka,
  aktiven,
  naKlik,
}: {
  oznaka: string
  aktiven: boolean
  naKlik: () => void
}) {
  return (
    <button
      type="button"
      className={'izbirnik__gumb' + (aktiven ? ' izbirnik__gumb--aktiven' : '')}
      aria-pressed={aktiven}
      onClick={naKlik}
    >
      {oznaka}
    </button>
  )
}

/* Delež zmag; kdor še ni igral, gre na konec seznama in ne na vrh. */
function uspesnost(igralec: LestvicaIgralcaDto): number {
  return igralec.odigrane > 0 ? igralec.zmage / igralec.odigrane : -1
}

/* Premik mesta proti stanju pred 30 dnevi. Brez podatka in brez premika sta
   isti zapis (nevtralna črtica) — v obeh primerih se ni kaj pokazati. */
function gibanje(premik: number | null): { zapis: string; smer: string; opis: string } {
  if (premik === null) return { zapis: '–', smer: 'brez', opis: 'Ni podatka o premiku' }
  if (premik === 0) return { zapis: '–', smer: 'brez', opis: 'Brez premika' }
  const mest = Math.abs(premik)
  return premik > 0
    ? { zapis: `↑${mest}`, smer: 'gor', opis: `Napredoval za ${mest} ${sklonMest(mest)}` }
    : { zapis: `↓${mest}`, smer: 'dol', opis: `Nazadoval za ${mest} ${sklonMest(mest)}` }
}

/* Razlika ratinga proti stanju pred 30 dnevi. Minus je tipografski (U+2212),
   da je enako širok kot plus in se stolpec poravna. */
function spremembaRatinga(sprememba: number | null): { zapis: string; smer: string } {
  if (sprememba === null) return { zapis: '—', smer: 'brez' }
  if (sprememba === 0) return { zapis: '±0', smer: 'brez' }
  return sprememba > 0
    ? { zapis: `+${sprememba}`, smer: 'gor' }
    : { zapis: `−${Math.abs(sprememba)}`, smer: 'dol' }
}

/* Slovnično pravilna oblika besede "mesto" glede na število. */
function sklonMest(n: number): string {
  const ostanek = n % 100
  if (ostanek === 1) return 'mesto'
  if (ostanek === 2) return 'mesti'
  if (ostanek === 3 || ostanek === 4) return 'mesta'
  return 'mest'
}

/* Slovnično pravilna oblika besede "igralec" glede na število. */
function sklonIgralcev(n: number): string {
  const ostanek = n % 100
  if (ostanek === 1) return 'igralec'
  if (ostanek === 2) return 'igralca'
  if (ostanek === 3 || ostanek === 4) return 'igralci'
  return 'igralcev'
}

/* Slovnično pravilna oblika besede "klub" glede na število. */
function sklonKlubov(n: number): string {
  const ostanek = n % 100
  if (ostanek === 1) return 'klub'
  if (ostanek === 2) return 'kluba'
  if (ostanek === 3 || ostanek === 4) return 'klubi'
  return 'klubov'
}
