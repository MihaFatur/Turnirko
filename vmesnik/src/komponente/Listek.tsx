/* En natisljiv sodniski listek posamicne (turnirske) tekme.

   Listek je zapisnik na papirju in je zgrajen kot tabela, v kateri vsak
   igralec dobi SVOJ stolpec: ime je glava stolpca, pod njim sta znacki za
   prvi servis in timeout, nato po ena celica za vsak niz in na dnu
   rezultat (dobljeni nizi). Tocke so tako vedno pod imenom tistega, ki jih je
   dosegel, ne glede na to, kdo je pisal, zato jih sodnik ne more zamenjati.

   Zgoraj stoji znamka Turnirko in prazna crta za stevilko mize, pod njo
   dogodek, kategorija in skupina oz. faza; spodaj crta za ime sodnika in
   podpis. Sodnik listek izpolni z roko za mizo, rezultat pa glavni sodnik
   pozneje vnese prek obicajnega vnosa.

   Listek je namenoma crno-bel ne glede na temo (natis na papir), zato tu ne
   uporabljamo temo odvisnih spremenljivk. Natis na list A4 (6 listkov, rezi)
   urejata ListkiStran in razdelek 16 v slog.css. */
import type { CSSProperties } from 'react'

import { ZnakTurnirko } from './Postavitev'

/* Ena stran listka: igralec ali dvojica. */
interface StranListka {
  /* Ime igralca; pri dvojicah oba, zdruzena z " / ". */
  ime: string
}

export interface ListekPodatki {
  /* Turnir, ki ga igralci poznajo kot »dogodek«; prazen, kadar ga ne poznamo. */
  dogodek: string | null
  /* Ime dogodka v Turnirku (kategorija turnirja), po potrebi s spolom in starostjo. */
  kategorija: string
  /* Oznaka skupine (»A«); prazna pri izlocilni mrezi. */
  skupina: string | null
  /* Kolo skupine ali krog mreze (»2. kolo«, »Polfinale«). */
  faza: string
  /* Najvec mogocih nizov (najboljsi od N): toliko celic za tocke. */
  steviloNizov: number
  /* Stevilka mize; null izpise prazno crto za rocni vpis. */
  miza: number | null
  stran1: StranListka
  stran2: StranListka
}

export function Listek({ podatki }: { podatki: ListekPodatki }) {
  const nizi = Array.from({ length: podatki.steviloNizov }, (_, i) => i + 1)

  return (
    <article
      className={'listek' + (podatki.steviloNizov > 5 ? ' listek--gosto' : '')}
      style={{ '--nizov': podatki.steviloNizov } as CSSProperties}
    >
      <header className="listek__glava">
        <span className="listek__znamka">
          <ZnakTurnirko velikost={18} />
          Turnirko
        </span>
        {/* Kadar miza se ni dodeljena, ostane crta za rocni vpis. */}
        <span className="listek__miza">
          Miza št.
          <span className="listek__vpis">{podatki.miza !== null ? podatki.miza : ''}</span>
        </span>
      </header>

      <div className="listek__kontekst">
        {podatki.dogodek && <Polje oznaka="Dogodek" vrednost={podatki.dogodek} sirok />}
        <Polje oznaka="Kategorija" vrednost={podatki.kategorija} sirok />
        {/* Skupinska tekma: »Skupina A  2. kolo«; tekma mreže: »Faza  Polfinale«. */}
        {podatki.skupina ? (
          <>
            <Polje oznaka="Skupina" vrednost={podatki.skupina} poudarjeno />
            <Polje oznaka={null} vrednost={podatki.faza} poudarjeno />
          </>
        ) : (
          <Polje oznaka="Faza" vrednost={podatki.faza} poudarjeno sirok />
        )}
      </div>

      <div className="listek__tabela">
        <div className="listek__vrsta listek__vrsta--imena">
          <span />
          <Ime ime={podatki.stran1.ime} />
          <span className="listek__dvopicje">:</span>
          <Ime ime={podatki.stran2.ime} />
        </div>

        {/* Prvi servis in timeout stojita pod imenom: sodnik ju oznaci s
            križcem, ko se zgodita. Vsak igralec (par) ima en timeout. */}
        <div className="listek__vrsta listek__vrsta--znaki">
          <span />
          <Znaki />
          <span />
          <Znaki />
        </div>

        <div className="listek__nizi">
          {nizi.map((n) => (
            <div key={n} className="listek__vrsta listek__vrsta--niz">
              <span className="listek__niz-oznaka">{n}. niz</span>
              <span className="listek__celica" />
              <span className="listek__dvopicje">:</span>
              <span className="listek__celica" />
            </div>
          ))}
        </div>

        <div className="listek__vrsta listek__vrsta--rezultat">
          <span className="listek__rezultat-oznaka">Rezultat</span>
          <span className="listek__celica listek__celica--rezultat" />
          <span className="listek__dvopicje">:</span>
          <span className="listek__celica listek__celica--rezultat" />
        </div>
      </div>

      <footer className="listek__noga">
        <span className="listek__podpis listek__podpis--sodnik">
          Sodnik
          <span className="listek__crta" />
        </span>
        <span className="listek__podpis">
          Podpis
          <span className="listek__crta" />
        </span>
      </footer>
    </article>
  )
}

/* Vrednost, daljša od tega, se v eni vrstici ne prilega pri običajni velikosti
   (turnir »1. otroški turnir NTZS za U-13, Kidričevo, 19. 9. 2026«). */
const NAJDALJSE_PRI_OBICAJNI_VELIKOSTI = 46

/* Oznaka in vrednost v glavi listka. */
function Polje({
  oznaka,
  vrednost,
  sirok = false,
  poudarjeno = false,
}: {
  /* null: vrednost stoji brez oznake, desno od prejšnjega polja (kolo ob skupini). */
  oznaka: string | null
  vrednost: string
  /* Vrednost zavzame ostalo vrstico (dogodek, kategorija). */
  sirok?: boolean
  poudarjeno?: boolean
}) {
  const dolga = sirok && !poudarjeno && vrednost.length > NAJDALJSE_PRI_OBICAJNI_VELIKOSTI
  return (
    <>
      {oznaka !== null && <span className="listek__oznaka">{oznaka}</span>}
      <span
        className={
          'listek__vrednost' +
          (sirok ? ' listek__vrednost--sirok' : '') +
          (oznaka === null ? ' listek__vrednost--drugo' : '') +
          (poudarjeno ? ' listek__vrednost--poudarjeno' : '') +
          (dolga ? ' listek__vrednost--dolga' : '')
        }
      >
        {vrednost}
      </span>
    </>
  )
}

/* Glava stolpca. Dvojica ima vsakega igralca v svoji vrstici, ker bi se par v
   enem stolpcu prelomil sredi imena. */
function Ime({ ime }: { ime: string }) {
  const igralci = ime.split(' / ')
  return (
    <span className={'listek__ime' + (igralci.length > 1 ? ' listek__ime--par' : '')}>
      {igralci.map((igralec) => (
        <span key={igralec} className="listek__ime-vrstica">
          {igralec}
        </span>
      ))}
    </span>
  )
}

function Znaki() {
  return (
    <span className="listek__znaki">
      <span className="listek__znak">
        <span className="listek__kvadratek" />
        Prvi servis
      </span>
      <span className="listek__znak">
        <span className="listek__kvadratek" />
        Timeout
      </span>
    </span>
  )
}
