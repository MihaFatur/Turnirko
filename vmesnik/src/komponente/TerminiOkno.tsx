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
   prestavljenega srečanja sledi dnevu kola, dokler organizator ne vpiše svojega.

   Prikaz (prenova, design_handoff_termini_prehodi, 2a) zamenja samo postavitev:
   vse poti podatkov — napolni, pobriši, premakni, vrni, sestaviVnos — ostanejo. */
import { useMemo, useState, type FormEvent } from 'react'
import { useMutation } from '@tanstack/react-query'

import { ligeApi } from '../api/zahteve'
import type { LigaDto, SrecanjeDto, TerminiVnos } from '../api/tipi'
import { oblikujDanMesec } from '../pomozno/oblikovanje'
import { terminKola } from '../pomozno/prestavitve'
import {
  danKratica,
  kolTekst,
  kratkoDatum,
  najpogostejsa,
  prestavitevTekst,
  prestej,
  razlikaDni,
} from '../pomozno/terminiKola'
import { IskalniIzbirnik, type MoznostIzbirnika } from './IskalniIzbirnik'
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
const NAJVEC_DNI = 365
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
  /* Razmik po meri je vse razen 7 in 14 dni; »Drugo« se izbere tudi ročno. */
  const [razmikPoMeri, nastaviRazmikPoMeri] = useState(() => razmik !== 7 && razmik !== 14)
  /* Odigrana kola so privzeto skrita: ob sezoni je jih večina že za nami, pogled
     na načrt pa naj bo čist. Skrita se shranijo kot vsa druga. */
  const [pokaziOdigrana, nastaviPokaziOdigrana] = useState(false)
  /* Kola, pri katerih so srečanja razprta (liga z urami); samo za pregled. */
  const [odprta, nastaviOdprta] = useState<Set<number>>(() => new Set())

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

  /* Ura kroga kola: zapiše se vsem neprestavljenim srečanjem tega kola in tega
     kroga — kolo ima eno uro za vse svoje »prvo« srečanje, ki pa ni vedno
     isto. Prestavljeno srečanje ima svojo uro in je ne prepiše. */
  function urediKrog(kolo: number, krog: number, ura: string) {
    nastaviVnose((prej) =>
      prej.map((s) => (s.kolo === kolo && s.uraVKolu === krog && !jePrestavljeno(s) ? { ...s, ura } : s)),
    )
  }

  /* Srečanje, ki v kolu nosi dani krog (neprestavljeno); po njem se bere ura
     kroga. Brez njega je polje kroga prazno in onemogočeno. */
  function srecanjeZaKrog(kolo: number, krog: number): VnosSrecanja | undefined {
    return vnosi.find((s) => s.kolo === kolo && s.uraVKolu === krog && !jePrestavljeno(s))
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

  function preklopiKolo(kolo: number) {
    nastaviOdprta((prej) => {
      const nova = new Set(prej)
      if (nova.has(kolo)) nova.delete(kolo)
      else nova.add(kolo)
      return nova
    })
  }

  /* Odigrano kolo: vsa njegova srečanja so odigrana (prazno kolo ni odigrano). */
  const odigranoKolo = (kolo: number) => {
    const vKolu = vnosi.filter((s) => s.kolo === kolo)
    return vKolu.length > 0 && vKolu.every((s) => s.odigrano)
  }
  const odigranih = kola.filter((k) => odigranoKolo(k.kolo)).length
  /* Prvo kolo, ki ni v celoti odigrano; po njem se oznaka »naslednje«. */
  const naslednjeKolo = kola.find((k) => !odigranoKolo(k.kolo))?.kolo ?? null
  const prikazana = pokaziOdigrana ? kola : kola.filter((k) => !odigranoKolo(k.kolo))

  /* Modus dneva in razmika čez VSA kola, tudi skrita: rjavo pomeni odstopanje
     od tistega, kar je v razporedu običajno. Datume računamo v UTC (kot prestej). */
  const datumi = kola.filter((k) => k.datum)
  const razmikPrej = new Map<number, number>()
  datumi.forEach((k, i) => {
    if (i > 0) razmikPrej.set(k.kolo, razlikaDni(datumi[i - 1].datum, k.datum))
  })
  const modusDan = najpogostejsa(datumi.map((k) => danKratica(k.datum)))
  const modusRazmik = najpogostejsa([...razmikPrej.values()])
  const odstopaDan = (k: VnosKola) =>
    k.datum !== '' && modusDan !== null && danKratica(k.datum) !== modusDan
  const odstopaRazmik = (kolo: number) =>
    modusRazmik !== null && razmikPrej.has(kolo) && razmikPrej.get(kolo) !== modusRazmik
  const rjavo = datumi.some((k) => odstopaDan(k) || odstopaRazmik(k.kolo))

  /* Stanje za nogo: kaj je spremenjeno glede na to, kar je bilo shranjeno. */
  const spremenjeno =
    JSON.stringify({ kola, vnosi }) !== JSON.stringify({ kola: zacetno.kola, vnosi: zacetno.srecanja })
  const kolaSpremenjena = kola.filter((k, i) =>
    k.datum !== zacetno.kola[i]?.datum || k.ura !== zacetno.kola[i]?.ura).length
  const prestavitveSpremenjene = vnosi.filter((s, i) => {
    const z = zacetno.srecanja[i]
    if (!z || !(jePrestavljeno(s) || jePrestavljeno(z))) return false
    return s.kolo !== z.kolo || s.datum !== z.datum || s.ura !== z.ura || s.razlog !== z.razlog
  }).length

  function stanjeBesedilo(): string {
    if (!spremenjeno) return 'Ni neshranjenih sprememb'
    const deli: string[] = []
    if (kolaSpremenjena > 0) deli.push(`${kolaSpremenjena} ${kolTekst(kolaSpremenjena)}`)
    if (prestavitveSpremenjene > 0) deli.push(`${prestavitveSpremenjene} ${prestavitevTekst(prestavitveSpremenjene)}`)
    return deli.length > 0 ? `Neshranjeno: ${deli.join(' · ')}` : 'Neshranjene spremembe'
  }

  /* Povzetek razdelka »Kola«: število, odigrana in prvi ter zadnji datum. */
  const prviDatum = datumi[0]?.datum
  const zadnjiDatum = datumi[datumi.length - 1]?.datum
  const povzetek = !prviDatum || !zadnjiDatum
    ? `${kola.length} ${kolTekst(kola.length)} · brez terminov`
    : `${kola.length} ${kolTekst(kola.length)} · odigrano ${odigranih} · ${kratkoDatum(prviDatum)} – ${kratkoDatum(zadnjiDatum)} ${zadnjiDatum.slice(0, 4)}`

  /* Srečanja, ki jih je mogoče prestaviti: neprestavljena, po kolu iz žreba. */
  const zaIzbiro: MoznostIzbirnika[] = vnosi
    .filter((s) => !jePrestavljeno(s))
    .sort((a, b) => a.izvirnoKolo - b.izvirnoKolo || a.id - b.id)
    .map((s) => ({
      id: s.id,
      ime: `${s.domaci} – ${s.gost}`,
      podrobnost: `${s.izvirnoKolo}. kolo${s.odigrano ? ' · odigrano' : ''}`,
      iskalno: `${s.domaci} ${s.gost} kolo ${s.izvirnoKolo}`,
    }))

  return (
    <ModalnoOkno srednje naslov="Termini kol" podnaslov={`${liga.ime} · ${liga.sezona ?? ''}`.trim()} onZapri={onZapri}>
      <form className="obrazec" onSubmit={obOddaji}>
        <p className="termini__uvod">
          {zUrami
            ? 'Dan velja za vsa srečanja kola, ura pa za vsako srečanje posebej. Urejaš jih lahko tudi med sezono.'
            : 'Termin velja za vsa srečanja kola. Urejaš jih lahko tudi med sezono.'}
        </p>

        <div className="termini__orodje">
          <span className="termini__orodje-naslov">Napolni po razmiku</span>
          <div className="termini__orodje-polja">
            <label className="termini__orodje-polje">
              <span className="oznaka-polja">Prvo kolo</span>
              <input type="date" value={odDatuma} onChange={(d) => nastaviOdDatuma(d.target.value)} />
            </label>
            {zUrami ? (
              odUr.map((ura, i) => (
                <label key={i} className="termini__orodje-polje termini__orodje-polje--krog">
                  <span className="oznaka-polja">{i + 1}. krog</span>
                  <input type="time" value={ura}
                    onChange={(d) => nastaviOdUr((prej) => prej.map((u, j) => (j === i ? d.target.value : u)))} />
                </label>
              ))
            ) : (
              <label className="termini__orodje-polje termini__orodje-polje--ura">
                <span className="oznaka-polja">Ura</span>
                <input type="time" value={odUre} onChange={(d) => nastaviOdUre(d.target.value)} />
              </label>
            )}
            <div className="termini__orodje-razmik">
              <span className="oznaka-polja">Razmik</span>
              <div className="izbirnik" role="group" aria-label="Razmik med kola">
                <button type="button"
                  className={izbirnikGumb(!razmikPoMeri && razmik === 7)}
                  aria-pressed={!razmikPoMeri && razmik === 7}
                  onClick={() => { nastaviRazmik(7); nastaviRazmikPoMeri(false) }}>
                  7 dni
                </button>
                <button type="button"
                  className={izbirnikGumb(!razmikPoMeri && razmik === 14)}
                  aria-pressed={!razmikPoMeri && razmik === 14}
                  onClick={() => { nastaviRazmik(14); nastaviRazmikPoMeri(false) }}>
                  14 dni
                </button>
                <button type="button"
                  className={izbirnikGumb(razmikPoMeri)}
                  aria-pressed={razmikPoMeri}
                  onClick={() => nastaviRazmikPoMeri(true)}>
                  Drugo
                </button>
              </div>
            </div>
            {razmikPoMeri && (
              <label className="termini__orodje-polje termini__orodje-polje--dni">
                <span className="oznaka-polja">Dni</span>
                <StevilskoPolje najvec={NAJVEC_DNI} vrednost={String(razmik)}
                  naSpremembo={(v) => nastaviRazmik(Number(v))} />
              </label>
            )}
          </div>
          <div className="termini__orodje-dejanja">
            <button type="button" className="gumb" disabled={!odDatuma} onClick={napolni}>
              Napolni vsa kola
            </button>
            <button type="button" className="gumb gumb--majhen" onClick={pobrisi}>
              Zbriši vse
            </button>
            {/* Opomba je v isti mreži kot gumba: pri tej širini se prelomi v svojo
                vrstico, ne pa da bi za njo ostal dodaten odmik orodja. */}
            <p className="termini__orodje-opomba">Prepiše datume vseh kol; prestavljena srečanja ostanejo.</p>
          </div>
        </div>

        <div className="termini__kola">
          <div className="termini__razdelek">
            <span>Kola</span>
            <span className="termini__razdelek-povzetek">{povzetek}</span>
          </div>
          <div className="termini__tabela">
            <div className="termini__glava">
              <span>Kolo</span>
              <span>Datum</span>
              <span>Dan</span>
              <span>{zUrami ? 'Ure krogov' : 'Ura'}</span>
            </div>
            {odigranih > 0 && (
              <div className="termini__skrita">
                <span>{pokaziOdigrana ? 'Odigrana kola prikazana' : skritoBesedilo(odigranih)}</span>
                <button type="button" className="gumb gumb--majhen"
                  onClick={() => nastaviPokaziOdigrana(!pokaziOdigrana)}>
                  {pokaziOdigrana ? 'Skrij' : 'Pokaži'}
                </button>
              </div>
            )}

            {prikazana.map((k) => {
              const vKolu = vnosi.filter((s) => s.kolo === k.kolo)
              const odigrano = odigranoKolo(k.kolo)
              const oznaka = odigrano ? 'odigrano' : k.kolo === naslednjeKolo ? 'naslednje' : null
              const razsirjeno = odprta.has(k.kolo)
              const razmikKola = razmikPrej.get(k.kolo)
              const dan = k.datum ? danKratica(k.datum) : '—'
              const danRazredi = 'termini__dan'
                + (odstopaDan(k) ? ' termini__dan--odstopa' : '')
                + (odstopaRazmik(k.kolo) ? ' termini__dan--razmik-odstopa' : '')
              return (
                <div key={k.kolo} className="termini__vrstica">
                  <div className="termini__celice">
                    <div className="termini__kolo-stolpec">
                      <span className={'termini__kolo' + (odigrano ? ' termini__kolo--odigrano' : '')}>
                        {k.kolo}. kolo
                      </span>
                      {oznaka && (
                        <span className={'termini__oznaka' + (oznaka === 'naslednje' ? ' termini__oznaka--naslednje' : '')}>
                          {oznaka}
                        </span>
                      )}
                      {zUrami && (
                        <button type="button" className="termini__srecanja-gumb"
                          aria-expanded={razsirjeno} onClick={() => preklopiKolo(k.kolo)}>
                          {razsirjeno ? 'Skrij ▴' : `${vKolu.length} srečanj ▾`}
                        </button>
                      )}
                    </div>

                    <input type="date" className="termini__vnos" aria-label={`Datum ${k.kolo}. kola`}
                      value={k.datum} onChange={(d) => uredi(k.kolo, { datum: d.target.value })} />

                    <div className={danRazredi}>
                      <span className="termini__dan-ime">{dan}</span>
                      <span className="termini__dan-razmik">
                        {k.datum && razmikKola !== undefined ? formatRazmika(razmikKola) : ''}
                      </span>
                    </div>

                    {zUrami ? (
                      <div className="termini__ure">
                        {odUr.map((_, krog) => {
                          const s = srecanjeZaKrog(k.kolo, krog)
                          return (
                            <input key={krog} type="time"
                              className="termini__vnos termini__vnos--krog"
                              aria-label={`${krog + 1}. krog ${k.kolo}. kola`}
                              value={s?.ura ?? ''} disabled={!s || !k.datum}
                              onChange={(d) => urediKrog(k.kolo, krog, d.target.value)} />
                          )
                        })}
                      </div>
                    ) : (
                      <input type="time" className="termini__vnos termini__vnos--ura"
                        aria-label={`Ura ${k.kolo}. kola`}
                        value={k.ura} disabled={!k.datum}
                        onChange={(d) => uredi(k.kolo, { ura: d.target.value })} />
                    )}
                  </div>

                  {zUrami && razsirjeno && (
                    <div className="termini__srecanja">
                      {[...vKolu].sort((a, b) => a.uraVKolu - b.uraVKolu || a.id - b.id).map((s) => (
                        <div key={s.id} className="termini__srecanje">
                          <input type="time" className="termini__vnos termini__vnos--krog"
                            aria-label={`Ura srečanja ${s.domaci} – ${s.gost}, ${k.kolo}. kolo`}
                            value={s.ura} disabled={!(s.datum ?? k.datum)}
                            onChange={(d) => urediSrecanje(s.id, { ura: d.target.value })} />
                          <span className="termini__srecanje-krog">{s.uraVKolu + 1}. krog</span>
                          <span>
                            {s.domaci} – {s.gost}
                            {s.kolo !== s.izvirnoKolo && (
                              <span className="termini__srecanje-iz">iz {s.izvirnoKolo}. kola</span>
                            )}
                          </span>
                        </div>
                      ))}
                    </div>
                  )}
                </div>
              )
            })}
          </div>
        {rjavo && (
          <p className="termini__legenda">
            Rjavo: dan ali razmik odstopa od običajnega ({modusDan}
            {modusRazmik !== null ? `, na ${modusRazmik} dni` : ''}).
          </p>
        )}
        </div>

        <div className="termini__prestavitve">
          <div className="termini__razdelek">
            <span>Prestavitve</span>
            <span className="termini__razdelek-povzetek">
              {prestavljena.length > 0 ? prestavljena.length : ''}
            </span>
          </div>
          <p className="termini__prestavitve-opis">
            Srečanje, ki se ne igra v svojem kolu, premakni v kolo, kjer se bo igralo; v razporedu stoji
            samo tam. Če novi dan še ni znan, ga pusti brez termina.
          </p>

          <IskalniIzbirnik
            vObrazcu
            oznaka="Prestavi srečanje"
            namig="Vpiši ekipo ali kolo, npr. »velenje« ali »7«"
            moznosti={zaIzbiro}
            naIzbiro={(id) => nastaviUrejano(id)}
          />

          {urejanoSrecanje && (
            <UrejevalnikPrestavitve
              s={urejanoSrecanje}
              kola={kola}
              zUrami={zUrami}
              onSpremeni={(popravek) => urediSrecanje(urejanoSrecanje.id, popravek)}
              onPremakni={(vrednost) => premakni(urejanoSrecanje, vrednost)}
              onVrni={() => vrni(urejanoSrecanje)}
              onZapri={() => nastaviUrejano(null)}
            />
          )}

          {prestavljena.length > 0 ? (
            <ul className="termini__prestavljena">
              {prestavljena.map((s) => (
                <li key={s.id} className="termini__prestavljeno">
                  <div>
                    <span className="termini__prestavljeno-ime">{s.domaci} – {s.gost}</span>
                    <span className="termini__oznaka-srecanja">
                      {s.kolo === null
                        ? `iz ${s.izvirnoKolo}. kola · čaka na termin`
                        : `iz ${s.izvirnoKolo}. v ${s.kolo}. kolo`}
                    </span>
                    {s.razlog && <div className="termini__prestavljeno-razlog">{s.razlog}</div>}
                  </div>
                  <button type="button" className="gumb gumb--majhen"
                    aria-expanded={urejano === s.id}
                    onClick={() => nastaviUrejano(urejano === s.id ? null : s.id)}>
                    {urejano === s.id ? 'Zapri' : 'Uredi'}
                  </button>
                </li>
              ))}
            </ul>
          ) : (
            <p className="termini__prazno">Ni prestavljenih srečanj.</p>
          )}
        </div>

        <SporociloNapake napaka={shranjevanje.error} />
        <div className="obrazec__noga">
          <span className={'obrazec__noga-stanje' + (spremenjeno ? ' obrazec__noga-stanje--spremembe' : '')}>
            {stanjeBesedilo()}
          </span>
          <div className="obrazec__noga-gumbi">
            <button type="button" className="gumb" onClick={onZapri}>Prekliči</button>
            <button type="submit" className="gumb gumb--glavni"
              disabled={!spremenjeno || shranjevanje.isPending}>
              Shrani termine
            </button>
          </div>
        </div>
      </form>
    </ModalnoOkno>
  )
}

function izbirnikGumb(aktiven: boolean): string {
  return 'izbirnik__gumb izbirnik__gumb--polje' + (aktiven ? ' izbirnik__gumb--aktiven' : '')
}

/* Razmik v oknu: »+7 dni«, »+1 dan«; negativnega (kolo pred prejšnjim) z minusom. */
function formatRazmika(n: number): string {
  if (n === 1) return '+1 dan'
  if (n < 0) return `−${Math.abs(n)} dni`
  return `+${n} dni`
}

/* Opomba o skritih odigranih kolih; oblika se sklanja po številu. */
function skritoBesedilo(n: number): string {
  if (n === 1) return '1 odigrano kolo skrito'
  if (n <= 4) return `${n} odigrana kola skrita`
  return `${n} odigranih kol skritih`
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
          <span className="termini__razlog-oznaka">
            <span>Razlog (neobvezno)</span>
            <span>{s.razlog.length}/{NAJVEC_ZNAKOV_RAZLOGA}</span>
          </span>
          <input type="text" value={s.razlog} maxLength={NAJVEC_ZNAKOV_RAZLOGA}
            placeholder="npr. dvorana zasedena"
            onChange={(d) => onSpremeni({ razlog: d.target.value })} />
          <span className="termini__razlog-pojasnilo">
            Razlog vidijo vsi obiskovalci. Brez osebnih podatkov (zdravje, naslovi).
          </span>
        </label>
      )}

      <div className="termini__urejevalnik-dejanja">
        {prestavljeno && (
          <button type="button" className="gumb gumb--majhen termini__vrni" onClick={onVrni}>
            Vrni v {s.izvirnoKolo}. kolo
          </button>
        )}
        <button type="button" className="gumb" onClick={onZapri}>Končano</button>
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
