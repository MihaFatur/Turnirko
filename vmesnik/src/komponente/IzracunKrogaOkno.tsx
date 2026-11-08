/* Okno z izračunom kroga: kako so bili izenačeni razvrščeni. Odpre ga gumb pod
   lestvico (Lestvica), viden samo, kadar je v skupini krog. Samo za branje, zato
   ga dobi vsak gledalec.

   Okno pravil ne pozna: izpiše, kar je strežnik pri razvrščanju res upošteval
   (KrogDto). Zato je tabela ena sama in ima stolpec samo za merilo, ki je
   odločalo — kjer odločijo nizi, točk ni. Številke veljajo za tekme med
   izenačenimi, zato skupne razlike tu namenoma ni.

   Imena se ne sklanjajo (ime je podatek), zato so stavki zapisani tako, da
   imena stojijo samo v imenovalniku. */
import { useState } from 'react'

import type {
  ClanKroga,
  IzidTekme,
  KorakKroga,
  KrogDto,
  OdlocitevKroga,
  OpombaKroga,
  TekmaKroga,
} from '../api/tipi'
import { sklonZmag } from '../pomozno/oblikovanje'
import { ModalnoOkno } from './ModalnoOkno'
import { OZNAKA_POSEBNEGA_IZIDA } from './TekmaKartica'

interface Lastnosti {
  krogi: KrogDto[]
  ekipno: boolean
  onZapri: () => void
}

export function IzracunKrogaOkno({ krogi, ekipno, onZapri }: Lastnosti) {
  const samoEden = krogi.length === 1
  return (
    <ModalnoOkno
      naslov={samoEden ? naslovKroga(krogi[0]) : 'Izračun krogov'}
      nadnaslov={samoEden ? 'Izračun kroga' : undefined}
      onZapri={onZapri}
    >
      {krogi.map((krog) => (
        <Krog key={krog.odMesta} krog={krog} ekipno={ekipno} zNaslovom={!samoEden} />
      ))}
      {krogi.some((krog) => !krog.vseTekmeOdigrane) && (
        <p className="krog-izracun__opomba">
          Vse tekme še niso odigrane, zato se izračun lahko spremeni.
        </p>
      )}
      <div className="obrazec__gumbi">
        <button type="button" className="gumb" onClick={onZapri}>
          Zapri
        </button>
      </div>
    </ModalnoOkno>
  )
}

function Krog({ krog, ekipno, zNaslovom }: { krog: KrogDto; ekipno: boolean; zNaslovom: boolean }) {
  /* Krog dveh je ena sama tekma: tabela bi isto povedala dvakrat. */
  const samoNjunaTekma = krog.clani.length === 2 && krog.tekme.length === 1
  return (
    <section className="krog-izracun">
      {zNaslovom && <h3 className="krog-izracun__naslov">{naslovKroga(krog)}</h3>}
      <p className="krog-izracun__uvod">{uvodKroga(krog, ekipno)}</p>
      {samoNjunaTekma ? (
        <VrsticaTekme tekma={krog.tekme[0]} />
      ) : (
        <>
          <TabelaKroga krog={krog} ekipno={ekipno} />
          {krog.tockeManjkajo && !ekipno && krog.clani.some((clan) => clan.tocke !== null) && (
            <p className="krog-izracun__opomba">
              Točke niso vpisane pri vseh tekmah, zato štejejo samo vpisane.
            </p>
          )}
        </>
      )}
      {krog.opombe.map((opomba, indeks) => (
        <p key={indeks} className="krog-izracun__opomba">
          {besediloOpombe(opomba, krog, ekipno)}
        </p>
      ))}
      {!samoNjunaTekma && <TekmeKroga tekme={krog.tekme} ekipno={ekipno} />}
    </section>
  )
}

function TabelaKroga({ krog, ekipno }: { krog: KrogDto; ekipno: boolean }) {
  const imaZmage = krog.clani.some((clan) => clan.zmage !== null)
  const imaNize = krog.clani.some((clan) => clan.nizi !== null)
  const imaTocke = krog.clani.some((clan) => clan.tocke !== null)
  const medNjimi = krog.clani.flatMap((clan) => clan.medNjimi)
  return (
    <table className="tabela krog-izracun__tabela">
      <caption className="samo-za-bralnik">
        Izračun kroga: mesto, {ekipno ? 'ekipa' : 'igralec'} in številke iz tekem med izenačenimi
      </caption>
      <thead>
        <tr>
          <th scope="col" className="krog-izracun__mesto">
            <span className="samo-za-bralnik">Mesto</span>
          </th>
          <th scope="col">
            <span className="samo-za-bralnik">{ekipno ? 'Ekipa' : 'Igralec'}</span>
          </th>
          {imaZmage && <th scope="col" className="lestvica__stevilka">Zmage</th>}
          {imaNize && (
            <th
              scope="col"
              className="lestvica__stevilka"
              title={ekipno ? 'Dobljene : izgubljene posamične tekme' : 'Dobljeni : izgubljeni nizi'}
            >
              {ekipno ? 'Tekme' : 'Nizi'}
            </th>
          )}
          {imaTocke && (
            <th scope="col" className="lestvica__stevilka" title="Dobljene : izgubljene točke">
              Točke
            </th>
          )}
          {(imaNize || imaTocke) && (
            <th scope="col" className="lestvica__stevilka" title="Razlika">
              ±
            </th>
          )}
          {medNjimi.length > 0 && (
            <th scope="col" className="lestvica__stevilka" title="Samo tekme med tistimi, ki so ostali izenačeni">
              {medNjimi.every((korak) => korak.merilo === 'TEKMA') ? 'Med njima' : 'Med njimi'}
            </th>
          )}
        </tr>
      </thead>
      <tbody>
        {krog.clani.map((clan) => (
          <tr key={clan.idPrijave}>
            <td className="krog-izracun__mesto">{clan.mesto}.</td>
            <td className="krog-izracun__ime">{clan.ime}</td>
            {imaZmage && <td className="lestvica__stevilka">{clan.zmage}</td>}
            {imaNize && <td className="lestvica__stevilka">{izkupicek(clan.nizi)}</td>}
            {imaTocke && <td className="lestvica__stevilka">{izkupicek(clan.tocke)}</td>}
            {(imaNize || imaTocke) && (
              <td className="lestvica__stevilka krog-izracun__razlika">{razlikaClana(clan)}</td>
            )}
            {medNjimi.length > 0 && (
              <td className="lestvica__stevilka">
                {clan.medNjimi.map((korak) => opisKoraka(korak, ekipno)).join(', ')}
              </td>
            )}
          </tr>
        ))}
      </tbody>
    </table>
  )
}

/* Tekme kroga so dokaz in ne del izračuna: kdor želi preveriti, si jih odpre.
   Pri krogu petih je to deset vrstic, ki jih večina gledalcev ne potrebuje. */
function TekmeKroga({ tekme, ekipno }: { tekme: TekmaKroga[]; ekipno: boolean }) {
  const [odprto, nastaviOdprto] = useState(false)
  const beseda = ekipno ? 'srečanja' : 'tekme'
  return (
    <div>
      <button
        type="button"
        className="povezava-gumb krog-izracun__preklop"
        aria-expanded={odprto}
        onClick={() => nastaviOdprto(!odprto)}
      >
        {odprto ? `Skrij ${beseda}` : `Pokaži ${beseda}`}
      </button>
      {odprto && (
        <ul className="krog-izracun__tekme">
          {tekme.map((tekma, indeks) => (
            <li key={indeks}>
              <VrsticaTekme tekma={tekma} />
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}

function VrsticaTekme({ tekma }: { tekma: TekmaKroga }) {
  return (
    <div className="krog-izracun__tekma">
      <span>
        <span className={tekma.prviZmagal ? 'krog-izracun__zmagovalec' : undefined}>{tekma.ime1}</span>
        {' – '}
        <span className={tekma.prviZmagal ? undefined : 'krog-izracun__zmagovalec'}>{tekma.ime2}</span>
      </span>
      <span className="krog-izracun__izid">{izidTekme(tekma.nizi1, tekma.nizi2, tekma.izid)}</span>
    </div>
  )
}

/* Mesta se ne smejo prelomiti (na telefonu je bilo »Krog za 2.–« / »4. mesto«),
   zato povezovalnik besed za pomišljajem in nedeljiv presledek pred »mesto«. */
function naslovKroga(krog: KrogDto): string {
  return krog.clani.length === 2
    ? `Krog za ${krog.odMesta}. in ${krog.doMesta}. mesto`
    : `Krog za ${krog.odMesta}.–⁠${krog.doMesta}. mesto`
}

/* En stavek nad tabelo: kaj je odločilo in zakaj prav to. */
function uvodKroga(krog: KrogDto, ekipno: boolean): string {
  if (krog.clani.length === 2 && krog.tekme.length === 1) {
    return ekipno ? 'Odloči njuno medsebojno srečanje.' : 'Odloči njuna medsebojna tekma.'
  }
  const vsi = ekipno ? 'Vse ekipe imajo' : 'Vsi imajo'
  const zmage = `${vsi} po ${krog.enakeZmage} ${zmagVTozilniku(krog.enakeZmage ?? 0)} med seboj`
  const izTekem = ekipno ? 'iz teh srečanj' : 'iz teh tekem'
  switch (krog.odlocilo) {
    case 'ZMAGE': {
      const seNaprej = krog.clani.some(
        (clan) => clan.nizi !== null || clan.tocke !== null || clan.medNjimi.length > 0,
      )
      const iz = ekipno ? 'iz srečanj med njimi' : 'iz tekem med njimi'
      return seNaprej
        ? `Najprej odločijo zmage ${iz}. Pri enakem številu zmag ${
            ekipno ? 'štejejo samo še srečanja' : 'štejejo samo še tekme'
          } med izenačenimi.`
        : `Odločijo zmage ${iz}.`
    }
    case 'NIZI': {
      const seNaprej = krog.clani.some((clan) => clan.medNjimi.length > 0)
      const stavek = `${zmage}, zato ${ekipno ? 'odločijo posamične tekme' : 'odločijo nizi'} ${izTekem}.`
      return seNaprej
        ? `${stavek} Pri enaki razliki ${
            ekipno ? 'štejejo samo še srečanja' : 'štejejo samo še tekme'
          } med izenačenimi.`
        : stavek
    }
    case 'TOCKE':
      return `${zmage} in enako razliko nizov, zato odločijo točke ${izTekem}.`
    default:
      if (ekipno) return `${zmage} in enako razliko posamičnih tekem.`
      return krog.tockeManjkajo
        ? `${zmage} in enako razliko nizov. Točke niso vpisane.`
        : `${zmage} ter enako razliko nizov in točk.`
  }
}

/* Izenačeni, ki jih medsebojne tekme niso razsodile. Opomba, ki pokriva cel
   krog, razlage ne potrebuje: to je stavek nad njo že povedal. */
function besediloOpombe(opomba: OpombaKroga, krog: KrogDto, ekipno: boolean): string {
  const celKrog = opomba.odlocitve[0]?.vrednosti.length === krog.clani.length
  const zacetek = opomba.brezTekem
    ? 'Še brez medsebojne tekme, zato odloči'
    : celKrog
      ? 'Odloči'
      : 'Kjer je vse enako, odloči'
  const merila = opomba.odlocitve.map((odlocitev) => opisOdlocitve(odlocitev, ekipno)).join(', nato ')
  return `${zacetek} ${merila}.`
}

function opisOdlocitve({ merilo, vrednosti }: OdlocitevKroga, ekipno: boolean): string {
  const seznam = vrednosti
    .map(({ ime, vrednost }) => {
      if (vrednost === null) return ime
      return `${ime} ${merilo === 'RAZLIKA_NIZOV' ? predznak(vrednost) : vrednost}`
    })
    .join(', ')
  switch (merilo) {
    case 'RAZLIKA_NIZOV':
      return `${ekipno ? 'razlika posamičnih tekem iz vseh srečanj' : 'razlika nizov iz vseh tekem'} (${seznam})`
    case 'DOBLJENI_NIZI':
      return `${ekipno ? 'število dobljenih posamičnih tekem' : 'število dobljenih nizov iz vseh tekem'} (${seznam})`
    case 'JAKOSTNO_MESTO':
      return `jakostno mesto z žreba (${seznam})`
    case 'ABECEDA':
      return `abecedni vrstni red (${seznam})`
  }
}

function opisKoraka(korak: KorakKroga, ekipno: boolean): string {
  switch (korak.merilo) {
    case 'TEKMA':
      return izidTekme(korak.za, korak.proti, korak.izid)
    case 'ZMAGE':
      return `${korak.za} ${sklonZmag(korak.za)}`
    case 'NIZI':
      return `${ekipno ? 'tekme' : 'nizi'} ${predznak(korak.za - korak.proti)}`
    case 'TOCKE':
      return `točke ${predznak(korak.za - korak.proti)}`
  }
}

/* Razlika pri zadnjem merilu, ki je za tega člana štelo. */
function razlikaClana(clan: ClanKroga): string {
  const zadnje = clan.tocke ?? clan.nizi
  return zadnje === null ? '' : predznak(zadnje.za - zadnje.proti)
}

function izkupicek(vrednost: { za: number; proti: number } | null): string {
  return vrednost === null ? '' : `${vrednost.za}:${vrednost.proti}`
}

/* Predana tekma ima delni rezultat in zmagovalec ima lahko manj nizov, zato brez
   oznake izgleda kot napaka. Brez boja nizov ni: oznaka je cel izid. */
function izidTekme(nizi1: number, nizi2: number, izid: IzidTekme | null): string {
  const oznaka = izid === null ? null : (OZNAKA_POSEBNEGA_IZIDA[izid] ?? null)
  if (oznaka === null) return `${nizi1}:${nizi2}`
  return nizi1 === 0 && nizi2 === 0 ? oznaka : `${nizi1}:${nizi2} ${oznaka}`
}

function predznak(razlika: number): string {
  if (razlika > 0) return `+${razlika}`
  return razlika < 0 ? `−${-razlika}` : '0'
}

/* »po 1 zmago, po 2 zmagi, po 3 zmage, po 5 zmag«: tožilnik se od imenovalnika
   (sklonZmag) loči samo pri ednini. */
function zmagVTozilniku(n: number): string {
  return n % 100 === 1 ? 'zmago' : sklonZmag(n)
}
