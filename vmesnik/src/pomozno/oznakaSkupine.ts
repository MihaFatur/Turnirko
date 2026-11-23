/* Oznaka skupine po vrsti: »1«, »2«, »3« … (odločitev lastnika, 23. 11. 2026; prej
   črke). Mora se ujemati z zaledjem (`pomozno/OznakaSkupine.java`), saj okna za
   ročni žreb pokažejo oznako, še preden jo strežnik zapiše.

   Oznaka je besedilo, zato je treba skupine razvrščati numerično (»2« pred »10«):
   `primerjajSkupine`. Starejši dogodki imajo še črke (A, B …); `numeric: true`
   jih razvrsti pravilno tudi takrat. */

export function oznakaSkupine(indeks: number): string {
  return String(indeks + 1)
}

/* Razvrščanje skupin po oznaki: številke po vrednosti, črke abecedno. */
export function primerjajSkupine(a: { oznaka: string }, b: { oznaka: string }): number {
  return a.oznaka.localeCompare(b.oznaka, 'sl', { numeric: true })
}

/* Skupine znotraj nivoja SV regije so črke po slovenski abecedi brez Č, Š, Ž
   (A … P, R, S, T, U, V, Z): »Skupina 1, 2 …« je tam nivo, zato številke znotraj
   njega ne bi imele pomena. Ko črk zmanjka (nad 22), se pred črko doda Z (ZA,
   ZB …), da abecedni vrstni red ostane vrstni red skupin. */
export const ABECEDA_SKUPIN = 'ABCDEFGHIJKLMNOPRSTUVZ'

export function crkaPoVrsti(indeks: number): string {
  let predpona = ''
  let ostanek = indeks
  while (ostanek >= ABECEDA_SKUPIN.length) {
    predpona += 'Z'
    ostanek -= ABECEDA_SKUPIN.length
  }
  return predpona + ABECEDA_SKUPIN.charAt(ostanek)
}
