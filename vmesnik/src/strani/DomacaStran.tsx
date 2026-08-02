/* Domača (začetna) stran - pregled v dveh stolpcih: levo turnirji, lige in
   strnjen pripomoček "1 na 1", desno pa daljša lestvica igralcev. V glavi
   strani stoji kolofon z utripom sezone (koliko tekmovanj teče). Vse je bralno
   in vidno tudi gostom; poglobljeni pogledi so dosegljivi prek povezav. */
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

  /* Utrip sezone: štejemo iz podatkov, ki jih stran že naloži - brez dodatnega
     klica na strežnik. Vrstica se izpiše šele, ko je vrednost znana. */
  const utrip: { oznaka: string; vrednost: number | null }[] = [
    {
      oznaka: 'Turnirji v teku',
      vrednost: turnirji.data ? turnirji.data.filter((t) => t.status === 'V_TEKU').length : null,
    },
    {
      oznaka: 'Lige v teku',
      vrednost: lige.data ? lige.data.filter((l) => l.status === 'V_TEKU').length : null,
    },
    {
      oznaka: 'Igralci z ratingom',
      vrednost: lestvica.data ? lestvica.data.filter((v) => v.rating !== null).length : null,
    },
    {
      oznaka: 'Odigrane tekme',
      vrednost: lestvica.data
        ? /* Vsaka tekma nastopa pri obeh igralcih, zato polovica vsote. */
          Math.round(lestvica.data.reduce((vsota, v) => vsota + v.odigrane, 0) / 2)
        : null,
    },
  ]

  return (
    <section className="domov">
      <div className="stran-glava">
        <div>
          <h1 className="naslov-strani">
            <span className="naslov-strani__nad">Namizni tenis</span>
            <span className="naslov-strani__glavni">Pregled</span>
          </h1>
          <p className="uvod">
            Turnirji, žive lestvice in medsebojni izidi na enem mestu.
          </p>
        </div>
        <div className="kolofon">
          {utrip.map((u) => (
            <div className="kolofon__vrstica" key={u.oznaka}>
              <span className="kolofon__oznaka">{u.oznaka}</span>
              <span className="kolofon__vrednost">{u.vrednost ?? '—'}</span>
            </div>
          ))}
        </div>
      </div>

      <div className="domov__mreza">
        <div className="domov__stolpec">
          <Sekcija naslov="Turnirji" povezava={{ pot: '/turnirji', oznaka: 'Vsi turnirji' }}>
            <TurnirjiPovzetek turnirji={turnirji.data} nalaganje={turnirji.isPending} />
          </Sekcija>

          <Sekcija naslov="Lige" povezava={{ pot: '/lige', oznaka: 'Vse lige' }}>
            <LigePovzetek lige={lige.data} nalaganje={lige.isPending} />
          </Sekcija>

          <Sekcija naslov="Ena na ena" povezava={{ pot: '/dvoboj', oznaka: 'Cel dvoboj' }}>
            <EnaNaEna />
          </Sekcija>
        </div>

        <Sekcija
          naslov="Lestvica"
          manjsi
          povezava={{ pot: '/lestvica', oznaka: 'Cela' }}
        >
          <MiniLestvica vrstice={lestvica.data} nalaganje={lestvica.isPending} />
        </Sekcija>
      </div>
    </section>
  )
}

/* Sekcija strani: 3 px črta, naslov 40/800 in mono povezava desno. Desni
   (ožji) stolpec dobi manjši naslov, da se ne tepe s širino. */
function Sekcija({
  naslov,
  povezava,
  manjsi,
  children,
}: {
  naslov: string
  povezava?: { pot: string; oznaka: string }
  manjsi?: boolean
  children: ReactNode
}) {
  return (
    <div>
      <div className="naslovna-vrstica">
        <h2 className={manjsi ? 'sekcija__naslov--manjsi' : undefined}>{naslov}</h2>
        {povezava && (
          <Link to={povezava.pot} className="sekcija__meta">
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
    <div className="domov__lestvica">
      {vrstice.slice(0, 15).map((v, indeks) => (
        <Link
          to={`/igralci/${v.idIgralca}/profil`}
          className="domov__lestvica-vrstica"
          key={v.idIgralca}
        >
          {/* Prva tri mesta so modra - edini poudarek v stolpcu mest. */}
          <span
            className={
              'domov__lestvica-mesto' + (indeks < 3 ? ' domov__lestvica-mesto--vrh' : '')
            }
          >
            {indeks + 1}.
          </span>
          <span>
            <span className="domov__lestvica-ime">{v.polnoIme}</span>
            <span className="domov__lestvica-klub">{v.klub ?? 'brez kluba'}</span>
          </span>
          <span className="domov__lestvica-rating">{v.rating ?? '—'}</span>
        </Link>
      ))}
    </div>
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
    <div className="kartice">
      {turnirji.slice(0, 5).map((t) => (
        <Link to={`/turnirji/${t.id}`} className="domov__postavka" key={t.id}>
          <span className="domov__postavka-ime">
            {t.ime}
            <span className="domov__postavka-kraj">
              {t.kraj?.ime ?? 'kraj še ni določen'}
            </span>
          </span>
          <span className="domov__postavka-datum">
            {oblikujObdobje(t.datumZacetka, t.datumKonca) || '—'}
          </span>
          <ZnackaStatusa status={t.status} />
        </Link>
      ))}
    </div>
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
    <div className="kartice">
      {lige.slice(0, 5).map((l) => (
        <Link to={`/lige/${l.id}`} className="domov__postavka" key={l.id}>
          <span className="domov__postavka-ime">{l.ime}</span>
          <span className="domov__postavka-datum">
            {l.steviloEkip} {ekipTekst(l.steviloEkip)}
          </span>
          <ZnackaStatusa status={l.status} />
        </Link>
      ))}
    </div>
  )
}

/* Slovnično pravilna oblika besede "ekipa" glede na število. */
function ekipTekst(n: number): string {
  if (n === 1) return 'ekipa'
  if (n === 2) return 'ekipi'
  if (n === 3 || n === 4) return 'ekipe'
  return 'ekip'
}
