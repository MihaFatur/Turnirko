/* Iskanje po imenu nad seznamom: lestvica, turnirji, lige (in prijave dogodka).

   Na namizju je iskalnik polje skrajno desno v naslovni vrstici seznama, števec
   seznama pa pod njim - krmilo TE tabele in ne svoj pas nad njo. Na telefonu je preklopnik »Išči« v vrsti
   naslova strani, desno ob njem (»Turnirji … Išči«), pas s poljem pa se odpre
   pod debelo črto lepljive glave šele na klik: stalno polje nad seznamom bi
   stalo 60 px zaslona, iskanje pa je redko opravilo. Pas ostane v glavi, da
   polje med drsenjem po zadetkih ne uide z zaslona.

   Vpisano živi v stanju strani in ne v naslovu - je opravilo enega obiska, ne
   stanje, ki bi ga kdo delil s povezavo. Stran z njim zoži seznam PRED filtri,
   zato so števci ob merilih števci tega, kar gledalec vidi. */
import { type ReactNode, useState } from 'react'

import { GlavaNaslov } from './GlavaTelefona'

interface LastnostiIskanja {
  iskanje: string
  naIskanje: (iskanje: string) => void
  /* Po čem se išče, z malo začetnico (»po imenu ali klubu«) - iz tega sta
     napis v polju in oznaka za bralnik zaslona. */
  poCem: string
}

/* Polje in pod njim števec seznama (»329 turnirjev«), oba na desnem robu.
   Stran ga postavi kot ZADNJEGA otroka .naslovna-vrstica__desno--iskanje, da
   stoji skrajno desno tudi ob gumbu urejevalca. Brez števca (lestvica, ki se
   še nalaga) ostane samo polje. */
export function IskalnikSeznama({
  iskanje,
  naIskanje,
  poCem,
  stevec,
}: LastnostiIskanja & { stevec?: ReactNode }) {
  return (
    <div className="naslovna-vrstica__iskanje">
      <input
        className="iskalnik iskalnik--kratek"
        type="search"
        value={iskanje}
        onChange={(dogodek) => naIskanje(dogodek.target.value)}
        placeholder={`išči ${poCem}`}
        aria-label={`Išči ${poCem}`}
      />
      {stevec != null && <span className="sekcija__meta">{stevec}</span>}
    </div>
  )
}

/* Preklopnik in pas s poljem. Preklopnik se izriše tam, kamor ga postavi
   stran (v .naslov-mobi__vrsta ob h1), pas pa gre skozi portal v glavo.
   Dejanja urejevalca (»+ Turnir«) ostanejo v glavi in jih stran vloži sama
   skozi GlavaDejanja. */
export function IskanjeTelefona({ iskanje, naIskanje, poCem }: LastnostiIskanja) {
  const [odprto, nastaviOdprto] = useState(false)

  /* Preklic izbriše iskanje in pas zapre: pas, ki ostane odprt s praznim
     poljem, gledalcu jemlje 68 px zaslona za nič. */
  const zapri = () => {
    naIskanje('')
    nastaviOdprto(false)
  }

  return (
    <>
      {/* Mere (36 px vidno, 44 px zadetek) so iste kot pri gumbih glave,
          zato si razred deli z njimi, čeprav stoji ob naslovu. */}
      <button
        type="button"
        className="glava-telefon__gumb glava-telefon__gumb--preklop"
        aria-pressed={odprto}
        onClick={() => (odprto ? zapri() : nastaviOdprto(true))}
      >
        Išči
      </button>

      {odprto && (
        <GlavaNaslov>
          <div className="iskanje-mobi">
            <input
              className="iskalnik"
              /* Pas se odpre na gledalčevo dejanje, zato tipkovnica sme priti
                 z njim - drugega opravila v pasu ni. */
              autoFocus
              aria-label={`Išči ${poCem}`}
              placeholder={`Išči ${poCem} …`}
              value={iskanje}
              onChange={(dogodek) => naIskanje(dogodek.target.value)}
            />
            <button type="button" className="iskanje-mobi__preklic" onClick={zapri}>
              Prekliči
            </button>
          </div>
        </GlavaNaslov>
      )}
    </>
  )
}
