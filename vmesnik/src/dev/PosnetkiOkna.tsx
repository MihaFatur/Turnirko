/* Prikazni harness za posnetke prenove »Termini kol« in »Prehodi in piramida«
   (design_handoff_termini_prehodi). Samo za razvoj: /dev/posnetki, naloži se leno
   in samo, kadar import.meta.env.DEV (glej App.tsx).

   Brez prijave in brez omrežja: podatki so iz primerPodatki.ts (enaki kot v
   predaji), shranjevanje pa je no-op, da gumb »Shrani« ne pošlje ničesar
   strežniku.

   Parametri:
     okno=termini            okno Termini kol (2a)
     okno=prehodi            okno Prehodi in piramida (2c)
     okno=obrazec-termini    polje Termini kol v obrazcu nove lige (2b), 512 px
     ure=1                   liga z dvema uri kola (vec_ur_na_kolo) */
import { useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'

import { ligeApi } from '../api/zahteve'
import type { LigaDto, SrecanjeDto } from '../api/tipi'
import { PrehodiOkno } from '../komponente/PrehodiOkno'
import { TerminiOkno } from '../komponente/TerminiOkno'
import { TerminiPolja } from '../komponente/TerminiPolja'
import { ENA_URA, LIGE, VEC_UR, type VzorecKola } from './primerPodatki'

/* Shranjevanje tu ne sme nikamor. */
ligeApi.termini = async () => []
ligeApi.prehodi = async () => ({} as LigaDto)

const ID_LIGE = 100

/* Liga B, ki se ureja; ure in razmik sta odvisna od variante. */
function ligaZaPrikaz(v: VzorecKola): LigaDto {
  return {
    id: ID_LIGE,
    ime: 'Savinja liga B',
    sezona: '2026/27',
    razmikDni: v.ure ? 14 : 7,
    zacetekPrvegaKola: `${v.kola[0][1]}T${v.ure?.[0] ?? '18:00'}`,
    ureSrecanj: v.ure,
    idVisjaLiga: 1,
    visjaLigaIme: 'Savinja liga A',
    stNapreduje: 2,
    stIzpade: 2,
    stKvalifikacijeGor: 1,
    stKvalifikacijeDol: 1,
  } as unknown as LigaDto
}

/* Srečanja v obliki, kakršno vrača strežnik (SrecanjeDto). Datum srečanja je
   datum njegovega kola, ura pa njegova — prestavljeno srečanje se tako igra na
   dan kola, v katerem stoji. */
function srecanjaZaPrikaz(v: VzorecKola): SrecanjeDto[] {
  const datumKola = new Map(v.kola.map((k) => [k[0], k[1]] as [number, string]))
  return v.srecanja.map((s) => {
    const [id, domaci, gost, izvirnoKolo, kolo, uraVKolu, ura, , razlog, status] = s
    return {
      id,
      idLiga: ID_LIGE,
      idTurnir: null,
      idDogodek: null,
      idTekma: null,
      kolo,
      idEkipaDomaci: 0,
      domaci,
      idEkipaGost: 0,
      gost,
      dobljeneDomaci: 0,
      dobljeneGost: 0,
      status,
      predvidenZacetek: `${datumKola.get(kolo)}T${ura}`,
      uraVKolu: v.ure ? uraVKolu : 0,
      idSerija: null,
      krogKoncnice: null,
      tekmaVSeriji: null,
      brezBoja: false,
      prenesen: false,
      izvirnoKolo,
      cakaNaTermin: false,
      razlogPrestavitve: razlog || null,
    } as unknown as SrecanjeDto
  })
}

/* Polje v obrazcu nove lige z začetnim stanjem iz predaje: datum 2026-10-03,
   ura 18:00, razmik 7, eno srečanje. Ure za več srečanj so pripravljene v
   stanju, da klik na »Več srečanj« pokaže 18:00, 19:30, 20:30. */
function ObrazecTermini() {
  const [datum, nastaviDatum] = useState('2026-10-03')
  const [ura, nastaviUro] = useState('18:00')
  const [razmik, nastaviRazmik] = useState(7)
  const [poMeri, nastaviPoMeri] = useState(false)
  const [srecanj, nastaviSrecanj] = useState(1)
  const [ure, nastaviUre] = useState(['18:00', '19:30', '20:30', '', '', '', '', '', '', ''])
  return (
    <TerminiPolja
      datum={datum} naDatum={nastaviDatum}
      ura={ura} naUro={nastaviUro}
      razmik={razmik} naRazmik={nastaviRazmik}
      poMeri={poMeri} naPoMeri={nastaviPoMeri}
      srecanj={srecanj} naSrecanj={nastaviSrecanj}
      ure={ure}
      naUroSrecanja={(mesto, u) => nastaviUre((prej) => prej.map((x, i) => (i === mesto ? u : x)))}
    />
  )
}

export default function PosnetkiOkna() {
  const [parametri] = useSearchParams()
  const okno = parametri.get('okno') ?? 'termini'
  const v = parametri.get('ure') === '1' ? VEC_UR : ENA_URA
  const [odjemalec] = useState(() => new QueryClient())
  const naZapri = () => {}

  let vsebina
  if (okno === 'prehodi') {
    const liga = ligaZaPrikaz(ENA_URA)
    const vse = [...LIGE, liga] as unknown as LigaDto[]
    vsebina = <PrehodiOkno liga={liga} vse={vse} onZapri={naZapri} onShranjeno={naZapri} />
  } else if (okno === 'obrazec-termini') {
    /* Referenca meri skupino 510 px: okno 560 px minus obrobi in odmika 24 px. */
    vsebina = <div style={{ width: 510 }}><ObrazecTermini /></div>
  } else {
    vsebina = (
      <TerminiOkno liga={ligaZaPrikaz(v)} srecanja={srecanjaZaPrikaz(v)}
        onZapri={naZapri} onShranjeno={naZapri} />
    )
  }

  return <QueryClientProvider client={odjemalec}>{vsebina}</QueryClientProvider>
}
