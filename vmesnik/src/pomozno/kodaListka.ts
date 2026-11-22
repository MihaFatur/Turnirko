/* Koda tekme na sodniškem listku: QR koda in natisnjena številka ob njej.

   Strežnik iz fotografije listka prebere QR kodo in po njej ve, za katero
   tekmo gre (KodaListka.java) - brez nje bi moral tekmo ugibati po imenih.
   Zapis mora biti isti kot v zaledju: QR »TK:<id>:<skrivnost>« (alfanumerični
   način, zato je koda majhna: verzija 1), natisnjeno »T-<id>« (za človeka in za
   bralnika, kadar QR na fotografiji ni berljiva). Skrivnost (S1) da sodniku
   pravico poslati izid te tekme brez prijave; izračuna jo samo strežnik in jo
   da organizatorju za tisk (dogodkiApi.kodeListkov). */
import { encode } from 'uqr'

/* Zapis brez skrivnosti (listki pred S1): bralnik ga še prebere, sodniška pot
   (S2) pa ga ne sprejme. Samo zasilno, kadar kode tekme s strežnika ni. */
export function vsebinaQr(idTekme: number): string {
  return `TK:${idTekme}`
}

export function natisnjenaKoda(idTekme: number): string {
  return `T-${idTekme}`
}

/* QR koda kot pot SVG v enotah modulov: en pravokotnik na zaporedje temnih
   modulov v vrstici (manj elementov kot kvadrat na modul, isto ostro na
   papirju). Mirna cona okrog kode ni v poti - zagotovi jo rob v CSS.
   Popravljanje napak M (15 %): koda prenese madež ali pregib, a ostane majhna.
   Verzija je vedno 1 (21 modulov): nanjo računa poravnava v zaledju
   (GeometrijaListka.QR_MODULOV). »TK:<id>:<8 znakov>« gre vanjo do id-ja z 8
   števkami; daljši zapis vrže napako, namesto da bi tiho natisnil drugo kodo. */
export function potQr(vsebina: string, samoVerzija1 = true): { velikost: number; pot: string } {
  // hrbtna stran listka (S2) nosi spletni naslov, ki v verzijo 1 ne gre; sprednja vedno
  const { data, size } = encode(vsebina, samoVerzija1
    ? { ecc: 'M', border: 0, minVersion: 1, maxVersion: 1 }
    : { ecc: 'M', border: 0 })
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
