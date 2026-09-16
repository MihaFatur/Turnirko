/* Iskanje po imenu nad seznamom: lestvica, turnirji, lige.

   Na namizju je iskalnik polje v naslovni vrstici seznama, ob števcu - krmilo
   TE tabele in ne svoj pas nad njo. Na telefonu je preklopnik »Išči« v
   lepljivi glavi, pas s poljem pa se odpre pod debelo črto šele na klik:
   stalno polje nad seznamom bi stalo 60 px zaslona, iskanje pa je redko
   opravilo.

   Vpisano živi v stanju strani in ne v naslovu - je opravilo enega obiska, ne
   stanje, ki bi ga kdo delil s povezavo. Stran z njim zoži seznam PRED filtri,
   zato so števci ob merilih števci tega, kar gledalec vidi. */
import { useState, type ReactNode } from 'react'

import { GlavaDejanja, GlavaNaslov } from './GlavaTelefona'

interface LastnostiIskanja {
  iskanje: string
  naIskanje: (iskanje: string) => void
  /* Po čem se išče, z malo začetnico (»po imenu ali klubu«) - iz tega sta
     napis v polju in oznaka za bralnik zaslona. */
  poCem: string
}

export function IskalnikSeznama({ iskanje, naIskanje, poCem }: LastnostiIskanja) {
  return (
    <input
      className="iskalnik iskalnik--kratek"
      type="search"
      value={iskanje}
      onChange={(dogodek) => naIskanje(dogodek.target.value)}
      placeholder={`išči ${poCem}`}
      aria-label={`Išči ${poCem}`}
    />
  )
}

/* Preklopnik v glavi in pas s poljem. Ostala dejanja glave strani (»+ Turnir«)
   gredo skozi `dejanja` v ISTI portal: dva portala v isti cilj bi vrstni red
   gumbov prepustila trenutku, ko se kateri izriše (dejanje urejevalca pride
   šele z naloženo prijavo). */
export function IskanjeTelefona({
  iskanje,
  naIskanje,
  poCem,
  dejanja,
}: LastnostiIskanja & { dejanja?: ReactNode }) {
  const [odprto, nastaviOdprto] = useState(false)

  /* Preklic izbriše iskanje in pas zapre: pas, ki ostane odprt s praznim
     poljem, gledalcu jemlje 68 px zaslona za nič. */
  const zapri = () => {
    naIskanje('')
    nastaviOdprto(false)
  }

  return (
    <>
      <GlavaDejanja>
        <button
          type="button"
          className="glava-telefon__gumb glava-telefon__gumb--preklop"
          aria-pressed={odprto}
          onClick={() => (odprto ? zapri() : nastaviOdprto(true))}
        >
          Išči
        </button>
        {dejanja}
      </GlavaDejanja>

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
