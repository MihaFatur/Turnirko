/* Izlocilna mreza: stolpec za vsako kolo, od izbranega kola do finala.

   Mreza 64 igralcev se namenoma NE rise cela: izbrano kolo je prvi stolpec,
   desno od njega stojijo vsa naslednja kola. Privzeto je izbrano najzgodnejse
   kolo, ki se ni v celoti odigrano - tam se turnir dogaja.

   Navpicno poravnavo naredi CSS (enakomerna porazdelitev po visini stolpca). */
import type { TekmaDto } from '../api/tipi'
import { imeKola } from '../pomozno/oblikovanje'
import { TekmaKartica } from './TekmaKartica'

interface Lastnosti {
  tekme: TekmaDto[]
  naKlikTekme?: (tekma: TekmaDto) => void
  /* Prvo kolo, ki se izrise; nizja kola so skrita. Brez vrednosti se izrise
     cela mreza (npr. za tisk ali kratke mreze). */
  odKola?: number
  /* Id prijave, katere pot je osvetljena skozi vsa kola. */
  osvetljenaPrijava?: number | null
  naOsvetlitev?: (idPrijave: number | null) => void
}

/* Kola mreze: [{ kolo, tekme }] od prvega do finala. */
export function kolaMreze(tekme: TekmaDto[]): { kolo: number; tekme: TekmaDto[] }[] {
  const glavne = tekme.filter((tekma) => tekma.faza === 'GLAVNI')
  if (glavne.length === 0) return []
  const zadnjeKolo = Math.max(...glavne.map((tekma) => tekma.kolo))
  const kola: { kolo: number; tekme: TekmaDto[] }[] = []
  for (let kolo = 1; kolo <= zadnjeKolo; kolo++) {
    kola.push({
      kolo,
      tekme: glavne
        .filter((tekma) => tekma.kolo === kolo)
        .sort((prva, druga) => prva.pozicija - druga.pozicija),
    })
  }
  return kola
}

export function Mreza({
  tekme,
  naKlikTekme,
  odKola,
  osvetljenaPrijava = null,
  naOsvetlitev,
}: Lastnosti) {
  const kola = kolaMreze(tekme)
  if (kola.length === 0) {
    return <p className="obvestilo">Mreža še ni ustvarjena.</p>
  }

  const zadnjeKolo = kola.length
  const prvo = odKola ?? 1
  const vidna = kola.filter((k) => k.kolo >= prvo)

  return (
    <div className="mreza">
      {vidna.map((k) => (
        <div className="mreza__kolo" key={k.kolo}>
          <div className="mreza__naslov-kola">{imeKola(k.kolo, zadnjeKolo)}</div>
          <div className="mreza__tekme">
            {k.tekme.map((tekma) => (
              <TekmaKartica
                key={tekma.id}
                tekma={tekma}
                naKlik={naKlikTekme}
                osvetljenaPrijava={osvetljenaPrijava}
                naOsvetlitev={naOsvetlitev}
              />
            ))}
          </div>
        </div>
      ))}
    </div>
  )
}
