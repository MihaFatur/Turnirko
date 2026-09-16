/* Okno s tockami po nizih ene odigrane tekme - odpre ga klik na koncano
   tekmo, ki ima tocke vpisane (turnirska mreza, seznam tekem skupine in
   zapisnik ligaskega srecanja). Samo za branje, zato ga dobi vsak gledalec.

   Okno ne ve, od kod tekma pride: turnirska (strani 1 in 2) in ligaska
   (domaci in gost) se pred klicem prevedeta v dve StranTekme, tako kot sta
   pri vnosu obe vrsti tekem ena komponenta TockeNizov.

   Strani sta vrstici, nizi stolpci - izid se bere od leve proti desni kot na
   semaforju, stolpci nizov pa so dovolj ozki, da gre tudi tekma na 7 nizov
   na telefon brez drsenja. */
import type { IzidTekme, NizVnos } from '../api/tipi'
import { OZNAKE_IZID } from '../api/tipi'
import { ModalnoOkno } from './ModalnoOkno'

export interface StranTekme {
  /* Igralec je ena vrstica, par dve - »Ana Novak / Eva Zajc« bi se v ozkem
     stolpcu prelomil na poljubnem mestu. */
  imena: string[]
  dobljeniNizi: number
  zmagovalec: boolean
}

interface Lastnosti {
  /* Kje je bila tekma odigrana, npr. ime kategorije ali oznaka tekme v
     zapisniku (»A-X«). */
  nadnaslov: string
  strani: [StranTekme, StranTekme]
  nizi: NizVnos[]
  izidTip: IzidTekme | null
  onZapri: () => void
}

export function NiziTekmeOkno({ nadnaslov, strani, nizi, izidTip, onZapri }: Lastnosti) {
  /* Predana tekma ima tocke samo do predaje - brez opombe bi zadnji niz
     izgledal kot napaka v vpisu. */
  const posebni = izidTip && izidTip !== 'IGRANO' ? OZNAKE_IZID[izidTip] : null

  return (
    <ModalnoOkno naslov="Točke po nizih" nadnaslov={nadnaslov} onZapri={onZapri}>
      <table className="tabela nizi-okno">
        <caption className="samo-za-bralnik">
          Točke po nizih: vrstici sta strani tekme, stolpci pa nizi po vrsti
        </caption>
        <thead>
          <tr>
            <th scope="col">
              <span className="samo-za-bralnik">Igralec</span>
            </th>
            <th scope="col" className="nizi-okno__izid">
              Izid
            </th>
            {nizi.map((_, i) => (
              <th scope="col" className="nizi-okno__niz" key={i}>
                <span className="samo-za-bralnik">Niz </span>
                {i + 1}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {strani.map((stran, s) => (
            <tr
              key={s}
              className={stran.zmagovalec ? 'nizi-okno__vrsta--zmaga' : 'nizi-okno__vrsta--poraz'}
            >
              <th scope="row" className="nizi-okno__ime">
                {stran.imena.map((ime) => (
                  <span className="nizi-okno__ime-vrsta" key={ime}>
                    {ime}
                  </span>
                ))}
              </th>
              <td className="nizi-okno__izid">{stran.dobljeniNizi}</td>
              {nizi.map((niz, i) => {
                const moje = s === 0 ? niz.tocke1 : niz.tocke2
                const nasprotnik = s === 0 ? niz.tocke2 : niz.tocke1
                return (
                  <td
                    key={i}
                    className={
                      'nizi-okno__niz' + (moje > nasprotnik ? ' nizi-okno__niz--dobljen' : '')
                    }
                  >
                    {moje}
                  </td>
                )
              })}
            </tr>
          ))}
        </tbody>
      </table>
      {posebni && <p className="namig">Tekma je končana z izidom »{posebni.toLowerCase()}«.</p>}
    </ModalnoOkno>
  )
}
