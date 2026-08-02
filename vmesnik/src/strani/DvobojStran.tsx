/* "1 na 1": lifetime izid med dvema igralcema prek vseh tekmovanj, z zgodovino
   vseh medsebojnih tekem in spremembami ELO. Vidno vsem (tudi gostom).

   Stran namenoma nima svojega naslova: po maketi sta naslov strani imeni obeh
   igralcev v semaforju, ki ga izriše komponenta EnaNaEna. */
import { EnaNaEna } from '../komponente/EnaNaEna'

export function DvobojStran() {
  return (
    <section>
      {/* Naslov je viden samo bralniku zaslona: maketa ga nima, dokument brez
          imena pa je za tipkovnico in bralnik slepa stran. */}
      <h1 className="samo-za-bralnik">Primerjava dveh igralcev, 1 na 1</h1>
      <EnaNaEna pokaziZgodovino />
    </section>
  )
}
