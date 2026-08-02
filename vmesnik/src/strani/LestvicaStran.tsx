/* Globalna lestvica igralcev po klubskem ELO ratingu, z razmerjem
   zmag in porazov prek vseh dogodkov. Vidna vsem (tudi gostom). */
import { useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'

import { statistikaApi } from '../api/zahteve'
import { NapakaPoizvedbe } from '../komponente/NapakaPoizvedbe'
import { useAvtentikacija } from '../avtentikacija/AvtentikacijaKontekst'

export function LestvicaStran() {
  const lestvica = useQuery({ queryKey: ['lestvica'], queryFn: statistikaApi.lestvica })
  const { mojIdIgralec } = useAvtentikacija()
  const [iskanje, nastaviIskanje] = useState('')

  /* Mesto pripnemo pred iskanjem, da ostane pravo tudi v zoženem seznamu. */
  const prikazani = useMemo(() => {
    const vse = (lestvica.data ?? []).map((igralec, indeks) => ({ igralec, mesto: indeks + 1 }))
    const iskano = iskanje.trim().toLowerCase()
    if (!iskano) return vse
    return vse.filter(({ igralec }) =>
      `${igralec.polnoIme} ${igralec.klub ?? ''}`.toLowerCase().includes(iskano),
    )
  }, [lestvica.data, iskanje])

  const vseh = lestvica.data?.length ?? 0
  const klubov = useMemo(
    () => new Set((lestvica.data ?? []).map((v) => v.klub).filter((v) => v !== null)).size,
    [lestvica.data],
  )

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

        {lestvica.data && lestvica.data.length === 0 && (
          <p className="obvestilo">Še ni igralcev.</p>
        )}

        {vseh > 0 && prikazani.length === 0 && (
          <p className="obvestilo">Iskanju ne ustreza noben igralec.</p>
        )}

        {prikazani.length > 0 && (
          <div className="tabela-ovoj">
            <table className="tabela">
              <caption className="samo-za-bralnik">Lestvica igralcev po klubskem ratingu ELO</caption>
              <thead>
                <tr>
                  <th scope="col" className="lestvica__mesto">#</th>
                  <th scope="col">Igralec</th>
                  <th scope="col">Klub</th>
                  <th scope="col" className="lestvica__stevilka lestvica__odigrane">Odigrane</th>
                  <th scope="col" className="lestvica__stevilka">Z – P</th>
                  <th scope="col" className="lestvica__uspesnost-glava">Uspešnost</th>
                  <th scope="col" className="lestvica__rating">Rating</th>
                </tr>
              </thead>
              <tbody>
                {prikazani.map(({ igralec, mesto }) => {
                  const odstotek =
                    igralec.odigrane > 0
                      ? Math.round((igralec.zmage / igralec.odigrane) * 100)
                      : null
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
                      <td>
                        <Link to={`/igralci/${igralec.idIgralca}/profil`} className="lestvica__ime">
                          {igralec.polnoIme}
                        </Link>
                      </td>
                      <td className="lestvica__klub">{igralec.klub ?? '—'}</td>
                      <td className="lestvica__stevilka lestvica__odigrane">{igralec.odigrane}</td>
                      <td className="lestvica__stevilka">
                        <span className="lestvica__zmage">{igralec.zmage}</span>
                        <span className="lestvica__locilo"> – </span>
                        <span className="lestvica__porazi">{igralec.porazi}</span>
                      </td>
                      <td className="lestvica__uspesnost-celica">
                        <span className="lestvica__uspesnost">
                          {odstotek !== null && (
                            <span className="palica">
                              <span className="palica__polnilo" style={{ width: `${odstotek}%` }} />
                            </span>
                          )}
                          <span className="lestvica__odstotek">
                            {odstotek === null ? '—' : `${odstotek} %`}
                          </span>
                        </span>
                      </td>
                      <td className="lestvica__rating">{igralec.rating ?? '—'}</td>
                    </tr>
                  )
                })}
              </tbody>
            </table>
          </div>
        )}

        <p className="namig">
          Igralci brez obračunane tekme še niso na lestvici. Ime igralca vodi na profil s
          statistiko.
        </p>
      </div>
    </section>
  )
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
