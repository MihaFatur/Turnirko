/* Profil igralca s statistiko.

   Javni del (uvrstitev, ELO blok, kolofon, graf, seznam tekem) vidi vsak.
   Zasebne analize (nasprotniki, nizi in točke, forma, konteksti) se naložijo
   posebej in samo takrat, ko je profil last prijavljenega igralca ali ko gleda
   administrator — strežnik na ta klic sicer odgovori s 403.

   Okolico na lestvici izračunamo iz globalne lestvice, ki jo vmesnik ima že
   predpomnjeno (isti ključ kot LestvicaStran) — brez novega klica na strežnik. */
import { Link, useParams } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'

import { profiliApi, statistikaApi } from '../api/zahteve'
import type {
  Delez,
  LestvicaIgralcaDto,
  ProfilNasprotnik,
  ProfilZasebnoDto,
  TekmaProfila,
} from '../api/tipi'
import { OZNAKE_IZID } from '../api/tipi'
import { useAvtentikacija } from '../avtentikacija/AvtentikacijaKontekst'
import { GrafElo } from '../komponente/GrafElo'
import { NapakaPoizvedbe } from '../komponente/NapakaPoizvedbe'
import { SporociloNapake } from '../komponente/SporociloNapake'

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

  const lestvica = useQuery({ queryKey: ['lestvica'], queryFn: statistikaApi.lestvica })

  /* Merilo je isLoading (= brez podatkov IN zahteva teče), ne isPending:
     poizvedba brez podatkov, ki ne teče, je ustavljena (npr. brez povezave)
     ali končana z napako - takrat mora stran to povedati, ne pa do konca sveta
     kazati "Nalaganje …". */
  if (profil.isLoading) return <p className="obvestilo">Nalaganje …</p>
  if (profil.isPaused) return <p className="obvestilo">Ni povezave — počakaj na signal.</p>
  if (profil.error) return <NapakaPoizvedbe poizvedba={profil} kaj="profila" />
  if (!profil.data) return <p className="obvestilo">Tega igralca ni (več).</p>

  const p = profil.data
  const jeMoj = mojIdIgralec === idIgralec
  const { priimek, ime } = razbijIme(p.glava.polnoIme)
  const f = zasebno.data?.forma

  return (
    <section className="profil">
      <div>
        <Link to="/lestvica" className="povezava-nazaj">← Lestvica</Link>

        <div className="stran-glava">
          <div>
            <div className="profil__uvrstitev">
              {p.uvrstitev.mesto !== null && (
                <span className="profil__mesto-znacka">
                  {p.uvrstitev.mesto}. / {p.uvrstitev.skupajIgralcev}
                </span>
              )}
              {p.uvrstitev.percentil !== null && (
                <span className="profil__percentil">
                  Boljši od {p.uvrstitev.percentil} % igralcev z ratingom
                </span>
              )}
            </div>

            <h1 className="naslov-strani">
              <span className="naslov-strani__nad">{ime}</span>
              <span className="naslov-strani__glavni">{priimek}</span>
            </h1>

            <div className="profil__meta">
              <span>{p.glava.klub ?? 'brez kluba'}</span>
              {p.glava.igralnaRoka && (
                <span>{p.glava.igralnaRoka === 'LEVA' ? 'Levičar' : 'Desničar'}</span>
              )}
              {jeMoj && <span className="profil__moj">Tvoj profil</span>}
            </div>

            <Okolica
              vrstice={lestvica.data}
              idIgralec={idIgralec}
              mesto={p.uvrstitev.mesto}
            />
          </div>

          <div>
            <div className="elo-blok">
              <span className="elo-blok__oznaka">Klubski ELO</span>
              <span className="elo-blok__vrednost">{p.glava.rating ?? '—'}</span>
              {f && (
                <div className="elo-blok__noga">
                  <span>
                    {f.spremembaElo30dni === null
                      ? 'brez tekem v 30 dneh'
                      : `${f.spremembaElo30dni >= 0 ? '+' : '−'}${Math.abs(f.spremembaElo30dni)} / 30 dni`}
                  </span>
                  {f.najvisjiElo !== null && <span className="elo-blok__vrh">vrh {f.najvisjiElo}</span>}
                </div>
              )}
            </div>

            <div className="kolofon">
              <div className="kolofon__vrstica">
                <span className="kolofon__oznaka">Mesto</span>
                <span className="kolofon__vrednost">
                  {p.uvrstitev.mesto ? `${p.uvrstitev.mesto}. / ${p.uvrstitev.skupajIgralcev}` : '—'}
                </span>
              </div>
              <div className="kolofon__vrstica">
                <span className="kolofon__oznaka">Odigrane</span>
                <span className="kolofon__vrednost">{p.pregled.odigrane}</span>
              </div>
              <div className="kolofon__vrstica">
                <span className="kolofon__oznaka">Zmage – porazi</span>
                <span className="kolofon__vrednost">
                  {p.pregled.zmage} – {p.pregled.porazi}
                </span>
              </div>
              <div className="kolofon__vrstica">
                <span className="kolofon__oznaka">Uspešnost</span>
                <span className="kolofon__vrednost">{p.pregled.odstotekZmag} %</span>
              </div>
              {p.uvrstitev.klubskoPovprecje !== null && (
                <div className="kolofon__vrstica">
                  <span className="kolofon__oznaka">Klubsko povprečje</span>
                  <span className="kolofon__vrednost">
                    {p.uvrstitev.klubskoPovprecje}
                    {p.glava.rating !== null && (
                      <>
                        {' '}
                        <span
                          className={
                            p.glava.rating >= p.uvrstitev.klubskoPovprecje
                              ? 'kolofon__vrednost--poz'
                              : 'kolofon__vrednost--neg'
                          }
                        >
                          {p.glava.rating >= p.uvrstitev.klubskoPovprecje ? '+' : '−'}
                          {Math.abs(p.glava.rating - p.uvrstitev.klubskoPovprecje)}
                        </span>
                      </>
                    )}
                  </span>
                </div>
              )}
            </div>
          </div>
        </div>
      </div>

      <GrafElo tocke={p.graf} />

      {smemZasebno && zasebno.data && <ZasebniDel podatki={zasebno.data} />}
      {smemZasebno && zasebno.error && <SporociloNapake napaka={zasebno.error} />}
      {!smemZasebno && (
        <p className="obvestilo">
          Poglobljene analize (nasprotniki, forma, nizi in točke) vidi samo igralec sam.
          Če je to tvoj profil, se prijavi.
        </p>
      )}

      <div>
        <div className="naslovna-vrstica">
          <h2>Odigrane tekme</h2>
          <span className="sekcija__meta">{p.tekme.length} skupaj</span>
        </div>
        {p.tekme.length === 0 ? (
          <p className="obvestilo">Ta igralec še ni odigral nobene tekme.</p>
        ) : (
          <SeznamTekem tekme={p.tekme} />
        )}
      </div>
    </section>
  )
}

/* Igralec pred in za tem igralcem na lestvici — pokaže, koliko točk ELO manjka
   do naslednjega mesta. Brez lestvice (ali brez uvrstitve) se blok ne izriše. */
function Okolica({
  vrstice,
  idIgralec,
  mesto,
}: {
  vrstice: LestvicaIgralcaDto[] | undefined
  idIgralec: number
  mesto: number | null
}) {
  if (!vrstice || mesto === null) return null
  const indeks = vrstice.findIndex((v) => v.idIgralca === idIgralec)
  if (indeks < 0) return null

  const od = Math.max(0, indeks - 1)
  const okolica = vrstice.slice(od, indeks + 2).map((v, i) => ({ v, mesto: od + i + 1 }))
  const jaz = vrstice[indeks]
  const nad = indeks > 0 ? vrstice[indeks - 1] : null
  const razlikaNad =
    nad && nad.rating !== null && jaz.rating !== null ? nad.rating - jaz.rating : null

  return (
    <div className="okolica">
      <div className="okolica__glava">
        <span className="profil__percentil">Okolica na lestvici</span>
        <Link to="/lestvica" className="sekcija__meta">
          Celotna lestvica →
        </Link>
      </div>
      {okolica.map(({ v, mesto: m }) => {
        const jeJaz = v.idIgralca === idIgralec
        const razlika =
          !jeJaz && v.rating !== null && jaz.rating !== null ? v.rating - jaz.rating : null
        return (
          <div
            className={'okolica__vrstica' + (jeJaz ? ' okolica__vrstica--jaz' : '')}
            key={v.idIgralca}
          >
            <span className="okolica__mesto">{m}.</span>
            <span>
              <span className="okolica__ime">{v.polnoIme}</span>
              <span className="okolica__klub">{v.klub ?? 'brez kluba'}</span>
            </span>
            <span className="okolica__elo">{v.rating ?? '—'}</span>
            <span className="okolica__razlika">
              {jeJaz ? '—' : razlika === null ? '' : `${razlika > 0 ? '+' : '−'}${Math.abs(razlika)}`}
            </span>
          </div>
        )
      })}
      {razlikaNad !== null && razlikaNad > 0 && (
        <p className="profil__primerjava">
          Do <strong>{indeks}. mesta</strong> ti manjka {razlikaNad} točk ELO.
        </p>
      )}
    </div>
  )
}

function SeznamTekem({ tekme }: { tekme: TekmaProfila[] }) {
  return (
    <div className="tabela-ovoj">
      <table className="tabela tabela--vrh">
        <caption className="samo-za-bralnik">Odigrane tekme igralca, od najnovejše</caption>
        <thead>
          <tr>
            <th scope="col">Datum</th>
            <th scope="col">Tekmovanje</th>
            <th scope="col">Nasprotnik</th>
            <th scope="col" className="lestvica__stevilka">Rezultat</th>
            <th scope="col" className="lestvica__rating">ELO</th>
          </tr>
        </thead>
        <tbody>
          {tekme.map((t) => (
            <tr key={(t.ligaska ? 'l' : 't') + t.idTekme}>
              <td className="profil__datum">{t.datum ? datum(t.datum) : '—'}</td>
              <td>
                {t.tekmovanje}
                {t.ligaska && <span className="enanaena__vir">liga</span>}
                <span className="profil__del">{t.del}</span>
              </td>
              <td>
                <Link to={`/igralci/${t.idNasprotnika}/profil`} className="profil__nasprotnik">
                  {t.nasprotnik}
                </Link>
                {t.klubNasprotnika && <span className="profil__klub">{t.klubNasprotnika}</span>}
              </td>
              <td className="lestvica__stevilka">
                <span
                  className={
                    'profil__izid ' + (t.zmaga ? 'profil__zmaga' : 'profil__poraz')
                  }
                >
                  {t.niziZa}:{t.niziProti}
                </span>
                {t.izidTip && t.izidTip !== 'IGRANO' && (
                  <span className="enanaena__posebni"> ({OZNAKE_IZID[t.izidTip]})</span>
                )}
              </td>
              <td className="lestvica__rating">
                <SpremembaVGrafu vrednost={t.spremembaElo} />
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}

/* Sprememba ELO v tabeli profila je mono +11 / −11 v barvi izida. */
function SpremembaVGrafu({ vrednost }: { vrednost: number | null }) {
  if (vrednost === null) return <span className="profil__del">—</span>
  const poz = vrednost >= 0
  return (
    <span className={'graf__sprememba ' + (poz ? 'graf__sprememba--plus' : 'graf__sprememba--minus')}>
      {poz ? '+' : '−'}
      {Math.abs(vrednost)}
    </span>
  )
}

function ZasebniDel({ podatki }: { podatki: ProfilZasebnoDto }) {
  const { nasprotniki, niziInTocke, forma, poTekmovanjih } = podatki
  return (
    <>
      <div className="dvostolpicno dvostolpicno--lestvica">
        <div>
          <div className="naslovna-vrstica">
            <h2>Nasprotniki</h2>
            <span className="plosca__zasebno">Samo zate</span>
          </div>

          <div className="podnaslov-sekcije">Po igralni roki</div>
          <DelezVrstice
            delezi={[nasprotniki.protiDesnicarjem, nasprotniki.protiLevicarjem, nasprotniki.rokaNeznana]}
          />

          <div className="podnaslov-sekcije">
            Po moči nasprotnika · {nasprotniki.tekemZZnanimRatingom} tekem z znanim ratingom
          </div>
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
              <div className="podnaslov-sekcije">Po klubih nasprotnika</div>
              <DelezVrstice delezi={nasprotniki.poKlubih} />
            </>
          )}
        </div>

        <div>
          <div className="naslovna-vrstica">
            <h2 className="sekcija__naslov--manjsi">Forma</h2>
          </div>
          <div className="profil__forma">
            <div className="forma__trak">
              {forma.zadnjih10.length === 0 && <span className="obvestilo">Ni še tekem.</span>}
              {forma.zadnjih10.map((zmaga, i) => (
                <span key={i} className={'forma__znak ' + (zmaga ? 'forma__znak--z' : 'forma__znak--p')}>
                  {zmaga ? 'Z' : 'P'}
                </span>
              ))}
            </div>
            <span className="forma__opis">Zadnjih 10 tekem · najstarejša levo</span>

            <FormaKazalnik
              oznaka={forma.trenutniNizZmag ? 'Niz zmag' : 'Niz porazov'}
              vrednost={forma.trenutniNiz}
            />
            <FormaKazalnik oznaka="Najdaljši niz zmag" vrednost={forma.najdaljsiNizZmag} />
            <FormaKazalnik
              oznaka="ELO (30 dni)"
              vrednost={
                forma.spremembaElo30dni === null
                  ? '—'
                  : (forma.spremembaElo30dni >= 0 ? '+' : '−') + Math.abs(forma.spremembaElo30dni)
              }
            />
            <FormaKazalnik oznaka="Najvišji ELO" vrednost={forma.najvisjiElo ?? '—'} />

            {forma.najvisjiEloDatum && (
              <p className="profil__opomba">
                Najvišji ELO dosežen {datum(forma.najvisjiEloDatum)}.
              </p>
            )}
          </div>
        </div>
      </div>

      <div>
        <div className="naslovna-vrstica">
          <h2>Nizi in točke</h2>
          <span className="plosca__zasebno">Samo zate</span>
        </div>

        <div className="profil__kazalniki">
          <Kazalnik oznaka="Dobljeni nizi" vrednost={niziInTocke.dobljeniNizi} />
          <Kazalnik oznaka="Prejeti nizi" vrednost={niziInTocke.prejetiNizi} />
          <Kazalnik
            oznaka="Odločilni niz"
            vrednost={`${niziInTocke.odlocilniNiz.zmage}:${niziInTocke.odlocilniNiz.porazi}`}
          />
          <Kazalnik
            oznaka="Uspešnost v odl. nizu"
            vrednost={`${niziInTocke.odlocilniNiz.odstotek} %`}
          />
        </div>

        {niziInTocke.tocke.steviloTekem > 0 && (
          <div className="profil__kazalniki">
            <Kazalnik oznaka="Osvojene točke" vrednost={niziInTocke.tocke.tockeZa} />
            <Kazalnik oznaka="Prejete točke" vrednost={niziInTocke.tocke.tockeProti} />
            <Kazalnik oznaka="Delež točk" vrednost={`${niziInTocke.tocke.odstotekTock} %`} />
            <Kazalnik oznaka="Povprečje na niz" vrednost={niziInTocke.tocke.povprecjeNaNiz} />
          </div>
        )}

        {niziInTocke.razmerja.length > 0 && (
          <ul className="profil__razmerja">
            <li className="profil__razmerja-oznaka">Končni izidi</li>
            {niziInTocke.razmerja.map((r) => (
              <li key={r.oznaka + r.zmaga} className={r.zmaga ? 'profil__zmaga' : 'profil__poraz'}>
                {r.oznaka} <span className="profil__stevec-razmerja">×{r.stevilo}</span>
              </li>
            ))}
          </ul>
        )}

        <p className="profil__opomba">
          {niziInTocke.tocke.steviloTekem === 0
            ? 'Točke po nizih so shranjene samo za turnirske tekme in za zdaj ni nobene take tekme.'
            : `Točke po nizih so shranjene samo za turnirske tekme; izračun temelji na ${niziInTocke.tocke.steviloTekem} takih tekmah, ligaška srečanja hranijo samo nize.`}
        </p>
      </div>

      <div>
        <div className="naslovna-vrstica">
          <h2>Po tekmovanjih</h2>
          <span className="plosca__zasebno">Samo zate</span>
        </div>

        <div className="profil__razrezi">
          <div>
            <div className="podnaslov-sekcije">Turnirji in lige</div>
            <DelezVrstice delezi={[poTekmovanjih.turnirji, poTekmovanjih.lige]} />
          </div>

          {(poTekmovanjih.doma.odigrane > 0 || poTekmovanjih.vGosteh.odigrane > 0) && (
            <div>
              <div className="podnaslov-sekcije">Liga · doma in v gosteh</div>
              <DelezVrstice delezi={[poTekmovanjih.doma, poTekmovanjih.vGosteh]} />
            </div>
          )}

          {poTekmovanjih.poFazi.length > 0 && (
            <div>
              <div className="podnaslov-sekcije">Po fazi turnirja</div>
              <DelezVrstice delezi={poTekmovanjih.poFazi} />
            </div>
          )}

          {poTekmovanjih.poPoziciji.length > 0 && (
            <div>
              <div className="podnaslov-sekcije">Po poziciji v postavi</div>
              <DelezVrstice delezi={poTekmovanjih.poPoziciji} />
            </div>
          )}

          {poTekmovanjih.dvojice.odigrane > 0 && (
            <div>
              <div className="podnaslov-sekcije">Ligaške dvojice</div>
              <DelezVrstice delezi={[poTekmovanjih.dvojice]} />
              <p className="profil__opomba">Dvojice ne štejejo v ELO ne med posamične zmage.</p>
            </div>
          )}
        </div>
      </div>
    </>
  )
}

/* Velika številka v stolpcu, ločenem s hairline (nikoli kartica). */
function Kazalnik({ oznaka, vrednost }: { oznaka: string; vrednost: string | number }) {
  return (
    <div className="kazalnik">
      <div className="kazalnik__vrednost">{vrednost}</div>
      <div className="kazalnik__oznaka">{oznaka}</div>
    </div>
  )
}

/* Vrstica forme: mono oznaka levo, velika številka desno. */
function FormaKazalnik({ oznaka, vrednost }: { oznaka: string; vrednost: string | number }) {
  return (
    <div className="forma__kazalnik">
      <span className="forma__kazalnik-oznaka">{oznaka}</span>
      <span className="forma__kazalnik-vrednost">{vrednost}</span>
    </div>
  )
}

/* Deleži: oznaka in odstotek v isti vrstici, palica pod njima. Prazne skupine
   izpustimo; manjkajoč vnos (neujemanje s DTO-jem zaledja) preskočimo, da
   napaka ne podre celotne strani. */
function DelezVrstice({ delezi }: { delezi: (Delez | undefined)[] }) {
  const vidni = delezi.filter((d): d is Delez => d != null && d.odigrane > 0)
  if (vidni.length === 0) return <p className="obvestilo">Ni podatkov za ta razrez.</p>
  return (
    <ul className="delezi">
      {vidni.map((d) => (
        <li key={d.oznaka} className="delez">
          <div className="delez__glava">
            <span className="delez__oznaka">{d.oznaka}</span>
            <span className="delez__vrednost">
              {d.odstotek} %{' '}
              <span className="delez__izid">
                {d.zmage}–{d.porazi}
              </span>
            </span>
          </div>
          <span className="delez__stolpec">
            <span className="delez__polnilo" style={{ width: `${d.odstotek}%` }} />
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

/* Zaledje sestavi polno ime kot "Priimek Ime", zato je prva beseda priimek.
   Naslov strani ga postavi v veliko vrstico, ime pa v nadnaslov. */
function razbijIme(polnoIme: string): { priimek: string; ime: string } {
  const presledek = polnoIme.indexOf(' ')
  if (presledek < 0) return { priimek: polnoIme, ime: '' }
  return { priimek: polnoIme.slice(0, presledek), ime: polnoIme.slice(presledek + 1) }
}

function datum(iso: string): string {
  const d = new Date(iso)
  return Number.isNaN(d.getTime()) ? '—' : d.toLocaleDateString('sl-SI')
}
