/* Vsebina odprte skupine: lestvica skupine in njene tekme po kolih.
   Skupna za vse sisteme s skupinami (skupine + izločilni, finalne skupine za
   mesta, SV regija), da se tabela skupine nikjer ne izrise drugace. */
import type { SkupinaDto, TekmaDto } from '../api/tipi'
import { sklonEkip, sklonIgralcev } from '../pomozno/oblikovanje'
import { Lestvica } from './Lestvica'
import type { KlikTekme } from './TekmaKartica'
import { TekmeSeznam } from './TekmeSeznam'

/* »4 igralci« oz. pri skupini za mesta »5.–8. mesto · 4 ekipe«. */
export function opisSkupine(skupina: SkupinaDto, ekipno: boolean): string {
  const n = skupina.lestvica.length
  const clani = `${n} ${ekipno ? sklonEkip(n) : sklonIgralcev(n)}`
  return skupina.ime ? `${skupina.ime} · ${clani}` : clani
}

export function VsebinaSkupine({
  skupina,
  tekme,
  klik,
  napreduje,
  tolazilni,
  legenda = 'napredujeta v izločilni del',
  legendaTolazilni,
  ekipno,
}: {
  skupina: SkupinaDto
  tekme: TekmaDto[]
  klik?: KlikTekme
  napreduje?: number
  /* Do katerega mesta gre drugi pas (SV regija: v tolažilni žreb). */
  tolazilni?: number
  /* Besedilo legende pod lestvico; privzeto pove, da dva napredujeta. */
  legenda?: string
  legendaTolazilni?: string
  ekipno: boolean
}) {
  /* Tekme so razdeljene po kolih tako kot pri krožnem sistemu: brez tega je
     skupina osmih igralcev en sam seznam 28 vrstic, iz katerega ni razvidno,
     kaj je bilo odigrano skupaj in kaj šele pride. */
  const kola = [...new Set(tekme.map((t) => t.kolo))].sort((a, b) => a - b)
  return (
    <>
      <div>
        <Lestvica
          vrstice={skupina.lestvica}
          napreduje={napreduje}
          tolazilni={tolazilni}
          strnjena
          ekipno={ekipno}
        />
        {napreduje !== undefined && (
          <div className="legenda">
            <span className="legenda__postavka">
              <span className="legenda__znak legenda__znak--napreduje" />
              {legenda}
            </span>
            {legendaTolazilni && (
              <span className="legenda__postavka">
                <span className="legenda__znak legenda__znak--tolazilni" />
                {legendaTolazilni}
              </span>
            )}
          </div>
        )}
      </div>
      <div>
        {kola.map((kolo) => (
          <div key={kolo} className="kolo-skupina">
            <div className="kolo-skupina__naslov">{kolo}. kolo</div>
            <TekmeSeznam tekme={tekme.filter((t) => t.kolo === kolo)} klik={klik} strnjen />
          </div>
        ))}
      </div>
    </>
  )
}
