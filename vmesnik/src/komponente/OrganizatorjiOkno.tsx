/* Okno »Organizatorji« lige ali turnirja (V62) - samo admin: kateri organizatorji
   imajo dostop do tekmovanja in ali se jim tekmovanje šteje v mejo paketa.

   Vsak preklop se shrani takoj (kot kaj vidijo ostali na zavihku Zanimivosti) in
   odgovor strežnika je novo stanje okna, zato se številke porabe vedno ujemajo z
   zaledjem. Lastnik (kdor je tekmovanje ustvaril) ima dostop vedno: preklopi se
   mu lahko samo štetje. Organizatorji istega kluba imajo dostop samodejno in so
   v seznamu samo kot opomba. Meje paketa okno ne vsiljuje - admin jo sme preseči,
   zato presežek pokaže rjasto. */
import { useMemo, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'

import { dostopApi } from '../api/zahteve'
import { OZNAKE_PAKET, type DostopOrganizator, type Paket } from '../api/tipi'
import { IskalniIzbirnik } from './IskalniIzbirnik'
import { ModalnoOkno } from './ModalnoOkno'
import { NapakaPoizvedbe } from './NapakaPoizvedbe'
import { SporociloNapake } from './SporociloNapake'

interface Lastnosti {
  vrsta: 'liga' | 'turnir'
  id: number
  ime: string
  onZapri: () => void
}

/* »Basic« namesto »Organizator Basic«: vrstica je že v oknu organizatorjev. */
function kratekPaket(paket: Paket): string {
  return OZNAKE_PAKET[paket].replace(/^Organizator /, '')
}

/* »Plus · 2 od 5 turnirjev v sezoni« - poraba meje za vrsto tega tekmovanja. */
function opisPorabe(o: DostopOrganizator, vrsta: 'liga' | 'turnir'): string {
  const { paket, uporabljeno, meja } = o.poraba
  if (paket === null || meja === null) return 'Brez organizatorskega paketa'
  return `${kratekPaket(paket)} · ${uporabljeno} od ${meja} ${vrsta === 'turnir' ? 'turnirjev' : 'lig'} v sezoni`
}

export function OrganizatorjiOkno({ vrsta, id, ime, onZapri }: Lastnosti) {
  const odjemalec = useQueryClient()
  const kljuc = ['dostop-organizatorjev', vrsta, id]
  const [novoSteje, nastaviNovoSteje] = useState(true)
  /* Vrstica, ki čaka na potrditev odvzema (okno v oknu ni mogoče: Escape bi
     zaprl obe, zato potrditev stoji v sami vrstici). */
  const [odvzemam, nastaviOdvzemam] = useState<number | null>(null)

  const stanje = useQuery({ queryKey: kljuc, queryFn: () => dostopApi.vrni(vrsta, id) })

  /* Račun organizatorja v kartonu (Dostopi) kaže porabo in dnevnik; po vsaki
     spremembi ju je treba prebrati znova. */
  const poSpremembi = (odgovor: Awaited<ReturnType<typeof dostopApi.vrni>>) => {
    odjemalec.setQueryData(kljuc, odgovor)
    odjemalec.invalidateQueries({ queryKey: ['racuni'] })
  }

  const nastavi = useMutation({
    mutationFn: (v: { idRacun: number; steje: boolean }) =>
      dostopApi.nastavi(vrsta, id, v.idRacun, v.steje),
    onSuccess: poSpremembi,
  })

  const odvzemi = useMutation({
    mutationFn: (idRacun: number) => dostopApi.odvzemi(vrsta, id, idRacun),
    onSuccess: (odgovor) => {
      nastaviOdvzemam(null)
      poSpremembi(odgovor)
    },
  })

  const zasedeno = nastavi.isPending || odvzemi.isPending
  const podatki = stanje.data

  const moznosti = useMemo(
    () =>
      (podatki?.kandidati ?? []).map((k) => ({
        id: k.idRacun,
        ime: k.ime,
        podrobnost: [k.klub, k.eposta].filter(Boolean).join(' · '),
        iskalno: [k.ime, k.klub, k.eposta].filter(Boolean).join(' '),
      })),
    [podatki],
  )

  const tekmovanje = vrsta === 'liga' ? 'ligo' : 'turnir'

  return (
    <ModalnoOkno srednje naslov="Organizatorji" podnaslov={ime} onZapri={onZapri}>
      <div className="obrazec organizatorji-okno">
        <p className="organizatorji-okno__uvod">
          Kdo od organizatorjev lahko ureja to {tekmovanje} in ali se mu šteje v omejitve
          paketa. Spremembe veljajo takoj.
        </p>

        <NapakaPoizvedbe poizvedba={stanje} kaj="organizatorjev" />

        {podatki && (
          <>
            {podatki.organizatorji.length === 0 ? (
              <p className="organizatorji-okno__prazno">Noben organizator nima dodeljenega dostopa.</p>
            ) : (
              <ul className="organizatorji-okno__seznam">
                {podatki.organizatorji.map((o) => {
                  const presezek = o.poraba.meja !== null && o.poraba.uporabljeno > o.poraba.meja
                  return (
                    <li key={o.idRacun} className="organizatorji-okno__vrstica">
                      <div className="organizatorji-okno__oseba">
                        <span className="organizatorji-okno__ime">{o.ime}</span>
                        <span className="organizatorji-okno__meta">
                          {[o.vir === 'LASTNIK' ? 'Lastnik' : 'Dodeljen', o.klub]
                            .filter(Boolean)
                            .join(' · ')}
                        </span>
                        <span
                          className={
                            'organizatorji-okno__meta'
                            + (presezek ? ' organizatorji-okno__meta--presezek' : '')
                          }
                        >
                          {opisPorabe(o, vrsta)}
                          {presezek && ' · čez mejo'}
                        </span>
                      </div>

                      <label className="obrazec__polje obrazec__polje--stikalo organizatorji-okno__steje">
                        <input
                          type="checkbox"
                          aria-label={`Šteje v omejitve: ${o.ime}`}
                          checked={o.steje}
                          disabled={zasedeno}
                          onChange={(d) =>
                            nastavi.mutate({ idRacun: o.idRacun, steje: d.target.checked })
                          }
                        />
                        <span>Šteje v omejitve</span>
                      </label>

                      {/* Celica je vedno tu, da stikala v vrstici lastnika (brez odvzema)
                          in dodeljenih stojijo v istem stolpcu. */}
                      <div className="organizatorji-okno__odvzem">
                        {o.vir === 'DODELJEN' && (
                          <button
                            type="button"
                            className="gumb"
                            disabled={zasedeno || odvzemam === o.idRacun}
                            onClick={() => nastaviOdvzemam(o.idRacun)}
                          >
                            Odvzemi
                          </button>
                        )}
                      </div>

                      {odvzemam === o.idRacun && (
                        <div className="organizatorji-okno__potrditev">
                          <span>Organizator tega {vrsta === 'liga' ? 'lige' : 'turnirja'} ne bo mogel več urejati.</span>
                          <div className="organizatorji-okno__odvzem">
                            <button
                              type="button"
                              className="gumb gumb--nevaren"
                              disabled={zasedeno}
                              onClick={() => odvzemi.mutate(o.idRacun)}
                            >
                              Odvzemi dostop
                            </button>
                            <button
                              type="button"
                              className="gumb"
                              disabled={zasedeno}
                              onClick={() => nastaviOdvzemam(null)}
                            >
                              Prekliči
                            </button>
                          </div>
                        </div>
                      )}
                    </li>
                  )
                })}
              </ul>
            )}

            {podatki.klubLastnik && (
              <p className="organizatorji-okno__opomba">
                Organizatorji kluba {podatki.klubLastnik} imajo dostop samodejno; tekmovanje se
                jim ne šteje v omejitve.
              </p>
            )}

            <div className="organizatorji-okno__dodaj">
              <IskalniIzbirnik
                oznaka="Dodaj organizatorja"
                vidnaOznaka
                vObrazcu
                namig="Vpiši ime, klub ali e-pošto"
                moznosti={moznosti}
                naIzbiro={(idRacun) => nastavi.mutate({ idRacun, steje: novoSteje })}
              />
              <label className="obrazec__polje obrazec__polje--stikalo">
                <input
                  type="checkbox"
                  aria-label="Šteje v omejitve: novi organizator"
                  checked={novoSteje}
                  onChange={(d) => nastaviNovoSteje(d.target.checked)}
                />
                <span>Šteje v omejitve</span>
              </label>
            </div>
          </>
        )}

        <SporociloNapake napaka={nastavi.error ?? odvzemi.error} />

        <div className="obrazec__noga">
          <span className="obrazec__noga-stanje" />
          <div className="obrazec__noga-gumbi">
            <button type="button" className="gumb" onClick={onZapri}>
              Zapri
            </button>
          </div>
        </div>
      </div>
    </ModalnoOkno>
  )
}
