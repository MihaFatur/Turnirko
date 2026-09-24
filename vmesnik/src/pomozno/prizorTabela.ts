/* Prizor v tabeli paketov (registracija, korak »Paket«): lik pade z vrha tabele,
   skače po vrsticah navzdol in ob vsaki, ki jo ima samo Premium, pokaže nanjo —
   okvir se pojavi okoli celice Premium —, nazadnje se potopi skozi spodnji rob.

   Vrstice tabele so odvisne od širine okna in pisave, zato je prizor funkcija
   IZMERE (izmeriTabelo), ne konstanta. Isto načelo kot pri prizorih čez glavo
   (umestitevMaskote.ts): če se ne prilega, ga ni — bolje nič kot lik čez
   besedilo. Igra ga isti predvajalnik in isti lik kot maskoto v glavi
   (animatorMaskote.ts, komponente/MaskotaPrizori.tsx). */
import type { Kljuc, Predvajljiv } from './animatorMaskote'
import { PADEC, VZPON, cas } from './prizoriMaskote'
import { MERILO_LIKA } from './umestitevMaskote'

export interface IzmeraTabele {
  /* Mere tabele v pikslih: platno je čez celotno tabelo in odreže vse, kar bi
     lik zaneslo čez rob. */
  sirina: number
  visina: number
  /* Vodoravna sredina lika: desni konec prvega stolpca, tik pred »Brezplačno«. */
  x: number
  /* Vrstice, ki jih ima samo Premium: zgornji rob in tla (črta pod vrstico). */
  vrstice: { zgoraj: number; tla: number }[]
  /* Okvir okoli celice Premium v vrstici. */
  okvir: { x: number; sirina: number; visina: number }
}

/* Sredina lika je toliko levo od stolpca »Brezplačno«; roke in stopala segajo
   od nje najdlje POL_SIRINE_LIKA na vsako stran. */
const ODMIK_OD_STOLPCA = 26
const POL_SIRINE_LIKA = 14
/* Zrak med koncem besedila vrstice in likom. */
const ZRAK = 8

/* Izmeri tabelo (`.registracija__primerjava`) ali vrne null, če se prizor ne
   prilega: nobene vrstice samo za Premium, besedilo vrstice se prelomi ali sega
   do lika (preozko okno). Pri vsaki vrstici, ne samo pri tistih, ki jih lik
   kaže: pade mimo prvih dveh. */
export function izmeriTabelo(tabela: HTMLElement): IzmeraTabele | null {
  const t = tabela.getBoundingClientRect()
  const vrstice = [...tabela.querySelectorAll<HTMLElement>('.registracija__primerjava-vrstica')]
  if (vrstice.length === 0) return null

  let x = 0
  let okvir: IzmeraTabele['okvir'] | null = null
  const dodatne: IzmeraTabele['vrstice'] = []

  for (const vrstica of vrstice) {
    const r = vrstica.getBoundingClientRect()
    const funkcija = vrstica.querySelector('.registracija__primerjava-funkcija')
    const vrednosti = vrstica.querySelectorAll('.registracija__primerjava-vrednost')
    if (!funkcija || vrednosti.length < 2) return null

    const brez = vrednosti[0].getBoundingClientRect()
    const premium = vrednosti[1].getBoundingClientRect()
    x = brez.left - t.left - ODMIK_OD_STOLPCA

    const besedilo = document.createRange()
    besedilo.selectNodeContents(funkcija)
    if (besedilo.getClientRects().length > 1) return null
    if (besedilo.getBoundingClientRect().right - t.left + ZRAK > x - POL_SIRINE_LIKA) return null

    if (vrednosti[0].classList.contains('registracija__primerjava-vrednost--ne')) {
      dodatne.push({ zgoraj: r.top - t.top, tla: r.bottom - t.top - 1 })
      /* Okvir je za eno višino vrstice: vse morajo biti enako visoke, sicer je
         katera prelomljena in prizor ne bi ustrezal. */
      if (okvir && Math.abs(okvir.visina - (r.height - 3)) > 2) return null
      okvir = { x: premium.left - t.left + 1, sirina: premium.width - 2, visina: r.height - 3 }
    }
  }

  if (dodatne.length === 0 || !okvir) return null
  return { sirina: t.width, visina: t.height, x, vrstice: dodatne, okvir }
}

/* Časi (ms): pad z vrha, mirovanje ob vrstici (najdlje), skok med vrsticama in
   potop na koncu. Skupaj ostane pod 5 s (WCAG 2.2.2). */
const PAD_MS = 520
const KAZE_MS = 640
const SKOK_MS = 380
const POTOP_MS = 460
const NAJDLJE_MS = 4750
/* Ob tem času mirovanja se lik še ne umiri (pristanek 140 ms, priprava na
   skok 90 ms). */
const NAJMANJ_KAZE_MS = 300

/* Počep ob pristanku in odskoku (enote lika). */
const POCEP = 3
const POCEP_NOGE = 0.72
/* Kako visoko lik skoči nad vrstico, iz katere odskoči. */
const VISINA_SKOKA = 15

const SPUSCENE = { r: 20, p: 10 }
const VZDIGNJENE = { r: 150, p: 22 }
const KAZE = { r: 88, p: 4 }
const KO_SKOCI = { r: 130, p: 25 }

export function prizorTabela(izmera: IzmeraTabele): Predvajljiv<void> {
  const n = izmera.vrstice.length
  const kaze = Math.max(
    NAJMANJ_KAZE_MS,
    Math.min(KAZE_MS, (NAJDLJE_MS - PAD_MS - POTOP_MS - (n - 1) * SKOK_MS) / n),
  )
  const trajanjeMs = Math.round(PAD_MS + n * kaze + (n - 1) * SKOK_MS + POTOP_MS)
  const t = (ms: number) => cas(ms / trajanjeMs)

  const prva = izmera.vrstice[0]
  /* Odmik telesa od mirovne lege na prvi vrstici, v enotah lika (merilo
     MERILO_LIKA). Noge zato ne sledijo telesu (`noge` na vsakem ključu). */
  const yVrstice = (i: number) => (izmera.vrstice[i].tla - prva.tla) / MERILO_LIKA
  /* Stopala nad zgornjim robom tabele (pad) in celoten lik pod spodnjim (potop);
     oboje merjeno od tal prve vrstice, kot vsak odmik telesa. */
  const yZunaj = (-4 - prva.tla) / MERILO_LIKA
  const yPotop = (izmera.visina + 44 - prva.tla) / MERILO_LIKA

  const pristanek = (i: number) => PAD_MS + i * (kaze + SKOK_MS)
  const odhod = (i: number) => pristanek(i) + kaze

  const figura: Kljuc[] = [
    { t: 0, y: yZunaj, noge: 1.2, e: PADEC },
    { t: t(PAD_MS), y: 0, noge: 1.15, e: 'ease-out' },
    { t: t(PAD_MS + 55), y: POCEP, noge: POCEP_NOGE },
    { t: t(PAD_MS + 140), y: 0, noge: 1 },
  ]
  /* Med padom roke ostanejo v zraku, glava in oči pa ravno, do pristanka. */
  const rokaD: Kljuc[] = [{ t: 0, ...VZDIGNJENE }, { t: t(PAD_MS) }]
  const rokaL: Kljuc[] = [{ t: 0, ...VZDIGNJENE }, { t: t(PAD_MS) }]
  const glava: Kljuc[] = [{ t: 0, r: 0 }, { t: t(PAD_MS) }]
  const pentlja: Kljuc[] = [{ t: 0, r: 0 }, { t: t(PAD_MS), r: -10 }, { t: t(PAD_MS + 140), r: 0 }]
  const oko: Kljuc[] = [{ t: 0, l: 1, d: 1, pogled: 0 }, { t: t(PAD_MS) }]
  const okvir: Kljuc[] = [{ t: 0, y: prva.zgoraj + 1, o: 0 }]

  for (let i = 0; i < n; i++) {
    const p = pristanek(i)
    const o = odhod(i)
    const yi = yVrstice(i)

    /* Ob vrstici: roka pokaže vstran (desno, proti Premium), glava in oči
       gledajo tja; ob daljšem mirovanju roka še dvakrat pokaže. */
    rokaD.push({ t: t(p + 150), ...KAZE })
    rokaL.push({ t: t(p + 150), ...SPUSCENE })
    if (kaze >= 560) {
      rokaD.push({ t: t(p + 300), r: 96, p: 0 }, { t: t(p + 420), ...KAZE })
    }
    glava.push({ t: t(p + 150), r: 4 })
    oko.push({ t: t(p + 120), pogled: 1.4 })
    if (i === 1 && kaze >= 450) {
      oko.push({ t: t(p + 300), l: 0.1, d: 0.1 }, { t: t(p + 340), l: 1, d: 1 })
    }
    okvir.push(
      { t: t(p + 90), y: izmera.vrstice[i].zgoraj + 1, o: 0 },
      { t: t(p + 170), o: 1 },
      { t: t(o - 30) },
      { t: t(o + 20), o: 0 },
    )

    if (i < n - 1) {
      const yn = yVrstice(i + 1)
      /* Skok: počep, vzpon nad vrstico, pad na naslednjo, počep in umiritev. */
      figura.push(
        { t: t(o - 90), y: yi, noge: 1 },
        { t: t(o), y: yi + POCEP, noge: POCEP_NOGE, e: VZPON },
        { t: t(o + 140), y: yi - VISINA_SKOKA, noge: 1.2, e: PADEC },
        { t: t(o + SKOK_MS), y: yn, noge: 1.15, e: 'ease-out' },
        { t: t(o + SKOK_MS + 55), y: yn + POCEP, noge: POCEP_NOGE },
        { t: t(o + SKOK_MS + 140), y: yn, noge: 1 },
      )
      for (const roka of [rokaD, rokaL]) {
        roka.push(
          { t: t(o - 90) },
          { t: t(o), r: 45, p: 20 },
          { t: t(o + 140), ...KO_SKOCI },
          { t: t(o + SKOK_MS), r: 110, p: 25 },
        )
      }
      glava.push({ t: t(o), r: 0 })
      oko.push({ t: t(o), pogled: 0 })
      pentlja.push(
        { t: t(o + 140), r: 14 },
        { t: t(o + SKOK_MS), r: -10 },
        { t: t(o + SKOK_MS + 140), r: 0 },
      )
    } else {
      /* Zadnja vrstica: počep in potop skozi spodnji rob tabele. */
      figura.push(
        { t: t(o - 90), y: yi, noge: 1 },
        { t: t(o), y: yi + POCEP, noge: POCEP_NOGE, e: 'ease-in' },
        { t: 1, y: yPotop, noge: 1 },
      )
      for (const roka of [rokaD, rokaL]) {
        roka.push({ t: t(o - 90) }, { t: t(o), r: 45, p: 20 }, { t: 1, ...KO_SKOCI })
      }
      glava.push({ t: t(o), r: 0 })
      oko.push({ t: t(o), pogled: 0 })
    }
  }

  return {
    id: 'tabela',
    trajanjeMs,
    sledi: () => ({ figura, rokaL, rokaD, glava, pentlja, oko, okvir }),
  }
}
