/* Tipi, ki zrcalijo DTO-je zaledja - en tip ustreza natanko enemu
   zapisu (record) v paketu si.turnirko.dto. Ce se API spremeni, se mora
   spremeniti tudi ta datoteka, sicer se neskladje pokaze sele med izvajanjem.

   Datumi so nizi oblike "YYYY-MM-DD", kot jih vraca in sprejema API. */

/* ---------- Enumi (vrednosti se morajo ujemati z Java enumi) ---------- */

export type StatusTekmovanja = 'PRIPRAVA' | 'V_TEKU' | 'ZAKLJUCEN'
export type StatusTekme = 'CAKA' | 'PRIPRAVLJENA' | 'V_IGRI' | 'KONCANA'
export type IzidTekme = 'IGRANO' | 'PROSTO' | 'BREZ_BOJA' | 'PREDAJA' | 'DISKVALIFIKACIJA'
export type FazaTekme = 'SKUPINA' | 'GLAVNI' | 'TOLAZILNI'
export type Spol = 'MOSKI' | 'ZENSKI'
/* MESANO pri dogodku pomeni STROGO mešan par (moški + ženska) in je zato
   mogoč samo pri dvojicah; odprt dogodek je KDORKOLI. Pri ligi ima MESANO
   svoj, starejši pomen — liga, v kateri igrajo oboji — in KDORKOLI tam ni
   izbira (obrazec lige našteje svoje tri možnosti). */
export type SpolKategorija = 'MOSKI' | 'ZENSKE' | 'MESANO' | 'KDORKOLI'
export type IgralnaRoka = 'LEVA' | 'DESNA'
/* Tekmovalni starostni pas igralca (11. člen PST), izpeljan iz letnice —
   ne shranjen. Pas je NAJOŽJI, ki mu igralec ustreza: kdor je U11, je tudi
   U13, a tu nastopa enkrat. Zato filter »U15« pomeni izbiro U11 + U13 + U15.
   Ni isto kot KategorijaIgralca na lestvici: ta nosi spol in teče po
   koledarskem letu, pas pa je brez spola in teče po sezoni. */
export type StarostniPas =
  | 'U11'
  | 'U13'
  | 'U15'
  | 'U17'
  | 'U19'
  | 'U21'
  | 'CLANI'
  | 'VETERANI'
export type Disciplina = 'POSAMICNO' | 'DVOJICE'
export type SistemTekmovanja = 'IZLOCILNI' | 'SKUPINE_IZLOCILNI' | 'KROZNI' | 'SKUPINE'
export type StatusPrijave =
  | 'PRIJAVLJEN'
  | 'ODJAVLJEN'
  | 'DISKVALIFICIRAN'
  | 'REZERVA'
  | 'ODSTOPIL'
export type Vloga = 'ADMIN' | 'ORGANIZATOR' | 'IGRALEC'

/* ---------- Izpisni DTO-ji ---------- */

export interface KrajDto {
  postnaSt: number
  ime: string
}

export interface KlubDto {
  id: number
  ime: string
  kratica: string | null
}

/* Igralec, kot ga vrne javna koncna tocka /igralci (zrcali IgralecJavniDto).
   Osebnih podatkov namenoma ni - te nosi IgralecPodrobenDto. */
export interface IgralecDto {
  id: number
  ime: string
  priimek: string
  spol: Spol
  igralnaRoka: IgralnaRoka | null
  /* Starostni pas, kot ga izpelje strežnik iz letnice; null, kadar je ta ne
     pozna. Datuma rojstva vmesnik nima in ga tudi ne sme imeti. */
  starostniPas: StarostniPas | null
  klub: KlubDto | null
  /* Trenutni klubski ELO; null, ce igralec se ni odigral nobene tekme. */
  rating: number | null
  /* Število že odigranih ratinških tekem. 0 → mogoče je postaviti začetni
     rating; nizko število → rating je še provizoričen. */
  steviloTekem: number
}

/* Šifrant z osebnimi podatki (/igralci/podrobno). Strežnik ga vrne samo
   administratorju; organizator dobi javni izpis, zato mora vsak pogled, ki
   ta polja riše, biti pripravljen tudi na to, da jih ni. */
export interface IgralecPodrobenDto extends IgralecDto {
  datumRojstva: string
  email: string | null
  telefonskaSt: string | null
  ntzsLicenca: string | null
  drzavljanstvo: string | null
  naslov: string | null
  kraj: KrajDto | null
}

export interface TurnirDto {
  id: number
  ime: string
  kraj: KrajDto | null
  dvorana: string | null
  datumZacetka: string | null
  datumKonca: string | null
  status: StatusTekmovanja
  opombe: string | null
  /* Ali tekme turnirja stejejo v klubski ELO. */
  stejeVElo: boolean
  /* Lastnistvo: racun, ki je turnir ustvaril, in klub lastnik. Po njiju
     vmesnik pokaze urejanje le lastniku (streznik je zadnja obramba). */
  idLastnik: number | null
  idKlubLastnik: number | null
  klubLastnik: string | null
  /* Stevci cez dogodke turnirja: pas "Danes v dvorani" in kolofon ju bereta
     brez dodatne poizvedbe. */
  steviloDogodkov: number
  dogodkovVTeku: number
  dogodkovVPripravi: number
  prijavljenihSkupaj: number
  odigranihTekem: number
  vsehTekem: number
  /* Besedno stanje turnirja za vrstico na domači strani. faza je zapolnjena
     samo pri turnirju v teku ("skupine", "četrtfinale", "3. kolo"),
     zmagovalec samo pri zaključenem; zadnjiIzid je "Vrhovnik 3:1 Kramar". */
  faza: string | null
  zmagovalec: string | null
  zadnjiIzid: string | null
}

export interface DogodekDto {
  id: number
  idTurnir: number
  ime: string
  disciplina: Disciplina
  spolKategorija: SpolKategorija
  starostnaKategorija: string | null
  sistemTekmovanja: SistemTekmovanja
  privzetoSteviloNizov: number
  /* Nastavitvi skupinskega dela; prazni pri drugih sistemih. */
  steviloSkupin: number | null
  velikostSkupine: number | null
  prijavnina: number | null
  rokPrijave: string | null
  status: StatusTekmovanja
  /* Stevci vrstice dogodka: palica napredka je edino, kar loci dogodek
     z 12 igralci od dogodka s 100. */
  steviloPrijav: number
  odigranihTekem: number
  vsehTekem: number
}

export interface PrijavaDto {
  id: number
  idIgralca: number
  polnoIme: string
  klub: string | null
  /* Drugi igralec para (samo dvojice). Prazen pri posamični prijavi in pri
     prijavljenem igralcu dvojic, ki soigralca še nima — tak v žreb ne gre. */
  idIgralca2: number | null
  polnoIme2: string | null
  klub2: string | null
  status: StatusPrijave
  /* Mesto na jakostni lestvici dogodka (1 = najmočnejši). */
  stNosilca: number | null
  ratingObZrebu: number | null
  ratingObZrebu2: number | null
  koncnoMesto: number | null
  /* Trenutni klubski ELO; null = igralec še nima obračunane tekme. */
  rating: number | null
  rating2: number | null
  idSkupina: number | null
  mestoVSkupini: number | null
}

/* Udelezenec tekme (stran 1 ali 2); null pomeni, da se ni znan
   ali da je igralec v tem kolu prost.

   Pri dvojicah je udeleženec PAR: polnoIme2/klub2 nosita drugega igralca.
   Imeni prideta ločeni, da ju kartica mreže izpiše v dveh vrsticah; kjer je
   dovolj ena vrstica, ju zlepi imeUdelezenca(). */
export interface Udelezenec {
  idPrijave: number
  polnoIme: string
  klub: string | null
  polnoIme2: string | null
  klub2: string | null
}

/* Ime udeleženca v eni vrstici: »Novak Ana« oz. »Novak Ana / Zajc Eva«. */
export function imeUdelezenca(udelezenec: Udelezenec | null | undefined): string | null {
  if (!udelezenec) return null
  return udelezenec.polnoIme2
    ? `${udelezenec.polnoIme} / ${udelezenec.polnoIme2}`
    : udelezenec.polnoIme
}

/* Isto za vrstico prijave (v pripravi in med udeleženci). */
export function imePrijave(prijava: { polnoIme: string; polnoIme2: string | null }): string {
  return prijava.polnoIme2 ? `${prijava.polnoIme} / ${prijava.polnoIme2}` : prijava.polnoIme
}

export interface TekmaDto {
  id: number
  faza: FazaTekme
  idSkupina: number | null
  kolo: number
  pozicija: number
  status: StatusTekme
  izidTip: IzidTekme | null
  steviloNizov: number
  udelezenec1: Udelezenec | null
  udelezenec2: Udelezenec | null
  dobljeniNizi1: number
  dobljeniNizi2: number
  idZmagovalcaPrijave: number | null
  miza: number | null
  /* Sprememba klubskega ELO ob tej tekmi (npr. +16 / -16); null, dokler
     tekma ni obračunana (prosti prehod, nedokončana). */
  spremembaElo1: number | null
  spremembaElo2: number | null
  /* Rating igralca pred tekmo (zgodovinski pri odigrani, sicer trenutni);
     null, če igralec še nima ratinga. */
  ratingPred1: number | null
  ratingPred2: number | null
}

/* Ena vrstica lestvice (krožni sistem ali skupina).
   niziZa/niziProti sta izkupiček v CELI skupini in ob izenačenju namenoma ne
   pojasnita vrstnega reda — o njem odloči le izkupiček med izenačenimi.
   `krog` je zaporedna številka takega izenačenja (člani istega kroga imajo
   isto), sicer null; prikaz z njo označi mesta, kjer skupna razlika nizov
   vrstnemu redu ne sledi. */
export interface VrsticaLestviceDto {
  idPrijave: number
  idIgralca: number
  polnoIme: string
  klub: string | null
  odigrane: number
  zmage: number
  porazi: number
  niziZa: number
  niziProti: number
  mesto: number | null
  krog: number | null
}

/* Skupina z lestvico (sistem skupine + izločilni). */
export interface SkupinaDto {
  id: number
  oznaka: string
  lestvica: VrsticaLestviceDto[]
}

/* Ena skupina v predogledu razreza: katera mesta jakostne lestvice zajame. */
export interface SkupinaPredogledDto {
  oznaka: string
  velikost: number
  odMesta: number
  doMesta: number
}

/* Črta reza in predogled skupin pri sistemu SKUPINE (format TOP).
   Razrez izračuna strežnik, da ga vmesnik ne podvaja. */
export interface IzborDto {
  meja: number
  prijavljenih: number
  igra: number
  /* Predogled razreza — samo pri formatu TOP, kjer je razporeditev zaporedna
     in torej vnaprej znana. Pri žrebanih skupinah je seznam prazen. */
  skupine: SkupinaPredogledDto[]
  /* Zakaj žreb (še) ni mogoč; null pomeni, da je vse pripravljeno. */
  zadrzek: string | null
  /* Ali vrstni red odloča tudi o IZBORU (format TOP): pod črto so rezerve.
     Drugod igrajo vsi in vrstni red določa le nosilce. */
  crtaReza: boolean
  /* Koliko skupin bo sestavil žreb; null pri čisti izločilni mreži. */
  steviloSkupin: number | null
}

/* Celotna slika dogodka - odgovor GET /dogodki/{id}.
   skupine so zapolnjene pri obeh skupinskih sistemih, lestvica pri krožnem,
   izbor pa povsod, kjer žreb pozna nosilce (vse razen krožnega in dvojic).
   Tam so prijave urejene po jakostnem vrstnem redu, sicer po priimku. */
export interface MrezaDto {
  dogodek: DogodekDto
  prijave: PrijavaDto[]
  tekme: TekmaDto[]
  skupine: SkupinaDto[]
  lestvica: VrsticaLestviceDto[]
  izbor: IzborDto | null
}

/* Prijavljeni uporabnik (administrator ali igralec); gost nima zapisa.
   Pri igralcu je uporabniško ime njegova e-pošta. */
export interface UporabnikDto {
  /* Id računa; vmesnik ga primerja z idLastnik turnirja/lige. */
  id: number
  uporabniskoIme: string
  vloga: Vloga
  status: StatusRacuna
  /* Zapis igralca, s katerim je račun povezan (šele po potrditvi). */
  idIgralec: number | null
  imeIgralca: string | null
  /* Klub organizatorja (po njem soupravlja klubska tekmovanja); sicer null. */
  idKlub: number | null
  klub: string | null
}

export type StatusRacuna = 'CAKA' | 'POTRJEN' | 'ZAVRNJEN'

export const OZNAKE_STATUSA_RACUNA: Record<StatusRacuna, string> = {
  CAKA: 'Čaka na potrditev',
  POTRJEN: 'Potrjen',
  ZAVRNJEN: 'Zavrnjen',
}

export const OZNAKE_VLOGA: Record<Vloga, string> = {
  ADMIN: 'Administrator',
  ORGANIZATOR: 'Organizator',
  IGRALEC: 'Igralec',
}

export interface RegistracijaVnos {
  ime: string
  priimek: string
  idKlub: number | null
  email: string
  geslo: string
  /* true = registracija organizatorja; sicer (false/undefined) igralec. */
  organizator?: boolean
}

export interface SpremembaGeslaVnos {
  staro: string
  novo: string
}

/* Administrator nastavi geslo tujemu računu (brez starega gesla). */
export interface NastavitevGeslaVnos {
  geslo: string
}

/* Račun osebe (igralca ali organizatorja) v administratorjevem pregledu. */
export interface RacunIgralcaDto {
  id: number
  email: string
  /* Loči račun igralca od organizatorja (potrjevanje je različno). */
  vloga: Vloga
  prijavljenoIme: string | null
  prijavljeniPriimek: string | null
  klubZelja: string | null
  /* Potrjen klub organizatorja (pri igralcu null). */
  idKlub: number | null
  klub: string | null
  status: StatusRacuna
  aktiven: boolean
  idIgralec: number | null
  imeIgralca: string | null
  ustvarjenOb: string
  predlogi: PredlogIgralcaDto[]
}

/* Administratorjeva potrditev organizatorja: (neobvezni) klub. */
export interface PotrditevOrganizatorjaVnos {
  idKlub: number | null
}

export interface PredlogIgralcaDto {
  idIgralec: number
  polnoIme: string
  klub: string | null
  zeImaRacun: boolean
}

/* Starostno-spolna kategorija igralca; zaledje jo izpelje iz spola in letnice
   rojstva (glej KategorijaIgralca.java), zato je ni v šifrantu. */
export type KategorijaIgralca = 'CLANI' | 'CLANICE' | 'U19' | 'VETERANI'

/* Vrstica globalne lestvice igralcev (po klubskem ELO). */
export interface LestvicaIgralcaDto {
  idIgralca: number
  /* Igralec se povsod bere kot "Jan Petrič" (polnoIme); ločena ime in priimek
     sta tu zato, ker lestvica zna teči abecedno po priimku. */
  ime: string
  priimek: string
  polnoIme: string
  klub: string | null
  idKluba: number | null
  rating: number | null
  odigrane: number
  zmage: number
  porazi: number
  /* Koliko mest je igralec pridobil (+) ali izgubil (−) v zadnjem mesecu;
     null, če ga pred mesecem na lestvici še ni bilo. */
  premik: number | null
  /* Razlika klubskega ELO proti stanju pred 30 dnevi; null z istim razlogom
     kot premik. */
  spremembaRatinga: number | null
  /* Spol in izpeljana kategorija za filtra nad lestvico; oba sta lahko null.
     Spol je zraven, ker ga kategorija nosi samo pri članih (pri U19 in
     veteranih se izgubi) — brez njega filtra »vse igralke« ni mogoče
     sestaviti. */
  spol: Spol | null
  kategorija: KategorijaIgralca | null
  /* Do sedem točk klubskega ELO čez zadnjih 12 mesecev (najstarejša prva). */
  eloZgodovina: number[]
  /* Lige, v katerih je igralec v kadru katere od ekip (filter "Moje lige"). */
  idjiLig: number[]
}

/* Povzetek ene lige za sklop "Moje lige" na domači strani. */
export interface DomovLigaDto {
  id: number
  ime: string
  sezona: string | null
  status: StatusTekmovanja
  odigranihKol: number
  vsehKol: number
  vrh: VrhLigeDto[]
  naslednje: NaslednjeKoloDto | null
}

export interface VrhLigeDto {
  mesto: number
  ekipa: string
  odigrane: number
  tocke: number
}

export interface NaslednjeKoloDto {
  kolo: number
  datum: string | null
  domaci: string
  gost: string
}

/* Naključni par za semafor »1 na 1«. Strežnik jamči, da sta igralca med sabo
   že odigrala vsaj eno tekmo (izjema je baza brez odigranih tekem). */
export interface NakljucniParDto {
  prvi: number
  drugi: number
}

/* Pregled "1 na 1" med dvema igralcema (izidi z vidika prvega). */
export interface DvobojDto {
  prvi: DvobojIgralec
  drugi: DvobojIgralec
  odigrane: number
  zmagePrvega: number
  zmageDrugega: number
  niziPrvega: number
  niziDrugega: number
  tekme: DvobojTekma[]
}

export interface DvobojIgralec {
  id: number
  /* Izpis je povsod "Nejc Vrhovnik"; ločena ime in priimek sta tu zato, ker
     izbirnik pod semaforjem teče abecedno po priimku. */
  ime: string
  priimek: string
  polnoIme: string
  klub: string | null
  rating: number | null
}

export interface DvobojTekma {
  idTekme: number
  /* Ligaška tekma (tekma_srecanja) ali turnirska (tekma) — id-ji prihajajo iz
     različnih tabel, zato je za enolični ključ potrebno oboje. */
  ligaska: boolean
  /* Ime turnirja oz. lige. */
  tekmovanje: string
  /* Ime dogodka oz. »N. kolo · Domači – Gostje«. */
  del: string
  /* Začetek turnirja oz. dan odigranega srečanja; null, kadar datuma ni. */
  datum: string | null
  niziPrvega: number
  niziDrugega: number
  zmagalPrvi: boolean
  izidTip: IzidTekme | null
  /* Sprememba klubskega ELO z vidika vsakega igralca (null, če ni obračunana). */
  spremembaPrvega: number | null
  spremembaDrugega: number | null
}

/* Zadnja odigrana tekma (za "Zadnji rezultati" na domači strani). */
export interface ZadnjaTekmaDto {
  idTekme: number
  turnir: string
  dogodek: string
  igralec1: string
  klub1: string | null
  igralec2: string
  klub2: string | null
  nizi1: number
  nizi2: number
  zmagalPrvi: boolean
  izidTip: IzidTekme | null
  spremembaElo1: number | null
  spremembaElo2: number | null
}

/* ---------- Vnosni DTO-ji ---------- */

export interface TurnirVnos {
  ime: string
  postnaSt: number | null
  dvorana: string | null
  datumZacetka: string | null
  datumKonca: string | null
  opombe: string | null
  /* null = privzeto (tekme stejejo v ELO). */
  stejeVElo: boolean | null
}

export interface DogodekVnos {
  ime: string
  spolKategorija: SpolKategorija
  starostnaKategorija: string | null
  privzetoSteviloNizov: number
  prijavnina: number | null
  rokPrijave: string | null
  /* DVOJICE zahtevajo izločilni sistem (takojšnje izpadanje). */
  disciplina: Disciplina
  sistemTekmovanja: SistemTekmovanja
  /* Obvezni pri sistemu SKUPINE, sicer se ne upoštevata. */
  steviloSkupin: number | null
  velikostSkupine: number | null
}

export interface IgralecVnos {
  ime: string
  priimek: string
  spol: Spol
  datumRojstva: string
  email: string | null
  telefonskaSt: string | null
  igralnaRoka: IgralnaRoka | null
  ntzsLicenca: string | null
  drzavljanstvo: string | null
  naslov: string | null
  postnaSt: number | null
  idKlub: number | null
}

export interface KlubVnos {
  ime: string
  kratica: string | null
}

export interface KrajVnos {
  postnaSt: number
  ime: string
}

/* Tocke enega niza, npr. 11:7. */
export interface NizVnos {
  tocke1: number
  tocke2: number
}

/* Vnos rezultata tekme. izidTip null pomeni normalno odigrano tekmo;
   za posebne izide je obvezen zmagovalecStran (1 ali 2). */
export interface VnosRezultata {
  izidTip: IzidTekme | null
  dobljeniNizi1: number | null
  dobljeniNizi2: number | null
  zmagovalecStran: 1 | 2 | null
  nizi: NizVnos[] | null
}

/* ---------- Oznake za prikaz (tu so sumniki dovoljeni) ---------- */

export const OZNAKE_STATUS_TEKMOVANJA: Record<StatusTekmovanja, string> = {
  PRIPRAVA: 'Priprava',
  V_TEKU: 'V teku',
  ZAKLJUCEN: 'Zaključen',
}

export const OZNAKE_SPOL: Record<Spol, string> = {
  MOSKI: 'Moški',
  ZENSKI: 'Ženski',
}

/* Pasovi so izključujoči, zato napis pove RAZPON let in ne le številke:
   »U15« bere organizator kot »do 15«, v seznamu pa so v tem pasu samo 13- in
   14-letniki (mlajši so v svojem). Starost je ta iz PST — na 31. december
   leta, v katerem se sezona začne. */
export const OZNAKE_STAROSTNI_PAS: Record<StarostniPas, string> = {
  U11: 'U11 (do 10 let)',
  U13: 'U13 (11–12)',
  U15: 'U15 (13–14)',
  U17: 'U17 (15–16)',
  U19: 'U19 (17–18)',
  U21: 'U21 (19–20)',
  CLANI: 'Člani (21–39)',
  VETERANI: 'Veterani (40+)',
}

/* Kratka oznaka za vrstico seznama, kjer pas stoji med klubom in ratingom:
   za razpon let tam ni prostora, »CLANI« pa je koda in ne beseda. */
export const OZNAKE_PASU_KRATKO: Record<StarostniPas, string> = {
  U11: 'U11',
  U13: 'U13',
  U15: 'U15',
  U17: 'U17',
  U19: 'U19',
  U21: 'U21',
  CLANI: 'člani',
  VETERANI: 'veterani',
}

/* Vrstni red pasov od najmlajšega navzgor - v oknu filtrov po pogostosti
   nimajo smisla (U13 nad U11 bi bilo branje po številu, ne po starosti). */
export const VRSTNI_RED_STAROSTNIH_PASOV: StarostniPas[] = [
  'U11',
  'U13',
  'U15',
  'U17',
  'U19',
  'U21',
  'CLANI',
  'VETERANI',
]

export const OZNAKE_DISCIPLINA: Record<Disciplina, string> = {
  POSAMICNO: 'Posamično',
  DVOJICE: 'Dvojice',
}

export const OZNAKE_SPOL_KATEGORIJA: Record<SpolKategorija, string> = {
  MOSKI: 'Moški',
  ZENSKE: 'Ženske',
  MESANO: 'Mešano',
  KDORKOLI: 'Kdorkoli',
}

/* Katere kategorije sme dogodek te discipline imeti. »Mešano« je pravilo o
   sestavi PARA, zato pri posamičnem tekmovanju ni izbira — isto pravilo
   varuje CHECK v shemi in TurnirjiStoritev. */
export function kategorijeZaDisciplino(disciplina: Disciplina): SpolKategorija[] {
  return disciplina === 'DVOJICE'
    ? ['MOSKI', 'ZENSKE', 'MESANO', 'KDORKOLI']
    : ['MOSKI', 'ZENSKE', 'KDORKOLI']
}

export const OZNAKE_IGRALNA_ROKA: Record<IgralnaRoka, string> = {
  LEVA: 'Leva',
  DESNA: 'Desna',
}

export const OZNAKE_IZID: Record<IzidTekme, string> = {
  IGRANO: 'Odigrano',
  PROSTO: 'Prosto',
  BREZ_BOJA: 'Brez boja (w.o.)',
  PREDAJA: 'Predaja',
  DISKVALIFIKACIJA: 'Diskvalifikacija',
}

export const OZNAKE_STATUS_PRIJAVE: Record<StatusPrijave, string> = {
  PRIJAVLJEN: 'Prijavljen',
  ODJAVLJEN: 'Odjavljen',
  DISKVALIFICIRAN: 'Diskvalificiran',
  REZERVA: 'Rezerva',
  ODSTOPIL: 'Odstopil',
}

export const OZNAKE_SISTEM: Record<SistemTekmovanja, string> = {
  IZLOCILNI: 'Izločilni (single-elimination)',
  KROZNI: 'Krožni (vsak z vsakim)',
  SKUPINE_IZLOCILNI: 'Skupine + izločilni',
  SKUPINE: 'Skupine po jakosti (TOP)',
}

/* Kratke oznake sistemov za značke. */
export const OZNAKE_SISTEM_KRATKO: Record<SistemTekmovanja, string> = {
  IZLOCILNI: 'Izločilni',
  KROZNI: 'Krožni',
  SKUPINE_IZLOCILNI: 'Skupine + izločilni',
  SKUPINE: 'Skupine po jakosti',
}

/* Še krajše oznake za mono vrstico telefona, kjer sistem deli 350 px s
   številom prijav in odigranostjo ("Skup + izl · 8 skupin po 4 · 42/61"). */
export const OZNAKE_SISTEM_MOBI: Record<SistemTekmovanja, string> = {
  IZLOCILNI: 'Izločilni',
  KROZNI: 'Krožni',
  SKUPINE_IZLOCILNI: 'Skup + izl',
  SKUPINE: 'Skupine',
}

/* Koliko dobljenih nizov je potrebnih za zmago (npr. na 5 nizov -> 3). */
export function nizovZaZmago(steviloNizov: number): number {
  return Math.floor(steviloNizov / 2) + 1
}

/* ---------- Ligaska (ekipna) tekmovanja ---------- */

export type FormatSrecanja =
  | 'SNTL' | 'SNTL_PRVA' | 'SNTL_DVOJICE_SEDMA' | 'SNTL_PRVA_DVOJICE_CETRTA'
  | 'SNTL_BREZ_DVOJIC' | 'OLIMPIJSKI' | 'CORBILLON' | 'SAVINJA'
export type StranEkipe = 'DOMACI' | 'GOST'
export type TipTekmeSrecanja = 'DVOJICE' | 'POSAMICNA'
export type StatusSrecanja = 'RAZPORED' | 'POTEKA' | 'KONCANO'
export type StatusTekmeSrecanja = 'CAKA' | 'KONCANA' | 'NEODIGRANA'

export const OZNAKE_FORMAT: Record<FormatSrecanja, string> = {
  SNTL: 'SNTL (3 igralci + dvojice)',
  SNTL_PRVA: 'SNTL 1. liga (dvojice + 6)',
  SNTL_DVOJICE_SEDMA: 'SNTL, dvojice sedma tekma',
  SNTL_PRVA_DVOJICE_CETRTA: 'SNTL 1. liga, dvojice četrta tekma',
  SNTL_BREZ_DVOJIC: 'SNTL brez dvojic (9 posamičnih)',
  OLIMPIJSKI: 'Olimpijski (3 igralci, 5 posamičnih)',
  CORBILLON: 'Corbillon (2 igralca + dvojice)',
  SAVINJA: 'Savinja liga (2 igralca, dvojice prve)',
}

/* Vrstni red tekem — kot ga vrne FormatSrecanja.razpored() na strežniku.
   Obrazec ga izpiše in po njem omeji prag zmag, da ne ponudi nemogoče meje. */
export const RAZPORED_FORMATA: Record<FormatSrecanja, string[]> = {
  SNTL: ['dvojice', 'A-X', 'B-Y', 'C-Z', 'B-X', 'A-Z', 'C-Y', 'B-Z', 'C-X', 'A-Y'],
  SNTL_PRVA: ['dvojice', 'B-X', 'A-Z', 'C-Y', 'B-Z', 'C-X', 'A-Y'],
  SNTL_DVOJICE_SEDMA:
    ['A-X', 'B-Y', 'C-Z', 'B-X', 'A-Z', 'C-Y', 'dvojice', 'B-Z', 'C-X', 'A-Y'],
  SNTL_PRVA_DVOJICE_CETRTA: ['B-X', 'A-Z', 'C-Y', 'dvojice', 'B-Z', 'C-X', 'A-Y'],
  SNTL_BREZ_DVOJIC: ['A-X', 'B-Y', 'C-Z', 'B-X', 'A-Z', 'C-Y', 'B-Z', 'C-X', 'A-Y'],
  OLIMPIJSKI: ['A-X', 'B-Y', 'C-Z', 'A-Y', 'B-X'],
  CORBILLON: ['A-X', 'B-Y', 'dvojice', 'A-Y', 'B-X'],
  SAVINJA: ['dvojice', 'A-X', 'B-Y', 'A-Y', 'B-X'],
}

/* Predloga uradnega ekipnega zapisnika (NTZS) za natis listkov lige. */
export type PredlogaLige = 'SNTL_1' | 'SNTL_23'

export const OZNAKE_PREDLOGA_LIGE: Record<PredlogaLige, string> = {
  SNTL_1: 'Ekipni zapisnik – 1. SNTL',
  SNTL_23: 'Ekipni zapisnik – 2./3. SNTL',
}

export interface LigaDto {
  id: number
  ime: string
  sezona: string | null
  spolKategorija: SpolKategorija
  formatSrecanja: FormatSrecanja
  steviloNizov: number
  zmagZaSrecanje: number | null
  dvokrozno: boolean
  tockeZmaga: number
  tockeNeodloceno: number
  tockePoraz: number
  dovoljenoNeodloceno: boolean
  prepovedDvojneRegistracije: boolean
  stejeVElo: boolean
  /* Žreb po parih: ekipe imajo jakostni vrstni red (EkipaDto.stNosilca) in
     razpored jih zveže v pare — zgornja polovica s spodnjo. */
  enakomernaRazvrstitev: boolean
  predlogaListka: PredlogaLige
  /* Seme terminov: kdaj se igra prvo kolo (ISO datum-čas; ura velja za celo
     kolo, 00:00 = ura ni določena) in na koliko dni sledijo naslednja. Iz njiju
     zaledje ob žrebu izračuna predvidene začetke srečanj. */
  zacetekPrvegaKola: string | null
  razmikDni: number | null
  idVisjaLiga: number | null
  visjaLigaIme: string | null
  stNapreduje: number
  stIzpade: number
  status: StatusTekmovanja
  steviloEkip: number
  /* Napredek lige: koliko kol ima razpored in koliko jih je odigranih (kolo je
     odigrano, ko je končano vsako njegovo srečanje). Vrstica lige na telefonu
     iz tega izpiše »7. od 18 kol« in palico; brez razporeda sta oba 0. */
  odigranihKol: number
  steviloKol: number
  /* Ali liga stoji v sklopu »Lige« na domači strani (največ dve, izbere
     admin). Javno polje: po njem vmesnik ligo označi v izboru. */
  naDomaci: boolean
  /* Lastnistvo (glej TurnirDto). */
  idLastnik: number | null
  idKlubLastnik: number | null
  klubLastnik: string | null
}

export interface LigaVnos {
  ime: string
  sezona: string | null
  spolKategorija: SpolKategorija
  formatSrecanja: FormatSrecanja
  steviloNizov: number
  zmagZaSrecanje: number | null
  dvokrozno: boolean
  tockeZmaga: number
  tockeNeodloceno: number
  tockePoraz: number
  dovoljenoNeodloceno: boolean
  prepovedDvojneRegistracije: boolean
  stejeVElo: boolean
  enakomernaRazvrstitev: boolean
  predlogaListka: PredlogaLige
  /* Termini se vpišejo že ob ustvarjanju lige, ko ekip (in s tem števila kol)
     še ni — zato seme in ne seznam datumov. null = terminov ni. */
  zacetekPrvegaKola: string | null
  razmikDni: number | null
}

/* Ročni termini kol. Ločeno od LigaVnos iz istega razloga kot PrehodiVnos:
   pravila se po žrebu zaklenejo, kolo pa se sme prestaviti tudi sredi sezone.
   Termin je last kola — vsa srečanja kola dobijo isti začetek; kolo, ki ga
   seznam ne našteje, ostane nedotaknjeno, kolo z zacetek = null termin izgubi. */
export interface TerminiVnos {
  kola: { kolo: number; zacetek: string | null }[]
}

/* Mesto lige v piramidi. Ločeno od LigaVnos, ker so pravila po generiranju
   razporeda zaklenjena, povezave med ligami pa ostanejo popravljive.
   idNizjeLige = null pomeni »ne dotikaj se nižjih lig«. */
export interface PrehodiVnos {
  idVisjaLiga: number | null
  idNizjeLige: number[] | null
  stNapreduje: number
  stIzpade: number
}

/* Klub je prazen pri »prosti« ekipi — zasedbi, ki v registru klubov nima
   zapisa in nastopa samo v tej ligi. */
export interface EkipaDto {
  id: number
  idKlub: number | null
  klub: string | null
  zaporedna: number
  ime: string | null
  prikazanoIme: string
  /* Mesto na jakostni lestvici lige (1 = najmočnejša). Pove kaj samo pri ligi
     z enakomerno razvrstitvijo, sicer je zgolj vrstni red vpisa. */
  stNosilca: number | null
  steviloKadra: number
}

/* Jakostni vrstni red ekip lige (enakomerna razvrstitev). Ločeno od EkipaVnos,
   ker se vrstni red ne ureja po eni ekipi, ampak kot celota — seznam mora
   našteti VSE ekipe lige natanko enkrat. */
export interface VrstniRedEkipVnos {
  idjiEkip: number[]
}

/* idKlub = null pomeni prosto ekipo; takrat je ime obvezno. */
export interface EkipaVnos {
  idKlub: number | null
  zaporedna: number | null
  ime: string | null
}

export interface KaderIgralecDto {
  id: number
  idIgralec: number
  polnoIme: string
  klub: string | null
  vrstniRed: number | null
  rating: number | null
  /* Bilanca posamičnih tekem igralca v tej ligi (dvojice ne štejejo). */
  zmage: number
  porazi: number
}

export interface KaderVnos {
  idIgralec: number
  vrstniRed: number | null
}

export interface LestvicaEkipeDto {
  mesto: number
  idEkipa: number
  ekipa: string
  /* Prazen pri prosti ekipi (brez kluba). */
  klub: string | null
  odigrane: number
  zmage: number
  neodlocene: number
  porazi: number
  dobljeneTekme: number
  prejeteTekme: number
  razlikaTekme: number
  dobljeniNizi: number
  prejetiNizi: number
  razlikaNizi: number
  tocke: number
  cona: 'NAPREDUJE' | 'IZPADE' | null
}

/* Vrstica lestvice posameznikov ZNOTRAJ lige. Ratinga ni: ta teče čez vsa
   tekmovanja, tu pa štejejo samo posamične tekme te lige. Merilo so zmage,
   ob izenačenju uspešnost in razlika nizov (glej LestvicaLigeStoritev). */
export interface LestvicaIgralcaLigeDto {
  mesto: number
  idIgralec: number
  polnoIme: string
  /* Ekipa, za katero je v tej ligi največkrat nastopil. */
  ekipa: string | null
  odigrane: number
  zmage: number
  porazi: number
  odstotek: number
  dobljeniNizi: number
  prejetiNizi: number
}

/* Vrstica lestvice dvojic v ligi. Par je ena tekmovalna enota - ista dva
   igralca sta ista dvojica ne glede na stran; imeni sta urejeni abecedno. */
export interface LestvicaDvojiceDto {
  mesto: number
  idPrvi: number
  prvi: string
  idDrugi: number
  drugi: string
  ekipa: string | null
  odigrane: number
  zmage: number
  porazi: number
  odstotek: number
  dobljeniNizi: number
  prejetiNizi: number
}

export interface SrecanjeDto {
  id: number
  /* Liga, ki ji srečanje pripada (za preverbo lastništva). */
  idLiga: number
  kolo: number
  idEkipaDomaci: number
  domaci: string
  idEkipaGost: number
  gost: string
  dobljeneDomaci: number
  dobljeneGost: number
  status: StatusSrecanja
  predvidenZacetek: string | null
}

export interface TekmaSrecanjaDto {
  id: number
  zaporedje: number
  tip: TipTekmeSrecanja
  oznaka: string
  domaci: string | null
  domaci2: string | null
  gost: string | null
  gost2: string | null
  steviloNizov: number
  dobljeniNiziDomaci: number
  dobljeniNiziGost: number
  zmagovalecStran: StranEkipe | null
  izidTip: IzidTekme | null
  status: StatusTekmeSrecanja
  spremembaEloDomaci: number | null
  spremembaEloGost: number | null
  /* Točke po nizih po vrsti; prazen seznam, kadar niso vpisane (vnos je
     neobvezen — enako kot pri turnirjih). tocke1 so domačih, tocke2 gostov. */
  nizi: NizVnos[]
}

export interface PostavaSrecanjaDto {
  stran: StranEkipe
  pozicija: string
  idIgralec: number
  polnoIme: string
  vDvojici: boolean
}

export interface SrecanjePodrobnoDto {
  srecanje: SrecanjeDto
  format: FormatSrecanja
  pozicijeDomaci: string[]
  pozicijeGost: string[]
  izbiraDvojice: boolean
  stVDvojici: number
  postave: PostavaSrecanjaDto[]
  tekme: TekmaSrecanjaDto[]
  kaderDomaci: KaderIgralecDto[]
  kaderGost: KaderIgralecDto[]
}

export interface MestoVnos {
  stran: StranEkipe
  pozicija: string
  idIgralec: number
  vDvojici: boolean
}

export interface PostavaVnos {
  mesta: MestoVnos[]
}

export interface VnosRezultataSrecanja {
  izidTip: IzidTekme | null
  dobljeniNiziDomaci: number | null
  dobljeniNiziGost: number | null
  zmagovalecStran: StranEkipe | null
  /* Točke po nizih (neobvezno); tocke1 so domačih, tocke2 gostov. */
  nizi: NizVnos[] | null
}

/* ---------- Profil igralca ---------- */

/* Javni del profila: izhaja iz že javnih rezultatov. */
export interface ProfilDto {
  glava: ProfilGlava
  pregled: ProfilPregled
  uvrstitev: ProfilUvrstitev
  graf: TockaGrafa[]
  tekme: TekmaProfila[]
  /* Tekme dvojic so ločen seznam in ne štejejo v »pregled« ne v ELO:
     izida para ni mogoče pripisati posamezniku. */
  dvojice: TekmaDvojic[]
}

export interface ProfilGlava {
  idIgralec: number
  polnoIme: string
  klub: string | null
  igralnaRoka: IgralnaRoka | null
  rating: number | null
}

export interface ProfilPregled {
  odigrane: number
  zmage: number
  porazi: number
  odstotekZmag: number
  dobljeniNizi: number
  prejetiNizi: number
  turnirskih: number
  ligaskih: number
}

export interface ProfilUvrstitev {
  mesto: number | null
  skupajIgralcev: number
  percentil: number | null
  klubskoPovprecje: number | null
}

export interface TockaGrafa {
  /* Trenutek obračuna ratinga — po njem so točke urejene. Datum tekme je
     "datum": pri uvoženi zgodovini so vsi obračuni nastali ob uvozu, zato se
     izpiše in po obdobju reže "datum", ne "kdaj". */
  kdaj: string
  /* Dan tekme (isti kot v vrstici seznama); prazen le pri postavitvenem
     ratingu, ki tekme nima. */
  datum: string | null
  vrednost: number
  sprememba: number
  idTekme: number | null
  ligaska: boolean
  nasprotnik: string | null
  /* Ime turnirja oz. lige in del (dogodek oz. kolo s parom ekip) — enak zapis
     kot v TekmaProfila. Prazna, kadar točka nima para v seznamu tekem. */
  tekmovanje: string | null
  del: string | null
}

export interface TekmaProfila {
  idTekme: number
  ligaska: boolean
  datum: string | null
  tekmovanje: string
  del: string
  idNasprotnika: number
  nasprotnik: string
  klubNasprotnika: string | null
  niziZa: number
  niziProti: number
  zmaga: boolean
  izidTip: IzidTekme | null
  spremembaElo: number | null
}

/* Ena odigrana tekma dvojic z vidika lastnika profila: s kom je igral
   (soigralec) in proti kateremu paru. Spremembe ELO ni. */
export interface TekmaDvojic {
  idTekme: number
  datum: string | null
  tekmovanje: string
  del: string
  idSoigralca: number | null
  soigralec: string | null
  nasprotnika: string
  niziZa: number
  niziProti: number
  zmaga: boolean
  izidTip: IzidTekme | null
}

/* Zasebni del profila: vidi ga samo igralec sam (in administrator). */
export interface ProfilZasebnoDto {
  nasprotniki: ProfilNasprotniki
  niziInTocke: ProfilNiziInTocke
  forma: ProfilForma
  poTekmovanjih: ProfilPoTekmovanjih
  razsevni: RazsevnaTocka[]
}

/* Ena tekma v razsevnem grafu: rating nasprotnika ob tekmi proti spremembi
   lastnega ratinga. */
export interface RazsevnaTocka {
  ratingNasprotnika: number
  sprememba: number
  zmaga: boolean
}

export interface Delez {
  oznaka: string
  odigrane: number
  zmage: number
  porazi: number
  odstotek: number
}

export interface ProfilNasprotnik {
  idIgralec: number
  polnoIme: string
  klub: string | null
  rating: number | null
  zmage: number
  porazi: number
}

export interface ProfilNasprotniki {
  protiDesnicarjem: Delez
  protiLevicarjem: Delez
  rokaNeznana: Delez
  protiMocnejsim: Delez
  protiPodobnim: Delez
  protiSibkejsim: Delez
  tekemZZnanimRatingom: number
  najboljsaZmaga: ProfilNasprotnik | null
  nemesis: ProfilNasprotnik | null
  najpogostejsi: ProfilNasprotnik | null
  poKlubih: Delez[]
}

export interface Razmerje {
  oznaka: string
  stevilo: number
  zmaga: boolean
}

export interface ProfilNiziInTocke {
  dobljeniNizi: number
  prejetiNizi: number
  razmerja: Razmerje[]
  odlocilniNiz: Delez
  /* »Zmage« so tu tekme brez izgubljenega niza, »porazi« vse ostale. */
  brezIzgubljenegaNiza: Delez
  tocke: ProfilTocke
}

/* Samo tekme (turnirske in ligaške) z vpisanimi točkami po nizih — vnos je
   povsod neobvezen. */
export interface ProfilTocke {
  steviloTekem: number
  tockeZa: number
  tockeProti: number
  odstotekTock: number
  povprecjeNaNiz: number
  najvecTockVNizu: number
  /* Tesni nizi (oba vsaj 9 točk) — približek izida 9:9 in več. */
  nizovPodPritiskom: number
  odstotekTockPodPritiskom: number
}

/* En koledarski mesec: dosežene zmage proti pričakovanim iz ELO nasprotnikov.
   »mesec« je oblike »2026-04«. */
export interface ProfilMesec {
  mesec: string
  zmage: number
  pricakovaneZmage: number
}

export interface ProfilForma {
  zadnjih10: boolean[]
  trenutniNiz: number
  trenutniNizZmag: boolean
  najdaljsiNizZmag: number
  najdaljsiNizPorazov: number
  spremembaElo30dni: number | null
  najvisjiElo: number | null
  najvisjiEloDatum: string | null
  poMesecih: ProfilMesec[]
}

export interface ProfilPoTekmovanjih {
  turnirji: Delez
  lige: Delez
  doma: Delez
  vGosteh: Delez
  poPoziciji: Delez[]
  poFazi: Delez[]
  dvojice: Delez
}

/* ---------- Koledar ---------- */

export type VrstaKoledarja = 'TURNIR' | 'LIGA'

/* Kdo igra v enem srečanju kola. */
export interface KoledarPar {
  idSrecanje: number
  domaci: string
  gost: string
}

/* En vnos koledarja: turnir ali ENO KOLO lige (in ne posamezno srečanje —
   termin je last kola, zato bi se ime lige v istem dnevu ponovilo petkrat).
   Turnir lahko traja več dni: takrat sta »datum« in »datumKonca« različna in
   koledar označi ves razpon. */
export interface KoledarVnosDto {
  vrsta: VrstaKoledarja
  /* Id turnirja oz. lige — iz njega vmesnik sestavi pot in barvo vnosa. */
  id: number
  ime: string
  datum: string
  datumKonca: string
  /* Termin kola z uro (samo liga); 00:00 pomeni, da ura ni določena. */
  zacetek: string | null
  kolo: number | null
  kraj: string | null
  dvorana: string | null
  sezona: string | null
  klub: string | null
  status: StatusTekmovanja
  srecanja: KoledarPar[]
}

/* ---------- Zavihek »Zanimivosti« turnirja oz. lige ---------- */

/* Igralec, kot ga izpiše vrstica zavihka. Klub je posnetek ob nastopu (pri
   turnirju klub ob prijavi), ne trenutni klub igralca. */
export interface StatOseba {
  idIgralec: number
  polnoIme: string
  klub: string | null
}

/* Pas kazalnikov na vrhu zavihka. »tock« je prazen, kadar točk po nizih ni
   vpisal nihče (vnos je povsod neobvezen); »dogodkov« nosi turnir, »ekip« liga. */
export interface StatStevilke {
  igralcev: number
  klubov: number
  tekem: number
  nizov: number
  tock: number | null
  dogodkov: number | null
  ekip: number | null
  tekemDvojic: number
}

export interface StatVzpon {
  oseba: StatOseba
  pridobil: number
  odigranih: number
  koncni: number
}

export interface StatPresenecenje {
  zmagovalec: StatOseba
  ratingZmagovalca: number
  porazenec: StatOseba
  ratingPorazenca: number
  razlika: number
  izid: string
  kontekst: string
}

export interface StatZid {
  oseba: StatOseba
  dobljeni: number
  prejeti: number
  odigrane: number
}

export interface StatDelavec {
  oseba: StatOseba
  odigrane: number
  zmage: number
  dvojic: number
}

export interface StatKlub {
  ime: string
  zmage: number
  odigrane: number
  igralcev: number
}

export interface StatObrat {
  zmagovalec: StatOseba
  porazenec: StatOseba
  izid: string
  nizi: string
  kontekst: string
  koliko: number
}

export interface StatNajdaljsiNiz {
  prvi: StatOseba
  drugi: StatOseba
  tockePrvi: number
  tockeDrugi: number
  zaporedna: number
  kontekst: string
}

export interface StatNajdaljsaTekma {
  zmagovalec: StatOseba
  porazenec: StatOseba
  izid: string
  tock: number
  nizov: number
  kontekst: string
}

export interface StatPrviNaslov {
  oseba: StatOseba
  dogodek: string
}

export interface StatNaNoz {
  idSrecanje: number
  kolo: number
  domaci: string
  gost: string
  dobljeneDomaci: number
  dobljeneGost: number
  odlocil: StatOseba | null
  koliko: number
}

export interface StatGostovanje {
  ekipa: string
  zmage: number
  srecanj: number
  odstotek: number
}

export interface StatNosilec {
  ekipa: string
  oseba: StatOseba
  zmage: number
  porazi: number
}

export interface StatDvojica {
  prvi: StatOseba
  drugi: StatOseba
  zmage: number
  porazi: number
}

/* Vsaka postavka je lahko prazna in se takrat NE izriše — uvožena zgodovina
   brez ratingov, liga brez vpisanih točk in turnir v prvi uri nimajo istih
   podatkov, izpis »ni podatka« pa je slabši od odsotnosti vrstice. */
export interface StatistikaTekmovanjaDto {
  dovoljPodatkov: boolean
  vTeku: boolean
  stejeVElo: boolean
  stevilke: StatStevilke | null
  vzponi: StatVzpon[]
  presenecenje: StatPresenecenje | null
  zid: StatZid[]
  delavci: StatDelavec[]
  klubi: StatKlub[]
  obrat: StatObrat | null
  najdaljsiNiz: StatNajdaljsiNiz | null
  najdaljsaTekma: StatNajdaljsaTekma | null
  prviNaslovi: StatPrviNaslov[]
  naNoz: StatNaNoz | null
  gostje: StatGostovanje[]
  nosilci: StatNosilec[]
  dvojica: StatDvojica | null
}
