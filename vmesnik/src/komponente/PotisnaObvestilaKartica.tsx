/* »Obvestila med turnirjem« (V60, paket Navijač in Igralec Premium): potisno
   obvestilo v telefon ali računalnik, ko je odigrana tekma igralca, ki ga račun
   spremlja ali ima v družini. Brez aplikacije (Web Push).

   Vklop velja za TO napravo (brskalnik): oseba ima lahko več naprav, vsaka se vklopi
   posebej. Kartica se pokaže samo računu s paketom in samo, če ima strežnik ključe
   VAPID (usePotisnaObvestila.naVoljo); na iPhonu brez dodane strani na začetni zaslon
   Push ne obstaja in kartica to pove. Preizkusno obvestilo pokaže, da deluje, preden
   to ugotovi prva prava tekma. */
import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'

import { narocninaApi } from '../api/zahteve'
import { usePotisnaObvestila } from '../pomozno/potisnaObvestila'

export function PotisnaObvestilaKartica() {
  const narocnina = useQuery({ queryKey: ['narocnina'], queryFn: narocninaApi.pregled })
  const smem = !!narocnina.data?.aktivna
    && (narocnina.data.paket === 'NAVIJAC' || narocnina.data.paket === 'PREMIUM')
  const potisna = usePotisnaObvestila()
  const [preizkusPoslan, nastaviPreizkus] = useState<number | null>(null)

  if (!smem || !potisna.naVoljo) return null

  return (
    <div id="obvestila-v-telefonu" className="potisna-kartica">
      <div className="naslovna-vrstica">
        <h2>Obvestila med turnirjem</h2>
      </div>
      <p className="namig">
        Ko je odigrana tekma igralca, ki ga spremljaš ali je v tvoji družini, dobiš kratko obvestilo v
        telefon ali računalnik: kdo je zmagal, s kakšnim izidom in kako se je spremenil rating.
        Vklop velja za to napravo.
      </p>

      {potisna.stanje === 'nepodprto' && (
        <p className="obvestilo">
          Ta brskalnik potisnih obvestil ne podpira. Na iPhonu najprej dodaj stran na začetni zaslon
          (Deli → Dodaj na začetni zaslon) in jo odpri od tam.
        </p>
      )}
      {potisna.stanje === 'zavrnjeno' && (
        <p className="obvestilo">
          Obvestila so v brskalniku blokirana. Dovoli jih v nastavitvah strani (ključavnica ob naslovu) in
          poskusi znova.
        </p>
      )}
      {(potisna.stanje === 'izklopljeno' || potisna.stanje === 'vklopljeno') && (
        <div className="obrazec__gumbi potisna-kartica__gumbi">
          {potisna.stanje === 'izklopljeno' ? (
            <button type="button" className="gumb gumb--glavni" disabled={potisna.zaposlen}
                    onClick={() => void potisna.vklopi()}>
              Vklopi obvestila na tej napravi
            </button>
          ) : (
            <>
              <span className="potisna-kartica__stanje">Vklopljena na tej napravi</span>
              <button type="button" className="gumb" disabled={potisna.zaposlen}
                      onClick={() => void potisna.preizkus().then(nastaviPreizkus)}>
                Pošlji preizkus
              </button>
              <button type="button" className="gumb" disabled={potisna.zaposlen}
                      onClick={() => void potisna.izklopi()}>
                Izklopi
              </button>
            </>
          )}
        </div>
      )}
      {preizkusPoslan !== null && (
        <p className="namig">
          {preizkusPoslan > 0
            ? `Preizkusno obvestilo je poslano na ${preizkusPoslan} ${preizkusPoslan === 1 ? 'napravo' : 'naprave'}.`
            : 'Preizkusno obvestilo ni bilo poslano nobeni napravi.'}
        </p>
      )}
      {potisna.napaka && <div className="napaka" role="alert">{potisna.napaka}</div>}
    </div>
  )
}
