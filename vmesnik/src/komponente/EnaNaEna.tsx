/* Pripomoček "Ena na ena": medsebojni izid dveh igralcev prek vseh tekmovanj —
   turnirskih tekem in posamičnih tekem ligaških srečanj (dvojice ne štejejo).

   Zgradba je simetrična kot semafor: levo in desno stran igralca (ime, iskalno
   polje in vrstica s klubom, mestom in ratingom), na sredini pa med navpičnima
   črtama medsebojni izid, seznam zadnjih tekem in gumb za nov naključni par.
   Ob prihodu se izžreba naključni par.

   Uporablja se na domači strani (s tremi zadnjimi tekmami) in na strani
   dvoboja (tam sta pod semaforjem še razmerje in vse tekme). Viden vsem. */
import { useCallback, useEffect, useId, useMemo, useRef, useState, type KeyboardEvent } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'

import { igralciApi, statistikaApi } from '../api/zahteve'
import type { DvobojDto, IgralecDto } from '../api/tipi'
import { OZNAKE_IZID } from '../api/tipi'
import { besedeIskanja, ustrezaBesedam } from '../pomozno/iskanje'
import { oblikujDanKratekMesec, sklonTekem } from '../pomozno/oblikovanje'
import { SporociloNapake } from './SporociloNapake'
import { SpremembaElo } from './SpremembaElo'

/* Koliko medsebojnih tekem pokaže strnjena različica. Več jih vrstica ne
   prenese - do ostalih vodi povezava pod seznamom. */
const TEKEM_V_POVZETKU = 3

/* Koliko predlogov pokaže iskalno polje igralca. Osem je toliko, kolikor jih
   na telefonu gre na zaslon, ne da bi seznam sam po sebi drsel. */
const NAJVEC_PREDLOGOV = 8

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

  /* Naključni par izbere strežnik, ker samo ta ve, kdo je s kom že igral -
     par brez medsebojne tekme pokaže 0 : 0 in o igralcih ne pove ničesar.

     Žreb teče MIMO TanStack Queryja, v navadnem stanju komponente. Poizvedba
     ne pride v poštev - predpomnjena bi ob vsakem kliku vrnila isti par -
     useMutation pa se pri žrebu ob priklopu zlomi: StrictMode učinke podvoji
     (naročnina → odjava → naročnina), ob odjavi se opazovalec odklopi od
     tekoče mutacije in se nazaj NE pripne. Odgovor tako še pride (par se
     zamenja), stanje opazovalca pa za vedno obtiči na "isPending" in gumb
     ostane onemogočen. Zadene prav VRNITEV na domačo stran, kjer je seznam
     igralcev že v predpomnilniku in žreb steče že v prvem učinku. */
  const [zrebTece, nastaviZrebTece] = useState(false)
  const [napakaZreba, nastaviNapakoZreba] = useState<unknown>(null)

  const izzrebaj = useCallback(async () => {
    nastaviZrebTece(true)
    nastaviNapakoZreba(null)
    try {
      const par = await statistikaApi.nakljucniPar()
      nastaviPrvega(par.prvi)
      nastaviDrugega(par.drugi)
    } catch (napaka) {
      nastaviNapakoZreba(napaka)
    } finally {
      nastaviZrebTece(false)
    }
  }, [])

  /* Če igralca nista prišla iz naslova, ob prvem nalaganju izžrebaj par.
     Zastavica varuje pred drugim žrebom: seznam igralcev se lahko osveži,
     obiskovalčeva izbira pa se ob tem ne sme povoziti. */
  const zeIzzrebano = useRef(false)
  useEffect(() => {
    if (zeIzzrebano.current || prvi !== '' || drugi !== '') return
    if ((igralci.data?.length ?? 0) < 2) return
    zeIzzrebano.current = true
    void izzrebaj()
  }, [igralci.data, prvi, drugi, izzrebaj])

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

  const premalo = (igralci.data?.length ?? 0) < 2
  const d = veljavniPar ? dvoboj.data : undefined
  const seznam = igralci.data ?? []

  return (
    <div className={'enanaena' + (pokaziZgodovino ? '' : ' enanaena--strnjen')}>
      <SporociloNapake napaka={igralci.error} />
      {veljavniPar && <SporociloNapake napaka={dvoboj.error} />}
      <SporociloNapake napaka={napakaZreba} />

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
          {/* Na domači strani oznake nad izidom ni: sklop ima naslov »Ena na
              ena«, pod številko pa piše, koliko tekem in kakšni nizi so za
              njo - vrstica verzalk je le še eno drobno besedilo nad velikim
              rezultatom. Na strani dvoboja naslova sklopa ni, zato tam ostane. */}
          {pokaziZgodovino && (
            <div className="enanaena__oznaka enanaena__oznaka--siroko">
              Medsebojno · vsa tekmovanja
            </div>
          )}
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
              pogleda, zato stoji pod črto na dnu sredinskega stolpca. Napis na
              gumbu pove vse - oznaka nad njim je na domači strani odveč. */}
          <div className="enanaena__noga">
            {pokaziZgodovino && <span className="enanaena__oznaka">Naključni par</span>}
            <button
              className="gumb gumb--majhen enanaena__zreb"
              onClick={() => void izzrebaj()}
              disabled={premalo || zrebTece}
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

/* Ena stran semaforja: znak, veliko ime, iskalno polje in vrstica s klubom,
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
  naSpremembo: (id: number) => void
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
      <IzbirnikIgralca
        oznaka={oznaka}
        igralci={igralci}
        izkljuci={izkljuci}
        naSpremembo={naSpremembo}
      />
      <div className="enanaena__meta">
        {/* Na telefonu od te vrstice ostane samo rating: klub in mesto sta v
            96 px širokem stolpcu tri vrstice besedila. */}
        <span className="enanaena__meta--siroko">{predRatingom(igralec, mesto)}</span>
        {igralec && (igralec.rating !== null ? igralec.rating : '—')}
      </div>
    </div>
  )
}

/* Iskalno polje s predlogi (combobox) namesto spustnega seznama: po uvozu
   zgodovine NTZS je v šifrantu več tisoč igralcev in seznama ni bilo mogoče
   prevrteti do imena. Vpiše se del imena, pod poljem pa se izpišejo zadetki.

   Ujemanje teče po besedah in ne po začetku niza: »miha« najde vse Mihe (tudi
   po imenu, ne le po priimku), »novak ana« pa Novak Ano ne glede na vrstni
   red vpisanega. Ob imenu stoji klub - brez njega soimenjakov ni mogoče
   ločiti, teh pa je v šifrantu cele države precej.

   Tipkovnica: gor/dol izbira med predlogi, Enter potrdi, Escape zapre.
   Fokus ves čas ostane v polju (vzorec combobox), zato predlogi niso gumbi,
   ampak postavke, na katere kaže aria-activedescendant. */
function IzbirnikIgralca({
  oznaka,
  igralci,
  izkljuci,
  naSpremembo,
}: {
  oznaka: string
  igralci: IgralecDto[]
  /* Igralec z druge strani semaforja: sam s sabo se nihče ne primerja. */
  izkljuci: number | ''
  naSpremembo: (id: number) => void
}) {
  const [iskanje, nastaviIskanje] = useState('')
  const [odprt, nastaviOdprt] = useState(false)
  const [oznacen, nastaviOznacen] = useState(0)
  const ovoj = useRef<HTMLDivElement>(null)
  const idSeznama = useId()

  const zadetki = useMemo(() => {
    const besede = besedeIskanja(iskanje)
    if (besede.length === 0) return []
    return igralci
      .filter((i) => i.id !== izkljuci)
      .filter((i) => ustrezaBesedam(`${i.ime} ${i.priimek}`, besede))
      .slice(0, NAJVEC_PREDLOGOV)
  }, [igralci, izkljuci, iskanje])

  /* Zapre se ob kliku zunaj in ob Escape - isto kot meni dejanj. Zapiranje ob
     izgubi fokusa (blur) ne pride v poštev: sprožilo bi se PRED klikom na
     predlog in ta klik bi padel v prazno. */
  useEffect(() => {
    if (!odprt) return
    function obKliku(dogodek: MouseEvent) {
      if (ovoj.current && !ovoj.current.contains(dogodek.target as Node)) nastaviOdprt(false)
    }
    function obTipki(dogodek: globalThis.KeyboardEvent) {
      if (dogodek.key === 'Escape') nastaviOdprt(false)
    }
    document.addEventListener('mousedown', obKliku)
    document.addEventListener('keydown', obTipki)
    return () => {
      document.removeEventListener('mousedown', obKliku)
      document.removeEventListener('keydown', obTipki)
    }
  }, [odprt])

  function izberi(id: number) {
    naSpremembo(id)
    /* Polje se izprazni: izbranega igralca nosi veliko ime nad njim, polje pa
       je iskalnik za naslednjo zamenjavo. */
    nastaviIskanje('')
    nastaviOdprt(false)
    nastaviOznacen(0)
  }

  function obTipki(dogodek: KeyboardEvent<HTMLInputElement>) {
    if (dogodek.key === 'ArrowDown' || dogodek.key === 'ArrowUp') {
      if (zadetki.length === 0) return
      dogodek.preventDefault()
      nastaviOdprt(true)
      nastaviOznacen((prej) => {
        const naslednji = dogodek.key === 'ArrowDown' ? prej + 1 : prej - 1
        return (naslednji + zadetki.length) % zadetki.length
      })
      return
    }
    if (dogodek.key === 'Enter' && odprt && zadetki[oznacen]) {
      // brez tega bi Enter poslal obrazec, v katerem polje morda stoji
      dogodek.preventDefault()
      izberi(zadetki[oznacen].id)
    }
  }

  const iscemo = odprt && iskanje.trim() !== ''

  return (
    <div className="enanaena__polje" ref={ovoj}>
      <label>
        <span className="samo-za-bralnik">{oznaka}</span>
        <input
          type="text"
          role="combobox"
          autoComplete="off"
          placeholder="Vpiši ime"
          aria-expanded={iscemo && zadetki.length > 0}
          aria-controls={idSeznama}
          aria-autocomplete="list"
          aria-activedescendant={
            iscemo && zadetki[oznacen] ? `${idSeznama}-${oznacen}` : undefined
          }
          value={iskanje}
          onChange={(dogodek) => {
            nastaviIskanje(dogodek.target.value)
            nastaviOznacen(0)
            nastaviOdprt(true)
          }}
          onFocus={() => nastaviOdprt(true)}
          onKeyDown={obTipki}
        />
      </label>

      {iscemo && zadetki.length > 0 && (
        <ul className="enanaena__predlogi" id={idSeznama} role="listbox" aria-label={oznaka}>
          {zadetki.map((igralec, indeks) => (
            <li
              key={igralec.id}
              id={`${idSeznama}-${indeks}`}
              role="option"
              aria-selected={indeks === oznacen}
              className={
                'enanaena__predlog' + (indeks === oznacen ? ' enanaena__predlog--oznacen' : '')
              }
              onMouseEnter={() => nastaviOznacen(indeks)}
              onClick={() => izberi(igralec.id)}
            >
              <span className="enanaena__predlog-ime">
                {igralec.ime} {igralec.priimek}
              </span>
              {igralec.klub && (
                <span className="enanaena__predlog-klub">{igralec.klub.ime}</span>
              )}
            </li>
          ))}
        </ul>
      )}

      {iscemo && zadetki.length === 0 && (
        <p className="enanaena__predlogi enanaena__brez-zadetka">Ni zadetka</p>
      )}
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
