/* Vrsta fotografij listkov brez signala (branje listkov F5.2, docs/NACRT-BRANJE-LISTKOV.md).

   V dvorani signal pogosto izgine. Fotografija zato ne gre naravnost na strežnik:
   najprej se shrani v brskalnik (IndexedDB), nato se pošilja, dokler ne uspe.
   - Napaka omrežja, časovna meja, 5xx, 408 in 429: poskusi znova z naraščajočim
     zamikom (2 s, 4 s … največ 1 min), takoj ko brskalnik spet javi povezavo.
   - 401 (prijava ni več veljavna): fotografija počaka v vrsti do nove prijave.
   - Ostali 4xx (fotografija ni listek, turnir zaprt …): ne ponavlja, pokaže razlog.
   Vsaka fotografija ima naključni ključ (glava Idempotency-Key): če je strežnik
   fotografijo prejel, odgovor pa se je izgubil, ponovitev vrne isto branje in ne
   ustvari dvojnika (BranjeListkovStoritev.sprejmi, V73).

   Zasebnost (V4): fotografija nosi imena igralcev, zato se iz brskalnika izbriše
   takoj po uspehu, najkasneje po 24 urah in ob odjavi. Pošilja se samo na strani
   branja listkov turnirja, na katerem je bila posneta, in samo za uporabnika, ki
   jo je posnel. Brez IndexedDB (zasebno okno starejšega brskalnika) vrsta živi
   samo v pomnilniku strani. */
import { useCallback, useEffect, useRef, useState } from 'react'

import { NapakaStreznika, opisNapake } from '../api/odjemalec'
import type { BranjeListkaDto } from '../api/tipi'
import { opisNapakeFotografije, pripraviFotografijo } from './branjeListkov'

/* Fotografija v vrsti, kot je shranjena v brskalniku. */
export interface ZapisVrste {
  kljuc: string
  idTurnir: number
  idUporabnika: number
  jpeg: Blob
  /* Date.now() ob fotografiranju. */
  ustvarjeno: number
}

/* Toliko časa vrsta hrani neposlano fotografijo (strežnik ključ hrani enako dolgo:
   BranjeListkovStoritev.KLJUC_POSILJANJA_VELJA). */
export const VELJA_MS = 24 * 60 * 60 * 1000
export const ZAMIK_ZACETNI_MS = 2000
export const ZAMIK_NAJVEC_MS = 60_000
/* Strežnik, ki je zaseden, pove Retry-After: 5. */
const ZAMIK_ZASEDEN_MS = 5000
/* Pošiljanje fotografije (~0,5 MB) po šibkem signalu in čakanje na dekodiranje
   (strežnik čaka do 10 s); dlje viseča zahteva se prekine in ponovi. */
export const CASOVNA_MEJA_MS = 45_000

export type OdzivNaNapako = 'ponovi' | 'ustavi' | 'zavrni'

/* Kaj narediti z napako pošiljanja (glej uvod). Vse, kar ni odgovor strežnika
   (TypeError »Failed to fetch«, prekinitev ob časovni meji, neberljiv odgovor),
   je napaka omrežja: ponovi - ključ poskrbi, da ne nastane dvojnik. */
export function odzivNaNapako(napaka: unknown): OdzivNaNapako {
  if (!(napaka instanceof NapakaStreznika)) return 'ponovi'
  const s = napaka.stanje
  if (s >= 500 || s === 408 || s === 429) return 'ponovi'
  // 403: dostop je lahko le začasno izklopljen (preizkus samo za admina) - ne briši
  if (s === 401 || s === 403) return 'ustavi'
  return 'zavrni'
}

/* Zamik pred naslednjim poskusom po »zaporednih« neuspehih zapored (od 1). */
export function zamikPonovitve(zaporednih: number, napaka?: unknown): number {
  const zamik = Math.min(ZAMIK_NAJVEC_MS, ZAMIK_ZACETNI_MS * 2 ** Math.max(0, zaporednih - 1))
  const zaseden = napaka instanceof NapakaStreznika && (napaka.stanje === 503 || napaka.stanje === 429)
  return zaseden ? Math.max(zamik, ZAMIK_ZASEDEN_MS) : zamik
}

export function jePretekel(zapis: Pick<ZapisVrste, 'ustvarjeno'>, zdaj: number): boolean {
  return zdaj - zapis.ustvarjeno > VELJA_MS
}

/* Naključni ključ pošiljanja (strežnik sprejme [A-Za-z0-9-]{8,64}). */
export function novKljucPosiljanja(): string {
  if (typeof crypto.randomUUID === 'function') return crypto.randomUUID()
  const bajti = crypto.getRandomValues(new Uint8Array(16))
  return [...bajti].map((b) => b.toString(16).padStart(2, '0')).join('')
}

/* »3 fotografije čakajo na signal.« (1 fotografija čaka, 2 fotografiji čakata,
   3/4 fotografije čakajo, 5+ fotografij čaka; po ostanku pri 100). */
export function besediloCakajocih(n: number): string {
  const ostanek = n % 100
  if (ostanek === 1) return `${n} fotografija čaka na signal.`
  if (ostanek === 2) return `${n} fotografiji čakata na signal.`
  if (ostanek === 3 || ostanek === 4) return `${n} fotografije čakajo na signal.`
  return `${n} fotografij čaka na signal.`
}

// ---------- Shramba v brskalniku ----------

const IME_BAZE = 'turnirko-vrsta'
const SHRAMBA = 'fotografije'

let baza: Promise<IDBDatabase | null> | null = null
/* Nadomestek, kadar IndexedDB ni na voljo ali zapis vanjo ne uspe. */
const spomin = new Map<string, ZapisVrste>()

function odpriBazo(): Promise<IDBDatabase | null> {
  baza ??= new Promise((razresi) => {
    try {
      if (typeof indexedDB === 'undefined') return razresi(null)
      const zahteva = indexedDB.open(IME_BAZE, 1)
      zahteva.onupgradeneeded = () => {
        zahteva.result.createObjectStore(SHRAMBA, { keyPath: 'kljuc' })
      }
      zahteva.onsuccess = () => razresi(zahteva.result)
      zahteva.onerror = () => razresi(null)
      zahteva.onblocked = () => razresi(null)
    } catch {
      razresi(null)
    }
  })
  return baza
}

function vShrambi<T>(nacin: IDBTransactionMode, delo: (s: IDBObjectStore) => IDBRequest<T>): Promise<T> {
  return odpriBazo().then(
    (b) =>
      new Promise<T>((razresi, zavrni) => {
        if (!b) return zavrni(new Error('IndexedDB ni na voljo.'))
        const transakcija = b.transaction(SHRAMBA, nacin)
        const zahteva = delo(transakcija.objectStore(SHRAMBA))
        transakcija.oncomplete = () => razresi(zahteva.result)
        transakcija.onerror = () => zavrni(transakcija.error ?? new Error('Zapis v brskalnik ni uspel.'))
        transakcija.onabort = () => zavrni(transakcija.error ?? new Error('Zapis v brskalnik ni uspel.'))
      }),
  )
}

export const shrambaVrste = {
  /* true: fotografija preživi zaprtje strani; false: samo v pomnilniku. */
  async shrani(zapis: ZapisVrste): Promise<boolean> {
    try {
      await vShrambi('readwrite', (s) => s.put(zapis))
      return true
    } catch {
      spomin.set(zapis.kljuc, zapis)
      return false
    }
  },

  async izbrisi(kljuc: string): Promise<void> {
    spomin.delete(kljuc)
    try {
      await vShrambi('readwrite', (s) => s.delete(kljuc))
    } catch {
      /* ni shranjena ali IndexedDB ni na voljo */
    }
  },

  async vsi(): Promise<ZapisVrste[]> {
    let shranjeni: ZapisVrste[] = []
    try {
      shranjeni = await vShrambi<ZapisVrste[]>('readonly', (s) => s.getAll() as IDBRequest<ZapisVrste[]>)
    } catch {
      /* samo pomnilnik */
    }
    const kljuci = new Set(shranjeni.map((z) => z.kljuc))
    return [...shranjeni, ...[...spomin.values()].filter((z) => !kljuci.has(z.kljuc))]
  },

  /* Ob odjavi: fotografije z imeni ne ostanejo v brskalniku. */
  /* Ob zagonu aplikacije: fotografije, starejše od 24 ur, ne ostanejo v telefonu,
     tudi če nihče več ne odpre strani branja (seja brez odjave). */
  async pocistiPretekle(zdaj = Date.now()): Promise<void> {
    for (const z of await shrambaVrste.vsi()) {
      if (jePretekel(z, zdaj)) await shrambaVrste.izbrisi(z.kljuc)
    }
  },

  async izbrisiVse(): Promise<void> {
    spomin.clear()
    try {
      await vShrambi('readwrite', (s) => s.clear())
    } catch {
      /* IndexedDB ni na voljo */
    }
  },
}

// ---------- Vrsta na strani ----------

export type StanjeFotografije = 'pripravljam' | 'v-vrsti' | 'posiljam' | 'caka' | 'napaka' | 'ustavljeno'

/* Fotografija, kot jo prikaže stran (vrsta »Berem«). */
export interface FotografijaVrste {
  kljuc: string
  predogled: string | null
  stanje: StanjeFotografije
  /* Neuspelih poskusov zaradi omrežja (za prikaz »Ni povezave«). */
  poskusov: number
  napaka: string | null
  /* false: zapis v telefon ni uspel (poln, zasebno okno), fotografija živi samo,
     dokler je stran odprta - organizator mora to vedeti. */
  trajna: boolean
}

interface Vnos extends FotografijaVrste {
  zapis: ZapisVrste | null
}

/* poslano: strežnik je fotografijo sprejel; predogled preide v last strani. */
export function useVrstaFotografij(
  idTurnir: number,
  idUporabnika: number | null,
  poslano: (branje: BranjeListkaDto, predogled: string | null) => void,
  posiljanje: (zapis: ZapisVrste, signal: AbortSignal) => Promise<BranjeListkaDto>,
) {
  const [fotografije, nastaviFotografije] = useState<FotografijaVrste[]>([])
  const vnosi = useRef(new Map<string, Vnos>())
  const tece = useRef(false)
  const ziva = useRef(true)
  const ustavljena = useRef(false)
  const zaporednih = useRef(0)
  const pavzaDo = useRef(0)
  const budilka = useRef<ReturnType<typeof setTimeout> | null>(null)
  /* Turnir in uporabnik, čigar fotografije so v vnosi (ob menjavi se izpraznijo). */
  const lastnik = useRef('')
  // zadnji povratni klici brez ponovnega zagona učinkov
  const klici = useRef({ poslano, posiljanje })
  useEffect(() => {
    klici.current = { poslano, posiljanje }
  })

  const prikazi = useCallback(() => {
    nastaviFotografije(
      [...vnosi.current.values()].map(({ kljuc, predogled, stanje, poskusov, napaka, trajna }) => ({
        kljuc,
        predogled,
        stanje,
        poskusov,
        napaka,
        trajna,
      })),
    )
  }, [])

  const obdelaj = useCallback(async () => {
    if (tece.current) return
    tece.current = true
    try {
      while (ziva.current && !ustavljena.current) {
        const zdaj = Date.now()
        if (zdaj < pavzaDo.current) {
          if (budilka.current) clearTimeout(budilka.current)
          budilka.current = setTimeout(() => void obdelaj(), pavzaDo.current - zdaj)
          return
        }
        // najstarejša najprej: novejša fotografija istega listka mora priti zadnja
        const vnos = [...vnosi.current.values()]
          .filter((v) => v.zapis && (v.stanje === 'v-vrsti' || v.stanje === 'caka'))
          .sort((a, b) => a.zapis!.ustvarjeno - b.zapis!.ustvarjeno)[0]
        if (!vnos) return
        const zapis = vnos.zapis!
        if (jePretekel(zapis, zdaj)) {
          vnosi.current.set(vnos.kljuc, {
            ...vnos,
            stanje: 'napaka',
            napaka: 'Fotografija 24 ur ni prišla do strežnika in je izbrisana. Listek fotografiraj znova.',
            zapis: null,
          })
          prikazi()
          await shrambaVrste.izbrisi(zapis.kljuc)
          continue
        }
        vnosi.current.set(vnos.kljuc, { ...vnos, stanje: 'posiljam' })
        prikazi()
        const prekinitev = new AbortController()
        const meja = setTimeout(() => prekinitev.abort(), CASOVNA_MEJA_MS)
        try {
          const branje = await klici.current.posiljanje(zapis, prekinitev.signal)
          zaporednih.current = 0
          vnosi.current.delete(vnos.kljuc)
          await shrambaVrste.izbrisi(zapis.kljuc)
          klici.current.poslano(branje, vnos.predogled)
          prikazi()
        } catch (napaka) {
          const odziv = odzivNaNapako(napaka)
          // vrsta je medtem dobila drugega lastnika (odjava, drug turnir): ne vrni tuje fotografije
          if (!vnosi.current.has(vnos.kljuc)) continue
          if (odziv === 'ponovi') {
            zaporednih.current += 1
            pavzaDo.current = Date.now() + zamikPonovitve(zaporednih.current, napaka)
            vnosi.current.set(vnos.kljuc, {
              ...vnos,
              stanje: 'caka',
              poskusov: vnos.poskusov + 1,
              napaka: napaka instanceof NapakaStreznika ? napaka.message : null,
            })
          } else if (odziv === 'ustavi') {
            ustavljena.current = true
            vnosi.current.set(vnos.kljuc, {
              ...vnos,
              stanje: 'ustavljeno',
              napaka:
                napaka instanceof NapakaStreznika && napaka.stanje === 403
                  ? `${napaka.message} Fotografije počakajo v vrsti.`
                  : 'Prijava ni več veljavna. Prijavi se znova; fotografije počakajo v vrsti.',
            })
          } else {
            vnosi.current.set(vnos.kljuc, { ...vnos, stanje: 'napaka', napaka: opisNapake(napaka), zapis: null })
            await shrambaVrste.izbrisi(zapis.kljuc)
          }
          prikazi()
        } finally {
          clearTimeout(meja)
        }
      }
    } finally {
      tece.current = false
    }
  }, [prikazi])

  /* Ob odprtju strani: izbriše pretekle (vseh uporabnikov) in nadaljuje s
     fotografijami tega uporabnika na tem turnirju (stran je bila zaprta,
     telefon je ugasnil brskalnik ...). */
  useEffect(() => {
    ziva.current = true
    if (idUporabnika === null) return
    // drug uporabnik (odjava in prijava na isti strani) ne pošilja tujih fotografij
    const kdo = `${idTurnir}:${idUporabnika}`
    if (lastnik.current !== kdo) {
      for (const v of vnosi.current.values()) if (v.predogled) URL.revokeObjectURL(v.predogled)
      vnosi.current.clear()
      lastnik.current = kdo
    }
    // po ponovni prijavi (401 je vrsto ustavil) se pošiljanje nadaljuje
    ustavljena.current = false
    for (const v of vnosi.current.values()) {
      if (v.stanje === 'ustavljeno') vnosi.current.set(v.kljuc, { ...v, stanje: 'v-vrsti', napaka: null })
    }
    let preklicano = false
    void (async () => {
      const zdaj = Date.now()
      for (const z of await shrambaVrste.vsi()) {
        if (preklicano) return
        if (jePretekel(z, zdaj)) {
          await shrambaVrste.izbrisi(z.kljuc)
        } else if (z.idTurnir === idTurnir && z.idUporabnika === idUporabnika && !vnosi.current.has(z.kljuc)) {
          vnosi.current.set(z.kljuc, {
            kljuc: z.kljuc,
            predogled: URL.createObjectURL(z.jpeg),
            stanje: 'v-vrsti',
            poskusov: 0,
            napaka: null,
            trajna: true,
            zapis: z,
          })
        }
      }
      if (preklicano) return
      prikazi()
      void obdelaj()
    })()

    const znova = () => {
      pavzaDo.current = 0
      void obdelaj()
    }
    const obVidnosti = () => {
      if (document.visibilityState === 'visible') znova()
    }
    window.addEventListener('online', znova)
    document.addEventListener('visibilitychange', obVidnosti)
    return () => {
      preklicano = true
      ziva.current = false
      if (budilka.current) clearTimeout(budilka.current)
      window.removeEventListener('online', znova)
      document.removeEventListener('visibilitychange', obVidnosti)
    }
  }, [idTurnir, idUporabnika, obdelaj, prikazi])

  /* Nova fotografija: pomanjša se v brskalniku, shrani v vrsto in pošlje. */
  const dodaj = useCallback(
    async (datoteka: File) => {
      if (idUporabnika === null) return
      const kljuc = novKljucPosiljanja()
      // čas posnetka, ne konca priprave: več hkrati izbranih se pripravlja vzporedno
      const ustvarjeno = Date.now()
      vnosi.current.set(kljuc, {
        kljuc,
        predogled: null,
        stanje: 'pripravljam',
        poskusov: 0,
        napaka: null,
        trajna: true,
        zapis: null,
      })
      prikazi()
      try {
        const { jpeg } = await pripraviFotografijo(datoteka)
        const zapis: ZapisVrste = { kljuc, idTurnir, idUporabnika, jpeg, ustvarjeno }
        const trajna = await shrambaVrste.shrani(zapis)
        if (!vnosi.current.has(kljuc)) return
        vnosi.current.set(kljuc, {
          kljuc,
          predogled: URL.createObjectURL(jpeg),
          stanje: 'v-vrsti',
          poskusov: 0,
          napaka: null,
          trajna,
          zapis,
        })
        prikazi()
        void obdelaj()
      } catch (napaka) {
        vnosi.current.set(kljuc, {
          kljuc,
          predogled: null,
          stanje: 'napaka',
          poskusov: 0,
          napaka: opisNapakeFotografije(napaka),
          trajna: true,
          zapis: null,
        })
        prikazi()
      }
    },
    [idTurnir, idUporabnika, obdelaj, prikazi],
  )

  /* Organizator fotografijo opusti (napaka ali fotografija, ki je ne želi več
     poslati). Med pošiljanjem ni mogoče: strežnik jo morda že ima. */
  const opusti = useCallback(
    (kljuc: string) => {
      const vnos = vnosi.current.get(kljuc)
      if (!vnos || vnos.stanje === 'posiljam' || vnos.stanje === 'pripravljam') return
      vnosi.current.delete(kljuc)
      if (vnos.predogled) URL.revokeObjectURL(vnos.predogled)
      void shrambaVrste.izbrisi(kljuc)
      prikazi()
    },
    [prikazi],
  )

  return { fotografije, dodaj, opusti }
}
