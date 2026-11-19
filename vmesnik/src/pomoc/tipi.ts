/* Vsebina pomoči za organizatorje (16. 11. 2026, naročilo lastnika).

   Navodila so PODATKI in ne JSX: isto besedilo izriše oblaček ob gumbu »?«,
   predal ob zaslonu in stran /pomoc, iskalnik pa ga lahko prebere v celoti.
   Sheme (narisani prikazi) so komponente, zato jih vsebina našteje samo po
   ključu (`sheme/index.tsx`); test `pomoc.test.ts` preveri, da vsak ključ,
   vsaka povezava in vsak gumb »?« v kodi kaže na nekaj, kar obstaja.

   Besedilo pozna dve oznaki v vrstici (`Besedilo.tsx`):
   - `**krepko**` za ime gumba ali polja, kot ga organizator vidi na zaslonu;
   - `[besedilo](id-clanka)` ali `[besedilo](id-clanka#razdelek)` za skok na
     drug članek. */
import type { KljucSheme } from './sheme/kljuci'

export type IdPoglavja =
  | 'vodici'
  | 'zacetek'
  | 'turnir'
  | 'rezultati'
  | 'liga'
  | 'tisk'
  | 'tekmovanje'
  | 'admin'

export interface Poglavje {
  id: IdPoglavja
  naslov: string
  /* Ena vrstica pod naslovom poglavja na strani Pomoč. */
  opis: string
  samoAdmin?: boolean
}

export type Blok =
  | { tip: 'odstavek'; besedilo: string }
  /* Podnaslov z ID-jem: nanj lahko kaže gumb »?« (`razdelek`) in povezava
     `[…](clanek#razdelek)`. `kratko` je besedilo oblačka, kadar gumb kaže
     prav na ta razdelek; brez njega oblaček vzame `kratko` članka. */
  | { tip: 'razdelek'; id: string; naslov: string; kratko?: string; shemaNamiga?: KljucSheme }
  | { tip: 'koraki'; postavke: string[] }
  | { tip: 'seznam'; postavke: string[] }
  | { tip: 'shema'; shema: KljucSheme; opis?: string }
  /* Kaj ne gre nazaj ali kje se organizator najpogosteje zmoti (rjasto). */
  | { tip: 'pazi'; besedilo: string }
  | { tip: 'nasvet'; besedilo: string }
  /* Pot klikov do funkcije: ['Turnir', 'kategorija', 'Izvedi žreb']. */
  | { tip: 'kje'; pot: string[] }
  | { tip: 'tabela'; glava: string[]; vrstice: string[][] }

export interface Clanek {
  id: string
  naslov: string
  poglavje: IdPoglavja
  /* Dve do tri povedi: oblaček ob gumbu »?« in vrstica v kazalu. */
  kratko: string
  /* Besede, ki jih organizator vpiše, čeprav jih v besedilu ni (»seed«,
     »nosilec«, »bye«). */
  kljucne?: string[]
  samoAdmin?: boolean
  /* Majhen prikaz v oblačku (če ga ni, je oblaček samo besedilo). */
  shemaNamiga?: KljucSheme
  vsebina: Blok[]
  povezani?: string[]
}

/* Kaj pokaže oblaček ob gumbu »?«. */
export interface VsebinaNamiga {
  naslov: string
  besedilo: string
  shema?: KljucSheme
}
