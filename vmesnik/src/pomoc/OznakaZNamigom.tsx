/* Oznaka polja obrazca z gumbom »?«.

   Gumb ne sme stati v <label>: gumb je sam »labelable« element, zato bi
   postal krmilo, ki ga oznaka poimenuje (klik na besedilo oznake bi odprl
   oblaček, izbirnik pa bi izgubil ime). Polje zato zgradimo iz <div> z
   razredom `obrazec__polje`, oznake z `htmlFor` in gumba ob njej; razred
   `.obrazec__polje > span` obliko oznake ohrani. */
import { Namig } from './Namig'

export function OznakaZNamigom({
  za,
  oznaka,
  clanek,
  razdelek,
}: {
  za: string
  oznaka: string
  clanek: string
  razdelek?: string
}) {
  return (
    <span className="oznaka-z-namigom">
      <label htmlFor={za}>{oznaka}</label>
      <Namig clanek={clanek} razdelek={razdelek} />
    </span>
  )
}
