/* Prizori maskote kot podatki (predvajalnik: pomozno/animatorMaskote.ts).

   Vsak prizor je funkcija, ki vrne ključe po delih lika. Čas `t` je delež
   trajanja (0–1). Merila, ki veljajo za vse:
   - lik stoji na tleh (y = 0), y = 48 je skrit za črto pod glavo; noge se ob
     odmiku telesa same prilagodijo;
   - roke: `r` je dvig nadlakti navzven, `p` upogib podlakti (stopinje);
   - tabla: `v` je višina (0 = zvita, 1 = razgrnjena), `r` nagib;
   - napis mora biti na tabli vidno vsaj ~2 s, sicer se dolg napis ne prebere;
   - vsak prizor ostane pod 5 s (WCAG 2.2.2).

   Dve družini: prizori z likom ob oznaki uporabnika (`tabla`, `kukaj`,
   `zogica`) in prizori čez celo glavo (`letalo`, `lestev`), ki se vežejo na
   izmerjene elemente glave (glej umestitevMaskote.ts).

   Nov prizor = nov zapis v PRIZORI; če rabi rekvizit ali drug izris, ga doda
   MaskotaPrizori.tsx. Prizor v tabeli paketov (registracija) je drugod:
   pomozno/prizorTabela.ts. */
import type { Cikel, Kljuc, Kontekst, PrizorPodatki, Sledi } from './animatorMaskote'
import {
  LESTEV,
  LESTEV_STOPALA,
  MERILO_LIKA,
  potLestve,
  potLetala,
  steviloPasov,
} from './umestitevMaskote'

/* Hiter začetek, mehak konec: dvig izza črte in pristanek. */
const IZTEK = 'cubic-bezier(0.2, 0.8, 0.2, 1)'
/* Prosti pad in vzpon žogice: parabola (easeInQuad / easeOutQuad). Z navadnim
   ease-in-out bi žogica na vrhu obstala predolgo in ob stiku ne bi pospešila.
   Isti pojemanji uporablja skakanje lika po tabeli (prizorTabela.ts). */
export const PADEC = 'cubic-bezier(0.55, 0.085, 0.68, 0.53)'
export const VZPON = 'cubic-bezier(0.25, 0.46, 0.45, 0.94)'

const SPUSCENE = { r: 20, p: 10 }
const ODPRTE = { r: 138, p: 30 }
const VRZENE = { r: 150, p: 22 }

/* Zaokrožen čas: seštevanje deležev daje 0,7800000001 in ključ bi zdrsnil za
   naslednjega. */
export const cas = (t: number) => Math.round(t * 10000) / 10000

/* Obe roki naenkrat: isti ključi za levo in desno. */
function roke(kljuci: Kljuc[]): Sledi {
  return { rokaL: kljuci, rokaD: kljuci }
}

/* Poskakovanje s tablo: izmenoma počep (telo +3, tabla in glava v eno stran)
   in raztezanje (telo -2, v drugo). Isti časi za vse dele, da se ujemajo. */
function poskoki(od: number, doo: number, stevilo: number, nagib: number) {
  const korak = (doo - od) / stevilo
  const figura: Kljuc[] = []
  const tabla: Kljuc[] = []
  const glava: Kljuc[] = []
  const pentlja: Kljuc[] = []
  for (let i = 0; i < stevilo; i++) {
    const t = cas(od + korak * (i + 1))
    const pocep = i % 2 === 0
    figura.push({ t, y: pocep ? 3 : -2 })
    tabla.push({ t, r: pocep ? nagib : -nagib })
    glava.push({ t, r: pocep ? 3 : -3 })
    pentlja.push({ t, r: pocep ? 14 : -10 })
  }
  return { figura, tabla, glava, pentlja }
}

/* Zvijanje table in spuščanje rok na koncu; skupno vsem prizorom, ki tablo
   razgrnejo. */
function zakljucekTable(od: number): Kljuc[] {
  return [{ t: cas(od), r: 0 }, { t: cas(od + 0.04) }, { t: cas(od + 0.09), v: 0 }]
}

/* Odbijanje žogice na loparju. Žogica leti po paraboli (vzpon in padec sta
   enako dolga in počasna na vrhu), ob stiku se stisne, ob odboju raztegne;
   roka z loparjem stik vzame (spusti se) in žogico potisne (dvigne), telo se
   ob stiku rahlo pripogne, glava in oči sledijo žogici.

   Vsak odbitek: {h višina, gor/dol trajanje v deležih}. Zadnji stik je udarec,
   ki žogico pošlje iz platna. */
const ODBITKI = [
  { h: 15, gor: 0.045, dol: 0.045 },
  { h: 20, gor: 0.048, dol: 0.048 },
  { h: 12, gor: 0.042, dol: 0.042 },
]

function prizorZogica({ nagib }: Kontekst): Sledi {
  const IZSTREL = 0.1
  const zoga: Kljuc[] = [{ t: 0, x: 0, y: 0, vidna: 1, sy: 1 }, { t: cas(IZSTREL - 0.004) }]
  const glava: Kljuc[] = [{ t: 0, r: 0 }, { t: IZSTREL }]
  const stiki: number[] = []

  let t = IZSTREL
  for (const o of ODBITKI) {
    zoga.push({ t, y: 0, sy: 1.12, e: VZPON })
    const vrh = cas(t + o.gor)
    zoga.push({ t: vrh, y: -o.h, sy: 1, e: PADEC })
    glava.push({ t: vrh, r: 5 })
    t = cas(vrh + o.dol)
    zoga.push({ t, y: 0.6, sy: 0.82, e: 'ease-out' })
    glava.push({ t: cas(t), r: 0 })
    stiki.push(t)
    t = cas(t + 0.007)
  }
  /* Zadnji stik je udarec: žogica zleti iz platna. */
  const udarec = stiki[stiki.length - 1]
  zoga.push(
    { t: cas(udarec + 0.009), y: 0, sy: 1.15, e: 'cubic-bezier(0.2, 0.6, 0.3, 1)' },
    { t: 0.46, y: -110, sy: 1 },
  )

  /* Roka z loparjem: mirovanje je vodoravna podlaket (r + p = 90). Ob stiku se
     spusti (r manj), ob odboju dvigne (r več), nato se vrne. */
  const rokaD: Kljuc[] = [
    { t: 0, r: 40, p: 50 },
    { t: cas(IZSTREL - 0.012) },
    { t: cas(IZSTREL - 0.002), r: 33, p: 57 },
    { t: cas(IZSTREL + 0.01), r: 46, p: 44 },
    { t: cas(IZSTREL + 0.03), r: 40, p: 50 },
  ]
  const telo: Kljuc[] = [
    { t: 0, y: 48, e: IZTEK },
    { t: 0.09, y: 0 },
    { t: cas(IZSTREL - 0.005), y: 0.8 },
    { t: cas(IZSTREL + 0.01), y: -0.4 },
    { t: cas(IZSTREL + 0.03), y: 0 },
  ]
  const pentlja: Kljuc[] = [{ t: 0, r: 0 }, { t: IZSTREL }]
  for (const tc of stiki.slice(0, -1)) {
    rokaD.push(
      { t: cas(tc - 0.03) },
      { t: tc, r: 32, p: 58 },
      { t: cas(tc + 0.012), r: 46, p: 44 },
      { t: cas(tc + 0.035), r: 40, p: 50 },
    )
    telo.push(
      { t: cas(tc - 0.03) },
      { t: tc, y: 1.3 },
      { t: cas(tc + 0.02), y: -0.5 },
      { t: cas(tc + 0.045), y: 0 },
    )
    pentlja.push({ t: tc, r: -6 }, { t: cas(tc + 0.05), r: 5 })
  }
  /* Udarec: vzmet navzdol, nato zamah, ki roko zdrsne v vrženo pozo. */
  rokaD.push(
    { t: cas(udarec - 0.03) },
    { t: udarec, r: 28, p: 62 },
    { t: 0.42, ...VRZENE },
    { t: 0.45, ...ODPRTE },
    { t: 0.88 },
    { t: 0.94, ...SPUSCENE },
  )

  const b = poskoki(0.46, 0.82, 5, nagib)
  telo.push(
    { t: cas(udarec - 0.03) },
    { t: udarec, y: 3 },
    { t: 0.4, y: -3 },
    { t: 0.46, y: 0 },
    ...b.figura,
    { t: 0.84, y: 0 },
    { t: 0.94, y: 0, e: 'ease-in' },
    { t: 1, y: 48 },
  )
  pentlja.push({ t: udarec, r: -8 }, { t: 0.42, r: 12 }, { t: 0.46, r: 0 }, ...b.pentlja, { t: 0.84, r: 0 })
  glava.push(
    { t: 0.46 },
    ...b.glava,
    { t: 0.84, r: 0 },
    { t: 0.86, r: 8 },
    { t: 0.9 },
    { t: 0.93, r: 0 },
  )

  return {
    figura: telo,
    rokaD,
    rokaL: [
      { t: 0, r: 35, p: -55 },
      { t: 0.38 },
      { t: 0.42, ...VRZENE },
      { t: 0.45, ...ODPRTE },
      { t: 0.88 },
      { t: 0.94, ...SPUSCENE },
    ],
    zoga,
    lopar: [{ t: 0, vidna: 1 }, { t: 0.4 }, { t: 0.44, vidna: 0 }],
    tabla: [
      { t: 0, v: 0, r: 0 },
      { t: 0.4 },
      { t: 0.44, v: 1.06, r: -3 },
      { t: 0.46, v: 1, r: -nagib },
      ...b.tabla,
      ...zakljucekTable(0.84),
    ],
    glava,
    pentlja,
    /* Oči gledajo proti žogici (desno), dokler se odbija; po udarcu naravnost. */
    oko: [
      { t: 0, l: 1, d: 1, pogled: 0 },
      { t: 0.09 },
      { t: 0.12, pogled: 1.3 },
      { t: 0.32 },
      { t: 0.33, l: 0.1, d: 0.1 },
      { t: 0.34, l: 1, d: 1 },
      { t: udarec },
      { t: 0.42, pogled: 0 },
      { t: 0.85 },
      { t: 0.87, d: 0.1 },
      { t: 0.89 },
      { t: 0.91, d: 1 },
    ],
  }
}

/* Letalo vleče zastavico z napisom: izza logotipa do povezave »Domov«. Hitro
   se pokaže, počasi preleti okno (napis se mora dati prebrati, zato je zastavica
   vsaj ~2,5 s cela vidna) in hitro izgine za povezavo. Zastavica plapola (cikli
   po pasovih zastavice), propeler se vrti. */
function prizorLetalo({ umestitev, sirinaTable }: Kontekst): Sledi {
  const p = potLetala(umestitev.glava!, sirinaTable)!

  /* Rahlo zibanje po višini in nagibu: dvig in spust vsakih 0,07. */
  const plovba: Kljuc[] = [{ t: 0, y: p.y, r: 0 }]
  for (let i = 1; i <= 13; i++) {
    const gor = i % 2 === 1
    plovba.push({ t: cas(i * 0.07), y: p.y + (gor ? -2.2 : 2.2), r: gor ? -1.6 : 1.6 })
  }
  plovba.push({ t: 1, y: p.y, r: 0 })

  /* Voznik maha: podlaket niha, nadlaket ostane dvignjena. */
  const mah: Kljuc[] = [{ t: 0, r: 150, p: 20 }]
  for (let i = 0; i < 16; i++) {
    mah.push({ t: cas(0.1 + i * 0.05), ...(i % 2 === 0 ? { r: 140, p: 45 } : { r: 152, p: 18 }) })
  }

  return {
    letalo: [
      { t: 0, x: p.x0, e: 'cubic-bezier(0.2, 0.6, 0.4, 1)' },
      { t: 0.2, x: p.x1, e: 'linear' },
      { t: 0.74, x: p.x2, e: 'cubic-bezier(0.6, 0, 1, 1)' },
      { t: 1, x: p.x3 },
    ],
    plovba,
    rokaD: mah,
    oko: [
      { t: 0, l: 1, d: 1 },
      { t: 0.38 },
      { t: 0.4, l: 0.1, d: 0.1 },
      { t: 0.42, l: 1, d: 1 },
      { t: 0.6 },
      { t: 0.62, d: 0.1 },
      { t: 0.68 },
      { t: 0.7, d: 1 },
    ],
  }
}

function cikliLetala({ sirinaTable }: Kontekst): Cikel[] {
  const n = steviloPasov(sirinaTable)
  const cikli: Cikel[] = []
  for (let i = 0; i < n; i++) {
    /* Prosti (levi) konec zastavice plapola bolj kot pritrjeni; val teče od
       letala nazaj, zato je pas ob letalu (večji i) fazno »naprej«. Pasovi so
       ozki in zamik majhen, da stik dveh sosednjih ni stopnica. */
    const a = 0.7 + 1.3 * (1 - i / (n - 1))
    cikli.push({
      del: `val${i}`,
      trajanjeMs: 760,
      zamikMs: i * 48,
      okvirji: [
        { transform: 'translateY(0px)' },
        { transform: `translateY(${a}px)` },
        { transform: 'translateY(0px)' },
        { transform: `translateY(${-a}px)` },
        { transform: 'translateY(0px)' },
      ],
    })
  }
  cikli.push({
    del: 'propeler',
    trajanjeMs: 80,
    okvirji: [{ transform: 'scaleY(1)' }, { transform: 'scaleY(0.3)' }, { transform: 'scaleY(1)' }],
  })
  cikli.push({
    del: 'pentlja',
    trajanjeMs: 320,
    okvirji: [
      { transform: 'rotate(6deg)' },
      { transform: 'rotate(-14deg)' },
      { transform: 'rotate(6deg)' },
    ],
  })
  return cikli
}

/* Vrvna lestev pade z vrha strani nad »Lestvica«, za njo tabla, ki prekrije
   povezavo; lik nato pleza po lestvi (za tablo) do njene sredine in pokaže
   palec gor. Na koncu lestev z likom potegnejo nazaj, tabla odleti navzgor. */
function prizorLestev({ umestitev, sirinaTable }: Kontekst): Sledi {
  const pot = potLestve(umestitev.glava!, sirinaTable)!
  const skrita = LESTEV.visina + LESTEV.rezerva
  /* Stopala lika so na začetku tik nad robom strani; y je odmik od mirovne lege
     na sredini lestve, v enotah lika (merilo 0,92). */
  const yZacetek = -(LESTEV_STOPALA + 5) / MERILO_LIKA
  const yTabla = -(pot.yZnak + 9 + 26) / MERILO_LIKA

  const T0 = 0.22
  const T1 = 0.6
  const KORAKOV = 7
  const korak = (T1 - T0) / KORAKOV
  const OPORA_GOR = { r: 150, p: 12 }
  const OPORA_DOL = { r: 118, p: 48 }

  /* Stanje po i korakih: telo nižje, roke in noge zamenjani. */
  const po = (i: number) => ({ y: yZacetek * (1 - i / KORAKOV), x: i % 2 === 0 ? 1.2 : -1.2, sodo: i % 2 === 0 })
  const figura: Kljuc[] = [{ t: 0, y: yZacetek, x: 1.2, noge: 1 }]
  const rokaL: Kljuc[] = [{ t: 0, ...OPORA_GOR }]
  const rokaD: Kljuc[] = [{ t: 0, ...OPORA_DOL }]
  const nogaL: Kljuc[] = [{ t: 0, r: 8 }]
  const nogaD: Kljuc[] = [{ t: 0, r: 30 }]
  for (let i = 0; i < KORAKOV; i++) {
    const a = po(i)
    const b = po(i + 1)
    const ts = cas(T0 + i * korak)
    /* Telo se premakne v prvih 65 % koraka, nato zagrabi (kratka pavza). */
    const te = cas(ts + korak * 0.65)
    figura.push({ t: ts, y: a.y, x: a.x, noge: 1 }, { t: te, y: b.y, x: b.x, noge: 1 })
    rokaL.push({ t: ts, ...(a.sodo ? OPORA_GOR : OPORA_DOL) }, { t: te, ...(b.sodo ? OPORA_GOR : OPORA_DOL) })
    rokaD.push({ t: ts, ...(a.sodo ? OPORA_DOL : OPORA_GOR) }, { t: te, ...(b.sodo ? OPORA_DOL : OPORA_GOR) })
    nogaL.push({ t: ts, r: a.sodo ? 8 : 30 }, { t: te, r: b.sodo ? 8 : 30 })
    nogaD.push({ t: ts, r: a.sodo ? 30 : 8 }, { t: te, r: b.sodo ? 30 : 8 })
  }

  /* Na sredini: vzravna se, leva roka drži vrv, desna pokaže palec gor. */
  figura.push(
    { t: T1 },
    { t: 0.63, y: 0, x: 0, noge: 1 },
    { t: 0.67, y: -1.6, x: 0, noge: 1 },
    { t: 0.72, y: 0, x: 0, noge: 1 },
    { t: 0.88, y: 0, x: 0, noge: 1 },
  )
  /* Leva roka drži vrv nižje (pod tablo), da je dlan vidna. */
  rokaL.push({ t: T1 }, { t: 0.66, ...OPORA_DOL })
  /* Palec: roka iztegnjena vstran (nadlaket vodoravno, podlaket rahlo navzgor),
     pest zunaj vrvi in pod tablo; palec je pravokoten na podlaket, torej gor. */
  rokaD.push({ t: T1 }, { t: 0.67, r: 88, p: 8 }, { t: 0.84 }, { t: 0.88, ...OPORA_DOL })
  nogaL.push({ t: T1 }, { t: 0.66, r: 14 })
  nogaD.push({ t: T1 }, { t: 0.66, r: 14 })

  return {
    lestev: [
      { t: 0, y: -skrita, e: 'cubic-bezier(0.2, 0.8, 0.3, 1)' },
      { t: 0.09, y: 5, e: 'ease-out' },
      { t: 0.12, y: 0 },
      { t: 0.86, e: 'ease-in' },
      { t: 1, y: -skrita },
    ],
    /* Lestev se po pristanku zamaje okoli zgornjega roba in se umiri. */
    zibanje: [
      { t: 0, r: 0 },
      { t: 0.1 },
      { t: 0.13, r: -3 },
      { t: 0.19, r: 2.4 },
      { t: 0.25, r: -1.6 },
      { t: 0.31, r: 1.1 },
      { t: 0.37, r: -0.7 },
      { t: 0.43, r: 0.4 },
      { t: 0.5, r: 0 },
      { t: 0.86 },
      { t: 1, r: 2 },
    ],
    /* Tabla pade po težnosti, se odbije, umiri in ob koncu odleti navzgor. */
    tabla: [
      { t: 0, v: 1, y: yTabla, r: 0 },
      { t: 0.08, r: 7, e: PADEC },
      { t: 0.19, y: 3, r: -3, e: 'ease-out' },
      { t: 0.22, y: -2, r: 2 },
      { t: 0.25, y: 0, r: 0 },
      { t: 0.84, e: 'ease-in' },
      { t: 0.9, y: yTabla, r: 4 },
    ],
    figura,
    rokaL,
    rokaD,
    nogaL,
    nogaD,
    palec: [
      { t: 0, o: 0 },
      { t: 0.65 },
      { t: 0.69, o: 1 },
      { t: 0.84 },
      { t: 0.87, o: 0 },
    ],
    glava: [{ t: 0, r: 0 }, { t: 0.6 }, { t: 0.68, r: 5 }, { t: 0.84 }, { t: 0.9, r: 0 }],
    oko: [
      { t: 0, l: 1, d: 1 },
      { t: 0.4 },
      { t: 0.41, l: 0.1, d: 0.1 },
      { t: 0.43, l: 1, d: 1 },
      { t: 0.72 },
      { t: 0.74, d: 0.1 },
      { t: 0.79 },
      { t: 0.81, d: 1 },
    ],
  }
}

function cikliLestve(): Cikel[] {
  return [
    {
      del: 'pentlja',
      trajanjeMs: 340,
      okvirji: [
        { transform: 'rotate(8deg)' },
        { transform: 'rotate(-10deg)' },
        { transform: 'rotate(8deg)' },
      ],
    },
  ]
}

export const PRIZORI: readonly PrizorPodatki[] = [
  /* Dvig izza črte, počep in skok, tabla se razgrne, poskakovanje, namig,
     potop. */
  {
    id: 'tabla',
    trajanjeMs: 4600,
    postavitev: 'uporabnik',
    slika: 'lik',
    sledi: ({ nagib }) => {
      const b = poskoki(0.22, 0.78, 8, nagib)
      return {
        figura: [
          { t: 0, y: 48, e: IZTEK },
          { t: 0.1, y: 0 },
          { t: 0.14, y: 3 },
          { t: 0.22, y: -3 },
          ...b.figura,
          { t: 0.84, y: 0 },
          { t: 0.94, y: 0, e: 'ease-in' },
          { t: 1, y: 48 },
        ],
        ...roke([
          { t: 0, ...SPUSCENE },
          { t: 0.15 },
          { t: 0.21, ...VRZENE },
          { t: 0.24, ...ODPRTE },
          { t: 0.88 },
          { t: 0.94, ...SPUSCENE },
        ]),
        tabla: [
          { t: 0, v: 0, r: 0 },
          { t: 0.15 },
          { t: 0.21, v: 1.06, r: -3 },
          { t: 0.24, v: 1, r: -nagib },
          ...b.tabla,
          ...zakljucekTable(0.84),
        ],
        glava: [
          { t: 0, r: 0 },
          { t: 0.24 },
          ...b.glava,
          { t: 0.84, r: 0 },
          { t: 0.86, r: 8 },
          { t: 0.9 },
          { t: 0.93, r: 0 },
        ],
        pentlja: [{ t: 0, r: 0 }, { t: 0.22 }, ...b.pentlja, { t: 0.84, r: 0 }],
        oko: [
          { t: 0, l: 1, d: 1 },
          { t: 0.45 },
          { t: 0.47, l: 0.1, d: 0.1 },
          { t: 0.49, l: 1, d: 1 },
          { t: 0.85 },
          { t: 0.87, d: 0.1 },
          { t: 0.89 },
          { t: 0.91, d: 1 },
        ],
      }
    },
  },

  /* Tabla in glava zlezeta izza črte, lik se ozira levo in desno, nato skoči
     ven in poskakuje. Tabla je vidna že med ozirom, zato se napis bere dlje. */
  {
    id: 'kukaj',
    trajanjeMs: 4800,
    postavitev: 'uporabnik',
    slika: 'lik',
    sledi: ({ nagib }) => {
      const b = poskoki(0.56, 0.77, 3, nagib)
      return {
        figura: [
          { t: 0, y: 48, e: 'cubic-bezier(0.3, 0.6, 0.3, 1)' },
          { t: 0.22, y: 22 },
          { t: 0.46, y: 22, e: IZTEK },
          { t: 0.52, y: -3 },
          { t: 0.56, y: 0 },
          ...b.figura,
          { t: 0.82, y: 0 },
          { t: 0.86, y: 3, e: 'ease-in' },
          { t: 0.94, y: 30 },
          { t: 1, y: 48 },
        ],
        ...roke([{ t: 0, ...ODPRTE }]),
        tabla: [
          { t: 0, v: 1, r: 0 },
          { t: 0.26 },
          { t: 0.3, r: -nagib * 0.5 },
          { t: 0.38, r: nagib * 0.5 },
          { t: 0.46, r: 0 },
          { t: 0.52, r: -3 },
          ...b.tabla,
          { t: 0.82, r: 0 },
        ],
        glava: [
          { t: 0, r: 0 },
          { t: 0.26 },
          { t: 0.3, r: -5 },
          { t: 0.38, r: 5 },
          { t: 0.46, r: 0 },
          { t: 0.56 },
          ...b.glava,
          { t: 0.82, r: 0 },
          { t: 0.85, r: 8 },
          { t: 0.9 },
          { t: 0.93, r: 0 },
        ],
        pentlja: [{ t: 0, r: 0 }, { t: 0.56 }, ...b.pentlja, { t: 0.82, r: 0 }],
        oko: [
          { t: 0, l: 1, d: 1, pogled: 0 },
          { t: 0.24 },
          { t: 0.29, pogled: -1.6 },
          { t: 0.33 },
          { t: 0.37, pogled: 1.6 },
          { t: 0.42 },
          { t: 0.46, pogled: 0 },
          { t: 0.62 },
          { t: 0.64, l: 0.1, d: 0.1 },
          { t: 0.66, l: 1, d: 1 },
          { t: 0.83 },
          { t: 0.85, d: 0.1 },
          { t: 0.89 },
          { t: 0.91, d: 1 },
        ],
      }
    },
  },

  /* Odbija žogico z loparjem, nato jo z udarcem pošlje iz platna in ob tem
     razgrne tablo. Lopar po udarcu izgine (roka rabi za tablo). */
  {
    id: 'zogica',
    trajanjeMs: 4800,
    postavitev: 'uporabnik',
    slika: 'lik',
    lopar: true,
    sledi: prizorZogica,
  },

  {
    id: 'letalo',
    trajanjeMs: 4600,
    postavitev: 'glava',
    slika: 'letalo',
    sledi: prizorLetalo,
    cikli: cikliLetala,
  },

  {
    id: 'lestev',
    trajanjeMs: 4800,
    postavitev: 'glava',
    slika: 'lestev',
    sledi: prizorLestev,
    cikli: cikliLestve,
  },
]
