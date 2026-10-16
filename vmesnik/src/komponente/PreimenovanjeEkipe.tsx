/* Preimenovanje ekipe lige ali ekipnega dogodka - v vsakem stanju tekmovanja.

   Ime ni pravilo tekmovanja: razpored, lestvica in zapisniki nosijo ekipo po
   id-ju, zato popravek (napaka v imenu se najpogosteje opazi, ko igra že teče
   in ekipe ni več mogoče odjaviti) spremeni samo izpis. Klub in zaporedna
   številka ostaneta; pri klubski ekipi prazno polje vrne ime »Klub N«, prosta
   ekipa pa drugega imena nima in ga ne more izgubiti.

   Dve obliki, ker ekipe urejamo na dveh vrstah mest: vrstica v razprti ekipi
   (okno »Ekipe in kader« - modalno okno znotraj modalnega bi zaprl Escape
   obema) in majhno okno iz vrstice ekipe (priprava lige, ekipni dogodek). */
import { useState, type FormEvent } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'

import type { EkipaDto, EkipaImeVnos } from '../api/tipi'
import { ModalnoOkno } from './ModalnoOkno'
import { SporociloNapake } from './SporociloNapake'

/* Isti meji kot v shemi (CHECK stolpca ekipa.ime) in na strežniku. */
const NAJMANJ_ZNAKOV = 2
const NAJVEC_ZNAKOV = 60

/* Šifranti ne nosijo imen ekip, a so veliki (več tisoč igralcev): ne
   osvežujemo jih po vsakem popravku imena. */
const BREZ_OSVEZITVE = new Set(['igralci', 'klubi', 'kraji'])

export type PreimenujEkipo = (idEkipa: number, vnos: EkipaImeVnos) => Promise<EkipaDto>

function useUrejanjeImena(ekipa: EkipaDto, preimenuj: PreimenujEkipo, naKoncu?: () => void) {
  const odjemalec = useQueryClient()
  const [ime, nastaviIme] = useState(ekipa.ime ?? '')
  const vneseno = ime.trim()
  const prosta = ekipa.klub === null

  /* Prazno polje je pri klubski ekipi izbira (ime iz kluba), pri prosti napaka. */
  const veljavno =
    vneseno === ''
      ? !prosta
      : vneseno.length >= NAJMANJ_ZNAKOV && vneseno.length <= NAJVEC_ZNAKOV
  const spremenjeno = vneseno !== (ekipa.ime ?? '')

  const shrani = useMutation({
    mutationFn: () => preimenuj(ekipa.id, { ime: vneseno === '' ? null : vneseno }),
    onSuccess: () => {
      /* Ime stoji v lestvici, razporedu, zapisnikih, mreži, koledarju, na
         domači strani in v profilih igralcev - ne vemo, kje vse je odprto. */
      odjemalec.invalidateQueries({
        predicate: (poizvedba) => !BREZ_OSVEZITVE.has(String(poizvedba.queryKey[0])),
      })
      naKoncu?.()
    },
  })

  const poslji = (dogodek: FormEvent) => {
    dogodek.preventDefault()
    if (veljavno && spremenjeno && !shrani.isPending) shrani.mutate()
  }

  const privzeto = prosta ? null : `${ekipa.klub} ${ekipa.zaporedna}`
  const namig = prosta
    ? `Prosta ekipa mora imeti ime (${NAJMANJ_ZNAKOV}–${NAJVEC_ZNAKOV} znakov).`
    : `Prazno polje vrne ime »${privzeto}«.`

  return { ime, nastaviIme, veljavno, spremenjeno, shrani, poslji, privzeto, namig }
}

function PoljeImena({
  stanje,
  samodejniFokus,
}: {
  stanje: ReturnType<typeof useUrejanjeImena>
  /* V oknu: brez tega fokus dobi gumb za zapiranje (prvi v oknu), ne polje. */
  samodejniFokus?: boolean
}) {
  return (
    <label className="obrazec__polje">
      <span>Ime ekipe</span>
      <input
        autoFocus={samodejniFokus}
        value={stanje.ime}
        maxLength={NAJVEC_ZNAKOV}
        onChange={(d) => stanje.nastaviIme(d.target.value)}
        placeholder={stanje.privzeto ?? 'npr. Kuhinja'}
      />
    </label>
  )
}

/* Vrstica v razprti ekipi: polje, gumb, namig in morebitna napaka. */
export function PreimenovanjeEkipe({
  ekipa,
  preimenuj,
}: {
  ekipa: EkipaDto
  preimenuj: PreimenujEkipo
}) {
  const stanje = useUrejanjeImena(ekipa, preimenuj)
  return (
    <>
      <form className="obrazec__vrstica liga__dodaj-ekipo" onSubmit={stanje.poslji}>
        <PoljeImena stanje={stanje} />
        <button
          className="gumb"
          type="submit"
          disabled={!stanje.veljavno || !stanje.spremenjeno || stanje.shrani.isPending}
        >
          Shrani ime
        </button>
      </form>
      <p className="namig">
        {stanje.namig} Spremeni se samo ime: rezultati, kader in lestvica ostanejo.
      </p>
      <SporociloNapake napaka={stanje.shrani.error} />
    </>
  )
}

/* Okno iz vrstice ekipe (priprava lige, ekipni dogodek). */
export function PreimenujEkipoOkno({
  ekipa,
  preimenuj,
  onZapri,
}: {
  ekipa: EkipaDto
  preimenuj: PreimenujEkipo
  onZapri: () => void
}) {
  const stanje = useUrejanjeImena(ekipa, preimenuj, onZapri)
  return (
    <ModalnoOkno naslov="Preimenuj ekipo" onZapri={onZapri}>
      <form className="obrazec" onSubmit={stanje.poslji}>
        <PoljeImena stanje={stanje} samodejniFokus />
        <p className="namig">
          {stanje.namig} Spremeni se samo ime: rezultati, kader in lestvica ostanejo.
        </p>
        <SporociloNapake napaka={stanje.shrani.error} />
        <div className="obrazec__gumbi">
          <button type="button" className="gumb" onClick={onZapri}>
            Prekliči
          </button>
          <button
            type="submit"
            className="gumb gumb--glavni"
            disabled={!stanje.veljavno || !stanje.spremenjeno || stanje.shrani.isPending}
          >
            Shrani ime
          </button>
        </div>
      </form>
    </ModalnoOkno>
  )
}
