/* Profil igralca s statistiko.

   Javni del (pregled, uvrstitev, graf ELO, seznam tekem) vidi vsak. Zasebne
   analize (nasprotniki, nizi in točke, forma, konteksti) se naložijo posebej
   in samo takrat, ko je profil last prijavljenega igralca ali ko gleda
   administrator — strežnik na ta klic sicer odgovori s 403. */
import { Link, useParams } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'

import { profiliApi } from '../api/zahteve'
import type { Delez, ProfilNasprotnik, TekmaProfila } from '../api/tipi'
import { OZNAKE_IZID } from '../api/tipi'
import { useAvtentikacija } from '../avtentikacija/AvtentikacijaKontekst'
import { GrafElo } from '../komponente/GrafElo'
import { SporociloNapake } from '../komponente/SporociloNapake'
import { SpremembaElo } from '../komponente/SpremembaElo'

export function ProfilStran() {
  const { id } = useParams()
  const idIgralec = Number(id)
  const { jeAdmin, mojIdIgralec } = useAvtentikacija()

  const profil = useQuery({
    queryKey: ['profil', idIgralec],
    queryFn: () => profiliApi.profil(idIgralec),
  })

  /* Zasebni del zahtevamo samo, kadar imamo pravico — da uporabnik ne dobi
     nepotrebne napake 403 v konzoli. */
  const smemZasebno = jeAdmin || mojIdIgralec === idIgralec
  const zasebno = useQuery({
    queryKey: ['profil-zasebno', idIgralec],
    queryFn: () => profiliApi.zasebno(idIgralec),
    enabled: smemZasebno && !Number.isNaN(idIgralec),
  })

  if (profil.isPending) return <p className="obvestilo">Nalaganje …</p>
  if (profil.error || !profil.data) return <SporociloNapake napaka={profil.error} />

  const p = profil.data
  const jeMoj = mojIdIgralec === idIgralec

  return (
    <section className="profil">
      <div className="naslovna-vrstica">
        <div>
          <Link to="/lestvica" className="nazaj">← Lestvica</Link>
          <h1>{p.glava.polnoIme}</h1>
          <p className="podnaslov">
            {p.glava.klub ?? 'brez kluba'}
            {p.glava.igralnaRoka && ` · ${p.glava.igralnaRoka === 'LEVA' ? 'levičar' : 'desničar'}`}
          </p>
        </div>
        {jeMoj && <span className="znacka znacka--uspeh">Moj profil</span>}
      </div>

      <div className="profil__kazalniki">
        <Kazalnik oznaka="Rating" vrednost={p.glava.rating ?? '—'} poudarjen />
        <Kazalnik oznaka="Odigrane" vrednost={p.pregled.odigrane} />
        <Kazalnik oznaka="Zmage" vrednost={p.pregled.zmage} />
        <Kazalnik oznaka="Porazi" vrednost={p.pregled.porazi} />
        <Kazalnik oznaka="Uspešnost" vrednost={`${p.pregled.odstotekZmag} %`} />
        <Kazalnik
          oznaka="Mesto"
          vrednost={p.uvrstitev.mesto ? `${p.uvrstitev.mesto}. / ${p.uvrstitev.skupajIgralcev}` : '—'}
        />
      </div>

      {(p.uvrstitev.percentil !== null || p.uvrstitev.klubskoPovprecje !== null) && (
        <p className="profil__primerjava">
          {p.uvrstitev.percentil !== null && (
            <>Boljši od <strong>{p.uvrstitev.percentil} %</strong> igralcev z ratingom. </>
          )}
          {p.uvrstitev.klubskoPovprecje !== null && p.glava.rating !== null && (
            <>
              Povprečje kluba je <strong>{p.uvrstitev.klubskoPovprecje}</strong> (
              {p.glava.rating >= p.uvrstitev.klubskoPovprecje ? '+' : ''}
              {p.glava.rating - p.uvrstitev.klubskoPovprecje} zate).
            </>
          )}
        </p>
      )}

      <div className="plosca">
        <h2>Napredek ELO</h2>
        <GrafElo tocke={p.graf} />
      </div>

      {smemZasebno && zasebno.data && <ZasebniDel podatki={zasebno.data} />}
      {smemZasebno && zasebno.error && <SporociloNapake napaka={zasebno.error} />}
      {!smemZasebno && (
        <p className="obvestilo">
          Poglobljene analize (nasprotniki, forma, nizi in točke) vidi samo igralec sam.
          Če je to tvoj profil, se prijavi.
        </p>
      )}

      <div className="plosca">
        <h2>Odigrane tekme <span className="plosca__stevec">{p.tekme.length}</span></h2>
        {p.tekme.length === 0 ? (
          <p className="obvestilo">Ta igralec še ni odigral nobene tekme.</p>
        ) : (
          <SeznamTekem tekme={p.tekme} />
        )}
      </div>
    </section>
  )
}

function Kazalnik({
  oznaka,
  vrednost,
  poudarjen = false,
}: {
  oznaka: string
  vrednost: string | number
  poudarjen?: boolean
}) {
  return (
    <div className={'kazalnik' + (poudarjen ? ' kazalnik--poudarjen' : '')}>
      <div className="kazalnik__vrednost">{vrednost}</div>
      <div className="kazalnik__oznaka">{oznaka}</div>
    </div>
  )
}

function SeznamTekem({ tekme }: { tekme: TekmaProfila[] }) {
  return (
    <div className="tabela-ovoj">
      <table className="tabela">
        <thead>
          <tr>
            <th>Datum</th>
            <th>Tekmovanje</th>
            <th>Nasprotnik</th>
            <th className="lestvica__stevilka">Rezultat</th>
            <th className="lestvica__stevilka">ELO</th>
          </tr>
        </thead>
        <tbody>
          {tekme.map((t) => (
            <tr key={(t.ligaska ? 'l' : 't') + t.idTekme}>
              <td className="profil__datum">{t.datum ? datum(t.datum) : '—'}</td>
              <td>
                {t.tekmovanje}
                {t.ligaska && <span className="enanaena__vir">liga</span>}
                <div className="profil__del">{t.del}</div>
              </td>
              <td>
                <Link to={`/igralci/${t.idNasprotnika}/profil`}>{t.nasprotnik}</Link>
                {t.klubNasprotnika && <div className="profil__del">{t.klubNasprotnika}</div>}
              </td>
              <td className="lestvica__stevilka">
                <span className={t.zmaga ? 'profil__zmaga' : 'profil__poraz'}>
                  {t.niziZa}:{t.niziProti}
                </span>
                {t.izidTip && t.izidTip !== 'IGRANO' && (
                  <span className="enanaena__posebni"> ({OZNAKE_IZID[t.izidTip]})</span>
                )}
              </td>
              <td className="lestvica__stevilka">
                <SpremembaElo vrednost={t.spremembaElo} />
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}

function ZasebniDel({ podatki }: { podatki: import('../api/tipi').ProfilZasebnoDto }) {
  const { nasprotniki, niziInTocke, forma, poTekmovanjih } = podatki
  return (
    <>
      <div className="plosca">
        <h2>Forma <span className="plosca__zasebno">samo zate</span></h2>
        <div className="profil__forma">
          <div className="forma__trak">
            {forma.zadnjih10.length === 0 && <span className="obvestilo">Ni še tekem.</span>}
            {forma.zadnjih10.map((zmaga, i) => (
              <span key={i} className={'forma__znak ' + (zmaga ? 'forma__znak--z' : 'forma__znak--p')}>
                {zmaga ? 'Z' : 'P'}
              </span>
            ))}
          </div>
          <div className="profil__kazalniki">
            <Kazalnik
              oznaka={forma.trenutniNizZmag ? 'Niz zmag' : 'Niz porazov'}
              vrednost={forma.trenutniNiz}
            />
            <Kazalnik oznaka="Najdaljši niz zmag" vrednost={forma.najdaljsiNizZmag} />
            <Kazalnik
              oznaka="ELO (30 dni)"
              vrednost={
                forma.spremembaElo30dni === null
                  ? '—'
                  : (forma.spremembaElo30dni >= 0 ? '+' : '') + forma.spremembaElo30dni
              }
            />
            <Kazalnik
              oznaka="Najvišji ELO"
              vrednost={forma.najvisjiElo ?? '—'}
            />
          </div>
          {forma.najvisjiEloDatum && (
            <p className="profil__opomba">Najvišji ELO dosežen {datum(forma.najvisjiEloDatum)}.</p>
          )}
        </div>
      </div>

      <div className="plosca">
        <h2>Nasprotniki <span className="plosca__zasebno">samo zate</span></h2>
        <h3 className="profil__podnaslov">Po igralni roki</h3>
        <DelezVrstice
          delezi={[nasprotniki.protiDesnicarjem, nasprotniki.protiLevicarjem, nasprotniki.rokaNeznana]}
        />

        <h3 className="profil__podnaslov">Po moči nasprotnika</h3>
        <p className="profil__opomba">
          Upoštevan je rating nasprotnika v trenutku tekme; takih tekem je{' '}
          {nasprotniki.tekemZZnanimRatingom}.
        </p>
        <DelezVrstice
          delezi={[
            nasprotniki.protiMocnejsim,
            nasprotniki.protiPodobnim,
            nasprotniki.protiSibkejsim,
          ]}
        />

        <div className="profil__izpostavljeni">
          <Izpostavljen naslov="Najboljša zmaga" nasprotnik={nasprotniki.najboljsaZmaga} kazeRating />
          <Izpostavljen naslov="Nemesis" nasprotnik={nasprotniki.nemesis} />
          <Izpostavljen naslov="Najpogostejši nasprotnik" nasprotnik={nasprotniki.najpogostejsi} />
        </div>

        {nasprotniki.poKlubih.length > 0 && (
          <>
            <h3 className="profil__podnaslov">Po klubih nasprotnika</h3>
            <DelezVrstice delezi={nasprotniki.poKlubih} />
          </>
        )}
      </div>

      <div className="plosca">
        <h2>Nizi in točke <span className="plosca__zasebno">samo zate</span></h2>
        <div className="profil__kazalniki">
          <Kazalnik oznaka="Dobljeni nizi" vrednost={niziInTocke.dobljeniNizi} />
          <Kazalnik oznaka="Prejeti nizi" vrednost={niziInTocke.prejetiNizi} />
          <Kazalnik
            oznaka="Odločilni niz"
            vrednost={`${niziInTocke.odlocilniNiz.zmage}:${niziInTocke.odlocilniNiz.porazi}`}
          />
          <Kazalnik oznaka="Uspešnost v odl. nizu" vrednost={`${niziInTocke.odlocilniNiz.odstotek} %`} />
        </div>

        {niziInTocke.razmerja.length > 0 && (
          <>
            <h3 className="profil__podnaslov">Končni izidi</h3>
            <ul className="profil__razmerja">
              {niziInTocke.razmerja.map((r) => (
                <li key={r.oznaka + r.zmaga} className={r.zmaga ? 'profil__zmaga' : 'profil__poraz'}>
                  <strong>{r.oznaka}</strong> ×{r.stevilo}
                </li>
              ))}
            </ul>
          </>
        )}

        <h3 className="profil__podnaslov">Točke</h3>
        {niziInTocke.tocke.steviloTekem === 0 ? (
          <p className="obvestilo">
            Točke po nizih so shranjene samo za turnirske tekme in za zdaj ni nobene take tekme.
          </p>
        ) : (
          <>
            <div className="profil__kazalniki">
              <Kazalnik oznaka="Osvojene točke" vrednost={niziInTocke.tocke.tockeZa} />
              <Kazalnik oznaka="Prejete točke" vrednost={niziInTocke.tocke.tockeProti} />
              <Kazalnik oznaka="Delež točk" vrednost={`${niziInTocke.tocke.odstotekTock} %`} />
              <Kazalnik oznaka="Povprečje na niz" vrednost={niziInTocke.tocke.povprecjeNaNiz} />
            </div>
            <p className="profil__opomba">
              Izračunano iz {niziInTocke.tocke.steviloTekem} turnirskih tekem z vpisanimi točkami
              (ligaška srečanja hranijo samo nize).
            </p>
          </>
        )}
      </div>

      <div className="plosca">
        <h2>Po tekmovanjih <span className="plosca__zasebno">samo zate</span></h2>
        <DelezVrstice delezi={[poTekmovanjih.turnirji, poTekmovanjih.lige]} />

        {(poTekmovanjih.doma.odigrane > 0 || poTekmovanjih.vGosteh.odigrane > 0) && (
          <>
            <h3 className="profil__podnaslov">Liga: doma in v gosteh</h3>
            <DelezVrstice delezi={[poTekmovanjih.doma, poTekmovanjih.vGosteh]} />
          </>
        )}
        {poTekmovanjih.poPoziciji.length > 0 && (
          <>
            <h3 className="profil__podnaslov">Po poziciji v postavi</h3>
            <DelezVrstice delezi={poTekmovanjih.poPoziciji} />
          </>
        )}
        {poTekmovanjih.poFazi.length > 0 && (
          <>
            <h3 className="profil__podnaslov">Po fazi turnirja</h3>
            <DelezVrstice delezi={poTekmovanjih.poFazi} />
          </>
        )}
        {poTekmovanjih.dvojice.odigrane > 0 && (
          <>
            <h3 className="profil__podnaslov">Ligaške dvojice</h3>
            <p className="profil__opomba">Dvojice ne štejejo v ELO ne med posamične zmage.</p>
            <DelezVrstice delezi={[poTekmovanjih.dvojice]} />
          </>
        )}
      </div>
    </>
  )
}

/* Vodoravni stolpci deležev; prazne skupine izpustimo, da ne motijo.
   Manjkajoč vnos (neujemanje tipa z DTO-jem zaledja) preskočimo, da napaka
   ne podre celotne strani. */
function DelezVrstice({ delezi }: { delezi: (Delez | undefined)[] }) {
  const vidni = delezi.filter((d): d is Delez => d != null && d.odigrane > 0)
  if (vidni.length === 0) return <p className="obvestilo">Ni podatkov za ta razrez.</p>
  return (
    <ul className="delezi">
      {vidni.map((d) => (
        <li key={d.oznaka} className="delez">
          <span className="delez__oznaka">{d.oznaka}</span>
          <span className="delez__stolpec">
            <span className="delez__polnilo" style={{ width: `${d.odstotek}%` }} />
          </span>
          <span className="delez__vrednost">
            {d.zmage}–{d.porazi} <strong>{d.odstotek} %</strong>
          </span>
        </li>
      ))}
    </ul>
  )
}

function Izpostavljen({
  naslov,
  nasprotnik,
  kazeRating = false,
}: {
  naslov: string
  nasprotnik: ProfilNasprotnik | null
  kazeRating?: boolean
}) {
  if (!nasprotnik) return null
  return (
    <div className="izpostavljen">
      <div className="izpostavljen__naslov">{naslov}</div>
      <Link to={`/igralci/${nasprotnik.idIgralec}/profil`} className="izpostavljen__ime">
        {nasprotnik.polnoIme}
      </Link>
      <div className="izpostavljen__opis">
        {nasprotnik.klub ?? 'brez kluba'} · {nasprotnik.zmage}–{nasprotnik.porazi}
        {kazeRating && nasprotnik.rating !== null && ` · rating ${nasprotnik.rating}`}
      </div>
    </div>
  )
}

function datum(iso: string): string {
  const d = new Date(iso)
  return Number.isNaN(d.getTime()) ? '—' : d.toLocaleDateString('sl-SI')
}
