/* Okno "Uredi izbor lig": tu si prijavljeni sestavi sklop "Moje lige".

   Zakaj okno in ne stran /lige: izbor je nastavitev domače strani in ne pot v
   ligo — po odkljukanju gledalec ostane tam, kjer je bil, in takoj vidi, kaj
   se je spremenilo. Seznam lig na /lige je tudi neprimeren nosilec preklopov,
   ker je vsaka vrstica povezava v ligo.

   Vsak klik se shrani takoj (gumba "Shrani" ni): preklop je ena zahteva in se
   optimistično prevesi, zato bi zbiranje sprememb do konca le dodalo stanje,
   ki se lahko izgubi. Gumb na dnu okno samo zapre.

   Seznam mora zdržati zgodovino (uvožene sezone naštejejo lige v stotinah),
   zato:
   - lige, ki jih uporabnik že spremlja, so v svoji skupini na vrhu in v
     vrstnem redu računa — in ostanejo tam, dokler je okno odprto, tudi če jih
     odkljuka (vrstica pod prstom ne sme odskočiti drugam);
   - zaključene lige so pospravljene za gumbom, ker jih je največ in jih
     gledalec redko išče;
   - iskanje teče čez ime in sezono in odpre tudi zaključene. */
import { useEffect, useMemo, useState } from 'react'
import { useQuery } from '@tanstack/react-query'

import { ligeApi } from '../api/zahteve'
import type { LigaDto } from '../api/tipi'
import { OZNAKE_SPOL_KATEGORIJA, OZNAKE_STATUS_TEKMOVANJA } from '../api/tipi'
import { ModalnoOkno } from './ModalnoOkno'
import { NapakaPoizvedbe } from './NapakaPoizvedbe'
import { SporociloNapake } from './SporociloNapake'
import { useSpremljanjeLig } from '../pomozno/spremljaneLige'

export function IzborLigOkno({ onZapri }: { onZapri: () => void }) {
  const lige = useQuery({ queryKey: ['lige'], queryFn: ligeApi.seznam })
  const { spremljane, nalaganje, preklopi, napaka } = useSpremljanjeLig()

  const [iskanje, nastaviIskanje] = useState('')
  const [kaziZakljucene, nastaviKaziZakljucene] = useState(false)

  /* Posnetek izbora ob odprtju okna. Živ seznam bi vrstico ob odkljukanju
     preselil v skupino njenega statusa — prst bi ostal nad drugo ligo. */
  const [zacetne, nastaviZacetne] = useState<number[] | null>(null)
  useEffect(() => {
    if (zacetne === null && !nalaganje) nastaviZacetne(spremljane)
  }, [zacetne, nalaganje, spremljane])

  const vse = useMemo(() => lige.data ?? [], [lige.data])
  const iskano = iskanje.trim().toLocaleLowerCase('sl')

  const najdene = useMemo(
    () =>
      iskano === ''
        ? vse
        : vse.filter(
            (l) =>
              l.ime.toLocaleLowerCase('sl').includes(iskano) ||
              (l.sezona ?? '').toLocaleLowerCase('sl').includes(iskano),
          ),
    [vse, iskano],
  )

  /* Skupina "Spremljam" sledi vrstnemu redu računa (tak je tudi na domači
     strani), ostale so po sezoni navzdol in nato po imenu. */
  const moje = useMemo(
    () =>
      (zacetne ?? [])
        .map((id) => najdene.find((l) => l.id === id))
        .filter((l): l is LigaDto => l !== undefined),
    [zacetne, najdene],
  )

  const ostale = useMemo(() => {
    const zeNasteta = new Set(zacetne ?? [])
    return najdene.filter((l) => !zeNasteta.has(l.id)).sort(poSezoniInImenu)
  }, [najdene, zacetne])

  const vTeku = ostale.filter((l) => l.status === 'V_TEKU')
  const priprava = ostale.filter((l) => l.status === 'PRIPRAVA')
  const zakljucene = ostale.filter((l) => l.status === 'ZAKLJUCEN')
  /* Iskanje odpre tudi zaključene: kdor išče po imenu, jih sicer ne bi našel. */
  const kaziZ = kaziZakljucene || iskano !== ''

  const pripravljeno = zacetne !== null && lige.data !== undefined
  const skupine: { naslov: string; lige: LigaDto[]; sStatusom?: boolean }[] = [
    { naslov: 'Spremljam', lige: moje, sStatusom: true },
    { naslov: 'V teku', lige: vTeku },
    { naslov: 'V pripravi', lige: priprava },
    ...(kaziZ ? [{ naslov: 'Zaključene', lige: zakljucene }] : []),
  ]

  return (
    <ModalnoOkno naslov="Moje lige" nadnaslov="Uredi izbor" onZapri={onZapri}>
      {/* Kratko: okno mora na telefonu obdržati prostor za seznam. */}
      <p className="modal__podnaslov">
        Označene lige so na tvoji domači strani. Izbor se shrani sproti.
      </p>

      <label className="izbor-lig__iskanje">
        <span className="samo-za-bralnik">Poišči ligo</span>
        <input
          type="search"
          value={iskanje}
          placeholder="Poišči po imenu ali sezoni"
          onChange={(dogodek) => nastaviIskanje(dogodek.target.value)}
        />
      </label>

      <SporociloNapake napaka={napaka} />
      <NapakaPoizvedbe poizvedba={lige} kaj="lig" />
      {!pripravljeno && !lige.error && <p className="obvestilo">Nalaganje …</p>}

      {pripravljeno && vse.length === 0 && (
        <p className="obvestilo">Lig še ni. Ko bo prva ustvarjena, jo boš lahko spremljal.</p>
      )}

      {pripravljeno && vse.length > 0 && najdene.length === 0 && (
        <p className="obvestilo">Za »{iskanje.trim()}« ni lige.</p>
      )}

      {/* Prazen seznam se ne izriše: dve črti brez vsebine pod sporočilom
          "za ... ni lige" nista prazno stanje, ampak smet. */}
      {pripravljeno && najdene.length > 0 && (
        <div className="izbor-lig">
          {skupine.map((s) => (
            <SkupinaLig
              key={s.naslov}
              naslov={s.naslov}
              lige={s.lige}
              sStatusom={s.sStatusom}
              spremljane={spremljane}
              naPreklop={preklopi}
            />
          ))}

          {!kaziZ && zakljucene.length > 0 && (
            <button
              type="button"
              className="gumb gumb--majhen izbor-lig__vec"
              onClick={() => nastaviKaziZakljucene(true)}
            >
              Pokaži zaključene ({zakljucene.length})
            </button>
          )}
        </div>
      )}

      <div className="izbor-lig__noga">
        <span className="izbor-lig__stanje">
          {spremljane.length === 0 ? 'Nobena liga ni izbrana' : `Izbranih: ${spremljane.length}`}
        </span>
        <button type="button" className="gumb gumb--glavni" onClick={onZapri}>
          Končano
        </button>
      </div>
    </ModalnoOkno>
  )
}

function SkupinaLig({
  naslov,
  lige,
  sStatusom = false,
  spremljane,
  naPreklop,
}: {
  naslov: string
  lige: LigaDto[]
  /* Skupina "Spremljam" meša statuse, zato ga vrstica pove sama. */
  sStatusom?: boolean
  spremljane: number[]
  naPreklop: (idLiga: number) => void
}) {
  if (lige.length === 0) return null
  return (
    <div className="izbor-lig__skupina">
      <h3 className="izbor-lig__naslov">
        {naslov}
        <span className="izbor-lig__stevec">{lige.length}</span>
      </h3>
      {lige.map((liga) => {
        const izbrana = spremljane.includes(liga.id)
        return (
          <button
            type="button"
            className={'izbor-lig__vrstica' + (izbrana ? ' izbor-lig__vrstica--izbrana' : '')}
            aria-pressed={izbrana}
            onClick={() => naPreklop(liga.id)}
            key={liga.id}
          >
            {/* Kvadratek je isti znak kot v sklopu "Moje lige"; stanje bere
                bralnik zaslona iz aria-pressed, zato je znak samo slika. */}
            <span className={'kljukica' + (izbrana ? ' kljukica--polna' : '')} aria-hidden="true" />
            <span className="izbor-lig__telo">
              <span className="izbor-lig__ime">{liga.ime}</span>
              <span className="izbor-lig__meta">{opis(liga, sStatusom)}</span>
            </span>
          </button>
        )
      })}
    </div>
  )
}

/* Sezona navzdol (najnovejša najprej), znotraj nje po imenu. Sezona je prosto
   besedilo, zato je primerjava besedilna — »2025/26« pride pred »2024/25«. */
function poSezoniInImenu(a: LigaDto, b: LigaDto): number {
  return (b.sezona ?? '').localeCompare(a.sezona ?? '', 'sl') || a.ime.localeCompare(b.ime, 'sl')
}

function opis(liga: LigaDto, sStatusom: boolean): string {
  return [
    liga.sezona,
    OZNAKE_SPOL_KATEGORIJA[liga.spolKategorija].toLowerCase(),
    sStatusom ? OZNAKE_STATUS_TEKMOVANJA[liga.status].toLowerCase() : null,
  ]
    .filter(Boolean)
    .join(' · ')
}
