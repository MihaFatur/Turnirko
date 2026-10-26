/* Uraden ekipni zapisnik (NTZS "ZAPISNIK EKIPNEGA DVOBOJA") - en natisljivi
   list na srecanje. Posnema uraden obrazec: glava s podatki o srecanju, obe
   ekipi s postavo (A/B/C : X/Y/Z), mreza posamicnih tekem z nizi ter podnozje
   s sodniki, vodji in podpisi. Nasi podatki napolnijo imena in mrezo; polja,
   ki jih ne hranimo (kraj, ura, st. gledalcev ...), ostanejo prazne crte za
   rocni vpis - enako kot na papirnatem obrazcu.

   Obrazec se natisne tudi PRED postavo: ekipi na list sami vpisete postavo,
   sodnik pa mrezo tekem. Tekem srecanja takrat se ni (nastanejo s postavo),
   zato se prazne vrstice sestavijo iz formata - v vrstnem redu, kot ga bo
   igralo srecanje - z mesti (A-X ... dvojice) in praznimi polji za imena.

   Varianta (1. SNTL / 2./3. SNTL) doloca oznako lige v glavi in prisotnost
   vrstice za delegata NTZS (imajo jo tekme 1. SNTL). Ekipna tekma turnirja
   variante nima (null): v glavi stoji ime dogodka, delegata ni. Zapisnik je
   crno-bel ne glede na temo (natis na papir). */
import type { LigaDto, PredlogaLige, SrecanjePodrobnoDto } from '../api/tipi'
import { razbijCas, sestaviPodatkeZapisnika } from '../pomozno/zapisnikPodatki'

const OZNAKA_VARIANTE: Record<PredlogaLige, string> = {
  SNTL_1: '1. SNTL',
  SNTL_23: '2. in 3. SNTL',
}

export function ZapisnikEkipnegaDvoboja({
  podrobno,
  liga,
  varianta,
  gosto = false,
}: {
  podrobno: SrecanjePodrobnoDto
  liga: LigaDto | undefined
  /* null = ekipna tekma turnirja (brez liginih predlog). */
  varianta: PredlogaLige | null
  /* Zgoščen obrazec za 2 ali 3 zapisnike na list: manjši razmiki in nižje
     vrstice; pomanjšanje do reze opravi, kdor ga postavi na list. */
  gosto?: boolean
}) {
  const s = podrobno.srecanje
  const { prazen, nizi: NIZI, vrstice, imeNa } = sestaviPodatkeZapisnika(podrobno)

  const { datum, ura } = razbijCas(s.predvidenZacetek)
  const jeLiga = s.idLiga != null

  return (
    <article
      className={['zapisnik', prazen && 'zapisnik--prazen', gosto && 'zapisnik--gosto']
        .filter(Boolean)
        .join(' ')}
    >
      <header className="zapisnik__vrh">
        <div className="zapisnik__naziv">
          <div className="zapisnik__zveza">NAMIZNOTENIŠKA ZVEZA SLOVENIJE</div>
          <div className="zapisnik__naslov">ZAPISNIK EKIPNEGA DVOBOJA</div>
          <div className="zapisnik__podnaslov">
            {varianta
              ? `ZA MOŠKE IN ŽENSKE · ${OZNAKA_VARIANTE[varianta]}`
              : (podrobno.kontekst.dogodek ?? podrobno.kontekst.tekmovanje)}
          </div>
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
              <th>{jeLiga ? 'Liga' : 'Turnir'}</th>
              <td className="zapisnik__vpis" colSpan={3}>{liga?.ime ?? podrobno.kontekst.tekmovanje}</td>
            </tr>
            <tr>
              <th>Sezona</th><td className="zapisnik__vpis">{liga?.sezona ?? podrobno.kontekst.sezona ?? ''}</td>
              <th>Krog</th><td className="zapisnik__vpis">{jeLiga && s.idSerija == null ? `${s.kolo}.` : podrobno.kontekst.opis ?? `${s.kolo}.`}</td>
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
            <th className="zapisnik__par">Par</th>
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
          {vrstice.map((v) => (
            <tr key={v.kljuc}>
              <td className="zapisnik__st">{v.zaporedje}</td>
              <td className="zapisnik__par">{v.oznaka}</td>
              <td className="zapisnik__igralec">{v.domaci}</td>
              <td className="zapisnik__igralec">{v.gost}</td>
              {/* Ze vneseno se izpise, ostalo ostane prazna celica za rocni
                  vpis - obrazec se natisne tudi pred srecanjem. */}
              {NIZI.map((n) => {
                const niz = v.nizi[n - 1]
                return (
                  <td key={n} className="zapisnik__niz">
                    {niz ? `${niz.tocke1}:${niz.tocke2}` : ''}
                  </td>
                )
              })}
              <td className="zapisnik__igre">{v.igre}</td>
              <td className="zapisnik__stanje" />
            </tr>
          ))}
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
