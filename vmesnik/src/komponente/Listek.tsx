/* En natisljiv sodniski listek posamicne (turnirske) tekme.

   Listek je zapisnik na papirju in je zgrajen kot tabela, v kateri vsak
   igralec dobi SVOJ stolpec: ime je glava stolpca, pod njim po ena celica za
   vsak niz in na dnu rezultat (dobljeni nizi). Tocke so tako vedno pod imenom
   tistega, ki jih je dosegel, ne glede na to, kdo je pisal, zato jih sodnik ne
   more zamenjati. Znacki za prvi servis in timeout stojita na zunanji strani
   stolpca ob prvih dveh nizih.

   Polja za tocke (od 24. 11. 2026, lastnik): visoka in ozka (17 mm, 9 mm pri
   petih nizih, 10 mm pri treh, okoli 7 mm pri sedmih), brez crte med deseticami
   in enicami. Prej so bila 35 x 6 mm in stevke, napisane cez crto, je bralnik
   odrezal. Visina vrstic je stalna (ne raztegne se po dolzini imen), zato so
   celice vedno na istem mestu (si.turnirko.branje.GeometrijaListka).

   Zgoraj stoji znamka Turnirko in okence za stevilko mize (S1: bralnik ga
   prebere), pod njo kategorija z dogodkom ter skupina oz. faza; spodaj crta za
   ime sodnika in podpis. Sodnik listek izpolni z roko za mizo; izid prebere
   branje listkov s fotografije ali ga organizator vnese prek obicajnega vnosa.

   Listek je namenoma crno-bel ne glede na temo (natis na papir), zato tu ne
   uporabljamo temo odvisnih spremenljivk. Natis na list A4 (6 listkov, rezi)
   urejata ListkiStran in razdelek 16 v slog.css. Ko sodniki slikajo listke,
   dobi list hrbtno stran (HrbetListka spodaj). */
import type { CSSProperties } from 'react'

import { natisnjenaKoda, potQr, vsebinaQr } from '../pomozno/kodaListka'
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
  /* Id tekme za QR kodo in natisnjeno stevilko (branje listka s fotografije);
     null pri praznem listku, ki tekme se nima. */
  idTekme: number | null
  /* Vsebina QR kode s skrivnostjo (»TK:<id>:<skrivnost>«, S1) s streznika; brez nje
     zasilno »TK:<id>«, ki ga prebere pult, sodniska pot (S2) pa ne. */
  kodaQr?: string | null
  stran1: StranListka
  stran2: StranListka
  /* Prazen listek: imena igralcev in skupino oz. fazo sodnik dopise z roko
     (organizator natisne zalogo listkov, ko igralcev se ne pozna). */
  prazno?: boolean
}

export function Listek({ podatki }: { podatki: ListekPodatki }) {
  const nizi = Array.from({ length: podatki.steviloNizov }, (_, i) => i + 1)

  return (
    <article
      className={'listek' + (podatki.steviloNizov > 5 ? ' listek--gosto' : '')}
      style={{ '--nizov': podatki.steviloNizov } as CSSProperties}
    >
      {/* Oznake za branje s fotografije: tri črni kvadrati v vogalih (zgoraj levo,
          spodaj levo, spodaj desno) in QR koda zgoraj desno stojijo na STALNIH
          mestih listka (mm). Strežnik po njih fotografijo poravna in najde celice
          (si.turnirko.branje.GeometrijaListka) - ob premiku spremeni oboje. */}
      <Oznaka kje="zl" />
      <Oznaka kje="sl" />
      <Oznaka kje="sd" />
      {podatki.idTekme !== null && (
        <KodaTekme idTekme={podatki.idTekme} vsebina={podatki.kodaQr ?? vsebinaQr(podatki.idTekme)} />
      )}
      <OkenceMize miza={podatki.miza} />

      {/* Vrh listka: glava in kontekst levo od okenca mize (do 60 mm). */}
      <div className="listek__vrh">
        <header className="listek__glava">
          <span className="listek__znamka">
            <ZnakTurnirko velikost={18} />
            Turnirko
          </span>
        </header>

        {/* Dve vrstici: kategorija (z dogodkom za piko, ki se ob pomanjkanju
            prostora odreže) ter skupina in kolo oz. faza. Prazen listek ima
            črto za »Skupina 1, 2. kolo« ali »Polfinale«. */}
        <div className="listek__kontekst">
          <Polje
            oznaka="Kategorija"
            vrednost={podatki.dogodek ? `${podatki.kategorija} · ${podatki.dogodek}` : podatki.kategorija}
            sirok
          />
          {podatki.prazno ? (
            <Polje oznaka="Faza" vrednost="" sirok prazno />
          ) : podatki.skupina ? (
            <>
              <Polje oznaka="Skupina" vrednost={podatki.skupina} poudarjeno />
              <Polje oznaka={null} vrednost={podatki.faza} poudarjeno />
            </>
          ) : (
            <Polje oznaka="Faza" vrednost={podatki.faza} poudarjeno sirok />
          )}
        </div>
      </div>

      <div className="listek__tabela">
        <div className="listek__vrsta listek__vrsta--imena">
          <Ime ime={podatki.stran1.ime} stran="levo" />
          <span className="listek__dvopicje">:</span>
          <Ime ime={podatki.stran2.ime} stran="desno" />
        </div>

        {/* Prvi servis ob 1. nizu, timeout ob 2. (pri enem nizu oba ob njem), na
            zunanji strani stolpca: sodnik ju označi s križcem, ko se zgodita.
            Vsak igralec (par) ima en timeout. Do celice je vsaj 11 mm, da križec
            ne pride v polje za točke. */}
        <div className="listek__nizi">
          {nizi.map((n) => {
            const znaki: Znak[] = nizi.length === 1 ? ['servis', 'timeout'] : n === 1 ? ['servis'] : n === 2 ? ['timeout'] : []
            return (
              <div key={n} className="listek__vrsta listek__vrsta--niz">
                <Znaki znaki={znaki} />
                <span className="listek__niz-oznaka">{n}. niz</span>
                <span className="listek__celica" />
                <span className="listek__dvopicje">:</span>
                <span className="listek__celica" />
                <Znaki znaki={znaki} desno />
              </div>
            )
          })}
        </div>

        <div className="listek__vrsta listek__vrsta--rezultat">
          <span />
          <span className="listek__rezultat-oznaka">Rezultat</span>
          <span className="listek__celica" />
          <span className="listek__dvopicje">:</span>
          <span className="listek__celica" />
          <span />
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

/* Hrbtna stran listka (S2): koda QR do strani za sodnika (/s) in trije koraki.
   Natisne se na hrbet lista, ko sodniki slikajo listke (ListkiStran). Koda je na
   vseh hrbtih ista in ne nosi nič skrivnega - pravico poslati izid da šele koda
   na sprednji strani. Vsebina ostane v sredini mesta (rob 20 mm zgoraj in spodaj,
   15 mm levo in desno, slog.css): pri obeh načinih obojestranskega tiska (obrat
   po dolgem ali kratkem robu) prosojno sije le za tabelo točk, oznake, QR in
   okence mize na sprednji strani pa ostanejo čisti. Da tabele ne moti, meri
   SimuliraneFotografijeTest.hrbetNeMotiBranja. */
export function HrbetListka({ naslov }: { naslov: string }) {
  const { velikost, pot } = potQr(naslov, false)
  return (
    <article className="listek-hrbet">
      <p className="listek-hrbet__naslov">
        <ZnakTurnirko velikost={16} />
        Sodnik? Izid pošlji s telefonom.
      </p>
      <svg
        className="listek-hrbet__qr"
        viewBox={`0 0 ${velikost} ${velikost}`}
        shapeRendering="crispEdges"
        role="img"
        aria-label={naslov}
      >
        <path d={pot} />
      </svg>
      <p className="listek-hrbet__stran">{naslov.replace(/^https?:\/\//, '')}</p>
      <ol className="listek-hrbet__koraki">
        <li>Po tekmi skeniraj to kodo s kamero telefona.</li>
        <li>Obrni listek, položi ga na mizo in ga fotografiraj od zgoraj, celega.</li>
        <li>
          Preveri točke in pritisni <strong>Potrdi izid</strong>. Listek oddaj na pultu.
        </li>
      </ol>
    </article>
  )
}

/* Poln črn kvadrat 4 x 4 mm. Vektorski lik, ker se rob ali ozadje zaokrožita na
   cele pike zaslona (v sredini ostane bela pika) in ozadja brskalnik ne natisne. */
function Oznaka({ kje }: { kje: 'zl' | 'sl' | 'sd' }) {
  return (
    <svg className={`listek__kot listek__kot--${kje}`} viewBox="0 0 1 1" aria-hidden="true">
      <rect width="1" height="1" />
    </svg>
  )
}

/* Okence za številko mize na STALNEM mestu glave (S1): bralnik ga poišče po robovih
   in prebere z isto mrežo kot točke (si.turnirko.branje.GeometrijaListka.MIZA) -
   ob premiku spremeni oboje. Kadar je miza ob tisku že znana, je natisnjena v
   okencu; sicer jo vpiše organizator ob klicu ali sodnik za mizo. */
function OkenceMize({ miza }: { miza: number | null }) {
  return (
    <>
      <span className="listek__miza-oznaka">Miza</span>
      <span className="listek__miza-okence">{miza !== null ? miza : ''}</span>
    </>
  )
}

/* QR koda tekme in njena številka. Sodnik ali organizator listek po tekmi
   fotografira, strežnik po kodi ve, katera tekma je na njem, skrivnost v njej pa
   sodniku da pravico poslati izid (branje listkov, docs/pravila/branje-listkov.md).
   Številka stoji pod okencem mize (ne pod kodo), da ime desnega igralca lahko
   začne takoj pod mirno cono kode. */
function KodaTekme({ idTekme, vsebina }: { idTekme: number; vsebina: string }) {
  const { velikost, pot } = qrAliZasilna(idTekme, vsebina)
  return (
    <>
      <span className="listek__koda">
        <svg
          className="listek__qr"
          viewBox={`0 0 ${velikost} ${velikost}`}
          shapeRendering="crispEdges"
          aria-hidden="true"
        >
          <path d={pot} />
        </svg>
      </span>
      <span className="listek__stevilka">{natisnjenaKoda(idTekme)}</span>
    </>
  )
}

/* Koda s skrivnostjo gre v QR verzije 1 do id-ja z 8 števkami; daljši zapis bi
   potQr zavrnil in stran za tisk bi ostala prazna. Takrat zasilna koda brez
   skrivnosti (pult jo prebere, sodnik ne). */
function qrAliZasilna(idTekme: number, vsebina: string) {
  try {
    return potQr(vsebina)
  } catch {
    return potQr(vsebinaQr(idTekme))
  }
}

/* Vrednost, daljša od tega, se v eni vrstici (do okenca mize) ne prilega pri
   običajni velikosti; daljša se skrči, nato odreže. */
const NAJDALJSE_PRI_OBICAJNI_VELIKOSTI = 34

/* Oznaka in vrednost v glavi listka. */
function Polje({
  oznaka,
  vrednost,
  sirok = false,
  poudarjeno = false,
  prazno = false,
}: {
  /* null: vrednost stoji brez oznake, desno od prejšnjega polja (kolo ob skupini). */
  oznaka: string | null
  vrednost: string
  /* Vrednost zavzame ostalo vrstico (dogodek, kategorija). */
  sirok?: boolean
  poudarjeno?: boolean
  /* Prazna črta za ročni vpis namesto besedila. */
  prazno?: boolean
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
          (prazno ? ' listek__vrednost--prazno' : '') +
          (dolga ? ' listek__vrednost--dolga' : '')
        }
      >
        {vrednost}
      </span>
    </>
  )
}

/* Glava stolpca: levo ime je poravnano proti dvopičju (nad svojo celico), desno
   tudi. Dvojica ima vsakega igralca v svoji vrstici, ker bi se par v enem
   stolpcu prelomil sredi imena. */
function Ime({ ime, stran }: { ime: string; stran: 'levo' | 'desno' }) {
  const igralci = ime.split(' / ')
  return (
    <span
      className={
        'listek__ime listek__ime--' + stran + (igralci.length > 1 ? ' listek__ime--par' : '')
      }
    >
      {igralci.map((igralec, i) => (
        <span key={i} className="listek__ime-vrstica">
          {igralec}
        </span>
      ))}
    </span>
  )
}

type Znak = 'servis' | 'timeout'

const OZNAKE_ZNAKOV: Record<Znak, string> = { servis: 'Prvi servis', timeout: 'Timeout' }

/* Kvadratek za križec na zunanji strani stolpca (levo igralec 1, desno igralec 2). */
function Znaki({ znaki, desno = false }: { znaki: Znak[]; desno?: boolean }) {
  return (
    <span className={'listek__znaki' + (desno ? ' listek__znaki--desno' : '')}>
      {znaki.map((z) => (
        <span key={z} className="listek__znak">
          <span className="listek__kvadratek" />
          {OZNAKE_ZNAKOV[z]}
        </span>
      ))}
    </span>
  )
}
