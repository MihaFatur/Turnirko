/* Predvajalnik prizorov maskote (izris: komponente/MaskotaPrizori.tsx,
   podatki prizorov: pomozno/prizoriMaskote.ts).

   Prizor je PODATEK: za vsak del lika seznam ključev (čas 0–1, vrednosti,
   pojemanje). Predvajalnik ga prevede v Web Animations API — en `animate()`
   na del lika —, zato nov prizor ni nov kos CSS-a. Prej so bili keyframes v
   slog.css in so se noge ob vsaki spremembi telesa preračunavale na roke;
   tu je zveza noge–telo izračunana.

   Ključi po delih (ne skupne poze): mežikanje ne sme vsiljevati vmesnih
   stopenj poskakovanju. Del, ki v ključu polja izpusti, obdrži prejšnjo
   vrednost (`{ t: 0.5 }` pomeni »do tu drži«). Prvi ključ je pri t = 0 in
   zadnji pri t = 1 — manjkajoča robova predvajalnik doda sam. Del, katerega
   gibanje se ne sme mešati z drugim (npr. premik letala in njegovo
   zibanje), stoji v ločenih ugnezdenih skupinah z lastnima sledema.

   Poleg ključev so CIKLI: neskončno ponavljajoče se kratko gibanje (propeler,
   plapolanje zastavice), ki teče ves prizor in ga ob koncu prekliče
   `ustavi`. Ključi bi za propeler pomenili sto ključev na sekundo.

   Animira se samo `transform` in `opacity`. Sklepi so vrtišča: zunanja
   skupina v SVG-ju postavi izhodišče v sklep, notranja (`data-del`) se vrti
   okoli svojega (0, 0). */

export type Kljuc = { t: number; e?: string; [polje: string]: number | string | undefined }
export type Sledi = Record<string, Kljuc[]>

/* Izmerjena glava strani (px, glede na njen zgornji levi kot): sidra, ob
   katerih se odvijata prizora čez celo glavo (letalo, lestev). */
export interface Glava {
  sirina: number
  /* Desni rob logotipa »Turnirko«: od tu se letalo pokaže. */
  logotipDesno: number
  /* Levi rob povezave »Domov«: tu letalo izgine. */
  domovLevo: number
  /* Sredina povezave »Lestvica«: nad njo visi lestev, nanjo pade tabla. */
  lestvicaSredina: number
  /* Navpična sredina vrstice z navigacijo. */
  vrsticaSredina: number
}

/* Kje in kako velik je nastop. `uporabnik`: platno levo od oznake uporabnika,
   lik stoji na črti. `glava`: platno čez celo glavo (in lestev pod njo), vse
   mere so v pikslih strani. */
export interface Umestitev {
  vrsta: 'uporabnik' | 'glava'
  sirina: number
  visina: number
  glava?: Glava
}

export interface Kontekst {
  /* Največji nagib table v stopinjah: širša tabla ima pri istem nagibu višji
     vogal, ki bi ušel čez rob platna. Izračuna ga gostitelj iz njene širine. */
  nagib: number
  umestitev: Umestitev
  /* Širina table v enotah lika (napis + naglasni trak). */
  sirinaTable: number
}

/* Ponavljajoče se gibanje enega elementa. `zamikMs` premakne fazo (kolikor je
   večji, tem bolj je cikel »naprej«), da pasovi zastavice ne nihajo vsi
   hkrati in nastane val. */
export interface Cikel {
  del: string
  trajanjeMs: number
  okvirji: Keyframe[]
  zamikMs?: number
}

/* Kar predvajalnik potrebuje od prizora. `K` je kontekst, ki ga prizor dobi:
   prizori v glavi dobijo `Kontekst`, prizor v tabeli paketov (prizorTabela.ts)
   ničesar, ker so mere že v njegovih ključih. */
export interface Predvajljiv<K> {
  id: string
  /* Ostane pod 5 s: gibanje, ki se začne samo in traja dlje, mora po
     WCAG 2.2.2 imeti gumb za ustavitev, krajšemu pa ga ni treba. */
  trajanjeMs: number
  sledi: (kontekst: K) => Sledi
  cikli?: (kontekst: K) => Cikel[]
}

export interface PrizorPodatki extends Predvajljiv<Kontekst> {
  /* Kje se prizor odvija (glej Umestitev) in kateri izris ga igra. */
  postavitev: 'uporabnik' | 'glava'
  slika: 'lik' | 'letalo' | 'lestev'
  /* Ali prizor rabi lopar in žogico (izris ju doda samo tedaj). */
  lopar?: boolean
}

type Stanje = Record<string, number>
type Okvir = Record<string, string | number>

interface Sled {
  privzeto: Stanje
  /* Vsak zapis je en element SVG-ja (`data-del`), ki ga sled premika. */
  deli: Record<string, (s: Stanje) => Okvir>
}

const vrti = (kot: number): Okvir => ({ transform: `rotate(${kot}deg)` })

/* Splošen sled za del, ki se premika, vrti in bledi: x, y (px), r (stopinje),
   sx, sy (merilo), o (motnost). Za dele, ki nimajo posebnega pomena (letalo,
   lestev, palec). */
function splosen(ime: string): Sled {
  return {
    privzeto: { x: 0, y: 0, r: 0, sx: 1, sy: 1, o: 1 },
    deli: {
      [ime]: (s) => ({
        transform: `translate(${s.x}px, ${s.y}px) rotate(${s.r}deg) scale(${s.sx}, ${s.sy})`,
        opacity: s.o,
      }),
    },
  }
}

/* Roke: predznak loči levo od desne, da imata isti ključi isti pomen
   (pozitivno = dvig navzven). */
const SLEDI: Record<string, Sled> = {
  figura: {
    privzeto: { x: 0, y: 0, vrtenje: 0 },
    deli: {
      figura: (s) => ({
        transform: `translate(${s.x}px, ${s.y}px) rotate(${s.vrtenje}deg)`,
      }),
    },
  },
  nogaL: { privzeto: { r: 0 }, deli: { nogaL: (s) => vrti(s.r) } },
  nogaD: { privzeto: { r: 0 }, deli: { nogaD: (s) => vrti(-s.r) } },
  rokaL: {
    privzeto: { r: 20, p: 10 },
    deli: { rokaL: (s) => vrti(s.r), podL: (s) => vrti(s.p) },
  },
  rokaD: {
    privzeto: { r: 20, p: 10 },
    deli: { rokaD: (s) => vrti(-s.r), podD: (s) => vrti(-s.p) },
  },
  glava: { privzeto: { r: 0 }, deli: { glava: (s) => vrti(s.r) } },
  pentlja: { privzeto: { r: 0 }, deli: { pentlja: (s) => vrti(s.r) } },
  oko: {
    privzeto: { l: 1, d: 1, pogled: 0 },
    deli: {
      okoL: (s) => ({ transform: `translate(${s.pogled}px, 0) scaleY(${s.l})` }),
      okoD: (s) => ({ transform: `translate(${s.pogled}px, 0) scaleY(${s.d})` }),
    },
  },
  tabla: {
    privzeto: { x: 0, y: 0, r: 0, v: 0 },
    deli: {
      tabla: (s) => ({
        transform: `translate(${s.x}px, ${s.y}px) rotate(${s.r}deg) scaleY(${s.v})`,
      }),
    },
  },
  /* Žogica se ob stiku z loparjem stisne (sy < 1) in v letu raztegne (sy > 1);
     površina ostane ista (sx = 1 / sy). */
  zoga: {
    privzeto: { x: 0, y: 0, vidna: 0, sy: 1 },
    deli: {
      zoga: (s) => ({
        transform: `translate(${s.x}px, ${s.y}px) scale(${1 / s.sy}, ${s.sy})`,
        opacity: s.vidna,
      }),
    },
  },
  lopar: { privzeto: { vidna: 0 }, deli: { lopar: (s) => ({ opacity: s.vidna }) } },
  letalo: splosen('letalo'),
  plovba: splosen('plovba'),
  lestev: splosen('lestev'),
  zibanje: splosen('zibanje'),
  palec: splosen('palec'),
  /* Okvir okoli celice v tabeli paketov (prizorTabela.ts). */
  okvir: splosen('okvir'),
}

/* Dolžina nog v enotah lika (od bokov do tal). */
const DOLZINA_NOG = 10.5

/* Stopala ostanejo na tleh: pri odmiku telesa `y` (navzdol pozitiven) je noga
   krajša oz. daljša za natanko toliko. Zgoraj omejeno, ker se lik v zraku ne
   more raztegniti čez svoje meje; spodaj 0, ker je pod črto noga skrita. */
function izpeljiNoge(y: number): number {
  return Math.min(1.25, Math.max(0, (DOLZINA_NOG - y) / DOLZINA_NOG))
}

const PRIVZETO_POJEMANJE = 'ease-in-out'

/* Ključe enega sleda pretvori v okvirje za vsak element, ki ga sled premika.
   `obKljucu` dobi stanje po vsakem ključu (za noge, ki jih izpelje iz telesa). */
function okvirjiSleda(
  kljuci: Kljuc[],
  sled: Sled,
  obKljucu?: (s: Stanje, kljuc: Kljuc) => void,
): Map<string, Keyframe[]> {
  const razvrsceni = [...kljuci].sort((a, b) => a.t - b.t)
  if (razvrsceni.length === 0 || razvrsceni[0].t > 0) razvrsceni.unshift({ t: 0 })
  if (razvrsceni[razvrsceni.length - 1].t < 1) razvrsceni.push({ t: 1 })

  const okvirji = new Map<string, Keyframe[]>(Object.keys(sled.deli).map((ime) => [ime, []]))
  const stanje: Stanje = { ...sled.privzeto }

  for (const kljuc of razvrsceni) {
    for (const [polje, vrednost] of Object.entries(kljuc)) {
      if (polje !== 't' && polje !== 'e' && typeof vrednost === 'number' && polje in stanje) {
        stanje[polje] = vrednost
      }
    }
    obKljucu?.(stanje, kljuc)
    for (const [ime, zgradi] of Object.entries(sled.deli)) {
      okvirji.get(ime)!.push({
        offset: kljuc.t,
        easing: kljuc.e ?? PRIVZETO_POJEMANJE,
        ...zgradi(stanje),
      })
    }
  }
  return okvirji
}

export interface Seja {
  /* Razrešen šele, ko se prizor res konča; ob preklicu ostane nerazrešen. */
  konec: Promise<void>
  ustavi: () => void
}

/* Požene prizor na že izrisanem liku. Lik je spremljevalec, ne funkcija
   strani: če karkoli spodleti, se prizor konča takoj in stran ostane cela. */
export function predvajaj<K>(koren: Element, prizor: Predvajljiv<K>, kontekst: K): Seja {
  const animacije: Animation[] = []
  const cikli: Animation[] = []
  const ustavi = () => [...animacije, ...cikli].forEach((a) => a.cancel())

  try {
    const sledi = prizor.sledi(kontekst)
    const okvirji = new Map<string, Keyframe[]>()

    for (const [ime, kljuci] of Object.entries(sledi)) {
      const sled = SLEDI[ime]
      if (!sled) {
        console.error(`Maskota: neznan sled »${ime}« v prizoru »${prizor.id}«`)
        continue
      }
      /* Noge nimajo lastnega sleda: izpeljejo se iz telesa, z istimi časi in
         pojemanjem, da se ujemata. Ključ telesa jih sme prepisati (`noge`). */
      const noge: Keyframe[] = []
      const izpelji =
        ime === 'figura'
          ? (s: Stanje, kljuc: Kljuc) => {
              const prepis = typeof kljuc.noge === 'number' ? kljuc.noge : null
              noge.push({
                offset: kljuc.t,
                easing: kljuc.e ?? PRIVZETO_POJEMANJE,
                transform: `scaleY(${prepis ?? izpeljiNoge(s.y)})`,
              })
            }
          : undefined
      for (const [del, okviri] of okvirjiSleda(kljuci, sled, izpelji)) okvirji.set(del, okviri)
      /* Obrobna ključa, ki ju je sled sam dodal, so v `noge` že zabeležena. */
      if (ime === 'figura') okvirji.set('noge', noge)
    }

    for (const [del, okviri] of okvirji) {
      const element = koren.querySelector(`[data-del="${del}"]`)
      if (!element) continue
      animacije.push(element.animate(okviri, { duration: prizor.trajanjeMs, fill: 'both' }))
    }

    for (const cikel of prizor.cikli?.(kontekst) ?? []) {
      const element = koren.querySelector(`[data-del="${cikel.del}"]`)
      if (!element) continue
      /* Pojemanje je na ključih (med sosednjima), ne na možnosti: ta bi zgladila
         celoten cikel kot eno gibanje in val bi bil na koncih počasen. */
      cikli.push(
        element.animate(
          cikel.okvirji.map((okvir) => ({ easing: PRIVZETO_POJEMANJE, ...okvir })),
          {
            duration: cikel.trajanjeMs,
            iterations: Infinity,
            delay: -(cikel.zamikMs ?? 0),
          },
        ),
      )
    }
  } catch (napaka) {
    console.error('Maskota: prizor se ni mogel začeti', napaka)
    ustavi()
    return { konec: Promise.resolve(), ustavi: () => {} }
  }

  const konec = new Promise<void>((razresi) => {
    Promise.all(animacije.map((a) => a.finished)).then(
      () => razresi(),
      () => {
        /* Preklic (odmontiranje): prizora ni konec, ampak ga ni več. */
      },
    )
  })
  return { konec, ustavi }
}
