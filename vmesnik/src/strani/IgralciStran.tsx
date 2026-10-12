/* Register igralcev (»Kartoteka«): pregled s trenutnim ratingom, filtri,
   razvrščanje, dodajanje, urejanje, arhiviranje, postavitev ratinga in čistka
   duplikatov. Vizualni jezik je »športni zapisnik na papirju« (DESIGN.md);
   postavitev je po design_handoff_igralci_prenova (smer 1a + 1c).

   Admin dobi podroben izpis (letnik, licenca, dejanja v meniju »⋯«),
   organizator javnega: ime, klub, spol in rating, dodaja pa lahko le novega
   igralca. Poslovna logika (poizvedbe, mutacije, validacije, opozorilo o
   podobnih) je ostala; spremenila se je predstavitev. */
import {
  useEffect,
  useMemo,
  useRef,
  useState,
  type ChangeEvent,
  type FormEvent,
} from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'

import { igralciApi, klubiApi, krajiApi } from '../api/zahteve'
import type {
  DvojnikIgralcaDto,
  IgralecDto,
  IgralecPodrobenDto,
  IgralecVnos,
  IgralnaRoka,
  PodobenIgralecDto,
  PodobenIgralecPodrobenDto,
  PodobniVnos,
  Spol,
  StarostniPas,
  UjemanjeImena,
} from '../api/tipi'
import {
  OZNAKE_IGRALNA_ROKA,
  OZNAKE_PASU_KRATKO,
  OZNAKE_SPOL,
  VRSTNI_RED_STAROSTNIH_PASOV,
} from '../api/tipi'
import { useAvtentikacija } from '../avtentikacija/AvtentikacijaKontekst'
import { IzbirnikKluba } from '../komponente/IzbirnikKluba'
import { IzbirnikKraja } from '../komponente/IzbirnikKraja'
import { ModalnoOkno } from '../komponente/ModalnoOkno'
import { StevilskoPolje } from '../komponente/StevilskoPolje'
import { SporociloNapake } from '../komponente/SporociloNapake'
import { letnica, oblikujDatum, sklonTekem, zVelikoZacetnico } from '../pomozno/oblikovanje'
import { useTelefon } from '../pomozno/telefon'

/* Do toliko odigranih tekem je rating še provizoričen (ujema se s strežniškim
   pragom dinamičnega K, TurnirkoRatingStoritev.PRAG_PROVIZORICNI). */
const PROVIZORICNO_DO = 10

/* Vrstic na stran. Seznam že prihaja v celoti, zato se deli v odjemalcu. */
const VRSTIC_NA_STRAN = 20

/* Zadnjih toliko vrstic na strani odpira meni dejanj navzgor, sicer bi padel
   pod konec seznama. */
const VRSTIC_Z_MENIJEM_NAVZGOR = 4

type Stolpec = 'priimek' | 'klub' | 'letnik' | 'rating'
type Pogled = 'seznam' | 'cistka'

const OZNAKE_UJEMANJA: Record<UjemanjeImena, string> = {
  ISTO: 'Isto ime in priimek',
  OBRNJENO: 'Ime in priimek zamenjana',
  PODOBNO: 'Podobno ime',
}

function primerjaj(a: string, b: string): number {
  return a.localeCompare(b, 'sl')
}

/* Ime se bere pred priimkom (»Ana Novak«, CLAUDE.md), seznam pa tece po
   priimku: urejevalni ključ je zato priimek in ime. */
function polnoIme(igralec: { ime: string; priimek: string }): string {
  return `${igralec.ime} ${igralec.priimek}`
}

function jeProvizoricen(igralec: IgralecDto): boolean {
  return igralec.rating != null && igralec.steviloTekem < PROVIZORICNO_DO
}

export function IgralciStran() {
  const odjemalec = useQueryClient()
  // organizator sme le dodati novega igralca; urejanje, arhiviranje in
  // postavitev ratinga ostanejo administratorju (streznik je zadnja obramba)
  const { jeAdmin } = useAvtentikacija()
  const telefon = useTelefon()
  /* Osebne podatke (letnik, licenca, kontakti) strežnik da samo adminu, zato
     organizator dobi javni izpis. Ločen ključ, da se odgovora ne mešata v
     predpomnilniku; invalidacija po ['igralci'] zajame oba. */
  const igralci = useQuery<IgralecDto[]>({
    queryKey: ['igralci', jeAdmin ? 'podrobno' : 'javno'],
    queryFn: jeAdmin ? igralciApi.seznamPodrobno : igralciApi.seznam,
  })
  /* Pari podobnih imen (čistka): imajo datume rojstva, zato samo admin. Če
     poizvedba odpove, seznam še vedno dela - le oznak »duplikat?« ni. */
  const duplikati = useQuery<DvojnikIgralcaDto[]>({
    queryKey: ['igralci', 'duplikati'],
    queryFn: igralciApi.duplikati,
    enabled: jeAdmin,
  })

  const [pogled, nastaviPogled] = useState<Pogled>('seznam')
  const [iskanje, nastaviIskanje] = useState('')
  const [spol, nastaviSpol] = useState<Spol | ''>('')
  const [klub, nastaviKlub] = useState('')
  const [pas, nastaviPas] = useState<StarostniPas | ''>('')
  const [brezRatinga, nastaviBrezRatinga] = useState(false)
  const [provizorici, nastaviProvizorice] = useState(false)
  const [samoDuplikati, nastaviSamoDuplikate] = useState(false)
  const [stolpec, nastaviStolpec] = useState<Stolpec>('priimek')
  const [smer, nastaviSmer] = useState<1 | -1>(1)
  const [stranZeljena, nastaviStran] = useState(0)
  const [filtriOdprti, nastaviFiltreOdprte] = useState(false)
  const [odprtMeni, nastaviOdprtMeni] = useState<number | null>(null)
  /* null = obrazec zaprt; 'nov' = nov igralec; sicer igralec za urejanje
     (urejati sme le admin, ki ima podroben zapis). */
  const [urejanje, nastaviUrejanje] = useState<'nov' | IgralecPodrobenDto | null>(null)
  /* Igralec, za katerega cakamo potrditev arhiviranja. */
  const [arhiviranec, nastaviArhiviranca] = useState<IgralecDto | null>(null)
  /* Igralec, ki mu postavljamo začetni rating (le pred prvo tekmo). */
  const [postavljanec, nastaviPostavljanca] = useState<IgralecDto | null>(null)
  /* Igralec, ki mu vpisujemo rating z zunanje lestvice (dovoljeno tudi po
     odigranih tekmah — glej ZunanjaUvrstitevOkno). */
  const [zunanjiGost, nastaviZunanjegaGosta] = useState<IgralecDto | null>(null)
  /* Par, ki ga admin združuje: obdržani in odvečni igralec. */
  const [zdruzevanje, nastaviZdruzevanje] = useState<{
    obdrzan: IgralecPodrobenDto
    odvec: IgralecPodrobenDto
  } | null>(null)

  const arhiviranje = useMutation({
    mutationFn: (id: number) => igralciApi.arhiviraj(id),
    onSuccess: () => odjemalec.invalidateQueries({ queryKey: ['igralci'] }),
  })
  const niDuplikat = useMutation({
    mutationFn: (par: DvojnikIgralcaDto) => igralciApi.niDuplikat(par.levi.id, par.desni.id),
    onSuccess: () => odjemalec.invalidateQueries({ queryKey: ['igralci'] }),
  })
  const zdruzitev = useMutation({
    mutationFn: (v: { obdrzan: number; odvec: number }) =>
      igralciApi.zdruzi(v.obdrzan, v.odvec),
    onSuccess: () => {
      nastaviZdruzevanje(null)
      return odjemalec.invalidateQueries({ queryKey: ['igralci'] })
    },
  })

  /* Meni dejanj se zapre s klikom drugam in z Escape. */
  const gumbMenija = useRef<HTMLButtonElement | null>(null)
  useEffect(() => {
    if (odprtMeni === null) return
    function obKliku(dogodek: MouseEvent) {
      const tarca = dogodek.target as Element | null
      if (tarca?.closest('.kartoteka__meni, .kartoteka__meni-gumb')) return
      nastaviOdprtMeni(null)
    }
    function obTipki(dogodek: KeyboardEvent) {
      if (dogodek.key === 'Escape') {
        nastaviOdprtMeni(null)
        gumbMenija.current?.focus()
      }
    }
    document.addEventListener('mousedown', obKliku)
    document.addEventListener('keydown', obTipki)
    return () => {
      document.removeEventListener('mousedown', obKliku)
      document.removeEventListener('keydown', obTipki)
    }
  }, [odprtMeni])

  const vsi = useMemo(() => igralci.data ?? [], [igralci.data])
  const pari = duplikati.data ?? []
  const idjiDuplikatov = useMemo(() => {
    const idji = new Set<number>()
    for (const par of duplikati.data ?? []) {
      idji.add(par.levi.id)
      idji.add(par.desni.id)
    }
    return idji
  }, [duplikati.data])

  const stevila = useMemo(() => {
    const klubi = new Set<number>()
    let zRatingom = 0
    let brezRatingaSt = 0
    let provizoricnih = 0
    for (const i of vsi) {
      if (i.klub) klubi.add(i.klub.id)
      if (i.rating == null) brezRatingaSt++
      else zRatingom++
      if (jeProvizoricen(i)) provizoricnih++
    }
    return { klubov: klubi.size, zRatingom, brezRatinga: brezRatingaSt, provizoricnih }
  }, [vsi])

  /* Klubi, ki jih igralci imajo, po slovenski abecedi. */
  const klubiSeznama = useMemo(() => {
    const poId = new Map<number, string>()
    for (const i of vsi) if (i.klub) poId.set(i.klub.id, i.klub.ime)
    return [...poId.entries()].sort((a, b) => primerjaj(a[1], b[1]))
  }, [vsi])

  const filtrirani = useMemo(() => {
    const iskano = iskanje.trim().toLowerCase()
    const lista = vsi.filter((igralec) => {
      if (
        iskano
        && !`${igralec.ime} ${igralec.priimek} ${igralec.klub?.ime ?? ''}`
          .toLowerCase()
          .includes(iskano)
      ) {
        return false
      }
      if (spol && igralec.spol !== spol) return false
      if (klub === '-' ? igralec.klub : klub && String(igralec.klub?.id) !== klub) return false
      if (pas && igralec.starostniPas !== pas) return false
      if (brezRatinga && igralec.rating != null) return false
      if (provizorici && !jeProvizoricen(igralec)) return false
      if (samoDuplikati && !idjiDuplikatov.has(igralec.id)) return false
      return true
    })
    const poLetniku = (i: IgralecDto) =>
      Number((i as IgralecPodrobenDto).datumRojstva?.slice(0, 4)) || 0
    lista.sort((x, y) => {
      let c: number
      if (stolpec === 'rating') c = (x.rating ?? -1) - (y.rating ?? -1)
      else if (stolpec === 'letnik') c = poLetniku(x) - poLetniku(y)
      else if (stolpec === 'klub') {
        // igralci brez kluba na koncu (pri naraščajočem)
        c = x.klub && y.klub ? primerjaj(x.klub.ime, y.klub.ime) : Number(!x.klub) - Number(!y.klub)
      } else c = primerjaj(`${x.priimek} ${x.ime}`, `${y.priimek} ${y.ime}`)
      return (c || primerjaj(`${x.priimek}${x.ime}`, `${y.priimek}${y.ime}`)) * smer
    })
    return lista
  }, [
    vsi, iskanje, spol, klub, pas, brezRatinga, provizorici, samoDuplikati,
    idjiDuplikatov, stolpec, smer,
  ])

  const strani = Math.max(1, Math.ceil(filtrirani.length / VRSTIC_NA_STRAN))
  const stran = Math.min(stranZeljena, strani - 1)
  const naStrani = filtrirani.slice(stran * VRSTIC_NA_STRAN, (stran + 1) * VRSTIC_NA_STRAN)

  const jeFilter = Boolean(
    iskanje || spol || klub || pas || brezRatinga || provizorici || samoDuplikati,
  )

  /* Vsak filter ponastavi na prvo stran. */
  function filter<T>(nastavi: (vrednost: T) => void) {
    return (vrednost: T) => {
      nastavi(vrednost)
      nastaviStran(0)
      nastaviOdprtMeni(null)
    }
  }
  const spremeniIskanje = filter(nastaviIskanje)
  const spremeniSpol = filter(nastaviSpol)
  const spremeniKlub = filter(nastaviKlub)
  const spremeniPas = filter(nastaviPas)

  function pocistiFiltre() {
    nastaviIskanje('')
    nastaviSpol('')
    nastaviKlub('')
    nastaviPas('')
    nastaviBrezRatinga(false)
    nastaviProvizorice(false)
    nastaviSamoDuplikate(false)
    nastaviStran(0)
  }

  /* Klik na aktivni stolpec obrne smer; klik na novega: naraščajoče, pri
     ratingu padajoče. */
  function razvrsti(novi: Stolpec) {
    if (novi === stolpec) nastaviSmer((s) => (s === 1 ? -1 : 1))
    else {
      nastaviStolpec(novi)
      nastaviSmer(novi === 'rating' ? -1 : 1)
    }
    nastaviStran(0)
    nastaviOdprtMeni(null)
  }

  function idiNaStran(naslednja: number) {
    nastaviStran(Math.max(0, Math.min(strani - 1, naslednja)))
    nastaviOdprtMeni(null)
  }

  /* Dejanje iz menija: fokus se vrne na »⋯«, preden se okno odpre, da ga
     ModalnoOkno ob zaprtju najde (postavke menija v tistem hipu izginejo). */
  function izMenija(dejanje: () => void) {
    gumbMenija.current?.focus()
    nastaviOdprtMeni(null)
    dejanje()
  }

  const odpriCistko = () => {
    nastaviPogled('cistka')
    nastaviOdprtMeni(null)
  }

  const kolofon = igralci.data && (
    <div className="kartoteka__glava">
      <div>
        <div className="kartoteka__kolofon">
          <div className="kartoteka__kolofon-vrstica">
            <span>Igralcev</span>
            <span className="kartoteka__kolofon-vrednost">{vsi.length}</span>
          </div>
          <div className="kartoteka__kolofon-vrstica">
            <span>Klubov</span>
            <span className="kartoteka__kolofon-vrednost">{stevila.klubov}</span>
          </div>
          <div className="kartoteka__kolofon-vrstica">
            <span>Z ratingom</span>
            <span className="kartoteka__kolofon-vrednost">{stevila.zRatingom}</span>
          </div>
          {jeAdmin && (
            <>
              <div className="kartoteka__kolofon-vrstica">
                <span>Provizorični (★ pod {PROVIZORICNO_DO} tekem)</span>
                <span className="kartoteka__kolofon-vrednost">{stevila.provizoricnih}</span>
              </div>
              <button
                type="button"
                className="kartoteka__kolofon-vrstica kartoteka__kolofon-vrstica--povezava"
                onClick={odpriCistko}
              >
                <span>Možnih duplikatov</span>
                <span className="kartoteka__kolofon-vrednost">
                  {duplikati.data ? pari.length : '—'} · pregled →
                </span>
              </button>
            </>
          )}
        </div>
        <div className="kartoteka__dejanja">
          <button className="gumb gumb--glavni" onClick={() => nastaviUrejanje('nov')}>
            + Nov igralec
          </button>
          {!jeAdmin && (
            <p className="kartoteka__opomba">
              Javni izpis: brez letnika, licence in dejanj. Dodaš lahko samo novega igralca.
            </p>
          )}
        </div>
      </div>
    </div>
  )

  return (
    <section className="kartoteka">
      {/* Glave strani (nadnaslov, naslov) ni: kje smo, pove navigacija
          (odločitev lastnika, 11. 10. 2026). Ostane kolofon s števci. */}
      {!telefon && kolofon}

      <div className="kartoteka__sekcija">
        {pogled === 'cistka' && jeAdmin ? (
          <CistkaDuplikatov
            pari={pari}
            nalaganje={duplikati.isPending}
            napaka={duplikati.error}
            napakaMutacij={niDuplikat.error ?? zdruzitev.error}
            zdruzujem={zdruzitev.isPending}
            onNazaj={() => nastaviPogled('seznam')}
            onNiDuplikat={(par) => niDuplikat.mutate(par)}
            onZdruzi={(obdrzan, odvec) => {
              zdruzitev.reset()
              nastaviZdruzevanje({ obdrzan, odvec })
            }}
          />
        ) : (
          <>
            {telefon ? (
              <TelefonskaGlava
                igralci={vsi}
                stevila={stevila}
                jeAdmin={jeAdmin}
                nalozeno={Boolean(igralci.data)}
                parov={duplikati.data ? pari.length : null}
                iskanje={iskanje}
                brezRatinga={brezRatinga}
                provizorici={provizorici}
                samoDuplikati={samoDuplikati}
                filtriOdprti={filtriOdprti}
                spol={spol}
                klub={klub}
                pas={pas}
                klubi={klubiSeznama}
                jeFilter={jeFilter}
                onIskanje={spremeniIskanje}
                onVsi={pocistiFiltre}
                onBrezRatinga={() => {
                  nastaviBrezRatinga((v) => !v)
                  nastaviStran(0)
                }}
                onProvizorici={() => {
                  nastaviProvizorice((v) => !v)
                  nastaviStran(0)
                }}
                onDuplikati={() => {
                  nastaviSamoDuplikate((v) => !v)
                  nastaviStran(0)
                }}
                onFiltri={() => nastaviFiltreOdprte((v) => !v)}
                onSpol={spremeniSpol}
                onKlub={spremeniKlub}
                onPas={spremeniPas}
                onCistka={odpriCistko}
              />
            ) : (
              <>
                <div className="kartoteka__naslovna">
                  <h2 className="kartoteka__naslov">Vsi igralci</h2>
                  {igralci.data && (
                    <span className="kartoteka__meta">
                      Prikazanih {filtrirani.length ? stran * VRSTIC_NA_STRAN + 1 : 0}–
                      {Math.min(filtrirani.length, (stran + 1) * VRSTIC_NA_STRAN)} od{' '}
                      {filtrirani.length}
                    </span>
                  )}
                </div>

                {igralci.data && (
                  <>
                    <div className="kartoteka__filtri">
                      <input
                        className="kartoteka__iskanje"
                        type="search"
                        aria-label="Išči po imenu ali klubu"
                        placeholder="Išči po imenu ali klubu …"
                        value={iskanje}
                        onChange={(dogodek) => spremeniIskanje(dogodek.target.value)}
                      />
                      <div className="kartoteka__izbirnik" role="group" aria-label="Spol">
                        {(
                          [
                            ['', 'Vsi'],
                            ['MOSKI', 'Moški'],
                            ['ZENSKI', 'Ženski'],
                          ] as const
                        ).map(([vrednost, oznaka]) => (
                          <button
                            key={oznaka}
                            type="button"
                            aria-pressed={spol === vrednost}
                            className={
                              'kartoteka__segment'
                              + (spol === vrednost ? ' kartoteka__segment--aktiven' : '')
                            }
                            // ponovni klik na aktivnega pomeni »vsi«
                            onClick={() => spremeniSpol(spol === vrednost ? '' : vrednost)}
                          >
                            {oznaka}
                          </button>
                        ))}
                      </div>
                      <select
                        className="kartoteka__izbira"
                        aria-label="Klub"
                        value={klub}
                        onChange={(dogodek) => spremeniKlub(dogodek.target.value)}
                      >
                        <option value="">Vsi klubi</option>
                        <option value="-">Brez kluba</option>
                        {klubiSeznama.map(([id, ime]) => (
                          <option key={id} value={String(id)}>
                            {ime}
                          </option>
                        ))}
                      </select>
                      <select
                        className="kartoteka__izbira"
                        aria-label="Pas"
                        value={pas}
                        onChange={(dogodek) => spremeniPas(dogodek.target.value as StarostniPas | '')}
                      >
                        <option value="">Vsi pasovi</option>
                        {VRSTNI_RED_STAROSTNIH_PASOV.map((p) => (
                          <option key={p} value={p}>
                            {OZNAKE_PASU_KRATKO[p]}
                          </option>
                        ))}
                      </select>
                    </div>

                    <div className="kartoteka__cipi">
                      <Cip
                        aktiven={brezRatinga}
                        oznaka={`BREZ RATINGA · ${stevila.brezRatinga}`}
                        onKlik={() => {
                          nastaviBrezRatinga((v) => !v)
                          nastaviStran(0)
                        }}
                      />
                      <Cip
                        aktiven={provizorici}
                        oznaka={`★ PROVIZORIČNI · ${stevila.provizoricnih}`}
                        onKlik={() => {
                          nastaviProvizorice((v) => !v)
                          nastaviStran(0)
                        }}
                      />
                      {jeAdmin && (
                        <Cip
                          aktiven={samoDuplikati}
                          oznaka={`MOŽNI DUPLIKATI · ${idjiDuplikatov.size}`}
                          onKlik={() => {
                            nastaviSamoDuplikate((v) => !v)
                            nastaviStran(0)
                          }}
                        />
                      )}
                      {jeFilter && (
                        <button type="button" className="kartoteka__pocisti" onClick={pocistiFiltre}>
                          POČISTI FILTRE
                        </button>
                      )}
                      <span className="kartoteka__legenda">
                        {`★ = PROVIZORIČEN RATING (MANJ KOT ${PROVIZORICNO_DO} TEKEM)`}
                      </span>
                    </div>
                  </>
                )}
              </>
            )}

            <SporociloNapake napaka={igralci.error} />
            <SporociloNapake napaka={arhiviranje.error} />
            {igralci.isPending && <p className="obvestilo">Nalaganje …</p>}

            {igralci.data && igralci.data.length === 0 && (
              <p className="obvestilo">
                Ni še nobenega igralca. Dodaj prvega z gumbom »+ Nov igralec«.
              </p>
            )}

            {vsi.length > 0 && (
              <>
                {telefon ? (
                  <SeznamTelefon
                    vrstice={naStrani}
                    jeAdmin={jeAdmin}
                    idjiDuplikatov={idjiDuplikatov}
                    odprtMeni={odprtMeni}
                    gumbMenija={gumbMenija}
                    onMeni={nastaviOdprtMeni}
                    izMenija={izMenija}
                    onUredi={(i) => nastaviUrejanje(i)}
                    onZacetni={nastaviPostavljanca}
                    onZunanja={nastaviZunanjegaGosta}
                    onArhiviraj={nastaviArhiviranca}
                  />
                ) : (
                  <SeznamNamizje
                    vrstice={naStrani}
                    jeAdmin={jeAdmin}
                    idjiDuplikatov={idjiDuplikatov}
                    stolpec={stolpec}
                    smer={smer}
                    razvrscenoPoPriimku={stolpec === 'priimek'}
                    odprtMeni={odprtMeni}
                    gumbMenija={gumbMenija}
                    onRazvrsti={razvrsti}
                    onMeni={nastaviOdprtMeni}
                    izMenija={izMenija}
                    onUredi={(i) => nastaviUrejanje(i)}
                    onZacetni={nastaviPostavljanca}
                    onZunanja={nastaviZunanjegaGosta}
                    onArhiviraj={nastaviArhiviranca}
                  />
                )}

                {filtrirani.length === 0 && (
                  <p className="kartoteka__prazno">Iskanju ne ustreza noben igralec.</p>
                )}

                {filtrirani.length > 0 && (
                  <div className="kartoteka__strani">
                    <span>
                      Stran {stran + 1} od {strani}
                    </span>
                    <div className="kartoteka__strani-gumbi">
                      <button
                        type="button"
                        className="kartoteka__stran-gumb"
                        disabled={stran === 0}
                        onClick={() => idiNaStran(stran - 1)}
                      >
                        ← Prejšnja
                      </button>
                      <button
                        type="button"
                        className="kartoteka__stran-gumb"
                        disabled={stran >= strani - 1}
                        onClick={() => idiNaStran(stran + 1)}
                      >
                        Naslednja →
                      </button>
                    </div>
                  </div>
                )}
              </>
            )}

            {telefon && igralci.data && (
              <div className="kartoteka__mobi-cta">
                <button className="gumb gumb--glavni" onClick={() => nastaviUrejanje('nov')}>
                  + Nov igralec
                </button>
              </div>
            )}
          </>
        )}
      </div>

      {arhiviranec && (
        <ArhivirajOkno
          igralec={arhiviranec}
          onPotrdi={() => arhiviranje.mutate(arhiviranec.id)}
          onZapri={() => nastaviArhiviranca(null)}
        />
      )}

      {urejanje && (
        <IgralecOkno
          igralec={urejanje === 'nov' ? null : urejanje}
          onZapri={() => nastaviUrejanje(null)}
          onShranjeno={() => odjemalec.invalidateQueries({ queryKey: ['igralci'] })}
        />
      )}

      {postavljanec && (
        <ZacetniRatingOkno
          igralec={postavljanec}
          onZapri={() => nastaviPostavljanca(null)}
          onShranjeno={() => odjemalec.invalidateQueries({ queryKey: ['igralci'] })}
        />
      )}

      {zunanjiGost && (
        <ZunanjaUvrstitevOkno
          igralec={zunanjiGost}
          onZapri={() => nastaviZunanjegaGosta(null)}
          onShranjeno={() => odjemalec.invalidateQueries({ queryKey: ['igralci'] })}
        />
      )}

      {zdruzevanje && (
        <ZdruzitevOkno
          obdrzan={zdruzevanje.obdrzan}
          odvec={zdruzevanje.odvec}
          pending={zdruzitev.isPending}
          napaka={zdruzitev.error}
          onPotrdi={() =>
            zdruzitev.mutate({ obdrzan: zdruzevanje.obdrzan.id, odvec: zdruzevanje.odvec.id })
          }
          onZapri={() => {
            if (zdruzitev.isPending) return
            zdruzitev.reset()
            nastaviZdruzevanje(null)
          }}
        />
      )}
    </section>
  )
}

/* Filter-čip (preklop): kot segment, a samostojen. */
function Cip({
  aktiven,
  oznaka,
  onKlik,
}: {
  aktiven: boolean
  oznaka: string
  onKlik: () => void
}) {
  return (
    <button
      type="button"
      aria-pressed={aktiven}
      className={'kartoteka__cip' + (aktiven ? ' kartoteka__cip--aktiven' : '')}
      onClick={onKlik}
    >
      {oznaka}
    </button>
  )
}

interface DejanjaSeznama {
  jeAdmin: boolean
  idjiDuplikatov: Set<number>
  odprtMeni: number | null
  gumbMenija: React.MutableRefObject<HTMLButtonElement | null>
  onMeni: (id: number | null) => void
  izMenija: (dejanje: () => void) => void
  onUredi: (igralec: IgralecPodrobenDto) => void
  onZacetni: (igralec: IgralecDto) => void
  onZunanja: (igralec: IgralecDto) => void
  onArhiviraj: (igralec: IgralecDto) => void
}

/* Gumb »⋯« in njegov meni (Uredi, Postavi/Popravi začetni rating, Zunanja
   uvrstitev, Arhiviraj). Začetni rating je mogoč samo pred prvo tekmo,
   zunanja uvrstitev vedno — prav zaradi redkih gostov obstaja. */
function MeniIgralca({
  igralec,
  navzgor,
  d,
}: {
  igralec: IgralecDto
  navzgor: boolean
  d: DejanjaSeznama
}) {
  const odprt = d.odprtMeni === igralec.id
  return (
    <>
      <button
        type="button"
        className="kartoteka__meni-gumb"
        aria-label="Dejanja"
        aria-haspopup="menu"
        aria-expanded={odprt}
        onClick={(dogodek) => {
          d.gumbMenija.current = dogodek.currentTarget
          d.onMeni(odprt ? null : igralec.id)
        }}
      >
        ⋯
      </button>
      {odprt && (
        <div
          className={'kartoteka__meni' + (navzgor ? ' kartoteka__meni--gor' : '')}
          role="menu"
        >
          <button
            type="button"
            role="menuitem"
            className="kartoteka__meni-postavka"
            onClick={() => d.izMenija(() => d.onUredi(igralec as IgralecPodrobenDto))}
          >
            Uredi
          </button>
          {igralec.steviloTekem === 0 && (
            <button
              type="button"
              role="menuitem"
              className="kartoteka__meni-postavka"
              onClick={() => d.izMenija(() => d.onZacetni(igralec))}
            >
              {igralec.rating == null ? 'Postavi začetni rating' : 'Popravi začetni rating'}
            </button>
          )}
          <button
            type="button"
            role="menuitem"
            className="kartoteka__meni-postavka"
            onClick={() => d.izMenija(() => d.onZunanja(igralec))}
          >
            Zunanja uvrstitev
          </button>
          <button
            type="button"
            role="menuitem"
            className="kartoteka__meni-postavka kartoteka__meni-postavka--nevarno"
            onClick={() => d.izMenija(() => d.onArhiviraj(igralec))}
          >
            Arhiviraj
          </button>
        </div>
      )}
    </>
  )
}

/* Rating v vrstici: značka »★ 2/10« (provizoričen) in število; brez ratinga »—«. */
function RatingVrstice({ igralec }: { igralec: IgralecDto }) {
  const provizoricen = jeProvizoricen(igralec)
  return (
    <span className="kartoteka__rating">
      {provizoricen && (
        <span className="kartoteka__znacka kartoteka__znacka--prov" title="Provizoričen rating">
          {`★ ${igralec.steviloTekem}/${PROVIZORICNO_DO}`}
        </span>
      )}
      <span
        className={
          'kartoteka__stevilo'
          + (igralec.rating == null
            ? ' kartoteka__stevilo--brez'
            : provizoricen
              ? ' kartoteka__stevilo--prov'
              : '')
        }
      >
        {igralec.rating ?? '—'}
      </span>
    </span>
  )
}

function ImeVrstice({
  igralec,
  rekreativec,
  duplikat,
}: {
  igralec: IgralecDto
  rekreativec: boolean
  duplikat: boolean
}) {
  return (
    <div className="kartoteka__igralec">
      <span className="kartoteka__ime">{igralec.ime}</span>
      <span className="kartoteka__priimek">{igralec.priimek}</span>
      {rekreativec && (
        <span
          className="kartoteka__znacka kartoteka__znacka--rekr"
          title="Rekreativec — začetni rating 800"
        >
          Rekr.
        </span>
      )}
      {duplikat && (
        <span className="kartoteka__znacka kartoteka__znacka--dup">Duplikat?</span>
      )}
    </div>
  )
}

function SeznamNamizje({
  vrstice,
  stolpec,
  smer,
  razvrscenoPoPriimku,
  onRazvrsti,
  ...d
}: DejanjaSeznama & {
  vrstice: IgralecDto[]
  stolpec: Stolpec
  smer: 1 | -1
  razvrscenoPoPriimku: boolean
  onRazvrsti: (stolpec: Stolpec) => void
}) {
  const { jeAdmin } = d
  const stolpci = jeAdmin ? 'kartoteka__stolpci--admin' : 'kartoteka__stolpci--javno'
  /* Stolpec »Igralec« je v prototipu vedno v barvi črnila (glavni ključ). */
  const glava = (kdo: Stolpec, oznaka: string, desno = false, vedno = false) => (
    <button
      type="button"
      className={
        'kartoteka__razvrsti'
        + (stolpec === kdo || vedno ? ' kartoteka__razvrsti--aktiven' : '')
        + (desno ? ' kartoteka__razvrsti--desno' : '')
      }
      onClick={() => onRazvrsti(kdo)}
    >
      {oznaka}
      {stolpec === kdo && (smer === 1 ? ' ↑' : ' ↓')}
    </button>
  )
  let prejsnjaCrka = ''

  return (
    <div>
      <div className={'kartoteka__glavastolpcev ' + stolpci}>
        {glava('priimek', 'Igralec', false, true)}
        {glava('klub', 'Klub')}
        <span>Spol</span>
        {jeAdmin && glava('letnik', 'Letnik · pas')}
        {jeAdmin && <span className="kartoteka__lic">Licenca NTZS</span>}
        {glava('rating', 'Rating', true)}
        {jeAdmin && <span />}
      </div>

      {vrstice.map((igralec, indeks) => {
        /* Adminu strežnik vrne podroben zapis (glej poizvedbo), organizatorju
           javnega. Pogojujemo na tej vrednosti in ne na jeAdmin, da so
           stolpci z osebnimi podatki in tip, ki jih nosi, ena in ista
           odločitev. */
        const podroben = jeAdmin ? (igralec as IgralecPodrobenDto) : null
        const crka = igralec.priimek.charAt(0).toLocaleUpperCase('sl')
        const novaCrka = razvrscenoPoPriimku && crka !== prejsnjaCrka
        prejsnjaCrka = crka
        return (
          <div key={igralec.id}>
            {novaCrka && <div className="kartoteka__crka">{crka}</div>}
            <div className={'kartoteka__vrstica ' + stolpci}>
              <ImeVrstice
                igralec={igralec}
                rekreativec={Boolean(podroben?.rekreativniVstop)}
                duplikat={jeAdmin && d.idjiDuplikatov.has(igralec.id)}
              />
              <span
                className={'kartoteka__klub' + (igralec.klub ? '' : ' kartoteka__klub--brez')}
              >
                {igralec.klub?.ime ?? 'brez kluba'}
              </span>
              <span className="kartoteka__spol">{igralec.spol === 'MOSKI' ? 'M' : 'Ž'}</span>
              {podroben && (
                <span className="kartoteka__mono">
                  {letnica(podroben.datumRojstva)
                    + (igralec.starostniPas ? ` · ${OZNAKE_PASU_KRATKO[igralec.starostniPas]}` : '')}
                </span>
              )}
              {podroben && (
                <span className="kartoteka__mono kartoteka__lic">
                  {podroben.ntzsLicenca ?? '—'}
                </span>
              )}
              <RatingVrstice igralec={igralec} />
              {podroben && (
                <div>
                  <MeniIgralca
                    igralec={igralec}
                    navzgor={indeks >= vrstice.length - VRSTIC_Z_MENIJEM_NAVZGOR}
                    d={d}
                  />
                </div>
              )}
            </div>
          </div>
        )
      })}
    </div>
  )
}

/* Telefon: zgornji del (povzetek, iskanje, čipi, filtri) pred seznamom. */
function TelefonskaGlava({
  stevila,
  jeAdmin,
  nalozeno,
  parov,
  iskanje,
  brezRatinga,
  provizorici,
  samoDuplikati,
  filtriOdprti,
  spol,
  klub,
  pas,
  klubi,
  jeFilter,
  igralci,
  onIskanje,
  onVsi,
  onBrezRatinga,
  onProvizorici,
  onDuplikati,
  onFiltri,
  onSpol,
  onKlub,
  onPas,
  onCistka,
}: {
  igralci: IgralecDto[]
  stevila: { klubov: number; provizoricnih: number }
  jeAdmin: boolean
  nalozeno: boolean
  parov: number | null
  iskanje: string
  brezRatinga: boolean
  provizorici: boolean
  samoDuplikati: boolean
  filtriOdprti: boolean
  spol: Spol | ''
  klub: string
  pas: StarostniPas | ''
  klubi: [number, string][]
  jeFilter: boolean
  onIskanje: (v: string) => void
  onVsi: () => void
  onBrezRatinga: () => void
  onProvizorici: () => void
  onDuplikati: () => void
  onFiltri: () => void
  onSpol: (v: Spol | '') => void
  onKlub: (v: string) => void
  onPas: (v: StarostniPas | '') => void
  onCistka: () => void
}) {
  if (!nalozeno) return null
  const vsiChipi = !brezRatinga && !provizorici && !samoDuplikati
  return (
    <>
      <p className="kartoteka__mobi-povzetek">
        {igralci.length} igralcev · {stevila.klubov} klubov · ★ {stevila.provizoricnih}
      </p>
      {jeAdmin && (
        <button type="button" className="kartoteka__meta" onClick={onCistka}>
          Možnih duplikatov: {parov ?? '—'} · pregled →
        </button>
      )}
      <input
        className="kartoteka__mobi-iskanje"
        type="search"
        aria-label="Išči po imenu ali klubu"
        placeholder="Išči po imenu ali klubu …"
        value={iskanje}
        onChange={(dogodek) => onIskanje(dogodek.target.value)}
      />
      <div className="kartoteka__mobi-cipi">
        <Cip aktiven={vsiChipi && !jeFilter} oznaka="VSI" onKlik={onVsi} />
        <Cip aktiven={brezRatinga} oznaka="BREZ RATINGA" onKlik={onBrezRatinga} />
        <Cip aktiven={provizorici} oznaka="★ PROV." onKlik={onProvizorici} />
        {jeAdmin && <Cip aktiven={samoDuplikati} oznaka="DUPLIKATI" onKlik={onDuplikati} />}
        <Cip aktiven={filtriOdprti} oznaka="FILTRI" onKlik={onFiltri} />
      </div>
      {filtriOdprti && (
        <div className="kartoteka__mobi-filtri">
          <div className="kartoteka__izbirnik" role="group" aria-label="Spol">
            {(
              [
                ['', 'Vsi'],
                ['MOSKI', 'Moški'],
                ['ZENSKI', 'Ženski'],
              ] as const
            ).map(([vrednost, oznaka]) => (
              <button
                key={oznaka}
                type="button"
                aria-pressed={spol === vrednost}
                className={
                  'kartoteka__segment' + (spol === vrednost ? ' kartoteka__segment--aktiven' : '')
                }
                onClick={() => onSpol(spol === vrednost ? '' : vrednost)}
              >
                {oznaka}
              </button>
            ))}
          </div>
          <select
            className="kartoteka__izbira"
            aria-label="Klub"
            value={klub}
            onChange={(dogodek) => onKlub(dogodek.target.value)}
          >
            <option value="">Vsi klubi</option>
            <option value="-">Brez kluba</option>
            {klubi.map(([id, ime]) => (
              <option key={id} value={String(id)}>
                {ime}
              </option>
            ))}
          </select>
          <select
            className="kartoteka__izbira"
            aria-label="Pas"
            value={pas}
            onChange={(dogodek) => onPas(dogodek.target.value as StarostniPas | '')}
          >
            <option value="">Vsi pasovi</option>
            {VRSTNI_RED_STAROSTNIH_PASOV.map((p) => (
              <option key={p} value={p}>
                {OZNAKE_PASU_KRATKO[p]}
              </option>
            ))}
          </select>
        </div>
      )}
    </>
  )
}

/* Telefon: enovrstični seznam (ime, klub · letnik · pas, rating, meni). */
function SeznamTelefon({ vrstice, ...d }: DejanjaSeznama & { vrstice: IgralecDto[] }) {
  const { jeAdmin } = d
  return (
    <div>
      <div className="kartoteka__mobi-glava">
        <span>Igralec</span>
        <span>Rating</span>
      </div>
      {vrstice.map((igralec, indeks) => {
        const podroben = jeAdmin ? (igralec as IgralecPodrobenDto) : null
        const meta = [
          igralec.klub?.ime ?? 'brez kluba',
          podroben ? letnica(podroben.datumRojstva) : null,
          podroben && igralec.starostniPas ? OZNAKE_PASU_KRATKO[igralec.starostniPas] : null,
        ]
          .filter(Boolean)
          .join(' · ')
        return (
          <div
            key={igralec.id}
            className={
              'kartoteka__mobi-vrstica' + (jeAdmin ? '' : ' kartoteka__mobi-vrstica--javno')
            }
          >
            <div className="kartoteka__mobi-ime">
              <span className="kartoteka__mobi-ime-vrsta">
                <span className="kartoteka__ime">{igralec.ime}</span>
                <span className="kartoteka__priimek">{igralec.priimek}</span>
                {podroben?.rekreativniVstop && (
                  <span
                    className="kartoteka__znacka kartoteka__znacka--rekr"
                    title="Rekreativec — začetni rating 800"
                  >
                    Rekr.
                  </span>
                )}
                {jeAdmin && d.idjiDuplikatov.has(igralec.id) && (
                  <span className="kartoteka__znacka kartoteka__znacka--dup">Duplikat?</span>
                )}
              </span>
              <span className="kartoteka__mobi-meta">{meta}</span>
            </div>
            <span className="kartoteka__mobi-rating">
              <span
                className={
                  'kartoteka__stevilo'
                  + (igralec.rating == null
                    ? ' kartoteka__stevilo--brez'
                    : jeProvizoricen(igralec)
                      ? ' kartoteka__stevilo--prov'
                      : '')
                }
              >
                {igralec.rating ?? '—'}
              </span>
              {jeProvizoricen(igralec) && (
                <span className="kartoteka__mobi-prov">
                  {`★ ${igralec.steviloTekem}/${PROVIZORICNO_DO}`}
                </span>
              )}
            </span>
            {podroben && (
              <div>
                <MeniIgralca
                  igralec={igralec}
                  navzgor={indeks >= vrstice.length - VRSTIC_Z_MENIJEM_NAVZGOR}
                  d={d}
                />
              </div>
            )}
          </div>
        )
      })}
    </div>
  )
}

/* Čistka duplikatov: par za parom; odloči človek, ker isto ime imata lahko
   dve osebi. »Ni duplikat« par odstrani za vedno, združitev prenese tekme in
   prijave na obdržanega, drugi zapis pa se arhivira. */
function CistkaDuplikatov({
  pari,
  nalaganje,
  napaka,
  napakaMutacij,
  zdruzujem,
  onNazaj,
  onNiDuplikat,
  onZdruzi,
}: {
  pari: DvojnikIgralcaDto[]
  nalaganje: boolean
  napaka: unknown
  napakaMutacij: unknown
  zdruzujem: boolean
  onNazaj: () => void
  onNiDuplikat: (par: DvojnikIgralcaDto) => void
  onZdruzi: (obdrzan: IgralecPodrobenDto, odvec: IgralecPodrobenDto) => void
}) {
  return (
    <div>
      <div className="kartoteka__naslovna">
        <h2 className="kartoteka__naslov">Čistka duplikatov</h2>
        <button type="button" className="kartoteka__meta" onClick={onNazaj}>
          ← Nazaj na seznam
        </button>
      </div>
      <p className="kartoteka__uvod">
        Isto ime imata lahko dve osebi, zato odloči človek. Klub, rating in datum rojstva
        zadostujejo, da ugotoviš, ali gre za isto osebo. Pri združitvi se tekme prenesejo na
        obdržanega igralca, drugi zapis se arhivira.
      </p>
      {!nalaganje && !napaka && (
        <p className="kartoteka__preostalo">Preostalih parov: {pari.length}</p>
      )}
      <SporociloNapake napaka={napaka} />
      <SporociloNapake napaka={napakaMutacij} />
      {nalaganje && <p className="obvestilo">Nalaganje …</p>}
      {!nalaganje && !napaka && pari.length === 0 && (
        <p className="kartoteka__cisto">Vse čisto. Ni več možnih duplikatov.</p>
      )}
      <div className="kartoteka__pari">
        {pari.map((par) => (
          <div key={par.id} className="kartoteka__par">
            <StranPara
              igralec={par.levi}
              zdruzujem={zdruzujem}
              onObdrzi={() => onZdruzi(par.levi, par.desni)}
            />
            <div className="kartoteka__par-sredina">
              <span className="kartoteka__ujemanje">{OZNAKE_UJEMANJA[par.ujemanje].toUpperCase()}</span>
              <span
                className={
                  'kartoteka__datum' + (par.datum === 'ENAK' ? ' kartoteka__datum--enak' : '')
                }
              >
                {par.datum === 'ENAK'
                  ? 'ENAK DATUM ROJSTVA — SKORAJ GOTOVO ISTI IGRALEC'
                  : par.datum === 'PODOBEN'
                    ? 'PODOBEN DATUM ROJSTVA'
                    : 'DRUG DATUM ROJSTVA'}
              </span>
              <button type="button" className="gumb" onClick={() => onNiDuplikat(par)}>
                Ni duplikat
              </button>
            </div>
            <StranPara
              igralec={par.desni}
              zdruzujem={zdruzujem}
              onObdrzi={() => onZdruzi(par.desni, par.levi)}
            />
          </div>
        ))}
      </div>
    </div>
  )
}

function StranPara({
  igralec,
  zdruzujem,
  onObdrzi,
}: {
  igralec: IgralecPodrobenDto
  zdruzujem: boolean
  onObdrzi: () => void
}) {
  return (
    <div className="kartoteka__par-stran">
      <span className="kartoteka__par-ime">{polnoIme(igralec)}</span>
      <span className="kartoteka__par-klub">{igralec.klub?.ime ?? 'brez kluba'}</span>
      <span className="kartoteka__par-meta">
        r. {oblikujDatum(igralec.datumRojstva) || '—'} · lic. {igralec.ntzsLicenca ?? '—'}
      </span>
      <span className="kartoteka__par-meta">
        rating {igralec.rating ?? '—'} · {igralec.steviloTekem} tekem
      </span>
      <button
        type="button"
        className="gumb gumb--glavni"
        disabled={zdruzujem}
        onClick={onObdrzi}
      >
        Obdrži tega, združi
      </button>
    </div>
  )
}

/* Potrditev združitve: nepovratno dejanje, zato okno kot pri arhiviranju. */
function ZdruzitevOkno({
  obdrzan,
  odvec,
  pending,
  napaka,
  onPotrdi,
  onZapri,
}: {
  obdrzan: IgralecPodrobenDto
  odvec: IgralecPodrobenDto
  pending: boolean
  napaka: unknown
  onPotrdi: () => void
  onZapri: () => void
}) {
  return (
    <ModalnoOkno zapisnik brezObrazca naslov="Združitev igralcev" onZapri={onZapri}>
      <p className="zapisnik__besedilo zapisnik__besedilo--potrditev">
        Obdržim <strong>{polnoIme(obdrzan)}</strong>
        {obdrzan.klub ? ` (${obdrzan.klub.ime})` : ''}. Zapis <strong>{polnoIme(odvec)}</strong>
        {odvec.klub ? ` (${odvec.klub.ime})` : ''} se arhivira, njegove tekme, prijave in račun
        pa se prenesejo na obdržanega.
        {odvec.steviloTekem > 0
          && ' Ker ima odvečni zapis tekme, se rating obdržanega sestavi znova od dneva prve tekme; to lahko traja nekaj minut.'}{' '}
        Združim?
      </p>
      <div className="zapisnik__napaka">
        <SporociloNapake napaka={napaka} />
      </div>
      <div className="zapisnik__noga">
        <button type="button" className="gumb" onClick={onZapri} disabled={pending}>
          Prekliči
        </button>
        <button
          type="button"
          className="gumb gumb--nevarno-polno"
          onClick={onPotrdi}
          disabled={pending}
        >
          {pending ? 'Združujem …' : 'Združi'}
        </button>
      </div>
    </ModalnoOkno>
  )
}

function ArhivirajOkno({
  igralec,
  onPotrdi,
  onZapri,
}: {
  igralec: IgralecDto
  onPotrdi: () => void
  onZapri: () => void
}) {
  return (
    <ModalnoOkno zapisnik brezObrazca naslov="Arhiviranje igralca" onZapri={onZapri}>
      <p className="zapisnik__besedilo zapisnik__besedilo--potrditev">
        Igralec <strong>{polnoIme(igralec)}</strong> bo izginil s seznamov, zgodovina njegovih
        tekem pa ostane. Arhiviram?
      </p>
      <div className="zapisnik__noga">
        <button type="button" className="gumb" onClick={onZapri}>
          Prekliči
        </button>
        <button
          type="button"
          className="gumb gumb--nevarno-polno"
          onClick={() => {
            onPotrdi()
            onZapri()
          }}
        >
          Arhiviraj
        </button>
      </div>
    </ModalnoOkno>
  )
}

/* Postavitveni (začetni) rating za novinca. Na voljo le, dokler igralec ni
   odigral nobene tekme — kasneje rating določajo samo rezultati. */
function ZacetniRatingOkno({
  igralec,
  onZapri,
  onShranjeno,
}: {
  igralec: IgralecDto
  onZapri: () => void
  onShranjeno: () => void
}) {
  const [vrednost, nastaviVrednost] = useState(String(igralec.rating ?? 1000))

  const shranjevanje = useMutation({
    mutationFn: () => igralciApi.nastaviZacetniRating(igralec.id, Number(vrednost)),
    onSuccess: () => {
      onShranjeno()
      onZapri()
    },
  })

  const stevilka = Number(vrednost)
  const veljaven = Number.isInteger(stevilka) && stevilka >= 100 && stevilka <= 3000

  return (
    <ModalnoOkno
      zapisnik
      brezObrazca
      naslov={`Začetni rating — ${polnoIme(igralec)}`}
      onZapri={onZapri}
    >
      <form
        onSubmit={(d) => {
          d.preventDefault()
          if (veljaven) shranjevanje.mutate()
        }}
      >
        <p className="zapisnik__besedilo">
          Močnemu novincu lahko postaviš vstopni rating, da mu ni treba plezati z 1000. Mogoče je
          le pred prvo odigrano tekmo; nato rating določajo rezultati.
        </p>
        <div className="zapisnik__rating-blok">
          <label className="zapisnik__polje">
            <span className="zapisnik__oznaka">Turnirko rating (100–3000)</span>
            <StevilskoPolje
              className="zapisnik__veliko"
              najvec={3000}
              vrednost={vrednost}
              naSpremembo={nastaviVrednost}
              autoFocus
            />
          </label>
          <div className="zapisnik__hitro">
            <span className="zapisnik__oznaka">Hitra izbira</span>
            <div className="zapisnik__hitro-gumbi">
              {(
                [
                  [800, '800 · rekreativec'],
                  [1000, '1000 · privzeto'],
                  [1500, '1500 · odrasel'],
                ] as const
              ).map(([v, oznaka]) => (
                <button
                  key={v}
                  type="button"
                  className="zapisnik__hitro-gumb"
                  onClick={() => nastaviVrednost(String(v))}
                >
                  {oznaka}
                </button>
              ))}
            </div>
          </div>
        </div>
        <div className="zapisnik__napaka">
          <SporociloNapake napaka={shranjevanje.error} />
        </div>
        <div className="zapisnik__noga">
          <button type="button" className="gumb" onClick={onZapri}>
            Prekliči
          </button>
          <button
            type="submit"
            className="gumb gumb--glavni"
            disabled={!veljaven || shranjevanje.isPending}
          >
            Shrani rating
          </button>
        </div>
      </form>
    </ModalnoOkno>
  )
}

/* Zunanja uvrstitev: rating, prepisan z zunanje lestvice (ITTF, NTZS, druga
   zveza). Za razliko od začetnega ratinga je na voljo tudi po odigranih
   tekmah — prav zato obstaja: igralec, ki pri nas odigra dve tekmi na leto,
   ker sicer igra po svetu, ima pri nas številko, ki o njem ne pove ničesar.

   Vir in pojasnilo sta obvezna in JAVNA (izpišeta se na profilu pod grafom).
   To ni formalnost: ročno vpisana številka na javni lestvici je brez
   zapisanega vira videti kot naklonjenost, z virom pa je trditev, ki jo lahko
   vsak preveri. */
function ZunanjaUvrstitevOkno({
  igralec,
  onZapri,
  onShranjeno,
}: {
  igralec: IgralecDto
  onZapri: () => void
  onShranjeno: () => void
}) {
  const [vrednost, nastaviVrednost] = useState(String(igralec.rating ?? 1000))
  const [vir, nastaviVir] = useState('')
  const [pojasnilo, nastaviPojasnilo] = useState('')

  const shranjevanje = useMutation({
    mutationFn: () =>
      igralciApi.zunanjaUvrstitev(igralec.id, {
        vrednost: Number(vrednost),
        vir: vir.trim(),
        pojasnilo: pojasnilo.trim(),
      }),
    onSuccess: () => {
      onShranjeno()
      onZapri()
    },
  })

  const stevilka = Number(vrednost)
  const veljaven =
    Number.isInteger(stevilka)
    && stevilka >= 100
    && stevilka <= 3000
    && vir.trim() !== ''
    && pojasnilo.trim() !== ''

  return (
    <ModalnoOkno
      zapisnik
      brezObrazca
      naslov={`Zunanja uvrstitev — ${polnoIme(igralec)}`}
      onZapri={onZapri}
    >
      <form
        onSubmit={(d) => {
          d.preventDefault()
          if (veljaven) shranjevanje.mutate()
        }}
      >
        <p className="zapisnik__besedilo" style={{ maxWidth: '56ch' }}>
          Za redkega gosta, ki pri nas odigra premalo tekem, da bi ga lestvica sama uvrstila. Možna
          je tudi po odigranih tekmah.
        </p>
        <div className="zapisnik__zunanja">
          <div className="zapisnik__trenutno">
            <span className="zapisnik__oznaka">Trenutno</span>
            <span className="zapisnik__trenutno-rating">{igralec.rating ?? '—'}</span>
            <span className="zapisnik__trenutno-tekme">
              {igralec.rating != null
                ? `po ${igralec.steviloTekem} ${igralec.steviloTekem === 1 ? 'tekmi' : 'tekmah'}`
                : 'brez ratinga'}
            </span>
          </div>
          <div className="zapisnik__stolpec">
            <label className="zapisnik__polje">
              <span>Turnirko rating (100–3000)</span>
              <StevilskoPolje
                className="zapisnik__rating-vnos"
                najvec={3000}
                vrednost={vrednost}
                naSpremembo={nastaviVrednost}
                autoFocus
              />
            </label>
            <label className="zapisnik__polje">
              <span>Vir *</span>
              <input
                type="text"
                maxLength={200}
                placeholder="npr. ITTF svetovna lestvica, september 2026"
                value={vir}
                onChange={(d) => nastaviVir(d.target.value)}
              />
            </label>
            <label className="zapisnik__polje">
              <span>Pojasnilo *</span>
              <textarea
                rows={3}
                maxLength={500}
                placeholder="npr. Igra skoraj samo mednarodno, pri nas dve tekmi na leto."
                value={pojasnilo}
                onChange={(d) => nastaviPojasnilo(d.target.value)}
              />
            </label>
          </div>
        </div>
        <div className="zapisnik__javno">
          <span className="zapisnik__oznaka">Javno — izpiše se na profilu pod grafom</span>
          <p>
            Vir in pojasnilo vidi vsak obiskovalec. Ročno vpisana številka brez zapisanega vira
            je na javni lestvici videti kot naklonjenost; z virom je trditev, ki jo lahko vsak
            preveri.
          </p>
        </div>
        <div className="zapisnik__napaka">
          <SporociloNapake napaka={shranjevanje.error} />
        </div>
        <div className="zapisnik__noga">
          <button type="button" className="gumb" onClick={onZapri}>
            Prekliči
          </button>
          <button
            type="submit"
            className="gumb gumb--glavni"
            disabled={!veljaven || shranjevanje.isPending}
          >
            Vpiši uvrstitev
          </button>
        </div>
      </form>
    </ModalnoOkno>
  )
}

function IgralecOkno({
  igralec,
  onZapri,
  onShranjeno,
}: {
  /* null = nov igralec (tega sme dodati tudi organizator, čeprav obstoječih
     osebnih podatkov ne vidi). Urejanje je vezano na podroben zapis. */
  igralec: IgralecPodrobenDto | null
  onZapri: () => void
  onShranjeno: () => void
}) {
  const klubi = useQuery({ queryKey: ['klubi'], queryFn: klubiApi.seznam })
  const kraji = useQuery({ queryKey: ['kraji'], queryFn: krajiApi.seznam })

  const [ime, nastaviIme] = useState(igralec?.ime ?? '')
  const [priimek, nastaviPriimek] = useState(igralec?.priimek ?? '')
  const [spol, nastaviSpol] = useState<Spol>(igralec?.spol ?? 'MOSKI')
  const [datumRojstva, nastaviDatumRojstva] = useState(igralec?.datumRojstva ?? '')
  const [idKlub, nastaviIdKlub] = useState(igralec?.klub ? String(igralec.klub.id) : '')
  const [ntzsLicenca, nastaviLicenco] = useState(igralec?.ntzsLicenca ?? '')
  const [igralnaRoka, nastaviRoko] = useState<'' | IgralnaRoka>(igralec?.igralnaRoka ?? '')
  const [email, nastaviEmail] = useState(igralec?.email ?? '')
  const [telefonskaSt, nastaviTelefon] = useState(igralec?.telefonskaSt ?? '')
  const [drzavljanstvo, nastaviDrzavljanstvo] = useState(igralec?.drzavljanstvo ?? '')
  const [naslov, nastaviNaslov] = useState(igralec?.naslov ?? '')
  const [postnaSt, nastaviPostnaSt] = useState(
    igralec?.kraj ? String(igralec.kraj.postnaSt) : '',
  )
  const [rekreativniVstop, nastaviRekreativniVstop] = useState(
    igralec?.rekreativniVstop ?? false,
  )
  /* Oznaka velja ob prvi tekmi. Kdor jih že ima, dobi nov začetek šele s
     preračunom — to mora urejevalec vedeti, preden shrani. */
  const potrebenPreracun =
    igralec != null && igralec.steviloTekem > 0 && rekreativniVstop !== igralec.rekreativniVstop

  const shranjevanje = useMutation({
    mutationFn: (vnos: IgralecVnos) =>
      igralec ? igralciApi.posodobi(igralec.id, vnos) : igralciApi.ustvari(vnos),
    onSuccess: () => {
      onShranjeno()
      onZapri()
    },
  })

  /* Opozorilo »podoben igralec že obstaja« ob dodajanju. Admin dobi zapis z
     datumom rojstva (strežnik ga drugim ne da), organizator brez njega. */
  const { jeAdmin } = useAvtentikacija()
  const preverjanje = useMutation<PodobenZapis[], Error, PodobniVnos>({
    mutationFn: (vnos) =>
      jeAdmin ? igralciApi.podobniPodrobno(vnos) : igralciApi.podobni(vnos),
  })
  /* Opozorilo velja za natanko tisti vpis, ki ga je sprožil: ko vpisovalec
     kaj popravi, izgine in ob naslednjem shranjevanju se preveri znova.
     Podpis je izpeljan, ne hranjen — brez učinka, ki bi opozorilo brisal. */
  const [opozorilo, nastaviOpozorilo] = useState<{
    podpis: string
    zadetki: PodobenZapis[]
  } | null>(null)
  const podpis = [ime.trim(), priimek.trim(), spol, datumRojstva].join('|')
  const podobni = opozorilo?.podpis === podpis ? opozorilo.zadetki : []
  const okvirOpozorila = useRef<HTMLDivElement>(null)
  useEffect(() => {
    if (podobni.length > 0) okvirOpozorila.current?.scrollIntoView({ block: 'nearest' })
  }, [podobni.length])

  /* Začetnica se popravi med tipkanjem. Vrednost polja zapišemo že tu, ne šele
     z novim izrisom: ko React polju zamenja vrednost, kazalec skoči na konec,
     popravek sredi besedila pa bi ga vsakič odnesel. Ker se spremeni le velikost
     črk, ostane dolžina ista in izbor se natančno obnovi. */
  function obSpremembiImena(nastavi: (vrednost: string) => void) {
    return (dogodek: ChangeEvent<HTMLInputElement>) => {
      const polje = dogodek.target
      const { selectionStart: zacetek, selectionEnd: konec } = polje
      polje.value = zVelikoZacetnico(polje.value)
      polje.setSelectionRange(zacetek, konec)
      nastavi(polje.value)
    }
  }

  function obOddaji(dogodek: FormEvent) {
    dogodek.preventDefault()
    const vnos: IgralecVnos = {
      ime: ime.trim(),
      priimek: priimek.trim(),
      spol,
      datumRojstva,
      email: email.trim() || null,
      telefonskaSt: telefonskaSt.trim() || null,
      igralnaRoka: igralnaRoka || null,
      ntzsLicenca: ntzsLicenca.trim() || null,
      drzavljanstvo: drzavljanstvo.trim() || null,
      naslov: naslov.trim() || null,
      postnaSt: postnaSt ? Number(postnaSt) : null,
      idKlub: idKlub ? Number(idKlub) : null,
      rekreativniVstop,
    }
    // urejanje ne ustvari novega zapisa, vpis, na katerega je bil bralec
    // že opozorjen, pa je s ponovnim klikom potrjen
    if (igralec || podobni.length > 0) {
      shranjevanje.mutate(vnos)
      return
    }
    // poizvedba dobi samo to, po čemer išče: e-pošta, telefon in naslov
    // novega igralca ji ne pripadajo
    const iskano: PodobniVnos = {
      ime: vnos.ime,
      priimek: vnos.priimek,
      spol: vnos.spol,
      datumRojstva: vnos.datumRojstva,
    }
    preverjanje.mutate(iskano, {
      onSuccess: (zadetki) => {
        if (zadetki.length > 0) nastaviOpozorilo({ podpis, zadetki })
        else shranjevanje.mutate(vnos)
      },
      // opozorilo je pomoč pri vpisu in ne pogoj zanj: če ga strežnik ne
      // zna dati, igralca ne sme zadržati
      onError: () => shranjevanje.mutate(vnos),
    })
  }

  return (
    <ModalnoOkno zapisnik naslov={igralec ? 'Uredi igralca' : 'Nov igralec'} onZapri={onZapri}>
      <form onSubmit={obOddaji}>
        <div className="zapisnik__sklop">Osnovno</div>
        <div className="zapisnik__mreza">
          <label className="zapisnik__polje">
            <span>Ime *</span>
            <input value={ime} onChange={obSpremembiImena(nastaviIme)} required />
          </label>
          <label className="zapisnik__polje">
            <span>Priimek *</span>
            <input value={priimek} onChange={obSpremembiImena(nastaviPriimek)} required />
          </label>
          <label className="zapisnik__polje">
            <span>Spol *</span>
            <select value={spol} onChange={(d) => nastaviSpol(d.target.value as Spol)}>
              {Object.entries(OZNAKE_SPOL).map(([vrednost, oznaka]) => (
                <option key={vrednost} value={vrednost}>
                  {oznaka}
                </option>
              ))}
            </select>
          </label>
          <label className="zapisnik__polje">
            <span>Datum rojstva *</span>
            <input
              type="date"
              className="zapisnik__mono"
              value={datumRojstva}
              onChange={(d) => nastaviDatumRojstva(d.target.value)}
              required
            />
          </label>
        </div>

        {/* Takoj pod datumom rojstva: tudi ta pove, kje igralec začne. */}
        <label className="zapisnik__kljukica">
          <input
            type="checkbox"
            checked={rekreativniVstop}
            onChange={(d) => nastaviRekreativniVstop(d.target.checked)}
          />
          <span>Rekreativec — začetni rating 800</span>
        </label>
        <p className="zapisnik__namig">
          Brez oznake novinec začne pri povprečju registriranih igralcev svoje starosti (odrasel
          okoli 1500). Po prvem dnevu ga v obeh primerih uvrstijo izidi.
        </p>
        {potrebenPreracun && (
          <p className="obvestilo obvestilo--opozorilo zapisnik__obvestilo" role="status">
            Igralec že ima odigrane tekme. Nov začetek obvelja šele, ko na strani Rating poženeš
            preračun od dneva njegove prve tekme.
          </p>
        )}

        <div className="zapisnik__sklop">Klub in licenca</div>
        <div className="zapisnik__mreza">
          <IzbirnikKluba
            namig="Brez kluba · vpiši ime"
            klubi={klubi.data ?? []}
            izbrano={idKlub ? Number(idKlub) : null}
            naSpremembo={(id) => nastaviIdKlub(id === null ? '' : String(id))}
          />
          <label className="zapisnik__polje">
            <span>Licenca NTZS</span>
            <input
              className="zapisnik__mono"
              value={ntzsLicenca}
              onChange={(d) => nastaviLicenco(d.target.value)}
            />
          </label>
          <label className="zapisnik__polje">
            <span>Igralna roka</span>
            <select
              value={igralnaRoka}
              onChange={(d) => nastaviRoko(d.target.value as '' | IgralnaRoka)}
            >
              <option value="">— ni podatka —</option>
              {Object.entries(OZNAKE_IGRALNA_ROKA).map(([vrednost, oznaka]) => (
                <option key={vrednost} value={vrednost}>
                  {oznaka}
                </option>
              ))}
            </select>
          </label>
          <label className="zapisnik__polje">
            <span>Državljanstvo</span>
            <input
              value={drzavljanstvo}
              onChange={(d) => nastaviDrzavljanstvo(d.target.value)}
              placeholder="npr. slovensko"
            />
          </label>
        </div>

        <div className="zapisnik__sklop">Kontakt</div>
        <div className="zapisnik__mreza">
          <label className="zapisnik__polje">
            <span>E-pošta</span>
            <input type="email" value={email} onChange={(d) => nastaviEmail(d.target.value)} />
          </label>
          <label className="zapisnik__polje">
            <span>Telefon</span>
            <input
              className="zapisnik__mono"
              value={telefonskaSt}
              onChange={(d) => nastaviTelefon(d.target.value)}
            />
          </label>
          <label className="zapisnik__polje">
            <span>Naslov</span>
            <input value={naslov} onChange={(d) => nastaviNaslov(d.target.value)} />
          </label>
          <IzbirnikKraja
            kraji={kraji.data ?? []}
            izbrano={postnaSt ? Number(postnaSt) : null}
            naSpremembo={(st) => nastaviPostnaSt(st === null ? '' : String(st))}
          />
        </div>

        {podobni.length > 0 && (
          <div ref={okvirOpozorila}>
            <PodobniIgralci zadetki={podobni} vpisanDatum={datumRojstva} />
          </div>
        )}
        <div className="zapisnik__napaka">
          <SporociloNapake napaka={shranjevanje.error} />
        </div>
        <div className="zapisnik__noga">
          <button type="button" className="gumb" onClick={onZapri}>
            Prekliči
          </button>
          {podobni.length > 0 && (
            <button type="button" className="gumb" onClick={() => nastaviOpozorilo(null)}>
              Popravi vnos
            </button>
          )}
          <button
            type="submit"
            className="gumb gumb--glavni"
            disabled={shranjevanje.isPending || preverjanje.isPending}
          >
            {igralec
              ? 'Shrani spremembe'
              : preverjanje.isPending
                ? 'Preverjam …'
                : podobni.length > 0
                  ? 'Vseeno dodaj igralca'
                  : 'Dodaj igralca'}
          </button>
        </div>
      </form>
    </ModalnoOkno>
  )
}

type PodobenZapis = PodobenIgralecDto | PodobenIgralecPodrobenDto

/* Datum rojstva obstoječega igralca proti vpisanemu — samo za admina, ki ga
   strežnik pošlje; organizator te vrstice ne dobi. */
function opisDatuma(zapis: PodobenIgralecPodrobenDto, vpisanDatum: string): string {
  const obstojec = oblikujDatum(zapis.igralec.datumRojstva)
  switch (zapis.datum) {
    case 'ENAK':
      return `Enak datum rojstva: ${obstojec}`
    case 'PODOBEN':
      return `Podoben datum rojstva: ${obstojec} (vpisan ${oblikujDatum(vpisanDatum)})`
    case 'DRUG':
      return `Drug datum rojstva: ${obstojec} (vpisan ${oblikujDatum(vpisanDatum)})`
  }
}

/* Opozorilo ob dodajanju: v bazi že obstaja igralec s podobnim imenom. Isto
   ime imata lahko dve osebi, zato je to vprašanje in ne zavrnitev — odloči
   vpisovalec. Klub, pas in rating zadostujejo, da ugotovi, ali gre za isto
   osebo; admin dobi povrh še datum rojstva. */
function PodobniIgralci({
  zadetki,
  vpisanDatum,
}: {
  zadetki: PodobenZapis[]
  vpisanDatum: string
}) {
  return (
    <div className="zapisnik__podobni" role="status">
      <h4>
        {zadetki.length === 1
          ? 'V bazi že obstaja podoben igralec.'
          : 'V bazi že obstajajo podobni igralci.'}
      </h4>
      <ul className="zapisnik__podobni-seznam">
        {zadetki.map((zapis) => {
          const { igralec } = zapis
          const meta = [
            igralec.klub?.ime ?? 'brez kluba',
            igralec.starostniPas ? OZNAKE_PASU_KRATKO[igralec.starostniPas] : null,
            igralec.rating != null ? `rating ${igralec.rating}` : 'brez ratinga',
            `${igralec.steviloTekem} ${sklonTekem(igralec.steviloTekem)}`,
            zapis.arhiviran ? 'arhiviran' : null,
          ].filter(Boolean)
          return (
            <li key={igralec.id} className="zapisnik__podobni-igralec">
              <div className="zapisnik__podobni-ime">
                <span>{polnoIme(igralec)}</span>
                <span
                  className={
                    'zapisnik__ujemanje' + (zapis.ujemanje === 'ISTO' ? ' zapisnik__ujemanje--isto' : '')
                  }
                >
                  {OZNAKE_UJEMANJA[zapis.ujemanje]}
                </span>
              </div>
              <span className="zapisnik__podobni-meta">{meta.join(' · ')}</span>
              {'datum' in zapis && (
                <span
                  className={
                    'zapisnik__podobni-meta zapisnik__podobni-meta--datum'
                    + (zapis.datum === 'ENAK' ? ' zapisnik__podobni-meta--enak' : '')
                  }
                >
                  {opisDatuma(zapis, vpisanDatum)}
                  {zapis.datum === 'ENAK' && ' — skoraj gotovo isti igralec'}
                </span>
              )}
            </li>
          )
        })}
      </ul>
      <p className="zapisnik__podobni-vprasanje">
        {zadetki.length === 1
          ? 'Preveri, ali vpisani igralec ni isti kot ta.'
          : 'Preveri, ali vpisani igralec ni isti kot kdo od teh.'}
      </p>
    </div>
  )
}
