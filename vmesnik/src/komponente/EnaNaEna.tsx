/* Pripomoček "Ena na ena": medsebojni izid dveh igralcev prek vseh tekmovanj —
   turnirskih tekem in posamičnih tekem ligaških srečanj (dvojice ne štejejo).

   Zgradba je simetrična kot semafor: levo in desno stran igralca (ime, izbirnik
   in vrstica s klubom, mestom in ratingom), na sredini pa med navpičnima
   črtama medsebojni izid, seznam zadnjih tekem in gumb za nov naključni par.
   Ob prihodu se izžreba naključni par.

   Uporablja se na domači strani (s tremi zadnjimi tekmami) in na strani
   dvoboja (tam sta pod semaforjem še razmerje in vse tekme). Viden vsem. */
import { useEffect, useMemo, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'

import { igralciApi, statistikaApi } from '../api/zahteve'
import type { DvobojDto, IgralecDto } from '../api/tipi'
import { OZNAKE_IZID } from '../api/tipi'
import { oblikujDanKratekMesec, sklonTekem } from '../pomozno/oblikovanje'
import { SporociloNapake } from './SporociloNapake'
import { SpremembaElo } from './SpremembaElo'

/* Koliko medsebojnih tekem pokaže strnjena različica. Več jih vrstica ne
   prenese - do ostalih vodi povezava pod seznamom. */
const TEKEM_V_POVZETKU = 3

interface Lastnosti {
  /* Ali pod semaforjem pokaži razmerje in tabelo vseh medsebojnih tekem.
     Brez tega je pripomoček strnjen (domača stran). */
  pokaziZgodovino?: boolean
}

export function EnaNaEna({ pokaziZgodovino = false }: Lastnosti) {
  const igralci = useQuery({ queryKey: ['igralci'], queryFn: igralciApi.seznam })
  /* Lestvico stran ob sebi večinoma že ima (domača stran, stran lestvice),
     zato je to praviloma zadetek v predpomnilniku in ne nov klic. */
  const lestvica = useQuery({ queryKey: ['lestvica'], queryFn: statistikaApi.lestvica })

  /* Če sta igralca podana v naslovu (klik "Vseh N tekem" z domače strani
     odpre /dvoboj?prvi=1&drugi=5), začni z njima; sicer se izžreba naključni par. */
  const [iskalniParametri] = useSearchParams()
  const [prvi, nastaviPrvega] = useState<number | ''>(() =>
    steviloIzParametra(iskalniParametri.get('prvi')),
  )
  const [drugi, nastaviDrugega] = useState<number | ''>(() =>
    steviloIzParametra(iskalniParametri.get('drugi')),
  )

  /* Če igralca nista prišla iz naslova, ob prvem nalaganju izberi naključni par. */
  useEffect(() => {
    const seznam = igralci.data
    if (!seznam || seznam.length < 2 || prvi !== '' || drugi !== '') return
    const [a, b] = dvaNakljucna(seznam)
    nastaviPrvega(a.id)
    nastaviDrugega(b.id)
  }, [igralci.data, prvi, drugi])

  const veljavniPar = prvi !== '' && drugi !== '' && prvi !== drugi

  const dvoboj = useQuery({
    queryKey: ['dvoboj', prvi, drugi],
    queryFn: () => statistikaApi.dvoboj(Number(prvi), Number(drugi)),
    enabled: veljavniPar,
  })

  /* Mesto na lestvici je edini podatek strani, ki ga igralec sam ne nosi. */
  const mesta = useMemo(() => {
    const zemljevid = new Map<number, number>()
    ;(lestvica.data ?? []).forEach((v, indeks) => zemljevid.set(v.idIgralca, indeks + 1))
    return zemljevid
  }, [lestvica.data])

  function izzrebaj() {
    if (!igralci.data || igralci.data.length < 2) return
    const [a, b] = dvaNakljucna(igralci.data)
    nastaviPrvega(a.id)
    nastaviDrugega(b.id)
  }

  const premalo = (igralci.data?.length ?? 0) < 2
  const d = veljavniPar ? dvoboj.data : undefined
  const seznam = igralci.data ?? []

  return (
    <div className={'enanaena' + (pokaziZgodovino ? '' : ' enanaena--strnjen')}>
      <SporociloNapake napaka={igralci.error} />
      {veljavniPar && <SporociloNapake napaka={dvoboj.error} />}

      <div className="enanaena__plosca">
        <StranIgralca
          polozaj="enanaena__stran--1"
          oznaka="Igralec A"
          kotNaslov={pokaziZgodovino}
          igralci={seznam}
          vrednost={prvi}
          izkljuci={drugi}
          mesto={prvi === '' ? undefined : mesta.get(Number(prvi))}
          naSpremembo={nastaviPrvega}
        />

        {/* Sredina je razdeljena na dvoje, ker se na telefonu razide: izid
            ostane med imenoma, seznam tekem in gumb pa se preselita pod
            semafor, kjer imata celo širino. */}
        <div className="enanaena__izid">
          <div className="enanaena__oznaka enanaena__oznaka--siroko">
            Medsebojno · vsa tekmovanja
          </div>
          <div className="enanaena__stevilo">
            <span className={barvaIzida(d?.zmagePrvega, d?.zmageDrugega)}>
              {d ? d.zmagePrvega : '–'}
            </span>
            <span className="enanaena__crtica">:</span>
            <span className={barvaIzida(d?.zmageDrugega, d?.zmagePrvega)}>
              {d ? d.zmageDrugega : '–'}
            </span>
          </div>
          <div className="enanaena__skupaj">
            {!d ? (
              'nalaganje …'
            ) : d.odigrane === 0 ? (
              'še nista igrala'
            ) : (
              <>
                {d.odigrane} {sklonTekem(d.odigrane)}
                <span className="enanaena__nizi">
                  {' '}
                  · {d.niziPrvega} : {d.niziDrugega} v nizih
                </span>
              </>
            )}
          </div>
        </div>

        <div className="enanaena__spodaj">
          {/* Strnjena različica nosi tudi zadnje tekme: brez njih je izid
              številka brez zgodovine, cela tabela pa je na strani dvoboja. */}
          {!pokaziZgodovino && d && d.tekme.length > 0 && (
            <>
              <div className="enanaena__zadnje">
                {d.tekme.slice(0, TEKEM_V_POVZETKU).map((t) => (
                  <div className="enanaena__zadnja" key={(t.ligaska ? 'l' : 't') + t.idTekme}>
                    <span>
                      {t.datum && `${oblikujDanKratekMesec(t.datum)} · `}
                      {t.tekmovanje}
                    </span>
                    <span className={t.zmagalPrvi ? 'profil__zmaga' : 'profil__poraz'}>
                      {t.niziPrvega}:{t.niziDrugega}{' '}
                      {zacetnica(t.zmagalPrvi ? d.prvi.priimek : d.drugi.priimek)}
                    </span>
                  </div>
                ))}
              </div>
              <Link to={`/dvoboj?prvi=${prvi}&drugi=${drugi}`} className="enanaena__podrobno">
                Vseh {d.odigrane} {sklonTekem(d.odigrane)} →
              </Link>
            </>
          )}

          {/* Naključni par je pripomoček za raziskovanje in ne glavno dejanje
              pogleda, zato stoji pod črto na dnu sredinskega stolpca. */}
          <div className="enanaena__noga">
            <span className="enanaena__oznaka">Naključni par</span>
            <button
              className="gumb gumb--majhen enanaena__zreb"
              onClick={izzrebaj}
              disabled={premalo}
            >
              Zamenjaj par
            </button>
          </div>
        </div>

        <StranIgralca
          polozaj="enanaena__stran--2"
          oznaka="Igralec B"
          kotNaslov={pokaziZgodovino}
          igralci={seznam}
          vrednost={drugi}
          izkljuci={prvi}
          mesto={drugi === '' ? undefined : mesta.get(Number(drugi))}
          naSpremembo={nastaviDrugega}
        />
      </div>

      {prvi !== '' && drugi !== '' && prvi === drugi && (
        <p className="napaka">Izberi dva različna igralca.</p>
      )}

      {igralci.data && premalo && (
        <p className="obvestilo">Za primerjavo sta potrebna vsaj dva igralca.</p>
      )}

      {pokaziZgodovino && d && d.tekme.length > 0 && (
        <>
          <Razmerje dvoboj={d} />
          <Zgodovina dvoboj={d} />
        </>
      )}
    </div>
  )
}

/* Ena stran semaforja: znak, veliko ime, izbirnik in vrstica s klubom,
   mestom na lestvici in ratingom. Podatki pridejo iz šifranta igralcev, da
   stolpec stoji tudi, dokler se medsebojni izid še nalaga. */
function StranIgralca({
  polozaj,
  oznaka,
  kotNaslov,
  igralci,
  vrednost,
  izkljuci,
  mesto,
  naSpremembo,
}: {
  polozaj: string
  oznaka: string
  /* Na strani dvoboja imeni nadomeščata naslov strani (maketa naslova nima),
     zato sta tam h2. Na domači strani je naslov sklopa "Ena na ena", ime pa
     je le oznaka strani - drugi h2 bi bralniku zaslona lagal o zgradbi. */
  kotNaslov: boolean
  igralci: IgralecDto[]
  vrednost: number | ''
  izkljuci: number | ''
  mesto: number | undefined
  naSpremembo: (id: number | '') => void
}) {
  const igralec = vrednost === '' ? undefined : igralci.find((i) => i.id === vrednost)
  const Ime = kotNaslov ? 'h2' : 'div'

  return (
    <div className={'enanaena__stran ' + polozaj}>
      <ZnakIgralca />
      <Ime className="enanaena__ime">
        {igralec ? (
          <Link to={`/igralci/${igralec.id}/profil`}>
            {igralec.ime} {igralec.priimek}
          </Link>
        ) : (
          '—'
        )}
      </Ime>
      <label className="enanaena__polje">
        <span className="samo-za-bralnik">{oznaka}</span>
        <select
          value={vrednost}
          onChange={(d) => naSpremembo(d.target.value === '' ? '' : Number(d.target.value))}
        >
          <option value="">— izberi —</option>
          {igralci
            .filter((i) => i.id !== izkljuci)
            .map((i) => (
              <option key={i.id} value={i.id}>
                {i.priimek} {i.ime}
              </option>
            ))}
        </select>
      </label>
      <div className="enanaena__meta">
        {/* Na telefonu od te vrstice ostane samo rating: klub in mesto sta v
            96 px širokem stolpcu tri vrstice besedila. */}
        <span className="enanaena__meta--siroko">{predRatingom(igralec, mesto)}</span>
        {igralec && (igralec.rating !== null ? igralec.rating : '—')}
      </div>
    </div>
  )
}

/* Del vrstice pred ratingom: "NTK Ljubljana · 4. mesto · rating ".
   Kar manjka, tiho odpade. */
function predRatingom(igralec: IgralecDto | undefined, mesto: number | undefined): string {
  if (!igralec) return ''
  const deli: string[] = []
  if (igralec.klub) deli.push(igralec.klub.ime)
  if (mesto !== undefined) deli.push(`${mesto}. mesto`)
  return deli.length > 0 ? `${deli.join(' · ')} · rating ` : 'rating '
}

/* Znak igralca nad imenom: obris glave in ramen. Ni ikona namesto besede -
   ime stoji tik pod njim - ampak oznaka strani semaforja, ki drži simetrijo
   levo/desno tudi takrat, ko sta imeni različno dolgi. */
function ZnakIgralca() {
  return (
    <svg className="enanaena__znak" width="40" height="40" viewBox="0 0 40 40" aria-hidden="true">
      <circle cx="20" cy="13" r="7.5" />
      <path d="M6 36c0-7.7 6.3-14 14-14s14 6.3 14 14" />
    </svg>
  )
}

/* Razmerje moči: štiri številke in dvobarvna palica deleža zmag. */
function Razmerje({ dvoboj }: { dvoboj: DvobojDto }) {
  const { prvi, drugi, odigrane, zmagePrvega, zmageDrugega, niziPrvega, niziDrugega } = dvoboj

  /* Tekma z razliko enega samega niza se je odločila šele v zadnjem nizu —
     ločenega podatka o odločilnem nizu strežnik ne pošilja. */
  const odlocilni = dvoboj.tekme.filter(
    (t) => Math.abs(t.niziPrvega - t.niziDrugega) === 1,
  ).length

  const delezPrvega = odigrane > 0 ? Math.round((zmagePrvega / odigrane) * 100) : 0

  return (
    <div>
      <div className="naslovna-vrstica">
        <h2>Razmerje</h2>
        <span className="sekcija__meta">Vsa tekmovanja</span>
      </div>

      <div className="profil__kazalniki">
        <div className="kazalnik">
          <div className={'kazalnik__vrednost ' + barvaIzida(zmagePrvega, zmageDrugega)}>
            {zmagePrvega}
          </div>
          <div className="kazalnik__oznaka">Zmage · {prvi.polnoIme}</div>
        </div>
        <div className="kazalnik">
          <div className={'kazalnik__vrednost ' + barvaIzida(zmageDrugega, zmagePrvega)}>
            {zmageDrugega}
          </div>
          <div className="kazalnik__oznaka">Zmage · {drugi.polnoIme}</div>
        </div>
        <div className="kazalnik">
          <div className="kazalnik__vrednost">
            {niziPrvega} : {niziDrugega}
          </div>
          <div className="kazalnik__oznaka">Nizi</div>
        </div>
        <div className="kazalnik">
          <div className="kazalnik__vrednost">{odlocilni}</div>
          <div className="kazalnik__oznaka">V odločilnem nizu</div>
        </div>
      </div>

      {odigrane > 0 && (
        <>
          <div className="razmerje-palica">
            <span className="razmerje-palica__z" style={{ width: `${delezPrvega}%` }} />
            <span className="razmerje-palica__p" style={{ width: `${100 - delezPrvega}%` }} />
          </div>
          <div className="razmerje-legenda">
            <span>
              {prvi.polnoIme} {delezPrvega} %
            </span>
            <span>
              {drugi.polnoIme} {100 - delezPrvega} %
            </span>
          </div>
        </>
      )}
    </div>
  )
}

function Zgodovina({ dvoboj }: { dvoboj: DvobojDto }) {
  const { prvi, drugi } = dvoboj
  return (
    <div>
      <div className="naslovna-vrstica">
        <h2>Odigrane tekme</h2>
        <span className="sekcija__meta">
          {dvoboj.tekme.length} {sklonTekem(dvoboj.tekme.length)}
        </span>
      </div>

      {/* Vrstice namesto tabele - tako je v maketi in tako se bere: pri
          dvoboju sta igralca ves cas ista, zato stolpec z imenom ni merilo
          za primerjavo, ampak sam izid. Ta zato dobi velikost, ki jo je
          mogoce prebrati z razdalje, glave tabele pa odpadejo.
          Obstojeci .tekme-seznam__vrstica ne pride v postev - ta je oblike
          "igralec | izid | igralec", tu pa sta strani ze znani. */}
      <ol className="dvoboj-tekme">
        {dvoboj.tekme.map((t) => (
          <li className="dvoboj-tekma" key={(t.ligaska ? 'l' : 't') + t.idTekme}>
            <span className="enanaena__vir">{t.ligaska ? 'liga' : 'turnir'}</span>

            <span className="dvoboj-tekma__kaj">
              {t.tekmovanje}
              <span className="profil__del">{t.del}</span>
            </span>

            <span
              className={
                'dvoboj-tekma__izid ' + (t.zmagalPrvi ? 'profil__zmaga' : 'profil__poraz')
              }
            >
              {t.niziPrvega}:{t.niziDrugega}
              {t.izidTip && t.izidTip !== 'IGRANO' && (
                <span className="enanaena__posebni"> ({OZNAKE_IZID[t.izidTip]})</span>
              )}
              {/* Sprememba ELO stoji pod izidom, ker je njegova posledica -
                  pod zmagovalcem bi vrstico po nepotrebnem podvojila. */}
              <span className="dvoboj-tekma__elo">
                <span className="samo-za-bralnik">Sprememba ELO: </span>
                <SpremembaElo vrednost={t.spremembaPrvega} />
                <span className="enanaena__elo-locilo">/</span>
                <SpremembaElo vrednost={t.spremembaDrugega} />
              </span>
            </span>

            <span
              className={
                'dvoboj-tekma__zmagovalec '
                + (t.zmagalPrvi ? 'profil__zmaga' : 'profil__poraz')
              }
            >
              {t.zmagalPrvi ? prvi.polnoIme : drugi.polnoIme}
            </span>
          </li>
        ))}
      </ol>
    </div>
  )
}

/* Zelena za vodilno stran, rjasta za zaostajajočo, brez barve ob izenačenju
   in dokler izida še ni. */
function barvaIzida(svoje: number | undefined, tuje: number | undefined): string {
  if (svoje === undefined || tuje === undefined) return ''
  if (svoje > tuje) return 'enanaena__vodi'
  if (svoje < tuje) return 'enanaena__izgublja'
  return ''
}

/* Začetnica priimka zmagovalca ob izidu ("3:1 V"). */
function zacetnica(priimek: string): string {
  return priimek.slice(0, 1).toUpperCase()
}

/* Pretvori naslovni parameter (npr. ?prvi=5) v veljaven id igralca ali v prazno. */
function steviloIzParametra(v: string | null): number | '' {
  if (v === null) return ''
  const n = Number(v)
  return Number.isInteger(n) && n > 0 ? n : ''
}

/* Dva različna naključna igralca s seznama. */
function dvaNakljucna(seznam: IgralecDto[]): [IgralecDto, IgralecDto] {
  const a = Math.floor(Math.random() * seznam.length)
  let b = Math.floor(Math.random() * seznam.length)
  while (b === a) b = Math.floor(Math.random() * seznam.length)
  return [seznam[a], seznam[b]]
}
