/* Glava profila igralca (možnost 1a »Urejeno«, design_handoff_profil_glava).

   Čisto prikazna komponenta: vse vrednosti dobi že pripravljene od ProfilStran
   (ali od razvojnega harnessa /dev/profil-glava). Trije sklopi: kdo je (portret,
   ime, klub, uvrstitev), kolikšen je (modra ploskev ratinga s primerjavama) in
   kaj je odigral (vrstica štirih številk). Dejanja so zgoraj desno: eno glavno
   in meni »⋯«. */
import type { CSSProperties, ReactNode } from 'react'
import { useEffect, useRef, useState } from 'react'
import { Link } from 'react-router-dom'

export interface GlavaUvrstitev {
  mesto: number
  od: number
  /* Oznaka lestvice brez predpone »Lestvica«, npr. »Moški · Člani«. */
  oznaka: string
}

export interface GlavaPas {
  mesto: number
  od: number
  oznaka: string
}

export interface GlavaPostavka {
  oznaka: string
  to?: string
  naKlik?: () => void
}

export type GlavnoDejanje =
  | { vrsta: 'povezava'; to: string; oznaka: string }
  | { vrsta: 'spremljanje'; ime: string; spremljam: boolean; naPreklop: () => void }

export interface ProfilGlavaLastnosti {
  /* Povezava »← …« (logika izvora ostane v ProfilStran). */
  nazaj: ReactNode
  glavno: GlavnoDejanje | null
  meni: GlavaPostavka[]
  /* DruzinaGumb, napaka spremljanja ipd.: prikaže se samo, če ni null. */
  dodatno?: ReactNode
  ime: string
  priimek: string
  slika: string | null
  klub: string
  roka: string | null
  jeMoj: boolean
  uvrstitev: (GlavaUvrstitev & { percentil: number | null }) | null
  pas: GlavaPas | null
  rating: number | null
  /* Noga ploskve ratinga: samo ob zasebnih podatkih. */
  noga: { sprememba: string; vrh: string | null } | null
  klubskoPovprecje: { vrednost: number; razlika: number | null } | null
  doMesta: { mesto: number; razlika: number; tock: string } | null
  odigrane: number
  turnirskih: number
  ligaskih: number
  zmage: number
  porazi: number
  odstotekZmag: number
  zadnjaTekma: string
  /* Razvojni harness: meni odprt že ob prvem izrisu. */
  meniOdprt?: boolean
}

function odstotek(del: number, celota: number): number {
  return celota > 0 ? (del / celota) * 100 : 0
}

export function ProfilGlava(l: ProfilGlavaLastnosti) {
  const [meniOdprt, nastaviMeni] = useState(l.meniOdprt ?? false)
  const gumbMenija = useRef<HTMLButtonElement | null>(null)

  /* Meni se zapre s klikom drugam in z Escape (fokus nazaj na gumb). */
  useEffect(() => {
    if (!meniOdprt) return
    function obKliku(dogodek: MouseEvent) {
      const tarca = dogodek.target as Element | null
      if (tarca?.closest('.profil-glava__meni, .profil-glava__meni-gumb')) return
      nastaviMeni(false)
    }
    function obTipki(dogodek: KeyboardEvent) {
      if (dogodek.key === 'Escape') {
        nastaviMeni(false)
        gumbMenija.current?.focus()
      }
    }
    document.addEventListener('mousedown', obKliku)
    document.addEventListener('keydown', obTipki)
    return () => {
      document.removeEventListener('mousedown', obKliku)
      document.removeEventListener('keydown', obTipki)
    }
  }, [meniOdprt])

  const razlikaKluba = l.klubskoPovprecje?.razlika ?? null
  const dodatno = l.dodatno ?? null

  return (
    <div className="profil-glava">
      <div className="profil-glava__vrstica">
        {l.nazaj}
        {(l.glavno || l.meni.length > 0) && (
          <div className="profil-glava__dejanja">
            {l.glavno?.vrsta === 'povezava' && (
              <Link to={l.glavno.to} className="profil-glava__glavno-dejanje">
                {l.glavno.oznaka}
              </Link>
            )}
            {l.glavno?.vrsta === 'spremljanje' &&
              (l.glavno.spremljam ? (
                <button
                  type="button"
                  className="profil-glava__glavno-dejanje profil-glava__glavno-dejanje--obroba"
                  aria-pressed
                  aria-label={`Nehaj spremljati ${l.glavno.ime}`}
                  onClick={l.glavno.naPreklop}
                >
                  Spremljaš
                </button>
              ) : (
                <button
                  type="button"
                  className="profil-glava__glavno-dejanje profil-glava__glavno-dejanje--spremljaj"
                  aria-pressed={false}
                  aria-label={`Spremljaj ${l.glavno.ime}`}
                  onClick={l.glavno.naPreklop}
                >
                  Spremljaj
                </button>
              ))}
            {l.meni.length > 0 && (
              <>
                <button
                  ref={gumbMenija}
                  type="button"
                  className="profil-glava__meni-gumb"
                  aria-label="Več dejanj"
                  aria-haspopup="menu"
                  aria-expanded={meniOdprt}
                  onClick={() => nastaviMeni((o) => !o)}
                >
                  ⋯
                </button>
                {meniOdprt && (
                  <div className="kartoteka__meni profil-glava__meni" role="menu">
                    {l.meni.map((p) =>
                      p.to ? (
                        <Link
                          key={p.oznaka}
                          to={p.to}
                          role="menuitem"
                          className="kartoteka__meni-postavka"
                          onClick={() => nastaviMeni(false)}
                        >
                          {p.oznaka}
                        </Link>
                      ) : (
                        <button
                          key={p.oznaka}
                          type="button"
                          role="menuitem"
                          className="kartoteka__meni-postavka"
                          onClick={() => {
                            nastaviMeni(false)
                            p.naKlik?.()
                          }}
                        >
                          {p.oznaka}
                        </button>
                      ),
                    )}
                  </div>
                )}
              </>
            )}
          </div>
        )}
      </div>

      {dodatno && <div className="profil-glava__dodatno">{dodatno}</div>}

      <div className={'profil-glava__glavno' + (l.slika ? '' : ' profil-glava__glavno--brez-slike')}>
        {l.slika && (
          <div className="profil-glava__slika">
            <img src={l.slika} alt="" />
          </div>
        )}

        {/* Na telefonu je »oseba« display: contents: ime in uvrstitev sta tam
            ločena sklopa, med njima pa stoji ploskev ratinga. */}
        <div className="profil-glava__oseba">
          <div
            className="profil-glava__kdo"
            style={{ '--znakov': Math.max(l.priimek.length, 1) } as CSSProperties}
          >
            <h1 className="profil-glava__ime">
              <span className="profil-glava__ime-nad">{l.ime}</span>
              <span className="profil-glava__ime-glavni">{l.priimek}</span>
            </h1>
            <div className="profil-glava__meta">
              <span className="profil-glava__klub">{l.klub}</span>
              {l.roka && <span className="profil-glava__roka">{l.roka}</span>}
              {l.jeMoj && <span className="profil-glava__moj">Tvoj profil</span>}
            </div>
          </div>

          {l.uvrstitev && (
            <div className="profil-glava__uvrstitev">
              <div className="profil-glava__uvrstitev-telo">
                <div className="profil-glava__mesto-vrstica">
                  <span className="profil-glava__mesto">{l.uvrstitev.mesto}.</span>
                  <span className="profil-glava__od">od {l.uvrstitev.od}</span>
                  <span className="profil-glava__lestvica">{l.uvrstitev.oznaka}</span>
                </div>
                {l.uvrstitev.percentil !== null && (
                  <>
                    <div className="profil-glava__palica" aria-hidden="true">
                      <div style={{ width: `${l.uvrstitev.percentil}%` }} />
                    </div>
                    <div className="profil-glava__boljsi">Boljši od {l.uvrstitev.percentil} %</div>
                  </>
                )}
                {l.pas && (
                  <div className="profil-glava__mesto-vrstica profil-glava__mesto-vrstica--pas">
                    <span className="profil-glava__mesto">{l.pas.mesto}.</span>
                    <span className="profil-glava__od">od {l.pas.od}</span>
                    <span className="profil-glava__lestvica">{l.pas.oznaka}</span>
                  </div>
                )}
              </div>
            </div>
          )}
        </div>

        <div className="profil-glava__rating-stolpec">
          <div className="profil-glava__rating">
            <div className="profil-glava__rating-vrstica">
              <span className="profil-glava__rating-oznaka">Turnirko rating</span>
              {/* Številka brez razlage se bere kot napaka ali naklonjenost -
                  posebej po prvem dnevu, ko skoči za stotine točk. */}
              <Link to="/o-ratingu" className="profil-glava__rating-razlaga">
                Kako se računa →
              </Link>
            </div>
            <div className="profil-glava__rating-vrednost">{l.rating ?? '—'}</div>
            {l.noga && (
              <div className="profil-glava__rating-noga">
                <span>{l.noga.sprememba}</span>
                {l.noga.vrh !== null && <span className="profil-glava__rating-vrh">{l.noga.vrh}</span>}
              </div>
            )}
          </div>

          {(l.klubskoPovprecje || l.doMesta) && (
            <div className="kolofon profil-glava__primerjava">
              {l.klubskoPovprecje && (
                <div className="kolofon__vrstica">
                  <span className="kolofon__oznaka">Klubsko povprečje</span>
                  <span className="kolofon__vrednost">
                    {l.klubskoPovprecje.vrednost}
                    {razlikaKluba !== null && (
                      <>
                        {' '}
                        <span
                          className={
                            razlikaKluba >= 0 ? 'kolofon__vrednost--poz' : 'kolofon__vrednost--neg'
                          }
                        >
                          {razlikaKluba >= 0 ? '+' : '−'}
                          {Math.abs(razlikaKluba)}
                        </span>
                      </>
                    )}
                  </span>
                </div>
              )}
              {l.doMesta && (
                <div className="kolofon__vrstica">
                  <span className="kolofon__oznaka">Do {l.doMesta.mesto}. mesta</span>
                  <span className="kolofon__vrednost">{l.doMesta.tock}</span>
                </div>
              )}
            </div>
          )}
        </div>
      </div>

      <div className="profil-glava__povzetek">
        <div className="profil-glava__kazalnik profil-glava__kazalnik--odigrane">
          <div className="profil-glava__vrednost">{l.odigrane}</div>
          <div className="profil-glava__oznaka">Odigrane tekme</div>
          <div className="profil-glava__palica-kazalnika" aria-hidden="true">
            <div
              className="profil-glava__palica-polna"
              style={{ width: `${odstotek(l.turnirskih, l.turnirskih + l.ligaskih)}%` }}
            />
          </div>
          <div className="profil-glava__legenda">
            <span>
              <i className="profil-glava__kvadratek profil-glava__kvadratek--turnirske" />
              {l.turnirskih} turnirske
            </span>
            <span>
              <i className="profil-glava__kvadratek profil-glava__kvadratek--ligaske" />
              {l.ligaskih} ligaške
            </span>
          </div>
        </div>

        <div className="profil-glava__kazalnik profil-glava__kazalnik--izkupicek">
          <div className="profil-glava__vrednost">{`${l.zmage}–${l.porazi}`}</div>
          <div className="profil-glava__oznaka">Zmage – porazi</div>
          <div className="profil-glava__palica-kazalnika profil-glava__palica-kazalnika--izid" aria-hidden="true">
            <div
              className="profil-glava__palica-polna"
              style={{ width: `${odstotek(l.zmage, l.zmage + l.porazi)}%` }}
            />
          </div>
          <div className="profil-glava__legenda profil-glava__legenda--izid">
            <span>
              <i className="profil-glava__kvadratek profil-glava__kvadratek--zmage" />
              zmage
            </span>
            <span>
              <i className="profil-glava__kvadratek profil-glava__kvadratek--porazi" />
              porazi
            </span>
          </div>
        </div>

        <div className="profil-glava__kazalnik profil-glava__kazalnik--uspesnost">
          <div className="profil-glava__vrednost">{`${l.odstotekZmag} %`}</div>
          <div className="profil-glava__oznaka">Uspešnost</div>
        </div>

        <div className="profil-glava__kazalnik profil-glava__kazalnik--zadnja">
          <div className="profil-glava__vrednost profil-glava__vrednost--datum">{l.zadnjaTekma}</div>
          <div className="profil-glava__oznaka">Zadnja tekma</div>
        </div>
      </div>
    </div>
  )
}
