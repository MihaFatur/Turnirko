/* Filtriranje in razvrscanje seznamov: turnirji, lige, lestvica.

   Zakaj en gumb namesto pasu gumbov: meril je po novem pet ali sest (stanje,
   sezona, kraj, organizator, klub, spol ...) in vsako od njih ima svoje
   vrednosti. Segmentirani izbirnik zanje ne zadosca - na namizju bi vzel tri
   vrste, na 390 px pa pol zaslona, preden bi gledalec videl prvo vrstico
   seznama. Zato je na strani ena vrstica krmil: gumb "Filtriraj", ki odpre
   okno z vsemi merili, in ob njem nativni izbor razvrstitve.

   Kaj je izbrano, pove vrsta zetonov POD krmili in ne stanje v oknu: filter,
   ki ga ne vidis, je past - gledalec bi seznam bral kot celoto, ceprav mu
   manjka polovica. Vsak zeton se da odstraniti sam, poleg njih stoji
   "Pocisti vse".

   Merila so podatkovna in ne nasteta:
   - skupina se izrise SAMO, kadar jo podatki napolnijo z vsaj dvema
     razlicnima vrednostma (uvozeni turnirji npr. nimajo ne kraja ne
     organizatorja - takrat sta skupini odvec, ne pa prazni);
   - stevec ob moznosti se preracuna glede na OSTALE izbrane skupine, moznosti
     brez zadetkov pa odpadejo. S tem ni mogoce sestaviti kombinacije, ki vrne
     prazen seznam ("Sezona 2015/16" + "Klub, ki takrat ni igral").

   Skupina drzi VEC izbranih vrednosti hkrati (ali-ali znotraj skupine, in
   med skupinami): "V teku ali priprava" je smiselno vprasanje, "v teku in
   hkrati priprava" ni. Postavka ima v vsaki skupini eno vrednost - vecvrednih
   meril (npr. "igra v tej ligi") tu namenoma ni. */
import { useMemo, useState } from 'react'

import { ModalnoOkno } from './ModalnoOkno'

/* Ena skupina meril nad seznamom postavk tipa T. */
export interface SkupinaFiltra<T> {
  kljuc: string
  oznaka: string
  /* Vrednost postavke v tej skupini; null pomeni, da postavka vrednosti nima
     (turnir brez kraja) - izbor te skupine je nikoli ne zajame. */
  vrednost: (postavka: T) => string | null
  /* Napis vrednosti; brez njega se izpise vrednost sama. */
  napis?: (vrednost: string) => string
  /* Vrstni red moznosti; privzeto po pogostosti navzdol (glej PO_POGOSTOSTI). */
  vrstniRed?: (a: MoznostFiltra, b: MoznostFiltra) => number
  /* Skupino krmili LASTNO krmilo nad seznamom (koledar: segmentirani izbirnik
     Vse / Turnirji / Lige), zato je v oknu z merili in med zetoni ni - dve
     krmili za isto merilo eno vrsto narazen sta past in ne udobje. Stanje
     ostane v istem izboru, da ni vzporednega. */
  zunanja?: boolean
}

export interface MoznostFiltra {
  vrednost: string
  napis: string
  stevec: number
}

/* Merilo z zvezno vrednostjo (rating): namesto nastetih moznosti dve meji.

   Zakaj ne pas nastetih razredov ("800-999, 1000-1199 ..."): meja, ki jo
   organizator potrebuje, je meja TEGA tekmovanja ("od 1200 navzgor") in ne
   ena od vnaprej narisanih. Pasovi bi jo znali samo priblizati.

   Za razliko od nastetih skupin obmocje LAHKO vrne prazen seznam (rating
   1900-2000 v klubu, ki takega igralca nima) - stevci ga ne morejo prepreciti,
   ker meja ni izbira med moznostmi. Prazno stanje seznama mora zato ponuditi
   izhod ("Pocisti filtre"). */
export interface ObmocjeFiltra<T> {
  kljuc: string
  oznaka: string
  /* Stevilcna vrednost postavke; null pomeni, da je postavka nima (igralec
     brez ratinga) - vpisana meja jo izloci, brez meje ostane v seznamu. */
  vrednost: (postavka: T) => number | null
  /* Pojasnilo pod poljema v oknu (npr. kaj se zgodi z igralci brez ratinga). */
  namig?: string
}

/* Meji enega obmocja; null pomeni "brez meje na tej strani". */
export interface Meja {
  od: number | null
  do: number | null
}

/* Eno merilo razvrstitve. Razvrstitev je vedno ena sama in ima privzetek
   (prva v seznamu), zato ni del izbora filtrov. */
export interface Razvrstitev<T> {
  kljuc: string
  oznaka: string
  primerjaj: (a: T, b: T) => number
}

/* Kljuc skupine -> izbrane vrednosti. Prazen seznam pomeni "brez omejitve",
   zato skupina brez izbire v izboru sploh ne obstaja. */
export type IzborFiltra = Record<string, string[]>

/* Nasteta merila (statusi, kategorije) imajo svoj vrstni red - "V teku" pride
   pred "Priprava" ne glede na to, cesa je vec. */
export function poSeznamu(vrstniRed: string[]) {
  return (a: MoznostFiltra, b: MoznostFiltra) =>
    vrstniRed.indexOf(a.vrednost) - vrstniRed.indexOf(b.vrednost)
}

/* Sezone in letnice: najnovejsa na vrh. Primerja se vodilna letnica, ker so
   uvozene sezone zapisane v dveh oblikah ("2023/24" in "2024-2025"). */
export function poVrednostiNazaj(a: MoznostFiltra, b: MoznostFiltra) {
  return b.vrednost.localeCompare(a.vrednost, 'sl', { numeric: true })
}

/* Privzetek: najpogostejsa vrednost na vrh, ob izenacenju abecedno. Pri
   klubih in krajih je to edini smiseln vrstni red - abecedni bi 60 klubov
   pustil enako neberljivih, pogostost pa tri, ki jih iscejo vsi, dvigne. */
const PO_POGOSTOSTI = (a: MoznostFiltra, b: MoznostFiltra) =>
  b.stevec - a.stevec || a.napis.localeCompare(b.napis, 'sl')

/* Stabilna prazna vrednost: privzetek [] bi ob vsakem izrisu ustvaril nov
   seznam in podrl memoizacijo (glej opozorilo pri useFiltri). */
const PRAZNA_OBMOCJA: never[] = []

/* Nad toliko moznostmi skupina dobi svoje polje za iskanje in se skrci na
   PRIKAZANIH_SKRCENO vrstic (klubov je lahko 60, sezon 14). */
const PRAG_ISKANJA = 12
const PRIKAZANIH_SKRCENO = 8

interface Skupina {
  kljuc: string
  oznaka: string
  moznosti: MoznostFiltra[]
}

/* Obmocje, kot ga vidi okno: poleg imena se najmanjsa in najvecja vrednost v
   seznamu - polji ju pokazeta kot namig, da meja ni ugibanje. */
interface Razpon {
  kljuc: string
  oznaka: string
  namig?: string
  najmanj: number
  najvec: number
}

/* Stanje filtrov in razvrstitve nad seznamom.

   POZOR: `skupine` in `razvrstitve` morata biti stabilna (useMemo v strani) -
   preracun visi na njuni identiteti. Ker se moznosti tako ali tako izpeljejo
   iz podatkov, jih stran memoizira ze zaradi sebe. */
export function useFiltri<T>(
  postavke: T[],
  skupine: SkupinaFiltra<T>[],
  razvrstitve: Razvrstitev<T>[],
  /* Zvezna merila (rating od-do); brez njih se ne spremeni nic. */
  obmocja: ObmocjeFiltra<T>[] = PRAZNA_OBMOCJA,
) {
  const [izbor, nastaviIzbor] = useState<IzborFiltra>({})
  const [meje, nastaviMeje] = useState<Record<string, Meja>>({})
  const [razvrstitev, nastaviRazvrstitev] = useState(razvrstitve[0]?.kljuc ?? '')

  /* Ali postavka ustreza izboru. `razenSkupine` izpusti eno skupino - tako se
     preracunajo stevci znotraj nje same (glej moznosti). Obmocja se izpustiti
     ne da: niso izbira med moznostmi in svojih stevcev nimajo, zato v stevcih
     drugih skupin normalno soodlocajo. */
  const ustreza = useMemo(
    () => (postavka: T, razenSkupine?: string) =>
      skupine.every((s) => {
        if (s.kljuc === razenSkupine) return true
        const izbrane = izbor[s.kljuc]
        if (!izbrane || izbrane.length === 0) return true
        const vrednost = s.vrednost(postavka)
        return vrednost !== null && izbrane.includes(vrednost)
      }) &&
      obmocja.every((o) => {
        const meja = meje[o.kljuc]
        if (!meja || (meja.od === null && meja.do === null)) return true
        const vrednost = o.vrednost(postavka)
        // postavka brez vrednosti meji ne more ustrezati (rating "-" ni 0)
        if (vrednost === null) return false
        if (meja.od !== null && vrednost < meja.od) return false
        return meja.do === null || vrednost <= meja.do
      }),
    [skupine, izbor, obmocja, meje],
  )

  const prikazani = useMemo(() => {
    const merilo = razvrstitve.find((r) => r.kljuc === razvrstitev)
    const izbrani = postavke.filter((p) => ustreza(p))
    return merilo ? izbrani.slice().sort(merilo.primerjaj) : izbrani
  }, [postavke, ustreza, razvrstitve, razvrstitev])

  /* Katere skupine sploh nosijo vprasanje: manj kot dve razlicni vrednosti v
     CELOTNEM seznamu pomeni, da ni cesa loceti. Merilo namenoma ni zozen
     seznam - skupina, ki med filtriranjem izgine in se vrne, je slabsa od
     skupine z eno moznostjo. */
  const smiselne = useMemo(() => {
    const stetje = new Map<string, Set<string>>()
    for (const s of skupine) stetje.set(s.kljuc, new Set())
    for (const p of postavke) {
      for (const s of skupine) {
        const vrednost = s.vrednost(p)
        if (vrednost !== null) stetje.get(s.kljuc)?.add(vrednost)
      }
    }
    return skupine.filter((s) => !s.zunanja && (stetje.get(s.kljuc)?.size ?? 0) >= 2)
  }, [postavke, skupine])

  const moznosti: Skupina[] = useMemo(
    () =>
      smiselne
        .map((s) => {
          const stetje = new Map<string, number>()
          for (const p of postavke) {
            if (!ustreza(p, s.kljuc)) continue
            const vrednost = s.vrednost(p)
            if (vrednost !== null) stetje.set(vrednost, (stetje.get(vrednost) ?? 0) + 1)
          }
          const napis = (v: string) => s.napis?.(v) ?? v
          const seznam = [...stetje].map(([vrednost, stevec]) => ({
            vrednost,
            napis: napis(vrednost),
            stevec,
          }))
          seznam.sort(s.vrstniRed ?? PO_POGOSTOSTI)
          return { kljuc: s.kljuc, oznaka: s.oznaka, moznosti: seznam }
        })
        .filter((s) => s.moznosti.length > 0),
    [smiselne, postavke, ustreza],
  )

  /* Katera obmocja nosijo vprasanje: manj kot dve razlicni vrednosti pomeni,
     da ni cesa rezati. Ob tem se izracuna razpon v seznamu - polji ga
     pokazeta kot namig, da meja ni ugibanje. */
  const razponi = useMemo(
    () =>
      obmocja.flatMap((o) => {
        let najmanj: number | null = null
        let najvec: number | null = null
        const razlicne = new Set<number>()
        for (const p of postavke) {
          const v = o.vrednost(p)
          if (v === null) continue
          razlicne.add(v)
          if (najmanj === null || v < najmanj) najmanj = v
          if (najvec === null || v > najvec) najvec = v
        }
        if (razlicne.size < 2 || najmanj === null || najvec === null) return []
        return [{ kljuc: o.kljuc, oznaka: o.oznaka, namig: o.namig, najmanj, najvec }]
      }),
    [obmocja, postavke],
  )

  /* Zetoni pod krmili: ena vrstica na izbrano vrednost, v vrstnem redu
     skupin, za njimi obmocja. Napis vzamemo iz definicije skupine in ne iz
     moznosti - izbrana vrednost, ki je trenutno brez zadetkov, mora ostati
     odstranljiva. Obmocje da en zeton z obema mejama ("Rating 1200-1500"):
     dva zetona za eno merilo bi bila dve vprasanji tam, kjer je eno. */
  const zetoni = useMemo(
    () => [
      ...skupine.flatMap((s) =>
        s.zunanja
          ? []
          : (izbor[s.kljuc] ?? []).map((vrednost) => ({
              skupina: s.kljuc,
              oznaka: s.oznaka,
              vrednost,
              napis: s.napis?.(vrednost) ?? vrednost,
              obmocje: false,
            })),
      ),
      /* Zetoni obmocij tecejo po VSEH obmocjih in ne po tistih, ki so
         trenutno "smiselna" (glej razponi): ozko iskanje pusti v seznamu eno
         samo vrednost ratinga, merilo iz okna izgine - vpisana meja pa se
         naprej reze. Zeton, ki bi takrat izginil, bi pustil filter, ki ga ni
         mogoce ne videti ne odstraniti. Isto velja za skupine zgoraj. */
      ...obmocja.flatMap((o) => {
        const napis = opisMeje(meje[o.kljuc])
        return napis === null
          ? []
          : [{ skupina: o.kljuc, oznaka: o.oznaka, vrednost: napis, napis, obmocje: true }]
      }),
    ],
    [skupine, izbor, obmocja, meje],
  )

  function preklopi(kljucSkupine: string, vrednost: string) {
    nastaviIzbor((prej) => {
      const izbrane = prej[kljucSkupine] ?? []
      const nove = izbrane.includes(vrednost)
        ? izbrane.filter((v) => v !== vrednost)
        : [...izbrane, vrednost]
      const naslednji = { ...prej }
      if (nove.length === 0) delete naslednji[kljucSkupine]
      else naslednji[kljucSkupine] = nove
      return naslednji
    })
  }

  /* Celoten izbor ene skupine naenkrat - za zunanje krmilo, kjer so moznosti
     izkljucujoce ("Vse / Turnirji / Lige") in preklop ene vrednosti ne bi
     odstranil druge. */
  function nastaviSkupino(kljucSkupine: string, vrednosti: string[]) {
    nastaviIzbor((prej) => {
      const naslednji = { ...prej }
      if (vrednosti.length === 0) delete naslednji[kljucSkupine]
      else naslednji[kljucSkupine] = vrednosti
      return naslednji
    })
  }

  /* Ena meja obmocja; null pomeni "brez meje". Prazno obmocje se iz stanja
     odstrani, da "je izbrano" ostane preprosto vprasanje o kljucu. */
  function nastaviMejo(kljucObmocja: string, stran: 'od' | 'do', vrednost: number | null) {
    nastaviMeje((prej) => {
      const trenutna = prej[kljucObmocja] ?? { od: null, do: null }
      const nova: Meja = { ...trenutna, [stran]: vrednost }
      const naslednji = { ...prej }
      if (nova.od === null && nova.do === null) delete naslednji[kljucObmocja]
      else naslednji[kljucObmocja] = nova
      return naslednji
    })
  }

  function pocistiObmocje(kljucObmocja: string) {
    nastaviMeje((prej) => {
      const naslednji = { ...prej }
      delete naslednji[kljucObmocja]
      return naslednji
    })
  }

  return {
    izbor,
    meje,
    razponi,
    zetoni,
    steviloIzbranih: zetoni.length,
    moznosti,
    prikazani,
    razvrstitev,
    nastaviRazvrstitev,
    preklopi,
    nastaviSkupino,
    nastaviMejo,
    pocistiObmocje,
    pocisti: () => {
      nastaviIzbor({})
      nastaviMeje({})
    },
  }
}

/* Napis meje za zeton in za naslov skupine v oknu; null, kadar meje ni. */
function opisMeje(meja: Meja | undefined): string | null {
  if (!meja || (meja.od === null && meja.do === null)) return null
  if (meja.od === null) return `do ${meja.do}`
  if (meja.do === null) return `od ${meja.od}`
  return `${meja.od}–${meja.do}`
}

type StanjeFiltrov<T> = ReturnType<typeof useFiltri<T>>

/* Vrstica krmil nad seznamom: gumb filtra, izbor razvrstitve in (na namizju)
   prosto mesto za dodatek strani - npr. uro zadnje osvezitve. */
export function KrmilaSeznama<T>({
  stanje,
  razvrstitve,
  naslovOkna,
  imeZadetkov,
  poFiltru,
  desno,
}: {
  stanje: StanjeFiltrov<T>
  razvrstitve: Razvrstitev<T>[]
  /* Nadnaslov okna ("Turnirji", "Lige") - okno se sicer bere brez konteksta. */
  naslovOkna: string
  /* Ime zadetkov v mnozini za gumb "Pokaži 42 turnirjev". */
  imeZadetkov: (n: number) => string
  /* Zunanje krmilo takoj za gumbom filtra (koledar: izbirnik vrste). Stoji v
     ISTI vrsti in ne nad njo, ker je del istega vprasanja "kaj vidim". */
  poFiltru?: React.ReactNode
  desno?: React.ReactNode
}) {
  const [odprto, nastaviOdprto] = useState(false)
  const { steviloIzbranih, zetoni, moznosti, razponi, prikazani, razvrstitev, nastaviRazvrstitev } =
    stanje
  const imeRazvrstitve = razvrstitve.find((r) => r.kljuc === razvrstitev)?.oznaka ?? ''
  const jeCesaFiltrirati = moznosti.length > 0 || razponi.length > 0

  return (
    <>
      {/* Z zunanjim krmilom so v vrsti trije deli in na 390 px se v eno ne
          zlozijo - takrat se sme prelomiti (glej .krmila--zavita). */}
      <div className={'krmila' + (poFiltru ? ' krmila--zavita' : '')}>
        {/* Gumb nosi stevilo izbranih meril, ne njihovih imen: imena so v
            zetonih pod njim, tu bi jih bilo pri treh filtrih ze cez dve
            vrsti. Ce ni izbrano nic, ostane sam napis. */}
        {jeCesaFiltrirati && (
          <button
            type="button"
            className={'krmila__filter' + (steviloIzbranih > 0 ? ' krmila__filter--aktiven' : '')}
            aria-haspopup="dialog"
            aria-expanded={odprto}
            onClick={() => nastaviOdprto(true)}
          >
            Filtriraj
            {steviloIzbranih > 0 && (
              <span className="krmila__stevec">
                {steviloIzbranih}
                <span className="samo-za-bralnik"> izbranih meril</span>
              </span>
            )}
          </button>
        )}

        {poFiltru}

        {razvrstitve.length > 1 && (
          <label className="krmilo-izbor">
            <span className="samo-za-bralnik">Razvrsti po</span>
            <span className="krmilo-izbor__oznaka" aria-hidden="true">
              <span className="krmilo-izbor__predpona">Razvrsti:</span> {imeRazvrstitve}
              <span className="krmilo-izbor__puscica">▾</span>
            </span>
            <select
              className="krmilo-izbor__polje"
              value={razvrstitev}
              onChange={(dogodek) => nastaviRazvrstitev(dogodek.target.value)}
            >
              {razvrstitve.map((r) => (
                <option key={r.kljuc} value={r.kljuc}>
                  {r.oznaka}
                </option>
              ))}
            </select>
          </label>
        )}

        {desno && <span className="krmila__desno">{desno}</span>}
      </div>

      {zetoni.length > 0 && (
        <div className="zetoni">
          {zetoni.map((z) => (
            <button
              type="button"
              key={`${z.skupina}:${z.vrednost}`}
              className="zeton"
              onClick={() =>
                z.obmocje ? stanje.pocistiObmocje(z.skupina) : stanje.preklopi(z.skupina, z.vrednost)
              }
            >
              <span className="zeton__oznaka">{z.oznaka}</span>
              <span className="zeton__vrednost">{z.napis}</span>
              <span className="zeton__odstrani" aria-hidden="true">
                ✕
              </span>
              <span className="samo-za-bralnik">— odstrani filter</span>
            </button>
          ))}
          <button type="button" className="zetoni__pocisti" onClick={stanje.pocisti}>
            Počisti vse
          </button>
        </div>
      )}

      {odprto && (
        <FiltriOkno
          nadnaslov={naslovOkna}
          skupine={moznosti}
          izbor={stanje.izbor}
          razponi={razponi}
          meje={stanje.meje}
          steviloIzbranih={steviloIzbranih}
          steviloZadetkov={prikazani.length}
          imeZadetkov={imeZadetkov}
          naPreklop={stanje.preklopi}
          naMejo={stanje.nastaviMejo}
          naPocisti={stanje.pocisti}
          onZapri={() => nastaviOdprto(false)}
        />
      )}
    </>
  )
}

/* Okno z merili. Izbira se prevesi TAKOJ (gumba "Uporabi" ni): seznam za
   oknom se sproti zozi, gumb na dnu pa samo pove, koliko ostane, in okno
   zapre - isto vedenje kot okno za izbor lig. */
function FiltriOkno({
  nadnaslov,
  skupine,
  izbor,
  razponi,
  meje,
  steviloIzbranih,
  steviloZadetkov,
  imeZadetkov,
  naPreklop,
  naMejo,
  naPocisti,
  onZapri,
}: {
  nadnaslov: string
  skupine: Skupina[]
  izbor: IzborFiltra
  razponi: Razpon[]
  meje: Record<string, Meja>
  steviloIzbranih: number
  steviloZadetkov: number
  imeZadetkov: (n: number) => string
  naPreklop: (skupina: string, vrednost: string) => void
  naMejo: (obmocje: string, stran: 'od' | 'do', vrednost: number | null) => void
  naPocisti: () => void
  onZapri: () => void
}) {
  return (
    <ModalnoOkno naslov="Filtriraj po" nadnaslov={nadnaslov} onZapri={onZapri}>
      <div className="filtri">
        {skupine.map((s) => (
          <SkupinaMeril
            key={s.kljuc}
            skupina={s}
            izbrane={izbor[s.kljuc] ?? []}
            naPreklop={naPreklop}
          />
        ))}
        {/* Obmocja stojijo za nastetimi merili: dve polji sta drugacno
            opravilo od odkljukavanja in med skupinami bi vrsto pretrgali. */}
        {razponi.map((r) => (
          <MeriloObmocja
            key={r.kljuc}
            razpon={r}
            meja={meje[r.kljuc] ?? { od: null, do: null }}
            naMejo={naMejo}
          />
        ))}
      </div>

      <div className="filtri__noga">
        <button
          type="button"
          className="gumb gumb--majhen"
          disabled={steviloIzbranih === 0}
          onClick={naPocisti}
        >
          Počisti
        </button>
        <button type="button" className="gumb gumb--glavni" onClick={onZapri}>
          Pokaži {steviloZadetkov} {imeZadetkov(steviloZadetkov)}
        </button>
      </div>
    </ModalnoOkno>
  )
}

/* Zvezno merilo: dve stevilski polji. Vpisano se prevesi TAKOJ (kot kljukica
   pri nastetih merilih) - prazno polje pomeni "brez meje na tej strani", zato
   gumba "uporabi" ni. Namestnica (placeholder) je dejanski razpon seznama:
   organizator vidi, med cim sploh reze.

   Polji sta type="number": na telefonu odpreta stevilcno tipkovnico, in ker
   sta meji ratinga celi stevili, drugih znakov ni treba loviti. */
function MeriloObmocja({
  razpon,
  meja,
  naMejo,
}: {
  razpon: Razpon
  meja: Meja
  naMejo: (obmocje: string, stran: 'od' | 'do', vrednost: number | null) => void
}) {
  const opis = opisMeje(meja)

  /* Prazno polje je "brez meje" in ne 0; nesmiselnega vnosa (crke) ne
     zapisemo, ker bi meja tiho postala null in seznam bi se odprl nazaj. */
  const preberi = (besedilo: string): number | null => {
    if (besedilo.trim() === '') return null
    const stevilo = Number(besedilo)
    return Number.isFinite(stevilo) ? Math.round(stevilo) : null
  }

  return (
    <div className="filtri__skupina">
      <h3 className="filtri__naslov">
        {razpon.oznaka}
        {opis !== null && <span className="filtri__izbranih">{opis}</span>}
      </h3>

      <div className="filtri__obmocje">
        <label className="filtri__meja">
          <span className="filtri__meja-oznaka">Od</span>
          <input
            type="number"
            inputMode="numeric"
            value={meja.od ?? ''}
            placeholder={String(razpon.najmanj)}
            onChange={(dogodek) => naMejo(razpon.kljuc, 'od', preberi(dogodek.target.value))}
          />
        </label>
        <label className="filtri__meja">
          <span className="filtri__meja-oznaka">Do</span>
          <input
            type="number"
            inputMode="numeric"
            value={meja.do ?? ''}
            placeholder={String(razpon.najvec)}
            onChange={(dogodek) => naMejo(razpon.kljuc, 'do', preberi(dogodek.target.value))}
          />
        </label>
      </div>

      {razpon.namig && <p className="filtri__prazno">{razpon.namig}</p>}
    </div>
  )
}

function SkupinaMeril({
  skupina,
  izbrane,
  naPreklop,
}: {
  skupina: Skupina
  izbrane: string[]
  naPreklop: (skupina: string, vrednost: string) => void
}) {
  const [iskanje, nastaviIskanje] = useState('')
  const [razsirjena, nastaviRazsirjeno] = useState(false)

  const dolga = skupina.moznosti.length > PRAG_ISKANJA
  const iskano = iskanje.trim().toLocaleLowerCase('sl')
  const najdene = iskano
    ? skupina.moznosti.filter((m) => m.napis.toLocaleLowerCase('sl').includes(iskano))
    : skupina.moznosti

  /* Izbrane ostanejo vidne tudi v skrceni skupini: sicer bi bila kljukica pod
     gumbom "Pokaži vse" in bi se videlo, da je filter izbran, samo po zetonu. */
  const skrcena = dolga && !razsirjena && !iskano
  const prikazane = skrcena
    ? najdene.filter((m, i) => i < PRIKAZANIH_SKRCENO || izbrane.includes(m.vrednost))
    : najdene
  const skritih = najdene.length - prikazane.length

  return (
    <div className="filtri__skupina">
      <h3 className="filtri__naslov">
        {skupina.oznaka}
        {izbrane.length > 0 && <span className="filtri__izbranih">izbranih {izbrane.length}</span>}
      </h3>

      {dolga && (
        <label className="filtri__iskanje">
          <span className="samo-za-bralnik">Poišči v skupini {skupina.oznaka}</span>
          <input
            type="search"
            value={iskanje}
            placeholder={`Poišči (${skupina.moznosti.length})`}
            onChange={(dogodek) => nastaviIskanje(dogodek.target.value)}
          />
        </label>
      )}

      {prikazane.map((m) => {
        const izbrana = izbrane.includes(m.vrednost)
        return (
          <button
            type="button"
            key={m.vrednost}
            className={'filtri__vrstica' + (izbrana ? ' filtri__vrstica--izbrana' : '')}
            aria-pressed={izbrana}
            onClick={() => naPreklop(skupina.kljuc, m.vrednost)}
          >
            {/* Isti kvadratek kot pri spremljanju lig; stanje pove
                aria-pressed, znak je samo slika. */}
            <span className={'kljukica' + (izbrana ? ' kljukica--polna' : '')} aria-hidden="true" />
            <span className="filtri__ime">{m.napis}</span>
            <span className="filtri__stevec">{m.stevec}</span>
          </button>
        )
      })}

      {najdene.length === 0 && <p className="filtri__prazno">Ni zadetkov.</p>}

      {skritih > 0 && (
        <button
          type="button"
          className="gumb gumb--majhen filtri__vec"
          onClick={() => nastaviRazsirjeno(true)}
        >
          Pokaži vse ({najdene.length})
        </button>
      )}
    </div>
  )
}
