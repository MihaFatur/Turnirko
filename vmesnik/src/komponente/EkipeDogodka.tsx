/* Ekipe ekipnega dogodka turnirja s kadri.

   V pripravi organizator ekipe prijavlja (klubska iz registra ali prosta z
   lastnim imenom - isto pravilo kot v ligi) in sestavlja kadre. Žreb zahteva,
   da ima vsaka ekipa v kadru vsaj toliko igralcev, kolikor jih format postavi
   za mizo, zato vrstica tako ekipo označi že tu in ne šele ob zavrnjenem
   žrebu. Med tekmovanjem sme kader samo dopolniti (ekipa pripelje rezervo);
   igralca, ki je za ekipo že nastopil, strežnik ne pusti odstraniti, ker bi
   njegove tekme ostale brez člana kadra. Po koncu dogodka je kader zaklenjen.

   Gost in uvožen dogodek vidita isti seznam brez dejanj: kdo je igral za
   katero ekipo, je del zapisa tekmovanja. */
import { useState, type ReactNode } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'

import { dogodkiApi, igralciApi, klubiApi } from '../api/zahteve'
import type { DogodekDto, EkipaDto } from '../api/tipi'
import { igralcevFormata } from '../api/tipi'
import { sklonEkip, sklonIgralcev } from '../pomozno/oblikovanje'
import { IzbirnikIgralca } from './IzbirnikIgralca'
import { IzbirnikKluba } from './IzbirnikKluba'
import { NapakaPoizvedbe } from './NapakaPoizvedbe'
import { PotrditvenoOkno } from './PotrditvenoOkno'
import { SporociloNapake } from './SporociloNapake'

interface Lastnosti {
  dogodek: DogodekDto
  /* Sme urejati (lastnik turnirja ali admin) in dogodek ni uvožen. */
  smem: boolean
  /* Dodatno dejanje ali stanje v vrstici ekipe (npr. odstop med tekmovanjem). */
  dejanje?: (ekipa: EkipaDto) => ReactNode
}

export function EkipeDogodka({ dogodek, smem, dejanje }: Lastnosti) {
  const odjemalec = useQueryClient()
  const vPripravi = dogodek.status === 'PRIPRAVA'
  const koncan = dogodek.status === 'ZAKLJUCEN'
  const potrebno = dogodek.formatSrecanja ? igralcevFormata(dogodek.formatSrecanja) : 0
  const ekipe = useQuery({
    queryKey: ['ekipe-dogodka', dogodek.id],
    queryFn: () => dogodkiApi.ekipe(dogodek.id),
  })
  const [odprta, nastaviOdprto] = useState<number | null>(null)
  const [zaOdjavo, nastaviZaOdjavo] = useState<EkipaDto | null>(null)

  /* Kader premakne tudi jakost ekipe v jakostnem vrstnem redu (povprečje
     najboljših ratingov kadra), zato se z ekipami osveži še dogodek. */
  const osvezi = () => {
    odjemalec.invalidateQueries({ queryKey: ['ekipe-dogodka', dogodek.id] })
    odjemalec.invalidateQueries({ queryKey: ['dogodek', dogodek.id] })
  }
  const odjava = useMutation({
    mutationFn: (idEkipa: number) => dogodkiApi.odstraniEkipo(idEkipa),
    onSuccess: osvezi,
  })

  const seznam = ekipe.data ?? []
  const premajhnih = vPripravi ? seznam.filter((e) => e.steviloKadra < potrebno).length : 0

  return (
    <div>
      <div className="naslovna-vrstica">
        <h2>{smem && !koncan ? 'Ekipe in kadri' : 'Ekipe'}</h2>
        <span className="sekcija__meta">
          {seznam.length} {sklonEkip(seznam.length)}
          {potrebno > 0 && ` · ${potrebno} ${sklonIgralcev(potrebno)} za mizo`}
        </span>
      </div>

      {smem && vPripravi && (
        <DodajanjeEkipe
          idDogodka={dogodek.id}
          onDodana={(ekipa) => {
            osvezi()
            // kader je naslednje opravilo, zato se ekipa odpre sama
            nastaviOdprto(ekipa.id)
          }}
        />
      )}

      {ekipe.isPending && <p className="obvestilo">Nalaganje ekip …</p>}
      <NapakaPoizvedbe poizvedba={ekipe} kaj="ekip" />
      {ekipe.data && seznam.length === 0 && <p className="obvestilo">Ni še prijavljenih ekip.</p>}

      {seznam.length > 0 && (
        <ul className="liga__ekipe">
          {seznam.map((ekipa) => {
            const odprt = odprta === ekipa.id
            const premajhen = vPripravi && ekipa.steviloKadra < potrebno
            return (
              <li key={ekipa.id}>
                <div className="liga__ekipa">
                  <ImeEkipe ekipa={ekipa} />
                  <span className="liga__ekipa-gumbi">
                    {premajhen && <span className="znacka znacka--opozorilo">premajhen kader</span>}
                    <span className="sekcija__meta">{kaderTekst(ekipa.steviloKadra)}</span>
                    <button
                      type="button"
                      className="gumb gumb--majhen"
                      aria-expanded={odprt}
                      onClick={() => nastaviOdprto(odprt ? null : ekipa.id)}
                    >
                      Kader {odprt ? '▴' : '▾'}
                    </button>
                    {dejanje?.(ekipa)}
                    {smem && vPripravi && (
                      <button
                        type="button"
                        className="gumb gumb--majhen gumb--nevaren"
                        onClick={() => nastaviZaOdjavo(ekipa)}
                      >
                        Odjavi
                      </button>
                    )}
                  </span>
                </div>
                {odprt && (
                  <KaderEkipe
                    ekipa={ekipa}
                    dogodek={dogodek}
                    urejanje={smem && !koncan}
                    onSpremenjeno={osvezi}
                  />
                )}
              </li>
            )
          })}
        </ul>
      )}

      {premajhnih > 0 && (
        <p className="namig">
          Pred žrebom mora imeti vsaka ekipa v kadru vsaj {potrebno}{' '}
          {potrebno === 2 ? 'igralca' : 'igralce'} (toliko jih format postavi za mizo). Igralec
          sme biti v kadru ene same ekipe dogodka.
        </p>
      )}
      <SporociloNapake napaka={odjava.error} />

      {zaOdjavo && (
        <PotrditvenoOkno
          naslov="Odjava ekipe"
          sporocilo={
            `Ekipa ${zaOdjavo.prikazanoIme} se odjavi z dogodka, njen kader pa se izbriše.` +
            ' Ekipo lahko prijaviš znova, kader pa bo treba sestaviti od začetka.'
          }
          besedaPotrditve="Odjavi ekipo"
          onPotrdi={() => odjava.mutate(zaOdjavo.id)}
          onZapri={() => nastaviZaOdjavo(null)}
        />
      )}
    </div>
  )
}

/* Prijava ekipe: vrsta ekipe je preklop in ne polje (isto kot v pripravi
   lige). Klubska se izbere iz registra, ime je neobvezno; prosta ekipa se
   poimenuje sama in v register klubov ne pride. */
function DodajanjeEkipe({
  idDogodka,
  onDodana,
}: {
  idDogodka: number
  onDodana: (ekipa: EkipaDto) => void
}) {
  const klubi = useQuery({ queryKey: ['klubi'], queryFn: klubiApi.seznam })
  const [nacin, nastaviNacin] = useState<'klub' | 'prosta'>('klub')
  const [idKlub, nastaviKlub] = useState('')
  const [ime, nastaviIme] = useState('')

  const lahkoDodam = nacin === 'klub' ? idKlub !== '' : ime.trim() !== ''
  const dodaj = useMutation({
    mutationFn: () =>
      dogodkiApi.dodajEkipo(idDogodka, {
        idKlub: nacin === 'klub' ? Number(idKlub) : null,
        zaporedna: null,
        ime: ime.trim() || null,
      }),
    onSuccess: (ekipa) => {
      nastaviKlub('')
      nastaviIme('')
      onDodana(ekipa)
    },
  })

  return (
    <>
      <div className="izbirnik liga__nacin-ekipe" role="group" aria-label="Vrsta ekipe">
        <button
          type="button"
          className={'izbirnik__gumb' + (nacin === 'klub' ? ' izbirnik__gumb--aktiven' : '')}
          aria-pressed={nacin === 'klub'}
          onClick={() => nastaviNacin('klub')}
        >
          Iz registra
        </button>
        <button
          type="button"
          className={'izbirnik__gumb' + (nacin === 'prosta' ? ' izbirnik__gumb--aktiven' : '')}
          aria-pressed={nacin === 'prosta'}
          onClick={() => nastaviNacin('prosta')}
        >
          Prosta ekipa
        </button>
      </div>

      <form
        className="obrazec__vrstica liga__dodaj-ekipo"
        onSubmit={(d) => {
          d.preventDefault()
          if (lahkoDodam && !dodaj.isPending) dodaj.mutate()
        }}
      >
        {nacin === 'klub' && (
          <IzbirnikKluba
            klubi={klubi.data ?? []}
            izbrano={idKlub ? Number(idKlub) : null}
            naSpremembo={(id) => nastaviKlub(id === null ? '' : String(id))}
          />
        )}
        <label className="obrazec__polje">
          <span>{nacin === 'klub' ? 'Ime ekipe (neobvezno)' : 'Ime ekipe'}</span>
          <input
            value={ime}
            maxLength={60}
            onChange={(d) => nastaviIme(d.target.value)}
            placeholder={nacin === 'klub' ? 'sicer klub in številka' : 'npr. Kombinirana ekipa'}
          />
        </label>
        <button className="gumb" type="submit" disabled={!lahkoDodam || dodaj.isPending}>
          + Prijavi ekipo
        </button>
      </form>
      {nacin === 'prosta' && (
        <p className="namig">
          Prosta ekipa nastopa samo na tem dogodku in v register klubov ne pride. Kader ji
          sestaviš iz igralcev registra, enako kot klubski.
        </p>
      )}
      <SporociloNapake napaka={dodaj.error} />
    </>
  )
}

/* Kader ene ekipe: rating in (po začetku) izkupiček posamičnih tekem za to
   ekipo na tem dogodku - vrstni red je strežnikov, po zmagah navzdol. */
function KaderEkipe({
  ekipa,
  dogodek,
  urejanje,
  onSpremenjeno,
}: {
  ekipa: EkipaDto
  dogodek: DogodekDto
  urejanje: boolean
  onSpremenjeno: () => void
}) {
  const odjemalec = useQueryClient()
  const kader = useQuery({
    queryKey: ['kader-dogodka', ekipa.id],
    queryFn: () => dogodkiApi.kader(ekipa.id),
  })
  const igralci = useQuery({
    queryKey: ['igralci'],
    queryFn: igralciApi.seznam,
    enabled: urejanje,
  })
  const vPripravi = dogodek.status === 'PRIPRAVA'

  const osvezi = () => {
    odjemalec.invalidateQueries({ queryKey: ['kader-dogodka', ekipa.id] })
    onSpremenjeno()
  }
  const dodaj = useMutation({
    mutationFn: (idIgralec: number) => dogodkiApi.dodajVKader(ekipa.id, { idIgralec, vrstniRed: null }),
    onSuccess: osvezi,
  })
  const odstrani = useMutation({
    mutationFn: (idKader: number) => dogodkiApi.odstraniIzKadra(idKader),
    onSuccess: osvezi,
  })

  /* Ponujeni so igralci, ki jih ta kader še nima in po spolu ustrezajo
     kategoriji. Da je igralec že v kadru druge ekipe, pove strežnik - seznam
     vseh kadrov dogodka vmesnik nima pri roki. */
  const vKadru = new Set(kader.data?.map((k) => k.idIgralec))
  const naVoljo = (igralci.data ?? []).filter((i) => {
    if (vKadru.has(i.id)) return false
    if (dogodek.spolKategorija === 'MOSKI') return i.spol === 'MOSKI'
    if (dogodek.spolKategorija === 'ZENSKE') return i.spol === 'ZENSKI'
    return true
  })

  return (
    <div className="liga__kader">
      <div className="liga__kader-glava">
        <span className="liga__kader-naslov">Kader — {ekipa.prikazanoIme}</span>
        <span className="sekcija__meta">{vPripravi ? 'rating' : 'rating · bilanca na dogodku'}</span>
      </div>
      {kader.isPending && <p className="obvestilo">Nalaganje kadra …</p>}
      <NapakaPoizvedbe poizvedba={kader} kaj="kadra" />
      {kader.data && kader.data.length === 0 && <p className="obvestilo">Kader je prazen.</p>}
      {kader.data?.map((k, i) => (
        <div
          key={k.id}
          className={'liga__kader-vrstica' + (urejanje ? ' liga__kader-vrstica--dejanje' : '')}
        >
          <span className="liga__kader-mesto">{i + 1}.</span>
          <span className="liga__kader-ime">{k.polnoIme}</span>
          <span className="liga__kader-rating">{k.rating ?? '—'}</span>
          <span className="liga__kader-bilanca">{vPripravi ? '' : `${k.zmage} : ${k.porazi}`}</span>
          {urejanje && (
            <button
              type="button"
              className="gumb gumb--majhen gumb--nevaren"
              disabled={odstrani.isPending}
              onClick={() => odstrani.mutate(k.id)}
            >
              Odstrani
            </button>
          )}
        </div>
      ))}
      <SporociloNapake napaka={odstrani.error} />

      {urejanje && (
        <div className="liga__dodaj-ekipo">
          <IzbirnikIgralca
            oznaka="Dodaj igralca v kader"
            vidnaOznaka
            namig="Vpiši ime igralca"
            igralci={naVoljo}
            izkljuci=""
            naSpremembo={(id) => dodaj.mutate(id)}
          />
          {!vPripravi && (
            <p className="namig">
              Med tekmovanjem je kader mogoče dopolniti; igralca, ki je za ekipo že igral, ni
              mogoče odstraniti.
            </p>
          )}
          <SporociloNapake napaka={dodaj.error} />
        </div>
      )}
    </div>
  )
}

/* Ime ekipe z oznako pod njim: klub pri klubski ekipi z lastnim imenom,
   »prosta ekipa« pri prosti (isto pravilo kot v pripravi lige). */
function ImeEkipe({ ekipa }: { ekipa: EkipaDto }) {
  const oznaka = ekipa.klub === null ? 'prosta ekipa' : ekipa.ime ? ekipa.klub : null
  return (
    <span className="liga__ekipa-ime">
      <span>{ekipa.prikazanoIme}</span>
      {oznaka && <span className="liga__ekipa-oznaka">{oznaka}</span>}
    </span>
  )
}

/* Velikost kadra v vrstici ekipe; prazen kader je stanje in ne "0 igralcev". */
function kaderTekst(n: number): string {
  return n === 0 ? 'kader prazen' : `${n} ${sklonIgralcev(n)}`
}
