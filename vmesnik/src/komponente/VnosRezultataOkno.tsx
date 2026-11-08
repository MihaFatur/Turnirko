/* Modalno okno za vnos rezultata tekme.

   Pravila (dokoncno jih preverja zaledje, tu jih le vodimo):
   - Odigrana tekma: nize vpisemo rocno, kot pri ligaski tekmi (zapisnik
     srecanja), ker se rezultat prepisuje s papirja. Zmagovalec mora dobiti
     natanko toliko nizov, kolikor jih je potrebnih za zmago (npr. 3 pri
     "najboljsi od 5"), porazenec manj; to preveri zaledje in napako vrne.
   - Tocke po nizih so neobvezne; ce so vnesene, jih mora biti natanko
     toliko, kot je odigranih nizov. Vnos in preverba sta v skupni komponenti
     TockeNizov - isti kot pri ligaski tekmi, ker so pravila niza ista.
   - Predaja: delni rezultat pred koncem tekme + zmagovalec.
   - Število nizov je privzeto tisto, ki ga je določil dogodek; pri vnosu ga lahko
     prepišeš za to tekmo (npr. finale na 3). Pri popravku prepisa ni.
   - Brez boja / diskvalifikacija: samo zmagovalec, nizi se pripisejo.

   POPRAVEK (lastnost popravek) je isti obrazec nad ze koncano tekmo: polja so
   napolnjena s shranjenim izidom, shrani pa se na drugo koncno tocko (glej
   TekmaStoritev.popraviRezultat). Isti obrazec zato, ker je popravek isti vpis -
   drugacen je samo njegov ucinek. Pred shranjevanjem strežnik pove, kaj bo popravek
   naredil: ob zamenjanem zmagovalcu ali spremenjenem vrstnem redu skupine okno
   preide na potrditev (PotrditevPopravka), v istem oknu, da Escape ne zapre dveh. */
import { useState, type FormEvent } from 'react'
import { useMutation } from '@tanstack/react-query'

import { tekmeApi } from '../api/zahteve'
import { imeUdelezenca, nizovZaZmago, priimekUdelezenca } from '../api/tipi'
import type {
  IzidTekme,
  NacinPopravka,
  NizVnos,
  PopravekPredogledDto,
  TekmaDto,
  VnosRezultata,
} from '../api/tipi'
import { ModalnoOkno } from './ModalnoOkno'
import { PotrditevPopravka } from './PotrditevPopravka'
import { SporociloNapake } from './SporociloNapake'
import { StevilskoPolje } from './StevilskoPolje'
import {
  TockeNizov,
  dopolniDrugi,
  fokusiraj,
  preveriNize,
  useSkliciNizov,
  vrsticeZaIzid,
  type VrsticaNiza,
} from './TockeNizov'

interface Lastnosti {
  tekma: TekmaDto
  onZapri: () => void
  /* Poklicano po uspesnem vnosu - stran osvezi podatke dogodka. */
  onShranjeno: () => void
  /* Ekipna tekma: izid nastane iz zapisnika srecanja, neposredno sta mogoca
     samo brez boja in diskvalifikacija (TekmaStoritev.vnesiRezultat). */
  samoBrezIgre?: boolean
  /* Popravek ze shranjenega rezultata (tekma je KONCANA). */
  popravek?: boolean
}

/* Izidi, ki jih rocno vnasa sodnik (PROSTO doloci sistem sam pri zrebu). */
const ROCNI_IZIDI: { vrednost: IzidTekme; oznaka: string }[] = [
  { vrednost: 'IGRANO', oznaka: 'Odigrana tekma' },
  { vrednost: 'PREDAJA', oznaka: 'Predaja med igro' },
  { vrednost: 'BREZ_BOJA', oznaka: 'Brez boja (nasprotnik ni nastopil)' },
  { vrednost: 'DISKVALIFIKACIJA', oznaka: 'Diskvalifikacija' },
]

export function VnosRezultataOkno({
  tekma,
  onZapri,
  onShranjeno,
  samoBrezIgre = false,
  popravek = false,
}: Lastnosti) {
  /* Prepis števila nizov za to tekmo (privzeto kar je določil dogodek). Pri popravku
     in ekipni tekmi ga ni: izid je že odigran oz. nastane iz srečanja. */
  const prepisMozen = !popravek && !samoBrezIgre
  const [steviloNizov, nastaviSteviloNizov] = useState(tekma.steviloNizov)
  /* Strežnik dobi število samo ob spremembi; nespremenjeno pusti, kar je v bazi. */
  const prepis = prepisMozen && steviloNizov !== tekma.steviloNizov ? steviloNizov : null
  const zaZmago = nizovZaZmago(steviloNizov)
  const ime1 = imeUdelezenca(tekma.udelezenec1) ?? 'Igralec 1'
  const ime2 = imeUdelezenca(tekma.udelezenec2) ?? 'Igralec 2'
  /* Priimka nad polji za nize in nad stolpci točk - prepis s papirja gre po imenu. */
  const priimek1 = priimekUdelezenca(tekma.udelezenec1) || 'Igralec 1'
  const priimek2 = priimekUdelezenca(tekma.udelezenec2) || 'Igralec 2'
  const izidi = samoBrezIgre
    ? ROCNI_IZIDI.filter((izid) => izid.vrednost === 'BREZ_BOJA' || izid.vrednost === 'DISKVALIFIKACIJA')
    : ROCNI_IZIDI

  /* Popravek izhaja iz shranjenega izida - organizator popravlja tisto, kar
     je vpisano, in ne izpolnjuje praznega obrazca znova. */
  const shranjenaStran: '' | '1' | '2' =
    tekma.idZmagovalcaPrijave === null
      ? ''
      : tekma.idZmagovalcaPrijave === tekma.udelezenec1?.idPrijave
        ? '1'
        : '2'

  const [izidTip, nastaviIzidTip] = useState<IzidTekme>(
    popravek ? (tekma.izidTip ?? 'IGRANO') : samoBrezIgre ? 'BREZ_BOJA' : 'IGRANO',
  )
  /* Dobljeni nizi vsakega igralca, vpisani rocno kot pri ligaski tekmi. Prazno
     pomeni, da še ni vpisano; pri popravku sta napolnjena s shranjenim izidom. */
  const [niziPrvi, nastaviNiziPrvi] = useState(
    popravek && tekma.izidTip === 'IGRANO' ? String(tekma.dobljeniNizi1) : '',
  )
  const [niziDrugi, nastaviNiziDrugi] = useState(
    popravek && tekma.izidTip === 'IGRANO' ? String(tekma.dobljeniNizi2) : '',
  )
  /* Točke po nizih so privzeto vklopljene - organizator jih prepisuje s papirja
     pri vsakem rezultatu, izklopi pa jih odznačenje. Pri popravku ostane stanje,
     kakršno je shranjeno. */
  const [vnasamTocke, nastaviVnasamTocke] = useState(popravek ? tekma.nizi.length > 0 : true)
  /* Pri popravku so nizi že vpisani, zato se vrstice ne morejo pojaviti ob
     njihovem vpisu — pripravimo jih takoj, sicer bi organizator, ki točke
     dodaja k tekmi brez njih, dobil prazno mrežo in nerazumljivo napako. */
  const [tockeNizov, nastaviTockeNizov] = useState<VrsticaNiza[]>(() =>
    popravek && tekma.izidTip === 'IGRANO'
      ? vrsticeZaIzid(
          tekma.nizi.map((niz) => ({ tocke1: String(niz.tocke1), tocke2: String(niz.tocke2) })),
          tekma.dobljeniNizi1 + tekma.dobljeniNizi2,
        )
      : [],
  )
  /* Za posebne izide: katera stran je zmagala (1 ali 2). */
  const [zmagovalecStran, nastaviZmagovalecStran] = useState<'' | '1' | '2'>(
    popravek ? shranjenaStran : '',
  )
  /* Delni rezultat ob predaji - tudi ta se vpisuje rocno. */
  const [predajaNizi1, nastaviPredajaNizi1] = useState(
    popravek && tekma.izidTip === 'PREDAJA' ? String(tekma.dobljeniNizi1) : '',
  )
  const [predajaNizi2, nastaviPredajaNizi2] = useState(
    popravek && tekma.izidTip === 'PREDAJA' ? String(tekma.dobljeniNizi2) : '',
  )
  const [napakaVnosa, nastaviNapakoVnosa] = useState<string | null>(null)

  /* Sklici za premik s puščicami in samodejni fokus na točke (glej useSkliciNizov). */
  const sklici = useSkliciNizov()

  /* Drugi korak popravka: kaj bo popravek naredil in organizatorjeva izbira. */
  const [potrditev, nastaviPotrditev] = useState<{
    vnos: VnosRezultata
    predogled: PopravekPredogledDto
  } | null>(null)
  const [nacin, nastaviNacin] = useState<NacinPopravka | null>(null)

  const shranjevanje = useMutation({
    mutationFn: ({ vnos, nacin: izbran }: { vnos: VnosRezultata; nacin: NacinPopravka | null }) =>
      popravek
        ? tekmeApi.popraviRezultat(tekma.id, vnos, izbran)
        : tekmeApi.vnesiRezultat(tekma.id, vnos),
    onSuccess: () => {
      onShranjeno()
      onZapri()
    },
  })

  /* Popravek, ki ne spremeni ničesar razen izpisa te tekme, gre mimo potrditve. */
  const predogledPopravka = useMutation({
    mutationFn: (vnos: VnosRezultata) => tekmeApi.predogledPopravka(tekma.id, vnos),
    onSuccess: (predogled, vnos) => {
      if (!predogled.zamenjanZmagovalec && predogled.izbire.length === 0) {
        shranjevanje.mutate({ vnos, nacin: null })
      } else {
        nastaviNacin(null)
        nastaviPotrditev({ vnos, predogled })
      }
    },
  })

  function oddaj(vnos: VnosRezultata) {
    if (popravek) {
      predogledPopravka.mutate(vnos)
    } else {
      shranjevanje.mutate({ vnos, nacin: null })
    }
  }

  function potrdiPopravek(dogodek: FormEvent) {
    dogodek.preventDefault()
    if (!potrditev) return
    if (potrditev.predogled.izbire.length > 0 && nacin === null) {
      nastaviNapakoVnosa('Izberi, kaj naj se zgodi.')
      return
    }
    nastaviNapakoVnosa(null)
    shranjevanje.mutate({ vnos: potrditev.vnos, nacin })
  }

  /* Koliko nizov je odigranih - po tem se ravna stevilo vrstic za tocke. Dokler
     je vpisan samo en igralec, ni izida in ni vrstic. */
  const odigranihNizov =
    niziPrvi !== '' && niziDrugi !== '' ? Number(niziPrvi) + Number(niziDrugi) : 0

  /* Ob vpisu nizov prilagodi stevilo vrstic za tocke nizov. Drugi igralec dobi
     ostanek samodejno (glej dopolniDrugi), zato je vpis enega dovolj za izid: ko
     sta oba vpisana, fokus skoči na točke, sicer na prazno polje drugega. */
  function obSpremembiNizov(stran: 'prvi' | 'drugi', vrednost: string) {
    const samodejni = dopolniDrugi(vrednost, stran === 'prvi' ? niziDrugi : niziPrvi, zaZmago)
    const prvi = stran === 'prvi' ? vrednost : samodejni
    const drugi = stran === 'drugi' ? vrednost : samodejni
    nastaviNiziPrvi(prvi)
    nastaviNiziDrugi(drugi)
    const skupaj = prvi !== '' && drugi !== '' ? Number(prvi) + Number(drugi) : 0
    nastaviTockeNizov((prejsnje) => vrsticeZaIzid(prejsnje, skupaj))

    if (prvi !== '' && drugi !== '') {
      if (vnasamTocke) sklici.naFokusTock.current = true
    } else if (vrednost !== '') {
      const sosednje = stran === 'prvi' ? sklici.sklicDrugi.current : sklici.sklicPrvi.current
      if (sosednje) fokusiraj(sosednje)
    }
  }

  /* Nova meja spremeni, koliko nizov se igra, zato vpisani nizi in točke pri stari
     meji ne ostanejo veljavni - organizator jih vpiše znova. */
  function obSpremembiSteviloNizov(nov: number) {
    nastaviSteviloNizov(nov)
    nastaviNiziPrvi('')
    nastaviNiziDrugi('')
    nastaviTockeNizov([])
    nastaviPredajaNizi1('')
    nastaviPredajaNizi2('')
  }

  function obOddaji(dogodek: FormEvent) {
    dogodek.preventDefault()
    nastaviNapakoVnosa(null)

    if (izidTip === 'IGRANO') {
      if (niziPrvi === '' || niziDrugi === '') {
        nastaviNapakoVnosa('Vnesi dobljene nize obeh igralcev.')
        return
      }
      const nizi1 = Number(niziPrvi)
      const nizi2 = Number(niziDrugi)

      let nizi: NizVnos[] | null = null
      if (vnasamTocke) {
        if (tockeNizov.some((niz) => niz.tocke1 === '' || niz.tocke2 === '')) {
          nastaviNapakoVnosa('Vnesi točke vseh nizov ali izklopi vnos točk.')
          return
        }
        nizi = tockeNizov.map((niz) => ({
          tocke1: Number(niz.tocke1),
          tocke2: Number(niz.tocke2),
        }))
        const napaka = preveriNize(nizi, nizi1, nizi2, zaZmago)
        if (napaka) {
          nastaviNapakoVnosa(napaka)
          return
        }
      }

      oddaj({
        izidTip: 'IGRANO',
        dobljeniNizi1: nizi1,
        dobljeniNizi2: nizi2,
        zmagovalecStran: null,
        nizi,
        steviloNizov: prepis,
      })
      return
    }

    /* Posebni izidi potrebujejo zmagovalca. */
    if (zmagovalecStran !== '1' && zmagovalecStran !== '2') {
      nastaviNapakoVnosa('Izberi zmagovalca.')
      return
    }
    oddaj({
      izidTip,
      dobljeniNizi1: izidTip === 'PREDAJA' ? Number(predajaNizi1) : null,
      dobljeniNizi2: izidTip === 'PREDAJA' ? Number(predajaNizi2) : null,
      zmagovalecStran: Number(zmagovalecStran) as 1 | 2,
      nizi: null,
      steviloNizov: prepis,
    })
  }

  if (potrditev) {
    return (
      <ModalnoOkno naslov="Potrditev popravka" onZapri={onZapri}>
        <p className="modal__podnaslov">
          {ime1} : {ime2}
        </p>
        <form className="obrazec" onSubmit={potrdiPopravek}>
          <PotrditevPopravka
            predogled={potrditev.predogled}
            nacin={nacin}
            naIzbiro={(izbira) => {
              nastaviNacin(izbira)
              nastaviNapakoVnosa(null)
            }}
          />
          {napakaVnosa && <div className="napaka">{napakaVnosa}</div>}
          <SporociloNapake napaka={shranjevanje.error} />
          <div className="obrazec__gumbi">
            <button
              type="button"
              className="gumb"
              disabled={shranjevanje.isPending}
              onClick={() => {
                shranjevanje.reset()
                nastaviNapakoVnosa(null)
                nastaviPotrditev(null)
              }}
            >
              Nazaj
            </button>
            <button type="submit" className="gumb gumb--glavni" disabled={shranjevanje.isPending}>
              {shranjevanje.isPending ? 'Shranjujem …' : 'Potrdi popravek'}
            </button>
          </div>
        </form>
      </ModalnoOkno>
    )
  }

  return (
    <ModalnoOkno
      naslov={popravek ? 'Popravek rezultata' : samoBrezIgre ? 'Izid brez igre' : 'Vnos rezultata'}
      onZapri={onZapri}
    >
      <p className="modal__podnaslov">
        {ime1} : {ime2}
        {!samoBrezIgre && (
          <span className="modal__namig"> (najboljši od {steviloNizov} nizov)</span>
        )}
      </p>
      {samoBrezIgre && !popravek && (
        <p className="namig">
          Ekipna tekma dobi izid iz zapisnika srečanja. Tu zapišeš samo, da ekipa ni
          nastopila ali je bila izključena — srečanje, ki se še ni začelo, se ob tem izbriše.
        </p>
      )}
      {popravek && (
        <p className="namig">
          Popravek sme spremeniti tudi zmagovalca. Preden se karkoli shrani, vidiš, kaj to
          pomeni za naslednje tekme. Turnirko rating se preračuna od dneva te tekme naprej.
        </p>
      )}

      <form className="obrazec" onSubmit={obOddaji}>
        <label className="obrazec__polje">
          <span>Način zaključka</span>
          <select
            value={izidTip}
            onChange={(dogodek) => nastaviIzidTip(dogodek.target.value as IzidTekme)}
          >
            {izidi.map((izid) => (
              <option key={izid.vrednost} value={izid.vrednost}>
                {izid.oznaka}
              </option>
            ))}
          </select>
        </label>

        {prepisMozen && (
          <>
            <label className="obrazec__polje">
              <span>Prvi do (dobljenih nizov)</span>
              <select
                value={steviloNizov}
                onChange={(dogodek) => obSpremembiSteviloNizov(Number(dogodek.target.value))}
              >
                <option value={3}>2</option>
                <option value={5}>3</option>
                <option value={7}>4</option>
              </select>
            </label>
            <p className="namig">Velja samo za to tekmo; dogodek ostane pri svoji nastavitvi.</p>
          </>
        )}

        {izidTip === 'IGRANO' && (
          <>
            {/* Nad poljema stoji priimek, ne »igralec 1«: s papirja se prepisuje ime. */}
            <div className="obrazec__vrstica">
              <label className="obrazec__polje">
                <span>Dobljeni nizi — {priimek1}</span>
                <StevilskoPolje
                  ref={sklici.sklicPrvi}
                  najvec={zaZmago}
                  vrednost={niziPrvi}
                  naSpremembo={(vrednost) => obSpremembiNizov('prvi', vrednost)}
                  onKeyDown={(dogodek) => sklici.obTipkiNizov(dogodek, 0)}
                  required
                />
              </label>
              <label className="obrazec__polje">
                <span>Dobljeni nizi — {priimek2}</span>
                <StevilskoPolje
                  ref={sklici.sklicDrugi}
                  najvec={zaZmago}
                  vrednost={niziDrugi}
                  naSpremembo={(vrednost) => obSpremembiNizov('drugi', vrednost)}
                  onKeyDown={(dogodek) => sklici.obTipkiNizov(dogodek, 1)}
                  required
                />
              </label>
            </div>

            <label className="obrazec__potrditev">
              <input
                type="checkbox"
                checked={vnasamTocke}
                onChange={(dogodek) => nastaviVnasamTocke(dogodek.target.checked)}
              />
              <span>Vnesi tudi točke po nizih</span>
            </label>

            {vnasamTocke && odigranihNizov > 0 && (
              <TockeNizov
                vrstice={tockeNizov}
                nastaviVrstice={nastaviTockeNizov}
                priimek1={priimek1}
                priimek2={priimek2}
                sklicPolj={sklici.sklicTock}
                naVrh={sklici.naVrh}
              />
            )}
          </>
        )}

        {izidTip !== 'IGRANO' && (
          <>
            <div className="obrazec__polje">
              <span>Zmagovalec</span>
              <div className="obrazec__radio-skupina">
                <label>
                  <input
                    type="radio"
                    name="zmagovalec"
                    checked={zmagovalecStran === '1'}
                    onChange={() => nastaviZmagovalecStran('1')}
                  />
                  {ime1}
                </label>
                <label>
                  <input
                    type="radio"
                    name="zmagovalec"
                    checked={zmagovalecStran === '2'}
                    onChange={() => nastaviZmagovalecStran('2')}
                  />
                  {ime2}
                </label>
              </div>
            </div>

            {izidTip === 'PREDAJA' && (
              <div className="obrazec__vrstica">
                <label className="obrazec__polje">
                  <span>Dobljeni nizi ob predaji — {priimek1}</span>
                  <StevilskoPolje
                    najvec={zaZmago - 1}
                    vrednost={predajaNizi1}
                    naSpremembo={nastaviPredajaNizi1}
                    required
                  />
                </label>
                <label className="obrazec__polje">
                  <span>Dobljeni nizi ob predaji — {priimek2}</span>
                  <StevilskoPolje
                    najvec={zaZmago - 1}
                    vrednost={predajaNizi2}
                    naSpremembo={nastaviPredajaNizi2}
                    required
                  />
                </label>
              </div>
            )}
          </>
        )}

        {napakaVnosa && <div className="napaka">{napakaVnosa}</div>}
        <SporociloNapake napaka={predogledPopravka.error ?? shranjevanje.error} />

        <div className="obrazec__gumbi">
          <button type="button" className="gumb" onClick={onZapri}>
            Prekliči
          </button>
          <button
            type="submit"
            className="gumb gumb--glavni"
            disabled={shranjevanje.isPending || predogledPopravka.isPending}
          >
            {shranjevanje.isPending || predogledPopravka.isPending
              ? 'Shranjujem …'
              : popravek
                ? 'Shrani popravek'
                : 'Shrani rezultat'}
          </button>
        </div>
      </form>
    </ModalnoOkno>
  )
}
