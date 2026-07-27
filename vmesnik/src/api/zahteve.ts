/* Tipizirane funkcije za vsako koncno tocko zaledja - ena skupina na vir.
   Strani nikoli ne klicejo fetch neposredno, ampak samo te funkcije. */

import { api } from './odjemalec'
import type {
  DogodekDto,
  DogodekVnos,
  DvobojDto,
  EkipaDto,
  EkipaVnos,
  IgralecDto,
  IgralecVnos,
  KaderIgralecDto,
  KaderVnos,
  KlubDto,
  KlubVnos,
  KrajDto,
  KrajVnos,
  LestvicaEkipeDto,
  LestvicaIgralcaDto,
  LigaDto,
  LigaVnos,
  MrezaDto,
  PostavaVnos,
  PrijavaDto,
  ProfilDto,
  ProfilZasebnoDto,
  RacunIgralcaDto,
  RegistracijaVnos,
  SpremembaGeslaVnos,
  SrecanjeDto,
  SrecanjePodrobnoDto,
  TekmaDto,
  TekmaSrecanjaDto,
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
  izvediZreb: (id: number) => api.objavi<TekmaDto[]>(`/dogodki/${id}/zreb`),
}

export const tekmeApi = {
  vnesiRezultat: (id: number, vnos: VnosRezultata) =>
    api.objavi<TekmaDto>(`/tekme/${id}/rezultat`, vnos),
}

export const igralciApi = {
  seznam: () => api.vrni<IgralecDto[]>('/igralci'),
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
  izbrisi: (id: number) => api.izbrisi(`/lige/${id}`),

  ekipe: (id: number) => api.vrni<EkipaDto[]>(`/lige/${id}/ekipe`),
  dodajEkipo: (id: number, vnos: EkipaVnos) => api.objavi<EkipaDto>(`/lige/${id}/ekipe`, vnos),
  odstraniEkipo: (idEkipa: number) => api.izbrisi(`/lige/ekipe/${idEkipa}`),

  kader: (idEkipa: number) => api.vrni<KaderIgralecDto[]>(`/lige/ekipe/${idEkipa}/kader`),
  dodajVKader: (idEkipa: number, vnos: KaderVnos) =>
    api.objavi<KaderIgralecDto>(`/lige/ekipe/${idEkipa}/kader`, vnos),
  odstraniIzKadra: (idKader: number) => api.izbrisi(`/lige/kader/${idKader}`),

  generirajRazpored: (id: number) => api.objavi<SrecanjeDto[]>(`/lige/${id}/razpored`),
  srecanja: (id: number) => api.vrni<SrecanjeDto[]>(`/lige/${id}/srecanja`),
  lestvica: (id: number) => api.vrni<LestvicaEkipeDto[]>(`/lige/${id}/lestvica`),
}

export const srecanjaApi = {
  podrobno: (id: number) => api.vrni<SrecanjePodrobnoDto>(`/srecanja/${id}`),
  nastaviPostavo: (id: number, vnos: PostavaVnos) =>
    api.posodobi<SrecanjePodrobnoDto>(`/srecanja/${id}/postava`, vnos),
  vnesiRezultat: (idTekma: number, vnos: VnosRezultataSrecanja) =>
    api.objavi<TekmaSrecanjaDto>(`/srecanja/tekme/${idTekma}/rezultat`, vnos),
}

export const statistikaApi = {
  lestvica: () => api.vrni<LestvicaIgralcaDto[]>('/lestvica'),
  dvoboj: (prvi: number, drugi: number) =>
    api.vrni<DvobojDto>(`/dvoboj?prvi=${prvi}&drugi=${drugi}`),
  zadnjeTekme: (koliko = 8) =>
    api.vrni<ZadnjaTekmaDto[]>(`/zadnje-tekme?koliko=${koliko}`),
}
