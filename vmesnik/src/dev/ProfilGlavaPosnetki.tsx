/* Prikazni harness za posnetke glave profila igralca (design_handoff_profil_glava).
   Samo za razvoj: /dev/profil-glava, naloži se leno in samo, kadar import.meta.env.DEV
   (glej App.tsx). Brez prijave in omrežja; podatki so fiksni (poglavje 12.1 predaje).

   Parametri:
     stanje=moj|gost   Miha Fatur (s sliko, zasebno) ali Luka Zupan (brez slike, javno)
     meni=1            meni »⋯« odprt */
import { useSearchParams } from 'react-router-dom'

import { ProfilGlava, type ProfilGlavaLastnosti } from '../komponente/ProfilGlava'

const nazaj = <a className="povezava-nazaj" href="#">← Lestvica</a>

const MOJ: Omit<ProfilGlavaLastnosti, 'meniOdprt'> = {
  nazaj,
  glavno: { vrsta: 'povezava', to: '/igralci/1/sezona', oznaka: 'Sezonski zapisnik →' },
  meni: [{ oznaka: 'Uredi sliko' }],
  ime: 'Miha',
  priimek: 'Fatur',
  slika: '/dev/miha-fatur.png',
  klub: 'Namiznoteniški klub Savinja',
  roka: 'Desničar',
  jeMoj: true,
  uvrstitev: { mesto: 96, od: 619, oznaka: 'Moški · Člani', percentil: 85 },
  pas: { mesto: 20, od: 259, oznaka: 'Moški · U21' },
  rating: 1393,
  noga: { sprememba: '+17 / 30 dni', vrh: 'vrh 1600' },
  klubskoPovprecje: { vrednost: 1474, razlika: 1393 - 1474 },
  doMesta: { mesto: 95, razlika: 5, tock: '5 točk' },
  odigrane: 521,
  turnirskih: 270,
  ligaskih: 251,
  zmage: 294,
  porazi: 227,
  odstotekZmag: 56,
  zadnjaTekma: '25. 10. 2026',
}

const GOST: Omit<ProfilGlavaLastnosti, 'meniOdprt'> = {
  nazaj,
  glavno: { vrsta: 'spremljanje', ime: 'Luka Zupan', spremljam: false, naPreklop: () => {} },
  meni: [{ oznaka: 'Sezonski zapisnik za tisk →', to: '/igralci/2/sezona' }],
  ime: 'Luka',
  priimek: 'Zupan',
  slika: null,
  klub: 'Namiznoteniški klub Žalec',
  roka: 'Levičar',
  jeMoj: false,
  uvrstitev: { mesto: 38, od: 619, oznaka: 'Moški · Člani', percentil: 94 },
  pas: null,
  rating: 1612,
  noga: null,
  klubskoPovprecje: { vrednost: 1498, razlika: 1612 - 1498 },
  doMesta: { mesto: 37, razlika: 9, tock: '9 točk' },
  odigrane: 388,
  turnirskih: 203,
  ligaskih: 185,
  zmage: 247,
  porazi: 141,
  odstotekZmag: 64,
  zadnjaTekma: '1. 11. 2026',
}

function Okvir() {
  const [iskanje] = useSearchParams()
  const moj = iskanje.get('stanje') !== 'gost'
  const telefon = window.innerWidth <= 640
  return (
    <div
      style={{
        background: 'var(--barva-papir)',
        padding: telefon ? '16px 20px 40px' : '40px 40px 56px',
        minHeight: '100vh',
      }}
    >
      <ProfilGlava
        key={iskanje.toString()}
        {...(moj ? MOJ : GOST)}
        meniOdprt={iskanje.get('meni') === '1'}
      />
    </div>
  )
}

export default Okvir
