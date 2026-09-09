/* Zavihek »Zanimivosti« turnirja oz. lige.

   Namenjen je gledalcu, ne organizatorju: pove, kaj je bilo na tem tekmovanju
   vredno videti. Zato so postavke pretežno pozitivne in nobena ne razglaša
   najslabšega — največjega padca ELO zavihek nima.

   Dve pravili postavitve, ki ju ne razbij:

   - **Vrstica, ki je podatki ne napolnijo, se ne izriše.** Uvožena zgodovina
     brez ratingov, liga brez vpisanih točk po nizih in turnir v prvi uri
     nimajo istih podatkov; »ni podatka« je slabše od odsotnosti vrstice.
     Zato je vsak sklop pogojen in ne pokaže ničle.
   - **Zgodba je blok, lestvička je vrstica.** Enkratni dogodki (presenečenje,
     obrat, najdaljši niz) so trditve in imajo obliko oznaka → stavek → mono
     kontekst; primerjave (vzpon ELO, zid, klubi) so vrstice s črtami kot
     povsod drugod. Nikoli mreža kartic s številkami.

   Imena so brez glagolov (»A proti B« in ne »A je premagal B«) — zapisnik
   nikogar ne sklanja po spolu, tekmo pa opiše izid. */
import type { ReactNode } from 'react'
import { Link } from 'react-router-dom'

import type {
  StatDelavec,
  StatGostovanje,
  StatKlub,
  StatNosilec,
  StatOseba,
  StatVzpon,
  StatZid,
  StatistikaTekmovanjaDto,
} from '../api/tipi'
import {
  oblikujStevilo,
  sklonIgralcev,
  sklonTekem,
  sklonTock,
  sklonZmag,
} from '../pomozno/oblikovanje'

interface Lastnosti {
  podatki: StatistikaTekmovanjaDto
  /* Turnir nima srečanj, zato se vrstica »na nož« ne poveže nikamor. */
  jeLiga: boolean
}

export function ZanimivostiTekmovanja({ podatki: s, jeLiga }: Lastnosti) {
  if (!s.dovoljPodatkov) {
    return (
      <p className="obvestilo">
        Zanimivosti se pokažejo, ko je odigranih vsaj deset tekem. Pri manj bi bila
        vsaka »najboljša« vrstica naključje in ne ugotovitev.
      </p>
    )
  }

  const imaZgodbe =
    s.presenecenje || s.obrat || s.najdaljsaTekma || s.najdaljsiNiz || s.naNoz || s.dvojica

  return (
    <div className="zanimivosti">
      {s.vTeku && (
        <p className="zanimivosti__opozorilo">
          Tekmovanje še traja — številke se bodo še premaknile.
        </p>
      )}

      {s.stevilke && (
        <section>
          <div className="naslovna-vrstica">
            <h2>V številkah</h2>
          </div>
          <div className="kolofon kolofon--mreza zanimivosti__stevilke">
            <Kazalnik oznaka="Igralcev" vrednost={s.stevilke.igralcev} />
            {s.stevilke.klubov > 0 && (
              <Kazalnik oznaka="Klubov" vrednost={s.stevilke.klubov} />
            )}
            <Kazalnik oznaka="Odigranih tekem" vrednost={s.stevilke.tekem} />
            <Kazalnik oznaka="Odigranih nizov" vrednost={s.stevilke.nizov} />
            {s.stevilke.tock !== null && (
              <Kazalnik oznaka="Osvojenih točk" vrednost={s.stevilke.tock} />
            )}
            {s.stevilke.dogodkov !== null && (
              <Kazalnik oznaka="Kategorij" vrednost={s.stevilke.dogodkov} />
            )}
            {s.stevilke.ekip !== null && <Kazalnik oznaka="Ekip" vrednost={s.stevilke.ekip} />}
            {s.stevilke.tekemDvojic > 0 && (
              <Kazalnik oznaka="Tekem dvojic" vrednost={s.stevilke.tekemDvojic} />
            )}
          </div>
        </section>
      )}

      {imaZgodbe && (
        <section>
          <div className="naslovna-vrstica">
            <h2>Zgodbe</h2>
          </div>

          {s.presenecenje && (
            <Zgodba
              oznaka="Presenečenje"
              meta={[
                s.presenecenje.izid,
                `ELO ${oblikujStevilo(s.presenecenje.ratingZmagovalca)} proti ${oblikujStevilo(
                  s.presenecenje.ratingPorazenca,
                )} · razlika ${oblikujStevilo(s.presenecenje.razlika)}`,
                s.presenecenje.kontekst,
              ]}
            >
              <Ime oseba={s.presenecenje.zmagovalec} /> <Proti />{' '}
              <Ime oseba={s.presenecenje.porazenec} />
            </Zgodba>
          )}

          {s.obrat && (
            <Zgodba
              oznaka="Obrat"
              stevec={
                s.obrat.koliko > 1
                  ? `${s.obrat.koliko} obratov na tekmovanju`
                  : undefined
              }
              meta={[
                `${s.obrat.izid} po zaostanku 0 : 2`,
                s.obrat.nizi,
                s.obrat.kontekst,
              ]}
            >
              <Ime oseba={s.obrat.zmagovalec} /> <Proti /> <Ime oseba={s.obrat.porazenec} />
            </Zgodba>
          )}

          {s.najdaljsiNiz && (
            <Zgodba
              oznaka="Najdaljši niz"
              meta={[
                `${s.najdaljsiNiz.tockePrvi} : ${s.najdaljsiNiz.tockeDrugi}`,
                `${s.najdaljsiNiz.zaporedna}. niz`,
                s.najdaljsiNiz.kontekst,
              ]}
            >
              <Ime oseba={s.najdaljsiNiz.prvi} /> <Proti /> <Ime oseba={s.najdaljsiNiz.drugi} />
            </Zgodba>
          )}

          {s.najdaljsaTekma && (
            <Zgodba
              oznaka="Najdaljša tekma"
              meta={[
                s.najdaljsaTekma.izid,
                `${s.najdaljsaTekma.nizov} nizov · ${oblikujStevilo(
                  s.najdaljsaTekma.tock,
                )} ${sklonTock(s.najdaljsaTekma.tock)}`,
                s.najdaljsaTekma.kontekst,
              ]}
            >
              <Ime oseba={s.najdaljsaTekma.zmagovalec} /> <Proti />{' '}
              <Ime oseba={s.najdaljsaTekma.porazenec} />
            </Zgodba>
          )}

          {s.naNoz && (
            <Zgodba
              oznaka="Srečanje na nož"
              stevec={
                s.naNoz.koliko > 1
                  ? `še ${s.naNoz.koliko - 1} enako tesnih srečanj`
                  : undefined
              }
              meta={[
                `${s.naNoz.dobljeneDomaci} : ${s.naNoz.dobljeneGost}`,
                s.naNoz.odlocil ? `odločil ${s.naNoz.odlocil.polnoIme}` : 'odločile dvojice',
                `${s.naNoz.kolo}. kolo`,
              ]}
            >
              {jeLiga ? (
                <Link to={`/srecanja/${s.naNoz.idSrecanje}`} className="zanimivost__ime">
                  {s.naNoz.domaci} <Proti /> {s.naNoz.gost}
                </Link>
              ) : (
                <>
                  {s.naNoz.domaci} <Proti /> {s.naNoz.gost}
                </>
              )}
            </Zgodba>
          )}

          {s.dvojica && (
            <Zgodba
              oznaka="Najuspešnejša dvojica"
              meta={[
                `${s.dvojica.zmage} ${sklonZmag(s.dvojica.zmage)}`,
                `${s.dvojica.porazi} ${s.dvojica.porazi === 1 ? 'poraz' : 'porazov'}`,
              ]}
            >
              <Ime oseba={s.dvojica.prvi} />
              <span className="zanimivost__vezaj" aria-hidden="true"> · </span>
              <Ime oseba={s.dvojica.drugi} />
            </Zgodba>
          )}
        </section>
      )}

      {/* Prvi naslovi so seznam in ne zgodba: turnir s petimi kategorijami jih
          ima lahko štiri, štirje enaki bloki »PRVI NASLOV« zapored pa so
          ponavljanje. Kategorija stoji desno namesto številke — pri tej
          vrstici je odgovor na »kje« in ne »koliko«. */}
      {s.prviNaslovi.length > 0 && (
        <Lestvicka naslov="Prvi naslov" meta="Prva zmaga v karieri">
          {s.prviNaslovi.map((n) => (
            <VrsticaZanimivosti
              key={n.oseba.idIgralec}
              oseba={n.oseba}
              pod={n.oseba.klub ?? ''}
              mono={n.dogodek}
            />
          ))}
        </Lestvicka>
      )}

      {s.stejeVElo && s.vzponi.length > 0 && (
        <Lestvicka naslov="Največ pridobljenega ELO" meta="Na tem tekmovanju">
          {s.vzponi.map((v: StatVzpon) => (
            <VrsticaZanimivosti
              key={v.oseba.idIgralec}
              oseba={v.oseba}
              pod={`ELO ${oblikujStevilo(v.koncni)} · ${v.odigranih} ${sklonTekem(v.odigranih)}`}
              stevilo={`+${oblikujStevilo(v.pridobil)}`}
              poudarek
            />
          ))}
        </Lestvicka>
      )}

      {s.zid.length > 0 && (
        <Lestvicka naslov="Zid" meta="Najmanj prejetih nizov">
          {s.zid.map((z: StatZid) => (
            <VrsticaZanimivosti
              key={z.oseba.idIgralec}
              oseba={z.oseba}
              pod={
                z.prejeti === 0
                  ? `brez izgubljenega niza · ${z.odigrane} ${sklonTekem(z.odigrane)}`
                  : `${z.odigrane} ${sklonTekem(z.odigrane)}`
              }
              stevilo={`${z.dobljeni} : ${z.prejeti}`}
            />
          ))}
        </Lestvicka>
      )}

      {s.delavci.length > 0 && (
        <Lestvicka naslov="Največ tekem" meta="Kdo je bil največ za mizo">
          {s.delavci.map((d: StatDelavec) => (
            <VrsticaZanimivosti
              key={d.oseba.idIgralec}
              oseba={d.oseba}
              pod={
                d.dvojic > 0
                  ? `${d.zmage} ${sklonZmag(d.zmage)} · ${d.dvojic} v dvojicah`
                  : `${d.zmage} ${sklonZmag(d.zmage)}`
              }
              stevilo={String(d.odigrane + d.dvojic)}
            />
          ))}
        </Lestvicka>
      )}

      {s.klubi.length > 0 && (
        <Lestvicka naslov="Klubi" meta="Po zmagah v posamičnih tekmah">
          {s.klubi.map((k: StatKlub) => (
            <VrsticaZanimivosti
              key={k.ime}
              ime={k.ime}
              pod={`${k.igralcev} ${sklonIgralcev(k.igralcev)} · ${k.odigrane} ${sklonTekem(
                k.odigrane,
              )}`}
              stevilo={String(k.zmage)}
            />
          ))}
        </Lestvicka>
      )}

      {s.gostje.length > 0 && (
        <Lestvicka naslov="Najboljši gost" meta="Zmage v gosteh">
          {s.gostje.map((g: StatGostovanje) => (
            <VrsticaZanimivosti
              key={g.ekipa}
              ime={g.ekipa}
              pod={`${g.zmage} od ${g.srecanj} · ${g.odstotek} %`}
              stevilo={String(g.zmage)}
            />
          ))}
        </Lestvicka>
      )}

      {s.nosilci.length > 0 && (
        <Lestvicka naslov="Nosilci ekip" meta="Največ zmag za svojo ekipo">
          {s.nosilci.map((n: StatNosilec) => (
            <VrsticaZanimivosti
              key={n.ekipa}
              oseba={n.oseba}
              pod={n.ekipa}
              stevilo={`${n.zmage} : ${n.porazi}`}
            />
          ))}
        </Lestvicka>
      )}
    </div>
  )
}

/* ---------- Gradniki ---------- */

function Kazalnik({ oznaka, vrednost }: { oznaka: string; vrednost: number }) {
  return (
    <div className="kolofon__vrstica">
      <span className="kolofon__oznaka">{oznaka}</span>
      <span className="kolofon__vrednost">{oblikujStevilo(vrednost)}</span>
    </div>
  )
}

/* Enkraten dogodek: mono oznaka, stavek z imeni, mono kontekst pod njim.
   »stevec« pove, kolikokrat se je isto zgodilo — vrstica pokaže najboljši
   primer, števec pa, da ni bil edini. */
function Zgodba({
  oznaka,
  meta,
  stevec,
  children,
}: {
  oznaka: string
  meta: string[]
  stevec?: string
  children: ReactNode
}) {
  return (
    <div className="zanimivost">
      <div className="zanimivost__glava">
        <span className="zanimivost__oznaka">{oznaka}</span>
        {stevec && <span className="zanimivost__stevec">{stevec}</span>}
      </div>
      <p className="zanimivost__stavek">{children}</p>
      <p className="zanimivost__meta">{meta.filter(Boolean).join(' · ')}</p>
    </div>
  )
}

/* Beseda »proti« nosi vlogo dvopičja v zapisniku: ne sklanja nikogar po
   spolu in je krajša od glagola, ki bi ga moral. */
function Proti() {
  return <span className="zanimivost__proti">proti</span>
}

function Ime({ oseba }: { oseba: StatOseba }) {
  return (
    <Link to={`/igralci/${oseba.idIgralec}/profil`} className="zanimivost__ime">
      {oseba.polnoIme}
    </Link>
  )
}

function Lestvicka({
  naslov,
  meta,
  children,
}: {
  naslov: string
  meta: string
  children: ReactNode
}) {
  return (
    <section>
      <div className="naslovna-vrstica">
        <h2>{naslov}</h2>
        <span className="sekcija__meta">{meta}</span>
      </div>
      <div>{children}</div>
    </section>
  )
}

/* Vrstica lestvičke: ime (povezava na profil, kadar gre za človeka), drobno
   pojasnilo pod njim in mono vrednost desno. Vrednost je »stevilo«, kadar meri
   količino, in »mono«, kadar je besedilo (ime kategorije) — številčni slog bi
   besedo postavil v velikost rezultata. */
function VrsticaZanimivosti({
  oseba,
  ime,
  pod,
  stevilo,
  mono,
  poudarek,
}: {
  oseba?: StatOseba
  ime?: string
  pod: string
  stevilo?: string
  mono?: string
  poudarek?: boolean
}) {
  /* Klub stoji pred pojasnilom, kadar vrstica meri količino — pri seznamu
     prvih naslovov je »pod« že klub sam. Igralec brez kluba podpisa nima:
     »brez kluba« štirikrat zapored je šum in ne podatek. */
  const podpis = [oseba?.klub && stevilo ? oseba.klub : null, pod].filter(Boolean).join(' · ')

  return (
    <div className="vrstica zanimivosti__vrstica">
      <span>
        {oseba ? (
          <Link to={`/igralci/${oseba.idIgralec}/profil`} className="vrstica__ime">
            {oseba.polnoIme}
          </Link>
        ) : (
          <span className="vrstica__ime">{ime}</span>
        )}
        {podpis && <span className="vrstica__pod">{podpis}</span>}
      </span>
      {stevilo ? (
        <span className={'vrstica__stevilo' + (poudarek ? ' zanimivosti__stevilo--poz' : '')}>
          {stevilo}
        </span>
      ) : (
        <span className="vrstica__mono">{mono}</span>
      )}
    </div>
  )
}
