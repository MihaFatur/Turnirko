/* Dodatni lestvici na dnu strani lige: najboljši posamezniki in najboljše
   dvojice te lige.

   Zakaj sta zložljivi in na dnu: glavna lestvica so ekipe (liga je ekipno
   tekmovanje) in razpored — to gledalec ob prihodu išče. Osebni izkupički so
   drugo branje, zato stojita sklopa zaprta pod njima in se odpreta na klik.
   Poizvedba teče šele ob odprtju: komponenta z njo se do takrat ne izriše.

   Obe lestvici štejeta SAMO tekme te lige (rating tega ne zna — teče čez vsa
   tekmovanja) in imata isto merilo: zmage, ob izenačenju uspešnost in razlika
   nizov. Posamične tekme in dvojice sta ločena seznama, ker izida para ni
   mogoče pripisati posamezniku — isto pravilo kot pri ELO. */
import { useId, useState } from 'react'
import type { ReactNode } from 'react'
import { Link } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'

import { ligeApi } from '../api/zahteve'
import type { LestvicaDvojiceDto } from '../api/tipi'
import { NapakaPoizvedbe } from './NapakaPoizvedbe'

export function LestviceLige({ idLiga, jeTelefon }: { idLiga: number; jeTelefon: boolean }) {
  return (
    <div className="lestvice-lige">
      <ZlozljivaSekcija naslov="Posamezniki" meta="Po zmagah v tej ligi">
        <LestvicaIgralcev idLiga={idLiga} jeTelefon={jeTelefon} />
      </ZlozljivaSekcija>
      <ZlozljivaSekcija naslov="Dvojice" meta="Po zmagah v tej ligi">
        <LestvicaDvojic idLiga={idLiga} jeTelefon={jeTelefon} />
      </ZlozljivaSekcija>
    </div>
  )
}

/* Zaprt sklop: glava je gumb čez vso širino (na dotik je tako cela vrstica
   zadetkovna površina), pod njo pa se izriše vsebina. Znak ▾/▴ je edini
   grafični del — ikone sistem ne dovoli, beseda "Odpri" pa bi ob naslovu
   tekmovala z njim. */
function ZlozljivaSekcija({
  naslov,
  meta,
  children,
}: {
  naslov: string
  meta: string
  children: ReactNode
}) {
  const [odprta, nastaviOdprto] = useState(false)
  const idVsebine = useId()

  return (
    <section className="zlozljiva">
      <button
        type="button"
        className="zlozljiva__glava"
        aria-expanded={odprta}
        aria-controls={idVsebine}
        onClick={() => nastaviOdprto(!odprta)}
      >
        <span className="zlozljiva__naslov">{naslov}</span>
        <span className="zlozljiva__meta">
          {meta}
          <span className="zlozljiva__znak" aria-hidden="true">
            {odprta ? '▴' : '▾'}
          </span>
        </span>
      </button>
      {odprta && (
        <div className="zlozljiva__vsebina" id={idVsebine}>
          {children}
        </div>
      )}
    </section>
  )
}

/* ---------- Posamezniki ---------- */

function LestvicaIgralcev({ idLiga, jeTelefon }: { idLiga: number; jeTelefon: boolean }) {
  const lestvica = useQuery({
    queryKey: ['lestvica-igralcev', idLiga],
    queryFn: () => ligeApi.lestvicaIgralcev(idLiga),
  })
  const vrstice = lestvica.data ?? []

  if (lestvica.isPending) return <p className="obvestilo">Nalaganje …</p>
  if (lestvica.error) return <NapakaPoizvedbe poizvedba={lestvica} kaj="lestvice posameznikov" />
  if (vrstice.length === 0) {
    return <p className="obvestilo">V tej ligi še ni odigranih posamičnih tekem.</p>
  }

  return (
    <>
      <p className="zlozljiva__stevec">
        {vrstice.length} {igralcevTekst(vrstice.length)} z odigrano tekmo
      </p>

      {jeTelefon ? (
        <div className="lestvica-mobi">
          {vrstice.map((v) => (
            <div key={v.idIgralec} className="lestvica-mobi__vrstica">
              <span className={'lestvica-mobi__mesto' + (v.mesto <= 3 ? ' lestvica-mobi__mesto--vrh' : '')}>
                {v.mesto}
              </span>
              <Link to={`/igralci/${v.idIgralec}/profil`} className="lestvica-mobi__ime">
                {v.polnoIme}
              </Link>
              <span className="lestvica-mobi__meta">
                {v.ekipa ?? '—'} · {v.odigrane} {tekemTekst(v.odigrane)} · {v.odstotek} %
              </span>
              <span className="lestvica-mobi__rating">{v.zmage}</span>
            </div>
          ))}
        </div>
      ) : (
        <div className="tabela-ovoj">
          <table className="tabela lestvice-lige__tabela">
            <caption className="samo-za-bralnik">
              Najboljši posamezniki lige po številu zmag v posamičnih tekmah te lige.
            </caption>
            <thead>
              <tr>
                <th scope="col" className="lestvica__mesto">#</th>
                <th scope="col">Igralec</th>
                <th scope="col">Ekipa</th>
                <th scope="col" className="lestvica__stevilka">Odig.</th>
                <th scope="col" className="lestvica__stevilka">Nizi</th>
                <th scope="col" className="lestvica__stevilka">Uspeh</th>
                <th scope="col" className="lestvica__rating">Zmage</th>
              </tr>
            </thead>
            <tbody>
              {vrstice.map((v) => (
                <tr key={v.idIgralec}>
                  <td className={'lestvica__mesto' + (v.mesto <= 3 ? ' lestvica__mesto--vrh' : '')}>
                    {v.mesto}
                  </td>
                  <td>
                    <Link to={`/igralci/${v.idIgralec}/profil`} className="lestvica__ime">
                      {v.polnoIme}
                    </Link>
                  </td>
                  <td className="lestvica__klub">{v.ekipa ?? '—'}</td>
                  <td className="lestvica__stevilka">{v.odigrane}</td>
                  <td className="lestvica__stevilka">
                    {v.dobljeniNizi} : {v.prejetiNizi}
                  </td>
                  <td className="lestvica__stevilka">{v.odstotek} %</td>
                  <td className="lestvica__rating">{v.zmage}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      <Opomba />
    </>
  )
}

/* ---------- Dvojice ---------- */

function LestvicaDvojic({ idLiga, jeTelefon }: { idLiga: number; jeTelefon: boolean }) {
  const lestvica = useQuery({
    queryKey: ['lestvica-dvojic', idLiga],
    queryFn: () => ligeApi.lestvicaDvojic(idLiga),
  })
  const vrstice = lestvica.data ?? []

  if (lestvica.isPending) return <p className="obvestilo">Nalaganje …</p>
  if (lestvica.error) return <NapakaPoizvedbe poizvedba={lestvica} kaj="lestvice dvojic" />
  if (vrstice.length === 0) {
    return <p className="obvestilo">V tej ligi še ni odigranih dvojic.</p>
  }

  return (
    <>
      <p className="zlozljiva__stevec">
        {vrstice.length} {dvojicTekst(vrstice.length)}
      </p>

      {jeTelefon ? (
        <div className="lestvica-mobi">
          {vrstice.map((v) => (
            <div key={kljucPara(v)} className="lestvica-mobi__vrstica">
              <span className={'lestvica-mobi__mesto' + (v.mesto <= 3 ? ' lestvica-mobi__mesto--vrh' : '')}>
                {v.mesto}
              </span>
              {/* Dve imeni v en 390 px pas ne gresta z odrezom, zato se par tu
                  edini prelomi v dve vrstici. */}
              <span className="lestvica-mobi__ime lestvica-mobi__ime--par">
                <Link to={`/igralci/${v.idPrvi}/profil`} className="lestvice-lige__ime">
                  {v.prvi}
                </Link>
                <Link to={`/igralci/${v.idDrugi}/profil`} className="lestvice-lige__ime">
                  {v.drugi}
                </Link>
              </span>
              <span className="lestvica-mobi__meta">
                {v.ekipa ?? '—'} · {v.odigrane} {tekemTekst(v.odigrane)} · {v.odstotek} %
              </span>
              <span className="lestvica-mobi__rating">{v.zmage}</span>
            </div>
          ))}
        </div>
      ) : (
        <div className="tabela-ovoj">
          <table className="tabela lestvice-lige__tabela">
            <caption className="samo-za-bralnik">
              Najboljše dvojice lige po številu zmag v tekmah dvojic te lige.
            </caption>
            <thead>
              <tr>
                <th scope="col" className="lestvica__mesto">#</th>
                <th scope="col">Dvojica</th>
                <th scope="col">Ekipa</th>
                <th scope="col" className="lestvica__stevilka">Odig.</th>
                <th scope="col" className="lestvica__stevilka">Nizi</th>
                <th scope="col" className="lestvica__stevilka">Uspeh</th>
                <th scope="col" className="lestvica__rating">Zmage</th>
              </tr>
            </thead>
            <tbody>
              {vrstice.map((v) => (
                <tr key={kljucPara(v)}>
                  <td className={'lestvica__mesto' + (v.mesto <= 3 ? ' lestvica__mesto--vrh' : '')}>
                    {v.mesto}
                  </td>
                  <td>
                    <span className="lestvice-lige__par">
                      <Link to={`/igralci/${v.idPrvi}/profil`} className="lestvica__ime">
                        {v.prvi}
                      </Link>
                      <span className="lestvice-lige__vezaj" aria-hidden="true">·</span>
                      <Link to={`/igralci/${v.idDrugi}/profil`} className="lestvica__ime">
                        {v.drugi}
                      </Link>
                    </span>
                  </td>
                  <td className="lestvica__klub">{v.ekipa ?? '—'}</td>
                  <td className="lestvica__stevilka">{v.odigrane}</td>
                  <td className="lestvica__stevilka">
                    {v.dobljeniNizi} : {v.prejetiNizi}
                  </td>
                  <td className="lestvica__stevilka">{v.odstotek} %</td>
                  <td className="lestvica__rating">{v.zmage}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      <Opomba />
    </>
  )
}

/* Merilo pod lestvico in ne nad njo: pravilo je pojasnilo za tistega, ki ga
   vrstni red preseneti, ne uvod v branje. */
function Opomba() {
  return (
    <p className="lestvica__opomba">
      Merilo so zmage; ob izenačenju odločata uspešnost in razlika nizov. Štejejo samo
      tekme te lige.
    </p>
  )
}

/* Par nima svojega identifikatorja - ključ sestavita igralca, tako kot na
   strežniku (glej Par v LestvicaLigeStoritev). */
function kljucPara(v: LestvicaDvojiceDto): string {
  return `${v.idPrvi}-${v.idDrugi}`
}

/* ---------- Sklanjanje ---------- */

function igralcevTekst(n: number): string {
  if (n === 1) return 'igralec'
  if (n === 2) return 'igralca'
  if (n === 3 || n === 4) return 'igralci'
  return 'igralcev'
}

function dvojicTekst(n: number): string {
  if (n === 1) return 'dvojica'
  if (n === 2) return 'dvojici'
  if (n === 3 || n === 4) return 'dvojice'
  return 'dvojic'
}

function tekemTekst(n: number): string {
  if (n === 1) return 'tekma'
  if (n === 2) return 'tekmi'
  if (n === 3 || n === 4) return 'tekme'
  return 'tekem'
}
