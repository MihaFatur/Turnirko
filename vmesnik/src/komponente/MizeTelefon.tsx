/* Telefonski pogled strani Mize (≤ 640 px; prenova »tloris dvorane«, 24. 11. 2026).

   Isti tloris, pomanjšan na ploščice: najprej tapneš mizo, nato tekmo (vlečenja na telefonu
   ni). Izbrana miza pokaže svoje podrobnosti (prosta: predlog in »Pokliči«; zasedena:
   »Vnesi izid«), spodaj je vrsta z gumbom »Na N«, ki tekmo takoj pokliče na izbrano prosto
   mizo. Stran je za prenosnik na pultu; telefon je zasilna pot v dvorani. */
import type { KandidatMizeDto, MizaDto, MizeTurnirjaDto, TekmaMizeDto } from '../api/tipi'
import {
  imenaTekme,
  jePredolgo,
  minutOd,
  opisTekme,
  oznakaMize,
  priimkiTekme,
  razlogiZasedenosti,
  sirinaTraka,
  kratkoImenaTekme,
} from '../pomozno/mize'

export function NaslovTelefon({
  kazalniki,
}: {
  kazalniki?: { proste: number; vIgri: number; naVrsti: number }
}) {
  return (
    <div className="mize-tel__naslov">
      <h1 className="mize-tel__h1">Mize</h1>
      {kazalniki && (
        <span className="mize-tel__vrstica">
          <span className="mize-tel__vrstica-proste">{kazalniki.proste} proste</span> · {kazalniki.vIgri} v igri ·{' '}
          {kazalniki.naVrsti} čaka
        </span>
      )}
    </div>
  )
}

/* ---------- mali tloris ---------- */

export function MaliTloris({
  podatki,
  izbrana,
  poudarjene,
  naIzbiro,
}: {
  podatki: MizeTurnirjaDto
  izbrana: number | null
  poudarjene: Set<number>
  naIzbiro: (miza: number) => void
}) {
  return (
    <div className="mize-tel__tloris">
      <div className="mize-tel__mreza">
        {podatki.mize.map((m) => {
          if (m.izklopljena) {
            return (
              <span key={m.stevilka} className="mize-tel__ploscica mize-tel__ploscica--izklopljena">
                {m.stevilka}
              </span>
            )
          }
          const t = m.tekma
          const minute = t ? (minutOd(t.poklicanaOb, podatki.casStreznika) ?? 0) : 0
          const dolgo = !!t && jePredolgo(t, minute)
          const razred = t
            ? dolgo
              ? 'dolgo'
              : 'igra'
            : poudarjene.has(m.stevilka)
              ? 'sproscena'
              : 'prosta'
          return (
            <button
              key={m.stevilka}
              type="button"
              className={
                'mize-tel__ploscica mize-tel__ploscica--' + razred + (izbrana === m.stevilka ? ' mize-tel__ploscica--izbrana' : '')
              }
              aria-pressed={izbrana === m.stevilka}
              aria-label={`Miza ${m.stevilka}${t ? ', v igri' : ', prosta'}`}
              onClick={() => naIzbiro(m.stevilka)}
            >
              {m.stevilka}
              {t && !dolgo && <span className="mize-tel__trak" style={{ width: `${sirinaTraka(minute)}%` }} />}
            </button>
          )
        })}
      </div>
    </div>
  )
}

/* ---------- podrobnosti izbrane mize ---------- */

export function PodrobnostiMize({
  miza,
  podatki,
  predlog,
  najdlje,
  onPoklici,
  onIzid,
}: {
  miza: MizaDto | undefined
  podatki: MizeTurnirjaDto
  predlog: KandidatMizeDto | undefined
  najdlje: string
  onPoklici: (k: KandidatMizeDto, miza: number) => void
  onIzid: (t: TekmaMizeDto) => void
}) {
  if (!miza) {
    return (
      <div className="mize-tel__podrobnosti">
        <p className="mize-tel__vse-zasedeno">Vse mize so zasedene. {najdlje}.</p>
      </div>
    )
  }
  const t = miza.tekma
  if (t) {
    const minute = minutOd(t.poklicanaOb, podatki.casStreznika) ?? 0
    return (
      <div className="mize-tel__podrobnosti">
        <div className="mize-tel__kartica mize-tel__kartica--igra">
          <span className="mize-tel__nad">
            Miza {oznakaMize(t, ' + ')} · {opisTekme(t)} · {minute} min
          </span>
          <p className="mize-tel__imena">{imenaTekme(t)}</p>
          <button type="button" className="mize-tel__gumb mize-tel__gumb--izid" onClick={() => onIzid(t)}>
            Vnesi izid
          </button>
        </div>
      </div>
    )
  }
  const prostaMinut = minutOd(miza.prostaOd, podatki.casStreznika)
  const [pa, pb] = predlog ? priimkiTekme(predlog.tekma) : ['', '']
  return (
    <div className="mize-tel__podrobnosti">
      <div className="mize-tel__kartica mize-tel__kartica--prosta">
        <span className="mize-tel__nad mize-tel__nad--prosta">
          Miza {miza.stevilka} · prosta{prostaMinut ? ` ${prostaMinut} min` : ''}
        </span>
        {predlog && (
          <>
            <div className="mize-tel__miza">
              <span className="mize-tel__mreza-sredina" />
              <span className="mize-tel__igralec">{pa}</span>
              <span className="mize-tel__igralec">{pb}</span>
            </div>
            <span className="mize-tel__opis">{opisTekme(predlog.tekma)} · listek se natisne</span>
            <button
              type="button"
              className="mize-tel__gumb mize-tel__gumb--glavni"
              onClick={() => onPoklici(predlog, miza.stevilka)}
            >
              Pokliči na mizo {miza.stevilka}
            </button>
          </>
        )}
        <span className="mize-tel__namig">Ali tapni drugo tekmo spodaj – gre na to mizo.</span>
      </div>
    </div>
  )
}

/* ---------- vrsta »Na vrsti« ---------- */

const NAJVEC_NA_TELEFONU = 8

export function VrstaTelefon({
  kandidati,
  predlagane,
  cilj,
  prva,
  vse,
  onNaMizo,
  naPreklopVseh,
}: {
  kandidati: KandidatMizeDto[]
  /* Id tekme → miza, za katero je tekma prvi predlog. */
  predlagane: Map<number, number>
  /* Izbrana prosta miza; kadar ni, gre tekma na predlagano ali prvo prosto. */
  cilj: number | null
  prva: number | null
  vse: boolean
  onNaMizo: (k: KandidatMizeDto, miza: number) => void
  naPreklopVseh: () => void
}) {
  const vidni = vse ? kandidati : kandidati.slice(0, NAJVEC_NA_TELEFONU)
  return (
    <div className="mize-tel__vrsta">
      <div className="mize-tel__vrsta-glava">
        <h2 className="mize-tel__vrsta-naslov">Na vrsti</h2>
        <span className="mize-tel__vrsta-stevec">{kandidati.length}</span>
      </div>
      <ol className="mize-tel__vrsta-seznam">
        {vidni.map((k) => {
          const miza = cilj ?? predlagane.get(k.tekma.tekma.id) ?? prva
          return (
            <li
              key={k.tekma.tekma.id}
              className={'mize-tel__vrstica-tekme' + (k.zaseden ? ' mize-tel__vrstica-tekme--zaseden' : '')}
            >
              <div className="mize-tel__vrstica-besedilo">
                <p className="mize-tel__vrstica-imena">{kratkoImenaTekme(k.tekma)}</p>
                <p className={'mize-tel__vrstica-opis' + (k.zaseden ? ' mize-tel__vrstica-opis--razlog' : '')}>
                  {k.zaseden ? razlogiZasedenosti(k).join(' · ') : opisTekme(k.tekma)}
                </p>
              </div>
              {!k.zaseden && miza !== null && (
                <button type="button" className="mize-tel__na-mizo" onClick={() => onNaMizo(k, miza)}>
                  Na {miza}
                </button>
              )}
            </li>
          )
        })}
      </ol>
      {kandidati.length > NAJVEC_NA_TELEFONU && (
        <button type="button" className="mize-tel__vec" onClick={naPreklopVseh}>
          {vse ? 'Pokaži manj' : `Pokaži vseh ${kandidati.length}`}
        </button>
      )}
    </div>
  )
}
