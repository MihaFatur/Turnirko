/* Izbira prizora in napisa maskote: VREČA, ne kocka.

   Ob vsakem nastopu je bila izbira čisto naključna (razen »ne dvakrat zapored
   isto«). Statistično je to enakomerno, na oko pa ne: gledalec vidi le nekaj
   nastopov (največ 3 na nalaganje strani), in v tako kratkem zaporedju je
   nekaj naključnih prizorov trikrat isti, drugi pa se ne pokaže nikoli — kar
   se bere kot »ta je veliko pogostejši«. Vreča to odpravi: vsi prizori (in vsi
   napisi) so v premešani vreči in se vlečejo brez vračanja; ko se vreča
   izprazni, se napolni znova. Vsak prizor se tako pokaže enkrat na krog, ne
   glede na to, koliko nastopov je v krogu ogledanih.

   Vreča se hrani v `localStorage`, zato krog teče PREK nalaganj strani: kdor
   osvežuje in vidi vsakič le prvi nastop, dobi tudi tam vsak prizor enkrat na
   krog. Brez tega bi se vsaka osvežitev začela z novim naključnim prizorom.
   Shranjevanje je udobje: če ga brskalnik ne dovoli (zasebno okno), vreča
   živi do osvežitve.

   Prizor in napis sta ločeni vreči. Ne ujemata se vedno (širok napis se ne
   prilega vsakemu prizoru v ozko okno), zato izbira išče v vrstnem redu vreč
   prvi par, ki se prilega (`ustreza`); kar se ne prilega, ostane v vreči za
   naslednjič in se ne izgubi. */

/* Vreča je po napravi: prizori na namizju in telefonu niso isti (lestev je samo
   na namizju), ob spremembi nabora pa se vreča začne znova — ena skupna bi se
   ob vsakem prehodu čez 640 px (zasuk tablice, spremenjeno okno) začela znova. */
const KLJUC = 'turnirko-maskota-vreca'

interface Vreca {
  /* Vsi elementi kroga; po njih se preveri, ali se je nabor spremenil. */
  vsi: string[]
  ostanek: string[]
  zadnji: string | null
}

interface Stanje {
  prizori: Vreca
  napisi: Vreca
}

const stanja = new Map<string, Stanje>()

/* Fisher–Yates. Prvi element ni isti kot `zadnji` (če je izbira sploh
   možna), da se prizor ob prehodu v nov krog ne ponovi. */
function premesaj(elementi: readonly string[], zadnji: string | null): string[] {
  const izhod = [...elementi]
  for (let i = izhod.length - 1; i > 0; i--) {
    const j = Math.floor(Math.random() * (i + 1))
    ;[izhod[i], izhod[j]] = [izhod[j], izhod[i]]
  }
  if (izhod.length > 1 && izhod[0] === zadnji) {
    const j = 1 + Math.floor(Math.random() * (izhod.length - 1))
    ;[izhod[0], izhod[j]] = [izhod[j], izhod[0]]
  }
  return izhod
}

function jeVreca(v: unknown): v is Vreca {
  if (typeof v !== 'object' || v === null) return false
  const { vsi, ostanek, zadnji } = v as Record<string, unknown>
  const nizi = (a: unknown) => Array.isArray(a) && a.every((e) => typeof e === 'string')
  return nizi(vsi) && nizi(ostanek) && (zadnji === null || typeof zadnji === 'string')
}

/* Shranjena vreča velja, če je nabor elementov isti (napis dodan ali prizor
   odstranjen → nov krog); iz ostanka pade, česar ni več. */
function uskladi(shranjena: unknown, vsi: readonly string[]): Vreca {
  if (jeVreca(shranjena)) {
    const enaka =
      shranjena.vsi.length === vsi.length && vsi.every((e) => shranjena.vsi.includes(e))
    if (enaka) return { ...shranjena, ostanek: shranjena.ostanek.filter((e) => vsi.includes(e)) }
  }
  return { vsi: [...vsi], ostanek: premesaj(vsi, null), zadnji: null }
}

function naloziStanje(
  naprava: string,
  prizori: readonly string[],
  napisi: readonly string[],
): Stanje {
  let shranjeno: { prizori?: unknown; napisi?: unknown } | null | undefined = stanja.get(naprava)
  if (!shranjeno) {
    try {
      shranjeno = JSON.parse(localStorage.getItem(`${KLJUC}-${naprava}`) ?? 'null')
    } catch {
      /* Brez shrambe ali pokvarjen zapis: začnemo znova. */
    }
  }
  const stanje = {
    prizori: uskladi(shranjeno?.prizori, prizori),
    napisi: uskladi(shranjeno?.napisi, napisi),
  }
  stanja.set(naprava, stanje)
  return stanje
}

function shrani(naprava: string, stanje: Stanje) {
  try {
    localStorage.setItem(`${KLJUC}-${naprava}`, JSON.stringify(stanje))
  } catch {
    /* Vreča ostane v pomnilniku. */
  }
}

/* Vrne porabljene elemente na konec vreče (novi krog za njih); kar še čaka,
   ostane spredaj — tako se element, ki se ta hip ne prilega, ne izgubi. */
function dopolni(vreca: Vreca) {
  const porabljeni = vreca.vsi.filter((e) => !vreca.ostanek.includes(e))
  vreca.ostanek = [...vreca.ostanek, ...premesaj(porabljeni, vreca.zadnji)]
}

function poberi(vreca: Vreca, element: string) {
  vreca.ostanek = vreca.ostanek.filter((e) => e !== element)
  vreca.zadnji = element
}

/* Potegne naslednji prizor in napis, ki se skupaj prilegata (`ustreza`), ali
   vrne null, če se ne prilega nobena kombinacija. Potegnjena elementa
   zapusti vreči šele tu: kdor pokliče, mora nastop tudi pokazati. */
export function izberiPar(
  naprava: string,
  prizori: readonly string[],
  napisi: readonly string[],
  ustreza: (prizor: string, napis: string) => boolean,
): { prizor: string; napis: string } | null {
  const s = naloziStanje(naprava, prizori, napisi)

  for (let poskus = 0; poskus < 2; poskus++) {
    if (s.prizori.ostanek.length === 0) dopolni(s.prizori)
    if (s.napisi.ostanek.length === 0) dopolni(s.napisi)

    for (const prizor of s.prizori.ostanek) {
      const napis = s.napisi.ostanek.find((n) => ustreza(prizor, n))
      if (napis !== undefined) {
        poberi(s.prizori, prizor)
        poberi(s.napisi, napis)
        shrani(naprava, s)
        return { prizor, napis }
      }
    }
    /* V ostanku se nič ne prilega: pusti porabljene nazaj v krog, morda se
       prilega kaj od njih. */
    dopolni(s.prizori)
    dopolni(s.napisi)
  }
  shrani(naprava, s)
  return null
}
