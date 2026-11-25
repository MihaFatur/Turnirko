/* Okno »Turnirji in kategorije« strani Mize (skupne mize, V78; docs/pravila/mize.md).

   Na isti dan v isti dvorani pogosto igrata dva turnirja istega organizatorja na istih
   mizah (lastnik 25. 11. 2026: U15 in U21, vsak z moškimi in ženskami). Organizator ju tu
   poveže: od tedaj so mize, vrsta, klici, sodniki in nastavitve na strani Mize obeh
   turnirjev ena celota, gledalec pa vidi turnirja kot prej.

   Tu organizator tudi razvrsti kategorije: predlog gre strogo po kolih (1. kolo vseh
   kategorij, nato 2. kolo …), v kolu po tem vrstnem redu. Velja tudi za en sam turnir z
   več kategorijami. */
import { useState } from 'react'
import { useMutation, useQuery } from '@tanstack/react-query'

import type { KategorijaMizDto, MizeTurnirjaDto } from '../api/tipi'
import { mizeApi } from '../api/zahteve'
import { ModalnoOkno } from './ModalnoOkno'
import { SporociloNapake } from './SporociloNapake'

const STATUS: Record<KategorijaMizDto['status'], string> = {
  PRIPRAVA: 'v pripravi',
  V_TEKU: 'v teku',
  ZAKLJUCEN: 'končana',
}

export function OknoSkupnihMiz({
  idTurnirja,
  podatki,
  onSpremenjeno,
  onZapri,
}: {
  idTurnirja: number
  podatki: MizeTurnirjaDto
  /* Po vsaki spremembi stran znova prebere stanje dvorane. */
  onSpremenjeno: () => void
  onZapri: () => void
}) {
  const { turnirji, kategorije, redNastavljen } = podatki.skupne
  const povezane = turnirji.length > 1
  const [izbran, nastaviIzbran] = useState<number | ''>('')

  const kandidati = useQuery({
    queryKey: ['turnir', idTurnirja, 'mize', 'za-povezavo'],
    queryFn: () => mizeApi.zaPovezavo(idTurnirja),
  })

  const povezi = useMutation({
    mutationFn: (idDrugega: number) => mizeApi.povezi(idTurnirja, idDrugega),
    onSuccess: () => {
      nastaviIzbran('')
      onSpremenjeno()
    },
  })
  const razdruzi = useMutation({
    mutationFn: (idTurnir: number) => mizeApi.razdruzi(idTurnir),
    onSuccess: onSpremenjeno,
  })
  const red = useMutation({
    mutationFn: (idji: number[]) => mizeApi.redKategorij(idTurnirja, idji),
    onSuccess: onSpremenjeno,
  })

  const zasedeno = povezi.isPending || razdruzi.isPending || red.isPending
  const premakni = (i: number, smer: -1 | 1) => {
    const idji = kategorije.map((k) => k.idDogodka)
    const j = i + smer
    if (j < 0 || j >= idji.length) return
    ;[idji[i], idji[j]] = [idji[j], idji[i]]
    red.mutate(idji)
  }
  /* Ime turnirja ob kategoriji samo, ko so turnirji povezani (sicer je vedno isti). */
  const imeKategorije = (k: KategorijaMizDto) => (povezane ? `${k.ime} · ${k.imeTurnirja}` : k.ime)
  const ponujeni = kandidati.data ?? []

  return (
    <ModalnoOkno naslov="Turnirji in kategorije" nadnaslov="Mize" razred="modal--mize" onZapri={onZapri}>
      <div className="mize-skupne">
        <section className="mize-skupne__razdelek" aria-labelledby="mize-skupne-turnirji">
          <h3 id="mize-skupne-turnirji" className="mize-skupne__naslov">
            Turnirji na teh mizah
          </h3>
          <p className="mize-okno__namig">
            Turnirja v isti dvorani povežeš, da imata skupne mize, vrsto, klice in tisk listkov.
            Gledalci ju vidijo ločeno kot prej.
          </p>
          <ul className="mize-skupne__seznam">
            {turnirji.map((t, i) => (
              <li key={t.id} className="mize-skupne__vrstica">
                <span className="mize-skupne__ime">
                  {t.ime}
                  {t.id === idTurnirja && <span className="mize-skupne__oznaka"> · ta turnir</span>}
                  {povezane && i === 0 && <span className="mize-skupne__oznaka"> · nastavitve miz</span>}
                </span>
                {povezane && (
                  <button
                    type="button"
                    className="mize-okno__gumb"
                    disabled={zasedeno}
                    onClick={() => razdruzi.mutate(t.id)}
                  >
                    Loči
                  </button>
                )}
              </li>
            ))}
          </ul>
          {kandidati.isPending ? (
            <p className="mize-okno__namig">Nalaganje turnirjev …</p>
          ) : ponujeni.length === 0 ? (
            <p className="mize-okno__namig">Na isti dan ni drugih tvojih turnirjev, s katerimi bi delil mize.</p>
          ) : (
            <form
              className="mize-skupne__povezi"
              onSubmit={(d) => {
                d.preventDefault()
                if (izbran !== '') povezi.mutate(izbran)
              }}
            >
              <label className="mize-okno__polje">
                <span>Deli mize s turnirjem</span>
                <select
                  value={izbran}
                  onChange={(d) => nastaviIzbran(d.target.value === '' ? '' : Number(d.target.value))}
                >
                  <option value="">Izberi turnir …</option>
                  {ponujeni.map((t) => (
                    <option key={t.id} value={t.id}>
                      {t.ime}
                    </option>
                  ))}
                </select>
              </label>
              <button
                type="submit"
                className="mize-okno__gumb mize-okno__gumb--glavni"
                disabled={izbran === '' || zasedeno}
              >
                Poveži
              </button>
            </form>
          )}
          <SporociloNapake napaka={povezi.error ?? razdruzi.error} />
        </section>

        <section className="mize-skupne__razdelek" aria-labelledby="mize-skupne-kategorije">
          <h3 id="mize-skupne-kategorije" className="mize-skupne__naslov">
            Vrstni red kategorij
          </h3>
          <p className="mize-okno__namig">
            Predlog gre po kolih: najprej 1. kolo vseh kategorij v tem vrstnem redu, nato 2. kolo …
          </p>
          <ol className="mize-skupne__seznam mize-skupne__seznam--red">
            {kategorije.map((k, i) => (
              <li key={k.idDogodka} className="mize-skupne__vrstica">
                <span className="mize-skupne__st">{i + 1}.</span>
                <span className="mize-skupne__ime">
                  {imeKategorije(k)}
                  <span className="mize-skupne__oznaka">
                    {' · '}
                    {STATUS[k.status]}
                    {k.ekipno ? ' · ekipno' : ''}
                  </span>
                </span>
                <span className="mize-skupne__premik">
                  <button
                    type="button"
                    className="mize-skupne__gumb"
                    aria-label={`${imeKategorije(k)} višje`}
                    disabled={zasedeno || i === 0}
                    onClick={() => premakni(i, -1)}
                  >
                    ↑
                  </button>
                  <button
                    type="button"
                    className="mize-skupne__gumb"
                    aria-label={`${imeKategorije(k)} nižje`}
                    disabled={zasedeno || i === kategorije.length - 1}
                    onClick={() => premakni(i, 1)}
                  >
                    ↓
                  </button>
                </span>
              </li>
            ))}
          </ol>
          {redNastavljen && (
            <button type="button" className="mize-okno__gumb" disabled={zasedeno} onClick={() => red.mutate([])}>
              Privzeti vrstni red
            </button>
          )}
          <SporociloNapake napaka={red.error} />
        </section>

        <div className="mize-okno__gumbi">
          <button type="button" className="mize-okno__gumb mize-okno__gumb--glavni" onClick={onZapri}>
            Zapri
          </button>
        </div>
      </div>
    </ModalnoOkno>
  )
}
