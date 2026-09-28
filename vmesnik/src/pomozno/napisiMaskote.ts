/* Napisi na tabli maskote (komponente/Maskota.tsx).

   Ob vsakem nastopu se izbere naključen prizor IN naključen napis, zato je
   kombinacij toliko, kolikor je prizorov krat napisov. Vsak napis omenja
   Premium; ton je šaljiv (odločitev lastnika, sep 2026 — izjema od
   DESIGN.md 5b, ki velja samo za maskoto). */
import { CENA_PREMIUM_MESECNO } from '../api/tipi'
import type { UporabnikDto } from '../api/tipi'

/* `{cena}` nadomesti mesečna cena, ki jo gledalec res plača. */
export const NAPISI: readonly string[] = [
  'Igralec Premium · {cena}',
  'Premium, ceneje kot bencin',
  'Premium? Zakaj pa ne!',
  'Veš, da hočeš - Premium ;)',
  'Lahko je tudi tvoj - Premium',
]

/* Cena po istem pravilu kot na strani naročnine (NarocninaStran): do 21 let
   nižja. Gost svoje starosti ne pove, zato vidi višjo — nižja cena bi mlajšemu
   od 21 let obljubila manj, kot bo plačal, višja pa mlajšemu ne škodi. */
export function cenaPremium(uporabnik: UporabnikDto | null): number {
  if (uporabnik === null) return CENA_PREMIUM_MESECNO.starejsi
  return uporabnik.starejsiOd21 ? CENA_PREMIUM_MESECNO.starejsi : CENA_PREMIUM_MESECNO.mlajsi
}

/* Zapis cene kot v napisu (»4,99 €«), enak kot drugod v vmesniku. */
function zapisCene(cena: number): string {
  return cena.toLocaleString('sl-SI', { minimumFractionDigits: 2, maximumFractionDigits: 2 }) + ' €'
}

export function besediloNapisa(predloga: string, cena: number): string {
  return predloga.replace('{cena}', zapisCene(cena))
}

/* Velikost pisave napisa v enotah lika na NAMIZJU (izriše se z merilom lika;
   glej MERA_LIKA v umestitevMaskote.ts). Skupna s CSS `.maskota__napis`.
   Širine so izmerjene pri tej velikosti; na telefonu je pisava večja, ker je
   lik manjši, in se širina preračuna sorazmerno. */
export const PISAVA_NAPISA = 13

/* Širina besedila v enotah lika. Tabla se prilagodi napisu, zato jo je treba
   izmeriti PREDEN se izriše; SVG-ja pa se pred izrisom ne da vprašati.

   Pisavo za ta napis izrecno naložimo: fontsource jo deli po podmnožicah in
   šumniki (š, č, ž) so v `latin-ext`, ki se naloži šele ob prvi rabi. Brez tega
   bi merili z rezervno pisavo, tabla pa bi bila za pravi napis napačno široka.

   Izmera je zapomnjena po besedilu: urnik meri vseh pet napisov ob vsakem
   poskusu, pisave pa se ne spreminjajo. */
const izmerjeno = new Map<string, Promise<number>>()

export function izmeriNapis(besedilo: string): Promise<number> {
  let izmera = izmerjeno.get(besedilo)
  if (!izmera) {
    izmera = izmeriNovo(besedilo)
    izmerjeno.set(besedilo, izmera)
  }
  return izmera
}

async function izmeriNovo(besedilo: string): Promise<number> {
  const pisava = getComputedStyle(document.documentElement).getPropertyValue('--pisava-display')
  const oznaka = `800 ${PISAVA_NAPISA}px ${pisava}`
  try {
    await document.fonts.load(oznaka, besedilo)
  } catch {
    /* Pisava se ni naložila: izmerimo z rezervno; tabla bo le malo netočna. */
  }
  const platno = document.createElement('canvas').getContext('2d')
  if (!platno) return besedilo.length * PISAVA_NAPISA * 0.55
  platno.font = oznaka
  return platno.measureText(besedilo).width
}
