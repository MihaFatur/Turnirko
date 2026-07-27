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
  const glave: Record<string, string> = {}
  if (telo !== undefined) glave['Content-Type'] = 'application/json'
  if (poverilnice) glave['Authorization'] = `Basic ${poverilnice}`

  const odgovor = await fetch(OSNOVNA_POT + pot, {
    method: metoda,
    headers: Object.keys(glave).length > 0 ? glave : undefined,
    body: telo !== undefined ? JSON.stringify(telo) : undefined,
  })

  if (!odgovor.ok) {
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
  izbrisi: (pot: string) => zahteva<void>(pot, 'DELETE'),
}
