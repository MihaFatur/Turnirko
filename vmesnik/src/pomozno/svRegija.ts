/* Imena in oznake sistema SV regija: razponi mest, stopnje žreba in skupine.
   Cista besedila, brez izgleda - uporabljajo jih pogled nivojev, urejevalnika
   in listki, zato so na enem mestu (dve kopiji bi se razšli). */

/* »5. mesto« oz. »5.–8. mesto«. */
export function opisRazpona(od: number, doMesta: number): string {
  return od === doMesta ? `${od}. mesto` : `${od}.–${doMesta}. mesto`
}

/* Ime stopnje po številu udeležencev mreže: 2 = finale, 4 = polfinale ... */
export function imeStopnje(velikost: number): string {
  switch (velikost) {
    case 2: return 'Finale'
    case 4: return 'Polfinale'
    case 8: return 'Četrtfinale'
    case 16: return 'Osmina finala'
    default: return `1/${velikost / 2} finala`
  }
}

/* Ime tekme znotraj žreba: »Polfinale« v zgornjem razponu, »Polfinale za
   5.–8. mesto« in »Za 7. mesto« drugje. Glavni žreb ima finale, tolažilni
   samo »Za 9. mesto«: finale je ena sama tekma na turnirju. */
export function imeTekmeZreba(
  od: number,
  doMesta: number,
  prvoMestoZreba: number,
  glavni: boolean,
): string {
  const velikost = doMesta - od + 1
  if (velikost === 2) {
    return od === prvoMestoZreba && glavni ? 'Finale' : `Za ${od}. mesto`
  }
  const stopnja = imeStopnje(velikost)
  return od === prvoMestoZreba ? stopnja : `${stopnja} za ${opisRazpona(od, doMesta)}`
}

/* Oznaka skupine brez Skupine: »1A« -> »A« (Skupina je že v naslovu pogleda). */
export function crkaSkupine(oznaka: string): string {
  return oznaka.replace(/^\d+/, '')
}

/* Raven, na kateri se žreb začne, v rodilniku: »od četrtfinala«. */
export function imeRavni(velikostMreze: number): string {
  switch (velikostMreze) {
    case 2: return 'finala'
    case 4: return 'polfinala'
    case 8: return 'četrtfinala'
    case 16: return 'osmine finala'
    case 32: return 'šestnajstine finala'
    default: return `mreže ${velikostMreze}`
  }
}

/* Ravni, na katerih se lahko začnejo žrebi (velikost mreže), od najzgodnejše. */
export const RAVNI_ZREBA = [32, 16, 8, 4, 2]

/* Ime ravni za izbirnik: »Osmina finala«. */
export function imeRavniZaIzbiro(velikostMreze: number): string {
  switch (velikostMreze) {
    case 32: return 'Šestnajstina finala'
    case 16: return 'Osmina finala'
    case 4: return 'Polfinale'
    case 2: return 'Finale'
    default: return 'Četrtfinale'
  }
}

/* Velikosti žrebov za toliko igralcev, kot jih sprejme raven (zadnji dobi
   ostanek) - isto pravilo kot strežnik, samo za predogled v obrazcu. */
export function velikostiZrebov(igralcev: number, velikostMreze: number): number[] {
  const velikosti: number[] = []
  for (let ostalo = igralcev; ostalo > 0; ostalo -= velikostMreze) {
    velikosti.push(Math.min(velikostMreze, ostalo))
  }
  return velikosti
}

/* Koliko mest vsake skupine gre v žreb: »A« -> 2 pomeni, da sta A1 in A2 v
   njem. Viri žreba so po vrsti A1, B1 …, zato so mesta skupine v žrebu vedno
   zaporedna in v zaporednih žrebih naraščajo. */
export function mestVZrebu(viri: string[]): Map<string, number> {
  const mest = new Map<string, number>()
  for (const vir of viri) {
    const crka = vir.replace(/\d+$/, '')
    mest.set(crka, (mest.get(crka) ?? 0) + 1)
  }
  return mest
}
