/* »Kaj prinese tekma«: koliko Turnirko ratinga bi igralec dobil ali izgubil,
   če bi zdaj odigral tekmo proti izbranemu nasprotniku.

   Zakaj ni ena sama številka. Sprememba je zmnožek
   K × margina × teža × (izid − pričakovano), od česar sta dve sestavini
   odvisni od tekme, ki je še ni bilo: izid v nizih (margina) in raven
   tekmovanja (teža). Ena povprečna številka bi bila taka, kakršne ne bi dala
   nobena prava tekma. Zato:

     - zgoraj sta dve VELIKI številki za tipičen izid (3:1 oz. 1:3) — to je
       odgovor na vprašanje, zaradi katerega je gledalec prišel,
     - pod njima tabela VSEH izidov od 3:0 do 0:3, ki pokaže, kako se številka
       spreminja z izidom (gladka zmaga proti močnejšemu je presenečenje in
       prinese največ),
     - raven tekmovanja preklaplja izbirnik: uradna, klubska in rekreativna
       tekma niso enako vredna informacija.

   Vse številke izračuna strežnik skozi isti izračun kot pravi obračun tekme
   (NapovedTekmeStoritev) — v vmesniku se ne računa nič, tudi množenja s težo
   ne: vsak zmnožek je zaokrožen posebej in 0,75 × prikazana številka ni to,
   kar bi tekma res prinesla. Edino, kar vmesnik izpelje sam, je lega oznak na
   okrasni osi v tabeli — ta številk ne nosi, samo razmerje med njimi. */
import { useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'

import { igralciApi, profiliApi } from '../api/zahteve'
import type { NapovedIzid, NapovedStran, RavenTekmovanja } from '../api/tipi'
import { sklonTekem, sklonTock } from '../pomozno/oblikovanje'
import { IzbirnikIgralca } from './IzbirnikIgralca'
import { NapakaPoizvedbe } from './NapakaPoizvedbe'

/* Ravni, med katerimi je mogoče preklapljati — isti vrstni red kot jih pošlje
   strežnik. NE_STEJE je ni: tekma, ki ratinga ne premakne, ni napoved. */
const RAVNI: RavenTekmovanja[] = ['URADNO', 'KLUBSKO', 'REKREATIVNO']

/* Kratka oznaka ravni na gumbu. Polno ime (OZNAKE_RAVEN) je predolgo za pas
   treh gumbov na telefonu, zato stoji v opombi pod tabelo. */
const KRATKO_RAVEN: Record<RavenTekmovanja, string> = {
  URADNO: 'Uradno',
  KLUBSKO: 'Klubsko',
  REKREATIVNO: 'Rekreativno',
  NE_STEJE: '–',
}

export function NapovedTekme({ idIgralec }: { idIgralec: number }) {
  const [nasprotnik, nastaviNasprotnika] = useState<number | ''>('')
  const [raven, nastaviRaven] = useState<RavenTekmovanja>('URADNO')

  /* Šifrant igralcev je za izbirnik in praviloma že v predpomnilniku (isti
     ključ kot na strani igralcev in v semaforju »Ena na ena«). */
  const igralci = useQuery({ queryKey: ['igralci'], queryFn: igralciApi.seznam })

  const napoved = useQuery({
    queryKey: ['napoved', idIgralec, nasprotnik],
    queryFn: () => profiliApi.napoved(idIgralec, Number(nasprotnik)),
    enabled: nasprotnik !== '' && nasprotnik !== idIgralec,
  })

  const izbrani = useMemo(
    () => (igralci.data ?? []).find((i) => i.id === nasprotnik),
    [igralci.data, nasprotnik],
  )

  const n = napoved.data
  const izbranaRaven = n?.ravni.find((r) => r.raven === raven)
  const vrstice = izbranaRaven?.izidi ?? []
  const zmage = vrstice.filter((v) => v.zmaga)
  const porazi = vrstice.filter((v) => !v.zmaga)
  /* Tipičen izid je sredinski: pri tekmi na tri dobljene nize 3:1 in 1:3.
     Robna izida (3:0 in 3:2) sta skrajnosti razpona, ki ga kaže tabela. */
  const tipicnaZmaga = zmage[srednji(zmage.length)]
  const tipicenPoraz = porazi[srednji(porazi.length)]
  const zaZmago = n ? Math.floor(n.steviloNizov / 2) + 1 : 0
  const lega = legaNaOsi(vrstice)

  return (
    <div>
      <div className="naslovna-vrstica">
        <h2>Kaj prinese tekma</h2>
        <span className="plosca__zasebno">Samo zate</span>
      </div>

      <p className="namig">
        Izberi kateregakoli igralca in poglej, koliko Turnirko ratinga bi ti prinesla
        zmaga oziroma vzel poraz, če bi tekmo odigrala zdaj.
      </p>

      <div className="napoved__izbira">
        <IzbirnikIgralca
          oznaka="Nasprotnik"
          vidnaOznaka
          namig="Vpiši ime nasprotnika"
          igralci={igralci.data ?? []}
          izkljuci={idIgralec}
          naSpremembo={nastaviNasprotnika}
        />
        {izbrani && (
          <div className="napoved__nasprotnik">
            <Link to={`/igralci/${izbrani.id}/profil`} className="napoved__ime">
              {izbrani.ime} {izbrani.priimek}
            </Link>
            <span className="napoved__klub">{izbrani.klub?.ime ?? 'brez kluba'}</span>
          </div>
        )}
      </div>

      {nasprotnik === '' && (
        <p className="obvestilo">Dokler nasprotnik ni izbran, ni kaj napovedati.</p>
      )}
      {napoved.error && <NapakaPoizvedbe poizvedba={napoved} kaj="napovedi" />}
      {nasprotnik !== '' && napoved.isLoading && <p className="obvestilo">Računam …</p>}

      {n && tipicnaZmaga && tipicenPoraz && (
        <>
          <Semafor jaz={n.jaz} nasprotnik={n.nasprotnik} odstotek={n.pricakovanOdstotek} />

          <div className="profil__kazalniki napoved__vrh">
            <div className="kazalnik kazalnik--prvi">
              <div className="kazalnik__vrednost profil__zmaga">
                {sPredznakom(tipicnaZmaga.sprememba)}
              </div>
              <div className="kazalnik__oznaka">
                Če zmagaš {tipicnaZmaga.mojiNizi}:{tipicnaZmaga.nizovNasprotnika} →{' '}
                <span className="napoved__cilj">{tipicnaZmaga.rating}</span>
              </div>
            </div>
            <div className="kazalnik">
              <div className="kazalnik__vrednost profil__poraz">
                {sPredznakom(tipicenPoraz.sprememba)}
              </div>
              <div className="kazalnik__oznaka">
                Če izgubiš {tipicenPoraz.mojiNizi}:{tipicenPoraz.nizovNasprotnika} →{' '}
                <span className="napoved__cilj">{tipicenPoraz.rating}</span>
              </div>
            </div>
          </div>

          {/* Raven tekmovanja je izbira in ne filter: ista zmaga na uradnem
              turnirju NTZS je vredna več kot na klubskem. */}
          <div className="izbirnik napoved__ravni">
            {RAVNI.map((v) => (
              <button
                key={v}
                type="button"
                className={'izbirnik__gumb' + (raven === v ? ' izbirnik__gumb--aktiven' : '')}
                aria-pressed={raven === v}
                onClick={() => nastaviRaven(v)}
              >
                {KRATKO_RAVEN[v]}
              </button>
            ))}
          </div>

          <div className="napoved__vrstica napoved__vrstica--glava">
            <span>Izid</span>
            <span className="napoved__desno">Tvoja sprememba</span>
            <span className="napoved__desno">Tvoj rating</span>
            <span className="napoved__desno">Njegova sprememba</span>
            <span className="napoved__desno" aria-hidden="true">− 0 +</span>
          </div>
          {vrstice.map((v) => {
            const tipicen = v === tipicnaZmaga || v === tipicenPoraz
            const barva = v.zmaga ? 'profil__zmaga' : 'profil__poraz'
            return (
              <div
                className={'napoved__vrstica' + (tipicen ? ' napoved__vrstica--tipicna' : '')}
                key={`${v.mojiNizi}-${v.nizovNasprotnika}`}
              >
                <span className="napoved__izid-celica">
                  <span className={'napoved__izid ' + barva}>
                    {v.mojiNizi}:{v.nizovNasprotnika}
                  </span>
                  <span className="napoved__opis">{opisIzida(v, tipicen, zaZmago)}</span>
                </span>
                <span className={'napoved__sprememba napoved__desno ' + barva}>
                  {sPredznakom(v.sprememba)}
                </span>
                <span className="napoved__rating napoved__desno">{v.rating}</span>
                <span className="napoved__nasprotnikova napoved__desno">
                  {sPredznakom(v.spremembaNasprotnika)}
                </span>
                <span className={'napoved__os ' + barva} aria-hidden="true">
                  <span className="napoved__os-crta" />
                  <span className="napoved__os-nicla" style={{ left: lega(0) }} />
                  <span className="napoved__os-oznaka" style={{ left: lega(v.sprememba) }} />
                </span>
              </div>
            )
          })}

          <Razlaga
            jaz={n.jaz}
            nasprotnik={n.nasprotnik}
            odstotek={n.pricakovanOdstotek}
            raven={raven}
            teza={izbranaRaven?.teza ?? 1}
            steviloNizov={n.steviloNizov}
          />
        </>
      )}
    </div>
  )
}

/* Izhodišče obeh strani: rating, razlika in pričakovan izid. Brez tega je
   tabela seznam številk brez vzroka — prav razlika v ratingu je tista, ki
   določi, koliko je zmaga vredna. */
function Semafor({
  jaz,
  nasprotnik,
  odstotek,
}: {
  jaz: NapovedStran
  nasprotnik: NapovedStran
  odstotek: number
}) {
  const razlika = jaz.rating - nasprotnik.rating
  return (
    <div className="napoved__semafor">
      <div className="napoved__stran">
        <span className="napoved__oznaka">Ti</span>
        <span className="napoved__stevilka">{jaz.rating}</span>
      </div>
      <div className="napoved__sredina">
        <span className="napoved__verjetnost">{oblikujOdstotek(odstotek)}</span>
        <span className="napoved__oznaka">Pričakovana zmaga</span>
      </div>
      <div className="napoved__stran napoved__stran--desna">
        <span className="napoved__oznaka">{nasprotnik.polnoIme}</span>
        <span className="napoved__stevilka">{nasprotnik.rating}</span>
      </div>
      {/* Pas je isti odstotek še enkrat, a kot razmerje: koliko modrega je
          tvojega, se vidi, preden se prebere številka. Palica je okras,
          legenda pod njo pa nosi obe številki. */}
      <div className="napoved__pas">
        <div className="napoved__pas-palica" aria-hidden="true">
          <span
            className="napoved__pas-polnilo"
            style={{ width: `${Math.min(100, Math.max(0, odstotek))}%` }}
          />
        </div>
        <div className="napoved__pas-legenda">
          <span>Tvoja zmaga {oblikujOdstotek(odstotek)}</span>
          <span>Njegova zmaga {oblikujOdstotek(100 - odstotek)}</span>
        </div>
      </div>
      <p className="napoved__razlika">
        {razlika === 0
          ? 'Enak rating — tekma je po številkah izenačena.'
          : `Razlika je ${Math.abs(razlika)} ${sklonTock(Math.abs(razlika))} `
            + (razlika > 0 ? 'v tvojo korist.' : 'v njegovo korist.')}
      </p>
    </div>
  )
}

/* Zakaj so številke takšne, kot so — in kje napoved ne velja. Sestavine so
   iste kot pod grafom ratinga (K, margina, teža, pričakovano), da igralec
   isti obrazec sreča dvakrat in ga drugič že pozna. */
function Razlaga({
  jaz,
  nasprotnik,
  odstotek,
  raven,
  teza,
  steviloNizov,
}: {
  jaz: NapovedStran
  nasprotnik: NapovedStran
  odstotek: number
  raven: RavenTekmovanja
  teza: number
  steviloNizov: number
}) {
  return (
    <>
      <p className="profil__primerjava">
        Sprememba je <strong>K × nizi × teža × (izid − pričakovano)</strong>. Tvoj K je{' '}
        {jaz.k}
        {jaz.vrnitev && ' (povišan, ker se vračaš po več kot letu dni)'}
        {jaz.stTekem < 30 && !jaz.vrnitev
          && (jaz.stTekem === 0
            ? ' (povišan, ker še nimaš obračunanih tekem)'
            : ` (povišan, ker je za tabo šele ${jaz.stTekem} ${sklonTekem(jaz.stTekem)})`)}
        , njegov {nasprotnik.k}; pričakovano zmagaš v {oblikujOdstotek(odstotek)}. Številke veljajo za{' '}
        {KRATKO_RAVEN[raven].toLowerCase()} tekmovanje (teža {oblikujTezo(teza)}) in tekmo na{' '}
        {steviloNizov === 3 ? 'dva' : steviloNizov === 5 ? 'tri' : 'štiri'} dobljene nize.
      </p>
      {jaz.opozorila.includes('BREZ_RATINGA') && (
        <p className="namig">
          Ratinga še nimaš — {jaz.rating} je starostno sidro, izhodišče za tvojo starost in spol.
          Po prvih tekmah se bo številka premaknila bolj, kot kaže tabela.
        </p>
      )}
      {nasprotnik.opozorila.includes('BREZ_RATINGA') && (
        <p className="namig">
          Nasprotnik ratinga še nima — {nasprotnik.rating} je starostno sidro za njegovo
          starost in spol, ne izmerjena moč.
        </p>
      )}
      {jaz.opozorila.includes('PRVI_DAN') && (
        <p className="namig">
          Danes igraš svoj prvi dan. Takrat se rating ne sešteva po tekmah, ampak se vsakič
          znova izračuna iz vseh izidov dneva, zato so te številke le okvir.
        </p>
      )}
      {nasprotnik.opozorila.includes('PRVI_DAN') && (
        <p className="namig">
          Nasprotnik danes igra svoj prvi dan, zato se njegova številka računa drugače —
          njegova stran tabele je le okvir.
        </p>
      )}
    </>
  )
}

/* Pričakovana zmaga v odstotkih. Zaokroženi 0 % in 100 % bi trdila, da je
   izid nemogoč oziroma gotov — pri razliki 900 točk je zmaga zelo neverjetna,
   a se zgodi; prav zato se ji ob zmagi pripiše toliko točk. */
function oblikujOdstotek(v: number): string {
  if (v <= 0) return '< 1 %'
  if (v >= 100) return '> 99 %'
  return `${v} %`
}

/* Teža z vejico in dvema decimalkama, kot je zapisana pod grafom ratinga
   (»× 0,75 (teža)«) — isti obrazec naj se bere enako na obeh mestih. */
function oblikujTezo(v: number): string {
  return v.toFixed(2).replace('.', ',')
}

/* Indeks sredinske vrstice: pri treh izidih 3:0 / 3:1 / 3:2 je to 3:1. */
function srednji(koliko: number): number {
  return Math.floor((koliko - 1) / 2)
}

/* Besedni opis ob rezultatu. Tipična vrstica je v tabeli poudarjena z robom in
   podlago, a poudarek ne sme stati samo na barvi (WCAG 1.4.1) — zato ga pove
   še beseda. Robna izida se opišeta po nizih poraženca: brez dobljenega niza
   je gladka, z enim manj od zmagovalca tesna. */
function opisIzida(v: NapovedIzid, tipicen: boolean, zaZmago: number): string {
  if (tipicen) return v.zmaga ? 'tipična zmaga' : 'tipičen poraz'
  const niziPorazenca = v.zmaga ? v.nizovNasprotnika : v.mojiNizi
  if (niziPorazenca === 0) return v.zmaga ? 'gladka zmaga' : 'gladek poraz'
  if (niziPorazenca === zaZmago - 1) return v.zmaga ? 'tesna zmaga' : 'tesen poraz'
  return v.zmaga ? 'zmaga' : 'poraz'
}

/* Lega na osi »− 0 +« v odstotkih širine stolpca. Merilo je razpon prikazanih
   sprememb, zato najhujši poraz stoji levo in najboljša zmaga desno ne glede na
   raven; 2 % roba na vsaki strani, da oznaka ne zleze čez konec črte. Ničla
   je vedno znotraj razpona, ker zmaga ratinga ne more vzeti in poraz ne dati. */
function legaNaOsi(vrstice: NapovedIzid[]): (v: number) => string {
  const spremembe = vrstice.map((v) => v.sprememba)
  const najmanj = Math.min(...spremembe)
  const razpon = Math.max(...spremembe) - najmanj || 1
  return (v) => `${(((v - najmanj) / razpon) * 96 + 2).toFixed(1)}%`
}

/* Pravi minus (−) namesto vezaja; ±0 pri ničli, ker »+0« obljublja pridobitev. */
function sPredznakom(v: number): string {
  if (v === 0) return '±0'
  return v > 0 ? `+${v}` : `−${Math.abs(v)}`
}

