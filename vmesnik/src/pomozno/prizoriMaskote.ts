/* Prizori maskote kot podatki (predvajalnik: pomozno/animatorMaskote.ts).

   Vsak prizor je funkcija, ki vrne ključe po delih lika. Čas `t` je delež
   trajanja (0–1). Merila, ki veljajo za vse:
   - lik stoji na tleh (y = 0), y = 48 je skrit za črto pod glavo; noge se ob
     odmiku telesa same prilagodijo;
   - roke: `r` je dvig nadlakti navzven, `p` upogib podlakti (stopinje);
   - tabla: `v` je višina (0 = zvita, 1 = razgrnjena), `r` nagib;
   - napis mora biti na tabli vidno vsaj ~2 s, sicer se dolg napis ne prebere;
   - vsak prizor ostane pod 5 s (WCAG 2.2.2).

   Prizori igrajo v glavi, na namizju in na telefonu (glej Umestitev v
   animatorMaskote.ts): isti prizor ima na telefonu ista števila, ker so mere
   v enotah lika, merilo pa določi umestitev. `lestev` je samo za namizje
   (sega pod črto glave). Dve razliki:
   - na TELEFONU oznaka uporabnika (»Gost · prijava«) med prizorom izgine
     (`zOznako`), ker lik stoji v njenem prostoru;
   - »zogica« ima dve različici z istim `id`: na telefonu žogico pošlje iz
     platna in razgrne tablo, na namizju jo pošlje v oznako uporabnika, ki se ob
     zadetku spremeni v znak.

   Nov prizor = nov zapis v PRIZORI; če rabi rekvizit ali drug izris, ga doda
   MaskotaPrizori.tsx. Prizor v tabeli paketov (registracija) je drugod:
   pomozno/prizorTabela.ts. */
import type { Cikel, Kljuc, Kontekst, Naprava, PrizorPodatki, Sledi } from './animatorMaskote'
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

/* Oznaka uporabnika med prizorom ni vidna: hitro zbledi, ob koncu se vrne. */
function skritaOznaka(): Kljuc[] {
  return [{ t: 0, vidna: 1 }, { t: 0.03, vidna: 0 }, { t: 0.965 }, { t: 1, vidna: 1 }]
}

/* Na telefonu prizor doda še skrivanje oznake uporabnika; na namizju ga pusti
   pri miru (razen prizora, ki ima svojo sled `oznaka`). */
function zOznako(prizor: PrizorPodatki): PrizorPodatki {
  return {
    ...prizor,
    sledi: (k: Kontekst) => {
      const sledi = prizor.sledi(k)
      if (k.umestitev.glava.naprava !== 'telefon' || sledi.oznaka) return sledi
      return { ...sledi, oznaka: skritaOznaka() }
    },
  }
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

/* Različica za TELEFON: lik odbija žogico, jo z udarcem pošlje iz platna in ob
   tem razgrne tablo. Lopar po udarcu izgine (roka rabi za tablo). */
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

/* Različica za NAMIZJE: lik dvigne žogico enkrat navzgor, jo ujame na lopar in
   jo nato pošlje v oznako uporabnika (»Gost · prijava«). V trenutku zadetka se
   oznaka spremeni v znak z napisom, lik se razveseli, po dveh sekundah se znak
   spet skrči v oznako in lik se potopi.

   Pot žogice je parabola med loparjem in središčem oznake: leti po nizkem loku,
   zato jo je treba vzorčiti (linearni odseki), ne animirati z enim pojemanjem. */
function prizorZogicaOznaka({ umestitev }: Kontekst): Sledi {
  const g = umestitev.glava
  const m = umestitev.merilo
  /* Žogica miruje na loparju (glej `Lik`: 33,75 desno in 22,4 nad tlemi lika). */
  const zacetekX = umestitev.x + 33.75 * m
  const zacetekY = g.tla - 22.4 * m
  const dx = ((g.oznakaLevo + g.oznakaDesno) / 2 - zacetekX) / m
  const dy = (g.oznakaY - zacetekY) / m
  const VISINA_LOKA = 16

  const NAVZGOR = 0.1
  const VRH = 0.155
  const STIK = 0.21
  const ZAMAH = 0.26
  const UDAREC = 0.285
  const ZADETEK = 0.41
  const ZNAK_KONEC = 0.87
  const OZNAKA_NAZAJ = 0.905

  const zoga: Kljuc[] = [
    { t: 0, x: 0, y: 0, vidna: 1, sy: 1 },
    { t: cas(NAVZGOR - 0.004) },
    { t: NAVZGOR, y: 0, sy: 1.12, e: VZPON },
    { t: VRH, y: -22, sy: 1, e: PADEC },
    { t: STIK, y: 0.6, sy: 0.82, e: 'ease-out' },
    { t: cas(STIK + 0.008), y: 0, sy: 1 },
    { t: cas(UDAREC - 0.004) },
    { t: UDAREC, y: 0, sy: 1.15, e: 'linear' },
  ]
  const KORAKOV = 8
  for (let i = 1; i <= KORAKOV; i++) {
    const p = i / KORAKOV
    zoga.push({
      t: cas(UDAREC + (ZADETEK - UDAREC) * p),
      x: dx * p,
      y: dy * p - VISINA_LOKA * 4 * p * (1 - p),
      sy: 1.08,
      e: 'linear',
    })
  }
  /* Ob zadetku se žogica splošči in izgine v znaku. */
  zoga.push({ t: cas(ZADETEK + 0.006), sy: 0.6 }, { t: cas(ZADETEK + 0.014), vidna: 0 })

  /* Roka z loparjem (mirovanje: r + p = 90): ob odboju se spusti in dvigne,
     ob stiku vzame žogico, nato zamah — in dvignjena v veselju. */
  const rokaD: Kljuc[] = [
    { t: 0, r: 40, p: 50 },
    { t: cas(NAVZGOR - 0.012) },
    { t: cas(NAVZGOR - 0.002), r: 33, p: 57 },
    { t: cas(NAVZGOR + 0.01), r: 46, p: 44 },
    { t: cas(NAVZGOR + 0.03), r: 40, p: 50 },
    { t: cas(STIK - 0.03) },
    { t: STIK, r: 32, p: 58 },
    { t: cas(STIK + 0.012), r: 46, p: 44 },
    { t: cas(STIK + 0.035), r: 40, p: 50 },
    { t: ZAMAH, r: 28, p: 62 },
    { t: UDAREC, r: 55, p: 35 },
    { t: 0.34, r: 70, p: 40 },
    { t: 0.44, ...VRZENE },
    { t: 0.84 },
    { t: 0.9, ...SPUSCENE },
  ]
  const rokaL: Kljuc[] = [
    { t: 0, r: 35, p: -55 },
    { t: 0.38 },
    { t: 0.43, ...VRZENE },
    { t: 0.84 },
    { t: 0.9, ...SPUSCENE },
  ]

  const telo: Kljuc[] = [
    { t: 0, y: 48, e: IZTEK },
    { t: 0.085, y: 0 },
    { t: cas(NAVZGOR - 0.005), y: 0.8 },
    { t: cas(NAVZGOR + 0.01), y: -0.4 },
    { t: cas(NAVZGOR + 0.03), y: 0 },
    { t: cas(STIK - 0.03) },
    { t: STIK, y: 1.3 },
    { t: cas(STIK + 0.02), y: -0.5 },
    { t: cas(STIK + 0.045), y: 0 },
    { t: ZAMAH },
    { t: UDAREC, y: 3 },
    { t: cas(UDAREC + 0.03), y: -3 },
    { t: cas(UDAREC + 0.07), y: 0 },
    /* Veselje ob zadetku: dva poskoka. */
    { t: ZADETEK },
    { t: cas(ZADETEK + 0.03), y: -4 },
    { t: cas(ZADETEK + 0.07), y: 0 },
    { t: cas(ZADETEK + 0.1), y: -2 },
    { t: cas(ZADETEK + 0.13), y: 0 },
    { t: 0.88, y: 0, e: 'ease-in' },
    { t: 0.96, y: 48 },
  ]

  return {
    figura: telo,
    rokaD,
    rokaL,
    zoga,
    glava: [
      { t: 0, r: 0 },
      { t: NAVZGOR },
      { t: VRH, r: 5 },
      { t: STIK, r: 0 },
      { t: UDAREC, r: -3 },
      { t: 0.35, r: 0 },
      { t: cas(ZADETEK + 0.02), r: 6 },
      { t: 0.84 },
      { t: 0.87, r: 0 },
    ],
    pentlja: [
      { t: 0, r: 0 },
      { t: NAVZGOR },
      { t: UDAREC, r: -8 },
      { t: 0.335, r: 12 },
      { t: 0.37, r: 0 },
    ],
    /* Oči gledajo proti žogici in nato proti znaku (oboje desno). */
    oko: [
      { t: 0, l: 1, d: 1, pogled: 0 },
      { t: 0.09 },
      { t: 0.12, pogled: 1.3 },
      { t: 0.6 },
      { t: 0.61, l: 0.1, d: 0.1 },
      { t: 0.63, l: 1, d: 1 },
      { t: 0.84 },
      { t: 0.88, pogled: 0 },
    ],
    /* Znak nastane na mestu oznake, ob zadetku, v velikosti oznake, in se
       razširi v celotno tablo; ob koncu se skrči nazaj. */
    znak: [
      { t: 0, o: 0, sx: 0.5, sy: 1 },
      { t: cas(ZADETEK - 0.001) },
      { t: ZADETEK, o: 1 },
      { t: cas(ZADETEK + 0.025), sx: 1.08, sy: 1.15, e: IZTEK },
      { t: cas(ZADETEK + 0.05), sx: 1, sy: 1 },
      { t: ZNAK_KONEC },
      { t: OZNAKA_NAZAJ, o: 0, sx: 0.5 },
    ],
    oznaka: [
      { t: 0, vidna: 1 },
      { t: cas(ZADETEK - 0.001) },
      { t: ZADETEK, vidna: 0 },
      { t: cas(OZNAKA_NAZAJ - 0.01) },
      { t: OZNAKA_NAZAJ, vidna: 1 },
    ],
  }
}

/* Lik pokuka izza črte s tablo nad glavo, se ozre levo in desno, nato tablo
   spusti pred sabo na črto in se potopi; tabla ostane stati na črti, dokler
   je izza črte ne povlečeta dve roki navzdol.

   Tablo lik nosi v skupini `figura`, zato je njen položaj vezan na lik. Da
   ostane na mestu, ko se lik potaplja, se njen odmik izniči z odmikom telesa
   (y table = 44,5 − y telesa, kar postavi njen spodnji rob na tla). Zato so
   ključi table in telesa v intervalu spusta in potopa NUJNO isti (isti časi in
   pojemanje): dodatni ključi na eni od sledi bi pojemanje razrezali in tabla bi
   zdrsnila. */
function prizorKukaj({ nagib }: Kontekst): Sledi {
  const DVIG = 0.14
  const OZIRANJE_OD = 0.17
  const SPUST_OD = 0.42
  const SPUST_DO = 0.5
  const POTOP_DO = 0.58
  const ROKE_OD = 0.78
  const ROKE_DO = 0.84
  const STRG = 0.87
  const STRG_NAZAJ = 0.895
  const VLEK_DO = 0.96
  /* Telo: viden do prsi (22), pripognjen za tablo (26), skrit pod črto (48).
     Tabla: odmik, pri katerem njen spodnji rob stoji na tleh (64). */
  const TELO_VIDNO = 22
  const TELO_PRIPETO = 26
  const TELO_SKRITO = 48
  const yTable = (telo: number) => 44.5 - telo
  const VLEK = 26

  return {
    figura: [
      { t: 0, y: TELO_SKRITO, e: 'cubic-bezier(0.3, 0.6, 0.3, 1)' },
      { t: DVIG, y: TELO_VIDNO },
      { t: SPUST_OD },
      { t: SPUST_DO, y: TELO_PRIPETO, e: 'ease-in' },
      { t: POTOP_DO, y: TELO_SKRITO },
    ],
    /* Roke držijo tablo od spodaj; ko se spusti, gredo z njo navzdol in za
       tablo, ki je pred njimi (`tablaNaprej`), izginejo. */
    ...roke([{ t: 0, ...ODPRTE }, { t: SPUST_OD }, { t: SPUST_DO, r: 60, p: 40 }]),
    tabla: [
      { t: 0, v: 1, r: 0, y: 0 },
      { t: cas(OZIRANJE_OD + 0.03) },
      { t: cas(OZIRANJE_OD + 0.04), r: -nagib * 0.5 },
      { t: cas(OZIRANJE_OD + 0.12), r: nagib * 0.5 },
      { t: cas(OZIRANJE_OD + 0.2), r: 0 },
      { t: SPUST_OD },
      { t: SPUST_DO, y: yTable(TELO_PRIPETO), e: 'ease-in' },
      { t: POTOP_DO, y: yTable(TELO_SKRITO) },
      /* Ko lika ni več, se tabla po prihodu na črto še malo zamaje. */
      { t: 0.6, r: -2 },
      { t: 0.63, r: 1 },
      { t: 0.66, r: 0 },
      { t: ROKE_DO },
      /* Roki zagrabita: rahlo popustita, nato potegneta. */
      { t: STRG, y: yTable(TELO_SKRITO) + 3 },
      { t: STRG_NAZAJ, y: yTable(TELO_SKRITO) - 0.5, e: 'ease-in' },
      { t: VLEK_DO, y: yTable(TELO_SKRITO) + VLEK },
    ],
    vlekL: potegRoke(ROKE_OD, ROKE_DO, STRG, STRG_NAZAJ, VLEK_DO, VLEK),
    vlekD: potegRoke(ROKE_OD, ROKE_DO, STRG, STRG_NAZAJ, VLEK_DO, VLEK),
    glava: [
      { t: 0, r: 0 },
      { t: OZIRANJE_OD },
      { t: cas(OZIRANJE_OD + 0.04), r: -5 },
      { t: cas(OZIRANJE_OD + 0.12), r: 5 },
      { t: cas(OZIRANJE_OD + 0.2), r: 0 },
    ],
    pentlja: [{ t: 0, r: 0 }, { t: POTOP_DO }],
    oko: [
      { t: 0, l: 1, d: 1, pogled: 0 },
      { t: OZIRANJE_OD },
      { t: cas(OZIRANJE_OD + 0.03), pogled: -1.6 },
      { t: cas(OZIRANJE_OD + 0.07) },
      { t: cas(OZIRANJE_OD + 0.11), pogled: 1.6 },
      { t: cas(OZIRANJE_OD + 0.16) },
      { t: cas(OZIRANJE_OD + 0.2), pogled: 0 },
      { t: 0.44 },
      { t: 0.45, l: 0.1, d: 0.1 },
      { t: 0.47, l: 1, d: 1 },
    ],
  }
}

/* Roka izza črte (prizor »kukaj«): čaka pod črto, dvigne se do table, ob
   potegu gre z njo navzdol. `pot` je razdalja, ki jo skupaj prehodita roka in
   tabla: isti časi in pojemanje na obeh sledeh, da se ne razmakneta. */
function potegRoke(
  od: number,
  doo: number,
  strg: number,
  strgNazaj: number,
  konec: number,
  pot: number,
): Kljuc[] {
  return [
    { t: 0, y: 32, o: 0 },
    { t: od },
    { t: cas(od + 0.005), o: 1, e: IZTEK },
    { t: doo, y: 0 },
    { t: strg, y: 3 },
    { t: strgNazaj, y: -0.5, e: 'ease-in' },
    { t: konec, y: pot },
  ]
}

/* Letalo vleče zastavico z napisom: izza logotipa do povezave »Domov« (na
   telefonu do roba zaslona). Hitro se pokaže, počasi preleti okno (napis se
   mora dati prebrati, zato je zastavica vsaj ~2,5 s cela vidna) in hitro
   izgine. Zastavica plapola (cikli po pasovih zastavice), propeler se vrti. */
function prizorLetalo({ umestitev, sirinaTable }: Kontekst): Sledi {
  const p = potLetala(umestitev.glava, sirinaTable)!

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
  const pot = potLestve(umestitev.glava, sirinaTable)!
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

const OBE: readonly Naprava[] = ['namizje', 'telefon']

/* Vsi prizori, po eden na `id` in napravo. Vrstni red ni pomemben: izbira je
   naključna (vrecaMaskote.ts). */
export const PRIZORI: readonly PrizorPodatki[] = [
  /* Dvig izza črte, počep in skok, tabla se razgrne, poskakovanje, namig,
     potop. */
  zOznako({
    id: 'tabla',
    trajanjeMs: 4600,
    naprave: OBE,
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
  }),

  zOznako({
    id: 'kukaj',
    trajanjeMs: 4800,
    naprave: OBE,
    slika: 'lik',
    vlek: true,
    tablaNaprej: true,
    sledi: prizorKukaj,
  }),

  zOznako({
    id: 'zogica',
    trajanjeMs: 4800,
    naprave: ['telefon'],
    slika: 'lik',
    lopar: true,
    sledi: prizorZogica,
  }),

  {
    id: 'zogica',
    trajanjeMs: 4900,
    naprave: ['namizje'],
    slika: 'lik',
    lopar: true,
    znakNaOznaki: true,
    sledi: prizorZogicaOznaka,
  },

  zOznako({
    id: 'letalo',
    trajanjeMs: 4600,
    naprave: OBE,
    slika: 'letalo',
    sledi: prizorLetalo,
    cikli: cikliLetala,
  }),

  /* Samo namizje: lestev sega 176 px pod vrh strani, torej pod črto glave, kjer
     telefon nima prostora. */
  {
    id: 'lestev',
    trajanjeMs: 4800,
    naprave: ['namizje'],
    slika: 'lestev',
    sledi: prizorLestev,
    cikli: cikliLestve,
  },
]

/* Prizori, ki se igrajo na dani napravi; `id` je v njih enolično. */
export function prizoriZaNapravo(naprava: Naprava): PrizorPodatki[] {
  return PRIZORI.filter((p) => p.naprave.includes(naprava))
}
