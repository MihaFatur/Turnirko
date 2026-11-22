/* Prikazni harness za posnetek sodniškega listka, iz katerega simulacija branja
   (zaledje: SimuliraneFotografijeTest, resources/branje/listek-5-nizov.png) riše
   fotografije. Samo za razvoj: /dev/listek, naloži se leno in samo, kadar
   import.meta.env.DEV (glej App.tsx). Brez prijave in omrežja; podatki so fiksni.

   Posnetek: brezglavi Chrome, deviceScaleFactor 3 (11,34 px/mm), izrez elementa
   .listek. Listek je prvi na listu A4, zato ima desno in spodaj črtkani rez kot
   pri tisku. Ob spremembi postavitve listka posnemi znova in preveri simulacijo.

   Parametri:
     miza=<n>   natisnjena številka mize v okencu (privzeto prazno okence) */
import { useSearchParams } from 'react-router-dom'

import { Listek, type ListekPodatki } from '../komponente/Listek'

const PODATKI: ListekPodatki = {
  dogodek: '3. OT Savinja – preizkus branja',
  kategorija: 'U19 mladinci · Moški',
  skupina: 'A',
  faza: '2. kolo',
  steviloNizov: 5,
  miza: null,
  idTekme: 3,
  // vsebina kot iz SkrivnostListka: 8 znakov base32 (skrivnost tu ni prava)
  kodaQr: 'TK:3:ABCD2345',
  stran1: { ime: 'Nik Horvat' },
  stran2: { ime: 'Tim Kranjc' },
}

export default function ListekPosnetek() {
  const [parametri] = useSearchParams()
  const miza = parametri.get('miza')
  return (
    <div className="listki-listi">
      <section className="listki-list" aria-label="Listek za posnetek">
        <Listek podatki={{ ...PODATKI, miza: miza ? Number(miza) : null }} />
      </section>
    </div>
  )
}
