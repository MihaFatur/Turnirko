/* Pokrovitelji v nogi tiskanega lista (V56): ozka vrstica z logotipi pod
   vsebino. Ker je list natanko ena ležeča stran A4, vrstica nosi omejeno
   višino (največ 12 mm) in je ni, če pokroviteljev ni; list ji prostor odstopi
   (zapisnik z vrsticami mreže, mreža z višino vrstice).

   Velikost je po enaki ploščini kot na strani (meraLogotipa), le v mm:
   navadni ~9 x 9 mm, glavni ~11 x 11 mm; širok napis je nižji, a ne širši od
   40 mm. Slike so v barvi - logotip pokrovitelja je znamka, ne okras. */
import type { PokroviteljDto } from '../api/tipi'
import { urlSlike } from '../pomozno/slike'

const PLOSCINA_MM2 = { navadni: 80, glavni: 121 } as const
const NAJVEC_VISINA_MM = { navadni: 10, glavni: 12 } as const
const NAJVEC_SIRINA_MM = 40

function meraMm(p: PokroviteljDto): { sirina: number; visina: number } {
  const vrsta = p.glavni ? 'glavni' : 'navadni'
  const razmerje = p.slika.sirina / p.slika.visina
  const visina = Math.sqrt(PLOSCINA_MM2[vrsta] / razmerje)
  const sirina = visina * razmerje
  const k = Math.min(1, NAJVEC_SIRINA_MM / sirina, NAJVEC_VISINA_MM[vrsta] / visina)
  return { sirina: sirina * k, visina: visina * k }
}

export function PokroviteljiTisk({ pokrovitelji, razred }: { pokrovitelji: PokroviteljDto[]; razred: string }) {
  if (pokrovitelji.length === 0) return null
  return (
    <div className={'pokrovitelji-tisk ' + razred}>
      <span className="pokrovitelji-tisk__oznaka">
        {pokrovitelji.length === 1 ? 'Pokrovitelj' : 'Pokrovitelji'}
      </span>
      {pokrovitelji.map((p) => {
        const m = meraMm(p)
        return (
          <img
            key={`${p.slika.id}-${p.ime}`}
            className="pokrovitelji-tisk__slika"
            src={urlSlike(p.slika, 'TISK')}
            alt={p.ime}
            style={{ width: `${m.sirina.toFixed(2)}mm`, height: `${m.visina.toFixed(2)}mm` }}
          />
        )
      })}
    </div>
  )
}
