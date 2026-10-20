/* Listki skupin za tisk, narejeni po papirnati predlogi lastnika (okt 2026):
   vsaka skupina je svoj listek s KRIŽNO TABELO (vsak z vsakim: nizi v celici,
   na koncu nizi, zmage in mesto) in pod njo RAZPOREDOM po kolih - bodisi z
   okenci za točke po nizih in rezultat (skupina igra sama za mizo) bodisi samo
   s pari (izide pišejo sodniki na svoje listke, kot pri TOP).

   Listek je širok kot list A4 in visok šestino lista ali več (tretjina,
   polovica, dve tretjini, cel list), zato se list reže samo vodoravno, po
   črtkanih črtah. Mere in zlaganje izračuna pomozno/zrebSkupin.ts; tu se iste
   številke postavijo v spremenljivke sloga (razdelek 16 v slog.css), da se
   izračun in izris ne moreta raziti.

   Vsak listek nosi kategorijo, turnir in skupino, da ostane berljiv, ko ga
   odrežeš od lista, in majhno znamko Turnirko. Odigrana tekma se izpiše (v
   tabeli nizi, v razporedu točke in rezultat), zato je ponoven tisk tudi
   pregled stanja; nizi, zmage in mesto pa šele, ko je skupina odigrana.
   Črno-belo ne glede na temo, kot sodniški listki. */
import type { CSSProperties } from 'react'

import { ZnakTurnirko } from './Postavitev'
import {
  KOL_V_PASU,
  type ListekSkupine,
  MERE,
  type TiskCelica,
  type TiskKolo,
  type TiskSkupina,
  type TiskTekma,
  VISINA_LISTA_MM,
  vrsticKola,
} from '../pomozno/zrebSkupin'

/* Širina vsebine listka: list 210 mm brez stranskih robov po 8 mm. */
const SIRINA_VSEBINE_MM = 194

export function ListiSkupin({ listi }: { listi: ListekSkupine[][] }) {
  return (
    <div className="listki-skupin">
      {listi.map((list, i) => {
        /* Rez je pod vsakim listkom, ki ne sega do dna lista. */
        let zasedeno = 0
        return (
          <section
            key={i}
            className="listki-skupin__list"
            aria-label={`List ${i + 1} od ${listi.length}`}
          >
            {list.map((listek) => {
              zasedeno += listek.sestin
              return <Listek key={listek.kljuc} listek={listek} rez={zasedeno < 6} />
            })}
          </section>
        )
      })}
    </div>
  )
}

function Listek({ listek, rez }: { listek: ListekSkupine; rez: boolean }) {
  const { skupina, del } = listek
  const r = del.raztezek
  const mere = {
    height: `${(listek.sestin * VISINA_LISTA_MM) / 6}mm`,
    '--rob-zgoraj': `${MERE.robZgoraj}mm`,
    '--rob-spodaj': `${MERE.robSpodaj}mm`,
    '--glava': `${MERE.glava}mm`,
    '--kriz-glava': `${MERE.krizGlava}mm`,
    '--kriz-vrstica': `${MERE.krizVrstica * r}mm`,
    '--razmik': `${MERE.razmik}mm`,
    '--seznam-glava': `${MERE.seznamGlava}mm`,
    '--seznam-vrstica': `${MERE.seznamVrstica * r}mm`,
    '--razpored-glava': `${MERE.razporedGlava}mm`,
    '--razpored-vrstica': `${MERE.razporedVrstica * r}mm`,
    '--nadaljevanje': `${MERE.nadaljevanje}mm`,
  } as CSSProperties

  return (
    <article
      className={'listek-skupine' + (rez ? ' listek-skupine--rez' : '')}
      style={mere}
      aria-label={`${listek.kategorija}, ${skupina.naslov}`}
    >
      <header className="listek-skupine__glava">
        <span className="listek-skupine__kategorija">{listek.kategorija}</span>
        {listek.turnir && <span className="listek-skupine__turnir">{listek.turnir}</span>}
        <span className="listek-skupine__znamka">
          <ZnakTurnirko velikost={13} />
          Turnirko
        </span>
      </header>

      {del.kriznaTabela ? (
        <KriznaTabela skupina={skupina} sklop={listek.sklop} ekipno={listek.ekipno} />
      ) : (
        <p className="listek-skupine__nadaljevanje">
          <strong>{skupina.naslov}</strong>
          {listek.sklop && ` · ${listek.sklop}`} · nadaljevanje razporeda · {del.del}. del od{' '}
          {del.delov}
        </p>
      )}

      {del.kola.length > 0 &&
        (listek.nacin === 'nizi' ? (
          <RazporedZNizi
            skupina={skupina}
            kola={del.kola}
            ekipno={listek.ekipno}
            oznaka={del.kriznaTabela && del.delov > 1 ? `Razpored · 1. del od ${del.delov}` : null}
          />
        ) : (
          <RazporedParov kola={del.kola} />
        ))}
    </article>
  )
}

/* ---------- Križna tabela ---------- */

/* Stolpci desno od celic: nizi, zmage, mesto. */
const SIRINA_VSOT_MM = 14 + 13 + 13
const SIRINA_STEVILKE_MM = 6
/* Ime in klub skupaj ne smeta biti ožja od tega (»Dajana Gošnjak Stojko«). */
const NAJMANJ_ZA_IME_MM = 54

/* Širine stolpcev: celice so pri majhni skupini široke (16 mm), pri veliki
   ožje, a ime ostane berljivo; klub se zoži, ko celic zmanjka prostora, in ga
   ni, kadar ga nihče nima (ekipa, igralci brez kluba). */
function sirineKrizne(clanov: number, sKlubom: boolean) {
  const zaCelice = SIRINA_VSEBINE_MM - 1 - SIRINA_STEVILKE_MM - SIRINA_VSOT_MM
  const celica = Math.min(16, (zaCelice - NAJMANJ_ZA_IME_MM) / clanov)
  const ostane = zaCelice - clanov * celica
  const klub = !sKlubom ? 0 : ostane >= 72 ? 18 : 14
  return { celica, klub, ime: ostane - klub }
}

/* Koliko znakov imena gre v stolpec pri običajni velikosti (9,5 pt, ~1,75 mm
   na znak); daljše ime se zmanjša, preden se odreže. */
function dolzinaImena(ime: string, sirina: number): '' | ' krizna__ime--dolgo' | ' krizna__ime--zelo-dolgo' {
  const gre = sirina / 1.75
  if (ime.length <= gre) return ''
  return ime.length <= gre * 1.2 ? ' krizna__ime--dolgo' : ' krizna__ime--zelo-dolgo'
}

function KriznaTabela({
  skupina,
  sklop,
  ekipno,
}: {
  skupina: TiskSkupina
  sklop: string | null
  ekipno: boolean
}) {
  const clani = skupina.clani
  const sKlubom = clani.some((clan) => clan.klub)
  const sirine = sirineKrizne(clani.length, sKlubom)
  const klub = sKlubom ? `${sirine.klub}mm ` : ''
  const stolpci = {
    '--kriz-stolpci': `${SIRINA_STEVILKE_MM}mm minmax(0, 1fr) ${klub}repeat(${clani.length}, ${sirine.celica}mm) 14mm 13mm 13mm`,
    '--kriz-clanov': clani.length,
  } as CSSProperties

  return (
    <div className="krizna" role="table" aria-label={skupina.naslov} style={stolpci}>
      <div className="krizna__vrsta krizna__vrsta--glava" role="row">
        <div
          className="krizna__naslov"
          role="columnheader"
          style={{ gridColumn: `span ${sKlubom ? 3 : 2}` }}
        >
          {sklop && <span className="krizna__sklop">{sklop}</span>}
          <span className="krizna__skupina">{skupina.naslov}</span>
        </div>
        {clani.map((clan, j) => (
          <div
            key={clan.idPrijave}
            className={'krizna__glava-celice' + (j === 0 ? ' krizna__glava-celice--prva' : '')}
            role="columnheader"
          >
            {clan.stevilka}
          </div>
        ))}
        <div className="krizna__glava-vsote krizna__glava-vsote--prva" role="columnheader">
          {ekipno ? 'Tekme' : 'Nizi'}
        </div>
        <div className="krizna__glava-vsote" role="columnheader">
          Zmage
        </div>
        <div className="krizna__glava-vsote" role="columnheader">
          Mesto
        </div>
      </div>

      {clani.map((clan, i) => {
        const izkupicek = skupina.izkupicki[i]
        return (
          <div key={clan.idPrijave} className="krizna__vrsta" role="row">
            <div className="krizna__stevilka" role="rowheader">
              {clan.stevilka}
            </div>
            <div className={'krizna__ime' + dolzinaImena(clan.ime, sirine.ime)} role="cell">
              <span className="krizna__ime-vrstica">{clan.ime}</span>
            </div>
            {sKlubom && (
              <div className="krizna__klub" role="cell">
                <span className="krizna__klub-ime">{clan.klub}</span>
              </div>
            )}
            {clani.map((_, j) =>
              i === j ? (
                <div
                  key={j}
                  className={'krizna__celica krizna__celica--sam' + (j === 0 ? ' krizna__celica--prva' : '')}
                  role="cell"
                />
              ) : (
                <Celica key={j} celica={skupina.izidi[i][j]} prva={j === 0} />
              ),
            )}
            <div className="krizna__vsota krizna__vsota--prva" role="cell">
              {izkupicek ? izkupicek.nizi : <span className="krizna__crta">/</span>}
            </div>
            <div className="krizna__vsota" role="cell">
              {izkupicek?.zmage}
            </div>
            <div className="krizna__vsota krizna__vsota--mesto" role="cell">
              {izkupicek?.mesto ?? ''}
            </div>
          </div>
        )
      })}
    </div>
  )
}

/* Celica tekme z vidika igralca v vrstici. Prazna nosi poševnico kot na
   papirju (nizi se vpišejo levo in desno od nje). */
function Celica({ celica, prva }: { celica: TiskCelica | null; prva: boolean }) {
  const razred = 'krizna__celica' + (prva ? ' krizna__celica--prva' : '')
  if (!celica) {
    return (
      <div className={razred} role="cell">
        <span className="krizna__crta">/</span>
      </div>
    )
  }
  return (
    <div
      className={razred + ' krizna__celica--izid' + (celica.zmaga ? ' krizna__celica--zmaga' : '')}
      role="cell"
    >
      <span className="krizna__izid">{celica.nizi}</span>
      {celica.oznaka && <span className="krizna__oznaka">{celica.oznaka}</span>}
    </div>
  )
}

/* ---------- Razpored z nizi ---------- */

function RazporedZNizi({
  skupina,
  kola,
  ekipno,
  oznaka,
}: {
  skupina: TiskSkupina
  kola: TiskKolo[]
  ekipno: boolean
  /* Napis nad stolpcem tekem, ko je razpored razdeljen na več listkov. */
  oznaka: string | null
}) {
  const nizov = skupina.steviloNizov
  const nizi = Array.from({ length: nizov }, (_, i) => i + 1)
  const stolpci = {
    '--seznam-stolpci': `16mm 10mm minmax(0, 1fr) ${nizov > 0 ? `repeat(${nizov}, ${nizov > 5 ? 9.5 : 11}mm) ` : ''}${nizov > 0 ? 14 : 18}mm`,
  } as CSSProperties

  return (
    <div className="seznam-skupine" role="table" aria-label="Razpored tekem" style={stolpci}>
      <div className="seznam-skupine__vrsta seznam-skupine__vrsta--glava" role="row">
        <div role="columnheader">Kolo</div>
        <div role="columnheader">Par</div>
        <div className="seznam-skupine__glava-tekme" role="columnheader">
          {oznaka ?? (ekipno ? 'Srečanje' : 'Tekma')}
        </div>
        {nizi.map((n) => (
          <div
            key={n}
            className={'seznam-skupine__glava-niza' + (n === 1 ? ' seznam-skupine__glava-niza--prva' : '')}
            role="columnheader"
          >
            {n}
          </div>
        ))}
        <div className="seznam-skupine__glava-izida" role="columnheader">
          Rezultat
        </div>
      </div>

      {kola.flatMap((kolo) =>
        kolo.tekme.map((tekma, indeks) => (
          <div
            key={tekma.id}
            className={
              'seznam-skupine__vrsta' +
              (indeks === kolo.tekme.length - 1 ? ' seznam-skupine__vrsta--konec-kola' : '')
            }
            role="row"
          >
            {indeks === 0 && (
              <div
                className="seznam-skupine__kolo"
                role="rowheader"
                style={{ gridRow: `span ${kolo.tekme.length}` }}
              >
                <span>{kolo.kolo}. kolo</span>
                {kolo.prosti.length > 0 && (
                  <span className="seznam-skupine__prost">prost {kolo.prosti.join(', ')}</span>
                )}
              </div>
            )}
            <div className="seznam-skupine__par" role="cell">
              {tekma.par[0]} – {tekma.par[1]}
            </div>
            <ImenaTekme tekma={tekma} />
            {nizi.map((n) => (
              <div
                key={n}
                className={'seznam-skupine__niz' + (n === 1 ? ' seznam-skupine__niz--prvi' : '')}
                role="cell"
              >
                {tekma.tocke[n - 1] ?? <span className="seznam-skupine__crta">:</span>}
              </div>
            ))}
            <div className="seznam-skupine__izid" role="cell">
              {tekma.izid ? (
                <>
                  <span>{tekma.izid.nizi}</span>
                  {(tekma.izid.oznaka ?? (tekma.prenesena ? 'prenesen' : null)) && (
                    <span className="seznam-skupine__oznaka">
                      {tekma.izid.oznaka ?? 'prenesen'}
                    </span>
                  )}
                </>
              ) : (
                <span className="seznam-skupine__crta">/</span>
              )}
            </div>
          </div>
        )),
      )}
    </div>
  )
}

/* »Ana Novak : Eva Kos« z dvopičjem na sredini stolpca, kot na papirju;
   zmagovalec odigrane tekme je odebeljen. */
function ImenaTekme({ tekma }: { tekma: TiskTekma }) {
  return (
    <div className="seznam-skupine__imena" role="cell">
      <span
        className={
          'seznam-skupine__ime seznam-skupine__ime--levo' +
          (tekma.zmagovalec === 1 ? ' seznam-skupine__ime--zmaga' : '')
        }
      >
        {tekma.ime1}
      </span>
      <span className="seznam-skupine__dvopicje">:</span>
      <span
        className={'seznam-skupine__ime' + (tekma.zmagovalec === 2 ? ' seznam-skupine__ime--zmaga' : '')}
      >
        {tekma.ime2}
      </span>
    </div>
  )
}

/* ---------- Razpored parov ---------- */

/* Kola v stolpcih, pari po številkah iz tabele (»1 – 8«); pri veliki skupini
   v več pasovih. Odigrana tekma nosi nize. */
function RazporedParov({ kola }: { kola: TiskKolo[] }) {
  const pasovi: TiskKolo[][] = []
  for (let i = 0; i < kola.length; i += KOL_V_PASU) pasovi.push(kola.slice(i, i + KOL_V_PASU))

  return (
    <div className="razpored-parov" aria-label="Razpored tekem">
      {pasovi.map((pas) => (
        <div
          key={pas[0].kolo}
          className="razpored-parov__pas"
          style={
            {
              '--kol': pas.length,
              '--vrstic': Math.max(...pas.map(vrsticKola)),
            } as CSSProperties
          }
        >
          {pas.map((kolo) => (
            <div key={kolo.kolo} className="razpored-parov__kolo">
              <div className="razpored-parov__naslov">{kolo.kolo}. kolo</div>
              {kolo.tekme.map((tekma) => (
                <div key={tekma.id} className="razpored-parov__par">
                  <span className="razpored-parov__stevilki">
                    {tekma.par[0]} – {tekma.par[1]}
                  </span>
                  {tekma.izid && <span className="razpored-parov__izid">{tekma.izid.nizi}</span>}
                </div>
              ))}
              {kolo.prosti.length > 0 && (
                <div className="razpored-parov__prost">prost {kolo.prosti.join(', ')}</div>
              )}
            </div>
          ))}
        </div>
      ))}
    </div>
  )
}
