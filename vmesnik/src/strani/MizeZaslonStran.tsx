/* Zaslon za igralce (/turnirji/:id/mize/zaslon): TV v dvorani (prenova »tloris dvorane«,
   24. 11. 2026, design_handoff_mize_tloris).

   Brez glave in spodnje vrstice aplikacije, celozaslonsko. Odpre se z gumba »Zaslon za
   igralce ↗« na strani Mize (window.open) in je namenjen drugemu zaslonu ali TV prek HDMI,
   v isti seji organizatorja: zaledja zato ni treba spreminjati, podatke bere isti klic kot
   stran Mize (samo organizator turnirja in admin). Osvežuje se vsake 4 s.

   Kaže zadnjih pet klicev (najnovejši zgoraj, zelen), šest naslednjih tekem brez zasedenih
   igralcev in mali zemljevid dvorane. Referenčni okvir je 1280 × 720; pri drugih
   ločljivostih se oder sorazmerno poveča (transform: scale()). */
import { useEffect, useState } from 'react'
import { useParams } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'

import { mizeApi } from '../api/zahteve'
import { useAvtentikacija } from '../avtentikacija/AvtentikacijaKontekst'
import { ZnakTurnirko } from '../komponente/Postavitev'
import {
  imenaTekme,
  kratkoImenaTekme,
  minutOd,
  opisTekme,
  oznakaMize,
  tekmeVIgri,
  uraIzCasa,
  zadnjaBeseda,
} from '../pomozno/mize'
import { useNaslovStrani } from '../pomozno/naslovStrani'

const OSVEZITEV_MS = 4000
const SIRINA = 1280
const VISINA = 720
/* Klic je »pravkar«, dokler ni starejši od toliko minut; potem ga najnovejši klic kaže z uro. */
const PRAVKAR_MIN = 5
const KLICEV = 5
const NA_VRSTI = 6

/* Merilo odra: celoten okvir 16 : 9 stoji na sredini zaslona. */
function useMerilo(): { merilo: number; levo: number; zgoraj: number } {
  const izmeri = () => {
    const merilo = Math.min(window.innerWidth / SIRINA, window.innerHeight / VISINA)
    return {
      merilo,
      levo: (window.innerWidth - SIRINA * merilo) / 2,
      zgoraj: (window.innerHeight - VISINA * merilo) / 2,
    }
  }
  const [mere, nastaviMere] = useState(izmeri)
  useEffect(() => {
    const obSpremembi = () => nastaviMere(izmeri())
    window.addEventListener('resize', obSpremembi)
    return () => window.removeEventListener('resize', obSpremembi)
  }, [])
  return mere
}

export function MizeZaslonStran() {
  const { id } = useParams()
  const idTurnirja = Number(id)
  const { nalaganje } = useAvtentikacija()
  useNaslovStrani('Zaslon za igralce')
  const mere = useMerilo()

  const pregled = useQuery({
    queryKey: ['turnir', idTurnirja, 'mize'],
    queryFn: () => mizeApi.pregled(idTurnirja),
    refetchInterval: OSVEZITEV_MS,
    /* Poverilnice se preberejo ob zagonu; prej bi prvi klic šel brez njih. */
    enabled: !nalaganje,
  })
  const podatki = pregled.data

  const klici = podatki
    ? tekmeVIgri(podatki.mize)
        .filter((t) => t.poklicanaOb !== null)
        .sort((a, b) => (b.poklicanaOb ?? '').localeCompare(a.poklicanaOb ?? ''))
        .slice(0, KLICEV)
    : []
  const vrsta = podatki ? podatki.kandidati.filter((k) => !k.zaseden).slice(0, NA_VRSTI) : []

  return (
    <div className="mize-zaslon">
      <div
        className="mize-zaslon__oder"
        style={{ transform: `scale(${mere.merilo})`, left: mere.levo, top: mere.zgoraj }}
      >
        <div className="mize-zaslon__vrh">
          <span className="mize-zaslon__znamka">
            <ZnakTurnirko velikost={28} />
            <span>Turnirko</span>
            {podatki && <span className="mize-zaslon__turnir">{podatki.imeTurnirja}</span>}
          </span>
          <span className="mize-zaslon__ura">{podatki ? uraIzCasa(podatki.casStreznika) : ''}</span>
        </div>

        <div className="mize-zaslon__telo">
          <div className="mize-zaslon__levo">
            <span className="mize-zaslon__oznaka">Poklicani · pojdite na mizo</span>
            {klici.map((t, i) => {
              const minute = minutOd(t.poklicanaOb, podatki!.casStreznika) ?? 0
              const sodnik = t.sodnik ? ` · sodi ${zadnjaBeseda(t.sodnik.ime)}` : ''
              const prvi = i === 0
              return (
                <div key={t.tekma.id} className={'mize-zaslon__klic' + (prvi ? ' mize-zaslon__klic--prvi' : '')}>
                  <span className="mize-zaslon__klic-miza">{oznakaMize(t, '+')}</span>
                  <div className="mize-zaslon__klic-besedilo">
                    <p className="mize-zaslon__klic-imena">{imenaTekme(t)}</p>
                    <p className="mize-zaslon__klic-meta">
                      {prvi && minute <= PRAVKAR_MIN ? (
                        <>
                          <span className="mize-zaslon__pravkar">Pravkar</span> · {opisTekme(t)}
                        </>
                      ) : (
                        `${uraIzCasa(t.poklicanaOb)} · ${opisTekme(t)}`
                      )}
                      {sodnik}
                    </p>
                  </div>
                </div>
              )
            })}
          </div>

          <div className="mize-zaslon__desno">
            <div>
              <span className="mize-zaslon__oznaka">Na vrsti · pripravite se</span>
              <ol className="mize-zaslon__vrsta">
                {vrsta.map((k) => (
                  <li key={k.tekma.tekma.id} className="mize-zaslon__vrsta-postavka">
                    <span className="mize-zaslon__vrsta-ime">{kratkoImenaTekme(k.tekma)}</span>
                    <span className="mize-zaslon__vrsta-kat">{k.tekma.imeDogodka}</span>
                  </li>
                ))}
              </ol>
            </div>

            <div className="mize-zaslon__dvorana">
              <span className="mize-zaslon__oznaka">Dvorana</span>
              <div className="mize-zaslon__zemljevid">
                {podatki?.mize.map((m) => (
                  <span
                    key={m.stevilka}
                    className={
                      'mize-zaslon__plosca mize-zaslon__plosca--' +
                      (m.izklopljena ? 'izklopljena' : m.tekma ? 'zasedena' : 'prosta')
                    }
                  >
                    {m.stevilka}
                  </span>
                ))}
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  )
}
