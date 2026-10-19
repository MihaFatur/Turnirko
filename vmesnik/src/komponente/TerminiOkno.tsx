/* Termini kol lige: kdaj se igra katero kolo in katera srečanja so prestavljena.

   Zakaj svoje okno in ne del pravil lige: pravila (format, nizi, točkovanje) se
   po generiranju razporeda zaklenejo, ker bi popravek razveljavil odigrano —
   termin pa se mora dati popraviti tudi sredi sezone (kolo se prestavi). Poleg
   tega se pravila vpišejo ob ustvarjanju lige, ko ekip (in s tem števila kol)
   še ni; seznam vseh kol je mogoč šele tu, po žrebu.

   Obrazec lige zato nosi le seme (prvo kolo + razmik), iz katerega žreb
   izračuna datume; to okno je ročni popravek. Prestavljeno kolo NE premakne
   naslednjih — ta so že objavljena in bi jih tiho zamaknilo.

   Dan je last kola: kolo se odigra en dan. Ura je pri kolu z enim krogom ena
   za vsa srečanja, liga z urami kola (večer z več krogi zapored) pa ima uro pri
   vsakem srečanju — polnilo jo vzame iz ure kroga, organizator pa jo sme
   srečanju prestaviti. Ura je neobvezna; prazna se shrani kot 00:00 in v
   razporedu ne izpiše.

   PRESTAVITEV SREČANJA (V47) je del istega obrazca, ker je tudi ona termin:
   ekipi sta se dogovorili za zamenjavo ali srečanje ne bo ob načrtovanem dnevu.
   Srečanje se premakne v kolo, v katerem se bo igralo — razpored ga kaže SAMO
   tam, zato igralec v vsakem kolu vidi, s kom je igral in kaj je odigrano — ali
   pa ostane brez termina, dokler dan ni znan (seznam čakajočih nad razporedom).
   Premakne se lahko tudi odigrano srečanje (poznega kola, odigrano že zdaj) in
   neodigrano, ki bi moralo biti že odigrano.

   Prestavitve imajo svoj razdelek z enim urejevalnikom in ne gumba pri vsakem
   srečanju: liga brez ur ima kolo v eni vrstici, 70 srečanj v seznamu pa bi
   pomenilo 70 gumbov, ki jih organizator v letu uporabi trikrat. Dan
   prestavljenega srečanja sledi dnevu kola, dokler organizator ne vpiše svojega. */
import { useMemo, useState, type FormEvent } from 'react'
import { useMutation } from '@tanstack/react-query'

import { ligeApi } from '../api/zahteve'
import type { LigaDto, SrecanjeDto, TerminiVnos } from '../api/tipi'
import { oblikujDanMesec } from '../pomozno/oblikovanje'
import { terminKola } from '../pomozno/prestavitve'
import { ModalnoOkno } from './ModalnoOkno'
import { StevilskoPolje } from './StevilskoPolje'
import { SporociloNapake } from './SporociloNapake'

interface Lastnosti {
  liga: LigaDto
  /* Vsa srečanja rednega dela lige, tudi čakajoča — iz njih se prebere, katera
     kola obstajajo, kdaj so in katera srečanja so prestavljena. */
  srecanja: SrecanjeDto[]
  onZapri: () => void
  onShranjeno: () => void
}

interface VnosSrecanja {
  id: number
  domaci: string
  gost: string
  /* Kolo iz žreba — vanj se srečanje vrne in po njem je prestavljeno. */
  izvirnoKolo: number
  /* Kolo, v katerem se igra po vnosu; null = čaka na termin. */
  kolo: number | null
  /* Lasten dan prestavljenega srečanja; null = sledi dnevu kola. */
  datum: string | null
  ura: string
  /* Ura kola, ob kateri srečanje igra po razporedu — po njej polnilo vrne
     srečanju uro lige, tudi ko je bilo prestavljeno ali ure še nima. */
  uraVKolu: number
  razlog: string
  /* Stanje na strežniku: po njem se vidi, kaj je vnos spremenil. */
  prej: { kolo: number | null; razlog: string }
  odigrano: boolean
  /* Odigrano ali začeto srečanje ima dan, zato ne more čakati na termin. */
  smeCakati: boolean
}

interface VnosKola {
  kolo: number
  datum: string
  ura: string
}

const PRIVZET_RAZMIK = 7
const NAJVEC_ZNAKOV_RAZLOGA = 200
/* Vrednost izbirnika za srečanje brez termina; kolo je številka. */
const CAKA = 'caka'

function jePrestavljeno(s: VnosSrecanja): boolean {
  return s.kolo === null || s.kolo !== s.izvirnoKolo
}

export function TerminiOkno({ liga, srecanja, onZapri, onShranjeno }: Lastnosti) {
  const zUrami = liga.ureSrecanj != null
  const zacetno = useMemo(() => zacetnoStanje(srecanja), [srecanja])
  const [kola, nastaviKola] = useState<VnosKola[]>(zacetno.kola)
  const [vnosi, nastaviVnose] = useState<VnosSrecanja[]>(zacetno.srecanja)
  /* Srečanje, katerega prestavitev se ureja; eno naenkrat. */
  const [urejano, nastaviUrejano] = useState<number | null>(null)
  /* Polnilo po razmiku: privzetka sta termin prvega kola oz. seme lige, da
     najpogostejši popravek (»vse skupaj teden naprej«) ne zahteva vpisa. */
  const [odDatuma, nastaviOdDatuma] = useState(
    () => zacetno.kola[0]?.datum || (liga.zacetekPrvegaKola?.slice(0, 10) ?? ''),
  )
  const [odUre, nastaviOdUre] = useState(
    () => zacetno.kola[0]?.ura || vpisanaUra(liga.zacetekPrvegaKola),
  )
  /* Pri ligi z urami polnilo nosi ure pravil lige — kolo je večer po njih. */
  const [odUr, nastaviOdUr] = useState<string[]>(
    () => liga.ureSrecanj?.map((u) => vpisanaUra(`T${u}`)) ?? [],
  )
  const [razmik, nastaviRazmik] = useState(liga.razmikDni ?? PRIVZET_RAZMIK)

  const prestavljena = vnosi.filter(jePrestavljeno)
  const urejanoSrecanje = vnosi.find((s) => s.id === urejano) ?? null

  const shranjevanje = useMutation({
    mutationFn: () => ligeApi.termini(liga.id, sestaviVnos(kola, vnosi, zUrami)),
    onSuccess: () => {
      onShranjeno()
      onZapri()
    },
  })

  function obOddaji(dogodek: FormEvent) {
    dogodek.preventDefault()
    shranjevanje.mutate()
  }

  function uredi(kolo: number, popravek: Partial<VnosKola>) {
    nastaviKola((prej) => prej.map((k) => (k.kolo === kolo ? { ...k, ...popravek } : k)))
  }

  function urediSrecanje(id: number, popravek: Partial<VnosSrecanja>) {
    nastaviVnose((prej) => prej.map((s) => (s.id === id ? { ...s, ...popravek } : s)))
  }

  /* Kolo, v katerem se srečanje igra. Dan spet sledi dnevu novega kola, ura
     ostane prazna: srečanje se igra poleg kola, v katerem obe ekipi ob uri
     kola že igrata, zato bi uro kola strežnik zavrnil kot dve srečanji hkrati. */
  function premakni(s: VnosSrecanja, vrednost: string) {
    if (vrednost === CAKA) {
      urediSrecanje(s.id, { kolo: null, datum: null, ura: '' })
      return
    }
    const novo = Number(vrednost)
    if (novo === s.izvirnoKolo) {
      vrni(s)
      return
    }
    urediSrecanje(s.id, { kolo: novo, datum: null, ura: '' })
  }

  /* Nazaj v kolo iz žreba: srečanje spet sledi kolu, razlog ni več potreben. */
  function vrni(s: VnosSrecanja) {
    urediSrecanje(s.id, {
      kolo: s.izvirnoKolo,
      datum: null,
      ura: zUrami ? vpisanaUra(`T${liga.ureSrecanj?.[s.uraVKolu] ?? ''}`) : '',
      razlog: '',
    })
  }

  /* Napolni vsa kola po razmiku od vpisanega začetka. Prepiše tudi že vpisane
     termine — to je namen gumba; posamezno kolo se popravi v vrstici pod njim.
     Pri ligi z urami dobi i-to srečanje kola i-to uro. Prestavljena srečanja
     ostanejo, kakršna so: z njimi se je organizator ukvarjal posebej. */
  function napolni() {
    if (!odDatuma) return
    nastaviKola((prej) =>
      prej.map((k, i) => ({ ...k, datum: prestej(odDatuma, i * razmik), ura: odUre })),
    )
    nastaviVnose((prej) =>
      prej.map((s) => (jePrestavljeno(s) ? s : { ...s, ura: odUr[s.uraVKolu] ?? '' })),
    )
  }

  function pobrisi() {
    nastaviKola((prej) => prej.map((k) => ({ ...k, datum: '', ura: '' })))
    nastaviVnose((prej) =>
      prej.map((s) =>
        s.kolo === null ? s : { ...s, ura: '', datum: s.kolo === s.izvirnoKolo ? null : '' },
      ),
    )
  }

  /* Okno je namenoma navadne širine (brez "siroko"): vrstica nosi le oznako,
     datum in uro - v širokem oknu bi se datumsko polje raztegnilo čez pol
     zaslona in bi izgledalo kot pomota. */
  return (
    <ModalnoOkno naslov="Termini kol" onZapri={onZapri}>
      <form className="obrazec" onSubmit={obOddaji}>
        <p className="obvestilo">
          {zUrami
            ? 'Dan velja za vsa srečanja kola, ura pa za vsako srečanje posebej. Urejaš jih lahko tudi med sezono.'
            : 'Termin velja za vsa srečanja kola. Urejaš jih lahko tudi med sezono.'}
        </p>

        <fieldset className="obrazec__skupina">
          <legend>Napolni po razmiku</legend>
          <div className="termini__polnilo">
            <label className="obrazec__polje">
              <span>Prvo kolo</span>
              <input type="date" value={odDatuma}
                onChange={(d) => nastaviOdDatuma(d.target.value)} />
            </label>
            {!zUrami && (
              <label className="obrazec__polje">
                <span>Ura</span>
                <input type="time" value={odUre} onChange={(d) => nastaviOdUre(d.target.value)} />
              </label>
            )}
            <label className="obrazec__polje">
              <span>Na koliko dni</span>
              <StevilskoPolje najvec={365} vrednost={String(razmik)}
                naSpremembo={(v) => nastaviRazmik(Number(v))} />
            </label>
          </div>
          {zUrami && (
            <div className="obrazec__vrstica obrazec__vrstica--ure">
              {odUr.map((ura, i) => (
                <label key={i} className="obrazec__polje">
                  <span>{i + 1}. srečanje</span>
                  <input type="time" value={ura}
                    onChange={(d) => nastaviOdUr((prej) => prej.map((u, j) => (j === i ? d.target.value : u)))} />
                </label>
              ))}
            </div>
          )}
          <div className="termini__polnilo-gumbi">
            <button type="button" className="gumb gumb--majhen" disabled={!odDatuma}
              onClick={napolni}>
              Napolni vsa kola
            </button>
            <button type="button" className="gumb gumb--majhen" onClick={pobrisi}>
              Zbriši vse
            </button>
          </div>
          <p className="namig">
            Polnilo prepiše vse datume spodaj. Prestavitev enega kola ostalih ne premakne.
          </p>
        </fieldset>

        <div className="termini">
          {kola.map((k) => {
            const vKolu = vnosi.filter((s) => s.kolo === k.kolo)
            const odigrano = vKolu.length > 0 && vKolu.every((s) => s.odigrano)
            return zUrami ? (
              <div key={k.kolo} className="termini__vecer">
                <div className="termini__vrstica termini__vrstica--vecer">
                  <OznakaKola kolo={k.kolo} odigrano={odigrano} />
                  <label className="termini__polje">
                    <span className="samo-za-bralnik">Datum {k.kolo}. kola</span>
                    <input type="date" value={k.datum}
                      onChange={(d) => uredi(k.kolo, { datum: d.target.value })} />
                  </label>
                </div>
                {vKolu.map((s) => (
                  <div key={s.id} className="termini__srecanje">
                    <label className="termini__polje termini__polje--ura">
                      <span className="samo-za-bralnik">
                        Ura srečanja {s.domaci} – {s.gost}, {k.kolo}. kolo
                      </span>
                      <input type="time" value={s.ura} disabled={!(s.datum ?? k.datum)}
                        onChange={(d) => urediSrecanje(s.id, { ura: d.target.value })} />
                    </label>
                    <span className="termini__par">
                      {s.domaci} – {s.gost}
                      {s.kolo !== s.izvirnoKolo && (
                        <span className="termini__oznaka-srecanja">iz {s.izvirnoKolo}. kola</span>
                      )}
                    </span>
                  </div>
                ))}
              </div>
            ) : (
              <div key={k.kolo} className="termini__vrstica">
                <OznakaKola kolo={k.kolo} odigrano={odigrano} />
                <label className="termini__polje">
                  <span className="samo-za-bralnik">Datum {k.kolo}. kola</span>
                  <input type="date" value={k.datum}
                    onChange={(d) => uredi(k.kolo, { datum: d.target.value })} />
                </label>
                <label className="termini__polje termini__polje--ura">
                  <span className="samo-za-bralnik">Ura {k.kolo}. kola</span>
                  <input type="time" value={k.ura} disabled={!k.datum}
                    onChange={(d) => uredi(k.kolo, { ura: d.target.value })} />
                </label>
              </div>
            )
          })}
        </div>

        <Prestavitve
          kola={kola}
          vnosi={vnosi}
          prestavljena={prestavljena}
          urejano={urejanoSrecanje}
          zUrami={zUrami}
          onIzberi={nastaviUrejano}
          onSpremeni={urediSrecanje}
          onPremakni={premakni}
          onVrni={vrni}
        />

        <SporociloNapake napaka={shranjevanje.error} />
        <div className="obrazec__gumbi">
          <button type="button" className="gumb" onClick={onZapri}>Prekliči</button>
          <button type="submit" className="gumb gumb--glavni" disabled={shranjevanje.isPending}>
            Shrani termine
          </button>
        </div>
      </form>
    </ModalnoOkno>
  )
}

function OznakaKola({ kolo, odigrano }: { kolo: number; odigrano: boolean }) {
  return (
    <span className="termini__kolo">
      {kolo}. kolo
      {/* Odigrano kolo se sme popraviti (napačen vpis), a naj bo vidno, da gre
          za preteklost in ne za načrt. */}
      {odigrano && <span className="termini__oznaka">odigrano</span>}
    </span>
  )
}

interface PrestavitveLastnosti {
  kola: VnosKola[]
  vnosi: VnosSrecanja[]
  prestavljena: VnosSrecanja[]
  urejano: VnosSrecanja | null
  zUrami: boolean
  onIzberi: (id: number | null) => void
  onSpremeni: (id: number, popravek: Partial<VnosSrecanja>) => void
  onPremakni: (s: VnosSrecanja, vrednost: string) => void
  onVrni: (s: VnosSrecanja) => void
}

/* Razdelek »Prestavitve«: seznam že prestavljenih srečanj in izbira novega, pod
   njima urejevalnik izbranega. Urejevalnik stoji na enem mestu, da se ob
   spremembi kola ne premakne skupaj s srečanjem, ki ga ureja. */
function Prestavitve({
  kola,
  vnosi,
  prestavljena,
  urejano,
  zUrami,
  onIzberi,
  onSpremeni,
  onPremakni,
  onVrni,
}: PrestavitveLastnosti) {
  /* Srečanja za izbiro: tista, ki so še tam, kamor jih je uvrstil žreb —
     prestavljena so v seznamu zgoraj. Razvrščena po kolu iz žreba, ker
     organizator srečanje išče po tem, kje bi moralo biti. */
  const zaIzbiro = useMemo(() => {
    const poKolih = new Map<number, VnosSrecanja[]>()
    for (const s of vnosi) {
      if (jePrestavljeno(s)) continue
      const v = poKolih.get(s.izvirnoKolo) ?? []
      v.push(s)
      poKolih.set(s.izvirnoKolo, v)
    }
    return [...poKolih.entries()].sort((a, b) => a[0] - b[0])
  }, [vnosi])

  return (
    <fieldset className="obrazec__skupina">
      <legend>Prestavitve{prestavljena.length > 0 ? ` (${prestavljena.length})` : ''}</legend>
      <p className="namig">
        Srečanje, ki se ne igra v svojem kolu, premakni v kolo, v katerem se bo igralo — v razporedu
        stoji samo tam. Če novi dan še ni znan, ga pusti brez termina.
      </p>

      {prestavljena.length > 0 && (
        <ul className="termini__prestavljena">
          {prestavljena.map((s) => (
            <li key={s.id} className="termini__prestavljeno">
              <span className="termini__par">
                {s.domaci} – {s.gost}
                <span className="termini__oznaka-srecanja">
                  {s.kolo === null
                    ? `iz ${s.izvirnoKolo}. kola · čaka na termin`
                    : `iz ${s.izvirnoKolo}. v ${s.kolo}. kolo`}
                </span>
              </span>
              <button type="button" className="gumb gumb--majhen"
                aria-expanded={urejano?.id === s.id}
                onClick={() => onIzberi(urejano?.id === s.id ? null : s.id)}>
                {urejano?.id === s.id ? 'Zapri' : 'Uredi'}
              </button>
            </li>
          ))}
        </ul>
      )}

      <label className="obrazec__polje">
        <span>Prestavi srečanje</span>
        <select
          value=""
          onChange={(d) => {
            if (d.target.value) onIzberi(Number(d.target.value))
          }}
        >
          <option value="">Izberi srečanje …</option>
          {zaIzbiro.map(([kolo, srecanja]) => (
            <optgroup key={kolo} label={`${kolo}. kolo`}>
              {srecanja.map((s) => (
                <option key={s.id} value={s.id}>
                  {s.domaci} – {s.gost}
                  {s.odigrano ? ' (odigrano)' : ''}
                </option>
              ))}
            </optgroup>
          ))}
        </select>
      </label>

      {urejano && (
        <UrejevalnikPrestavitve
          s={urejano}
          kola={kola}
          zUrami={zUrami}
          onSpremeni={(popravek) => onSpremeni(urejano.id, popravek)}
          onPremakni={(vrednost) => onPremakni(urejano, vrednost)}
          onVrni={() => onVrni(urejano)}
          onZapri={() => onIzberi(null)}
        />
      )}
    </fieldset>
  )
}

interface UrejevalnikLastnosti {
  s: VnosSrecanja
  kola: VnosKola[]
  zUrami: boolean
  onSpremeni: (popravek: Partial<VnosSrecanja>) => void
  onPremakni: (vrednost: string) => void
  onVrni: () => void
  onZapri: () => void
}

function UrejevalnikPrestavitve({
  s,
  kola,
  zUrami,
  onSpremeni,
  onPremakni,
  onVrni,
  onZapri,
}: UrejevalnikLastnosti) {
  const caka = s.kolo === null
  const prestavljeno = jePrestavljeno(s)
  const dnevKola = kola.find((k) => k.kolo === s.kolo)?.datum ?? ''
  const dan = s.datum ?? dnevKola

  return (
    <div className="termini__prestavitev">
      <p className="termini__prestavitev-naslov">
        {s.domaci} – {s.gost}
        <span className="termini__oznaka-srecanja">po žrebu {s.izvirnoKolo}. kolo</span>
      </p>

      <label className="obrazec__polje">
        <span>Igra se v</span>
        <select value={caka ? CAKA : String(s.kolo)} onChange={(d) => onPremakni(d.target.value)}>
          {kola.map((k) => (
            <option key={k.kolo} value={k.kolo}>
              {k.kolo}. kolo{k.datum ? ` · ${oblikujDanMesec(k.datum)}` : ''}
              {k.kolo === s.izvirnoKolo ? ' (po žrebu)' : ''}
            </option>
          ))}
          {/* Odigrano ali začeto srečanje ima dan; brez njega bi izid ostal
              nikjer v razporedu. */}
          {(s.smeCakati || caka) && <option value={CAKA}>Termin še ni znan</option>}
        </select>
      </label>

      {!caka && (
        <div className="termini__prestavitev-cas">
          <label className="obrazec__polje">
            <span>Dan</span>
            <input type="date" value={dan}
              onChange={(d) => onSpremeni({ datum: d.target.value })} />
          </label>
          <label className="obrazec__polje">
            <span>Ura</span>
            <input type="time" value={s.ura} disabled={!dan}
              onChange={(d) => onSpremeni({ ura: d.target.value })} />
          </label>
        </div>
      )}

      {!caka && !s.ura && prestavljeno && (
        <p className="namig">
          Brez ure se srečanje igra ob nedoločeni uri tistega dne. Če ekipa v tem kolu že igra,
          vpiši uro, da ne bosta imeli dveh srečanj hkrati.
        </p>
      )}
      {zUrami && !caka && !prestavljeno && (
        <p className="namig">Ura tega srečanja je v seznamu kola zgoraj.</p>
      )}

      {prestavljeno && (
        <label className="obrazec__polje">
          <span>Razlog (neobvezno)</span>
          <input type="text" value={s.razlog} maxLength={NAJVEC_ZNAKOV_RAZLOGA}
            onChange={(d) => onSpremeni({ razlog: d.target.value })} />
          <span className="namig">
            Razlog vidijo vsi obiskovalci. Brez osebnih podatkov (zdravje, naslovi).
          </span>
        </label>
      )}

      <div className="termini__polnilo-gumbi">
        {prestavljeno && (
          <button type="button" className="gumb gumb--majhen" onClick={onVrni}>
            Vrni v {s.izvirnoKolo}. kolo
          </button>
        )}
        <button type="button" className="gumb gumb--majhen" onClick={onZapri}>
          Zapri urejanje
        </button>
      </div>
    </div>
  )
}

/* Vnos, kakršnega pričakuje strežnik.

   Kolo dobi termin kola; prestavljeno srečanje pa vedno nosi SVOJ začetek, da
   ga kasnejša sprememba termina kola ne pogazi in da ga strežnik ne zadene ob
   uro, ob kateri obe ekipi v kolu že igrata. Srečanje brez termina gre kot
   čakajoče. Razlog nosijo samo prestavljena: pri drugih strežnik razloga ne
   bere in ga ne briše. */
function sestaviVnos(kola: VnosKola[], vnosi: VnosSrecanja[], zUrami: boolean): TerminiVnos {
  const poKolih = new Map(kola.map((k) => [k.kolo, k]))
  /* Pri ligi z urami uro kola nosi prvo srečanje, ki v kolu sodi. */
  const uraKola = (k: VnosKola) =>
    zUrami
      ? (vnosi.find((s) => s.kolo === k.kolo && s.kolo === s.izvirnoKolo)?.ura ?? '')
      : k.ura

  return {
    kola: kola.map((k) => ({
      kolo: k.kolo,
      zacetek: k.datum ? `${k.datum}T${uraKola(k) || '00:00'}` : null,
    })),
    srecanja: vnosi.flatMap((s): NonNullable<TerminiVnos['srecanja']> => {
      if (s.kolo === null) {
        return [{ id: s.id, zacetek: null, cakaNaTermin: true, razlog: s.razlog.trim() }]
      }
      const k = poKolih.get(s.kolo)
      const prestavljeno = s.kolo !== s.izvirnoKolo
      const spremenjeno = s.kolo !== s.prej.kolo || s.razlog !== s.prej.razlog
      /* Liga brez ur kolo ureja s terminom kola; posamezno srečanje šteje le,
         če je prestavljeno ali pravkar vrnjeno. */
      if (!zUrami && !prestavljeno && !spremenjeno) return []
      const dan = s.datum ?? k?.datum ?? ''
      const ura = zUrami || prestavljeno ? s.ura : (k?.ura ?? '')
      return [
        {
          id: s.id,
          zacetek: dan ? `${dan}T${ura || '00:00'}` : null,
          kolo: s.kolo,
          razlog: prestavljeno ? s.razlog.trim() : undefined,
        },
      ]
    }),
  }
}

/* Kola iz razporeda s termini, kot so zdaj, in stanje vsakega srečanja. Kolo
   obstaja tudi, če so se vsa njegova srečanja izselila — vanj se da vrniti.
   Termin kola vzamemo iz srečanj, ki v njem sodijo (pomozno/prestavitve):
   srečanje, prestavljeno semkaj, se sme igrati na svoj dan. */
function zacetnoStanje(srecanja: SrecanjeDto[]): { kola: VnosKola[]; srecanja: VnosSrecanja[] } {
  const stevilke = [
    ...new Set(srecanja.flatMap((s) => [s.kolo, s.izvirnoKolo ?? s.kolo])),
  ].sort((a, b) => a - b)
  const kola = stevilke.map((kolo) => {
    const zacetek = terminKola(srecanja, kolo)
    return { kolo, datum: zacetek?.slice(0, 10) ?? '', ura: vpisanaUra(zacetek) }
  })
  const dnevi = new Map(kola.map((k) => [k.kolo, k.datum]))
  const vnosi = srecanja.map((s): VnosSrecanja => {
    const kolo = s.cakaNaTermin ? null : s.kolo
    const izvirno = s.izvirnoKolo ?? s.kolo
    const dan = s.predvidenZacetek?.slice(0, 10) ?? ''
    /* Lasten dan ima samo tisto prestavljeno srečanje, ki se igra drug dan kot
       njegovo kolo; sicer sledi kolu in ga sprememba termina kola premakne. */
    const lastniDan = kolo !== null && kolo !== izvirno && dan !== (dnevi.get(kolo) ?? '')
    const razlog = s.razlogPrestavitve ?? ''
    return {
      id: s.id,
      domaci: s.domaci,
      gost: s.gost,
      izvirnoKolo: izvirno,
      kolo,
      datum: lastniDan ? dan : null,
      ura: vpisanaUra(s.predvidenZacetek),
      uraVKolu: s.uraVKolu ?? 0,
      razlog,
      prej: { kolo, razlog },
      odigrano: s.status === 'KONCANO',
      smeCakati: s.status === 'RAZPORED',
    }
  })
  return { kola, srecanja: vnosi }
}

/* Ura iz ISO datuma-časa za polje obrazca. 00:00 pomeni »ura ni določena«,
   zato se v polje ne vpiše nazaj — sicer bi shranjevanje iz nje naredilo
   vpisano polnoč in razpored bi jo začel izpisovati. */
function vpisanaUra(iso: string | null | undefined): string {
  const t = iso?.split('T')[1] ?? ''
  const ura = t.slice(0, 5)
  return ura === '00:00' ? '' : ura
}

/* Datum + n dni, oboje kot "YYYY-MM-DD". Računamo v UTC, ker bi lokalni čas ob
   prehodu na poletni/zimski čas datum premaknil za dan. */
function prestej(datum: string, dni: number): string {
  const [leto, mesec, dan] = datum.split('-').map(Number)
  if (!leto || !mesec || !dan) return datum
  const cas = new Date(Date.UTC(leto, mesec - 1, dan))
  cas.setUTCDate(cas.getUTCDate() + dni)
  return cas.toISOString().slice(0, 10)
}
