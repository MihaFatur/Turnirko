/* Domača (začetna) stran - strnjena nadzorna plošča: pripomoček "1 na 1",
   mini lestvica igralcev in pregled turnirjev. Vse je bralno in vidno tudi
   gostom; poglobljeni pogledi so dosegljivi prek povezav. */
import type { ReactNode } from 'react'
import { Link } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'

import { statistikaApi, turnirjiApi } from '../api/zahteve'
import type { LestvicaIgralcaDto, TurnirDto } from '../api/tipi'
import { EnaNaEna } from '../komponente/EnaNaEna'
import { ZnackaStatusa } from '../komponente/Znacka'
import { oblikujObdobje } from '../pomozno/oblikovanje'

export function DomacaStran() {
  const turnirji = useQuery({ queryKey: ['turnirji'], queryFn: turnirjiApi.seznam })
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

      <Plosca naslov="Ena na ena">
        <EnaNaEna />
      </Plosca>

      <div className="domov__mreza">
        <Plosca naslov="Lestvica igralcev" povezava={{ pot: '/lestvica', oznaka: 'Cela lestvica' }}>
          <MiniLestvica vrstice={lestvica.data} nalaganje={lestvica.isPending} />
        </Plosca>

        <Plosca naslov="Turnirji" povezava={{ pot: '/turnirji', oznaka: 'Vsi turnirji' }}>
          <TurnirjiPovzetek turnirji={turnirji.data} nalaganje={turnirji.isPending} />
        </Plosca>
      </div>
    </section>
  )
}

function Plosca({
  naslov,
  povezava,
  children,
}: {
  naslov: string
  povezava?: { pot: string; oznaka: string }
  children: ReactNode
}) {
  return (
    <div className="plosca domov__plosca">
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
    <table className="tabela lestvica domov__lestvica">
      <tbody>
        {vrstice.slice(0, 10).map((v, indeks) => (
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
      {turnirji.slice(0, 6).map((t) => (
        <li key={t.id}>
          <Link to={`/turnirji/${t.id}`} className="domov__turnir">
            <span className="domov__turnir-ime">{t.ime}</span>
            <span className="domov__turnir-desno">
              <span className="domov__turnir-datum">
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
