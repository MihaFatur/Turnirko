/* Podatki natisljivega ekipnega zapisnika: obe obliki (uradni obrazec NTZS in
   Turnirkov ležeči zapisnik) berejo srečanje na isti način - kar je že vpisano,
   se izpiše, ostalo ostane prazno za ročni vpis -, zato je sestava vrstic na
   enem mestu in se obliki ne moreta razliti.

   Obrazec se natisne tudi PRED postavo. Tekem srečanja takrat še ni (nastanejo
   s postavo), zato se prazne vrstice sestavijo iz formata - v vrstnem redu, kot
   ga bo igralo srečanje - z mesti (A-X ... dvojice) in praznimi polji. */
import type { NizVnos, SrecanjePodrobnoDto, StranEkipe } from '../api/tipi'
import { RAZPORED_FORMATA, izidNizov } from '../api/tipi'

/* Papirnati obrazec ima pet stolpcev za nize (SNTL igra na 5), zato je to
   privzetek; liga na 7 nizov jih dobi sedem, da natis ne odreže vpisanih
   točk. */
const PRIVZETO_NIZOV = 5

/* Ena vrstica mreže: bodisi odigrana/napovedana tekma srečanja bodisi prazno
   mesto iz formata (pred postavo). */
export interface VrsticaTekme {
  kljuc: number
  zaporedje: number
  oznaka: string
  dvojice: boolean
  /* Igralci vsake strani po vrsti (dva pri dvojicah); prazen seznam, kadar še
     ni znano, kdo igra. */
  domaciIgralci: string[]
  gostIgralci: string[]
  /* Isto v eni vrstici (»A / B«), »—«, kadar igralcev ni. */
  domaci: string
  gost: string
  nizi: NizVnos[]
  igre: string
  /* Stran, ki je tekmo dobila; null, dokler tekma ni končana. */
  zmagovalec: StranEkipe | null
}

export function sestaviPodatkeZapisnika(podrobno: SrecanjePodrobnoDto) {
  const tekme = [...podrobno.tekme].sort((a, b) => a.zaporedje - b.zaporedje)
  /* Brez tekem postave še ni: obrazec ostane prazen in ga izpolni roka. */
  const prazen = tekme.length === 0
  const nizi = Array.from(
    { length: Math.max(PRIVZETO_NIZOV, podrobno.steviloNizov, ...tekme.map((t) => t.steviloNizov)) },
    (_, i) => i + 1,
  )

  const vrstice: VrsticaTekme[] = prazen
    ? RAZPORED_FORMATA[podrobno.format].map((oznaka, i) => ({
        kljuc: i,
        zaporedje: i + 1,
        oznaka,
        dvojice: oznaka === 'dvojice',
        domaciIgralci: [],
        gostIgralci: [],
        domaci: '',
        gost: '',
        nizi: [],
        igre: '',
        zmagovalec: null,
      }))
    : tekme.map((t) => {
        const domaciIgralci = [t.domaci, t.domaci2].filter((i): i is string => !!i)
        const gostIgralci = [t.gost, t.gost2].filter((i): i is string => !!i)
        return {
          kljuc: t.id,
          zaporedje: t.zaporedje,
          oznaka: t.oznaka,
          dvojice: t.tip === 'DVOJICE',
          domaciIgralci,
          gostIgralci,
          domaci: domaciIgralci.join(' / ') || '—',
          gost: gostIgralci.join(' / ') || '—',
          nizi: t.nizi,
          igre: t.status === 'KONCANA' ? izidNizov(t.dobljeniNiziDomaci, t.dobljeniNiziGost, t.izidTip) : '',
          zmagovalec: t.status === 'KONCANA' ? t.zmagovalecStran : null,
        }
      })

  const imeNa = (stran: StranEkipe, poz: string) =>
    podrobno.postave.find((p) => p.stran === stran && p.pozicija === poz)?.polnoIme ?? ''

  return { prazen, nizi, vrstice, imeNa }
}

/* Stanje dvoboja po vsaki končani tekmi (»2:1«), v vrstnem redu obrazca;
   neodigrana tekma ostane prazna. */
export function stanjaDvoboja(vrstice: VrsticaTekme[]): string[] {
  let domaci = 0
  let gost = 0
  return vrstice.map((v) => {
    if (!v.zmagovalec) return ''
    if (v.zmagovalec === 'DOMACI') domaci += 1
    else gost += 1
    return `${domaci}:${gost}`
  })
}

/* Iz ISO datuma-casa (npr. "2026-07-28T18:00") loci datum (dd.mm.llll) in uro
   (hh:mm). Kadar casa ni, vrne prazna niza (obrazec izpise prazno crto). Ura
   00:00 pomeni »ura ni dolocena« (turnirsko srecanje pozna samo dan) - tam
   mora ostati polje prazno za rocni vpis, ne pa lagati o polnoci. */
export function razbijCas(iso: string | null): { datum: string; ura: string } {
  if (!iso) return { datum: '', ura: '' }
  const [d, t] = iso.split('T')
  const deli = d.split('-')
  const datum = deli.length === 3 ? `${deli[2]}.${deli[1]}.${deli[0]}` : ''
  const ura = t && t.slice(0, 5) !== '00:00' ? t.slice(0, 5) : ''
  return { datum, ura }
}
