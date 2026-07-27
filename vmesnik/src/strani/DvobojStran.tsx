/* "1 na 1": lifetime izid med dvema igralcema prek vseh tekmovanj, z zgodovino
   vseh medsebojnih tekem in spremembami ELO. Vidno vsem (tudi gostom). */
import { EnaNaEna } from '../komponente/EnaNaEna'

export function DvobojStran() {
  return (
    <section>
      <div className="naslovna-vrstica">
        <div>
          <h1>1 na 1</h1>
          <p className="podnaslov">
            Medsebojni izidi dveh igralcev prek vseh tekmovanj — turnirjev in lig.
          </p>
        </div>
      </div>

      <EnaNaEna pokaziZgodovino />
    </section>
  )
}
