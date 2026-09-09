/* Tipizirane funkcije za vsako koncno tocko zaledja - ena skupina na vir.
   Strani nikoli ne klicejo fetch neposredno, ampak samo te funkcije. */

import { api } from './odjemalec'
import type {
  DogodekDto,
  DogodekVnos,
  DomovLigaDto,
  DvobojDto,
  EkipaDto,
  EkipaVnos,
  IgralecDto,
  IgralecPodrobenDto,
  IgralecVnos,
  KaderIgralecDto,
  KaderVnos,
  KlubDto,
  KlubVnos,
  KoledarVnosDto,
  KrajDto,
  KrajVnos,
  LestvicaDvojiceDto,
  LestvicaEkipeDto,
  LestvicaIgralcaDto,
  LestvicaIgralcaLigeDto,
  LigaDto,
  LigaVnos,
  MrezaDto,
  NakljucniParDto,
  PostavaVnos,
  PrehodiVnos,
  PrijavaDto,
  ProfilDto,
  ProfilZasebnoDto,
  RacunIgralcaDto,
  RegistracijaVnos,
  SpremembaGeslaVnos,
  SrecanjeDto,
  SrecanjePodrobnoDto,
  StatistikaTekmovanjaDto,
  TekmaDto,
  TekmaSrecanjaDto,
  TerminiVnos,
  TurnirDto,
  TurnirVnos,
  UporabnikDto,
  VnosRezultata,
  VnosRezultataSrecanja,
  ZadnjaTekmaDto,
} from './tipi'

export const turnirjiApi = {
  seznam: () => api.vrni<TurnirDto[]>('/turnirji'),
  najdi: (id: number) => api.vrni<TurnirDto>(`/turnirji/${id}`),
  dogodki: (id: number) => api.vrni<DogodekDto[]>(`/turnirji/${id}/dogodki`),
  ustvari: (vnos: TurnirVnos) => api.objavi<TurnirDto>('/turnirji', vnos),
  dodajDogodek: (id: number, vnos: DogodekVnos) =>
    api.objavi<DogodekDto>(`/turnirji/${id}/dogodki`, vnos),
  zakljuci: (id: number) => api.objavi<TurnirDto>(`/turnirji/${id}/zakljuci`),
  /* Zavihek »Zanimivosti« — čez vse dogodke turnirja skupaj. */
  statistika: (id: number) =>
    api.vrni<StatistikaTekmovanjaDto>(`/turnirji/${id}/statistika`),
}

export const dogodkiApi = {
  /* Celotna slika dogodka: podatki, prijave in mreza tekem. */
  mreza: (id: number) => api.vrni<MrezaDto>(`/dogodki/${id}`),
  prijaviIgralce: (id: number, idjiIgralcev: number[]) =>
    api.objavi<PrijavaDto[]>(`/dogodki/${id}/prijave`, { idjiIgralcev }),
  odjavi: (idPrijave: number) =>
    api.objavi<PrijavaDto>(`/dogodki/prijave/${idPrijave}/odjava`),
  /* Ročno urejen jakostni vrstni red (format TOP); vsebovati mora
     vse prijavljene natanko enkrat. */
  shraniVrstniRed: (id: number, idjiPrijav: number[]) =>
    api.posodobi<PrijavaDto[]>(`/dogodki/${id}/vrstni-red`, { idjiPrijav }),
  /* Odstop med tekmovanjem: odigrane tekme obveljajo,
     preostale dobijo nasprotniki. */
  odstop: (idPrijave: number) =>
    api.objavi<PrijavaDto>(`/dogodki/prijave/${idPrijave}/odstop`),
  /* Dvojice: iz dveh prijav sestavi par (vrne nastali par — druga prijava
     izgine, njen igralec je odslej soigralec te). */
  poveziVPar: (id: number, idPrijave1: number, idPrijave2: number) =>
    api.objavi<PrijavaDto>(`/dogodki/${id}/pari`, { idPrijave1, idPrijave2 }),
  /* Dvojice: par nazaj v dve samostojni prijavi. */
  razdruziPar: (idPrijave: number) =>
    api.objavi<PrijavaDto[]>(`/dogodki/pari/${idPrijave}/razdruzi`),
  izvediZreb: (id: number) => api.objavi<TekmaDto[]>(`/dogodki/${id}/zreb`),
}

export const tekmeApi = {
  vnesiRezultat: (id: number, vnos: VnosRezultata) =>
    api.objavi<TekmaDto>(`/tekme/${id}/rezultat`, vnos),
}

export const igralciApi = {
  seznam: () => api.vrni<IgralecDto[]>('/igralci'),
  /* Šifrant z osebnimi podatki; strežnik ga da samo administratorju, zato
     tega ne kliči, dokler ne veš, da je prijavljeni admin (sicer 401). */
  seznamPodrobno: () => api.vrni<IgralecPodrobenDto[]>('/igralci/podrobno'),
  ustvari: (vnos: IgralecVnos) => api.objavi<IgralecDto>('/igralci', vnos),
  posodobi: (id: number, vnos: IgralecVnos) =>
    api.posodobi<IgralecDto>(`/igralci/${id}`, vnos),
  /* Brisanje je v resnici arhiviranje - zgodovina tekem ostane. */
  arhiviraj: (id: number) => api.izbrisi(`/igralci/${id}`),
  /* Postavitveni (začetni) klubski ELO za novinca; dovoljen le pred prvo
     odigrano tekmo. */
  nastaviZacetniRating: (id: number, vrednost: number) =>
    api.objavi<IgralecDto>(`/igralci/${id}/zacetni-rating`, { vrednost }),
}

export const klubiApi = {
  seznam: () => api.vrni<KlubDto[]>('/klubi'),
  ustvari: (vnos: KlubVnos) => api.objavi<KlubDto>('/klubi', vnos),
  posodobi: (id: number, vnos: KlubVnos) => api.posodobi<KlubDto>(`/klubi/${id}`, vnos),
  izbrisi: (id: number) => api.izbrisi(`/klubi/${id}`),
}

export const krajiApi = {
  seznam: () => api.vrni<KrajDto[]>('/kraji'),
  ustvari: (vnos: KrajVnos) => api.objavi<KrajDto>('/kraji', vnos),
  posodobi: (postnaSt: number, vnos: KrajVnos) =>
    api.posodobi<KrajDto>(`/kraji/${postnaSt}`, vnos),
  izbrisi: (postnaSt: number) => api.izbrisi(`/kraji/${postnaSt}`),
}

export const authApi = {
  /* Preveri poverilnice in vrne profil prijavljenega uporabnika
     (zahteva veljavno glavo Authorization; sicer strežnik vrne 401). */
  jaz: () => api.vrni<UporabnikDto>('/auth/me'),
  /* Edina mutacija brez prijave; račun nastane v stanju CAKA. */
  registracija: (vnos: RegistracijaVnos) =>
    api.objavi<UporabnikDto>('/auth/registracija', vnos),
  zamenjajGeslo: (vnos: SpremembaGeslaVnos) => api.objavi<void>('/auth/geslo', vnos),
}

export const profiliApi = {
  profil: (idIgralec: number) => api.vrni<ProfilDto>(`/igralci/${idIgralec}/profil`),
  zasebno: (idIgralec: number) =>
    api.vrni<ProfilZasebnoDto>(`/igralci/${idIgralec}/profil/zasebno`),
}

export const racuniApi = {
  seznam: () => api.vrni<RacunIgralcaDto[]>('/racuni'),
  potrdi: (id: number, idIgralec: number) =>
    api.objavi<RacunIgralcaDto>(`/racuni/${id}/potrdi`, { idIgralec }),
  /* Potrditev organizatorja z (neobveznim) klubom. */
  potrdiOrganizatorja: (id: number, idKlub: number | null) =>
    api.objavi<RacunIgralcaDto>(`/racuni/${id}/potrdi-organizatorja`, { idKlub }),
  zavrni: (id: number) => api.objavi<RacunIgralcaDto>(`/racuni/${id}/zavrni`),
  nastaviAktiven: (id: number, vrednost: boolean) =>
    api.objavi<RacunIgralcaDto>(`/racuni/${id}/aktiven?vrednost=${vrednost}`),
  ponastaviGeslo: (id: number) =>
    api.objavi<{ geslo: string }>(`/racuni/${id}/ponastavi-geslo`),
  /* Admin nastavi geslo po svoji izbiri; strežnik shrani le zgostitev. */
  nastaviGeslo: (id: number, geslo: string) =>
    api.objavi<void>(`/racuni/${id}/geslo`, { geslo }),
  /* Sprosti e-pošto za novo registracijo; igralca in tekem ne briše. */
  izbrisi: (id: number) => api.izbrisi(`/racuni/${id}`),
}

export const ligeApi = {
  seznam: () => api.vrni<LigaDto[]>('/lige'),
  najdi: (id: number) => api.vrni<LigaDto>(`/lige/${id}`),
  ustvari: (vnos: LigaVnos) => api.objavi<LigaDto>('/lige', vnos),
  uredi: (id: number, vnos: LigaVnos) => api.posodobi<LigaDto>(`/lige/${id}`, vnos),
  /* Mesto v piramidi je ločeno od pravil - ureja se tudi med sezono. Poleg te
     lige lahko spremeni tudi nižje (povezavo nosijo one), zato po klicu
     osveži cel seznam lig, ne le te ene. */
  prehodi: (id: number, vnos: PrehodiVnos) =>
    api.posodobi<LigaDto>(`/lige/${id}/prehodi`, vnos),
  /* Termini kol - prav tako ločeni od pravil (kolo se prestavi tudi sredi
     sezone). Odgovor so vsa srečanja lige, ker se je spremenil razpored. */
  termini: (id: number, vnos: TerminiVnos) =>
    api.posodobi<SrecanjeDto[]>(`/lige/${id}/termini`, vnos),
  /* Izbor lig za domačo stran (največ dve). Preklop in ne polje obrazca —
     pravila se ob žrebu zaklenejo, ligo na domači strani pa je treba zamenjati
     prav takrat, ko teče. Sme samo admin; strežnik zavrne tretjo ligo. */
  naDomaco: (id: number) => api.posodobi<LigaDto>(`/lige/${id}/na-domaci`, undefined),
  zDomace: (id: number) => api.izbrisi(`/lige/${id}/na-domaci`),
  izbrisi: (id: number) => api.izbrisi(`/lige/${id}`),

  ekipe: (id: number) => api.vrni<EkipaDto[]>(`/lige/${id}/ekipe`),
  dodajEkipo: (id: number, vnos: EkipaVnos) => api.objavi<EkipaDto>(`/lige/${id}/ekipe`, vnos),
  odstraniEkipo: (idEkipa: number) => api.izbrisi(`/lige/ekipe/${idEkipa}`),
  /* Jakostni vrstni red ekip (enakomerna razvrstitev) — kot celota, ker se
     mesta preštevilčijo vsem. Odgovor so vse ekipe lige v novem vrstnem redu. */
  vrstniRedEkip: (id: number, idjiEkip: number[]) =>
    api.posodobi<EkipaDto[]>(`/lige/${id}/vrstni-red`, { idjiEkip }),

  kader: (idEkipa: number) => api.vrni<KaderIgralecDto[]>(`/lige/ekipe/${idEkipa}/kader`),
  dodajVKader: (idEkipa: number, vnos: KaderVnos) =>
    api.objavi<KaderIgralecDto>(`/lige/ekipe/${idEkipa}/kader`, vnos),
  odstraniIzKadra: (idKader: number) => api.izbrisi(`/lige/kader/${idKader}`),

  generirajRazpored: (id: number) => api.objavi<SrecanjeDto[]>(`/lige/${id}/razpored`),
  srecanja: (id: number) => api.vrni<SrecanjeDto[]>(`/lige/${id}/srecanja`),
  lestvica: (id: number) => api.vrni<LestvicaEkipeDto[]>(`/lige/${id}/lestvica`),
  /* Lestvici posameznikov in dvojic te lige. Ločeni poti, ker ju stran naloži
     šele, ko gledalec sklop odpre. */
  lestvicaIgralcev: (id: number) =>
    api.vrni<LestvicaIgralcaLigeDto[]>(`/lige/${id}/lestvica-igralcev`),
  lestvicaDvojic: (id: number) =>
    api.vrni<LestvicaDvojiceDto[]>(`/lige/${id}/lestvica-dvojic`),
  /* Zavihek »Zanimivosti« — ista oblika kot pri turnirju, druge postavke. */
  statistika: (id: number) => api.vrni<StatistikaTekmovanjaDto>(`/lige/${id}/statistika`),
}

export const srecanjaApi = {
  podrobno: (id: number) => api.vrni<SrecanjePodrobnoDto>(`/srecanja/${id}`),
  nastaviPostavo: (id: number, vnos: PostavaVnos) =>
    api.posodobi<SrecanjePodrobnoDto>(`/srecanja/${id}/postava`, vnos),
  vnesiRezultat: (idTekma: number, vnos: VnosRezultataSrecanja) =>
    api.objavi<TekmaSrecanjaDto>(`/srecanja/tekme/${idTekma}/rezultat`, vnos),
}

/* Domača stran: povzetki lig in osebni izbor spremljanih lig. */
export const domovApi = {
  /* Povzetki lig za sklop »Lige«. Seznama sta LOČENA, ker nista enako tehtna:
     »idji« je izbor računa in prevlada, »ogledane« pa spomin gostovega
     brskalnika, ki obvelja šele, če admin domače strani ni uredil. Brez obojega
     strežnik vrne adminov izbor oz. lige v teku. */
  lige: (idji: number[], ogledane: number[] = []) => {
    const deli: string[] = []
    if (idji.length > 0) deli.push(`idji=${idji.join(',')}`)
    if (ogledane.length > 0) deli.push(`ogledane=${ogledane.join(',')}`)
    return api.vrni<DomovLigaDto[]>(
      deli.length > 0 ? `/domov/lige?${deli.join('&')}` : '/domov/lige',
    )
  },
  /* Izbor je last računa - gost dobi 401 in ga hrani brskalnik sam. */
  mojeLige: () => api.vrni<number[]>('/domov/moje-lige'),
  spremljaj: (idLiga: number) => api.posodobi<number[]>(`/domov/moje-lige/${idLiga}`, undefined),
  nehajSpremljati: (idLiga: number) => api.izbrisi(`/domov/moje-lige/${idLiga}`),
}

export const statistikaApi = {
  lestvica: () => api.vrni<LestvicaIgralcaDto[]>('/lestvica'),
  dvoboj: (prvi: number, drugi: number) =>
    api.vrni<DvobojDto>(`/dvoboj?prvi=${prvi}&drugi=${drugi}`),
  /* Naključni par, ki ima za sabo vsaj eno medsebojno tekmo — izbor je na
     strežniku, ker samo ta ve, kdo je s kom že igral. */
  nakljucniPar: () => api.vrni<NakljucniParDto>('/dvoboj/nakljucni'),
  zadnjeTekme: (koliko = 8) =>
    api.vrni<ZadnjaTekmaDto[]>(`/zadnje-tekme?koliko=${koliko}`),
}

/* Koledar: turnirji in kola lig v danem obdobju, urejeni po datumu.
   Obdobje je vključno na obeh straneh in sme obsegati največ leto dni. */
export const koledarApi = {
  obdobje: (od: string, doKdaj: string) =>
    api.vrni<KoledarVnosDto[]>(`/koledar?od=${od}&do=${doKdaj}`),
}
