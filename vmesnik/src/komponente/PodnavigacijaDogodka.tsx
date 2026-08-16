/* Pas pogledov strani dogodka: gumbi pogleda levo, povzetek desno.

   Pas se ne odloca po sistemu tekmovanja, ampak po tem, kaj dogodek DEJANSKO
   ima (skupine, izlocilne tekme, prijave). Zato nov sistem tekmovanja doda
   svoj pogled brez posega v to komponento, dogodek s sistemom, ki ga
   podnavigacija ne pozna, pa se izrise brez napake - le z gumbi, ki mu
   pripadajo.

   Na namizju je pas lepljiv pod glavo strani. Na telefonu se preseli V glavo
   (portal v GlavaZavihki): prej je stal pod celotnim naslovnim blokom in ga je
   pri zakljuceni kategoriji zakrilo ~1400 px razvrstitve - krmilo strani mora
   biti nad vsebino, ki jo krmili. Povzetek tam odpade, ker isto stevilo nosijo
   zavihki sami ("Tekme · 66"). */
import { GlavaZavihki } from './GlavaTelefona'
import { useTelefon } from '../pomozno/telefon'

export type PogledDogodka = 'zakljucek' | 'skupine' | 'tekme' | 'mreza' | 'udelezenci'

export interface PogledGumb {
  kljuc: PogledDogodka
  oznaka: string
  /* Stevec ob oznaki; izrise se samo na telefonu, kjer nadomesca povzetek. */
  stevec?: number
}

interface Lastnosti {
  pogledi: PogledGumb[]
  izbrani: PogledDogodka
  naIzbiro: (pogled: PogledDogodka) => void
  /* Mono povzetek desno, npr. "25 skupin · odigranih 78 / 150". */
  povzetek: string
}

export function PodnavigacijaDogodka({ pogledi, izbrani, naIzbiro, povzetek }: Lastnosti) {
  const jeTelefon = useTelefon()

  /* En sam pogled ni izbira - pas bi bil samo crta z enim gumbom. */
  if (pogledi.length < 2 && (jeTelefon || !povzetek)) return null

  const gumbi = pogledi.map((pogled) => (
    <button
      type="button"
      key={pogled.kljuc}
      className={
        'izbirnik__gumb' + (izbrani === pogled.kljuc ? ' izbirnik__gumb--aktiven' : '')
      }
      aria-pressed={izbrani === pogled.kljuc}
      onClick={() => naIzbiro(pogled.kljuc)}
    >
      {pogled.oznaka}
      {jeTelefon && pogled.stevec !== undefined && ` · ${pogled.stevec}`}
    </button>
  ))

  if (jeTelefon) {
    return (
      <GlavaZavihki>
        <div className="podnavigacija podnavigacija--telefon">{gumbi}</div>
      </GlavaZavihki>
    )
  }

  return (
    <div className="podnavigacija">
      <div className="izbirnik">{gumbi}</div>
      {povzetek && <span className="podnavigacija__povzetek">{povzetek}</span>}
    </div>
  )
}
