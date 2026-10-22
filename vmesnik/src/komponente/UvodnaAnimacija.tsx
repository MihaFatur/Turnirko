/* Uvodna animacija (DESIGN.md 5e; predaja design_handoff_uvodna_animacija,
   različica »1a · Poteze«, odločitev lastnika 22. 10. 2026).

   Ob prvem nalaganju aplikacije v zavihku papir čez celo okno pokaže znak
   Turnirka: poteze se naložijo od leve proti desni, prižge se zelena, ob znaku
   se izpiše »Turnirko«, logotip odleti točno na svoje mesto v glavi, črti glave
   se narišeta, vsebina se v vrstah dvigne. Logotip pristane ob 1,70 s, vse je
   na mestu pod 3 s. Dolžina je vedno enaka in ni vezana na nalaganje podatkov:
   ti se medtem nalagajo v ozadju, vrstica, ki pride med uvodom, pa se dvigne
   v svojem času, kot bi bila tam od začetka (opazovalec v `zazeniUvod`).

   Znamka ni slika, ampak ISTA postavitev kot logotip v glavi, povečana k-krat
   (pisava, razmik, znak in optična velikost pisave), zato ga ob pristanku
   pokrije do piksla. Črke so posamezni elementi, a stojijo tam, kjer bi stale
   v celi besedi (`Range` čez merilo): brez spodsekavanja bi logotip ob menjavi
   poskočil.

   Gibanje je Web Animations API s `fill: 'both'`; animirata se samo
   `transform` in `opacity`. Konec je vezan na `finished` animacij in ne na
   uro: ustavljena animacija (zajem posnetkov) ustavi tudi pospravljanje.
   Uvod je `aria-hidden`, fokusa ne prevzame, ozadje pa je do pristanka
   `inert`, da klik ali tipka ne zadene strani pod papirjem. */
import { useLayoutEffect, useRef, useState } from 'react'
import { createPortal } from 'react-dom'

import { KLJUC_UVODA, koncajUvod, smeUvod, zacniUvod } from '../pomozno/uvod'

/* Krivulje iz predaje. */
const IZHOD = 'cubic-bezier(.2,.8,.2,1)'
const INOUT = 'cubic-bezier(.65,0,.35,1)'
const POLET = 'cubic-bezier(.7,0,.2,1)'

/* Časovnica v ms (tempo »Srednje«). */
const POLET_OD = 1240
const POLET_TRAJA = 460
/* Logotip v glavi zamenja znamko; do tod je ozadje zaklenjeno. */
const PRISTANEK = POLET_OD + POLET_TRAJA
const VSEBINA_OD = PRISTANEK - 160
const KORAK_VRSTE = 30
/* Zadnja vrsta krene ob 1540 + 30 × 30 ms in je na mestu pred 3 s (WCAG
   2.2.2); na visokem zaslonu z drobnimi vrsticami se vse nadaljnje dvignejo z
   njo. */
const NAJVEC_VRST = 30

/* Mere logotipa telefonske glave (`.glava-telefon .glava__logotip`, znak 22
   px) za podstran, kjer ga glava ne kaže (puščica nazaj): znamka tam nima po
   čem meriti in namesto poleta zbledi. */
const LOGOTIP_TELEFONA = { pisava: 20, razmik: 8, znak: 22 }

const CRKE = [...'Turnirko']

const PISAVE = [
  "800 24px 'Bricolage Grotesque Variable'",
  "800 120px 'Bricolage Grotesque Variable'",
  "400 16px 'Karla Variable'",
  "700 19px 'Karla Variable'",
  "500 12px 'IBM Plex Mono'",
  "600 16px 'IBM Plex Mono'",
]

/* Elementi, ki se pri iskanju vrst ne razgradijo naprej: vsebina risbe ali
   polja ni vrsta strani. */
const CELOTE = new Set(['svg', 'SVG', 'CANVAS', 'IMG', 'VIDEO', 'IFRAME', 'SELECT', 'TEXTAREA', 'P'])

export function UvodnaAnimacija() {
  const [aktiven, nastaviAktiven] = useState(smeUvod)
  const zaslon = useRef<HTMLDivElement>(null)

  /* Po izrisu Postavitve (ta komponenta je njen zadnji otrok), da glava in
     vsebina že stojita, ko jih uvod izmeri. */
  useLayoutEffect(() => {
    const ovoj = zaslon.current
    if (!aktiven || !ovoj) return

    zacniUvod()
    const html = document.documentElement
    /* Zaklene samo, če ozadje ni že zaklenjeno - tujega zaklepa ne sprosti. */
    const koren = document.getElementById('koren')
    const zaklenjen = koren && !koren.hasAttribute('inert') ? koren : null
    html.classList.add('uvod-tece')
    zaklenjen?.setAttribute('inert', '')
    window.scrollTo(0, 0)

    const odkleni = () => zaklenjen?.removeAttribute('inert')
    const prekini = zazeniUvod(ovoj, odkleni, () => nastaviAktiven(false))

    return () => {
      prekini()
      odkleni()
      html.classList.remove('uvod-tece')
      koncajUvod()
    }
  }, [aktiven])

  if (!aktiven) return null

  return createPortal(
    <div ref={zaslon} className="uvod-zaslon" aria-hidden="true">
      <div className="uvod__podlaga" />
      <div className="uvod__sredina">
        <div className="uvod__znamka">
          <div className="uvod__vrsta">
            <div className="uvod__znak">
              <div className="uvod__znak-notranji">
                {[0, 1, 2, 3].map((i) => (
                  <div className="uvod__poteza" key={i}>
                    <div className="uvod__duh" />
                    <div className="uvod__polnilo" />
                  </div>
                ))}
              </div>
            </div>
            <div className="uvod__napis">
              {/* En sam tekstovni vozel: po njem se izmeri lega vsake črke. */}
              <span className="uvod__merilo">{CRKE.join('')}</span>
              {CRKE.map((crka, i) => (
                <span className="uvod__crka" key={i}>
                  {crka}
                </span>
              ))}
            </div>
          </div>
        </div>
      </div>
    </div>,
    document.body,
  )
}

/* Počaka pisave (največ 2,5 s; do takrat je viden samo papir), dva okvirja in
   viden zavihek, nato izmeri glavo in zažene vse animacije. Vrne prekinitev. */
function zazeniUvod(ovoj: HTMLDivElement, obPristanku: () => void, obKoncu: () => void): () => void {
  let preklicano = false
  let opazovalec: MutationObserver | null = null
  const animacije: Animation[] = []
  /* Dvignjeni elementi vsebine z vrsto, v kateri se dvignejo. */
  const dvignjeni = new Map<Element, { vrsta: number; animacija: Animation }>()

  const animiraj = (
    el: Element | null | undefined,
    kljuci: Keyframe[],
    zamik: number,
    trajanje: number,
    krivulja = 'linear',
  ): Animation | null => {
    if (!el) return null
    const animacija = el.animate(kljuci, { delay: zamik, duration: trajanje, easing: krivulja, fill: 'both' })
    animacije.push(animacija)
    return animacija
  }

  void (async () => {
    await Promise.race([
      Promise.all(PISAVE.map((pisava) => document.fonts.load(pisava).catch(() => null))),
      new Promise((razresi) => window.setTimeout(razresi, 2500)),
    ])
    await new Promise((razresi) => requestAnimationFrame(() => requestAnimationFrame(razresi)))
    await pocakajVidnost()
    if (preklicano) return
    try {
      predvajaj()
    } catch (napaka) {
      console.warn('Uvod ni uspel:', napaka)
      koncaj()
      return
    }
    try {
      sessionStorage.setItem(KLJUC_UVODA, '1')
    } catch {
      /* Brez shrambe se uvod ponovi ob naslednjem nalaganju. */
    }
  })()

  function predvajaj() {
    window.scrollTo(0, 0)
    const glava = document.querySelector<HTMLElement>('.glava-telefon, .glava')
    const telefon = glava?.classList.contains('glava-telefon') ?? false
    const logotip = glava?.querySelector<HTMLElement>('.glava__logotip') ?? null

    const najdi = <T extends Element>(izbira: string) => ovoj.querySelector<T>(izbira)!
    const znamka = najdi<HTMLElement>('.uvod__znamka')
    const vrsta = najdi<HTMLElement>('.uvod__vrsta')
    const znak = najdi<HTMLElement>('.uvod__znak')
    const notranji = najdi<HTMLElement>('.uvod__znak-notranji')
    const napis = najdi<HTMLElement>('.uvod__napis')
    const merilo = najdi<HTMLElement>('.uvod__merilo')
    const crke = [...ovoj.querySelectorAll<HTMLElement>('.uvod__crka')]
    const duhovi = [...ovoj.querySelectorAll('.uvod__duh')]
    const polnila = [...ovoj.querySelectorAll('.uvod__polnilo')]

    /* Logotip v glavi: znamka je ista postavitev, povečana k-krat. */
    let { pisava, razmik, znak: znakLogotipa } = LOGOTIP_TELEFONA
    if (logotip) {
      const slog = getComputedStyle(logotip)
      pisava = parseFloat(slog.fontSize)
      razmik = parseFloat(slog.columnGap) || 0
      znakLogotipa = logotip.querySelector('svg')?.getBoundingClientRect().width ?? znakLogotipa
    }
    const postavi = (k: number) => {
      znamka.style.fontSize = `${pisava * k}px`
      /* Brez tega Bricolage pri veliki pisavi izbere drugo optično velikost
         in besedi se ob pristanku ne pokrijeta. */
      znamka.style.fontVariationSettings = `'opsz' ${pisava}`
      vrsta.style.gap = `${razmik * k}px`
      znak.style.width = `${znakLogotipa * k}px`
      znak.style.height = `${znakLogotipa * k}px`
    }
    postavi(1)
    const L = (logotip ?? znamka).getBoundingClientRect()
    const W = ovoj.getBoundingClientRect().width
    const k = Math.max(1.5, Math.min(5, (W * (W <= 640 ? 0.82 : 0.56)) / L.width))
    postavi(k)
    const S = znakLogotipa * k

    /* Vsaka črka tam, kjer stoji v celi besedi (s spodsekavanjem). */
    const napisR = napis.getBoundingClientRect()
    const tekst = merilo.firstChild as Text
    crke.forEach((crka, i) => {
      const obseg = document.createRange()
      obseg.setStart(tekst, i)
      obseg.setEnd(tekst, i + 1)
      crka.style.left = `${obseg.getBoundingClientRect().left - napisR.left}px`
    })

    znamka.style.visibility = 'visible'
    const Z = znamka.getBoundingClientRect()
    const odmik = (Z.width - S) / 2
    const dx = L.left - Z.left
    const dy = L.top - Z.top

    /* Sidro pristanka: brez vidnega učinka, njegov konec odklene ozadje. Je
       prva animacija, zato po njej vrstice, ki pridejo pozneje, uskladijo
       svojo uro. */
    const sidro = animiraj(ovoj, [{ opacity: 1 }, { opacity: 1 }], PRISTANEK, 1)!
    sidro.finished
      .then(() => {
        if (preklicano) return
        ovoj.style.pointerEvents = 'none'
        obPristanku()
      })
      .catch(() => {})

    /* Poteze se naložijo od leve proti desni, nato se prižge zelena. */
    duhovi.slice(0, 3).forEach((duh, i) =>
      animiraj(duh, [{ opacity: 0 }, { opacity: 1 }], 20 + i * 30, 200, 'ease-out'),
    )
    animiraj(
      duhovi[3],
      [
        { opacity: 0 },
        { opacity: 1, offset: 0.25 },
        { opacity: 0.35, offset: 0.5625 },
        { opacity: 1, offset: 0.775 },
        { opacity: 1, offset: 0.85 },
        { opacity: 0, offset: 0.975 },
        { opacity: 0 },
      ],
      20,
      800,
    )
    ;[100, 220, 340].forEach((zamik, i) =>
      animiraj(
        polnila[i],
        [
          { transform: 'scaleY(0)', easing: 'cubic-bezier(.3,0,.2,1)' },
          { transform: 'scaleY(0.58)', offset: 0.45, easing: 'cubic-bezier(.5,0,.2,1)' },
          { transform: 'scaleY(1)' },
        ],
        zamik,
        320,
      ),
    )
    animiraj(
      polnila[3],
      [
        { transform: 'scaleY(0)', easing: 'cubic-bezier(.2,.9,.3,1)' },
        { transform: 'scaleY(1.12)', offset: 0.5, easing: 'ease-in-out' },
        { transform: 'scaleY(0.97)', offset: 0.78, easing: 'ease-out' },
        { transform: 'scaleY(1)' },
      ],
      660,
      360,
    )
    animiraj(
      notranji,
      [{ transform: 'scale(1)' }, { transform: 'scale(1.07)', offset: 0.3 }, { transform: 'scale(1)' }],
      680,
      450,
      'cubic-bezier(.3,.7,.3,1)',
    )

    /* Znak, ki je stal sam na sredini, se umakne levo in naredi prostor
       napisu; črke se dvignejo izpod reza. */
    animiraj(vrsta, [{ transform: `translateX(${odmik}px)` }, { transform: 'translateX(0px)' }], 760, 380, INOUT)
    crke.forEach((crka, i) =>
      animiraj(
        crka,
        [
          { opacity: 0, transform: 'translateY(80%)' },
          { opacity: 1, transform: 'translateY(0%)' },
        ],
        800 + i * 22,
        340,
        IZHOD,
      ),
    )

    /* Polet v glavo in menjava z logotipom; brez logotipa znamka zbledi. */
    if (logotip) {
      animiraj(
        znamka,
        [
          { transform: 'translate(0px, 0px) scale(1)' },
          { transform: `translate(${dx}px, ${dy}px) scale(${1 / k})` },
        ],
        POLET_OD,
        POLET_TRAJA,
        POLET,
      )
      animiraj(logotip, [{ opacity: 0 }, { opacity: 1 }], PRISTANEK, 1)
      animiraj(znamka, [{ opacity: 1 }, { opacity: 0 }], PRISTANEK + 30, 1)
    } else {
      znamka.style.transformOrigin = '50% 50%'
      animiraj(
        znamka,
        [
          { opacity: 1, transform: 'scale(1)' },
          { opacity: 0, transform: 'scale(0.96)' },
        ],
        POLET_OD,
        300,
        INOUT,
      )
    }
    animiraj(ovoj.querySelector('.uvod__podlaga'), [{ opacity: 1 }, { opacity: 0 }], POLET_OD + 100, 420, 'ease')

    /* Črti glave se narišeta od leve proti desni, tanka pred debelo. */
    const crtaGlave = [{ transform: 'scaleX(0)' }, { transform: 'scaleX(1)' }]
    animiraj(glava?.querySelector('.glava__crta-tanka'), crtaGlave, POLET_OD + 160, 500, INOUT)
    animiraj(glava?.querySelector('.glava__crta-debela'), crtaGlave, POLET_OD + 240, 500, INOUT)
    animiraj(
      document.querySelector('.spodnja-vrstica'),
      [{ transform: 'translateY(100%)' }, { transform: 'translateY(0%)' }],
      VSEBINA_OD,
      500,
      IZHOD,
    )

    dvigniVsebino(glava, telefon)
    void pocakajKonec()
  }

  /* ---- Vsebina: kaj se dvigne in v kateri vrsti ----

     Najprej glava (navigacija, nato uporabnik; na telefonu puščica nazaj,
     dejanja in uporabnik), za njo vsebina. Stran, ki svoj vrstni red pove
     sama (`data-uvod` = vrsta od začetka vsebine, domača stran), ga dobi
     natanko; drugje se dvignejo neposredni otroci `.vsebina > section` in
     vrstice njihovih seznamov, ki so v prvem zaslonu, po navpični legi. Kar je
     pod prvim zaslonom, se samo pokaže. */
  function dvigniVsebino(glava: HTMLElement | null, telefon: boolean) {
    const vrsteGlave: (Element | null | undefined)[] = telefon
      ? [
          glava?.querySelector('.glava-telefon__nazaj'),
          glava?.querySelector('.glava-telefon__dejanja:not(:empty)'),
          glava?.querySelector('.glava__uporabnik'),
        ]
      : [
          ...(glava?.querySelectorAll('.glava__navigacija .glava__povezava') ?? []),
          glava?.querySelector('.glava__uporabnik'),
        ]
    const prisotne = vrsteGlave.filter((el): el is Element => !!el)
    prisotne.forEach((el, i) => dvigni(el, i))
    const zamikVsebine = prisotne.length

    const vsebina = document.querySelector('.vsebina')
    if (!vsebina) return
    const izrecno = vsebina.querySelector('[data-uvod]') !== null
    const vrstaIzrecno = (el: Element) => zamikVsebine + (Number(el.getAttribute('data-uvod')) || 0)

    /* Razvrstitev po legi: enaka lega = ista vrsta. Nove lege (vrstica, ki
       pride med uvodom) se vrinejo; že dvignjenim vrsta ostane. */
    const lege: number[] = []
    const vrstaPoLegi = (vrh: number) => {
      let i = lege.findIndex((lega) => Math.abs(lega - vrh) < 1)
      if (i < 0) {
        lege.push(vrh)
        lege.sort((a, b) => a - b)
        i = lege.indexOf(vrh)
      }
      return zamikVsebine + i
    }
    const koreni = () => [
      ...vsebina.querySelectorAll(':scope > section > *'),
      ...document.querySelectorAll('.glava-telefon__vlozek > *'),
    ]

    if (izrecno) {
      vsebina.querySelectorAll('[data-uvod]').forEach((el) => dvigni(el, vrstaIzrecno(el)))
    } else {
      const kandidati = vrsteVPrvemZaslonu(koreni())
      kandidati.forEach((el) => vrstaPoLegi(el.getBoundingClientRect().top))
      kandidati.forEach((el) => dvigni(el, vrstaPoLegi(el.getBoundingClientRect().top)))
    }

    /* Podatki, ki pridejo med uvodom: nova vrstica se dvigne v svoji vrsti,
       kot bi bila tam od začetka (ura je usklajena s sidrom). Kar React
       odstrani (okostje, ko pridejo podatki), izgubi animacijo. */
    const animiran = (el: Element) => {
      for (let p: Element | null = el; p && p !== vsebina; p = p.parentElement) {
        if (dvignjeni.has(p)) return true
      }
      return false
    }
    const vVsebini = (el: Element) =>
      vsebina.contains(el) || (!izrecno && el.closest('.glava-telefon__vlozek') !== null)
    opazovalec = new MutationObserver((zapisi) => {
      for (const zapis of zapisi) {
        if (zapis.type === 'attributes') {
          const el = zapis.target as Element
          if (el.hasAttribute('data-uvod')) dvigni(el, vrstaIzrecno(el), true)
          continue
        }
        zapis.removedNodes.forEach(pozabi)
        zapis.addedNodes.forEach((vozel) => {
          if (!(vozel instanceof Element) || !vozel.isConnected || !vVsebini(vozel)) return
          if (izrecno) {
            const nove = [...(vozel.matches('[data-uvod]') ? [vozel] : []), ...vozel.querySelectorAll('[data-uvod]')]
            nove.forEach((el) => dvigni(el, vrstaIzrecno(el), true))
          } else if (!animiran(vozel)) {
            vrsteVPrvemZaslonu([vozel]).forEach((el) =>
              dvigni(el, vrstaPoLegi(el.getBoundingClientRect().top), true),
            )
          }
        })
      }
    })
    /* Čez celo telo: odstranjen je lahko tudi element glave. */
    opazovalec.observe(
      document.body,
      izrecno
        ? { childList: true, subtree: true, attributes: true, attributeFilter: ['data-uvod'] }
        : { childList: true, subtree: true },
    )
  }

  /* Animacija elementa, ki ni več v dokumentu, se v Chromu ne konča nikoli
     (`finished` ne pride) in bi zadržala konec uvoda - zato jo prekličemo. */
  function pozabi(vozel: Node) {
    if (!(vozel instanceof Element)) return
    animacije.forEach((animacija) => {
      const cilj = (animacija.effect as KeyframeEffect | null)?.target
      if (cilj && !cilj.isConnected && (cilj === vozel || vozel.contains(cilj))) {
        animacija.cancel()
        dvignjeni.delete(cilj)
      }
    })
  }

  function dvigni(el: Element, vrsta: number, pozno = false) {
    const n = Math.min(vrsta, NAJVEC_VRST)
    const prej = dvignjeni.get(el)
    if (prej?.vrsta === n) return
    prej?.animacija.cancel()
    const animacija = animiraj(
      el,
      [
        { opacity: 0, transform: 'translateY(14px)' },
        { opacity: 1, transform: 'translateY(0px)' },
      ],
      VSEBINA_OD + n * KORAK_VRSTE,
      500,
      IZHOD,
    )!
    dvignjeni.set(el, { vrsta: n, animacija })
    if (pozno) uskladi(animacija)
  }

  /* Animacija, ustvarjena pozneje, teče po isti uri kot ostale. */
  function uskladi(animacija: Animation) {
    const sidro = animacije[0]
    if (sidro.playState === 'paused') {
      animacija.pause()
      animacija.currentTime = sidro.currentTime
    } else if (sidro.startTime !== null) {
      animacija.startTime = sidro.startTime
    }
  }

  /* Konec, ko so končane VSE animacije - tudi tiste, ki so med čakanjem
     nastale (pozne vrstice). */
  async function pocakajKonec() {
    for (;;) {
      animacije.forEach((animacija) => {
        if ((animacija.effect as KeyframeEffect | null)?.target?.isConnected === false) animacija.cancel()
      })
      const prej = animacije.length
      /* Preklicana (idle) dobi nov `finished`, ki ne pride nikoli. */
      await Promise.all(
        animacije
          .filter((animacija) => animacija.playState !== 'idle')
          .map((animacija) => animacija.finished.catch(() => null)),
      )
      if (preklicano) return
      if (animacije.length === prej) break
    }
    koncaj()
  }

  /* Končno stanje vsake animacije je naravno stanje elementa, zato preklic
     ne premakne ničesar; papir se skrije prej, da ga preklic ne pokaže. */
  function koncaj() {
    opazovalec?.disconnect()
    ovoj.style.visibility = 'hidden'
    animacije.forEach((animacija) => animacija.cancel())
    obKoncu()
  }

  return () => {
    preklicano = true
    opazovalec?.disconnect()
    animacije.forEach((animacija) => animacija.cancel())
  }
}

function pocakajVidnost(): Promise<void> {
  if (document.visibilityState === 'visible') return Promise.resolve()
  return new Promise((razresi) => {
    const obSpremembi = () => {
      if (document.visibilityState !== 'visible') return
      document.removeEventListener('visibilitychange', obSpremembi)
      razresi()
    }
    document.addEventListener('visibilitychange', obSpremembi)
  })
}

/* Neposredni otroci in vrstice seznamov v prvem zaslonu. Visok element z več
   otroki (seznam, tabela) se razgradi na otroke, dokler vrstice niso nižje od
   tretjine zaslona. */
function vrsteVPrvemZaslonu(koreni: Element[]): Element[] {
  const meja = window.innerHeight
  const izhod: Element[] = []
  const razgradi = (el: Element) => {
    if (el.closest('.uvod-zaslon')) return
    if (getComputedStyle(el).display === 'contents') {
      ;[...el.children].forEach(razgradi)
      return
    }
    const r = el.getBoundingClientRect()
    if (r.width === 0 || r.height === 0 || r.top >= meja) return
    const otroci = [...el.children]
    if (r.height > meja / 3 && otroci.length > 1 && !CELOTE.has(el.tagName)) {
      otroci.forEach(razgradi)
      return
    }
    izhod.push(el)
  }
  koreni.forEach(razgradi)
  return izhod
}
