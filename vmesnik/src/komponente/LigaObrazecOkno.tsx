/* Obrazec za pravila lige — isti za ustvarjanje in za urejanje, da se možnosti
   ne razhajajo. Kadar je podana obstoječa liga, gre za urejanje (polja so
   prednapolnjena, shrani se s PUT). Strežnik urejanje dovoli samo, dokler je
   liga v PRIPRAVI; vmesnik gumb v drugih stanjih samo skrije.

   Mesta lige v piramidi (višja/nižja liga, napredovanje, izpad) tu namenoma
   ni: to ni pravilo tekmovanja, ampak opis sezone, in se sme popravljati tudi
   potem, ko so pravila zaklenjena — ureja ga PrehodiOkno. */
import { useState, type FormEvent } from 'react'
import { useMutation } from '@tanstack/react-query'

import { ligeApi } from '../api/zahteve'
import type { FormatSrecanja, LigaDto, LigaVnos, PredlogaLige, SpolKategorija } from '../api/tipi'
import { OZNAKE_FORMAT, OZNAKE_PREDLOGA_LIGE, RAZPORED_FORMATA } from '../api/tipi'
import { ModalnoOkno } from './ModalnoOkno'
import { SporociloNapake } from './SporociloNapake'

interface Lastnosti {
  /* Podana liga = urejanje; brez nje se ustvari nova. */
  liga?: LigaDto
  onZapri: () => void
  onShranjeno: (liga: LigaDto) => void
}

/* Konec srečanja je izbira dveh pravil, ne prazno polje: »vse tekme« je
   enakovredna možnost in mora biti napisana, ne uganjena iz praznega vnosa. */
type KonecSrecanja = 'VSE' | 'PRAG'

/* Privzeti prag = večina tekem (npr. 6 od 10 pri SNTL, 3 od 5 pri Savinji). */
function vecina(format: FormatSrecanja): number {
  return Math.floor(RAZPORED_FORMATA[format].length / 2) + 1
}

export function LigaObrazecOkno({ liga, onZapri, onShranjeno }: Lastnosti) {
  const urejanje = liga !== undefined

  const [ime, nastaviIme] = useState(liga?.ime ?? '')
  const [sezona, nastaviSezono] = useState(liga?.sezona ?? '')
  const [spol, nastaviSpol] = useState<SpolKategorija>(liga?.spolKategorija ?? 'MOSKI')
  const [format, nastaviFormat] = useState<FormatSrecanja>(liga?.formatSrecanja ?? 'SNTL')
  const [steviloNizov, nastaviSteviloNizov] = useState(liga?.steviloNizov ?? 5)
  const [konec, nastaviKonec] = useState<KonecSrecanja>(
    liga?.zmagZaSrecanje != null ? 'PRAG' : 'VSE',
  )
  const [prag, nastaviPrag] = useState(
    liga?.zmagZaSrecanje ?? vecina(liga?.formatSrecanja ?? 'SNTL'),
  )
  const [dvokrozno, nastaviDvokrozno] = useState(liga?.dvokrozno ?? true)
  const [tockeZmaga, nastaviTockeZmaga] = useState(liga?.tockeZmaga ?? 2)
  const [tockeNeodloceno, nastaviTockeNeodloceno] = useState(liga?.tockeNeodloceno ?? 1)
  const [tockePoraz, nastaviTockePoraz] = useState(liga?.tockePoraz ?? 0)
  const [dovoljenoNeodloceno, nastaviDovoljenoNeodloceno] = useState(liga?.dovoljenoNeodloceno ?? true)
  const [prepoved, nastaviPrepoved] = useState(liga?.prepovedDvojneRegistracije ?? false)
  const [stejeVElo, nastaviStejeVElo] = useState(liga?.stejeVElo ?? true)
  const [predloga, nastaviPredlogo] = useState<PredlogaLige>(liga?.predlogaListka ?? 'SNTL_23')

  const tekme = RAZPORED_FORMATA[format]

  /* Ob zamenjavi formata se prag prilagodi: mej, večjih od števila tekem,
     strežnik ne sprejme, tiho poslana napaka pa bi bila nerazumljiva. */
  function zamenjajFormat(nov: FormatSrecanja) {
    nastaviFormat(nov)
    nastaviPrag((p) => Math.min(p, RAZPORED_FORMATA[nov].length))
  }

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
      zmagZaSrecanje: konec === 'PRAG' ? prag : null,
      dvokrozno,
      tockeZmaga,
      tockeNeodloceno,
      tockePoraz,
      dovoljenoNeodloceno,
      prepovedDvojneRegistracije: prepoved,
      stejeVElo,
      predlogaListka: predloga,
    })
  }

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
            <select value={format} onChange={(d) => zamenjajFormat(d.target.value as FormatSrecanja)}>
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

        <p className="namig">
          Vrstni red tekem: {tekme.join(' · ')} ({tekme.length} tekem)
        </p>

        <div className="obrazec__vrstica">
          <label className="obrazec__polje">
            <span>Konec srečanja</span>
            <select value={konec} onChange={(d) => nastaviKonec(d.target.value as KonecSrecanja)}>
              <option value="VSE">Odigrajo se vse tekme</option>
              <option value="PRAG">Konča se pri pragu zmag</option>
            </select>
          </label>
          {konec === 'PRAG' && (
            <label className="obrazec__polje">
              <span>Prag zmag (največ {tekme.length})</span>
              <input type="number" min={1} max={tekme.length} value={prag}
                onChange={(d) => nastaviPrag(Number(d.target.value))} required />
            </label>
          )}
        </div>
        <p className="namig">
          {konec === 'VSE'
            ? 'Vseh ' + tekme.length + ' tekem se odigra do konca, tudi ko je zmagovalec srečanja že znan — rezultat šteje v razliko tekem in v ELO.'
            : `Ko ena ekipa doseže ${prag} dobljenih tekem, se srečanje konča; preostale tekme ostanejo neodigrane.`}
        </p>

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
          <input type="checkbox" checked={dvokrozno} onChange={(d) => nastaviDvokrozno(d.target.checked)} />
          <span>Dvokrožno (doma in v gosteh)</span>
        </label>
        <label className="obrazec__polje obrazec__polje--stikalo">
          <input type="checkbox" checked={prepoved} onChange={(d) => nastaviPrepoved(d.target.checked)} />
          <span>Prepovej dvojno registracijo (igralec le v eni ekipi lige)</span>
        </label>
        <label className="obrazec__polje obrazec__polje--stikalo">
          <input type="checkbox" checked={stejeVElo} onChange={(d) => nastaviStejeVElo(d.target.checked)} />
          <span>Posamične tekme štejejo v klubski ELO</span>
        </label>

        <label className="obrazec__polje">
          <span>Predloga zapisnika (za natis listkov)</span>
          <select value={predloga} onChange={(d) => nastaviPredlogo(d.target.value as PredlogaLige)}>
            {(Object.keys(OZNAKE_PREDLOGA_LIGE) as PredlogaLige[]).map((p) => (
              <option key={p} value={p}>{OZNAKE_PREDLOGA_LIGE[p]}</option>
            ))}
          </select>
        </label>

        <p className="namig">
          Mesto lige v piramidi (višja in nižje lige, napredovanje, izpad) se
          ureja posebej — na strani lige, tudi ko so pravila že zaklenjena.
        </p>

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
