/* Iskanje po imenih igralcev - eno pravilo za vse iskalnike v vmesniku.

   Dve lastnosti, brez katerih iskalnik v dvorani ne pomaga:
   - brez šumnikov: »krizan« mora najti Križana, ker jih na telefonu nihče ne
     tipka s strešico;
   - po besedah in ne po začetku niza: »novak ana« najde Novak Ano ne glede na
     vrstni red vpisanega, »miha« pa vse Mihe (tudi po imenu, ne le priimku).

   Iskalno besedilo sestavi klicatelj (ime, priimek, klub …); tu je samo
   primerjava. */

/* Niz, pripravljen za primerjavo: male črke brez šumnikov. */
export function zaIskanje(v: string): string {
  return v
    .toLocaleLowerCase('sl')
    .normalize('NFD')
    .replace(/\p{Diacritic}/gu, '')
}

/* Vpisano razbito na besede; prazno iskanje da prazen seznam (= brez
   omejitve, o čemer odloči klicatelj). */
export function besedeIskanja(iskanje: string): string[] {
  return zaIskanje(iskanje).split(/\s+/).filter(Boolean)
}

/* Ali besedilo vsebuje VSE iskane besede. Prazen seznam besed ustreza vsem. */
export function ustrezaBesedam(besedilo: string, besede: string[]): boolean {
  if (besede.length === 0) return true
  const pripravljeno = zaIskanje(besedilo)
  return besede.every((beseda) => pripravljeno.includes(beseda))
}
