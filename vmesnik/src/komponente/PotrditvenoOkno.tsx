/* Potrditveno okno za nepovratna dejanja (zreb, brisanje, zakljucek).
   Nadomesca window.confirm: sistemskega okna ni mogoce oblikovati,
   poleg tega blokira celotno stran. */
import { ModalnoOkno } from './ModalnoOkno'

interface Lastnosti {
  naslov: string
  sporocilo: string
  /* Napis na potrditvenem gumbu, npr. "Izvedi žreb". */
  besedaPotrditve: string
  onPotrdi: () => void
  onZapri: () => void
}

export function PotrditvenoOkno({
  naslov,
  sporocilo,
  besedaPotrditve,
  onPotrdi,
  onZapri,
}: Lastnosti) {
  return (
    /* Okno je vedno "nevarno": dejanja, ki jih potrjuje, ni mogoce razveljaviti,
       zato crta pod naslovom in potrditveni gumb nosita rjasto barvo. */
    <ModalnoOkno naslov={naslov} onZapri={onZapri} nevarno>
      <p className="potrditev__sporocilo">{sporocilo}</p>
      <div className="obrazec__gumbi">
        <button type="button" className="gumb" onClick={onZapri}>
          Prekliči
        </button>
        <button
          type="button"
          className="gumb gumb--nevaren"
          onClick={() => {
            onPotrdi()
            onZapri()
          }}
        >
          {besedaPotrditve}
        </button>
      </div>
    </ModalnoOkno>
  )
}
