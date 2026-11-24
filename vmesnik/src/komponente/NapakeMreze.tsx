/* Napake mreže števk (branje listkov F6.6, samo admin; v razdelku Mreža števk).

   Katere števke mreža v uporabi bere narobe: na preizkusnih števkah (vsak peti
   list vadnice ali listek, teh se ni učila) po števkah 0–9, z vgrajeno za
   primerjavo, in vse narobe prebrane s sliko. Klik na števko v tabeli pokaže samo
   njene napake. Sumljive oznake so učne števke, ki jih mreža kljub učenju odločno
   bere drugače - pogosto napačno napisana števka v vrstici vadnice ali narobe
   izrezano okence; take admin izloči iz učenja.

   Izračun teče na strežniku ob ogledu (vse števke skozi mrežo), zato šele na klik
   in ne ob vsakem fokusu okna; po učenju ali vrnitvi mreže ga MrezeStevk osveži. */
import { useState, type ReactNode } from 'react'
import { Link } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'

import type { NapakeMrezeDto, StevkaNapakeDto } from '../api/tipi'
import { meritevBranjaApi } from '../api/zahteve'
import { brezStevke, delez, delezOd, zamenjaveBesedilo } from '../pomozno/branjeListkov'
import { oznakaLista } from '../pomozno/vadnica'
import { SlikaStevke } from './SlikaStevke'
import { SporociloNapake } from './SporociloNapake'

export const KLJUC_NAPAK = ['napake-mreze']

const OZNAKA_VIRA: Record<NapakeMrezeDto['poViru'][number]['vir'], string> = {
  LISTEK: 'listki',
  VADNICA: 'vadnica',
  OSTALO: 'izbrisana branja',
}

export function NapakeMreze() {
  const odjemalec = useQueryClient()
  const [odprto, nastaviOdprto] = useState(false)
  const [izbrana, nastaviIzbrano] = useState<number | null>(null)
  const pregled = useQuery({
    queryKey: KLJUC_NAPAK,
    queryFn: meritevBranjaApi.napake,
    enabled: odprto,
    staleTime: Infinity,
  })
  const izloci = useMutation({
    mutationFn: (id: number) => meritevBranjaApi.izlociStevko(id),
    onSuccess: (_, id) => {
      odjemalec.setQueryData<NapakeMrezeDto>(KLJUC_NAPAK, (p) => (p ? brezStevke(p, id) : p))
      // števec »Števk v učenju« zgoraj
      void odjemalec.invalidateQueries({ queryKey: ['mreze-stevk'], exact: true })
    },
  })
  const izlociPoPotrditvi = (s: StevkaNapakeDto) => {
    if (
      window.confirm(
        `Izločim to števko iz učenja? Napisano ${s.vrednost}, mreža bere ${s.prebrano}. ` +
          'Izloči samo napačno oznako ali narobe izrezano okence, ne grdo napisane prave števke.',
      )
    ) {
      izloci.mutate(s.id)
    }
  }

  const p = pregled.data
  return (
    <div className="napake-mreze">
      <h3 className="meritev-branja__podnaslov">Napake mreže</h3>
      <p className="meritev-branja__besedilo">
        Katere števke mreža bere narobe. Šteje samo preizkusne števke (vsak peti list vadnice ali listek), ki se jih
        mreža ni učila. Pod vsako sliko je napisana števka → kar mreža prebere, njena gotovost in list vadnice.
      </p>
      {!odprto && (
        <div className="meritev-branja__gumbi">
          <button type="button" className="gumb" onClick={() => nastaviOdprto(true)}>
            Pokaži napake
          </button>
        </div>
      )}
      <SporociloNapake napaka={pregled.error ?? izloci.error} />
      {odprto && pregled.isPending && <p className="obvestilo">Preverjam vse števke …</p>}
      {p && (
        <>
          <PoStevkah p={p} izbrana={izbrana} onIzberi={nastaviIzbrano} />

          <Galerija
            naslov={`Narobe prebrane (${p.napakVseh})`}
            stevke={izbrana === null ? p.napake : p.napake.filter((s) => s.vrednost === izbrana)}
            prazno={
              p.napakVseh === 0
                ? 'Mreža vse preizkusne števke prebere prav.'
                : `Napisano ${izbrana}: mreža vse prebere prav.`
            }
            izlocam={izloci.isPending ? izloci.variables : undefined}
            onIzloci={izlociPoPotrditvi}
          >
            {izbrana !== null && (
              <p className="namig">
                Samo napisane {izbrana}.{' '}
                <button type="button" className="meritev-branja__izbira" onClick={() => nastaviIzbrano(null)}>
                  Pokaži vse
                </button>
              </p>
            )}
            {p.napakVseh > p.napake.length && (
              <p className="namig">Za vsako števko je prikazanih največ {p.najvecNaStevko} napak.</p>
            )}
          </Galerija>

          <Galerija
            naslov={`Sumljive oznake (${p.sumljive.length})`}
            stevke={p.sumljive}
            prazno="Mreža vse učne števke bere tako, kot so označene, ali pa ni prepričana."
            izlocam={izloci.isPending ? izloci.variables : undefined}
            onIzloci={izlociPoPotrditvi}
          >
            <p className="meritev-branja__besedilo">
              Učne števke, ki jih mreža kljub učenju odločno (vsaj {delez(p.pragSumljive)}) bere drugače, kot so
              označene: pogosto je v vrstici vadnice napisana druga števka ali je okence narobe izrezano. Drugače bere{' '}
              {p.drugacnihUcnih} od {p.ucnih} učnih števk.
            </p>
          </Galerija>

          <p className="namig">
            »Izloči« števko izbriše iz učenja; že naučena mreža ostane. Izloči samo napačno oznako ali narobe izrezano
            okence. Grdo napisana prava števka naj ostane, sicer je preizkus videti boljši, kot je.
          </p>
          <div className="meritev-branja__gumbi">
            <button
              type="button"
              className="gumb gumb--majhen"
              disabled={pregled.isFetching}
              onClick={() => void pregled.refetch()}
            >
              {pregled.isFetching ? 'Preverjam …' : 'Preveri znova'}
            </button>
          </div>
        </>
      )}
    </div>
  )
}

/* Tabela po števkah 0–9: koliko preizkusnih, delež prav in najpogostejše zamenjave.
   Števka v prvem stolpcu je gumb, ki galerijo omeji nanjo. */
function PoStevkah({
  p,
  izbrana,
  onIzberi,
}: {
  p: NapakeMrezeDto
  izbrana: number | null
  onIzberi: (stevka: number | null) => void
}) {
  const primerjava = p.vUporabi !== null
  const pravVgrajena = p.poStevkah.reduce((a, s) => a + s.pravVgrajena, 0)
  const pravVUporabi = p.poStevkah.reduce((a, s) => a + s.pravVUporabi, 0)
  return (
    <>
      <p className="meritev-branja__besedilo">
        Preizkusnih števk {p.preizkusnih}.{' '}
        {primerjava
          ? `Različica ${p.vUporabi} prebere prav ${delezOd(pravVUporabi, p.preizkusnih)}, vgrajena ${delezOd(pravVgrajena, p.preizkusnih)}.`
          : `Vgrajena mreža prebere prav ${delezOd(pravVUporabi, p.preizkusnih)}.`}
      </p>
      {/* Po viru: skupni delež skrije, da so preizkusne večinoma z vadnice (skrbno pisane v
          okenca), na listku pa pišejo med tekmo (diagnostika 24. 11. 2026). */}
      <p className="meritev-branja__besedilo">
        Po viru:{' '}
        {p.poViru
          .filter((v) => v.preizkusnih > 0)
          .map((v) => `${OZNAKA_VIRA[v.vir]} ${delezOd(v.pravVUporabi, v.preizkusnih)} (${v.preizkusnih})`)
          .join(' · ') || 'ni preizkusnih števk'}
        .
      </p>
      <div className="tabela-ovoj">
        <table className={`tabela napake-mreze__tabela${primerjava ? ' napake-mreze__tabela--primerjava' : ''}`}>
          <thead>
            <tr>
              <th>Števka</th>
              <th className="meritev-branja__desno napake-mreze__stevk">Števk</th>
              {primerjava && <th className="meritev-branja__desno">Vgrajena</th>}
              <th className="meritev-branja__desno">{primerjava ? 'V uporabi' : 'Prav'}</th>
              <th>Prebrana kot</th>
            </tr>
          </thead>
          <tbody>
            {p.poStevkah.map((s) => (
              <tr key={s.stevka} className={s.stevka === izbrana ? 'napake-mreze__izbrana' : undefined}>
                <td>
                  <button
                    type="button"
                    className="meritev-branja__izbira napake-mreze__stevilka"
                    aria-pressed={s.stevka === izbrana}
                    aria-label={`Pokaži napake števke ${s.stevka}`}
                    onClick={() => onIzberi(s.stevka === izbrana ? null : s.stevka)}
                  >
                    {s.stevka}
                  </button>
                </td>
                <td className="meritev-branja__stevilo napake-mreze__stevk">{s.preizkusnih}</td>
                {primerjava && <td className="meritev-branja__stevilo">{delezOd(s.pravVgrajena, s.preizkusnih)}</td>}
                <td className="meritev-branja__stevilo">{delezOd(s.pravVUporabi, s.preizkusnih)}</td>
                <td className="meritev-branja__stevilo napake-mreze__zamenjave">{zamenjaveBesedilo(s.zamenjave)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </>
  )
}

/* Števke s sliko: napisano → prebrano, verjetnost in list vadnice. */
function Galerija({
  naslov,
  stevke,
  prazno,
  izlocam,
  onIzloci,
  children,
}: {
  naslov: string
  stevke: StevkaNapakeDto[]
  prazno: string
  izlocam: number | undefined
  onIzloci: (s: StevkaNapakeDto) => void
  children?: ReactNode
}) {
  return (
    <>
      <h4 className="napake-mreze__naslov">{naslov}</h4>
      {children}
      {stevke.length === 0 ? (
        <p className="namig">{prazno}</p>
      ) : (
        <ul className="napake-mreze__galerija">
          {stevke.map((s) => (
            <li key={s.id} className="napake-mreze__stevka">
              <SlikaStevke slika={s.slika} className="napake-mreze__slika" />
              <span className="napake-mreze__branje">
                {s.vrednost} → {s.prebrano}
              </span>
              <span className="napake-mreze__vir">
                {delez(s.verjetnost)}
                <br />
                {s.idVadnica !== null ? (
                  <Link to={`/vadnica/${s.idVadnica}`}>{oznakaLista(s.idVadnica)}</Link>
                ) : (
                  'listek'
                )}
              </span>
              <button
                type="button"
                className="gumb gumb--majhen napake-mreze__izloci"
                disabled={izlocam !== undefined}
                onClick={() => onIzloci(s)}
              >
                {izlocam === s.id ? 'Izločam …' : 'Izloči'}
              </button>
            </li>
          ))}
        </ul>
      )}
    </>
  )
}
