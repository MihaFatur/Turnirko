/* Koda tekme na sodniškem listku: QR koda in natisnjena številka ob njej.

   Strežnik iz fotografije listka prebere QR kodo in po njej ve, za katero
   tekmo gre (KodaListka.java) - brez nje bi moral tekmo ugibati po imenih.
   Zapis mora biti isti kot v zaledju: QR »TK:<id>« (alfanumerični način, zato
   je koda majhna: verzija 1 do 2), natisnjeno »T-<id>« (za človeka in za
   bralnika, kadar QR na fotografiji ni berljiva). */
import { encode } from 'uqr'

export function vsebinaQr(idTekme: number): string {
  return `TK:${idTekme}`
}

export function natisnjenaKoda(idTekme: number): string {
  return `T-${idTekme}`
}

/* QR koda kot pot SVG v enotah modulov: en pravokotnik na zaporedje temnih
   modulov v vrstici (manj elementov kot kvadrat na modul, isto ostro na
   papirju). Mirna cona okrog kode ni v poti - zagotovi jo rob v CSS.
   Popravljanje napak M (15 %): koda prenese madež ali pregib, a ostane majhna. */
export function potQr(vsebina: string): { velikost: number; pot: string } {
  const { data, size } = encode(vsebina, { ecc: 'M', border: 0 })
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
