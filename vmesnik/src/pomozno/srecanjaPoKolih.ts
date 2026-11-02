/* Stanje in pravila pogleda »Srečanja po kolih« v oknu »Termini kol«: vnos
   kola, vnos srečanja in ugotavljanje, kje ekipa že igra.

   Ekipo ločimo po prikazanem imenu: v ligi je enolično (LigaStoritev.dodajEkipo),
   okno pa nosi imena in ne id-jev ekip.

   Ekipa v kolu praviloma igra eno srečanje; pri ligi z urami kola (večer z več
   krogi) toliko srečanj, kolikor je ur. Temu pravimo »običajno« (`dovoljeno`):
   več srečanj v kolu je lahko hoteno (nadoknadeni tekmi v enem večeru), zato
   okno samo opozori in shranjevanja ne ustavi. */

export interface VnosKola {
  kolo: number
  datum: string
  ura: string
}

export interface VnosSrecanja {
  id: number
  domaci: string
  gost: string
  /* Kolo iz žreba — vanj se srečanje vrne in po njem je prestavljeno. */
  izvirnoKolo: number
  /* Kolo, v katerem se igra po vnosu; null = čaka na termin. */
  kolo: number | null
  /* Lasten dan prestavljenega srečanja; null = sledi dnevu kola. */
  datum: string | null
  ura: string
  /* Ura kola, ob kateri srečanje igra po razporedu — po njej polnilo vrne
     srečanju uro lige, tudi ko je bilo prestavljeno ali ure še nima. */
  uraVKolu: number
  razlog: string
  /* Stanje na strežniku: po njem se vidi, kaj je vnos spremenil. */
  prej: { kolo: number | null; razlog: string }
  odigrano: boolean
  /* Odigrano ali začeto srečanje ima dan, zato ne more čakati na termin. */
  smeCakati: boolean
}

/* Vrednost za »termin še ni znan«; kolo je številka. */
export const CAKA = 'caka'

export const NAJVEC_ZNAKOV_RAZLOGA = 200

export function jePrestavljeno(s: VnosSrecanja): boolean {
  return s.kolo === null || s.kolo !== s.izvirnoKolo
}

export function igra(s: VnosSrecanja, ekipa: string): boolean {
  return s.domaci === ekipa || s.gost === ekipa
}

export function oznakaSrecanja(s: VnosSrecanja): string {
  return `${s.domaci} – ${s.gost}`
}

/* Imena ekip, slovensko urejena (CLAUDE.md: nikoli privzeta primerjava). */
export function ekipeVnosov(vnosi: VnosSrecanja[]): string[] {
  const imena = new Set<string>()
  for (const s of vnosi) {
    imena.add(s.domaci)
    imena.add(s.gost)
  }
  return [...imena].sort((a, b) => a.localeCompare(b, 'sl'))
}

/* Ekipa, ki v kolu že igra »dovolj«, in njena druga srečanja v njem. */
export interface Zasedenost {
  ekipa: string
  druga: VnosSrecanja[]
}

/* Katera od ekip srečanja bi v kolu `kolo` imela več srečanj, kot jih ima
   običajno, če bi srečanje igralo tam. Prazen seznam = kolo je zanju prosto. */
export function zasedenostV(
  s: VnosSrecanja,
  kolo: number,
  vnosi: VnosSrecanja[],
  dovoljeno: number,
): Zasedenost[] {
  return [s.domaci, s.gost].flatMap((ekipa) => {
    const druga = vnosi.filter((d) => d.id !== s.id && d.kolo === kolo && igra(d, ekipa))
    return druga.length >= dovoljeno ? [{ ekipa, druga }] : []
  })
}

/* Srečanja, katerih ekipa v svojem kolu igra več srečanj, kot jih ima običajno
   (po id-ju srečanja). Gre za stanje po vnosu in ne za napako: neuradne
   menjave termina to tudi povzročijo. */
export function podvojitve(vnosi: VnosSrecanja[], dovoljeno: number): Map<number, Zasedenost[]> {
  const poKolih = new Map<number, VnosSrecanja[]>()
  for (const s of vnosi) {
    if (s.kolo === null) continue
    const v = poKolih.get(s.kolo)
    if (v) v.push(s)
    else poKolih.set(s.kolo, [s])
  }
  const rezultat = new Map<number, Zasedenost[]>()
  for (const vKolu of poKolih.values()) {
    for (const s of vKolu) {
      const zasedene = [s.domaci, s.gost].flatMap((ekipa) => {
        const vse = vKolu.filter((d) => igra(d, ekipa))
        return vse.length > dovoljeno ? [{ ekipa, druga: vse.filter((d) => d.id !== s.id) }] : []
      })
      if (zasedene.length > 0) rezultat.set(s.id, zasedene)
    }
  }
  return rezultat
}

/* Kola, v katerih ekipa nima nobenega srečanja (prosto kolo pri lihem številu
   ekip ali ker je njeno srečanje prestavljeno drugam). */
export function prostaKola(vnosi: VnosSrecanja[], kola: VnosKola[], ekipa: string): number[] {
  return kola
    .filter((k) => !vnosi.some((s) => s.kolo === k.kolo && igra(s, ekipa)))
    .map((k) => k.kolo)
}
