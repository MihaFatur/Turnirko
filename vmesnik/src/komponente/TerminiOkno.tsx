/* Termini kol lige: kdaj se igra katero kolo.

   Zakaj svoje okno in ne del pravil lige: pravila (format, nizi, točkovanje) se
   po generiranju razporeda zaklenejo, ker bi popravek razveljavil odigrano —
   termin pa se mora dati popraviti tudi sredi sezone (kolo se prestavi). Poleg
   tega se pravila vpišejo ob ustvarjanju lige, ko ekip (in s tem števila kol)
   še ni; seznam vseh kol je mogoč šele tu, po žrebu.

   Obrazec lige zato nosi le seme (prvo kolo + razmik), iz katerega žreb
   izračuna datume; to okno je ročni popravek. Prestavljeno kolo NE premakne
   naslednjih — ta so že objavljena in bi jih tiho zamaknilo.

   Termin je last kola in ne posameznega srečanja: kolo se odigra en dan, zato
   vsa njegova srečanja dobijo isti začetek. Ura je neobvezna; prazna se shrani
   kot 00:00 in v razporedu ne izpiše. */
import { useMemo, useState, type FormEvent } from 'react'
import { useMutation } from '@tanstack/react-query'

import { ligeApi } from '../api/zahteve'
import type { LigaDto, SrecanjeDto } from '../api/tipi'
import { ModalnoOkno } from './ModalnoOkno'
import { SporociloNapake } from './SporociloNapake'

interface Lastnosti {
  liga: LigaDto
  /* Vsa srečanja lige — iz njih se prebere, katera kola obstajajo in kdaj so. */
  srecanja: SrecanjeDto[]
  onZapri: () => void
  onShranjeno: () => void
}

interface VnosKola {
  kolo: number
  datum: string
  ura: string
  odigrano: boolean
}

const PRIVZET_RAZMIK = 7

export function TerminiOkno({ liga, srecanja, onZapri, onShranjeno }: Lastnosti) {
  const zacetna = useMemo(() => zacetniVnosi(srecanja), [srecanja])
  const [vnosi, nastaviVnose] = useState<VnosKola[]>(zacetna)
  /* Polnilo po razmiku: privzetka sta termin prvega kola oz. seme lige, da
     najpogostejši popravek (»vse skupaj teden naprej«) ne zahteva vpisa. */
  const [odDatuma, nastaviOdDatuma] = useState(
    () => zacetna[0]?.datum || (liga.zacetekPrvegaKola?.slice(0, 10) ?? ''),
  )
  const [odUre, nastaviOdUre] = useState(
    () => zacetna[0]?.ura || vpisanaUra(liga.zacetekPrvegaKola),
  )
  const [razmik, nastaviRazmik] = useState(liga.razmikDni ?? PRIVZET_RAZMIK)

  const shranjevanje = useMutation({
    mutationFn: () =>
      ligeApi.termini(liga.id, {
        kola: vnosi.map((v) => ({
          kolo: v.kolo,
          zacetek: v.datum ? `${v.datum}T${v.ura || '00:00'}` : null,
        })),
      }),
    onSuccess: () => {
      onShranjeno()
      onZapri()
    },
  })

  function obOddaji(dogodek: FormEvent) {
    dogodek.preventDefault()
    shranjevanje.mutate()
  }

  function uredi(kolo: number, popravek: Partial<VnosKola>) {
    nastaviVnose((prej) => prej.map((v) => (v.kolo === kolo ? { ...v, ...popravek } : v)))
  }

  /* Napolni vsa kola po razmiku od vpisanega začetka. Prepiše tudi že vpisane
     termine — to je namen gumba; posamezno kolo se popravi v vrstici pod njim. */
  function napolni() {
    if (!odDatuma) return
    nastaviVnose((prej) =>
      prej.map((v, i) => ({ ...v, datum: prestej(odDatuma, i * razmik), ura: odUre })),
    )
  }

  function pobrisi() {
    nastaviVnose((prej) => prej.map((v) => ({ ...v, datum: '', ura: '' })))
  }

  /* Okno je namenoma navadne širine (brez "siroko"): vrstica nosi le oznako,
     datum in uro - v širokem oknu bi se datumsko polje raztegnilo čez pol
     zaslona in bi izgledalo kot pomota. */
  return (
    <ModalnoOkno naslov="Termini kol" onZapri={onZapri}>
      <form className="obrazec" onSubmit={obOddaji}>
        <p className="obvestilo">
          Termin velja za vsa srečanja kola. Urejaš jih lahko tudi med sezono.
        </p>

        <fieldset className="obrazec__skupina">
          <legend>Napolni po razmiku</legend>
          <div className="termini__polnilo">
            <label className="obrazec__polje">
              <span>Prvo kolo</span>
              <input type="date" value={odDatuma}
                onChange={(d) => nastaviOdDatuma(d.target.value)} />
            </label>
            <label className="obrazec__polje">
              <span>Ura</span>
              <input type="time" value={odUre} onChange={(d) => nastaviOdUre(d.target.value)} />
            </label>
            <label className="obrazec__polje">
              <span>Na koliko dni</span>
              <input type="number" min={1} max={365} value={razmik}
                onChange={(d) => nastaviRazmik(Number(d.target.value))} />
            </label>
          </div>
          <div className="termini__polnilo-gumbi">
            <button type="button" className="gumb gumb--majhen" disabled={!odDatuma}
              onClick={napolni}>
              Napolni vsa kola
            </button>
            <button type="button" className="gumb gumb--majhen" onClick={pobrisi}>
              Zbriši vse
            </button>
          </div>
          <p className="namig">
            Polnilo prepiše vse datume spodaj. Prestavitev enega kola ostalih ne premakne.
          </p>
        </fieldset>

        <div className="termini">
          {vnosi.map((v) => (
            <div key={v.kolo} className="termini__vrstica">
              <span className="termini__kolo">
                {v.kolo}. kolo
                {/* Odigrano kolo se sme popraviti (napačen vpis), a naj bo
                    vidno, da gre za preteklost in ne za načrt. */}
                {v.odigrano && <span className="termini__oznaka">odigrano</span>}
              </span>
              <label className="termini__polje">
                <span className="samo-za-bralnik">Datum {v.kolo}. kola</span>
                <input type="date" value={v.datum}
                  onChange={(d) => uredi(v.kolo, { datum: d.target.value })} />
              </label>
              <label className="termini__polje termini__polje--ura">
                <span className="samo-za-bralnik">Ura {v.kolo}. kola</span>
                <input type="time" value={v.ura} disabled={!v.datum}
                  onChange={(d) => uredi(v.kolo, { ura: d.target.value })} />
              </label>
            </div>
          ))}
        </div>

        <SporociloNapake napaka={shranjevanje.error} />
        <div className="obrazec__gumbi">
          <button type="button" className="gumb" onClick={onZapri}>Prekliči</button>
          <button type="submit" className="gumb gumb--glavni" disabled={shranjevanje.isPending}>
            Shrani termine
          </button>
        </div>
      </form>
    </ModalnoOkno>
  )
}

/* Kola iz razporeda s termini, kot so zdaj. Termin kola vzamemo iz prvega
   srečanja, ki ga ima — vsa srečanja kola nosijo isti čas (piše ga zaledje). */
function zacetniVnosi(srecanja: SrecanjeDto[]): VnosKola[] {
  const kola = [...new Set(srecanja.map((s) => s.kolo))].sort((a, b) => a - b)
  return kola.map((kolo) => {
    const vKolu = srecanja.filter((s) => s.kolo === kolo)
    const zacetek = vKolu.find((s) => s.predvidenZacetek)?.predvidenZacetek ?? null
    return {
      kolo,
      datum: zacetek?.slice(0, 10) ?? '',
      ura: vpisanaUra(zacetek),
      odigrano: vKolu.every((s) => s.status === 'KONCANO'),
    }
  })
}

/* Ura iz ISO datuma-časa za polje obrazca. 00:00 pomeni »ura ni določena«,
   zato se v polje ne vpiše nazaj — sicer bi shranjevanje iz nje naredilo
   vpisano polnoč in razpored bi jo začel izpisovati. */
function vpisanaUra(iso: string | null | undefined): string {
  const ura = iso?.slice(11, 16) ?? ''
  return ura === '00:00' ? '' : ura
}

/* Datum + n dni, oboje kot "YYYY-MM-DD". Računamo v UTC, ker bi lokalni čas ob
   prehodu na poletni/zimski čas datum premaknil za dan. */
function prestej(datum: string, dni: number): string {
  const [leto, mesec, dan] = datum.split('-').map(Number)
  if (!leto || !mesec || !dan) return datum
  const cas = new Date(Date.UTC(leto, mesec - 1, dan))
  cas.setUTCDate(cas.getUTCDate() + dni)
  return cas.toISOString().slice(0, 10)
}
