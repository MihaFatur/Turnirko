/* Prikazni harness za posnetek lista vadnice števk, iz katerega test zaledja
   (VadnicaTest, resources/branje/vadnica-list-v2.png) riše fotografije izpolnjenega
   lista. Samo za razvoj: /dev/vadnica, naloži se leno in samo, kadar
   import.meta.env.DEV (glej App.tsx). Brez prijave in omrežja; id lista je stalen.

   Posnetek: brezglavi Chrome pri 10 px/mm (deviceScaleFactor 2,6458), izrez
   elementa .vadnica-list (2100 × 2970 px). Ob spremembi postavitve lista posnemi
   znova in preveri VadnicaTest. */
import { ListVadnice } from '../komponente/ListVadnice'

export default function VadnicaPosnetek() {
  return (
    <div className="listki-listi">
      <ListVadnice idLista={2411202601} />
    </div>
  )
}
