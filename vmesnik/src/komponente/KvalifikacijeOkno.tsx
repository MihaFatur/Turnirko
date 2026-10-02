/* Nastanek kvalifikacij med višjo in nižjo ligo (V41).

   Kdo igra, odloči strežnik iz lestvic obeh lig (cone kvalifikacij) — okno
   pokaže njegov predlog: križne pare oz. udeležence male lige in ovire, zaradi
   katerih kvalifikacij še ni mogoče ustvariti (redni del ni odigran, prehodi
   niso vpisani …). Organizator izbere samo način igranja in ime: pravila se od
   lige do lige razlikujejo (Savinja liga ena tekma, SNTL serija na dve zmagi).

   Kvalifikacije so svoja liga s kopijami ekip — zato po nastanku okno odpelje
   nanjo: tam se vpisujejo termini in zapisniki. */
import { useState, type FormEvent } from 'react'
import { useNavigate } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'

import { ligeApi } from '../api/zahteve'
import type { KvalifikacijePredlogDto, LigaDto, NacinKvalifikacij, UdelezenecKvalifikacij } from '../api/tipi'
import { OZNAKE_NACINA_KVALIFIKACIJ, RAZPORED_FORMATA } from '../api/tipi'
import { ModalnoOkno } from './ModalnoOkno'
import { NapakaPoizvedbe } from './NapakaPoizvedbe'
import { SporociloNapake } from './SporociloNapake'

interface Lastnosti {
  visja: LigaDto
  nizja: LigaDto
  onZapri: () => void
}

const PO_PARIH: NacinKvalifikacij[] = ['ENA_TEKMA', 'SERIJA_DO_2', 'SERIJA_DO_3']
const MALA_LIGA: NacinKvalifikacij[] = ['VSAK_Z_VSAKIM', 'VSAK_Z_VSAKIM_DVOKROZNO']

export function KvalifikacijeOkno({ visja, nizja, onZapri }: Lastnosti) {
  const predlog = useQuery({
    queryKey: ['kvalifikacije', visja.id, nizja.id],
    queryFn: () => ligeApi.predlogKvalifikacij(visja.id, nizja.id),
  })

  return (
    <ModalnoOkno naslov="Ustvari kvalifikacije" podnaslov={`${visja.ime} – ${nizja.ime}`} onZapri={onZapri}>
      {predlog.isPending && <p className="obvestilo">Nalaganje …</p>}
      <NapakaPoizvedbe poizvedba={predlog} kaj="kvalifikacij" />
      {predlog.data && (
        <ObrazecKvalifikacij visja={visja} predlog={predlog.data} onZapri={onZapri} />
      )}
    </ModalnoOkno>
  )
}

function ObrazecKvalifikacij({
  visja,
  predlog,
  onZapri,
}: {
  visja: LigaDto
  predlog: KvalifikacijePredlogDto
  onZapri: () => void
}) {
  const odjemalec = useQueryClient()
  const navigiraj = useNavigate()
  const poParihMogoce = predlog.pari.length > 0
  const [nacin, nastaviNacin] = useState<NacinKvalifikacij>(poParihMogoce ? 'ENA_TEKMA' : 'VSAK_Z_VSAKIM')
  const [ime, nastaviIme] = useState(predlog.predlaganoIme)

  const ustvarjanje = useMutation({
    mutationFn: () =>
      ligeApi.ustvariKvalifikacije(visja.id, {
        idNizja: predlog.idNizja,
        nacin,
        ime: ime.trim() || null,
      }),
    onSuccess: (nova) => {
      odjemalec.invalidateQueries({ queryKey: ['lige'] })
      onZapri()
      navigiraj(`/lige/${nova.id}`)
    },
  })

  function obOddaji(d: FormEvent) {
    d.preventDefault()
    ustvarjanje.mutate()
  }

  const poParih = PO_PARIH.includes(nacin)
  /* Srečanje brez praga zmag s sodim številom tekem se lahko konča neodločeno
     in para ne odloči — isto opozorilo kot pri končnici v obrazcu lige. */
  const neodlocenoMogoce =
    visja.zmagZaSrecanje == null && RAZPORED_FORMATA[visja.formatSrecanja].length % 2 === 0
  const vseh = predlog.ekipeVisje.length + predlog.ekipeNizje.length

  return (
    <form className="obrazec" onSubmit={obOddaji}>
      {!predlog.koncano && (
        <p className="obvestilo">
          Redni del še teče — spodaj so ekipe po trenutnih lestvicah. Kvalifikacije
          nastanejo iz končnih.
        </p>
      )}

      {poParihMogoce ? (
        <div className="obrazec__polje">
          <span>{predlog.pari.length === 1 ? 'Par' : 'Pari (križno)'}</span>
          <ul className="kvalifikacije__pari">
            {predlog.pari.map((p) => (
              <li key={p.visja.idEkipa}>
                <Udelezenec u={p.visja} liga={predlog.visja} />
                <span className="kvalifikacije__proti">–</span>
                <Udelezenec u={p.nizja} liga={predlog.nizja} />
              </li>
            ))}
          </ul>
        </div>
      ) : (
        <div className="obrazec__polje">
          <span>Udeleženci</span>
          <ul className="kvalifikacije__pari">
            {[...predlog.ekipeVisje.map((u) => [u, predlog.visja] as const),
              ...predlog.ekipeNizje.map((u) => [u, predlog.nizja] as const)].map(([u, liga]) => (
              <li key={u.idEkipa}>
                <Udelezenec u={u} liga={liga} />
              </li>
            ))}
          </ul>
        </div>
      )}

      {predlog.ovire.length > 0 && (
        <div className="obvestilo obvestilo--opozorilo kvalifikacije__ovire">
          <p>Kvalifikacij še ni mogoče ustvariti:</p>
          <ul>
            {predlog.ovire.map((o) => <li key={o}>{o}</li>)}
          </ul>
        </div>
      )}

      <label className="obrazec__polje">
        <span>Način</span>
        <select value={nacin} onChange={(d) => nastaviNacin(d.target.value as NacinKvalifikacij)}>
          <optgroup label="Po parih">
            {PO_PARIH.map((n) => (
              <option key={n} value={n} disabled={!poParihMogoce}>{OZNAKE_NACINA_KVALIFIKACIJ[n]}</option>
            ))}
          </optgroup>
          <optgroup label="Mala liga">
            {MALA_LIGA.map((n) => (
              <option key={n} value={n}>{OZNAKE_NACINA_KVALIFIKACIJ[n]}</option>
            ))}
          </optgroup>
        </select>
      </label>
      <p className="namig">
        {opisNacina(nacin, predlog, vseh)}
        {poParih && neodlocenoMogoce &&
          ' Srečanje brez praga zmag se lahko konča neodločeno in para ne odloči — za kvalifikacije po parih nastavi višji ligi prag zmag.'}
        {!poParihMogoce &&
          ` Po parih ni mogoče, ker ligi ne dasta enako ekip (${predlog.visja}: ${predlog.izVisje}, ${predlog.nizja}: ${predlog.izNizje}).`}
      </p>

      <label className="obrazec__polje">
        <span>Ime lige kvalifikacij</span>
        <input value={ime} onChange={(d) => nastaviIme(d.target.value)} maxLength={80} />
      </label>
      <p className="namig">
        Kvalifikacije so svoja liga s pravili srečanja lige {predlog.visja}; ekipe
        se prenesejo s kadri. Lestvici obeh lig ostaneta, kot sta.
      </p>

      <SporociloNapake napaka={ustvarjanje.error} />
      <div className="obrazec__gumbi">
        <button type="button" className="gumb" onClick={onZapri}>Prekliči</button>
        <button
          type="submit"
          className="gumb gumb--glavni"
          disabled={ustvarjanje.isPending || predlog.ovire.length > 0 || (poParih && !poParihMogoce)}
        >
          Ustvari kvalifikacije
        </button>
      </div>
    </form>
  )
}

function Udelezenec({ u, liga }: { u: UdelezenecKvalifikacij; liga: string }) {
  return (
    <span className="kvalifikacije__ekipa">
      <span>{u.mesto}. {u.ekipa}</span>
      <span className="kvalifikacije__liga">{liga}</span>
    </span>
  )
}

function opisNacina(nacin: NacinKvalifikacij, p: KvalifikacijePredlogDto, vseh: number): string {
  switch (nacin) {
    case 'ENA_TEKMA':
      return `Vsak par odigra eno srečanje, doma igra ekipa iz lige ${p.visja}. Zmagovalec igra v ligi ${p.visja}.`
    case 'SERIJA_DO_2':
    case 'SERIJA_DO_3': {
      const zmag = nacin === 'SERIJA_DO_2' ? 2 : 3
      return `Par igra do ${zmag} zmag (največ ${2 * zmag - 1} srečanja). Prvo srečanje gosti ekipa nižje lige, drugo in odločilno ekipa višje.`
    }
    case 'VSAK_Z_VSAKIM':
    case 'VSAK_Z_VSAKIM_DVOKROZNO':
      return `Vseh ${vseh} ekip igra malo ligo${nacin === 'VSAK_Z_VSAKIM_DVOKROZNO' ? ' dvokrožno' : ''}; prvih ${p.ekipeVisje.length} igra v ligi ${p.visja}. Termine in razpored urediš na strani kvalifikacij.`
  }
}
