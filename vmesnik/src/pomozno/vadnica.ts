/* Vadnica števk (načrt F3): list A4, na katerem ena oseba v okenca prepiše
   števke 0-9 in števila 10-13, kot jih piše na sodniški listek. Iz fotografije
   lista nastanejo označene števke slovenske pisave za učenje branja listkov.

   Postavitev lista (mm) je ista kot v zaledju (si.turnirko.branje.GeometrijaVadnice):
   ob spremembi spremeni oboje in povečaj RAZLICICA, sicer se že natisnjeni listi
   preberejo narobe. Koda QR je »TKV:<različica>:<id lista>« (KodaListka.java). */
import { encode } from 'uqr'

/* Okenca ene različice lista (GeometrijaVadnice.Okenca): vrstice (kaj se piše v
   okenca; vzorec stoji levo od vrstice), okenc v vrstici in mere v mm. */
export interface OkencaVadnice {
  vrednosti: readonly number[]
  stolpcev: number
  okenceX: number
  okenceY: number
  okenceSirina: number
  okenceVisina: number
  korakX: number
  korakY: number
}

/* Različica 1 (20. 11. 2026): 14 vrstic po 10 okenc 14 × 10 mm. */
const V1: OkencaVadnice = {
  vrednosti: [0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13],
  stolpcev: 10,
  okenceX: 38,
  okenceY: 46,
  okenceSirina: 14,
  okenceVisina: 10,
  korakX: 16,
  korakY: 16,
}

/* Različica 2 (24. 11. 2026): okenca kot celica za točke na sodniškem listku
   (17 × 9 mm), da se mreža uči na pisavi v istih pogojih; 16 vrstic po 8, zadnji
   dve še enkrat 11 in 10 (na listkih najpogostejši dvomestni vrednosti). */
const V2: OkencaVadnice = {
  vrednosti: [0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 11, 10],
  stolpcev: 8,
  okenceX: 38,
  okenceY: 46,
  okenceSirina: 17,
  okenceVisina: 9,
  korakX: 19,
  korakY: 13,
}

const POSTAVITVE: Record<number, OkencaVadnice> = { 1: V1, 2: V2 }

/* Različica, ki se natisne. */
export const RAZLICICA = 2

/* Okenca različice lista (pregled že natisnjenih listov); neznana → trenutna. */
export function okencaVadnice(razlicica: number): OkencaVadnice {
  return POSTAVITVE[razlicica] ?? POSTAVITVE[RAZLICICA]
}

/* Vrstice in okenca lista, ki se natisne. */
export const VREDNOSTI = POSTAVITVE[RAZLICICA].vrednosti
export const STOLPCEV = POSTAVITVE[RAZLICICA].stolpcev

/* Mere v mm od zgornjega levega roba lista (GeometrijaVadnice): oznake in koda so
   pri vseh različicah na istem mestu, okenca so iz trenutne različice. */
export const GEOMETRIJA = {
  stranicaOznake: 6,
  /* Središča oznak. */
  zgorajLevo: [15, 15],
  spodajLevo: [15, 282],
  spodajDesno: [195, 282],
  qrX: 173,
  qrY: 9,
  qrStranica: 22,
  ...POSTAVITVE[RAZLICICA],
} as const

/* Največ listov v enem tisku (en list je ena stran A4). */
export const NAJVEC_LISTOV = 50

/* Fotografija lista A4 gre na strežnik do 3200 px (VadnicaStoritev.NAJVEC_STRANICA):
   števka v okencu 9-10 mm ima tako ~65-70 px, kot na listku pri 2000 px. */
export const NAJVEC_STRANICA_VADNICE = 3200

/* Naložena slika, ki jo je stisnila aplikacija za sporočila: preizkus na
   simuliranih fotografijah lista (VadnicaTest) bere pri 1600 in 1280 px daljše
   stranice enako kot izvirnik, pri 1000 px še bere, a slabše, pri 800 px kode QR
   ne prebere več. Pod to mejo je ob neuspelem branju vzrok verjetno velikost. */
export const MAJHNA_SLIKA = 1200

/* Dodatek k sporočilu o neuspelem branju; prazen, če slika ni majhna. */
export function namigMajhneSlike(sirina: number, visina: number): string {
  if (Math.max(sirina, visina) >= MAJHNA_SLIKA) return ''
  return (
    ` Slika ima samo ${sirina} × ${visina} px, verjetno jo je stisnila aplikacija za sporočila.` +
    ' Prosi za izvirno fotografijo (po e-pošti ali kot datoteko).'
  )
}

export function vsebinaQrVadnice(idLista: number): string {
  return `TKV:${RAZLICICA}:${idLista}`
}

/* Oznaka lista pod kodo QR (za človeka; VadnicaStoritev.oznaka). */
export function oznakaLista(idLista: number): string {
  return `V-${idLista}`
}

/* Naključni id-ji listov (deset mest): id izbere tisk, ne strežnik, zato list,
   natisnjen na testnem okolju, velja tudi v produkciji, in tisk ne potrebuje
   povezave. Deset mest ohrani kodo QR v različici 1 (21 modulov), na katero
   računa postavitev. Trk dveh od nekaj sto listov je praktično nemogoč. */
export function noviIdjiListov(n: number, nakljucno: (meja: number) => number = nakljucnoStevilo): number[] {
  const idji = new Set<number>()
  while (idji.size < n) idji.add(1_000_000_000 + nakljucno(9_000_000_000))
  return [...idji]
}

function nakljucnoStevilo(meja: number): number {
  const besedi = new Uint32Array(2)
  crypto.getRandomValues(besedi)
  // 53 bitov naključnosti, nato v razpon [0, meja)
  const x = besedi[0] * 2 ** 21 + (besedi[1] >>> 11)
  return Math.floor((x / 2 ** 53) * meja)
}

/* Koda QR lista: pot SVG v enotah modulov in število modulov (vedno 21). */
export function qrVadnice(idLista: number): { velikost: number; pot: string } {
  const { data, size } = encode(vsebinaQrVadnice(idLista), { ecc: 'M', border: 0, minVersion: 1, maxVersion: 1 })
  let pot = ''
  for (let y = 0; y < size; y++) {
    let x = 0
    while (x < size) {
      if (!data[y][x]) {
        x++
        continue
      }
      let konec = x
      while (konec < size && data[y][konec]) konec++
      pot += `M${x} ${y}h${konec - x}v1h${x - konec}z`
      x = konec
    }
  }
  return { velikost: size, pot }
}

/* Števka 28 × 28 (base64, 784 bajtov, 0 = papir, 255 = črnilo) v podatke slike
   za platno: črnilo temno na belem. */
export function slikaStevke(base64: string): Uint8ClampedArray<ArrayBuffer> {
  const bajti = atob(base64)
  const piksli = new Uint8ClampedArray(new ArrayBuffer(28 * 28 * 4))
  for (let i = 0; i < 28 * 28; i++) {
    const v = 255 - (i < bajti.length ? bajti.charCodeAt(i) : 0)
    piksli[i * 4] = v
    piksli[i * 4 + 1] = v
    piksli[i * 4 + 2] = v
    piksli[i * 4 + 3] = 255
  }
  return piksli
}
