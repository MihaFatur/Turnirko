/* Gibanje oglasa »Igralec Premium« (komponente/PremiumOglas.tsx): zavesa,
   nalepka, žig, ključavnica in maskota.

   Prenos iz prototipa (design_handoff_premium_popup): Web Animations API na
   elementih z oznakami `data-anim` (oglas), `data-m` (maskota) in `data-k`
   (ključavnica), brez knjižnice in brez keyframes v CSS. Isti pristop kot pri
   maskoti v glavi (animatorMaskote.ts), a ločen predvajalnik: tu ni prizorov kot
   podatka, ker je vsak zaslon enkraten in ga bere človek z oči na oči, ne kot
   mimoidoča živalca v glavi.

   Vsaka funkcija PRVA prekliče, kar teče na njenem poddrevesu, in vrne funkcijo
   za ustavitev: React v razvoju izvede učinek dvakrat, oglas pa se sme zapreti
   sredi animacije. `fill: 'both'` drži končno stanje, zato po koncu ni treba
   ničesar postavljati. */

export type PozaLika = 'zavesa' | 'semafor'

/* Predaja: pri `prefers-reduced-motion: reduce` brez animacij, takoj končno
   stanje. (To je namerno DRUGAČE kot pri maskoti v glavi, kjer lastnik nastopa ne
   ustavlja: tam je lik dekoracija in kratek, tu je celozaslonski oglas.) */
export function jeMirno(): boolean {
  return typeof window !== 'undefined' && window.matchMedia('(prefers-reduced-motion: reduce)').matches
}

function preklici(koren: Element) {
  koren.getAnimations({ subtree: true }).forEach((a) => a.cancel())
}

type Kljuc = Keyframe & { offset?: number }

function an(el: Element | null, kljuci: Kljuc[], moznosti: KeyframeAnimationOptions = {}) {
  if (!el) return null
  return el.animate(kljuci, { fill: 'both', easing: 'cubic-bezier(.3,.7,.3,1)', ...moznosti })
}

const vrtenje = (stopinj: number) => `rotate(${stopinj}deg)`
const r = (stopinj: number): Kljuc => ({ transform: vrtenje(stopinj) })

/* ---------- Maskota (poza zavesa / semafor) ---------- */

export function predvajajLika(svg: SVGSVGElement, poza: PozaLika): () => void {
  preklici(svg)
  if (jeMirno()) return () => {}
  const q = (ime: string) => svg.querySelector(`[data-m="${ime}"]`)

  if (poza === 'zavesa') {
    an(q('ramaL'), [r(28), { ...r(28), offset: 0.2 }, r(-14), r(0)], { duration: 1800 })
    an(
      q('figura'),
      [
        { transform: 'translateX(-3px)' },
        { transform: 'translateX(1.5px)', offset: 0.8 },
        { transform: 'translateX(0)' },
      ],
      { duration: 1800 },
    )
    an(q('podD'), [r(0), r(-35), r(0), r(-35), r(0)], {
      duration: 1100,
      delay: 1800,
      easing: 'ease-in-out',
    })
    an(q('glava'), [r(0), r(-8), r(0)], { duration: 700, delay: 1800 })
  } else {
    an(
      q('zoga'),
      [
        { transform: 'translateY(0)', easing: 'cubic-bezier(.2,.7,.4,1)' },
        { transform: 'translateY(-16px)', easing: 'cubic-bezier(.6,0,.8,.3)' },
        { transform: 'translateY(0)' },
      ],
      { duration: 560, iterations: 5, fill: 'none' },
    )
    an(q('podD'), [r(0), { ...r(4), offset: 0.1 }, r(0)], { duration: 560, iterations: 5, fill: 'none' })
    an(q('ramaL'), [r(0), r(-18), r(0)], {
      duration: 700,
      delay: 400,
      iterations: 3,
      easing: 'ease-in-out',
    })
  }
  return () => preklici(svg)
}

/* ---------- Ključavnica (1b) ---------- */

const T = 4700
/* Odmik v zaporedju (0–1) → čas na časovnici: odmiki do 0,36 so raztegnjeni na
   3800 ms, ostali so za 900 ms pomaknjeni (namerna pavza, ko ključ miruje). */
const w = (o: number) => (o <= 0.36 ? o * 3800 : o * 3800 + 900) / T
const KONFETI: [number, number, number][] = [
  [-70, -40, 260], [-40, -70, -300], [-10, -86, 200], [26, -80, -240], [58, -60, 320],
  [84, -30, -200], [-90, 10, 180], [100, 6, 280], [-54, 40, -160], [70, 44, 220],
]

type Sled = [number, number][]
const vzorci = (sled: Sled, t: number) => {
  for (let i = 0; i < sled.length - 1; i++) {
    const [a, va] = sled[i]
    const [b, vb] = sled[i + 1]
    if (t <= b) return va + (vb - va) * ((t - a) / (b - a || 1))
  }
  return sled[sled.length - 1][1]
}

export function predvajajKljucavnico(svg: SVGSVGElement): () => void {
  preklici(svg)
  if (jeMirno()) return () => {}
  const q = (ime: string) => svg.querySelector(`[data-k="${ime}"]`)
  const ank = (
    el: Element | null,
    kljuci: (Kljuc & { offset: number })[],
    moznosti: KeyframeAnimationOptions = {},
  ) =>
    an(el, kljuci.map((f) => ({ ...f, offset: w(f.offset) })), {
      duration: T,
      easing: 'ease-in-out',
      ...moznosti,
    })
  const rot = (d: number) => vrtenje(d)

  // pot: teče s ključem, zamah nazaj, sunek v ključavnico
  const X: Sled = [[0, -230], [0.24, -56], [0.31, -72], [0.355, 0], [1, 0]]
  const Yhod: Sled = [[0, 0], [0.04, -7], [0.08, 0], [0.12, -7], [0.16, 0], [0.2, -7], [0.24, 0], [1, 0]]
  const offs = [0, 0.04, 0.08, 0.12, 0.16, 0.2, 0.24, 0.31, 0.355, 1]
  ank(q('tim'), offs.map((o) => ({ offset: o, transform: `translateX(${vzorci(X, o)}px)` })), { easing: 'linear' })
  ank(
    q('kljucPot'),
    offs.map((o) => ({ offset: o, transform: `translate(${vzorci(X, o)}px, ${vzorci(Yhod, o)}px)` })),
    { easing: 'linear' },
  )
  ank(
    q('skok'),
    [
      ...[0, 0.04, 0.08, 0.12, 0.16, 0.2, 0.24].map((o) => ({
        offset: o,
        transform: `translateY(${vzorci(Yhod, o)}px)`,
      })),
      { offset: 0.64, transform: 'translateY(0)' },
      { offset: 0.69, transform: 'translateY(-26px)', easing: 'cubic-bezier(.5,0,.9,.5)' },
      { offset: 0.74, transform: 'translateY(0)' },
      { offset: 0.78, transform: 'translateY(-12px)', easing: 'cubic-bezier(.5,0,.9,.5)' },
      { offset: 0.82, transform: 'translateY(0)' },
      { offset: 1, transform: 'translateY(0)' },
    ],
    { easing: 'cubic-bezier(.2,.6,.4,1)' },
  )
  ank(q('nagib'), [
    { offset: 0, transform: rot(7) }, { offset: 0.24, transform: rot(4) },
    { offset: 0.31, transform: rot(-9) }, { offset: 0.355, transform: rot(6) },
    { offset: 0.42, transform: rot(0) }, { offset: 0.64, transform: rot(0) },
    { offset: 0.7, transform: rot(-4) }, { offset: 0.8, transform: rot(0) }, { offset: 1, transform: rot(0) },
  ])
  const noga = (znak: 1 | -1) =>
    ank(q(znak > 0 ? 'nogaL' : 'nogaD'), [
      ...[0, 0.04, 0.08, 0.12, 0.16, 0.2, 0.24].map((o, i) => ({
        offset: o,
        transform: rot(i === 6 ? 0 : (i % 2 ? 24 : -24) * znak),
      })),
      { offset: 0.66, transform: rot(0) }, { offset: 0.69, transform: rot(14 * znak) },
      { offset: 0.74, transform: rot(0) }, { offset: 1, transform: rot(0) },
    ])
  noga(1)
  noga(-1)

  // roke: desna drži ključ (−71°), leva zamahuje nazaj; po kliku obe v zrak
  ank(q('ramaD'), [
    { offset: 0, transform: rot(89) }, { offset: 0.62, transform: rot(89) },
    { offset: 0.68, transform: rot(-8) }, { offset: 0.72, transform: rot(0) },
    { offset: 0.8, transform: rot(-10) }, { offset: 0.88, transform: rot(0) }, { offset: 1, transform: rot(0) },
  ])
  ank(q('podD'), [
    { offset: 0, transform: rot(0) }, { offset: 0.4, transform: rot(0) },
    { offset: 0.46, transform: rot(-14) }, { offset: 0.52, transform: rot(4) }, { offset: 0.56, transform: rot(0) },
    { offset: 0.66, transform: rot(-30) }, { offset: 0.74, transform: rot(0) }, { offset: 1, transform: rot(0) },
  ])
  ank(q('ramaL'), [
    { offset: 0, transform: rot(-110) }, { offset: 0.04, transform: rot(-150) }, { offset: 0.08, transform: rot(-110) },
    { offset: 0.12, transform: rot(-150) }, { offset: 0.16, transform: rot(-110) }, { offset: 0.2, transform: rot(-150) },
    { offset: 0.31, transform: rot(-60) }, { offset: 0.355, transform: rot(-150) },
    { offset: 0.44, transform: rot(-120) }, { offset: 0.62, transform: rot(-120) },
    { offset: 0.7, transform: rot(6) }, { offset: 0.76, transform: rot(0) },
    { offset: 0.84, transform: rot(10) }, { offset: 0.92, transform: rot(0) }, { offset: 1, transform: rot(0) },
  ])
  ank(q('podL'), [
    { offset: 0, transform: rot(40) }, { offset: 0.62, transform: rot(40) },
    { offset: 0.72, transform: rot(0) }, { offset: 1, transform: rot(0) },
  ])
  ank(q('glava'), [
    { offset: 0, transform: rot(6) }, { offset: 0.24, transform: rot(6) },
    { offset: 0.31, transform: rot(-10) }, { offset: 0.355, transform: rot(4) },
    { offset: 0.42, transform: rot(8) }, { offset: 0.53, transform: rot(8) },
    { offset: 0.6, transform: rot(-6) }, { offset: 0.7, transform: rot(-12) },
    { offset: 0.8, transform: rot(0) }, { offset: 1, transform: rot(0) },
  ])
  ank(
    q('pentlja'),
    [0, 0.04, 0.08, 0.12, 0.16, 0.2, 0.24, 0.36, 0.7, 0.75, 1].map((o, i) => ({
      offset: o,
      transform: rot([0, 18, -6, 18, -6, 18, 0, 12, 0, 20, 0][i]),
    })),
  )
  const oko = [
    { offset: 0, opacity: 1 }, { offset: 0.66, opacity: 1 },
    { offset: 0.67, opacity: 0 }, { offset: 1, opacity: 0 },
  ]
  ank(q('okoOdprto'), oko, { easing: 'step-end' })
  ank(q('okoMezik'), oko.map((k) => ({ ...k, opacity: 1 - k.opacity })), { easing: 'step-end' })
  const usta = [
    { offset: 0, opacity: 0 }, { offset: 0.24, opacity: 0 }, { offset: 0.25, opacity: 1 },
    { offset: 0.56, opacity: 1 }, { offset: 0.57, opacity: 0 }, { offset: 1, opacity: 0 },
  ]
  ank(q('ustaZ'), usta, { easing: 'step-end' })
  ank(q('ustaS'), usta.map((k) => ({ ...k, opacity: 1 - k.opacity })), { easing: 'step-end' })

  // ključ se zavrti (pol obrata okoli svoje osi)
  ank(
    q('kljucObrat'),
    [
      { offset: 0, transform: 'scaleY(1)' }, { offset: 0.4, transform: 'scaleY(1)' },
      { offset: 0.47, transform: 'scaleY(0.08)' }, { offset: 0.53, transform: 'scaleY(-1)' },
      { offset: 1, transform: 'scaleY(-1)' },
    ],
    { easing: 'linear' },
  )

  // ključavnica
  ank(
    q('telo'),
    [
      { offset: 0, transform: 'translate(0,0)' }, { offset: 0.355, transform: 'translate(0,0)' },
      { offset: 0.37, transform: 'translate(5px,0)' }, { offset: 0.39, transform: 'translate(-2px,0)' },
      { offset: 0.41, transform: 'translate(0,0)' }, { offset: 0.535, transform: 'translate(0,0)' },
      { offset: 0.55, transform: 'translate(0,3px)' }, { offset: 0.57, transform: 'translate(0,-2px)' },
      { offset: 0.6, transform: 'translate(0,0)' }, { offset: 1, transform: 'translate(0,0)' },
    ],
    { easing: 'linear' },
  )
  ank(
    q('lukDvig'),
    [
      { offset: 0, transform: 'translateY(26px)' }, { offset: 0.535, transform: 'translateY(26px)' },
      { offset: 0.565, transform: 'translateY(-8px)' }, { offset: 0.59, transform: 'translateY(2px)' },
      { offset: 0.61, transform: 'translateY(0)' }, { offset: 1, transform: 'translateY(0)' },
    ],
    { easing: 'cubic-bezier(.2,.8,.3,1)' },
  )
  ank(
    q('lukKlap'),
    [
      { offset: 0, transform: 'scaleX(-1)' }, { offset: 0.61, transform: 'scaleX(-1)' },
      { offset: 0.7, transform: 'scaleX(1.12)' }, { offset: 0.74, transform: 'scaleX(0.96)' },
      { offset: 0.78, transform: 'scaleX(1)' }, { offset: 1, transform: 'scaleX(1)' },
    ],
    { easing: 'cubic-bezier(.4,0,.3,1)' },
  )
  const odklep = [
    { offset: 0, fill: '#FBF9F5' }, { offset: 0.535, fill: '#FBF9F5' },
    { offset: 0.56, fill: '#7BB900' }, { offset: 1, fill: '#7BB900' },
  ]
  ank(q('luknja'), odklep, { easing: 'linear' })
  ank(
    q('trak'),
    [
      { offset: 0, fill: '#4A433D' }, { offset: 0.535, fill: '#4A433D' },
      { offset: 0.55, fill: '#FBF9F5' }, { offset: 0.62, fill: '#7BB900' }, { offset: 1, fill: '#7BB900' },
    ],
    { easing: 'linear' },
  )
  ank(
    q('iskre'),
    [
      { offset: 0, opacity: 0, transform: 'translateY(40px) scale(.6)' },
      { offset: 0.54, opacity: 0, transform: 'translateY(40px) scale(.6)' },
      { offset: 0.57, opacity: 1, transform: 'translateY(40px) scale(1)' },
      { offset: 0.66, opacity: 0, transform: 'translateY(30px) scale(1.25)' },
      { offset: 1, opacity: 0, transform: 'translateY(30px) scale(1.25)' },
    ],
    { easing: 'ease-out' },
  )
  ank(
    q('klik'),
    [
      { offset: 0, opacity: 0, transform: 'scale(.2)' }, { offset: 0.545, opacity: 0, transform: 'scale(.2)' },
      { offset: 0.585, opacity: 1, transform: 'scale(1.25)' }, { offset: 0.62, opacity: 1, transform: 'scale(.95)' },
      { offset: 0.66, opacity: 1, transform: 'scale(1)' }, { offset: 1, opacity: 1, transform: 'scale(1)' },
    ],
    { easing: 'ease-out' },
  )
  svg.querySelectorAll('[data-k="kf"]').forEach((el, i) => {
    const [dx, dy, kot] = KONFETI[i % KONFETI.length]
    ank(
      el,
      [
        { offset: 0, opacity: 0, transform: 'translate(0,0) rotate(0)' },
        { offset: 0.55, opacity: 0, transform: 'translate(0,0) rotate(0)' },
        { offset: 0.56, opacity: 1, transform: 'translate(0,0) rotate(0)' },
        {
          offset: 0.72, opacity: 1,
          transform: `translate(${dx}px,${dy}px) rotate(${kot / 2}deg)`,
          easing: 'cubic-bezier(.4,0,1,1)',
        },
        { offset: 0.95, opacity: 0, transform: `translate(${dx * 1.3}px,${dy + 150}px) rotate(${kot}deg)` },
        { offset: 1, opacity: 0, transform: `translate(${dx * 1.3}px,${dy + 150}px) rotate(${kot}deg)` },
      ],
      { easing: 'cubic-bezier(.1,.7,.3,1)' },
    )
  })
  return () => preklici(svg)
}

/* ---------- Zavesa, nalepka in žig (1a, 1c) ---------- */

/* Kaj od tega teče na strani, določajo oznake `data-anim`:
   - `zavesa` (1a): zdrsne iz `data-shift` na 0, 20 % časa miruje;
   - `nalepka` (1a): priletí z zamikom 1700 ms;
   - `zig` (1c): pade z zamikom 2150 ms, ob udarcu se vrstica z izidom stresne
     (vrstica je starš žiga). */
export function predvajajOglas(koren: HTMLElement): () => void {
  const cilji = [...koren.querySelectorAll<HTMLElement>('[data-anim]')].flatMap((el) =>
    el.dataset.anim === 'zig' && el.parentElement ? [el, el.parentElement] : [el],
  )
  /* Prekliče se samo to, kar tu teče: maskota in ključavnica imata svoje učinke
     (v otroku, torej pred tem) in bi jim splošen preklic odvzel gibanje. */
  const ustavi = () => cilji.forEach((el) => el.getAnimations().forEach((an) => an.cancel()))
  ustavi()
  if (jeMirno()) return () => {}
  const o = { fill: 'both' as const }
  koren.querySelectorAll<HTMLElement>('[data-anim]').forEach((el) => {
    const t = el.dataset.anim
    if (t === 'zavesa') {
      const s = el.dataset.shift
      el.animate(
        [
          { transform: `translateX(${s})` },
          { transform: `translateX(${s})`, offset: 0.2 },
          { transform: 'translateX(0)' },
        ],
        { ...o, duration: 1800, easing: 'cubic-bezier(.5,0,.2,1)' },
      )
    } else if (t === 'nalepka') {
      el.animate(
        [
          { opacity: 0, transform: 'scale(2.8) rotate(-10deg)' },
          { opacity: 1, transform: 'scale(.9) rotate(7deg)', offset: 0.7 },
          { opacity: 1, transform: 'scale(1) rotate(7deg)' },
        ],
        { ...o, duration: 420, delay: 1700, easing: 'cubic-bezier(.6,0,.9,.4)' },
      )
    } else if (t === 'zig') {
      el.animate(
        [
          { opacity: 0, transform: 'scale(2.6) rotate(-14deg)' },
          { opacity: 1, transform: 'scale(.92) rotate(-8deg)', offset: 0.7 },
          { opacity: 1, transform: 'scale(1) rotate(-8deg)' },
        ],
        { ...o, duration: 420, delay: 2150, easing: 'cubic-bezier(.6,0,.9,.4)' },
      )
      el.parentElement?.animate(
        [{ transform: 'none' }, { transform: 'translateY(4px)', offset: 0.3 }, { transform: 'none' }],
        { duration: 260, delay: 2450 },
      )
    }
  })
  return ustavi
}

/* Poskok številke ob točki (1c): navzgor in nazaj. */
export function poskokStevilke(el: Element | null) {
  el?.animate([{ transform: 'translateY(-10px) scale(1.18)' }, { transform: 'none' }], {
    duration: 220,
    easing: 'cubic-bezier(.2,.8,.3,1)',
  })
}
