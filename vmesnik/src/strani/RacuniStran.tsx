/* Administracija dostopov igralcev: potrjevanje registracij (s povezavo na
   zapis igralca), zavrnitev, vklop/izklop ter nastavitev gesla (po izbiri ali
   naključno). Gesla ni mogoče prebrati — shranjena je le zgostitev — zato ga
   admin ne "vidi", ampak ga poljubno nastavi in izroči igralcu.
   Vidi jo samo administrator. */
import { useEffect, useMemo, useRef, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'

import { igralciApi, klubiApi, racuniApi } from '../api/zahteve'
import type { RacunIgralcaDto } from '../api/tipi'
import { OZNAKE_STATUSA_RACUNA, OZNAKE_VLOGA } from '../api/tipi'
import { IskalniIzbirnik, type MoznostIzbirnika } from '../komponente/IskalniIzbirnik'
import { IzbirnikKluba } from '../komponente/IzbirnikKluba'
import { PotrditvenoOkno } from '../komponente/PotrditvenoOkno'
import { SporociloNapake } from '../komponente/SporociloNapake'

export function RacuniStran() {
  const racuni = useQuery({ queryKey: ['racuni'], queryFn: racuniApi.seznam })

  if (racuni.isPending) return <p className="obvestilo">Nalaganje …</p>
  if (racuni.error) return <SporociloNapake napaka={racuni.error} />

  const vsi = racuni.data ?? []
  const cakajoci = vsi.filter((r) => r.status === 'CAKA')
  const ostali = vsi.filter((r) => r.status !== 'CAKA')

  return (
    <section>
      {/* Glave strani (nadnaslov, naslov, uvod) ni: kje smo, pove navigacija.
          Ostane kolofon s števili — tam, kjer je stal prej. */}
      <div className="stran-glava stran-glava--ozka stran-glava--brez-naslova">
        <div className="kolofon">
          <div className="kolofon__vrstica">
            <span className="kolofon__oznaka">Računi</span>
            <span className="kolofon__vrednost">{vsi.length}</span>
          </div>
          <div className="kolofon__vrstica">
            <span className="kolofon__oznaka">Čaka na potrditev</span>
            <span
              className={
                'kolofon__vrednost' + (cakajoci.length > 0 ? ' kolofon__vrednost--neg' : '')
              }
            >
              {cakajoci.length}
            </span>
          </div>
          <div className="kolofon__vrstica">
            <span className="kolofon__oznaka">Organizatorji</span>
            <span className="kolofon__vrednost">
              {vsi.filter((r) => r.vloga === 'ORGANIZATOR').length}
            </span>
          </div>
        </div>
      </div>

      <div>
        <div className="naslovna-vrstica">
          <h2>Čaka na potrditev</h2>
          <span className="sekcija__meta">{cakajoci.length} zahtev</span>
        </div>
        {cakajoci.length === 0 ? (
          <p className="obvestilo">Ni novih zahtev.</p>
        ) : (
          cakajoci.map((r) =>
            r.vloga === 'ORGANIZATOR' ? (
              <ZahtevaOrganizator key={r.id} racun={r} />
            ) : (
              <ZahtevaKartica key={r.id} racun={r} />
            ),
          )
        )}
      </div>

      <div>
        <div className="naslovna-vrstica">
          <h2>Računi</h2>
          <span className="sekcija__meta">{ostali.length} potrjenih</span>
        </div>
        {ostali.length === 0 ? (
          <p className="obvestilo">Ni še potrjenih dostopov.</p>
        ) : (
          <div className="tabela-ovoj">
            <table className="tabela">
              <thead>
                <tr>
                  <th scope="col">E-pošta</th>
                  <th scope="col">Vloga</th>
                  <th scope="col">Igralec / klub</th>
                  <th scope="col">Stanje</th>
                  <th scope="col" className="tabela__dejanja"></th>
                </tr>
              </thead>
              <tbody>
                {ostali.map((r) => (
                  <ObstojecaVrstica key={r.id} racun={r} />
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </section>
  )
}

function ZahtevaKartica({ racun }: { racun: RacunIgralcaDto }) {
  const odjemalec = useQueryClient()
  const igralci = useQuery({ queryKey: ['igralci'], queryFn: igralciApi.seznam })
  const [izbran, nastaviIzbranega] = useState<number | null>(
    racun.predlogi.find((p) => !p.zeImaRacun)?.idIgralec ?? null,
  )
  const [zavrnitev, nastaviZavrnitev] = useState(false)

  /* Predlogi so le bližnjica — če se priimek ne ujema (drugačen zapis,
     šumniki, dekliški priimek), mora biti mogoče izbrati kogarkoli. Zato jih
     polje ponudi, preden je kaj vpisano, iskanje pa teče čez vse igralce
     (predlogi ostanejo na vrhu zadetkov). */
  const predlagani = useMemo<MoznostIzbirnika[]>(
    () =>
      racun.predlogi.map((p) => ({
        id: p.idIgralec,
        ime: p.polnoIme,
        podrobnost: [p.klub, p.zeImaRacun ? 'že ima dostop' : 'predlog po priimku']
          .filter(Boolean)
          .join(' · '),
        onemogocena: p.zeImaRacun,
      })),
    [racun.predlogi],
  )
  const moznosti = useMemo<MoznostIzbirnika[]>(() => {
    const idjiPredlogov = new Set(racun.predlogi.map((p) => p.idIgralec))
    return [
      ...predlagani,
      ...(igralci.data ?? [])
        .filter((i) => !idjiPredlogov.has(i.id))
        .map((i) => ({ id: i.id, ime: `${i.ime} ${i.priimek}`, podrobnost: i.klub?.ime ?? null })),
    ]
  }, [racun.predlogi, predlagani, igralci.data])

  const osvezi = () => odjemalec.invalidateQueries({ queryKey: ['racuni'] })
  const potrdi = useMutation({
    mutationFn: (idIgralec: number) => racuniApi.potrdi(racun.id, idIgralec),
    onSuccess: osvezi,
  })
  const zavrni = useMutation({
    mutationFn: () => racuniApi.zavrni(racun.id),
    onSuccess: () => {
      osvezi()
      nastaviZavrnitev(false)
    },
  })

  return (
    <div className={`racun racun--${racun.vloga}`}>
      <div className="racun__glava">
        <div>
          <span className="racun__uporabnik">
            {racun.prijavljenoIme} {racun.prijavljeniPriimek}
          </span>
          <span className="racun__podrobnost">
            {racun.email}
            {racun.klubZelja && ` · navedel klub: ${racun.klubZelja}`}
          </span>
        </div>
        <span className={`racun__vloga racun__vloga--${racun.vloga}`}>
          {OZNAKE_VLOGA[racun.vloga]}
        </span>
        <span className="racun__cas">{datum(racun.ustvarjenOb)}</span>
      </div>

      {racun.predlogi.length === 0 && (
        <p className="racun__namig">
          Med igralci ni nikogar s tem priimkom — morda je vpisan drugače. Poišči ga
          z vpisom imena.
        </p>
      )}

      <div className="obrazec__vrstica racun__izbira">
        <IskalniIzbirnik
          vObrazcu
          oznaka="Poveži z igralcem"
          namig="Vpiši ime igralca"
          moznosti={moznosti}
          brezIskanja={predlagani}
          izbrano={izbran}
          naIzbiro={nastaviIzbranega}
          naPraznjenje={() => nastaviIzbranega(null)}
        />
        <button
          className="gumb gumb--glavni"
          disabled={izbran === null || potrdi.isPending}
          onClick={() => izbran !== null && potrdi.mutate(izbran)}
        >
          Potrdi dostop
        </button>
        <button className="gumb gumb--nevaren" onClick={() => nastaviZavrnitev(true)}>
          Zavrni
        </button>
      </div>

      <SporociloNapake napaka={potrdi.error} />
      <SporociloNapake napaka={zavrni.error} />

      {zavrnitev && (
        <PotrditvenoOkno
          naslov="Zavrnitev zahteve"
          sporocilo={`Zavrnem zahtevo za ${racun.email}? Račun ostane (da se ista e-pošta ne registrira znova), a brez dostopa.`}
          besedaPotrditve="Zavrni"
          onPotrdi={() => zavrni.mutate()}
          onZapri={() => nastaviZavrnitev(false)}
        />
      )}
    </div>
  )
}

/* Potrditev organizatorja: mu dodeli (neobvezni) klub, po katerem soupravlja
   klubska tekmovanja. Za razliko od igralca ni povezave z zapisom v sifrantu. */
function ZahtevaOrganizator({ racun }: { racun: RacunIgralcaDto }) {
  const odjemalec = useQueryClient()
  const klubi = useQuery({ queryKey: ['klubi'], queryFn: klubiApi.seznam })
  const [idKlub, nastaviKlub] = useState<number | null>(null)
  const [zavrnitev, nastaviZavrnitev] = useState(false)

  /* Predizberi klub, ki ga je organizator navedel ob registraciji (ujemanje
     po imenu - DTO nosi le ime navedenega kluba). Samo enkrat, ko se klubi
     naložijo: sicer bi admin, ki polje izprazni (brez kluba), navedeni klub
     takoj dobil nazaj. */
  const predizbrano = useRef(false)
  useEffect(() => {
    if (predizbrano.current || !klubi.data) return
    predizbrano.current = true
    const najden = klubi.data.find((k) => k.ime === racun.klubZelja)
    if (najden) nastaviKlub(najden.id)
  }, [klubi.data, racun.klubZelja])

  const osvezi = () => odjemalec.invalidateQueries({ queryKey: ['racuni'] })
  const potrdi = useMutation({
    mutationFn: () => racuniApi.potrdiOrganizatorja(racun.id, idKlub),
    onSuccess: osvezi,
  })
  const zavrni = useMutation({
    mutationFn: () => racuniApi.zavrni(racun.id),
    onSuccess: () => {
      osvezi()
      nastaviZavrnitev(false)
    },
  })

  return (
    <div className="racun racun--ORGANIZATOR">
      <div className="racun__glava">
        <div>
          <span className="racun__uporabnik">
            {racun.prijavljenoIme} {racun.prijavljeniPriimek}
          </span>
          <span className="racun__podrobnost">
            {racun.email}
            {racun.klubZelja && ` · navedel klub: ${racun.klubZelja}`}
          </span>
        </div>
        <span className="racun__vloga racun__vloga--ORGANIZATOR">Organizator</span>
        <span className="racun__cas">{datum(racun.ustvarjenOb)}</span>
      </div>

      <p className="racun__namig">
        Dodeli klub, po katerem bo soupravljal tekmovanja. Brez kluba upravlja samo
        turnirje in lige, ki jih ustvari sam.
      </p>

      <div className="obrazec__vrstica racun__izbira">
        <IzbirnikKluba
          namig="Brez kluba · vpiši ime"
          klubi={klubi.data ?? []}
          izbrano={idKlub}
          naSpremembo={nastaviKlub}
        />
        <button
          className="gumb gumb--glavni"
          disabled={potrdi.isPending}
          onClick={() => potrdi.mutate()}
        >
          Potrdi organizatorja
        </button>
        <button className="gumb gumb--nevaren" onClick={() => nastaviZavrnitev(true)}>
          Zavrni
        </button>
      </div>

      <SporociloNapake napaka={potrdi.error} />
      <SporociloNapake napaka={zavrni.error} />

      {zavrnitev && (
        <PotrditvenoOkno
          naslov="Zavrnitev zahteve"
          sporocilo={`Zavrnem zahtevo za ${racun.email}? Račun ostane (da se ista e-pošta ne registrira znova), a brez dostopa.`}
          besedaPotrditve="Zavrni"
          onPotrdi={() => zavrni.mutate()}
          onZapri={() => nastaviZavrnitev(false)}
        />
      )}
    </div>
  )
}

function ObstojecaVrstica({ racun }: { racun: RacunIgralcaDto }) {
  const odjemalec = useQueryClient()
  /* Geslo, ki ga admin pravkar nastavi ali generira — prikaže se enkrat,
     saj ga strežnik shrani le kot zgostitev in ga kasneje ni več mogoče
     prebrati. */
  const [novoGeslo, nastaviNovoGeslo] = useState<string | null>(null)
  const [urejanje, nastaviUrejanje] = useState(false)
  const [vnos, nastaviVnos] = useState('')
  const [brisanje, nastaviBrisanje] = useState(false)

  const osvezi = () => odjemalec.invalidateQueries({ queryKey: ['racuni'] })
  const preklop = useMutation({
    mutationFn: () => racuniApi.nastaviAktiven(racun.id, !racun.aktiven),
    onSuccess: osvezi,
  })
  const nastavi = useMutation({
    mutationFn: () => racuniApi.nastaviGeslo(racun.id, vnos),
    onSuccess: () => {
      nastaviNovoGeslo(vnos)
      nastaviVnos('')
      nastaviUrejanje(false)
    },
  })
  const generiraj = useMutation({
    mutationFn: () => racuniApi.ponastaviGeslo(racun.id),
    onSuccess: (odgovor) => {
      nastaviNovoGeslo(odgovor.geslo)
      nastaviVnos('')
      nastaviUrejanje(false)
    },
  })
  const izbrisi = useMutation({
    mutationFn: () => racuniApi.izbrisi(racun.id),
    onSuccess: () => {
      osvezi()
      nastaviBrisanje(false)
    },
  })

  const prekratko = vnos.trim().length < 8

  return (
    <tr>
      <td>{racun.email}</td>
      <td>{OZNAKE_VLOGA[racun.vloga]}</td>
      <td>{racun.vloga === 'ORGANIZATOR' ? (racun.klub ?? '—') : (racun.imeIgralca ?? '—')}</td>
      <td>
        {OZNAKE_STATUSA_RACUNA[racun.status]}
        {!racun.aktiven && <span className="enanaena__vir">izklopljen</span>}
      </td>
      <td className="racun__dejanja">
        <button className="gumb gumb--majhen" onClick={() => preklop.mutate()}>
          {racun.aktiven ? 'Izklopi' : 'Vklopi'}
        </button>
        <button
          className="gumb gumb--majhen"
          onClick={() => {
            nastaviUrejanje((v) => !v)
            nastaviNovoGeslo(null)
          }}
        >
          {urejanje ? 'Prekliči' : 'Nastavi geslo'}
        </button>
        <button
          className="gumb gumb--majhen gumb--nevaren"
          onClick={() => nastaviBrisanje(true)}
        >
          Izbriši
        </button>

        {urejanje && (
          <div className="racun__geslo-ured">
            <input
              type="text"
              value={vnos}
              placeholder="novo geslo (vsaj 8 znakov)"
              autoComplete="off"
              onChange={(d) => nastaviVnos(d.target.value)}
              onKeyDown={(d) => {
                if (d.key === 'Enter' && !prekratko) nastavi.mutate()
              }}
            />
            <button
              className="gumb gumb--majhen gumb--glavni"
              disabled={prekratko || nastavi.isPending}
              onClick={() => nastavi.mutate()}
            >
              Nastavi
            </button>
            <button
              className="gumb gumb--majhen"
              disabled={generiraj.isPending}
              onClick={() => generiraj.mutate()}
            >
              Generiraj naključno
            </button>
          </div>
        )}

        <SporociloNapake napaka={nastavi.error} />
        <SporociloNapake napaka={generiraj.error} />

        {novoGeslo && (
          <div className="racun__geslo">
            Geslo za <strong>{racun.email}</strong>: <code>{novoGeslo}</code> — izroči ga
            igralcu, kasneje ga ni več mogoče prebrati.
          </div>
        )}

        {brisanje && (
          <PotrditvenoOkno
            naslov="Izbris računa"
            sporocilo={`Izbrišem račun ${racun.email}? E-pošta se s tem sprosti za novo registracijo. Igralec in njegove tekme ostanejo nedotaknjeni.`}
            besedaPotrditve="Izbriši"
            onPotrdi={() => izbrisi.mutate()}
            onZapri={() => nastaviBrisanje(false)}
          />
        )}
      </td>
    </tr>
  )
}

function datum(iso: string): string {
  const d = new Date(iso)
  return Number.isNaN(d.getTime()) ? '—' : d.toLocaleDateString('sl-SI')
}
