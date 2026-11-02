/* »Napoved dvoboja« za KATERI KOLI par igralcev (V58, paket Navijač in Igralec
   Premium): koliko Turnirko ratinga bi vsak dobil ali izgubil, če bi se igralca
   zdaj pomerila. Rating in medsebojne tekme sta javna, napoved zato ne razkrije
   ničesar zasebnega — a je ugodnost paketa, ker ni ena od stvari, ki jih gost
   vidi danes.

   Številke izračuna strežnik skozi isti izračun kot pravi obračun tekme
   (NapovedTekmeStoritev); vmesnik ne računa nič. Strežnik odgovor odda samo računu
   s paketom, zato se račun brez njega napovedi niti ne vpraša. Za razliko od
   NapovedTekme na profilu (prvo lice, »Ti«) je tu tretja oseba: gledalec ni nujno
   noben od obeh igralcev. */
import { useState } from 'react'
import { Link } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'

import type { NapovedIzid, RavenTekmovanja } from '../api/tipi'
import { profiliApi } from '../api/zahteve'
import { useAvtentikacija } from '../avtentikacija/AvtentikacijaKontekst'
import { NapakaPoizvedbe } from './NapakaPoizvedbe'

/* Isti vrstni red in imena kot v NapovedTekme: NE_STEJE ni napoved. */
const RAVNI: { raven: RavenTekmovanja; ime: string }[] = [
  { raven: 'URADNO', ime: 'Uradno' },
  { raven: 'KLUBSKO', ime: 'Klubsko' },
  { raven: 'REKREATIVNO', ime: 'Rekreativno' },
]

const predznak = (n: number) => (n > 0 ? `+${n}` : n < 0 ? `−${Math.abs(n)}` : '±0')

export function NapovedPara({ prvi, drugi }: { prvi: number; drugi: number }) {
  const { uporabnik, imaSirokoSpremljanje } = useAvtentikacija()
  const [raven, nastaviRaven] = useState<RavenTekmovanja>('URADNO')

  const napoved = useQuery({
    queryKey: ['napoved-para', prvi, drugi],
    queryFn: () => profiliApi.napoved(prvi, drugi),
    enabled: imaSirokoSpremljanje,
  })

  /* Gostu sklopa ne kažemo (nič se mu ne zaklene, česar je imel); prijavljen račun brez
     paketa vidi, kje se napoved odpre. */
  if (!imaSirokoSpremljanje) {
    if (!uporabnik || uporabnik.vloga === 'ADMIN') return null
    return (
      <div className="napoved-para">
        <div className="naslovna-vrstica">
          <h2>Napoved dvoboja</h2>
        </div>
        <p className="obvestilo">
          Koliko ratinga bi zmaga prinesla vsakemu od obeh: za kateri koli par. Del paketa{' '}
          {uporabnik.vloga === 'NAVIJAC' ? 'Navijač' : 'Navijač in Igralec Premium'}.{' '}
          <Link to="/narocnina">Naročnina →</Link>
        </p>
      </div>
    )
  }

  const n = napoved.data
  const izbrana = n?.ravni.find((r) => r.raven === raven)

  return (
    <div className="napoved-para">
      <div className="naslovna-vrstica">
        <h2>Napoved dvoboja</h2>
      </div>
      {napoved.error && <NapakaPoizvedbe poizvedba={napoved} kaj="napovedi" />}
      {napoved.isLoading && <p className="obvestilo">Računam …</p>}
      {n && izbrana && (
        <>
          <p className="napoved-para__verjetnost">
            <strong>{n.jaz.polnoIme}</strong> zmaga s{' '}
            <strong>{n.pricakovanOdstotek} %</strong> (rating {n.jaz.rating} proti {n.nasprotnik.rating}).
          </p>
          <div className="izbirnik napoved__ravni">
            {RAVNI.map((r) => (
              <button
                key={r.raven}
                type="button"
                className={'izbirnik__gumb' + (raven === r.raven ? ' izbirnik__gumb--aktiven' : '')}
                aria-pressed={raven === r.raven}
                onClick={() => nastaviRaven(r.raven)}
              >
                {r.ime}
              </button>
            ))}
          </div>
          <div className="napoved-para__vrstica napoved-para__vrstica--glava">
            <span>Izid</span>
            <span>{n.jaz.polnoIme}</span>
            <span>{n.nasprotnik.polnoIme}</span>
          </div>
          <Vrstica oznaka={`Zmaga ${n.jaz.polnoIme}`} izid={izbrana.zmaga} />
          <Vrstica oznaka={`Zmaga ${n.nasprotnik.polnoIme}`} izid={izbrana.poraz} />
          <p className="namig">
            Napoved velja, če bi bila tekma zdaj; teža tekmovanja (uradno, klubsko, rekreativno) spremeni,
            koliko točk premakne zmaga. <Link to="/o-ratingu">Kako se računa →</Link>
          </p>
        </>
      )}
    </div>
  )
}

/* Izid z vidika prvega igralca: njegova sprememba in rating, nato nasprotnikova. */
function Vrstica({ oznaka, izid }: { oznaka: string; izid: NapovedIzid }) {
  return (
    <div className="napoved-para__vrstica">
      <span>{oznaka}</span>
      <span>
        <span className={izid.sprememba >= 0 ? 'profil__zmaga' : 'profil__poraz'}>{predznak(izid.sprememba)}</span>
        {' → '}
        {izid.rating}
      </span>
      <span>
        <span className={izid.spremembaNasprotnika >= 0 ? 'profil__zmaga' : 'profil__poraz'}>
          {predznak(izid.spremembaNasprotnika)}
        </span>
        {' → '}
        {izid.ratingNasprotnika}
      </span>
    </div>
  )
}
