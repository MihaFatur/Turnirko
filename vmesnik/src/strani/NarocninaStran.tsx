/* Pregled in nadgradnja placilnega paketa (Premium za igralca, Basic/Plus/
   Pro za organizatorja). Nadgradnja gre prek Stripe Checkouta - stran samo
   zacne placilo in preusmeri brskalnik tja; racun in nova narocnina
   nastaneta sele prek webhooka (glej PlacilaStoritev). Upravljanje/preklic
   obstojece narocnine gre prek Stripe Billing Portala. */
import { useState } from 'react'
import { useMutation } from '@tanstack/react-query'

import { placilaApi } from '../api/zahteve'
import { opisNapake } from '../api/odjemalec'
import {
  CENA_ORGANIZATOR_LETNO,
  CENA_PREMIUM_MESECNO,
  MESECEV_V_LETNI_NAROCNINI,
  OMEJITVE_ORGANIZATORJA,
  OZNAKE_PAKET,
  type CiklusPlacila,
  type Paket,
} from '../api/tipi'
import { useAvtentikacija } from '../avtentikacija/AvtentikacijaKontekst'
import { oblikujCeno } from '../pomozno/oblikovanje'

const ORGANIZATORSKI_PAKETI: Extract<Paket, `ORGANIZATOR_${string}`>[] = [
  'ORGANIZATOR_BASIC',
  'ORGANIZATOR_PLUS',
  'ORGANIZATOR_PRO',
]

export function NarocninaStran() {
  const { uporabnik, nalaganje } = useAvtentikacija()
  const [ciklusPremium, nastaviCiklusPremium] = useState<CiklusPlacila>('MESECNO')
  const [napaka, nastaviNapako] = useState<string | null>(null)

  const zacniPlacilo = useMutation({
    mutationFn: (vnos: { paket: Paket; ciklus: CiklusPlacila | null }) =>
      placilaApi.nadgradnja(vnos),
    onSuccess: (seja) => {
      window.location.href = seja.url
    },
    onError: (e) => nastaviNapako(opisNapake(e)),
  })

  const odpriPortal = useMutation({
    mutationFn: () => placilaApi.portal(),
    onSuccess: (seja) => {
      window.location.href = seja.url
    },
    onError: (e) => nastaviNapako(opisNapake(e)),
  })

  if (nalaganje) return <p className="obvestilo">Preverjanje prijave …</p>
  if (!uporabnik) {
    return <p className="obvestilo">Za pregled naročnine se najprej prijavi.</p>
  }
  if (uporabnik.vloga === 'ADMIN') {
    return <p className="obvestilo">Administrator nima placilnega paketa.</p>
  }

  const jeIgralec = uporabnik.vloga === 'IGRALEC'
  const cenaMesecno = uporabnik.starejsiOd21
    ? CENA_PREMIUM_MESECNO.starejsi
    : CENA_PREMIUM_MESECNO.mlajsi
  const cenaLetno = Math.round(cenaMesecno * MESECEV_V_LETNI_NAROCNINI * 100) / 100

  const jePremiumAktiven = jeIgralec && uporabnik.paket === 'PREMIUM' && uporabnik.paketAktiven

  return (
    <section>
      <div className="stran-glava stran-glava--ozka">
        <div>
          <h1 className="naslov-strani">
            <span className="naslov-strani__nad">Račun</span>
            <span className="naslov-strani__glavni">Naročnina</span>
          </h1>
          <p className="uvod">
            Trenutni paket: <strong>{OZNAKE_PAKET[uporabnik.paket ?? 'BREZPLACNO']}</strong>
            {uporabnik.paket && !uporabnik.paketAktiven && ' (potekla)'}
          </p>
        </div>
      </div>

      {napaka && <div className="napaka">{napaka}</div>}

      {jeIgralec && (
        <div className="plosca">
          <div className="naslovna-vrstica">
            <h2>Premium</h2>
          </div>
          <p className="namig">
            Zasebna statistika profila (forma, nasprotniki, napoved tekme, nizi in točke,
            razrezi) in spremljanje lig. Brez Premium je račun kot gost, le prijavljen.
          </p>

          {jePremiumAktiven ? (
            <button
              type="button"
              className="gumb gumb--glavni"
              disabled={odpriPortal.isPending}
              onClick={() => odpriPortal.mutate()}
            >
              {odpriPortal.isPending ? 'Odpiram …' : 'Upravljaj naročnino'}
            </button>
          ) : (
            <>
              <label className="obrazec__polje">
                <span>Plačevanje</span>
                <select
                  value={ciklusPremium}
                  onChange={(d) => nastaviCiklusPremium(d.target.value as CiklusPlacila)}
                >
                  <option value="MESECNO">{oblikujCeno(cenaMesecno)} / mesec</option>
                  <option value="LETNO">
                    {oblikujCeno(cenaLetno)} / leto ({MESECEV_V_LETNI_NAROCNINI}× mesečna cena)
                  </option>
                </select>
              </label>
              <button
                type="button"
                className="gumb gumb--glavni"
                disabled={zacniPlacilo.isPending}
                onClick={() =>
                  zacniPlacilo.mutate({ paket: 'PREMIUM', ciklus: ciklusPremium })
                }
              >
                {zacniPlacilo.isPending ? 'Preusmerjam na plačilo …' : 'Nadgradi na Premium'}
              </button>
            </>
          )}
        </div>
      )}

      {uporabnik.vloga === 'ORGANIZATOR' && (
        <div className="plosca">
          <div className="naslovna-vrstica">
            <h2>Organizatorski paket</h2>
          </div>
          <p className="namig">
            Obseg (koliko lig in turnirjev smeš ustvariti na sezono) je edina razlika med
            paketi - vsi so letni.
          </p>

          <ul className="seznam-preprost">
            {ORGANIZATORSKI_PAKETI.map((paket) => {
              const omejitev = OMEJITVE_ORGANIZATORJA[paket]
              const trenutni = uporabnik.paket === paket && uporabnik.paketAktiven
              return (
                <li key={paket}>
                  <strong>{OZNAKE_PAKET[paket]}</strong> — {oblikujCeno(CENA_ORGANIZATOR_LETNO[paket])}
                  /leto ({omejitev.lig} {omejitev.lig === 1 ? 'tekoča liga' : 'tekoče lige'},{' '}
                  {omejitev.turnirjev} turnirjev na sezono){' '}
                  {trenutni ? (
                    '— trenutni paket'
                  ) : (
                    <button
                      type="button"
                      className="gumb"
                      disabled={zacniPlacilo.isPending}
                      onClick={() => zacniPlacilo.mutate({ paket, ciklus: 'LETNO' })}
                    >
                      Izberi
                    </button>
                  )}
                </li>
              )
            })}
          </ul>

          {uporabnik.paket?.startsWith('ORGANIZATOR_') && uporabnik.paketAktiven && (
            <button
              type="button"
              className="gumb"
              disabled={odpriPortal.isPending}
              onClick={() => odpriPortal.mutate()}
            >
              {odpriPortal.isPending ? 'Odpiram …' : 'Upravljaj naročnino'}
            </button>
          )}
        </div>
      )}
    </section>
  )
}
