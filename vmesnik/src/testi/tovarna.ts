/* Tovarna testnih podatkov: DTO-ji z razumnimi privzetki, da test napiše samo
   polja, o katerih govori. Polja, ki jih pravila v pomozno/ ne berejo, so
   izpuščena (zato pretvorba tipa na koncu) - če jih kakšna funkcija začne
   brati, jih dodaj sem in ne v vsak test posebej. */
import type {
  DogodekDto,
  KoledarVnosDto,
  MrezaDto,
  PrijavaDto,
  SrecanjeDto,
  TekmaDto,
  Udelezenec,
} from '../api/tipi'

export function prijava(id: number, polja: Partial<PrijavaDto> = {}): PrijavaDto {
  return {
    id,
    idIgralca: id,
    polnoIme: `Igralec ${id}`,
    ime: 'Igralec',
    priimek: String(id),
    klub: null,
    idIgralca2: null,
    polnoIme2: null,
    ime2: null,
    priimek2: null,
    klub2: null,
    status: 'PRIJAVLJEN',
    stNosilca: null,
    ratingObZrebu: null,
    ratingObZrebu2: null,
    koncnoMesto: null,
    rating: null,
    rating2: null,
    idSkupina: null,
    mestoVSkupini: null,
    idEkipa: null,
    kader: [],
    ...polja,
  } as PrijavaDto
}

function udelezenec(idPrijave: number): Udelezenec {
  return {
    idPrijave,
    polnoIme: `Igralec ${idPrijave}`,
    klub: null,
    polnoIme2: null,
    klub2: null,
    priimek: String(idPrijave),
    priimek2: null,
  } as Udelezenec
}

/* Tekma med prijavama `prva` in `druga` (null = mesto še ni zasedeno).
   Privzeto je končana z zmago prve. */
export function tekma(
  id: number,
  prva: number | null,
  druga: number | null,
  polja: Partial<TekmaDto> = {},
): TekmaDto {
  return {
    id,
    faza: 'GLAVNI',
    idSkupina: null,
    kolo: 1,
    pozicija: id,
    status: 'KONCANA',
    izidTip: 'IGRANO',
    steviloNizov: 5,
    udelezenec1: prva === null ? null : udelezenec(prva),
    udelezenec2: druga === null ? null : udelezenec(druga),
    dobljeniNizi1: 3,
    dobljeniNizi2: 0,
    nizi: [],
    idZmagovalcaPrijave: prva,
    miza: null,
    spremembaElo1: null,
    spremembaElo2: null,
    idZreb: null,
    listekNatisnjenOb: null,
    ...polja,
  } as TekmaDto
}

export function mreza(
  dogodek: Partial<DogodekDto>,
  prijave: PrijavaDto[],
  tekme: TekmaDto[] = [],
  polja: Partial<MrezaDto> = {},
): MrezaDto {
  return {
    dogodek: {
      id: 1,
      idTurnir: 1,
      ime: 'Člani',
      disciplina: 'POSAMICNO',
      sistemTekmovanja: 'IZLOCILNI',
      status: 'V_TEKU',
      dejanskiStatus: 'V_TEKU',
      ...dogodek,
    } as DogodekDto,
    prijave,
    tekme,
    skupine: [],
    lestvica: [],
    krogi: [],
    izbor: null,
    svRegija: null,
    ...polja,
  } as MrezaDto
}

export function srecanje(id: number, polja: Partial<SrecanjeDto> = {}): SrecanjeDto {
  return {
    id,
    idLiga: 1,
    kolo: 1,
    domaci: `Domači ${id}`,
    gost: `Gost ${id}`,
    dobljeneDomaci: 0,
    dobljeneGost: 0,
    status: 'RAZPORED',
    predvidenZacetek: null,
    brezBoja: false,
    prenesen: false,
    izvirnoKolo: null,
    cakaNaTermin: false,
    razlogPrestavitve: null,
    ...polja,
  } as SrecanjeDto
}

export function vnosKoledarja(
  ime: string,
  datum: string,
  datumKonca: string = datum,
  polja: Partial<KoledarVnosDto> = {},
): KoledarVnosDto {
  return {
    vrsta: 'TURNIR',
    id: 1,
    ime,
    datum,
    datumKonca,
    status: 'PRIPRAVA',
    srecanja: [],
    ...polja,
  } as KoledarVnosDto
}
