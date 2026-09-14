/* Obrazec za pravila lige — isti za ustvarjanje in za urejanje, da se možnosti
   ne razhajajo. Kadar je podana obstoječa liga, gre za urejanje (polja so
   prednapolnjena, shrani se s PUT). Strežnik urejanje dovoli samo, dokler je
   liga v PRIPRAVI; vmesnik gumb v drugih stanjih samo skrije.

   Mesta lige v piramidi (višja/nižja liga, napredovanje, izpad) tu namenoma
   ni: to ni pravilo tekmovanja, ampak opis sezone, in se sme popravljati tudi
   potem, ko so pravila zaklenjena — ureja ga PrehodiOkno. */
import { useState, type FormEvent } from 'react'
import { useMutation } from '@tanstack/react-query'

import { ligeApi } from '../api/zahteve'
import type { FormatSrecanja, LigaDto, LigaVnos, PredlogaLige, RavenTekmovanja, SpolKategorija } from '../api/tipi'
import {
  FORMATI_SAMO_TURNIR,
  OZNAKE_FORMAT,
  OZNAKE_PREDLOGA_LIGE,
  OZNAKE_RAVEN,
  RAZPORED_FORMATA,
  TEZA_RAVNI,
} from '../api/tipi'
import { ModalnoOkno } from './ModalnoOkno'
import { SporociloNapake } from './SporociloNapake'

interface Lastnosti {
  /* Podana liga = urejanje; brez nje se ustvari nova. */
  liga?: LigaDto
  onZapri: () => void
  onShranjeno: (liga: LigaDto) => void
}

/* Konec srečanja je izbira dveh pravil, ne prazno polje: »vse tekme« je
   enakovredna možnost in mora biti napisana, ne uganjena iz praznega vnosa. */
type KonecSrecanja = 'VSE' | 'PRAG'

/* Privzeti prag = večina tekem (npr. 6 od 10 pri SNTL, 3 od 5 pri Savinji). */
function vecina(format: FormatSrecanja): number {
  return Math.floor(RAZPORED_FORMATA[format].length / 2) + 1
}

/* Liga se praviloma igra tedensko (isto privzeto kot v zaledju). */
const PRIVZET_RAZMIK = 7

/* Največ srečanj ekipe v kolu (ista meja kot LigaStoritev.NAJVEC_UR_V_KOLU). */
const NAJVEC_UR_V_KOLU = 10

/* Kaj pomeni izbrani večer: ob vsaki uri se odigra en krog krožnega sistema,
   zato vsaka ekipa igra toliko srečanj, kolikor je ur, kol pa je toliko manj. */
function namigUr(ure: string[]): string {
  const n = ure.length
  const vpisane = ure.filter(Boolean).map((u) => u.replace(':', '.'))
  const kdaj = vpisane.length === n
    ? ` — ob ${vpisane.slice(0, -1).join(', ')} in ${vpisane[n - 1]}`
    : ''
  const manj = n === 2 ? 'pol manj' : `${n}-krat manj`
  return `V kolu vsaka ekipa odigra ${n} ${srecanjTekst(n)}${kdaj}. Ob vsaki uri igrajo vse ekipe hkrati, vsaka enkrat, zato je kol ${manj} kot pri eni uri. Pri lihem številu ekip ena ekipa ob vsaki uri počiva. Prazna ura pomeni, da ura ni določena.`
}

function srecanjTekst(n: number): string {
  if (n === 1) return 'srečanje'
  if (n === 2) return 'srečanji'
  if (n === 3 || n === 4) return 'srečanja'
  return 'srečanj'
}

/* Končnica po rednem delu: toliko ekip gre naprej (2 = samo finale). */
const MOZNOSTI_KONCNICE: { ekip: number; oznaka: string }[] = [
  { ekip: 2, oznaka: 'Finale (prva dva)' },
  { ekip: 4, oznaka: 'Polfinale in finale (prve štiri)' },
  { ekip: 8, oznaka: 'Četrtfinale, polfinale in finale (prvih osem)' },
]

export function LigaObrazecOkno({ liga, onZapri, onShranjeno }: Lastnosti) {
  const urejanje = liga !== undefined

  const [ime, nastaviIme] = useState(liga?.ime ?? '')
  const [sezona, nastaviSezono] = useState(liga?.sezona ?? '')
  const [spol, nastaviSpol] = useState<SpolKategorija>(liga?.spolKategorija ?? 'MOSKI')
  const [format, nastaviFormat] = useState<FormatSrecanja>(liga?.formatSrecanja ?? 'SNTL')
  const [steviloNizov, nastaviSteviloNizov] = useState(liga?.steviloNizov ?? 5)
  const [konec, nastaviKonec] = useState<KonecSrecanja>(
    liga?.zmagZaSrecanje != null ? 'PRAG' : 'VSE',
  )
  const [prag, nastaviPrag] = useState(
    liga?.zmagZaSrecanje ?? vecina(liga?.formatSrecanja ?? 'SNTL'),
  )
  const [dvokrozno, nastaviDvokrozno] = useState(liga?.dvokrozno ?? true)
  const [tockeZmaga, nastaviTockeZmaga] = useState(liga?.tockeZmaga ?? 2)
  const [tockeNeodloceno, nastaviTockeNeodloceno] = useState(liga?.tockeNeodloceno ?? 1)
  const [tockePoraz, nastaviTockePoraz] = useState(liga?.tockePoraz ?? 0)
  const [dovoljenoNeodloceno, nastaviDovoljenoNeodloceno] = useState(liga?.dovoljenoNeodloceno ?? true)
  const [prepoved, nastaviPrepoved] = useState(liga?.prepovedDvojneRegistracije ?? false)
  const [raven, nastaviRaven] = useState<RavenTekmovanja>(liga?.raven ?? 'KLUBSKO')
  const [enakomerna, nastaviEnakomerno] = useState(liga?.enakomernaRazvrstitev ?? false)
  const [predloga, nastaviPredlogo] = useState<PredlogaLige>(liga?.predlogaListka ?? 'SNTL_23')
  /* Termini so seme, ne seznam datumov: ekip (in s tem števila kol) ob
     ustvarjanju še ni. Datume vsem kolom izračuna žreb, ročno popravljanje po
     kolih pride kasneje (TerminiOkno na strani lige). */
  const [datumPrvega, nastaviDatumPrvega] = useState(
    liga?.zacetekPrvegaKola?.slice(0, 10) ?? '',
  )
  const [uraPrvega, nastaviUroPrvega] = useState(
    liga?.zacetekPrvegaKola?.slice(11, 16) ?? '',
  )
  const [razmik, nastaviRazmik] = useState(liga?.razmikDni ?? PRIVZET_RAZMIK)
  /* Ure kola: null = vsaka ekipa v kolu igra enkrat (ena ura za vse), sicer
     ura vsakega kroga večera. Prazna ura je »ura ni določena« (00:00) — polje
     se zanjo ne izpolni, da ne kaže polnoči. */
  const [ure, nastaviUre] = useState<string[] | null>(
    liga?.ureSrecanj?.map((u) => (u === '00:00' ? '' : u.slice(0, 5))) ?? null,
  )
  /* 0 = liga končnice nima. Končnica je del pravil tekmovanja, zato je tu
     (in se po žrebu zaklene), njen potek pa vodi stran lige. */
  const [koncnicaEkip, nastaviKoncnicaEkip] = useState(liga?.koncnicaEkip ?? 0)
  const [koncnicaZmag, nastaviKoncnicaZmag] = useState(liga?.koncnicaZmag ?? 2)

  const tekme = RAZPORED_FORMATA[format]

  /* Ob zamenjavi formata se prag prilagodi: mej, večjih od števila tekem,
     strežnik ne sprejme, tiho poslana napaka pa bi bila nerazumljiva. */
  function zamenjajFormat(nov: FormatSrecanja) {
    nastaviFormat(nov)
    nastaviPrag((p) => Math.min(p, RAZPORED_FORMATA[nov].length))
  }

  /* Ura prvega srečanja je ista kot »Ura« kola z enim srečanjem, zato gre ob
     preklopu v obe smeri s sabo; vpisane ure se ob spremembi števila ohranijo.
     Eno srečanje je navadna liga (ure null) — dveh zapisov za isto ni. */
  function zamenjajSrecanjVKolu(stevilo: number) {
    if (stevilo <= 1) {
      if (ure) nastaviUroPrvega(ure[0] ?? '')
      nastaviUre(null)
      return
    }
    const prej = ure ?? [uraPrvega]
    nastaviUre(Array.from({ length: stevilo }, (_, i) => prej[i] ?? ''))
  }

  function urediUro(mesto: number, ura: string) {
    nastaviUre((prej) => prej && prej.map((u, i) => (i === mesto ? ura : u)))
  }

  const shranjevanje = useMutation({
    mutationFn: (vnos: LigaVnos) =>
      urejanje ? ligeApi.uredi(liga.id, vnos) : ligeApi.ustvari(vnos),
    onSuccess: (shranjena) => {
      onShranjeno(shranjena)
      onZapri()
    },
  })

  function obOddaji(dogodek: FormEvent) {
    dogodek.preventDefault()
    shranjevanje.mutate({
      ime: ime.trim(),
      sezona: sezona.trim() || null,
      spolKategorija: spol,
      formatSrecanja: format,
      steviloNizov,
      zmagZaSrecanje: konec === 'PRAG' ? prag : null,
      dvokrozno,
      tockeZmaga,
      tockeNeodloceno,
      tockePoraz,
      dovoljenoNeodloceno,
      prepovedDvojneRegistracije: prepoved,
      raven,
      enakomernaRazvrstitev: enakomerna,
      predlogaListka: predloga,
      /* Brez datuma prvega kola terminov ni; ura je neobvezna (00:00 pomeni
         »ura ni določena« in se v razporedu ne izpiše). */
      zacetekPrvegaKola: datumPrvega
        ? `${datumPrvega}T${(ure ? ure[0] : uraPrvega) || '00:00'}`
        : null,
      razmikDni: datumPrvega ? razmik : null,
      koncnicaEkip: koncnicaEkip > 0 ? koncnicaEkip : null,
      koncnicaZmag: koncnicaEkip > 0 ? koncnicaZmag : null,
      ureSrecanj: ure ? ure.map((u) => u || '00:00') : null,
    })
  }

  return (
    <ModalnoOkno naslov={urejanje ? 'Uredi pravila lige' : 'Nova liga'} onZapri={onZapri}>
      <form className="obrazec" onSubmit={obOddaji}>
        {urejanje && (
          <p className="obvestilo">
            Pravila je mogoče spreminjati samo, dokler je liga v pripravi — po generiranju
            razporeda so zaklenjena.
          </p>
        )}

        <label className="obrazec__polje">
          <span>Ime lige *</span>
          <input value={ime} onChange={(d) => nastaviIme(d.target.value)}
            placeholder="npr. 1. SNTL – moški 2025/26" required />
        </label>

        <div className="obrazec__vrstica">
          <label className="obrazec__polje">
            <span>Sezona</span>
            <input value={sezona} onChange={(d) => nastaviSezono(d.target.value)} placeholder="2025/26" />
          </label>
          <label className="obrazec__polje">
            <span>Kategorija</span>
            <select value={spol} onChange={(d) => nastaviSpol(d.target.value as SpolKategorija)}>
              <option value="MOSKI">Moški</option>
              <option value="ZENSKE">Ženske</option>
              <option value="MESANO">Mešano</option>
            </select>
          </label>
        </div>

        <div className="obrazec__vrstica">
          <label className="obrazec__polje">
            <span>Format srečanja</span>
            <select value={format} onChange={(d) => zamenjajFormat(d.target.value as FormatSrecanja)}>
              {(Object.keys(OZNAKE_FORMAT) as FormatSrecanja[])
                .filter((f) => !FORMATI_SAMO_TURNIR.includes(f) || f === format)
                .map((f) => (
                  <option key={f} value={f}>{OZNAKE_FORMAT[f]}</option>
                ))}
            </select>
          </label>
          <label className="obrazec__polje">
            <span>Nizi (najboljši od)</span>
            <select value={steviloNizov} onChange={(d) => nastaviSteviloNizov(Number(d.target.value))}>
              <option value={3}>3</option>
              <option value={5}>5</option>
              <option value={7}>7</option>
            </select>
          </label>
        </div>

        <p className="namig">
          Vrstni red tekem: {tekme.join(' · ')} ({tekme.length} tekem)
        </p>

        <div className="obrazec__vrstica">
          <label className="obrazec__polje">
            <span>Konec srečanja</span>
            <select value={konec} onChange={(d) => nastaviKonec(d.target.value as KonecSrecanja)}>
              <option value="VSE">Odigrajo se vse tekme</option>
              <option value="PRAG">Konča se pri pragu zmag</option>
            </select>
          </label>
          {konec === 'PRAG' && (
            <label className="obrazec__polje">
              <span>Prag zmag (največ {tekme.length})</span>
              <input type="number" min={1} max={tekme.length} value={prag}
                onChange={(d) => nastaviPrag(Number(d.target.value))} required />
            </label>
          )}
        </div>
        <p className="namig">
          {konec === 'VSE'
            ? 'Vseh ' + tekme.length + ' tekem se odigra do konca, tudi ko je zmagovalec srečanja že znan — rezultat šteje v razliko tekem in v rating.'
            : `Ko ena ekipa doseže ${prag} dobljenih tekem, se srečanje konča; preostale tekme ostanejo neodigrane.`}
        </p>

        <fieldset className="obrazec__skupina">
          <legend>Termini kol</legend>
          <div className="obrazec__vrstica">
            <label className="obrazec__polje">
              <span>Prvo kolo</span>
              <input type="date" value={datumPrvega}
                onChange={(d) => nastaviDatumPrvega(d.target.value)} />
            </label>
            {/* Pri ligi z urami je ura prvega srečanja prva v seznamu spodaj —
                drugo polje za isto uro bi se z njo lahko razšlo. */}
            {!ure && (
              <label className="obrazec__polje">
                <span>Ura</span>
                <input type="time" value={uraPrvega}
                  onChange={(d) => nastaviUroPrvega(d.target.value)} />
              </label>
            )}
            <label className="obrazec__polje">
              <span>Na koliko dni</span>
              <input type="number" min={1} max={365} value={razmik}
                disabled={!datumPrvega}
                onChange={(d) => nastaviRazmik(Number(d.target.value))} />
            </label>
          </div>
          <p className="namig">
            {datumPrvega
              ? `Vsako kolo se odigra ${razmik === 7 ? 'teden' : `${razmik} dni`} za prejšnjim; datumi se zapišejo ob generiranju razporeda, ko je znano, koliko kol liga ima. Posamezno kolo je pozneje mogoče prestaviti — na strani lige pod »Termini«.`
              : 'Brez datuma prvega kola razpored pri neodigranih kolih ne pokaže dneva, ampak samo oznako »razpored«. Datume je mogoče vpisati tudi pozneje — na strani lige pod »Termini«.'}
          </p>

          <label className="obrazec__polje">
            <span>Srečanj vsake ekipe v kolu</span>
            <select value={ure?.length ?? 1}
              onChange={(d) => zamenjajSrecanjVKolu(Number(d.target.value))}>
              <option value={1}>1 srečanje</option>
              {Array.from({ length: NAJVEC_UR_V_KOLU - 1 }, (_, i) => i + 2).map((n) => (
                <option key={n} value={n}>{n} {srecanjTekst(n)} — ob {n} urah</option>
              ))}
            </select>
          </label>
          {ure && (
            <div className="obrazec__vrstica obrazec__vrstica--ure">
              {ure.map((ura, i) => (
                <label key={i} className="obrazec__polje">
                  <span>{i + 1}. srečanje</span>
                  <input type="time" value={ura} onChange={(d) => urediUro(i, d.target.value)} />
                </label>
              ))}
            </div>
          )}
          <p className="namig">
            {ure
              ? namigUr(ure)
              : 'V kolu vsaka ekipa odigra eno srečanje, vsa srečanja kola se začnejo ob isti uri.'}
          </p>
        </fieldset>

        <fieldset className="obrazec__skupina">
          <legend>Končnica</legend>
          <div className="obrazec__vrstica">
            <label className="obrazec__polje">
              <span>Po rednem delu</span>
              <select value={koncnicaEkip} onChange={(d) => nastaviKoncnicaEkip(Number(d.target.value))}>
                <option value={0}>Brez končnice</option>
                {MOZNOSTI_KONCNICE.map((m) => (
                  <option key={m.ekip} value={m.ekip}>{m.oznaka}</option>
                ))}
              </select>
            </label>
            {koncnicaEkip > 0 && (
              <label className="obrazec__polje">
                <span>Serija do zmag</span>
                <select value={koncnicaZmag} onChange={(d) => nastaviKoncnicaZmag(Number(d.target.value))}>
                  {[1, 2, 3, 4].map((z) => (
                    <option key={z} value={z}>
                      {z === 1 ? '1 (ena tekma)' : `${z} (največ ${2 * z - 1} tekem)`}
                    </option>
                  ))}
                </select>
              </label>
            )}
          </div>
          <p className="namig">
            {koncnicaEkip > 0
              ? `Po rednem delu končnico ustvariš na strani lige: pari sledijo končni lestvici (1. proti ${koncnicaEkip}. …), serija traja do ${koncnicaZmag} ${koncnicaZmag === 1 ? 'zmage' : 'zmag'}. Prvo tekmo igra doma slabše uvrščena ekipa, drugo in odločilno bolje uvrščena. Tekme končnice ne štejejo v lestvico rednega dela.`
              : 'Prvak je prvi po rednem delu.'}
            {koncnicaEkip > 0 && konec === 'VSE' && tekme.length % 2 === 0 &&
              ' Srečanje brez praga zmag se lahko konča neodločeno in serije ne odloči — za končnico nastavi prag zmag.'}
          </p>
        </fieldset>

        <fieldset className="obrazec__skupina">
          <legend>Točkovanje</legend>
          <div className="obrazec__vrstica">
            <label className="obrazec__polje">
              <span>Zmaga</span>
              <input type="number" min={0} value={tockeZmaga} onChange={(d) => nastaviTockeZmaga(Number(d.target.value))} />
            </label>
            <label className="obrazec__polje">
              <span>Neodločeno</span>
              <input type="number" min={0} value={tockeNeodloceno} onChange={(d) => nastaviTockeNeodloceno(Number(d.target.value))} />
            </label>
            <label className="obrazec__polje">
              <span>Poraz</span>
              <input type="number" min={0} value={tockePoraz} onChange={(d) => nastaviTockePoraz(Number(d.target.value))} />
            </label>
          </div>
          <label className="obrazec__polje obrazec__polje--stikalo">
            <input type="checkbox" checked={dovoljenoNeodloceno} onChange={(d) => nastaviDovoljenoNeodloceno(d.target.checked)} />
            <span>Neodločen izid srečanja je mogoč</span>
          </label>
        </fieldset>

        <label className="obrazec__polje obrazec__polje--stikalo">
          <input type="checkbox" checked={dvokrozno} onChange={(d) => nastaviDvokrozno(d.target.checked)} />
          <span>Dvokrožno (doma in v gosteh)</span>
        </label>
        <label className="obrazec__polje obrazec__polje--stikalo">
          <input type="checkbox" checked={prepoved} onChange={(d) => nastaviPrepoved(d.target.checked)} />
          <span>Prepovej dvojno registracijo (igralec le v eni ekipi lige)</span>
        </label>
        <label className="obrazec__polje">
          <span>Raven tekmovanja (teža v Turnirko ratingu)</span>
          <select value={raven} onChange={(d) => nastaviRaven(d.target.value as RavenTekmovanja)}>
            {(Object.keys(OZNAKE_RAVEN) as RavenTekmovanja[]).map((r) => (
              <option key={r} value={r}>{OZNAKE_RAVEN[r]} — {TEZA_RAVNI[r]}</option>
            ))}
          </select>
        </label>
        <p className="namig">
          Teža pove, koliko rating premakne ena tekma te lige: uradna tekmovanja
          NTZS štejejo v celoti, klubska tri četrtine, rekreativna polovico.
          Zmaga v rekreativni ligi pač ni enako vredna kot zmaga v SNTL.
          Dvojice ne štejejo nikoli.
        </p>
        <label className="obrazec__polje obrazec__polje--stikalo">
          <input type="checkbox" checked={enakomerna}
            onChange={(d) => nastaviEnakomerno(d.target.checked)} />
          <span>Enakomerna razvrstitev ekip (žreb po parih)</span>
        </label>
        <p className="namig">
          {enakomerna
            ? 'Ekipe pred žrebom razvrstiš po moči (na strani lige, pod »Ekipe«). Žreb jih zveže v pare — prva zgornje polovice s prvo spodnje in tako naprej — vsak par pa v vsakem krogu igra proti istemu nasprotnemu paru: ena ekipa proti močnejši, druga proti šibkejši. Sezona se za vsak par konča z njunim medsebojnim srečanjem.'
            : 'Brez tega se žreb ne ozira na moč ekip: ena lahko v prvih kolih dobi same favorite, druga same tekmece z dna lestvice.'}
        </p>

        <label className="obrazec__polje">
          <span>Predloga zapisnika (za natis listkov)</span>
          <select value={predloga} onChange={(d) => nastaviPredlogo(d.target.value as PredlogaLige)}>
            {(Object.keys(OZNAKE_PREDLOGA_LIGE) as PredlogaLige[]).map((p) => (
              <option key={p} value={p}>{OZNAKE_PREDLOGA_LIGE[p]}</option>
            ))}
          </select>
        </label>

        <p className="namig">
          Mesto lige v piramidi (višja in nižje lige, napredovanje, izpad) se
          ureja posebej — na strani lige, tudi ko so pravila že zaklenjena.
        </p>

        <SporociloNapake napaka={shranjevanje.error} />
        <div className="obrazec__gumbi">
          <button type="button" className="gumb" onClick={onZapri}>Prekliči</button>
          <button type="submit" className="gumb gumb--glavni" disabled={shranjevanje.isPending}>
            {urejanje ? 'Shrani pravila' : 'Ustvari ligo'}
          </button>
        </div>
      </form>
    </ModalnoOkno>
  )
}
