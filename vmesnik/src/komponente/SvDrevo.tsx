/* Drevo žreba za vsa mesta (SV regija), narisano kot izločilna mreža s črtami.

   Žreb za vsa mesta ni ena mreža, ampak več poti: zmagovalci gredo po glavni
   poti do finala, poraženci vsakega kola pa na svojo pot za slabša mesta. Pot
   (»odsek«) so vse tekme z istim začetnim mestom razpona: 1.–8., 1.–4. in 1.–2.
   so glavna pot, 5.–8. in 5.–6. druga, 3.–4. in 7.–8. sta samostojni tekmi. Odseki
   si sledijo po mestih - tako kot v papirnem zapisniku.

   Vmesnik tu računa samo TOPOLOGIJO (v katerem stolpcu je tekma in na kateri
   višini, v enotah vrstice); mere in črte nosi CSS prek spremenljivk (--y, --s),
   zato telefon dobi ožje kartice brez kakršnegakoli znanja o širini v JS.

   Višino tekme določita njena vira (povezava »zmagovalec tekme X«): stoji na
   sredini med njima. Tekma, katere en nasprotnik je prišel s prostim prehodom,
   ima samo en vir in stoji ob njem - drevo zato ostane pravilno tudi, ko
   tekem ni vse. */
import { useMemo } from 'react'

import type { SvZrebDto, TekmaDto } from '../api/tipi'
import { imeStopnje, opisRazpona } from '../pomozno/svRegija'
import { TekmaKartica, type KlikTekme } from './TekmaKartica'

interface Postavljena {
  tekma: TekmaDto
  stolpec: number
  /* Višina sredine kartice v enotah vrstice (0,5 = prva vrstica). */
  y: number
}

interface Odsek {
  od: number
  doMesta: number
  naslov: string
  stolpci: { velikost: number; od: number; glava: string }[]
  tekme: Postavljena[]
  /* Višina odseka v enotah vrstice. */
  visina: number
  /* Zadnja tekma odseka odloča par mest (od, od + 1): iz nje se bere uvrstitev. */
  zadnja: Postavljena | null
}

function postaviOdsek(tekmeOdseka: TekmaDto[], zreb: SvZrebDto): Odsek {
  const kola = [...new Set(tekmeOdseka.map((t) => t.kolo))].sort((a, b) => a - b)
  const y = new Map<number, number>()
  const postavljene: Postavljena[] = []

  kola.forEach((kolo, stolpec) => {
    const tekmeKola = tekmeOdseka
      .filter((t) => t.kolo === kolo)
      .sort((a, b) => a.pozicija - b.pozicija)

    // višina iz virov; tekma brez vira v odseku (prvo kolo) dobi naslednjo prosto vrstico
    const zelene = tekmeKola.map((t) => {
      const viri = [t.idIzvorZmagovalca1, t.idIzvorZmagovalca2]
        .filter((id): id is number => id !== null && y.has(id))
        .map((id) => y.get(id) as number)
      return viri.length > 0 ? viri.reduce((a, b) => a + b, 0) / viri.length : null
    })
    let prejsnja = -0.5
    tekmeKola.forEach((t, i) => {
      const zeljena = zelene[i]
      const visina = Math.max(zeljena ?? prejsnja + 1, prejsnja + 1)
      y.set(t.id, visina)
      prejsnja = visina
      postavljene.push({ tekma: t, stolpec, y: visina })
    })
  })

  const prva = tekmeOdseka[0]
  const od = prva.razponOd ?? 0
  // razpon tekme sega do konca mreže, žreb pa ima lahko manj igralcev (prosta mesta)
  const doMesta = Math.min(zreb.zadnjeMesto, Math.max(...tekmeOdseka.map((t) => t.razponDo ?? od)))
  const stolpci = kola.map((kolo) => {
    const vzorec = tekmeOdseka.find((t) => t.kolo === kolo) as TekmaDto
    const vOd = vzorec.razponOd ?? od
    const vDo = vzorec.razponDo ?? vOd
    const velikost = vDo - vOd + 1
    const glava =
      velikost === 2
        ? vOd === zreb.prvoMesto && zreb.indeks === 0
          ? 'Finale'
          : `Za ${vOd}. mesto`
        : imeStopnje(velikost)
    return { velikost, od: vOd, glava }
  })

  const zadnjeKolo = kola[kola.length - 1]
  const zadnja = postavljene.find((p) => p.tekma.kolo === zadnjeKolo) ?? null
  const velikostOdseka = doMesta - od + 1
  return {
    od,
    doMesta,
    naslov: velikostOdseka === 2 ? `Za ${od}. mesto` : opisRazpona(od, doMesta),
    stolpci,
    tekme: postavljene,
    visina: Math.max(...postavljene.map((p) => p.y)) + 0.5,
    zadnja,
  }
}

/* Odseki žreba po mestih; znotraj odseka tekme po kolih. */
export function odsekiZreba(tekme: TekmaDto[], zreb: SvZrebDto): Odsek[] {
  const poZacetku = new Map<number, TekmaDto[]>()
  for (const t of tekme) {
    const od = t.razponOd ?? 0
    poZacetku.set(od, [...(poZacetku.get(od) ?? []), t])
  }
  return [...poZacetku.entries()]
    .sort((a, b) => a[0] - b[0])
    .map(([, ts]) => postaviOdsek(ts, zreb))
}

interface Lastnosti {
  tekme: TekmaDto[]
  zreb: SvZrebDto
  klik?: KlikTekme
  osvetljena: number | null
  naOsvetlitev: (idPrijave: number | null) => void
}

export function SvDrevo({ tekme, zreb, klik, osvetljena, naOsvetlitev }: Lastnosti) {
  const odseki = useMemo(() => odsekiZreba(tekme, zreb), [tekme, zreb])
  const imena = useMemo(() => {
    const poId = new Map<number, string>()
    for (const t of tekme) {
      if (t.udelezenec1) poId.set(t.udelezenec1.idPrijave, t.udelezenec1.polnoIme)
      if (t.udelezenec2) poId.set(t.udelezenec2.idPrijave, t.udelezenec2.polnoIme)
    }
    return poId
  }, [tekme])

  return (
    <div className="sv-drevo">
      {odseki.map((odsek) => {
        const stolpcev = odsek.stolpci.length
        const idTekme = new Map(odsek.tekme.map((p) => [p.tekma.id, p]))
        return (
          <section className="sv-drevo__odsek" key={odsek.od}>
            <h4 className="sv-drevo__naslov">{odsek.naslov}</h4>
            <div className="sv-drevo__ovoj">
              {/* Samostojna tekma (za 3. mesto) ima že naslov odseka - glava stolpca bi ga
                  ponovila. */}
              {stolpcev > 1 && (
                <div className="sv-drevo__stolpci" style={{ ['--n' as string]: stolpcev }}>
                  {odsek.stolpci.map((stolpec, i) => (
                    <div className="sv-drevo__glava" key={i}>
                      {stolpec.glava}
                    </div>
                  ))}
                  <div className="sv-drevo__glava sv-drevo__glava--mesta">Uvrstitev</div>
                </div>
              )}
              <div
                className="sv-drevo__platno"
                style={{ ['--n' as string]: stolpcev, ['--v' as string]: odsek.visina }}
              >
                {/* Črte: vodoravni krak iz vsakega vira, navpična povezava in vstop v tekmo. */}
                {odsek.tekme.map((p) => {
                  const viri = [p.tekma.idIzvorZmagovalca1, p.tekma.idIzvorZmagovalca2]
                    .map((id) => (id !== null ? idTekme.get(id) : undefined))
                    .filter((v): v is Postavljena => v !== undefined)
                  if (viri.length === 0) return null
                  const ys = viri.map((v) => v.y)
                  const zgoraj = Math.min(...ys, p.y)
                  const spodaj = Math.max(...ys, p.y)
                  const stil = (s: number, yy: number) => ({
                    ['--s' as string]: s,
                    ['--y' as string]: yy,
                  })
                  return (
                    <span key={`v${p.tekma.id}`}>
                      {viri.map((v) => (
                        <i
                          className="sv-drevo__crta sv-drevo__crta--iz"
                          key={v.tekma.id}
                          style={stil(v.stolpec, v.y)}
                        />
                      ))}
                      <i
                        className="sv-drevo__crta sv-drevo__crta--navpicno"
                        style={{
                          ...stil(p.stolpec - 1, zgoraj),
                          ['--dolzina' as string]: spodaj - zgoraj,
                        }}
                      />
                      <i className="sv-drevo__crta sv-drevo__crta--vstop" style={stil(p.stolpec, p.y)} />
                    </span>
                  )
                })}

                {odsek.tekme.map((p) => (
                  <div
                    className="sv-drevo__tekma"
                    key={p.tekma.id}
                    style={{ ['--s' as string]: p.stolpec, ['--y' as string]: p.y }}
                  >
                    <TekmaKartica
                      tekma={p.tekma}
                      klik={klik}
                      osvetljenaPrijava={osvetljena}
                      naOsvetlitev={naOsvetlitev}
                    />
                  </div>
                ))}

                {odsek.zadnja && (
                  <UvrstitevOdseka
                    zadnja={odsek.zadnja}
                    od={odsek.od}
                    stolpec={stolpcev}
                    imena={imena}
                  />
                )}
              </div>
            </div>
          </section>
        )
      })}
    </div>
  )
}

/* Kdo je dobil par mest, ki ga odloča zadnja tekma odseka: »5. Bor Markovič«,
   »6. Aleksander Nelson Hujs«. Dokler tekma ni odigrana, ostane prazno. */
function UvrstitevOdseka({
  zadnja,
  od,
  stolpec,
  imena,
}: {
  zadnja: Postavljena
  od: number
  stolpec: number
  imena: Map<number, string>
}) {
  const t = zadnja.tekma
  if (t.status !== 'KONCANA' || t.idZmagovalcaPrijave === null) return null
  const zmagovalec = t.idZmagovalcaPrijave
  const porazenec =
    t.udelezenec1?.idPrijave === zmagovalec ? t.udelezenec2?.idPrijave : t.udelezenec1?.idPrijave
  return (
    <ol
      className="sv-drevo__mesta"
      style={{ ['--s' as string]: stolpec, ['--y' as string]: zadnja.y }}
    >
      <li>
        <span className="sv-drevo__mesto">{od}.</span>
        {imena.get(zmagovalec)}
      </li>
      {porazenec !== undefined && porazenec !== null && (
        <li>
          <span className="sv-drevo__mesto">{od + 1}.</span>
          {imena.get(porazenec)}
        </li>
      )}
    </ol>
  )
}
