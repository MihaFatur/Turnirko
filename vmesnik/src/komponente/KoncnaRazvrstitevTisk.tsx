/* Končna razvrstitev za tisk (predaja design_handoff_koncna_razvrstitev, 1c):
   cela razvrstitev na pokončnem A4, črno-belo, brez slik. Vrh (mesta 1.–3.)
   stoji v vrsticah čez celo širino, ostali v dveh stolpcih; pri več kot ~60
   udeležencih se seznam prelomi na drugo stran (vrstice se ne lomijo, glava se
   ne ponavlja). Isti podatek kot na zaslonu (`UdelezenecRazvrstitve`). */
import {
  type UdelezenecRazvrstitve,
  razdeliNaStopnice,
  stolpciTiska,
} from '../pomozno/koncnaRazvrstitev'

export function KoncnaRazvrstitevTisk({
  naslov,
  lastnosti,
  skupaj,
  udelezenci,
}: {
  /* Ime turnirja (ali dogodka), ki ga list nosi kot naslov. */
  naslov: string
  /* »Člani · Moški · Posamezno · Izločilni sistem« */
  lastnosti: string
  /* »48 igralcev« */
  skupaj: string
  udelezenci: UdelezenecRazvrstitve[]
}) {
  const { vrh, ostali } = razdeliNaStopnice(udelezenci)
  const stolpci = stolpciTiska(ostali)

  return (
    <section className="razvrstitev-list">
      <header className="razvrstitev-list__glava">
        <div className="razvrstitev-list__znamka">
          <svg width="22" height="22" viewBox="0 0 28 28" aria-hidden="true">
            <rect x="1.5" y="4" width="3" height="20" />
            <rect x="8.5" y="4" width="3" height="20" />
            <rect x="15.5" y="4" width="3" height="20" />
            <rect x="22.5" y="4" width="3" height="20" />
          </svg>
          Turnirko
        </div>
        <span className="razvrstitev-list__oznaka">Končna razvrstitev</span>
      </header>

      <div className="razvrstitev-list__naslov-blok">
        <h1 className="razvrstitev-list__naslov">{naslov}</h1>
        <p className="razvrstitev-list__lastnosti">
          {lastnosti} · {skupaj}
        </p>
      </div>

      <div className="razvrstitev-list__vrh">
        {vrh.map((u) => (
          <div className="razvrstitev-list__vrh-vrstica" key={u.id}>
            <span className="razvrstitev-list__vrh-mesto">{u.oznaka}</span>
            <span className="razvrstitev-list__vrh-ime">
              <span className="razvrstitev-list__ime-lahko">{u.ime}</span>{' '}
              <span className="razvrstitev-list__priimek-vrh">{u.priimek}</span>
            </span>
            <span className="razvrstitev-list__vrh-desno">
              {u.klub} {u.izid}
            </span>
          </div>
        ))}
      </div>

      {ostali.length > 0 && (
        <div className="razvrstitev-list__ostali">
          {stolpci.map((stolpec, i) => (
            <div className="razvrstitev-list__stolpec" key={i}>
              {stolpec.map(({ udelezenec: u, oznaka, rob }) => (
                <div
                  className={`razvrstitev-list__vrstica razvrstitev-list__vrstica--${rob}`}
                  key={u.id}
                >
                  <span className="razvrstitev-list__vrstica-mesto">{oznaka}</span>
                  <span className="razvrstitev-list__vrstica-ime">
                    <span className="razvrstitev-list__ime-lahko">{u.ime}</span>{' '}
                    <span className="razvrstitev-list__priimek">{u.priimek}</span>
                  </span>
                  <span className="razvrstitev-list__vrstica-desno">
                    {u.klub} {u.izid}
                  </span>
                </div>
              ))}
            </div>
          ))}
        </div>
      )}

      <footer className="razvrstitev-list__noga">
        <span>Turnirko · turnirko-nt.si</span>
        <span>Natisnjeno iz končne razvrstitve</span>
      </footer>
    </section>
  )
}
