/* Pripomoček "1 na 1": medsebojni izid dveh igralcev prek vseh tekmovanj —
   turnirskih tekem in posamičnih tekem ligaških srečanj (dvojice ne štejejo).
   Kartice rezultata in izbira igralcev so v skupni tristolpčni mreži, zato je
   vsak spustni seznam poravnan pod svojo kartico, gumb za naključni par pa je
   na sredini pod rezultatom. Ob prihodu se izžreba naključni par.
   Uporablja se na domači strani (strnjeno) in na strani 1 na 1 (z zgodovino
   tekem). Viden vsem (tudi gostom). */
import { useEffect, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'

import { igralciApi, statistikaApi } from '../api/zahteve'
import type { DvobojDto, IgralecDto } from '../api/tipi'
import { OZNAKE_IZID } from '../api/tipi'
import { SporociloNapake } from './SporociloNapake'
import { SpremembaElo } from './SpremembaElo'

interface Lastnosti {
  /* Ali pod karticami pokaži tabelo vseh medsebojnih tekem. */
  pokaziZgodovino?: boolean
}

export function EnaNaEna({ pokaziZgodovino = false }: Lastnosti) {
  const igralci = useQuery({ queryKey: ['igralci'], queryFn: igralciApi.seznam })

  /* Če sta igralca podana v naslovu (klik "Podrobna primerjava" z domače strani
     odpre /dvoboj?prvi=1&drugi=5), začni z njima; sicer se izžreba naključni par. */
  const [iskalniParametri] = useSearchParams()
  const [prvi, nastaviPrvega] = useState<number | ''>(() =>
    steviloIzParametra(iskalniParametri.get('prvi')),
  )
  const [drugi, nastaviDrugega] = useState<number | ''>(() =>
    steviloIzParametra(iskalniParametri.get('drugi')),
  )

  /* Če igralca nista prišla iz naslova, ob prvem nalaganju izberi naključni par. */
  useEffect(() => {
    const seznam = igralci.data
    if (!seznam || seznam.length < 2 || prvi !== '' || drugi !== '') return
    const [a, b] = dvaNakljucna(seznam)
    nastaviPrvega(a.id)
    nastaviDrugega(b.id)
  }, [igralci.data, prvi, drugi])

  const veljavniPar = prvi !== '' && drugi !== '' && prvi !== drugi

  const dvoboj = useQuery({
    queryKey: ['dvoboj', prvi, drugi],
    queryFn: () => statistikaApi.dvoboj(Number(prvi), Number(drugi)),
    enabled: veljavniPar,
  })

  function izzrebaj() {
    if (!igralci.data || igralci.data.length < 2) return
    const [a, b] = dvaNakljucna(igralci.data)
    nastaviPrvega(a.id)
    nastaviDrugega(b.id)
  }

  const premalo = (igralci.data?.length ?? 0) < 2
  const d = veljavniPar ? dvoboj.data : undefined

  return (
    <div className="enanaena">
      <SporociloNapake napaka={igralci.error} />
      {veljavniPar && <SporociloNapake napaka={dvoboj.error} />}
      {veljavniPar && dvoboj.isPending && <p className="obvestilo">Nalaganje …</p>}

      <div className="enanaena__plosca">
        {d && (
          <>
            <IgralecKartica
              polozaj="enanaena__kartica--1"
              idIgralca={d.prvi.id}
              ime={d.prvi.polnoIme}
              klub={d.prvi.klub}
              rating={d.prvi.rating}
              vodi={d.zmagePrvega > d.zmageDrugega}
            />
            <div className="enanaena__sredina">
              <div className="enanaena__oznaka">Medsebojni rezultat</div>
              <div className="enanaena__stevilo">
                <span className={d.zmagePrvega >= d.zmageDrugega ? 'enanaena__vodi' : ''}>
                  {d.zmagePrvega}
                </span>
                <span className="enanaena__crtica">:</span>
                <span className={d.zmageDrugega >= d.zmagePrvega ? 'enanaena__vodi' : ''}>
                  {d.zmageDrugega}
                </span>
              </div>
              <div className="enanaena__skupaj">
                {d.odigrane === 0
                  ? 'še nista igrala'
                  : `Skupaj tekem: ${d.odigrane} · nizi ${d.niziPrvega}:${d.niziDrugega}`}
              </div>
              {!pokaziZgodovino && (
                <div className="enanaena__podrobno">
                  <Link to={`/dvoboj?prvi=${prvi}&drugi=${drugi}`} className="domov__vec">
                    Podrobna primerjava →
                  </Link>
                </div>
              )}
            </div>
            <IgralecKartica
              polozaj="enanaena__kartica--2"
              idIgralca={d.drugi.id}
              ime={d.drugi.polnoIme}
              klub={d.drugi.klub}
              rating={d.drugi.rating}
              vodi={d.zmageDrugega > d.zmagePrvega}
            />
          </>
        )}

        <IzbiraIgralca
          polozaj="enanaena__polje--1"
          oznaka="1. igralec"
          igralci={igralci.data ?? []}
          vrednost={prvi}
          izkljuci={drugi}
          naSpremembo={nastaviPrvega}
        />
        <button className="gumb gumb--glavni enanaena__zreb" onClick={izzrebaj} disabled={premalo}>
          🎲 Naključno
        </button>
        <IzbiraIgralca
          polozaj="enanaena__polje--2"
          oznaka="2. igralec"
          igralci={igralci.data ?? []}
          vrednost={drugi}
          izkljuci={prvi}
          naSpremembo={nastaviDrugega}
        />
      </div>

      {prvi !== '' && drugi !== '' && prvi === drugi && (
        <p className="napaka">Izberi dva različna igralca.</p>
      )}

      {igralci.data && premalo && (
        <p className="obvestilo">Za primerjavo sta potrebna vsaj dva igralca.</p>
      )}

      {pokaziZgodovino && d && d.tekme.length > 0 && <Zgodovina dvoboj={d} />}
    </div>
  )
}

function IzbiraIgralca({
  polozaj,
  oznaka,
  igralci,
  vrednost,
  izkljuci,
  naSpremembo,
}: {
  polozaj: string
  oznaka: string
  igralci: IgralecDto[]
  vrednost: number | ''
  izkljuci: number | ''
  naSpremembo: (id: number | '') => void
}) {
  return (
    <div className={'enanaena__polje ' + polozaj}>
      <select
        aria-label={oznaka}
        value={vrednost}
        onChange={(d) => naSpremembo(d.target.value === '' ? '' : Number(d.target.value))}
      >
        <option value="">— izberi —</option>
        {igralci
          .filter((i) => i.id !== izkljuci)
          .map((i) => (
            <option key={i.id} value={i.id}>
              {i.priimek} {i.ime}
            </option>
          ))}
      </select>
    </div>
  )
}

function IgralecKartica({
  polozaj,
  idIgralca,
  ime,
  klub,
  rating,
  vodi,
}: {
  polozaj: string
  idIgralca: number
  ime: string
  klub: string | null
  rating: number | null
  vodi: boolean
}) {
  return (
    <div className={'enanaena__igralec ' + polozaj + (vodi ? ' enanaena__igralec--vodi' : '')}>
      <div className="enanaena__avatar">👤</div>
      <div className="enanaena__ime">
        <Link to={`/igralci/${idIgralca}/profil`}>{ime}</Link>
      </div>
      {klub && <div className="enanaena__klub">{klub}</div>}
      <div className="enanaena__rating">
        Rating: <strong>{rating !== null ? rating : '—'}</strong>
      </div>
    </div>
  )
}

function Zgodovina({ dvoboj }: { dvoboj: DvobojDto }) {
  const { prvi, drugi } = dvoboj
  return (
    <table className="tabela enanaena__tabela">
      <thead>
        <tr>
          <th>Tekmovanje</th>
          <th>Dogodek / kolo</th>
          <th className="lestvica__stevilka">Rezultat</th>
          <th className="lestvica__stevilka">ELO</th>
          <th>Zmagovalec</th>
        </tr>
      </thead>
      <tbody>
        {dvoboj.tekme.map((t) => (
          <tr key={(t.ligaska ? 'l' : 't') + t.idTekme}>
            <td>
              {t.tekmovanje}
              {t.ligaska && <span className="enanaena__vir">liga</span>}
            </td>
            <td>{t.del}</td>
            <td className="lestvica__stevilka">
              {t.niziPrvega}:{t.niziDrugega}
              {t.izidTip && t.izidTip !== 'IGRANO' && (
                <span className="enanaena__posebni"> ({OZNAKE_IZID[t.izidTip]})</span>
              )}
            </td>
            <td className="lestvica__stevilka enanaena__elo-celica">
              <SpremembaElo vrednost={t.spremembaPrvega} />
              <span className="enanaena__elo-locilo">/</span>
              <SpremembaElo vrednost={t.spremembaDrugega} />
            </td>
            <td>{t.zmagalPrvi ? prvi.polnoIme : drugi.polnoIme}</td>
          </tr>
        ))}
      </tbody>
    </table>
  )
}

/* Pretvori naslovni parameter (npr. ?prvi=5) v veljaven id igralca ali v prazno. */
function steviloIzParametra(v: string | null): number | '' {
  if (v === null) return ''
  const n = Number(v)
  return Number.isInteger(n) && n > 0 ? n : ''
}

/* Dva različna naključna igralca s seznama. */
function dvaNakljucna(seznam: IgralecDto[]): [IgralecDto, IgralecDto] {
  const a = Math.floor(Math.random() * seznam.length)
  let b = Math.floor(Math.random() * seznam.length)
  while (b === a) b = Math.floor(Math.random() * seznam.length)
  return [seznam[a], seznam[b]]
}
