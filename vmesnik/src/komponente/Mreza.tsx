/* Izlocilna mreza: stolpec za vsako kolo, od prvega kola do finala.
   Tekme so razvrscene po poziciji; navpicno poravnavo naredi CSS
   (enakomerna porazdelitev po visini stolpca). */
import type { TekmaDto } from '../api/tipi'
import { imeKola } from '../pomozno/oblikovanje'
import { TekmaKartica } from './TekmaKartica'

interface Lastnosti {
  tekme: TekmaDto[]
  naKlikTekme?: (tekma: TekmaDto) => void
}

export function Mreza({ tekme, naKlikTekme }: Lastnosti) {
  /* Za zdaj rise samo glavno mrezo; skupine in tolazilne tekme pridejo kasneje. */
  const glavne = tekme.filter((tekma) => tekma.faza === 'GLAVNI')
  if (glavne.length === 0) {
    return <p className="obvestilo">Mreža še ni ustvarjena.</p>
  }

  const zadnjeKolo = Math.max(...glavne.map((tekma) => tekma.kolo))
  const kola: TekmaDto[][] = []
  for (let kolo = 1; kolo <= zadnjeKolo; kolo++) {
    kola.push(
      glavne
        .filter((tekma) => tekma.kolo === kolo)
        .sort((prva, druga) => prva.pozicija - druga.pozicija),
    )
  }

  return (
    <div className="mreza">
      {kola.map((tekmeKola, indeks) => (
        <div className="mreza__kolo" key={indeks}>
          <div className="mreza__naslov-kola">{imeKola(indeks + 1, zadnjeKolo)}</div>
          <div className="mreza__tekme">
            {tekmeKola.map((tekma) => (
              <TekmaKartica key={tekma.id} tekma={tekma} naKlik={naKlikTekme} />
            ))}
          </div>
        </div>
      ))}
    </div>
  )
}
