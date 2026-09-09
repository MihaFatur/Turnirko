/* Uraden ekipni zapisnik (NTZS "ZAPISNIK EKIPNEGA DVOBOJA") - en natisljivi
   list na srecanje. Posnema uraden obrazec: glava s podatki o srecanju, obe
   ekipi s postavo (A/B/C : X/Y/Z), mreza posamicnih tekem z nizi ter podnozje
   s sodniki, vodji in podpisi. Nasi podatki napolnijo imena in mrezo; polja,
   ki jih ne hranimo (kraj, ura, st. gledalcev ...), ostanejo prazne crte za
   rocni vpis - enako kot na papirnatem obrazcu.

   Varianta (1. SNTL / 2./3. SNTL) doloca oznako lige v glavi in prisotnost
   vrstice za delegata NTZS (imajo jo tekme 1. SNTL). Zapisnik je crno-bel ne
   glede na temo (natis na papir). */
import type { LigaDto, PredlogaLige, SrecanjePodrobnoDto, StranEkipe } from '../api/tipi'

/* Papirnati obrazec ima pet stolpcev za nize (SNTL igra na 5), zato je to
   privzetek; liga na 7 nizov jih dobi sedem, da natis ne odreze vpisanih
   tock. */
const PRIVZETO_NIZOV = 5

const OZNAKA_VARIANTE: Record<PredlogaLige, string> = {
  SNTL_1: '1. SNTL',
  SNTL_23: '2. in 3. SNTL',
}

export function ZapisnikEkipnegaDvoboja({
  podrobno,
  liga,
  varianta,
}: {
  podrobno: SrecanjePodrobnoDto
  liga: LigaDto | undefined
  varianta: PredlogaLige
}) {
  const s = podrobno.srecanje
  const tekme = [...podrobno.tekme].sort((a, b) => a.zaporedje - b.zaporedje)
  const NIZI = Array.from(
    { length: Math.max(PRIVZETO_NIZOV, tekme[0]?.steviloNizov ?? PRIVZETO_NIZOV) },
    (_, i) => i + 1,
  )

  const imeNa = (stran: StranEkipe, poz: string) =>
    podrobno.postave.find((p) => p.stran === stran && p.pozicija === poz)?.polnoIme ?? ''

  const { datum, ura } = razbijCas(s.predvidenZacetek)

  return (
    <article className="zapisnik">
      <header className="zapisnik__glava">
        <div className="zapisnik__naziv">
          <div className="zapisnik__zveza">NAMIZNOTENIŠKA ZVEZA SLOVENIJE</div>
          <div className="zapisnik__naslov">ZAPISNIK EKIPNEGA DVOBOJA</div>
          <div className="zapisnik__podnaslov">ZA MOŠKE IN ŽENSKE · {OZNAKA_VARIANTE[varianta]}</div>
        </div>
        <table className="zapisnik__meta">
          <tbody>
            <tr>
              <th>Kraj</th><td className="zapisnik__vpis" />
              <th>ura</th><td className="zapisnik__vpis">{ura}</td>
            </tr>
            <tr>
              <th>Datum</th><td className="zapisnik__vpis">{datum}</td>
              <th>do</th><td className="zapisnik__vpis" />
            </tr>
            <tr>
              <th>Dvorana</th><td className="zapisnik__vpis" colSpan={3} />
            </tr>
            <tr>
              <th>Liga</th><td className="zapisnik__vpis" colSpan={3}>{liga?.ime ?? ''}</td>
            </tr>
            <tr>
              <th>Sezona</th><td className="zapisnik__vpis">{liga?.sezona ?? ''}</td>
              <th>Krog</th><td className="zapisnik__vpis">{s.kolo}.</td>
            </tr>
          </tbody>
        </table>
      </header>

      <div className="zapisnik__ekipi">
        <span className="zapisnik__ekipa">{s.domaci}</span>
        <span className="zapisnik__proti">:</span>
        <span className="zapisnik__ekipa zapisnik__ekipa--desno">{s.gost}</span>
      </div>

      {/* Postava obeh ekip - mesta z imeni (prazna, kjer postava ni dolocena). */}
      <div className="zapisnik__postave">
        <PostavaEkipe naslov="Ekipa A (domači)" pozicije={podrobno.pozicijeDomaci}
          ime={(p) => imeNa('DOMACI', p)} />
        <PostavaEkipe naslov="Ekipa B (gostje)" pozicije={podrobno.pozicijeGost}
          ime={(p) => imeNa('GOST', p)} />
      </div>

      <table className="zapisnik__tekme">
        <thead>
          <tr>
            <th className="zapisnik__st">#</th>
            <th className="zapisnik__oznaka">Par</th>
            <th>Domači</th>
            <th>Gost</th>
            {NIZI.map((n) => (
              <th key={n} className="zapisnik__niz">{n}</th>
            ))}
            <th className="zapisnik__igre">Igre</th>
            <th className="zapisnik__stanje">Stanje</th>
          </tr>
        </thead>
        <tbody>
          {tekme.map((t) => {
            const konec = t.status === 'KONCANA'
            return (
              <tr key={t.id}>
                <td className="zapisnik__st">{t.zaporedje}</td>
                <td className="zapisnik__oznaka">{t.oznaka}</td>
                <td className="zapisnik__igralec">{[t.domaci, t.domaci2].filter(Boolean).join(' / ') || '—'}</td>
                <td className="zapisnik__igralec">{[t.gost, t.gost2].filter(Boolean).join(' / ') || '—'}</td>
                {/* Ze vneseno se izpise, ostalo ostane prazna celica za rocni
                    vpis - obrazec se natisne tudi pred srecanjem. */}
                {NIZI.map((n) => {
                  const niz = t.nizi[n - 1]
                  return (
                    <td key={n} className="zapisnik__niz">
                      {niz ? `${niz.tocke1}:${niz.tocke2}` : ''}
                    </td>
                  )
                })}
                <td className="zapisnik__igre">
                  {konec ? `${t.dobljeniNiziDomaci}:${t.dobljeniNiziGost}` : ''}
                </td>
                <td className="zapisnik__stanje" />
              </tr>
            )
          })}
        </tbody>
      </table>

      <div className="zapisnik__izid">
        Rezultat dvoboja je
        <span className="zapisnik__crta zapisnik__crta--ozka" /> :
        <span className="zapisnik__crta zapisnik__crta--ozka" /> za ekipo NTK
        <span className="zapisnik__crta" />
      </div>

      <div className="zapisnik__sodniki">
        <span>Gl. sodnik: <span className="zapisnik__crta" /></span>
        <span>Sodnik: <span className="zapisnik__crta" /></span>
      </div>

      <div className="zapisnik__podpisi">
        <PodpisnoPolje naslov="Vodja domače ekipe (ime, podpis)" />
        <PodpisnoPolje naslov="Vodja gostujoče ekipe (ime, podpis)" />
        {varianta === 'SNTL_1' && (
          <PodpisnoPolje naslov="Delegat NTZS (ime, podpis)" />
        )}
        <div className="zapisnik__gledalci">
          Št. gledalcev: <span className="zapisnik__crta zapisnik__crta--ozka" />
        </div>
      </div>

      <div className="zapisnik__pripombe">
        <span>Pripombe sodnika:</span>
        <span className="zapisnik__crta zapisnik__crta--polna" />
      </div>
    </article>
  )
}

function PostavaEkipe({
  naslov,
  pozicije,
  ime,
}: {
  naslov: string
  pozicije: string[]
  ime: (pozicija: string) => string
}) {
  return (
    <table className="zapisnik__postava">
      <caption>{naslov}</caption>
      <tbody>
        {pozicije.map((p) => (
          <tr key={p}>
            <th>{p}</th>
            <td className="zapisnik__vpis">{ime(p)}</td>
          </tr>
        ))}
      </tbody>
    </table>
  )
}

function PodpisnoPolje({ naslov }: { naslov: string }) {
  return (
    <div className="zapisnik__podpis">
      <span className="zapisnik__crta zapisnik__crta--polna" />
      <span className="zapisnik__podpis-naslov">{naslov}</span>
    </div>
  )
}

/* Iz ISO datuma-casa (npr. "2026-07-28T18:00") loci datum (dd.mm.llll) in uro
   (hh:mm). Kadar casa ni, vrne prazna niza (obrazec izpise prazno crto). */
function razbijCas(iso: string | null): { datum: string; ura: string } {
  if (!iso) return { datum: '', ura: '' }
  const [d, t] = iso.split('T')
  const deli = d.split('-')
  const datum = deli.length === 3 ? `${deli[2]}.${deli[1]}.${deli[0]}` : ''
  const ura = t ? t.slice(0, 5) : ''
  return { datum, ura }
}
