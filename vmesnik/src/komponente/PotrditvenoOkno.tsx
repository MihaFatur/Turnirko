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
    <ModalnoOkno naslov={naslov} onZapri={onZapri}>
      <p className="potrditev__sporocilo">{sporocilo}</p>
      <div className="obrazec__gumbi">
        <button type="button" className="gumb" onClick={onZapri}>
          Prekliči
        </button>
        <button
          type="button"
          className="gumb gumb--glavni"
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
