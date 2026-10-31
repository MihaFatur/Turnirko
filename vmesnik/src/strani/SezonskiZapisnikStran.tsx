/* Sezonski zapisnik igralca za tisk (paket Navijač, faza 3): ena sezona na
   listu A4, kot ga starš ali trener shrani v mapo ali pokaže ob koncu sezone.
   Zasnova kot žreb za vrata dvorane: stran je zunaj skupne postavitve, tiska
   jo brskalnik (window.print), zaledje ne rabi ničesar svojega — vse je že
   javno na profilu igralca.

   Povezavo ponudita domača stran in profil računom s paketom, a sam list je
   javen kot profil: nobenega podatka ne razkrije, ki ga gledalec ne bi videl
   že na profilu. */
import { useQuery } from '@tanstack/react-query'
import { useParams, useSearchParams } from 'react-router-dom'

import { profiliApi } from '../api/zahteve'
import { ZrebOkvir } from '../komponente/ZrebOkvir'
import { ZnakTurnirko } from '../komponente/Postavitev'
import {
  oblikujMesto,
  oblikujSpremembo,
  sestaviSezonskiZapisnik,
  sezoneIgralca,
  tekemBrezDatuma,
  type SezonskiZapisnik,
} from '../pomozno/sezonskiZapisnik'
import { OZNAKE_IZIDA } from '../pomozno/zrebSkupin'
import { oblikujDanMesec, oblikujDatum, sklonTekem } from '../pomozno/oblikovanje'

export function SezonskiZapisnikStran() {
  const { id } = useParams()
  const idIgralec = Number(id)
  const [iskalniNiz, nastaviIskalniNiz] = useSearchParams()

  const profil = useQuery({
    queryKey: ['profil', idIgralec],
    queryFn: () => profiliApi.profil(idIgralec),
    enabled: !Number.isNaN(idIgralec),
  })

  const sezone = profil.data ? sezoneIgralca(profil.data) : []
  const zahtevana = iskalniNiz.get('sezona')
  /* Neznana sezona v naslovu pade na najnovejšo: povezava ne sme voditi na prazen list. */
  const sezona = zahtevana && sezone.includes(zahtevana) ? zahtevana : (sezone[0] ?? null)
  const zapisnik = profil.data && sezona ? sestaviSezonskiZapisnik(profil.data, sezona) : null
  const brezDatuma = profil.data ? tekemBrezDatuma(profil.data) : 0
  const polnoIme = profil.data?.glava.polnoIme ?? null

  const orodja = (
    <label className="listki-orodja__izbira listki-orodja__izbira--zraven">
      Sezona:
      <select
        value={sezona ?? ''}
        disabled={sezone.length === 0}
        onChange={(d) => nastaviIskalniNiz({ sezona: d.target.value }, { replace: true })}
      >
        {sezone.map((s) => (
          <option key={s} value={s}>
            {s}
          </option>
        ))}
      </select>
    </label>
  )

  return (
    <ZrebOkvir
      nazaj={`/igralci/${idIgralec}/profil`}
      nazajOznaka="← Nazaj na profil"
      naslov={polnoIme && sezona ? `Sezonski zapisnik ${sezona} · ${polnoIme}` : null}
      nalaganje={profil.isPending}
      napaka={profil.error}
      stevec={zapisnik ? `${zapisnik.tekem} ${sklonTekem(zapisnik.tekem)}` : '0 tekem'}
      steviloZaTisk={zapisnik?.tekem ?? 0}
      prazno="Igralec nima odigranih tekem z znanim datumom, zato sezonskega zapisnika ni."
      razred="zreb-stran--zapisnik"
      orodja={orodja}
    >
      {zapisnik && polnoIme && (
        <ListZapisnika zapisnik={zapisnik} polnoIme={polnoIme} klub={profil.data?.glava.klub ?? null}
                       brezDatuma={brezDatuma} />
      )}
    </ZrebOkvir>
  )
}

function ListZapisnika({
  zapisnik,
  polnoIme,
  klub,
  brezDatuma,
}: {
  zapisnik: SezonskiZapisnik
  polnoIme: string
  klub: string | null
  brezDatuma: number
}) {
  const z = zapisnik
  const sprememba = z.ratingZacetek !== null && z.ratingKonec !== null ? z.ratingKonec - z.ratingZacetek : null
  return (
    <article className="sezonski-zapisnik">
      <header className="sezonski-zapisnik__glava">
        <span className="sezonski-zapisnik__znamka">
          <ZnakTurnirko velikost={24} />
          Turnirko
        </span>
        <span className="sezonski-zapisnik__vrsta">Sezonski zapisnik · {z.sezona}</span>
      </header>

      <h1 className="sezonski-zapisnik__ime">{polnoIme}</h1>
      <p className="sezonski-zapisnik__klub">{klub ?? 'brez kluba'}</p>

      <dl className="sezonski-zapisnik__kolofon">
        <Podatek oznaka="Tekem" vrednost={String(z.tekem)} />
        <Podatek
          oznaka="Zmage – porazi"
          vrednost={`${z.zmag} – ${z.porazov}`}
          pod={z.odstotekZmag !== null ? `${z.odstotekZmag} % zmag` : undefined}
        />
        <Podatek oznaka="Nizi" vrednost={`${z.dobljeniNizi} : ${z.prejetiNizi}`} pod="dobljeni : prejeti" />
        <Podatek
          oznaka="Rating v sezoni"
          vrednost={
            z.ratingZacetek !== null && z.ratingKonec !== null ? `${z.ratingZacetek} → ${z.ratingKonec}` : '—'
          }
          pod={sprememba !== null ? oblikujSpremembo(sprememba) : undefined}
        />
        <Podatek oznaka="Najvišji" vrednost={z.ratingNajvisji !== null ? String(z.ratingNajvisji) : '—'} />
        <Podatek oznaka="Najnižji" vrednost={z.ratingNajnizji !== null ? String(z.ratingNajnizji) : '—'} />
      </dl>

      {z.potRatinga.length >= 2 && <GrafSezone vrednosti={z.potRatinga} />}

      <h2 className="sezonski-zapisnik__naslov">Tekmovanja</h2>
      <table className="sezonski-zapisnik__tabela">
        <thead>
          <tr>
            <th>Datum</th>
            <th>Tekmovanje</th>
            <th className="sezonski-zapisnik__st">Z – P</th>
            <th className="sezonski-zapisnik__mesto">Mesto</th>
            <th className="sezonski-zapisnik__st">Rating</th>
          </tr>
        </thead>
        <tbody>
          {z.tekmovanja.map((t) => (
            <tr key={t.kljuc}>
              <td className="sezonski-zapisnik__mono">{oblikujDanMesec(t.datum)}</td>
              <td>
                {t.ime}
                {t.del && <span className="sezonski-zapisnik__del"> · {t.del}</span>}
              </td>
              <td className="sezonski-zapisnik__st sezonski-zapisnik__mono">
                {t.zmage} – {t.porazi}
              </td>
              <td className="sezonski-zapisnik__mono sezonski-zapisnik__mesto">{oblikujMesto(t)}</td>
              <td className="sezonski-zapisnik__st sezonski-zapisnik__mono">{oblikujSpremembo(t.sprememba)}</td>
            </tr>
          ))}
        </tbody>
      </table>

      <h2 className="sezonski-zapisnik__naslov">Tekme</h2>
      <table className="sezonski-zapisnik__tabela">
        <thead>
          <tr>
            <th>Datum</th>
            <th>Nasprotnik</th>
            <th>Tekmovanje</th>
            <th className="sezonski-zapisnik__st">Izid</th>
            <th className="sezonski-zapisnik__st">Rating</th>
          </tr>
        </thead>
        <tbody>
          {z.tekme.map((t) => {
            const oznaka = (t.izidTip && OZNAKE_IZIDA[t.izidTip]) || null
            return (
              <tr key={`${t.ligaska ? 'l' : 't'}${t.idTekme}`}>
                <td className="sezonski-zapisnik__mono">{oblikujDanMesec(t.datum)}</td>
                <td>{t.nasprotnik}</td>
                <td className="sezonski-zapisnik__pod">{t.tekmovanje}</td>
                <td className="sezonski-zapisnik__st sezonski-zapisnik__mono">
                  <strong>{t.zmaga ? 'Z' : 'P'}</strong> {t.niziZa}:{t.niziProti}
                  {oznaka && <span className="sezonski-zapisnik__del"> {oznaka}</span>}
                </td>
                <td className="sezonski-zapisnik__st sezonski-zapisnik__mono">{oblikujSpremembo(t.spremembaRatinga)}</td>
              </tr>
            )
          })}
        </tbody>
      </table>

      <footer className="sezonski-zapisnik__noga">
        <span>Vir: javni profil igralca na Turnirku · Turnirko rating · natisnjeno {oblikujDatum(danes())}</span>
        {brezDatuma > 0 && (
          <span>
            Zunaj sezon: {brezDatuma} {sklonTekem(brezDatuma)} brez znanega datuma.
          </span>
        )}
      </footer>
    </article>
  )
}

function danes(): string {
  const d = new Date()
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

function Podatek({ oznaka, vrednost, pod }: { oznaka: string; vrednost: string; pod?: string }) {
  return (
    <div className="sezonski-zapisnik__podatek">
      <dt>{oznaka}</dt>
      <dd>{vrednost}</dd>
      {pod && <span className="sezonski-zapisnik__pod">{pod}</span>}
    </div>
  )
}

/* Črta ratinga čez sezono. Os je zaporedje sprememb in ne koledar (isto kot
   graf na profilu): klub vnese celo kolo naenkrat. Brez barve, da je list
   enak na črno-belem tiskalniku. */
function GrafSezone({ vrednosti }: { vrednosti: number[] }) {
  const sirina = 720
  const visina = 110
  const rob = 6
  const najmanj = Math.min(...vrednosti)
  const najvec = Math.max(...vrednosti)
  const razpon = Math.max(najvec - najmanj, 1)
  const x = (i: number) => rob + (i / (vrednosti.length - 1)) * (sirina - 2 * rob)
  const y = (v: number) => visina - rob - ((v - najmanj) / razpon) * (visina - 2 * rob)
  const tocke = vrednosti.map((v, i) => `${x(i).toFixed(1)},${y(v).toFixed(1)}`).join(' ')
  return (
    <figure className="sezonski-zapisnik__graf">
      <svg viewBox={`0 0 ${sirina} ${visina}`} role="img" aria-label="Rating čez sezono">
        <line x1={rob} x2={sirina - rob} y1={y(najvec)} y2={y(najvec)} className="sezonski-zapisnik__mrezna" />
        <line x1={rob} x2={sirina - rob} y1={y(najmanj)} y2={y(najmanj)} className="sezonski-zapisnik__mrezna" />
        <polyline points={tocke} className="sezonski-zapisnik__crta" fill="none" />
        <circle cx={x(vrednosti.length - 1)} cy={y(vrednosti[vrednosti.length - 1])} r={3.5}
                className="sezonski-zapisnik__konec" />
      </svg>
      <figcaption>
        <span>{najvec}</span>
        <span>{najmanj}</span>
      </figcaption>
    </figure>
  )
}
