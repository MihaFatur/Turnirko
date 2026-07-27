/* Obrazec za pravila lige — isti za ustvarjanje in za urejanje, da se možnosti
   ne razhajajo. Kadar je podana obstoječa liga, gre za urejanje (polja so
   prednapolnjena, shrani se s PUT). Strežnik urejanje dovoli samo, dokler je
   liga v PRIPRAVI; vmesnik gumb v drugih stanjih samo skrije. */
import { useState, type FormEvent } from 'react'
import { useMutation, useQuery } from '@tanstack/react-query'

import { ligeApi } from '../api/zahteve'
import type { FormatSrecanja, LigaDto, LigaVnos, SpolKategorija } from '../api/tipi'
import { OZNAKE_FORMAT } from '../api/tipi'
import { ModalnoOkno } from './ModalnoOkno'
import { SporociloNapake } from './SporociloNapake'

interface Lastnosti {
  /* Podana liga = urejanje; brez nje se ustvari nova. */
  liga?: LigaDto
  onZapri: () => void
  onShranjeno: (liga: LigaDto) => void
}

export function LigaObrazecOkno({ liga, onZapri, onShranjeno }: Lastnosti) {
  const urejanje = liga !== undefined
  const lige = useQuery({ queryKey: ['lige'], queryFn: ligeApi.seznam })

  const [ime, nastaviIme] = useState(liga?.ime ?? '')
  const [sezona, nastaviSezono] = useState(liga?.sezona ?? '')
  const [spol, nastaviSpol] = useState<SpolKategorija>(liga?.spolKategorija ?? 'MOSKI')
  const [format, nastaviFormat] = useState<FormatSrecanja>(liga?.formatSrecanja ?? 'SNTL')
  const [steviloNizov, nastaviSteviloNizov] = useState(liga?.steviloNizov ?? 5)
  const [zmagZaSrecanje, nastaviZmag] = useState(
    liga?.zmagZaSrecanje != null ? String(liga.zmagZaSrecanje) : '',
  )
  const [dvokrozno, nastaviDvokrozno] = useState(liga?.dvokrozno ?? true)
  const [tockeZmaga, nastaviTockeZmaga] = useState(liga?.tockeZmaga ?? 2)
  const [tockeNeodloceno, nastaviTockeNeodloceno] = useState(liga?.tockeNeodloceno ?? 1)
  const [tockePoraz, nastaviTockePoraz] = useState(liga?.tockePoraz ?? 0)
  const [dovoljenoNeodloceno, nastaviDovoljenoNeodloceno] = useState(liga?.dovoljenoNeodloceno ?? true)
  const [prepoved, nastaviPrepoved] = useState(liga?.prepovedDvojneRegistracije ?? false)
  const [stejeVElo, nastaviStejeVElo] = useState(liga?.stejeVElo ?? true)
  const [idVisjaLiga, nastaviVisjo] = useState(liga?.idVisjaLiga != null ? String(liga.idVisjaLiga) : '')
  const [stNapreduje, nastaviNapreduje] = useState(liga?.stNapreduje ?? 0)
  const [stIzpade, nastaviIzpade] = useState(liga?.stIzpade ?? 0)

  const shranjevanje = useMutation({
    mutationFn: (vnos: LigaVnos) =>
      urejanje ? ligeApi.uredi(liga.id, vnos) : ligeApi.ustvari(vnos),
    onSuccess: (shranjena) => {
      onShranjeno(shranjena)
      onZapri()
    },
  })

  function obOddaji(dogodek: FormEvent) {
    dogodek.preventDefault()
    shranjevanje.mutate({
      ime: ime.trim(),
      sezona: sezona.trim() || null,
      spolKategorija: spol,
      formatSrecanja: format,
      steviloNizov,
      zmagZaSrecanje: zmagZaSrecanje ? Number(zmagZaSrecanje) : null,
      dvokrozno,
      tockeZmaga,
      tockeNeodloceno,
      tockePoraz,
      dovoljenoNeodloceno,
      prepovedDvojneRegistracije: prepoved,
      stejeVElo,
      idVisjaLiga: idVisjaLiga ? Number(idVisjaLiga) : null,
      stNapreduje,
      stIzpade,
    })
  }

  /* Liga ne more biti sama sebi nadrejena, zato se pri urejanju ne ponudi. */
  const mozneVisje = (lige.data ?? []).filter((l) => l.id !== liga?.id)

  return (
    <ModalnoOkno naslov={urejanje ? 'Uredi pravila lige' : 'Nova liga'} onZapri={onZapri}>
      <form className="obrazec" onSubmit={obOddaji}>
        {urejanje && (
          <p className="obvestilo">
            Pravila je mogoče spreminjati samo, dokler je liga v pripravi — po generiranju
            razporeda so zaklenjena.
          </p>
        )}

        <label className="obrazec__polje">
          <span>Ime lige *</span>
          <input value={ime} onChange={(d) => nastaviIme(d.target.value)}
            placeholder="npr. 1. SNTL – moški 2025/26" required />
        </label>

        <div className="obrazec__vrstica">
          <label className="obrazec__polje">
            <span>Sezona</span>
            <input value={sezona} onChange={(d) => nastaviSezono(d.target.value)} placeholder="2025/26" />
          </label>
          <label className="obrazec__polje">
            <span>Kategorija</span>
            <select value={spol} onChange={(d) => nastaviSpol(d.target.value as SpolKategorija)}>
              <option value="MOSKI">Moški</option>
              <option value="ZENSKE">Ženske</option>
              <option value="MESANO">Mešano</option>
            </select>
          </label>
        </div>

        <div className="obrazec__vrstica">
          <label className="obrazec__polje">
            <span>Format srečanja</span>
            <select value={format} onChange={(d) => nastaviFormat(d.target.value as FormatSrecanja)}>
              {(Object.keys(OZNAKE_FORMAT) as FormatSrecanja[]).map((f) => (
                <option key={f} value={f}>{OZNAKE_FORMAT[f]}</option>
              ))}
            </select>
          </label>
          <label className="obrazec__polje">
            <span>Nizi (najboljši od)</span>
            <select value={steviloNizov} onChange={(d) => nastaviSteviloNizov(Number(d.target.value))}>
              <option value={3}>3</option>
              <option value={5}>5</option>
              <option value={7}>7</option>
            </select>
          </label>
        </div>

        <div className="obrazec__vrstica">
          <label className="obrazec__polje">
            <span>Prag zmag za konec srečanja</span>
            <input type="number" min={1} value={zmagZaSrecanje}
              onChange={(d) => nastaviZmag(d.target.value)} placeholder="prazno = vse tekme" />
          </label>
          <label className="obrazec__polje obrazec__polje--stikalo">
            <input type="checkbox" checked={dvokrozno} onChange={(d) => nastaviDvokrozno(d.target.checked)} />
            <span>Dvokrožno (doma in v gosteh)</span>
          </label>
        </div>

        <fieldset className="obrazec__skupina">
          <legend>Točkovanje</legend>
          <div className="obrazec__vrstica">
            <label className="obrazec__polje">
              <span>Zmaga</span>
              <input type="number" min={0} value={tockeZmaga} onChange={(d) => nastaviTockeZmaga(Number(d.target.value))} />
            </label>
            <label className="obrazec__polje">
              <span>Neodločeno</span>
              <input type="number" min={0} value={tockeNeodloceno} onChange={(d) => nastaviTockeNeodloceno(Number(d.target.value))} />
            </label>
            <label className="obrazec__polje">
              <span>Poraz</span>
              <input type="number" min={0} value={tockePoraz} onChange={(d) => nastaviTockePoraz(Number(d.target.value))} />
            </label>
          </div>
          <label className="obrazec__polje obrazec__polje--stikalo">
            <input type="checkbox" checked={dovoljenoNeodloceno} onChange={(d) => nastaviDovoljenoNeodloceno(d.target.checked)} />
            <span>Neodločen izid srečanja je mogoč</span>
          </label>
        </fieldset>

        <label className="obrazec__polje obrazec__polje--stikalo">
          <input type="checkbox" checked={prepoved} onChange={(d) => nastaviPrepoved(d.target.checked)} />
          <span>Prepovej dvojno registracijo (igralec le v eni ekipi lige)</span>
        </label>
        <label className="obrazec__polje obrazec__polje--stikalo">
          <input type="checkbox" checked={stejeVElo} onChange={(d) => nastaviStejeVElo(d.target.checked)} />
          <span>Posamične tekme štejejo v klubski ELO</span>
        </label>

        <fieldset className="obrazec__skupina">
          <legend>Prehodi (neobvezno)</legend>
          <div className="obrazec__vrstica">
            <label className="obrazec__polje">
              <span>Višja liga</span>
              <select value={idVisjaLiga} onChange={(d) => nastaviVisjo(d.target.value)}>
                <option value="">— brez —</option>
                {mozneVisje.map((l) => (
                  <option key={l.id} value={l.id}>{l.ime}</option>
                ))}
              </select>
            </label>
            <label className="obrazec__polje">
              <span>Napreduje</span>
              <input type="number" min={0} value={stNapreduje} onChange={(d) => nastaviNapreduje(Number(d.target.value))} />
            </label>
            <label className="obrazec__polje">
              <span>Izpade</span>
              <input type="number" min={0} value={stIzpade} onChange={(d) => nastaviIzpade(Number(d.target.value))} />
            </label>
          </div>
        </fieldset>

        <SporociloNapake napaka={shranjevanje.error} />
        <div className="obrazec__gumbi">
          <button type="button" className="gumb" onClick={onZapri}>Prekliči</button>
          <button type="submit" className="gumb gumb--glavni" disabled={shranjevanje.isPending}>
            {urejanje ? 'Shrani pravila' : 'Ustvari ligo'}
          </button>
        </div>
      </form>
    </ModalnoOkno>
  )
}
