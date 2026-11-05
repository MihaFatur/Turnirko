/* Pogled »Udeleženci« dogodka (predaja design_handoff_udelezenci, varianta 1a).

   Zaporedje od zgoraj: naslovna vrstica, opomba, filtri (iskanje, klub,
   razvrstitev), kartice nosilcev 1–4, skupine nosilcev in dodatek (rezerve,
   igralci brez soigralca). Isti pogled velja za posameznike, pare in ekipe ter za
   vse tri faze (priprava, v teku, zaključen); kaj se v kateri fazi pokaže, določa
   pomozno/udelezenci.

   Namizje in telefon sta ena struktura z dvema postavitvama (medijska poizvedba
   v slog.css), razen besedila v polju za iskanje. Dejanje ob udeležencu
   (odstop med tekmovanjem) pride iz strani; gledalec ga nima. */
import { useEffect, useMemo, useState, type CSSProperties, type ReactNode } from 'react'

import type { MrezaDto } from '../api/tipi'
import { useTelefon } from '../pomozno/telefon'
import {
  glavaImena,
  metaUdelezencev,
  moznostiKluba,
  moznostiRazvrstitve,
  naslovUdelezencev,
  opombaUdelezencev,
  sestaviPrikaz,
  udelezenciDogodka,
  type DelImena,
  type FazaUdelezencev,
  type RazvrstitevUdelezencev,
  type VrstaUdelezencev,
  type VrsticaUdelezenca,
} from '../pomozno/udelezenci'
import { MeniIzbire } from './MeniIzbire'

interface Lastnosti {
  podatki: MrezaDto
  /* Dejanje ob vrstici in kartici (npr. »Odstopil« za organizatorja); vrne null,
     kjer ga ni. */
  dejanje?: (vrstica: VrsticaUdelezenca) => ReactNode
}

type OdprtMeni = 'klub' | 'razvrstitev' | null

export function UdelezenciPogled({ podatki, dejanje }: Lastnosti) {
  const jeTelefon = useTelefon()
  const { vrsta, faza, vrstice, imaIzpad, dodatek } = useMemo(
    () => udelezenciDogodka(podatki),
    [podatki],
  )

  const [iskanje, nastaviIskanje] = useState('')
  const [klub, nastaviKlub] = useState<string | null>(null)
  const [razvrstitev, nastaviRazvrstitev] = useState<RazvrstitevUdelezencev>('jakost')
  const [odprtMeni, nastaviOdprtMeni] = useState<OdprtMeni>(null)

  const moznostiKlubov = useMemo(() => moznostiKluba(vrstice), [vrstice])
  const moznostiRazvrstitev = useMemo(() => moznostiRazvrstitve(faza), [faza])

  /* Izbira, ki je seznam po osveritvi ne pozna več (odjavil se je zadnji igralec
     kluba, dogodek je prešel v drugo fazo), ne sme pustiti seznama praznega brez
     razloga. */
  useEffect(() => {
    if (klub !== null && !moznostiKlubov.some((m) => m.vrednost === klub)) nastaviKlub(null)
  }, [moznostiKlubov, klub])
  const veljavnaRazvrstitev = moznostiRazvrstitev.some((m) => m.vrednost === razvrstitev)
    ? razvrstitev
    : 'jakost'

  const kazeVIgri = faza === 'v_teku' && imaIzpad
  const prikaz = useMemo(
    () => sestaviPrikaz(vrstice, { iskanje, klub, razvrstitev: veljavnaRazvrstitev }, kazeVIgri),
    [vrstice, iskanje, klub, veljavnaRazvrstitev, kazeVIgri],
  )

  const razred = 'udelezenci' + (vrsta === 'posamezniki' ? '' : ` udelezenci--${vrsta}`)

  return (
    <section className={razred} aria-label={naslovUdelezencev(vrsta, faza)}>
      <div className="udelezenci__glava">
        <h2 className="udelezenci__naslov">{naslovUdelezencev(vrsta, faza)}</h2>
        <span className="udelezenci__meta">{metaUdelezencev(vrsta, vrstice)}</span>
      </div>
      <p className="udelezenci__opomba">{opombaUdelezencev(faza, vrstice, imaIzpad)}</p>

      {vrstice.length === 0 ? (
        <p className="udelezenci__prazno">{praznoBesedilo(vrsta)}</p>
      ) : (
        <>
          <div className="udelezenci__filtri">
            <label className="udelezenci__polje">
              <span className="udelezenci__oznaka udelezenci__oznaka--polje">Iskanje</span>
              <input
                type="search"
                className="udelezenci__iskanje"
                value={iskanje}
                onChange={(dogodek) => nastaviIskanje(dogodek.target.value)}
                placeholder={jeTelefon ? 'išči po priimku' : 'po priimku'}
                aria-label="Išči po priimku"
              />
            </label>
            <div className="udelezenci__meniji">
              <MeniIzbire
                oznaka="Klub"
                moznosti={moznostiKlubov}
                izbrano={klub}
                odprt={odprtMeni === 'klub'}
                naPreklop={() => nastaviOdprtMeni((prej) => (prej === 'klub' ? null : 'klub'))}
                naZapri={() => nastaviOdprtMeni(null)}
                naIzbiro={(izbrano) => {
                  nastaviKlub(izbrano)
                  nastaviOdprtMeni(null)
                }}
              />
              <MeniIzbire
                oznaka="Razvrsti"
                moznosti={moznostiRazvrstitev}
                izbrano={veljavnaRazvrstitev}
                odprt={odprtMeni === 'razvrstitev'}
                naPreklop={() =>
                  nastaviOdprtMeni((prej) => (prej === 'razvrstitev' ? null : 'razvrstitev'))
                }
                naZapri={() => nastaviOdprtMeni(null)}
                naIzbiro={(izbrano) => {
                  nastaviRazvrstitev(izbrano)
                  nastaviOdprtMeni(null)
                }}
                poravnava="desno"
              />
            </div>
            <span className="udelezenci__stevec">
              {prikaz.prikazanih} od {vrstice.length}
            </span>
          </div>

          {prikaz.karte.length > 0 && (
            <div className="udelezenci__karte">
              {prikaz.karte.map((skupina, skupinaIndeks) => (
                <div className="udelezenci__karte-skupina" key={skupina.naslov}>
                  <p className="udelezenci__karte-naslov">
                    <span>{skupina.naslov}</span>
                  </p>
                  <div className="udelezenci__karte-mreza">
                    {skupina.vrstice.map((vrstica, indeks) => (
                      <Kartica
                        key={vrstica.id}
                        vrstica={vrstica}
                        faza={faza}
                        prva={skupinaIndeks === 0 && indeks === 0}
                        dejanje={dejanje}
                      />
                    ))}
                  </div>
                </div>
              ))}
            </div>
          )}

          {prikaz.skupine.length > 0 && (
            <div className="udelezenci__skupine">
              {prikaz.skupine.map((skupina) => (
                <div className="udelezenci__skupina" key={skupina.naslov ?? 'vsi'}>
                  {skupina.naslov !== null && (
                    <div className="udelezenci__skupina-glava">
                      <span className="udelezenci__skupina-naslov">{skupina.naslov}</span>
                      <span>{skupina.razpon}</span>
                    </div>
                  )}
                  <div
                    className={
                      'udelezenci__stolpci' + (skupina.stolpci.length > 1 ? ' udelezenci__stolpci--dva' : '')
                    }
                  >
                    {skupina.stolpci.map((stolpec, stolpecIndeks) => (
                      <div className="udelezenci__stolpec" key={stolpecIndeks}>
                        {skupina.naslov === null && (
                          <div className="udelezenci__tabela-glava">
                            <span>#</span>
                            <span>{glavaImena(vrsta)}</span>
                            <span>Rating</span>
                          </div>
                        )}
                        {stolpec.map((vrstica) => (
                          <Vrstica key={vrstica.id} vrstica={vrstica} faza={faza} dejanje={dejanje} />
                        ))}
                      </div>
                    ))}
                  </div>
                </div>
              ))}
            </div>
          )}

          {prikaz.prikazanih === 0 && (
            <p className="udelezenci__prazno">Noben udeleženec ne ustreza iskanju.</p>
          )}
        </>
      )}

      {dodatek && (
        <div className="udelezenci__dodatek">
          <span className="udelezenci__dodatek-naslov">{dodatek.naslov}</span>
          <p className="udelezenci__dodatek-namig">{dodatek.namig}</p>
          <div className="udelezenci__dodatek-imena">
            {dodatek.imena.map((ime, indeks) => (
              <span key={indeks}>
                {ime.besedilo}
                <span className="udelezenci__dodatek-klub"> · {ime.klub}</span>
              </span>
            ))}
          </div>
        </div>
      )}
    </section>
  )
}

function praznoBesedilo(vrsta: VrstaUdelezencev): string {
  if (vrsta === 'dvojice') return 'Ni še sestavljenih parov.'
  return vrsta === 'ekipe' ? 'Ni še prijavljenih ekip.' : 'Ni še prijavljenih igralcev.'
}

/* Značka stanja: med turnirjem »V igri« / »Izpadel« / »Odstopil«, po koncu
   »Končal 3.–4.«; v pripravi je ni. Zelena je za tiste v igri in končna mesta
   1–4, vse drugo je sivo. */
function znacka(vrstica: VrsticaUdelezenca, faza: FazaUdelezencev): { besedilo: string; zelena: boolean } {
  if (faza === 'zakljucen') {
    return vrstica.koncnoMesto === null
      ? { besedilo: '', zelena: false }
      : { besedilo: `Končal ${vrstica.koncnaOznaka}`, zelena: vrstica.koncnoMesto <= 4 }
  }
  if (vrstica.stanje === 'v_igri') return { besedilo: 'V igri', zelena: true }
  if (vrstica.stanje === 'izpadel') return { besedilo: 'Izpadel', zelena: false }
  if (vrstica.stanje === 'odstopil') return { besedilo: 'Odstopil', zelena: false }
  return { besedilo: '', zelena: false }
}

function jeZbledel(vrstica: VrsticaUdelezenca, faza: FazaUdelezencev): boolean {
  return faza === 'v_teku' && (vrstica.stanje === 'izpadel' || vrstica.stanje === 'odstopil')
}

function Imena({ imena, razred }: { imena: DelImena[]; razred: string }) {
  return (
    <>
      {imena.map((ime, indeks) => (
        <span className={razred} key={indeks}>
          {ime.ime !== '' && <span className="udelezenci__ime">{ime.ime} </span>}
          <span className="udelezenci__priimek">{ime.priimek}</span>
        </span>
      ))}
    </>
  )
}

function Palica({ odstotek }: { odstotek: number }) {
  return (
    <span className="udelezenci__palica" aria-hidden="true">
      <span
        className="udelezenci__palica-polnilo"
        style={{ '--palica': `${odstotek}%` } as CSSProperties}
      />
    </span>
  )
}

function Kartica({
  vrstica,
  faza,
  prva,
  dejanje,
}: {
  vrstica: VrsticaUdelezenca
  faza: FazaUdelezencev
  prva: boolean
  dejanje?: (vrstica: VrsticaUdelezenca) => ReactNode
}) {
  const { besedilo, zelena } = znacka(vrstica, faza)
  return (
    <div
      className={
        'udelezenci__karta'
        + (prva ? ' udelezenci__karta--prva' : '')
        + (jeZbledel(vrstica, faza) ? ' udelezenci__karta--zbledela' : '')
      }
    >
      <div className="udelezenci__karta-vrh">
        <span className="udelezenci__karta-oznaka">Nosilec {vrstica.mesto}</span>
        <span className={'udelezenci__znacka' + (zelena ? ' udelezenci__znacka--zelena' : '')}>{besedilo}</span>
      </div>
      <div className="udelezenci__karta-ime">
        <Imena imena={vrstica.imena} razred="udelezenci__karta-vrstica" />
      </div>
      <div className="udelezenci__karta-klub">{vrstica.klub}</div>
      {vrstica.podnapis !== '' && <div className="udelezenci__karta-podnapis">{vrstica.podnapis}</div>}
      <div className="udelezenci__karta-rating">
        <span className="udelezenci__karta-stevilka">{vrstica.rating}</span>
        <Palica odstotek={vrstica.palica} />
      </div>
      {dejanje?.(vrstica)}
    </div>
  )
}

function Vrstica({
  vrstica,
  faza,
  dejanje,
}: {
  vrstica: VrsticaUdelezenca
  faza: FazaUdelezencev
  dejanje?: (vrstica: VrsticaUdelezenca) => ReactNode
}) {
  const { besedilo, zelena } = znacka(vrstica, faza)
  return (
    <div className={'udelezenci__vrstica' + (jeZbledel(vrstica, faza) ? ' udelezenci__vrstica--zbledela' : '')}>
      <span className="udelezenci__vrstica-mesto">{vrstica.mesto}</span>
      <span className="udelezenci__vrstica-ime">
        <Imena imena={vrstica.imena} razred="udelezenci__vrstica-vrstica" />
        <span className="udelezenci__vrstica-klub">{vrstica.klub}</span>
        {vrstica.podnapis !== '' && <span className="udelezenci__vrstica-podnapis">{vrstica.podnapis}</span>}
        {dejanje?.(vrstica)}
      </span>
      <span className="udelezenci__vrstica-desno">
        <span className="udelezenci__vrstica-podatki">
          {/* Značka ostane element tudi, kadar je prazna: razmik pod njo je del
              višine vrstice. */}
          <span
            className={'udelezenci__znacka udelezenci__znacka--vrstica' + (zelena ? ' udelezenci__znacka--zelena' : '')}
          >
            {besedilo}
          </span>
          <span className="udelezenci__vrstica-rating">{vrstica.rating}</span>
        </span>
        <Palica odstotek={vrstica.palica} />
      </span>
    </div>
  )
}
