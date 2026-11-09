/* Izpis rezultatov za tisk: listi A4 z enotno glavo (znak in napis Turnirko, vrsta
   lista, turnir, kategorija, datum in kraj, logotip turnirja) in nogo (pokrovitelji,
   številka lista). Kaj je na katerem listu, izračuna pomozno/izpisRezultatov; tu se iste
   številke postavijo v spremenljivke sloga, da se izračun in izris ne moreta raziti.
   Črno-belo ne glede na temo, kot listki in žreb. */
import type { CSSProperties } from 'react'

import type { PokroviteljDto, SlikaDto } from '../api/tipi'
import {
  MERE_LISTA,
  MERE_SKUPIN,
  sirinaVsebine,
  type GlavaIzpisa,
  type ListIzpisa,
  type ListRazvrstitve,
  type ListSkupin,
} from '../pomozno/izpisRezultatov'
import { urlSlike } from '../pomozno/slike'
import { IzpisMreze, IzpisSv } from './IzpisMreza'
import { IzpisSvrPlatno } from './IzpisSvr'
import { PokroviteljiTisk } from './PokroviteljiTisk'
import { ZnakTurnirko } from './Postavitev'
import { KriznaTabela, RazporedZNizi } from './ZrebSkupinTisk'

export function IzpisListi({
  listi,
  glava,
  logotip,
  pokrovitelji,
  oblikovanje = true,
}: {
  listi: ListIzpisa[]
  glava: GlavaIzpisa
  logotip: SlikaDto | null
  pokrovitelji: PokroviteljDto[]
  /* Samo za liste SVR: Turnirkov znak, logotip, kraj in datum zgoraj, pokrovitelji in
     številka lista spodaj. Brez njega je list kot Excelov vzorec. */
  oblikovanje?: boolean
}) {
  return (
    <>
      {listi.map((list, i) =>
        list.vrsta === 'svr' ? (
          <section
            key={list.kljuc}
            className="izpis-list izpis-list--pokoncni izpis-list--svr"
            aria-label={`List ${i + 1} od ${listi.length}: ${list.oznaka}`}
          >
            <IzpisSvrPlatno list={list} />
            {oblikovanje && (
              <>
                <VrhSvr glava={glava} logotip={logotip} />
                <footer className="izpis-svr__dno">
                  <PokroviteljiTisk pokrovitelji={pokrovitelji} razred="izpis-list__pokrovitelji" />
                  <div className="izpis-list__noga">
                    <span>Turnirko · turnirko-nt.si</span>
                    <span>
                      List {i + 1} od {listi.length}
                    </span>
                  </div>
                </footer>
              </>
            )}
          </section>
        ) : (
          <section
            key={list.kljuc}
            className={`izpis-list izpis-list--${list.usmeritev}`}
            aria-label={`List ${i + 1} od ${listi.length}: ${list.oznaka}`}
            style={
              {
                '--izpis-glava': `${MERE_LISTA.glava}mm`,
                '--izpis-noga': `${MERE_LISTA.noga}mm`,
              } as CSSProperties
            }
          >
            <GlavaLista oznaka={list.oznaka} glava={glava} logotip={logotip} />
            <div className="izpis-list__vsebina">
              <VsebinaLista list={list} />
            </div>
            <footer className="izpis-list__dno">
              <PokroviteljiTisk pokrovitelji={pokrovitelji} razred="izpis-list__pokrovitelji" />
              <div className="izpis-list__noga">
                <span>Turnirko · turnirko-nt.si</span>
                <span>
                  List {i + 1} od {listi.length}
                </span>
              </div>
            </footer>
          </section>
        ),
      )}
    </>
  )
}

/* Dodatno oblikovanje lista SVR: znamka Turnirko, kategorija, datum in kraj ter logotip
   turnirja v tankem pasu nad naslovom (SVR naslov stoji nižje, zato pas ne zadene vzorca). */
function VrhSvr({ glava, logotip }: { glava: GlavaIzpisa; logotip: SlikaDto | null }) {
  return (
    <header className="izpis-svr__vrh">
      <div className="izpis-list__znamka">
        <ZnakTurnirko velikost={15} />
        Turnirko
      </div>
      <p className="izpis-svr__vrh-besedilo">
        <span>{glava.lastnosti}</span>
        {glava.kdajKje && <span>{glava.kdajKje}</span>}
      </p>
      {logotip && (
        <img
          className="izpis-svr__logotip"
          src={urlSlike(logotip, 'TISK')}
          width={logotip.sirina}
          height={logotip.visina}
          alt=""
        />
      )}
    </header>
  )
}

/* Dolgo ime (uvožena »Državno prvenstvo …, Kidričevo, 19. 9. 2026«) se zmanjša, preden
   se prelomi: višina glave je stalna, ker je od nje odvisno, koliko gre na list. */
function velikostNaslova(naslov: string): string {
  if (naslov.length <= 40) return ''
  return naslov.length <= 62 ? ' izpis-list__naslov--dolg' : ' izpis-list__naslov--zelo-dolg'
}

function GlavaLista({
  oznaka,
  glava,
  logotip,
}: {
  oznaka: string
  glava: GlavaIzpisa
  logotip: SlikaDto | null
}) {
  return (
    <header className="izpis-list__glava">
      <div className="izpis-list__vrh">
        <div className="izpis-list__znamka">
          <ZnakTurnirko velikost={18} />
          Turnirko
        </div>
        <span className="izpis-list__oznaka">{oznaka}</span>
      </div>
      <div className="izpis-list__naslov-blok">
        <div className="izpis-list__besedilo">
          <h1 className={'izpis-list__naslov' + velikostNaslova(glava.naslov)}>{glava.naslov}</h1>
          <p className="izpis-list__lastnosti">{glava.lastnosti}</p>
          {glava.kdajKje && <p className="izpis-list__lastnosti">{glava.kdajKje}</p>}
        </div>
        {logotip && (
          <img
            className="izpis-list__logotip"
            src={urlSlike(logotip, 'TISK')}
            width={logotip.sirina}
            height={logotip.visina}
            alt=""
          />
        )}
      </div>
    </header>
  )
}

function VsebinaLista({ list }: { list: ListIzpisa }) {
  switch (list.vrsta) {
    case 'razvrstitev':
      return <VsebinaRazvrstitve list={list} />
    case 'skupine':
      return <VsebinaSkupin list={list} />
    case 'mreza':
      return <IzpisMreze list={list} />
    case 'sv':
      return <IzpisSv list={list} />
    case 'svr':
      /* List SVR ima svoj okvir (brez Turnirkove glave), glej IzpisListi. */
      return null
  }
}

/* ---------- Končna razvrstitev ---------- */

function VsebinaRazvrstitve({ list }: { list: ListRazvrstitve }) {
  const merila = {
    '--vrh-vrstica': `${list.gostota.vrh}mm`,
    '--vrstica': `${list.gostota.vrstica}mm`,
    '--sirina-mesta': `${list.sirinaMesta}mm`,
    '--stolpcev': list.stolpcev,
  } as CSSProperties

  const zIzidom = list.vrh.some((u) => u.izid) || list.stolpci.some((s) => s.some((v) => v.udelezenec.izid))

  return (
    <div
      className={
        `razvrstitev-list razvrstitev-list--${list.gostota.ime}` + (zIzidom ? ' razvrstitev-list--z-izidom' : '')
      }
      style={merila}
    >
      {list.vrh.length > 0 && (
        <div className="razvrstitev-list__vrh">
          {list.vrh.map((u) => (
            <div className="razvrstitev-list__vrh-vrstica" key={u.id}>
              <span className="razvrstitev-list__vrh-mesto">{u.oznaka}</span>
              <span className="razvrstitev-list__vrh-ime">
                <span className="razvrstitev-list__ime-lahko">{u.ime}</span>{' '}
                <span className="razvrstitev-list__priimek-vrh">{u.priimek}</span>
              </span>
              <span className="razvrstitev-list__vrh-desno">
                <span className="razvrstitev-list__klub">{u.klub}</span>
                {u.izid && <span className="razvrstitev-list__izid">{u.izid}</span>}
              </span>
            </div>
          ))}
        </div>
      )}

      {list.stolpci.length > 0 && (
        <div className="razvrstitev-list__ostali">
          {list.stolpci.map((stolpec, i) => (
            <div className="razvrstitev-list__stolpec" key={i}>
              {stolpec.map(({ udelezenec: u, oznaka, rob }) => (
                <div
                  className={`razvrstitev-list__vrstica razvrstitev-list__vrstica--${rob}`}
                  key={u.id}
                >
                  <span className="razvrstitev-list__vrstica-mesto">{oznaka}</span>
                  <span className="razvrstitev-list__vrstica-ime">
                    <span className="razvrstitev-list__ime-lahko">{u.ime}</span>{' '}
                    <span className="razvrstitev-list__priimek">{u.priimek}</span>
                  </span>
                  <span className="razvrstitev-list__vrstica-desno">
                    <span className="razvrstitev-list__klub">{u.klub}</span>
                    {u.izid && <span className="razvrstitev-list__izid">{u.izid}</span>}
                  </span>
                </div>
              ))}
            </div>
          ))}
        </div>
      )}
    </div>
  )
}

/* ---------- Skupinski del ---------- */

const MERE_SKUPIN_CSS = {
  '--kriz-glava': `${MERE_SKUPIN.krizGlava}mm`,
  '--kriz-vrstica': `${MERE_SKUPIN.krizVrstica}mm`,
  '--razmik': `${MERE_SKUPIN.razmik}mm`,
  '--seznam-glava': `${MERE_SKUPIN.seznamGlava}mm`,
  '--seznam-vrstica': `${MERE_SKUPIN.seznamVrstica}mm`,
  '--izpis-sklop': `${MERE_SKUPIN.sklop}mm`,
  '--izpis-nadaljevanje': `${MERE_SKUPIN.nadaljevanje}mm`,
  '--izpis-med-skupinami': `${MERE_SKUPIN.medSkupinami}mm`,
} as CSSProperties

function VsebinaSkupin({ list }: { list: ListSkupin }) {
  return (
    <div className="izpis-skupine" style={MERE_SKUPIN_CSS}>
      {list.bloki.map((blok) =>
        blok.vrsta === 'sklop' ? (
          <h2 key={blok.kljuc} className="izpis-skupine__sklop">
            {blok.naslov}
          </h2>
        ) : blok.vrsta === 'tekme' ? (
          <div key={blok.kljuc} className="izpis-skupine__skupina">
            <h2 className="izpis-skupine__sklop">{blok.skupina.naslov}</h2>
            <RazporedZNizi
              skupina={blok.skupina}
              kola={blok.kola}
              ekipno={list.ekipno}
              oznaka={null}
              zaRezultate
              brezPara
            />
          </div>
        ) : (
          <div key={blok.kljuc} className="izpis-skupine__skupina">
            {blok.kriznaTabela ? (
              <KriznaTabela
                skupina={blok.skupina}
                sklop={null}
                ekipno={list.ekipno}
                sirinaVsebine={sirinaVsebine(list.usmeritev)}
                zaRezultate
              />
            ) : (
              <p className="izpis-skupine__nadaljevanje">
                <strong>{blok.skupina.naslov}</strong> · nadaljevanje · {blok.del}. del od {blok.delov}
              </p>
            )}
            {blok.kola.length > 0 && (
              <RazporedZNizi
                skupina={blok.skupina}
                kola={blok.kola}
                ekipno={list.ekipno}
                oznaka={null}
                zaRezultate
              />
            )}
          </div>
        ),
      )}
    </div>
  )
}
