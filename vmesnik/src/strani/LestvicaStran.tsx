/* Globalna lestvica igralcev po klubskem ELO ratingu, z razmerjem
   zmag in porazov prek vseh dogodkov. Vidna vsem (tudi gostom). */
import { useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'

import { statistikaApi } from '../api/zahteve'
import { SporociloNapake } from '../komponente/SporociloNapake'

export function LestvicaStran() {
  const lestvica = useQuery({ queryKey: ['lestvica'], queryFn: statistikaApi.lestvica })
  const [iskanje, nastaviIskanje] = useState('')

  const prikazani = useMemo(() => {
    if (!lestvica.data) return []
    const iskano = iskanje.trim().toLowerCase()
    if (!iskano) return lestvica.data
    return lestvica.data.filter((v) =>
      `${v.polnoIme} ${v.klub ?? ''}`.toLowerCase().includes(iskano),
    )
  }, [lestvica.data, iskanje])

  return (
    <section>
      <div className="naslovna-vrstica">
        <div>
          <h1>Lestvica igralcev</h1>
          <p className="podnaslov">Razvrstitev po klubskem ELO ratingu.</p>
        </div>
      </div>

      <input
        className="iskalnik"
        placeholder="Išči po imenu ali klubu …"
        value={iskanje}
        onChange={(d) => nastaviIskanje(d.target.value)}
      />

      <SporociloNapake napaka={lestvica.error} />
      {lestvica.isPending && <p className="obvestilo">Nalaganje …</p>}

      {lestvica.data && lestvica.data.length === 0 && (
        <p className="obvestilo">Še ni igralcev.</p>
      )}

      {prikazani.length > 0 && (
        <table className="tabela lestvica lestvica--razvrstitev">
          <thead>
            <tr>
              <th className="lestvica__mesto">#</th>
              <th>Igralec</th>
              <th>Klub</th>
              <th className="lestvica__stevilka">Rating</th>
              <th className="lestvica__stevilka" title="Odigrane tekme">Od.</th>
              <th className="lestvica__stevilka" title="Zmage">Z</th>
              <th className="lestvica__stevilka" title="Porazi">P</th>
              <th className="lestvica__stevilka" title="Delež zmag">%</th>
            </tr>
          </thead>
          <tbody>
            {prikazani.map((v, indeks) => {
              const odstotek =
                v.odigrane > 0 ? Math.round((v.zmage / v.odigrane) * 100) : null
              return (
                <tr key={v.idIgralca}>
                  <td className="lestvica__mesto">{iskanje ? '·' : indeks + 1}</td>
                  <td>
                    <Link to={`/igralci/${v.idIgralca}/profil`}>
                      <strong>{v.polnoIme}</strong>
                    </Link>
                  </td>
                  <td>{v.klub ?? '—'}</td>
                  <td className="lestvica__stevilka lestvica__rating">{v.rating ?? '—'}</td>
                  <td className="lestvica__stevilka">{v.odigrane}</td>
                  <td className="lestvica__stevilka">{v.zmage}</td>
                  <td className="lestvica__stevilka">{v.porazi}</td>
                  <td className="lestvica__stevilka">{odstotek === null ? '—' : `${odstotek}%`}</td>
                </tr>
              )
            })}
          </tbody>
        </table>
      )}
    </section>
  )
}
