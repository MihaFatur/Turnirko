/* Pravila nastopanja maskote »Igralec Premium« (komponente/Maskota.tsx).

   Maskota je oglas, oglas pa ne sme postati šum: pojavi se redko, naključno in
   samo ob primernem trenutku. Pravila so zbrana tu, ločeno od izrisa, da se
   jih da brati (in spreminjati) na enem mestu. */

/* Razpon [najmanj, največ] v ms (odločitev lastnika, 24. 9. 2026): prvi nastop
   10–25 s po nalaganju, nato v enakih razmakih 60–80 s do meje na nalaganje
   (`NAJVEC_NASTOPOV_NA_NALAGANJE`). Razmik se šteje od konca prejšnjega
   nastopa. */
export const PRVI_NASTOP_MS: readonly [number, number] = [10_000, 25_000]
export const ZAMIK_MED_NASTOPI_MS: readonly [number, number] = [60_000, 80_000]

/* Kadar trenutek ni primeren (skrit zavihek, odprto okno, premalo prostora),
   nastopa ne štejemo, ampak poskusimo znova čez ta čas. */
export const PONOVNI_POSKUS_MS = 15_000

/* Na nalaganje strani (odločitev lastnika, 24. 9. 2026): osvežitev zaporedje
   začne znova (prvi nastop spet po 10–25 s). Števec zato živi v modulu in ne v
   `sessionStorage`; premikanje po aplikaciji strani ne naloži znova, torej
   števca ne ponastavi. */
export const NAJVEC_NASTOPOV_NA_NALAGANJE = 3

let nastopovOdNalaganja = 0

export function nakljucniZamik([najmanj, najvec]: readonly [number, number]): number {
  return najmanj + Math.random() * (najvec - najmanj)
}

export function steviloNastopov(): number {
  return nastopovOdNalaganja
}

export function zabeleziNastop() {
  nastopovOdNalaganja += 1
}

/* Ali je zdaj primeren trenutek za nastop (ne glede na to, ali se prizor v
   glavo prilega: to preveri umestitevMaskote.ts).

   `prefers-reduced-motion` NAMENOMA ne ustavi nastopa (odločitev lastnika,
   28. 9. 2026). Windows jo sporoči brskalniku, kadar so v sistemu izklopljeni
   »Učinki animacije« — to pa pogosto ni izbira gledalca, ampak privzetek
   šibkejšega prenosnika, šolskega računalnika ali oddaljenega namizja, in
   maskote na takih računalnikih ni bilo nikoli. Nastop je pod 5 s (WCAG
   2.2.2 ne terja gumba za ustavitev) in ga gledalec ne sproži sam. */
export function jePrimernTrenutek(): boolean {
  if (document.hidden) return false

  /* Odprto okno ali predal zaklene ozadje z `inert` (ModalnoOkno, PredalVec):
     maskota v ozadju bi gledalca motila pri vnosu. */
  if (document.getElementById('koren')?.hasAttribute('inert')) return false

  /* Odprt uporabniški meni: prizor na telefonu skrije oznako, iz katere se
     meni odpre, na namizju pa bi lik stal čez meni. */
  if (document.querySelector('.uporabnik-meni')) return false

  /* Oznaka ima fokus (tipkovnica): prizor jo za nekaj sekund skrije in fokus
     bi zdrknil na telo strani. */
  if (document.querySelector('.glava__uporabnik:focus-within')) return false

  return true
}

/* `?maskota` v naslovu takoj pokaže nastop; `?maskota=<prizor>` izbere prizor,
   `&napis=<1..n>` napis. Brez tega bi kombinacijo lahko ogledal samo tisti, ki
   10 do 25 sekund nepremično čaka. */
export function predogledIzNaslova(): { prizor: string | null; napis: number | null } | null {
  const parametri = new URLSearchParams(window.location.search)
  if (!parametri.has('maskota')) return null
  const napis = Number(parametri.get('napis'))
  return {
    prizor: parametri.get('maskota') || null,
    napis: Number.isInteger(napis) && napis > 0 ? napis : null,
  }
}

/* Naključen element, a nikoli isti kot prejšnji (če je izbira sploh možna).
   Samo za predogled (`?maskota`): nastope po urniku izbira vreča
   (vrecaMaskote.ts), ki je enakomerna tudi na kratek rok. */
export function izberiNakljucno<T>(elementi: readonly T[], zadnji: T | null): T {
  const izbira = elementi.length > 1 ? elementi.filter((e) => e !== zadnji) : elementi
  return izbira[Math.floor(Math.random() * izbira.length)]
}
