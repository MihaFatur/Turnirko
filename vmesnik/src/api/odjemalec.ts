/* Osrednji ovoj okoli fetch.

   Vse zahteve gredo skozi to datoteko, da so glave, serializacija in
   obravnava napak na enem mestu. Zaledje ob napaki vraca problem-detail
   (RFC 9457) s slovenskim opisom v polju "detail" - tu ga prevedemo v
   izjemo NapakaStreznika, ki jo obrazci prikazejo uporabniku. */

const OSNOVNA_POT = '/api/v1'

/* Poverilnice administratorja za HTTP Basic (base64 "ime:geslo").
   Hranijo se v pomnilniku; nastavi jih avtentikacijski kontekst ob prijavi.
   Gost jih nima - njegove zahteve gredo brez glave Authorization. */
let poverilnice: string | null = null

export function nastaviPoverilnice(osnovaBase64: string | null) {
  poverilnice = osnovaBase64
}

/* Kaj se zgodi, ko strežnik poverilnice zavrne (401 na zahtevi, ki jih je
   nosila): geslo je bilo spremenjeno ali račun izklopljen, zavihek pa je ostal
   odprt. Poslušalca nastavi avtentikacijski kontekst, ki uporabnika vrne v
   stanje gosta.

   Zakaj osrednje in ne v vsaki poizvedbi: strežnik šteje vsako zahtevo z
   napačnimi poverilnicami kot neuspelo prijavo (PrijavaDogodki) in po desetih
   račun s tega naslova IP za 15 minut zavrača s 429 (OmejitevPrijavFilter) -
   tudi njegovega lastnika z novim, pravilnim geslom. Zavihek, ki je glavo
   pošiljal naprej (stran turnirja se osvežuje na 15 s), je to blokado
   obnavljal, dokler je bil odprt. */
let obNeveljavnih: (() => void) | null = null

export function obNeveljavnihPoverilnicah(poslusalec: (() => void) | null) {
  obNeveljavnih = poslusalec
}

export class NapakaStreznika extends Error {
  constructor(
    /* HTTP koda odgovora (400, 404, 409 ...). */
    public readonly stanje: number,
    /* Kratek naslov napake iz polja "title". */
    public readonly naslov: string,
    sporocilo: string,
  ) {
    super(sporocilo)
    this.name = 'NapakaStreznika'
  }
}

/* Iz poljubne napake sestavi besedilo, primerno za prikaz uporabniku. */
export function opisNapake(napaka: unknown): string {
  if (napaka instanceof NapakaStreznika) return napaka.message
  if (napaka instanceof Error && napaka.message) return 'Povezava s strežnikom ni uspela.'
  return 'Prišlo je do nepričakovane napake.'
}

async function zahteva<T>(pot: string, metoda: string, telo?: unknown): Promise<T> {
  /* Obrazec z datoteko (nalaganje slike) gre kot multipart: mejo med deli
     zapiše brskalnik sam v Content-Type, zato glave ne smemo postaviti. */
  const obrazec = telo instanceof FormData
  const glave: Record<string, string> = {}
  if (telo !== undefined && !obrazec) glave['Content-Type'] = 'application/json'
  /* Poverilnice te zahteve: ob odgovoru so lahko v modulu že druge. */
  const poslane = poverilnice
  if (poslane) glave['Authorization'] = `Basic ${poslane}`

  const odgovor = await fetch(OSNOVNA_POT + pot, {
    method: metoda,
    headers: Object.keys(glave).length > 0 ? glave : undefined,
    body: obrazec ? telo : telo !== undefined ? JSON.stringify(telo) : undefined,
  })

  if (!odgovor.ok) {
    /* Zavrnjene poverilnice se zavržejo ENKRAT: primerjava s trenutnimi
       poskrbi, da več hkratnih zahtev (stran naloži deset poizvedb naenkrat)
       poslušalca ne pokliče desetkrat in da pozen odgovor stare zahteve ne
       odnese poverilnic, s katerimi se je uporabnik medtem prijavil znova.
       Z zavrnjenimi poverilnicami ni nobenega ponovnega poskusa: naslednje
       zahteve gredo brez glave, kot gost. 401 brez poslanih poverilnic je
       gost na zaščiteni poti in ostane navadna napaka. */
    if (odgovor.status === 401 && poslane !== null) {
      if (poslane === poverilnice) {
        poverilnice = null
        obNeveljavnih?.()
      }
      /* Branje se ENKRAT ponovi brez glave: javna stran (turnir, lestvica) se
         tako gledalcu izriše takoj, namesto da bi obstala pri »nimaš pravic«.
         Zanke ni - ponovitev poverilnic ne nosi, zato sem ne pride več - in
         strežnik je ne šteje kot neuspelo prijavo. Pisanja ne ponavljamo:
         brez prijave ga strežnik zavrne. */
      if (metoda === 'GET' && poverilnice === null) return zahteva<T>(pot, metoda)
    }
    let naslov = 'Napaka'
    let podrobnost = `Strežnik je vrnil napako ${odgovor.status}.`
    try {
      const problem = await odgovor.json()
      if (typeof problem.title === 'string') naslov = problem.title
      if (typeof problem.detail === 'string') podrobnost = problem.detail
    } catch {
      /* telo ni JSON - obdrzimo splosno sporocilo */
    }
    throw new NapakaStreznika(odgovor.status, naslov, podrobnost)
  }

  /* 204 No Content (npr. arhiviranje) nima telesa. */
  if (odgovor.status === 204) return undefined as T
  return odgovor.json() as Promise<T>
}

export const api = {
  vrni: <T>(pot: string) => zahteva<T>(pot, 'GET'),
  objavi: <T>(pot: string, telo?: unknown) => zahteva<T>(pot, 'POST', telo),
  posodobi: <T>(pot: string, telo: unknown) => zahteva<T>(pot, 'PUT', telo),
  /* Večina brisanj vrne 204 brez telesa; umik preklopa naročnine vrne
     posodobljeno naročnino, zato je tip odgovora izbiren. */
  izbrisi: <T = void>(pot: string) => zahteva<T>(pot, 'DELETE'),
}
