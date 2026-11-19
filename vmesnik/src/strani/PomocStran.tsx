/* Stran Pomoč (/pomoc in /pomoc/:clanek#razdelek): navodila za organizatorje.

   Zgradba po odločitvi lastnika: zgoraj vodiči po opravilih (od začetka do
   konca), spodaj priročnik po funkcijah, na katerega kažejo gumbi »?«; čez
   vse iskalnik. Na namizju stoji kazalo levo in ostane na mestu, na telefonu
   je kazalo vstopna stran in članek svoja podstran (puščica nazaj vodi vanj).

   Vidita jo organizator in admin (pot je v App za SamoUrejevalec); poglavje
   za admina samo admin. */
import { useEffect, useMemo, useRef, useState, type ReactNode } from 'react'
import { Link, useLocation, useNavigate, useParams } from 'react-router-dom'

import { useNazaj } from '../komponente/GlavaTelefona'
import { Besedilo, potDoClanka, SkokPomociKontekst } from '../pomoc/Besedilo'
import { isciPomoc } from '../pomoc/iskanje'
import { IkonaPomoci } from '../pomoc/Namig'
import { usePomoc } from '../pomoc/PomocKontekst'
import { idRazdelka, VsebinaClanka } from '../pomoc/VsebinaClanka'
import type { Clanek } from '../pomoc/tipi'
import {
  CLANKI,
  clankiPoglavja,
  najdiClanek,
  POGLAVJA,
  razdelkiClanka,
  smeVideti,
  sosedaClanka,
} from '../pomoc/vsebina'
import { useNaslovStrani } from '../pomozno/naslovStrani'
import { useTelefon } from '../pomozno/telefon'

const PREDPONA = 'stran'

export function PomocStran() {
  const { clanek: idClanka } = useParams()
  const { jeAdmin } = usePomoc()
  const jeTelefon = useTelefon()
  const [iskanje, nastaviIskanje] = useState('')

  const clanek = idClanka ? najdiClanek(idClanka) : undefined
  const dovoljen = clanek && smeVideti(clanek, jeAdmin) ? clanek : undefined

  useNaslovStrani(dovoljen ? `${dovoljen.naslov} · Pomoč` : 'Pomoč')
  useNazaj(idClanka ? '/pomoc' : '/', idClanka ? 'Pomoč' : 'Domov')

  /* Iskanje velja za kazalo; ob odprtju članka se izprazni, da kazalo pokaže
     poglavje, v katerem bralec je. */
  useEffect(() => {
    nastaviIskanje('')
  }, [idClanka])

  /* Izbrani članek v kazalu na namizju pomakne v vidni del (kazalo je daljše
     od zaslona, članki admina in liga so na dnu). Odmik se računa v kazalu,
     da se stran ob skoku na razdelek ne premakne. */
  const stranskoKazalo = useRef<HTMLElement>(null)
  useEffect(() => {
    const ovoj = stranskoKazalo.current
    const izbran = ovoj?.querySelector<HTMLElement>('.pomoc-kazalo__clanek--izbran')
    if (!ovoj || !izbran) return
    const o = ovoj.getBoundingClientRect()
    const i = izbran.getBoundingClientRect()
    if (i.top < o.top || i.bottom > o.bottom) ovoj.scrollTop += i.top - o.top - ovoj.clientHeight / 3
  }, [dovoljen?.id])

  const kazalo = <Kazalo iskanje={iskanje} nastaviIskanje={nastaviIskanje} izbran={dovoljen?.id} />

  if (jeTelefon) {
    return (
      <section className="pomoc-stran pomoc-stran--telefon">
        {idClanka ? <Clanek clanek={dovoljen} /> : <Vstop kazalo={kazalo} iskanjeAktivno={!!iskanje.trim()} />}
      </section>
    )
  }

  return (
    <section className="pomoc-stran">
      <aside ref={stranskoKazalo} className="pomoc-stran__kazalo">
        {kazalo}
      </aside>
      <div className="pomoc-stran__vsebina">
        {idClanka ? <Clanek clanek={dovoljen} /> : <Vstop />}
      </div>
    </section>
  )
}

/* Vstopna stran: naslov, kratko kako, vodiči in poglavja. */
function Vstop({ kazalo, iskanjeAktivno }: { kazalo?: ReactNode; iskanjeAktivno?: boolean }) {
  const { jeAdmin, namigiVklopljeni, nastaviNamige } = usePomoc()
  const vodici = clankiPoglavja('vodici', jeAdmin)
  return (
    <>
      <header className="pomoc-glava">
        <h1 className="naslov-strani">
          <span className="naslov-strani__nad">Pomoč</span>
          <span className="naslov-strani__glavni pomoc-glava__naslov">za organizatorje</span>
        </h1>
        <p className="uvod">
          Kako se v Turnirku izpelje turnir in vodi liga — po korakih, s prikazi. Ob funkcijah v
          aplikaciji stoji gumb{' '}
          <span className="pomoc-glava__ikona">
            <IkonaPomoci velikost={16} />
          </span>
          , ki odpre kratko razlago in vodi sem.
        </p>
        <label className="pomoc-stikalo">
          <input
            type="checkbox"
            checked={namigiVklopljeni}
            onChange={(d) => nastaviNamige(d.target.checked)}
          />
          <span>Pokaži gumbe »?« ob funkcijah</span>
        </label>
      </header>

      {kazalo}

      {!iskanjeAktivno && (
        <>
          <section className="pomoc-sklop">
            <div className="naslovna-vrstica">
              <h2>Vodiči</h2>
              <span className="sekcija__meta">od začetka do konca</span>
            </div>
            <ol className="pomoc-vodici">
              {vodici.map((v, i) => (
                <li key={v.id}>
                  <Link to={potDoClanka({ clanek: v.id })} className="pomoc-vodici__vrstica">
                    <span className="pomoc-vodici__st">{String(i + 1).padStart(2, '0')}</span>
                    <span className="pomoc-vodici__besedilo">
                      <strong>{v.naslov}</strong>
                      <span>{v.kratko}</span>
                    </span>
                    <span className="pomoc-vodici__puscica" aria-hidden="true">
                      →
                    </span>
                  </Link>
                </li>
              ))}
            </ol>
          </section>

          {POGLAVJA.filter((p) => p.id !== 'vodici' && (!p.samoAdmin || jeAdmin)).map((p) => (
            <section key={p.id} className="pomoc-sklop">
              <div className="naslovna-vrstica">
                <h2>{p.naslov}</h2>
                <span className="sekcija__meta">{p.opis}</span>
              </div>
              <ul className="pomoc-clanki">
                {clankiPoglavja(p.id, jeAdmin).map((c) => (
                  <li key={c.id}>
                    <Link to={potDoClanka({ clanek: c.id })} className="pomoc-clanki__vrstica">
                      <strong>{c.naslov}</strong>
                      <span>{c.kratko}</span>
                    </Link>
                  </li>
                ))}
              </ul>
            </section>
          ))}
        </>
      )}
    </>
  )
}

/* Kazalo z iskalnikom. Na namizju stoji ob članku, na telefonu je vstopna stran. */
function Kazalo({
  iskanje,
  nastaviIskanje,
  izbran,
}: {
  iskanje: string
  nastaviIskanje: (v: string) => void
  izbran?: string
}) {
  const { jeAdmin } = usePomoc()
  const jeTelefon = useTelefon()
  const navigiraj = useNavigate()
  const vidni = useMemo(() => CLANKI.filter((c) => smeVideti(c, jeAdmin)), [jeAdmin])
  const zadetki = useMemo(() => isciPomoc(vidni, iskanje), [vidni, iskanje])
  const [aktiven, nastaviAktiven] = useState(0)
  useEffect(() => nastaviAktiven(0), [iskanje])

  return (
    <nav className="pomoc-kazalo" aria-label="Kazalo pomoči">
      <label className="pomoc-iskalnik">
        <span className="pomoc-iskalnik__oznaka">Išči po navodilih</span>
        <input
          type="search"
          value={iskanje}
          placeholder="npr. žreb skupin, prestavitev, popravek"
          onChange={(d) => nastaviIskanje(d.target.value)}
          onKeyDown={(d) => {
            if (zadetki.length === 0) return
            if (d.key === 'ArrowDown') {
              d.preventDefault()
              nastaviAktiven((a) => Math.min(a + 1, zadetki.length - 1))
            } else if (d.key === 'ArrowUp') {
              d.preventDefault()
              nastaviAktiven((a) => Math.max(a - 1, 0))
            } else if (d.key === 'Enter') {
              d.preventDefault()
              const z = zadetki[aktiven]
              navigiraj(potDoClanka({ clanek: z.clanek.id, razdelek: z.razdelek }))
            }
          }}
        />
      </label>

      {iskanje.trim() ? (
        <div className="pomoc-zadetki" aria-live="polite">
          <p className="sekcija__meta">
            {zadetki.length === 0
              ? 'Ni zadetkov. Poskusi z drugo besedo (npr. »mreža« namesto »bracket«).'
              : `${zadetki.length} ${zadetki.length === 1 ? 'zadetek' : zadetki.length === 2 ? 'zadetka' : zadetki.length < 5 ? 'zadetki' : 'zadetkov'}`}
          </p>
          <ul>
            {zadetki.map((z, i) => {
              const razdelek = z.razdelek
                ? razdelkiClanka(z.clanek).find((r) => r.id === z.razdelek)
                : undefined
              return (
                <li key={z.clanek.id}>
                  <Link
                    to={potDoClanka({ clanek: z.clanek.id, razdelek: z.razdelek })}
                    className={'pomoc-zadetki__vrstica' + (i === aktiven ? ' pomoc-zadetki__vrstica--aktivna' : '')}
                  >
                    <strong>{z.clanek.naslov}</strong>
                    {razdelek && <span className="pomoc-zadetki__razdelek">→ {razdelek.naslov}</span>}
                    <span className="pomoc-zadetki__odlomek">{z.odlomek}</span>
                  </Link>
                </li>
              )
            })}
          </ul>
        </div>
      ) : jeTelefon ? null : (
        POGLAVJA.filter((p) => !p.samoAdmin || jeAdmin).map((p) => (
          <div key={p.id} className="pomoc-kazalo__poglavje">
            <span className="pomoc-kazalo__naslov">{p.naslov}</span>
            <ul>
              {clankiPoglavja(p.id, jeAdmin).map((c) => (
                <li key={c.id}>
                  <Link
                    to={potDoClanka({ clanek: c.id })}
                    className={'pomoc-kazalo__clanek' + (c.id === izbran ? ' pomoc-kazalo__clanek--izbran' : '')}
                    aria-current={c.id === izbran ? 'page' : undefined}
                  >
                    {c.naslov}
                  </Link>
                </li>
              ))}
            </ul>
          </div>
        ))
      )}
    </nav>
  )
}

function Clanek({ clanek }: { clanek?: Clanek }) {
  const { jeAdmin } = usePomoc()
  const { hash } = useLocation()
  const navigiraj = useNavigate()
  const glava = useRef<HTMLHeadingElement>(null)

  /* Skok na razdelek iz naslova (#razdelek), sicer na vrh. Fokus na cilj, da
     bralnik zaslona začne brati tam, kamor je pokazal gumb »?«. */
  useEffect(() => {
    const razdelek = hash.replace(/^#/, '')
    const cilj = razdelek
      ? document.getElementById(idRazdelka(PREDPONA, decodeURIComponent(razdelek)))
      : null
    if (cilj) {
      cilj.scrollIntoView({ block: 'start' })
      cilj.focus({ preventScroll: true })
    } else {
      window.scrollTo(0, 0)
      glava.current?.focus({ preventScroll: true })
    }
  }, [clanek?.id, hash])

  if (!clanek) {
    return (
      <div className="pomoc-clanek">
        <h1 className="pomoc-clanek__naslov">Navodila ni mogoče najti</h1>
        <p className="obvestilo">
          Članek ne obstaja ali ni namenjen tvojemu računu. <Link to="/pomoc">Vsa navodila</Link>
        </p>
      </div>
    )
  }

  const poglavje = POGLAVJA.find((p) => p.id === clanek.poglavje)
  const razdelki = razdelkiClanka(clanek)
  const { prej, naprej } = sosedaClanka(clanek, jeAdmin)
  const povezani = (clanek.povezani ?? [])
    .map((id) => najdiClanek(id))
    .filter((c): c is Clanek => !!c && smeVideti(c, jeAdmin))

  return (
    <article className="pomoc-clanek">
      <p className="pomoc-clanek__pot">
        <Link to="/pomoc">Pomoč</Link> <span aria-hidden="true">/</span> {poglavje?.naslov}
      </p>
      <h1 ref={glava} tabIndex={-1} className="pomoc-clanek__naslov">
        {clanek.naslov}
      </h1>
      <p className="uvod pomoc-clanek__uvod">
        <Besedilo besedilo={clanek.kratko} />
      </p>

      {razdelki.length >= 3 && (
        <nav className="pomoc-na-strani" aria-label="Na tej strani">
          <span className="pomoc-na-strani__oznaka">Na tej strani</span>
          <ul>
            {razdelki.map((r) => (
              <li key={r.id}>
                <a
                  href={`#${r.id}`}
                  onClick={(d) => {
                    d.preventDefault()
                    navigiraj(potDoClanka({ clanek: clanek.id, razdelek: r.id }), { replace: true })
                  }}
                >
                  {r.naslov}
                </a>
              </li>
            ))}
          </ul>
        </nav>
      )}

      {/* Na strani povezave vodijo po naslovih (Nazaj brskalnika deluje). */}
      <SkokPomociKontekst.Provider value={null}>
        <VsebinaClanka clanek={clanek} predpona={PREDPONA} />
      </SkokPomociKontekst.Provider>

      {povezani.length > 0 && (
        <div className="pomoc-povezano">
          <h2 className="pomoc-povezano__naslov">Povezano</h2>
          <ul>
            {povezani.map((c) => (
              <li key={c.id}>
                <Link to={potDoClanka({ clanek: c.id })} className="pomoc-povezava">
                  {c.naslov}
                </Link>
              </li>
            ))}
          </ul>
        </div>
      )}

      <div className="pomoc-sosedje">
        {prej ? (
          <Link to={potDoClanka({ clanek: prej.id })} className="pomoc-sosedje__povezava">
            <span className="pomoc-sosedje__smer">← Prejšnji</span>
            {prej.naslov}
          </Link>
        ) : (
          <span />
        )}
        {naprej && (
          <Link to={potDoClanka({ clanek: naprej.id })} className="pomoc-sosedje__povezava pomoc-sosedje__povezava--desno">
            <span className="pomoc-sosedje__smer">Naslednji →</span>
            {naprej.naslov}
          </Link>
        )}
      </div>
    </article>
  )
}
