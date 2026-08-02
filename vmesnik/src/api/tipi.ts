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
export type SpolKategorija = 'MOSKI' | 'ZENSKE' | 'MESANO'
export type IgralnaRoka = 'LEVA' | 'DESNA'
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

export interface IgralecDto {
  id: number
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
  kraj: KrajDto | null
  klub: KlubDto | null
  /* Trenutni klubski ELO; null, ce igralec se ni odigral nobene tekme. */
  rating: number | null
  /* Število že odigranih ratinških tekem. 0 → mogoče je postaviti začetni
     rating; nizko število → rating je še provizoričen. */
  steviloTekem: number
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
}

export interface PrijavaDto {
  id: number
  idIgralca: number
  polnoIme: string
  klub: string | null
  status: StatusPrijave
  /* Mesto na jakostni lestvici dogodka (1 = najmočnejši). */
  stNosilca: number | null
  ratingObZrebu: number | null
  koncnoMesto: number | null
  /* Trenutni klubski ELO; null = igralec še nima obračunane tekme. */
  rating: number | null
  idSkupina: number | null
  mestoVSkupini: number | null
}

/* Udelezenec tekme (stran 1 ali 2); null pomeni, da se ni znan
   ali da je igralec v tem kolu prost. */
export interface Udelezenec {
  idPrijave: number
  polnoIme: string
  klub: string | null
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

/* Ena vrstica lestvice (krožni sistem ali skupina). */
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
  skupine: SkupinaPredogledDto[]
  /* Zakaj žreb (še) ni mogoč; null pomeni, da je vse pripravljeno. */
  zadrzek: string | null
}

/* Celotna slika dogodka - odgovor GET /dogodki/{id}.
   skupine so zapolnjene pri obeh skupinskih sistemih, lestvica pri krožnem,
   izbor pa samo pri sistemu SKUPINE. Pri tem sistemu so prijave urejene po
   jakostnem vrstnem redu, sicer po priimku. */
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

/* Vrstica globalne lestvice igralcev (po klubskem ELO). */
export interface LestvicaIgralcaDto {
  idIgralca: number
  polnoIme: string
  klub: string | null
  rating: number | null
  odigrane: number
  zmage: number
  porazi: number
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

export const OZNAKE_SPOL_KATEGORIJA: Record<SpolKategorija, string> = {
  MOSKI: 'Moški',
  ZENSKE: 'Ženske',
  MESANO: 'Mešano',
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
  SKUPINE: 'Skupine (TOP)',
}

/* Koliko dobljenih nizov je potrebnih za zmago (npr. na 5 nizov -> 3). */
export function nizovZaZmago(steviloNizov: number): number {
  return Math.floor(steviloNizov / 2) + 1
}

/* ---------- Ligaska (ekipna) tekmovanja ---------- */

export type FormatSrecanja = 'SNTL' | 'CORBILLON'
export type StranEkipe = 'DOMACI' | 'GOST'
export type TipTekmeSrecanja = 'DVOJICE' | 'POSAMICNA'
export type StatusSrecanja = 'RAZPORED' | 'POTEKA' | 'KONCANO'
export type StatusTekmeSrecanja = 'CAKA' | 'KONCANA' | 'NEODIGRANA'

export const OZNAKE_FORMAT: Record<FormatSrecanja, string> = {
  SNTL: 'SNTL (3 igralci + dvojice)',
  CORBILLON: 'Corbillon (2 igralca + dvojice)',
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
  predlogaListka: PredlogaLige
  idVisjaLiga: number | null
  visjaLigaIme: string | null
  stNapreduje: number
  stIzpade: number
  status: StatusTekmovanja
  steviloEkip: number
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
  predlogaListka: PredlogaLige
  idVisjaLiga: number | null
  stNapreduje: number
  stIzpade: number
}

export interface EkipaDto {
  id: number
  idKlub: number
  klub: string
  zaporedna: number
  ime: string | null
  prikazanoIme: string
}

export interface EkipaVnos {
  idKlub: number
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
}

export interface KaderVnos {
  idIgralec: number
  vrstniRed: number | null
}

export interface LestvicaEkipeDto {
  mesto: number
  idEkipa: number
  ekipa: string
  klub: string
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
}

/* ---------- Profil igralca ---------- */

/* Javni del profila: izhaja iz že javnih rezultatov. */
export interface ProfilDto {
  glava: ProfilGlava
  pregled: ProfilPregled
  uvrstitev: ProfilUvrstitev
  graf: TockaGrafa[]
  tekme: TekmaProfila[]
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
  kdaj: string
  vrednost: number
  sprememba: number
  idTekme: number | null
  ligaska: boolean
  nasprotnik: string | null
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

/* Zasebni del profila: vidi ga samo igralec sam (in administrator). */
export interface ProfilZasebnoDto {
  nasprotniki: ProfilNasprotniki
  niziInTocke: ProfilNiziInTocke
  forma: ProfilForma
  poTekmovanjih: ProfilPoTekmovanjih
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
  tocke: ProfilTocke
}

/* Samo turnirske tekme — ligaška srečanja hranijo le nize. */
export interface ProfilTocke {
  steviloTekem: number
  tockeZa: number
  tockeProti: number
  odstotekTock: number
  povprecjeNaNiz: number
  najvecTockVNizu: number
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
