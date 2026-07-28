/* Domača (začetna) stran - nadzorna plošča v dveh stolpcih: levo turnirji,
   lige in strnjen pripomoček "1 na 1", desno pa daljša lestvica igralcev.
   Vse je bralno in vidno tudi gostom; poglobljeni pogledi so dosegljivi prek
   povezav. */
import type { ReactNode } from 'react'
import { Link } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'

import { ligeApi, statistikaApi, turnirjiApi } from '../api/zahteve'
import type { LestvicaIgralcaDto, LigaDto, TurnirDto } from '../api/tipi'
import { EnaNaEna } from '../komponente/EnaNaEna'
import { ZnackaStatusa } from '../komponente/Znacka'
import { oblikujObdobje } from '../pomozno/oblikovanje'

export function DomacaStran() {
  const turnirji = useQuery({ queryKey: ['turnirji'], queryFn: turnirjiApi.seznam })
  const lige = useQuery({ queryKey: ['lige'], queryFn: ligeApi.seznam })
  const lestvica = useQuery({ queryKey: ['lestvica'], queryFn: statistikaApi.lestvica })

  return (
    <section className="domov">
      <div className="naslovna-vrstica">
        <div>
          <h1>Pregled</h1>
          <p className="podnaslov">
            Namiznoteniški turnirji, žive lestvice in medsebojni izidi na enem mestu.
          </p>
        </div>
      </div>

      <div className="domov__mreza">
        <div className="domov__stolpec">
          <Plosca naslov="Turnirji" povezava={{ pot: '/turnirji', oznaka: 'Vsi turnirji' }}>
            <TurnirjiPovzetek turnirji={turnirji.data} nalaganje={turnirji.isPending} />
          </Plosca>

          <Plosca naslov="Lige" povezava={{ pot: '/lige', oznaka: 'Vse lige' }}>
            <LigePovzetek lige={lige.data} nalaganje={lige.isPending} />
          </Plosca>

          <Plosca naslov="Ena na ena" kompakt>
            <EnaNaEna />
          </Plosca>
        </div>

        <Plosca naslov="Lestvica igralcev" povezava={{ pot: '/lestvica', oznaka: 'Cela lestvica' }}>
          <MiniLestvica vrstice={lestvica.data} nalaganje={lestvica.isPending} />
        </Plosca>
      </div>
    </section>
  )
}

function Plosca({
  naslov,
  povezava,
  kompakt,
  children,
}: {
  naslov: string
  povezava?: { pot: string; oznaka: string }
  /* Strnjena različica (npr. pripomoček "1 na 1" v ožjem stolpcu). */
  kompakt?: boolean
  children: ReactNode
}) {
  return (
    <div className={'plosca domov__plosca' + (kompakt ? ' domov__plosca--kompakt' : '')}>
      <div className="domov__plosca-glava">
        <h2>{naslov}</h2>
        {povezava && (
          <Link to={povezava.pot} className="domov__vec">
            {povezava.oznaka} →
          </Link>
        )}
      </div>
      {children}
    </div>
  )
}

function MiniLestvica({
  vrstice,
  nalaganje,
}: {
  vrstice: LestvicaIgralcaDto[] | undefined
  nalaganje: boolean
}) {
  if (nalaganje) return <p className="obvestilo">Nalaganje …</p>
  if (!vrstice || vrstice.length === 0) return <p className="obvestilo">Še ni igralcev.</p>

  return (
    <table className="tabela lestvica domov__lestvica lestvica--razvrstitev">
      <tbody>
        {vrstice.slice(0, 15).map((v, indeks) => (
          <tr key={v.idIgralca}>
            <td className="lestvica__mesto">{indeks + 1}</td>
            <td>
              <strong>{v.polnoIme}</strong>
              {v.klub && <span className="lestvica__klub"> · {v.klub}</span>}
            </td>
            <td className="lestvica__stevilka lestvica__rating">{v.rating ?? '—'}</td>
          </tr>
        ))}
      </tbody>
    </table>
  )
}

function TurnirjiPovzetek({
  turnirji,
  nalaganje,
}: {
  turnirji: TurnirDto[] | undefined
  nalaganje: boolean
}) {
  if (nalaganje) return <p className="obvestilo">Nalaganje …</p>
  if (!turnirji || turnirji.length === 0) return <p className="obvestilo">Ni še turnirjev.</p>

  return (
    <ul className="domov__seznam">
      {turnirji.slice(0, 5).map((t) => (
        <li key={t.id}>
          <Link to={`/turnirji/${t.id}`} className="domov__postavka">
            <span className="domov__postavka-ime">{t.ime}</span>
            <span className="domov__postavka-desno">
              <span className="domov__postavka-datum">
                {oblikujObdobje(t.datumZacetka, t.datumKonca) || '—'}
              </span>
              <ZnackaStatusa status={t.status} />
            </span>
          </Link>
        </li>
      ))}
    </ul>
  )
}

function LigePovzetek({
  lige,
  nalaganje,
}: {
  lige: LigaDto[] | undefined
  nalaganje: boolean
}) {
  if (nalaganje) return <p className="obvestilo">Nalaganje …</p>
  if (!lige || lige.length === 0) return <p className="obvestilo">Ni še lig.</p>

  return (
    <ul className="domov__seznam">
      {lige.slice(0, 5).map((l) => (
        <li key={l.id}>
          <Link to={`/lige/${l.id}`} className="domov__postavka">
            <span className="domov__postavka-ime">{l.ime}</span>
            <span className="domov__postavka-desno">
              <span className="domov__postavka-datum">
                {l.steviloEkip} {ekipTekst(l.steviloEkip)}
              </span>
              <ZnackaStatusa status={l.status} />
            </span>
          </Link>
        </li>
      ))}
    </ul>
  )
}

/* Slovnično pravilna oblika besede "ekipa" glede na število. */
function ekipTekst(n: number): string {
  if (n === 1) return 'ekipa'
  if (n === 2) return 'ekipi'
  if (n === 3 || n === 4) return 'ekipe'
  return 'ekip'
}
