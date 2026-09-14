/* Ročni žreb lige: organizator razpored VPIŠE, namesto da bi ga sestavil žreb.

   Zakaj to obstaja: liga, ki se je doslej vodila na roke, pride v Turnirko z
   razporedom, ki je že narejen in razposlan igralcem. Naključni žreb bi ga
   zavrgel in ljudje bi imeli v rokah dva različna razporeda.

   Obrazec je prepisovalnica papirja, zato so vsa kola našteta eno pod drugim
   (in ne krmar po kolih kot v razporedu): prepis teče od zgoraj navzdol in
   organizator mora videti, kje je ostal. Številka kola je POLOŽAJ v seznamu —
   odstranjeno kolo preostala preštevilči, zato vrzeli, ki jih strežnik zavrača,
   iz tega obrazca sploh ne morejo priti.

   Kaj ustavi shranjevanje in kaj je samo opozorilo, je ista meja kot na
   strežniku (LigaStoritev.rocniRazpored): ekipa ne sme igrati dvakrat v istem
   kolu in ne sama s sabo, kolo ne sme biti prazno — nepopoln razpored (par, ki
   se ne sreča, ali par, ki se sreča drugačnokrat, kot pravijo pravila lige) pa
   je stvar tekmovanja in gre skozi kot opozorilo: ročno vodene lige takšne
   razporede imajo. */
import { useMemo, useState } from 'react'
import { useMutation, useQuery } from '@tanstack/react-query'

import { ligeApi } from '../api/zahteve'
import type { EkipaDto, LigaDto, ParRazporedaDto } from '../api/tipi'
import { ModalnoOkno } from './ModalnoOkno'
import { NapakaPoizvedbe } from './NapakaPoizvedbe'
import { SporociloNapake } from './SporociloNapake'

interface Lastnosti {
  liga: LigaDto
  ekipe: EkipaDto[]
  onZapri: () => void
  onShranjeno: () => void
}

/* Ena vrstica vnosa. Ključ je lasten, ker se vrstice dodajajo in odstranjujejo
   sredi seznama in bi indeks Reactu premešal polja. */
interface VrsticaZreba {
  kljuc: number
  domaci: number | null
  gost: number | null
}

/* Koliko opozoril o parih se izpiše, preden jih seznam samo prešteje — pri
   šestnajstih ekipah je nepopolnih parov lahko sto in vrstica bi postala stena. */
const NAJVEC_NASTETIH = 8

let stevecKljucev = 0
function praznaVrstica(): VrsticaZreba {
  stevecKljucev += 1
  return { kljuc: stevecKljucev, domaci: null, gost: null }
}

export function RocniZrebOkno({ liga, ekipe, onZapri, onShranjeno }: Lastnosti) {
  /* Predlog žreba je vir OBLIKE lige: koliko kol ima in koliko srečanj je v
     kolu. Računa ga strežnik po istih pravilih kot pravi žreb, zato tu ni
     druge kopije razporeda, ki bi se z njimi sčasoma razšla. */
  const predlog = useQuery({
    queryKey: ['liga', liga.id, 'predlog-razporeda'],
    queryFn: () => ligeApi.predlogRazporeda(liga.id),
  })

  /* null = mreže še ni uredil človek; takrat velja prazna mreža iz predloga. */
  const [urejena, nastaviUrejeno] = useState<VrsticaZreba[][] | null>(null)
  const [pregledOdprt, nastaviPregled] = useState(false)

  const prazna = useMemo(() => praznaMreza(predlog.data ?? []), [predlog.data])
  const kola = urejena ?? prazna

  const poId = useMemo(() => new Map(ekipe.map((e) => [e.id, e])), [ekipe])
  const preverba = useMemo(() => preveri(kola, ekipe, liga.dvokrozno), [kola, ekipe, liga.dvokrozno])
  const vpisanih = kola.flat().filter((v) => v.domaci !== null && v.gost !== null).length
  const jePrazen = kola.flat().every((v) => v.domaci === null && v.gost === null)

  const shranjevanje = useMutation({
    mutationFn: () =>
      ligeApi.rocniRazpored(liga.id, {
        srecanja: kola.flatMap((vrstice, i) =>
          vrstice
            .filter((v) => v.domaci !== null && v.gost !== null)
            .map((v) => ({ kolo: i + 1, idDomaci: v.domaci as number, idGost: v.gost as number })),
        ),
      }),
    onSuccess: () => {
      onShranjeno()
      onZapri()
    },
  })

  function uredi(spremeni: (mreza: VrsticaZreba[][]) => VrsticaZreba[][]) {
    nastaviUrejeno(spremeni(kola.map((vrstice) => vrstice.map((v) => ({ ...v })))))
  }

  /* Izbira ekipe, ki v tem kolu že igra, ekipi ZAMENJA: prejšnja zasedba tega
     mesta se preseli tja, od koder je prišla nova. Brez tega polnega kola ne bi
     bilo mogoče popraviti — vsaka ekipa je že nekje in izbirnik bi bil prazen,
     organizator pa bi moral mesta najprej prazniti. Zamenjava tudi ne more
     ustvariti podvojene ekipe v kolu: število ekip v kolu ostane isto.
     Izbira nasprotnika iz iste vrstice po istem pravilu obrne domačo pravico. */
  function nastaviEkipo(iKolo: number, kljuc: number, stran: 'domaci' | 'gost', id: number | null) {
    uredi((mreza) => {
      const vrstice = mreza[iKolo]
      const cilj = vrstice.find((v) => v.kljuc === kljuc)
      if (!cilj) return mreza
      const prejsnja = cilj[stran]
      if (id !== null) {
        for (const v of vrstice) {
          for (const s of ['domaci', 'gost'] as const) {
            if ((v.kljuc !== kljuc || s !== stran) && v[s] === id) v[s] = prejsnja
          }
        }
      }
      cilj[stran] = id
      return mreza
    })
  }

  /* Predlog napolni mrežo s pari, ki bi jih vrgel žreb — smiselno le, kadar je
     vnos še prazen. Gumb, ki bi prepisal štirideset prepisanih vrstic, bi bil
     past, zato je takrat ugasnjen in pot do njega vodi skozi »Počisti vse«. */
  function napolniSPredlogom() {
    if (!predlog.data) return
    nastaviUrejeno(napolnjenaMreza(predlog.data))
  }

  function pocisti() {
    uredi((mreza) => mreza.map((vrstice) => vrstice.map(() => praznaVrstica())))
  }

  const lahkoShranim = vpisanih > 0 && preverba.napake.length === 0 && !shranjevanje.isPending

  return (
    <ModalnoOkno naslov="Ročni žreb" onZapri={onZapri} siroko>
      <p className="obvestilo">
        {/* »Prva / druga« in ne »leva / desna«: na telefonu sta izbirnika eden
            pod drugim in leva-desna razlaga tam ne bi držala. */}
        Vpiši razpored, kot je bil izžreban. V vrstici je prva ekipa domača,
        druga gostujoča. Kolo je en igralni dan, zato ekipa v njem odigra eno
        srečanje; ekipa, ki v kolu ne igra, je prosta. Če izbereš ekipo, ki v
        tem kolu že igra, se ekipi zamenjata.
      </p>

      {predlog.isPending && <p className="obvestilo">Nalaganje …</p>}
      <NapakaPoizvedbe poizvedba={predlog} kaj="predloga razporeda" />

      <div className="zreb__krmila">
        <button
          type="button"
          className="gumb gumb--majhen"
          disabled={!predlog.data || !jePrazen}
          onClick={napolniSPredlogom}
        >
          Predlagaj razpored
        </button>
        <button type="button" className="gumb gumb--majhen" disabled={jePrazen} onClick={pocisti}>
          Počisti vse
        </button>
        <span className="sekcija__meta">
          {vpisanih} {srecanjTekst(vpisanih)} v {kola.length} {kolTekst(kola.length)}
          {preverba.pricakovanih > 0 && ` · po pravilih lige ${preverba.pricakovanih}`}
        </span>
      </div>
      <p className="namig">
        Predlog napolni mrežo z naključnim žrebom — uporaben je, če je papirnati
        razpored po istem sistemu in je treba popraviti le nekaj parov. Vpisanega
        razporeda ne prepiše.
      </p>

      <div className="zreb">
        {kola.map((vrstice, iKolo) => {
          /* V praznem kolu so »prosti« vsi in seznam vseh ekip ne pove nič —
             takrat glava izpiše, koliko vrstic kolo ima. */
          const vpisanihVKolu = vrstice.filter((v) => v.domaci !== null || v.gost !== null).length
          const proste = vpisanihVKolu > 0 ? prosteVKolu(vrstice, ekipe) : []
          return (
            <section key={iKolo} className="zreb__kolo">
              <div className="zreb__kolo-glava">
                <span className="zreb__kolo-st">{iKolo + 1}. kolo</span>
                <span className="sekcija__meta">
                  {proste.length > 0
                    ? `prosti: ${proste.join(' · ')}`
                    : `${vrstice.length} ${srecanjTekst(vrstice.length)}`}
                </span>
                <button
                  type="button"
                  className="gumb gumb--majhen gumb--nevaren"
                  onClick={() => uredi((mreza) => mreza.filter((_, i) => i !== iKolo))}
                >
                  Odstrani kolo
                </button>
              </div>

              <div className="zreb__vrstice">
              {vrstice.map((v, iVrstica) => (
                <div key={v.kljuc} className="zreb__vrstica">
                  <IzbirnikEkipe
                    oznaka={`Domača ekipa, ${iKolo + 1}. kolo, ${iVrstica + 1}. srečanje`}
                    kratka="doma"
                    ekipe={ekipe}
                    vrednost={v.domaci}
                    onIzbor={(id) => nastaviEkipo(iKolo, v.kljuc, 'domaci', id)}
                  />
                  <span className="zreb__vrstica-vez" aria-hidden="true">–</span>
                  <IzbirnikEkipe
                    oznaka={`Gostujoča ekipa, ${iKolo + 1}. kolo, ${iVrstica + 1}. srečanje`}
                    kratka="gost"
                    ekipe={ekipe}
                    vrednost={v.gost}
                    onIzbor={(id) => nastaviEkipo(iKolo, v.kljuc, 'gost', id)}
                  />
                  <button
                    type="button"
                    className="zreb__odstrani"
                    aria-label={`Odstrani ${iVrstica + 1}. srečanje ${iKolo + 1}. kola`}
                    title="Odstrani srečanje"
                    onClick={() =>
                      uredi((mreza) => {
                        mreza[iKolo] = mreza[iKolo].filter((x) => x.kljuc !== v.kljuc)
                        return mreza
                      })
                    }
                  >
                    ✕
                  </button>
                </div>
              ))}
              </div>

              <button
                type="button"
                className="gumb gumb--majhen"
                onClick={() =>
                  uredi((mreza) => {
                    mreza[iKolo] = [...mreza[iKolo], praznaVrstica()]
                    return mreza
                  })
                }
              >
                + Srečanje
              </button>
            </section>
          )
        })}

        <button
          type="button"
          className="gumb"
          onClick={() => uredi((mreza) => [...mreza, [praznaVrstica()]])}
        >
          + Dodaj kolo
        </button>
      </div>

      {preverba.napake.length > 0 && (
        <div className="zreb__napake">
          <span className="podnaslov-sekcije">Popraviti je treba</span>
          <ul className="zreb__seznam">
            {preverba.napake.map((n) => (
              <li key={n}>{n}</li>
            ))}
          </ul>
        </div>
      )}

      {/* Opozorila o nepopolnem razporedu povedo kaj šele, ko je kaj vpisano —
          prazen obrazec ni nepopoln razpored, ampak prazen obrazec. */}
      {vpisanih > 0 && preverba.opozorila.length > 0 && (
        <div className="zreb__opozorila">
          <span className="podnaslov-sekcije">Razpored ni popoln</span>
          <ul className="zreb__seznam">
            {preverba.opozorila.map((o) => (
              <li key={o}>{o}</li>
            ))}
          </ul>
          <p className="namig">
            To ni napaka — ročno vodene lige imajo tudi nepopolne razporede in
            razpored se shrani tak, kot je vpisan.
          </p>
        </div>
      )}

      <div className="zreb__pregled">
        <button
          type="button"
          className="gumb gumb--majhen"
          aria-expanded={pregledOdprt}
          onClick={() => nastaviPregled(!pregledOdprt)}
        >
          {pregledOdprt ? 'Skrij pregled po ekipah' : 'Pregled po ekipah'}
        </button>
        {pregledOdprt && (
          <ul className="zreb__seznam">
            {preverba.poEkipah.map((e) => (
              <li key={e.id}>
                {poId.get(e.id)?.prikazanoIme ?? '—'}
                <span className="sekcija__meta">
                  {' '}
                  {e.skupaj} {srecanjTekst(e.skupaj)} · {e.doma} doma · {e.skupaj - e.doma} v gosteh
                </span>
              </li>
            ))}
          </ul>
        )}
      </div>

      <SporociloNapake napaka={shranjevanje.error} />
      <div className="obrazec__gumbi">
        <button type="button" className="gumb" onClick={onZapri}>
          Prekliči
        </button>
        <button
          type="button"
          className="gumb gumb--glavni"
          disabled={!lahkoShranim}
          onClick={() => shranjevanje.mutate()}
        >
          Shrani razpored
        </button>
      </div>
      <p className="namig">
        Shranjeni razpored takoj vidijo vsi obiskovalci strani lige, označen kot
        ročni žreb. Dokler ni vpisan noben rezultat, ga je mogoče razveljaviti in
        vpisati znova.
      </p>
    </ModalnoOkno>
  )
}

/* Izbirnik ekipe. Nobena možnost ni ugasnjena — ekipo, ki v kolu že igra,
   izbira ZAMENJA (glej nastaviEkipo). Ugasnjene možnosti bi polno kolo
   zaklenile: tam so vse ekipe že nekje in izbirnik bi ostal prazen.

   Kratka oznaka (»doma« / »gost«) se izpiše samo na telefonu: tam sta izbirnika
   eden pod drugim in leva-desna razlaga iz uvoda ne velja več. Bralnik zaslona
   dobi celotno oznako s kolom in številko srečanja na obeh širinah. */
function IzbirnikEkipe({
  oznaka,
  kratka,
  ekipe,
  vrednost,
  onIzbor,
}: {
  oznaka: string
  kratka: string
  ekipe: EkipaDto[]
  vrednost: number | null
  onIzbor: (id: number | null) => void
}) {
  return (
    <label className="zreb__polje">
      <span className="zreb__polje-oznaka" aria-hidden="true">{kratka}</span>
      <select
        aria-label={oznaka}
        value={vrednost ?? ''}
        onChange={(d) => onIzbor(d.target.value ? Number(d.target.value) : null)}
      >
        <option value="">— ekipa —</option>
        {ekipe.map((e) => (
          <option key={e.id} value={e.id}>
            {e.prikazanoIme}
          </option>
        ))}
      </select>
    </label>
  )
}

/* Ekipe, ki v kolu ne igrajo. Pri lihem številu ekip je to normalno stanje
   (ena je prosta), zato je to meta podatek kola in ne opozorilo. */
function prosteVKolu(vrstice: VrsticaZreba[], ekipe: EkipaDto[]): string[] {
  const igrajo = new Set<number>()
  for (const v of vrstice) {
    if (v.domaci !== null) igrajo.add(v.domaci)
    if (v.gost !== null) igrajo.add(v.gost)
  }
  return ekipe.filter((e) => !igrajo.has(e.id)).map((e) => e.prikazanoIme)
}

/* Prazna mreža po obliki predloga: toliko kol in toliko vrstic v kolu, kot bi
   jih imel žreb. Organizator tako ne šteje, koliko srečanj ima kolo. */
function praznaMreza(predlog: ParRazporedaDto[]): VrsticaZreba[][] {
  return poKolih(predlog).map((vKolu) => vKolu.map(() => praznaVrstica()))
}

function napolnjenaMreza(predlog: ParRazporedaDto[]): VrsticaZreba[][] {
  return poKolih(predlog).map((vKolu) =>
    vKolu.map((p) => ({ ...praznaVrstica(), domaci: p.idDomaci, gost: p.idGost })),
  )
}

/* Predlog po kolih; kola predloga tečejo od 1 naprej brez vrzeli (tako jih
   sestavi RazporedStoritev), zato je položaj v seznamu tudi številka kola. */
function poKolih(predlog: ParRazporedaDto[]): ParRazporedaDto[][] {
  const kola = [...new Set(predlog.map((p) => p.kolo))].sort((a, b) => a - b)
  return kola.map((kolo) => predlog.filter((p) => p.kolo === kolo))
}

interface BilancaEkipe {
  id: number
  skupaj: number
  doma: number
}

interface Preverba {
  /* Napake ustavijo shranjevanje — strežnik bi jih zavrnil. */
  napake: string[]
  /* Opozorila ne ustavijo ničesar; povedo, v čem razpored ni popoln. */
  opozorila: string[]
  poEkipah: BilancaEkipe[]
  pricakovanih: number
}

/* Preverba vpisanega razporeda. Meja med napako in opozorilom je ista kot na
   strežniku: napaka je tisto, kar bi razpored pokvarilo (ekipa dvakrat v kolu,
   sama proti sebi, prazno kolo, nedokončana vrstica), opozorilo pa to, da
   razpored ni popoln krožni sistem — kar je pri ročno vodeni ligi lahko
   namerno. */
function preveri(kola: VrsticaZreba[][], ekipe: EkipaDto[], dvokrozno: boolean): Preverba {
  const ime = (id: number) => ekipe.find((e) => e.id === id)?.prikazanoIme ?? `ekipa ${id}`
  const napake: string[] = []
  const opozorila: string[] = []
  const bilance = new Map<number, BilancaEkipe>(
    ekipe.map((e) => [e.id, { id: e.id, skupaj: 0, doma: 0 }]),
  )
  /* Ključ para je neurejen (manjši-večji id): ista ekipi sta isti par doma in
     v gosteh — pri vprašanju »ali sta se srečali« stran ne šteje. */
  const srecanjaParov = new Map<string, number>()

  kola.forEach((vrstice, i) => {
    const kolo = i + 1
    const vKolu = new Map<number, number>()
    let polnih = 0

    for (const v of vrstice) {
      if (v.domaci === null && v.gost === null) continue
      if (v.domaci === null || v.gost === null) {
        napake.push(`${kolo}. kolo: srečanje ima vpisano samo eno ekipo.`)
        continue
      }
      if (v.domaci === v.gost) {
        napake.push(`${kolo}. kolo: ekipa ${ime(v.domaci)} ne more igrati sama s sabo.`)
        continue
      }
      polnih += 1
      for (const id of [v.domaci, v.gost]) {
        vKolu.set(id, (vKolu.get(id) ?? 0) + 1)
      }
      const domaci = bilance.get(v.domaci)
      const gost = bilance.get(v.gost)
      if (domaci) {
        domaci.skupaj += 1
        domaci.doma += 1
      }
      if (gost) gost.skupaj += 1
      const kljuc = [v.domaci, v.gost].sort((a, b) => a - b).join('-')
      srecanjaParov.set(kljuc, (srecanjaParov.get(kljuc) ?? 0) + 1)
    }

    for (const [id, kolikokrat] of vKolu) {
      if (kolikokrat > 1) {
        napake.push(
          `${kolo}. kolo: ekipa ${ime(id)} ima ${kolikokrat} srečanja, kolo pa je en igralni dan.`,
        )
      }
    }
    /* Prazno kolo med polnimi bi bila prazna stran v razporedu; strežnik kola
       brez srečanj ne pozna. */
    if (polnih === 0 && kola.flat().some((v) => v.domaci !== null && v.gost !== null)) {
      napake.push(`${kolo}. kolo je prazno — vpiši srečanja ali odstrani kolo.`)
    }
  })

  const nakrat = dvokrozno ? 2 : 1
  const pricakovanih = (ekipe.length * (ekipe.length - 1) * nakrat) / 2
  /* Par, ki se ne sreča, in par, ki se sreča drugačnokrat, sta dve različni
     zgodbi: prvega v razporedu ni, drugi je tam narobe pogosto (ali preredko —
     v dvokrožni ligi je eno srečanje prav tako odstopanje kot tri). */
  const manjkajoci: string[] = []
  const drugacnoKrat: string[] = []
  for (let a = 0; a < ekipe.length; a++) {
    for (let b = a + 1; b < ekipe.length; b++) {
      const kljuc = [ekipe[a].id, ekipe[b].id].sort((x, y) => x - y).join('-')
      const kolikokrat = srecanjaParov.get(kljuc) ?? 0
      const par = `${ekipe[a].prikazanoIme} – ${ekipe[b].prikazanoIme}`
      if (kolikokrat === 0) manjkajoci.push(par)
      else if (kolikokrat !== nakrat) drugacnoKrat.push(`${par} (${kolikokrat}×)`)
    }
  }
  if (manjkajoci.length > 0) {
    opozorila.push(`Se ne srečata: ${nastej(manjkajoci)}.`)
  }
  if (drugacnoKrat.length > 0) {
    opozorila.push(
      `Po pravilih lige igrata ${nakrat}×, v razporedu pa drugače: ${nastej(drugacnoKrat)}.`,
    )
  }
  const brezSrecanj = [...bilance.values()].filter((b) => b.skupaj === 0)
  if (brezSrecanj.length > 0 && srecanjaParov.size > 0) {
    opozorila.push(`V razporedu sploh ni: ${nastej(brezSrecanj.map((b) => ime(b.id)))}.`)
  }
  const igrajo = [...bilance.values()].filter((b) => b.skupaj > 0)
  const najmanj = Math.min(...igrajo.map((b) => b.skupaj))
  const najvec = Math.max(...igrajo.map((b) => b.skupaj))
  if (igrajo.length > 0 && najmanj !== najvec) {
    opozorila.push(
      `Ekipe nimajo enako srečanj: od ${najmanj} do ${najvec}. Podrobno v pregledu po ekipah.`,
    )
  }

  return {
    napake: [...new Set(napake)],
    opozorila,
    poEkipah: [...bilance.values()],
    pricakovanih,
  }
}

/* Našteje največ NAJVEC_NASTETIH postavk, ostale prešteje — seznam sto parov
   bi iz opozorila naredil steno besedila. */
function nastej(postavke: string[]): string {
  if (postavke.length <= NAJVEC_NASTETIH) return postavke.join(', ')
  const vidne = postavke.slice(0, NAJVEC_NASTETIH).join(', ')
  return `${vidne} in še ${postavke.length - NAJVEC_NASTETIH}`
}

function kolTekst(n: number): string {
  if (n === 1) return 'kolu'
  return 'kolih'
}

function srecanjTekst(n: number): string {
  if (n === 1) return 'srečanje'
  if (n === 2) return 'srečanji'
  if (n === 3 || n === 4) return 'srečanja'
  return 'srečanj'
}
