/* Obrazec dogodka: nov dogodek in popravek imena ter pravil pred zrebom.
   Isti obrazec za oba, ker so pravila ista - preverja jih strežnik, vmesnik le vodi
   vnos. Vrste dogodka (posamično, dvojice, ekipno) in kategorije ekipnega dogodka se
   pri popravku ne spreminjajo; vrsto zakleni tudi prijava, ker je prijava igralec,
   par ali ekipa. */
import { useState, type FormEvent } from 'react'
import { useMutation } from '@tanstack/react-query'

import { dogodkiApi, turnirjiApi } from '../api/zahteve'
import type {
  Disciplina,
  DogodekDto,
  DogodekVnos,
  FormatSrecanja,
  SistemTekmovanja,
  SpolKategorija,
} from '../api/tipi'
import {
  FIKSNI_PRAG_FORMATA,
  OZNAKE_FORMAT,
  OZNAKE_SISTEM,
  OZNAKE_SPOL_KATEGORIJA,
  RAZPORED_FORMATA,
  kategorijeZaDisciplino,
} from '../api/tipi'
import { ModalnoOkno } from './ModalnoOkno'
import { SporociloNapake } from './SporociloNapake'
import { StevilskoPolje } from './StevilskoPolje'

type Lastnosti = {
  idTurnirja: number
  /* Brez dogodka je obrazec nov; z dogodkom gre za popravek v pripravi. */
  dogodek?: DogodekDto
  /* Ali so na dogodku prijave (tudi odjavljene) - takrat se vrsta ne spremeni. */
  imaPrijave?: boolean
  onZapri: () => void
  onShranjeno: () => void
}

export function DogodekObrazecOkno({
  idTurnirja,
  dogodek,
  imaPrijave = false,
  onZapri,
  onShranjeno,
}: Lastnosti) {
  const urejanje = dogodek !== undefined
  const ekipniDogodek = dogodek?.disciplina === 'EKIPNO'
  const vrstaZaklenjena = urejanje && (imaPrijave || ekipniDogodek)
  const kategorijaZaklenjena = urejanje && ekipniDogodek

  const [ime, nastaviIme] = useState(dogodek?.ime ?? '')
  const [disciplina, nastaviDisciplino] = useState<Disciplina>(dogodek?.disciplina ?? 'POSAMICNO')
  const [spolKategorija, nastaviSpolKategorijo] = useState<SpolKategorija>(
    dogodek?.spolKategorija ?? 'KDORKOLI',
  )
  const [starostnaKategorija, nastaviStarostnoKategorijo] = useState(
    dogodek?.starostnaKategorija ?? '',
  )
  const [steviloNizov, nastaviSteviloNizov] = useState(String(dogodek?.privzetoSteviloNizov ?? 5))
  const [sistem, nastaviSistem] = useState<SistemTekmovanja>(dogodek?.sistemTekmovanja ?? 'IZLOCILNI')
  const [steviloSkupin, nastaviSteviloSkupin] = useState(String(dogodek?.steviloSkupin ?? 3))
  const [velikostSkupine, nastaviVelikostSkupine] = useState(String(dogodek?.velikostSkupine ?? 8))
  const [prijavnina, nastaviPrijavnino] = useState(
    dogodek?.prijavnina != null ? String(dogodek.prijavnina) : '',
  )
  const [rokPrijave, nastaviRokPrijave] = useState(dogodek?.rokPrijave ?? '')
  /* Ekipni dogodek: format srečanja in prag zmag (privzeto večina tekem -
     tekma v mreži ali skupini mora imeti zmagovalca). */
  const [format, nastaviFormat] = useState<FormatSrecanja>(dogodek?.formatSrecanja ?? 'EKIPNI_DP')
  const [prag, nastaviPrag] = useState(
    dogodek?.zmagZaSrecanje ?? Math.floor(RAZPORED_FORMATA.EKIPNI_DP.length / 2) + 1,
  )
  const [zaTretje, nastaviZaTretje] = useState(dogodek?.tekmaZaTretjeMesto ?? false)

  /* Dvojice se igrajo samo po izločilnem sistemu (takojšnje izpadanje), zato
     izbirnik sistema odpade; »Mešano« pa je pravilo o sestavi para in ga
     posamično tekmovanje ne pozna. Obojega ne uveljavlja samo obrazec —
     enako zavrneta strežnik in shema. */
  const dvojice = disciplina === 'DVOJICE'
  const ekipno = disciplina === 'EKIPNO'
  const kategorije = kategorijeZaDisciplino(disciplina)
  const tekemFormata = RAZPORED_FORMATA[format].length
  const najmanjsiPrag = Math.floor(tekemFormata / 2) + 1
  /* Format z zaklenjenim pragom (DŠI) prag določi sam; vrednost gre v oddajo. */
  const fiksniPrag = FIKSNI_PRAG_FORMATA[format]
  const pragVeljaven = fiksniPrag ?? prag
  const sistemDogodka: SistemTekmovanja = dvojice ? 'IZLOCILNI' : sistem
  /* Tekma za 3. mesto je del izločilne mreže. */
  const mozna3mesto = sistemDogodka === 'IZLOCILNI' || sistemDogodka === 'SKUPINE_IZLOCILNI'

  function zamenjajFormat(nov: FormatSrecanja) {
    nastaviFormat(nov)
    nastaviPrag(Math.floor(RAZPORED_FORMATA[nov].length / 2) + 1)
  }

  /* Format TOP: skupine so rangi po jakosti, zato je treba njihovo
     število in velikost določiti že ob dogodku - zmnožek pove, koliko
     najboljših prijavljenih sploh igra. */
  const skupinskiSistem = !dvojice && sistem === 'SKUPINE'
  const mejaIzbora = Number(steviloSkupin) * Number(velikostSkupine)

  /* Ob preklopu na dvojice kategorija, ki je tam ni, obtiči izbrana in
     strežnik bi vnos zavrnil - zato jo vrnemo na odprto. */
  function zamenjajDisciplino(nova: Disciplina) {
    nastaviDisciplino(nova)
    if (!kategorijeZaDisciplino(nova).includes(spolKategorija)) {
      nastaviSpolKategorijo('KDORKOLI')
    }
  }

  /* Nastavitve skupin in nivojev, ki jih obrazec ne ponuja (SV regija vpisuje nivoje
     v svojem oknu), se pri popravku ohranijo, dokler se sistem ne spremeni. Brez tega
     bi urejanje imena pobrisalo razrez, ki ga je organizator nastavil drugje. */
  function ohranjeneNastavitve(): Pick<DogodekVnos, 'steviloSkupin' | 'velikostSkupine' | 'steviloNivojev'> {
    const ohrani = dogodek !== undefined && dogodek.sistemTekmovanja === sistemDogodka && !skupinskiSistem
    return {
      steviloSkupin: skupinskiSistem ? Number(steviloSkupin) : ohrani ? (dogodek?.steviloSkupin ?? null) : null,
      velikostSkupine: skupinskiSistem ? Number(velikostSkupine) : ohrani ? (dogodek?.velikostSkupine ?? null) : null,
      steviloNivojev: ohrani ? (dogodek?.steviloNivojev ?? null) : null,
    }
  }

  const shranjevanje = useMutation({
    mutationFn: (vnos: DogodekVnos) =>
      dogodek ? dogodkiApi.uredi(dogodek.id, vnos) : turnirjiApi.dodajDogodek(idTurnirja, vnos),
    onSuccess: () => {
      onShranjeno()
      onZapri()
    },
  })

  function obOddaji(dogodekObrazec: FormEvent) {
    dogodekObrazec.preventDefault()
    shranjevanje.mutate({
      ime: ime.trim(),
      spolKategorija,
      starostnaKategorija: starostnaKategorija.trim() || null,
      privzetoSteviloNizov: Number(steviloNizov),
      prijavnina: prijavnina ? Number(prijavnina) : null,
      rokPrijave: rokPrijave || null,
      disciplina,
      sistemTekmovanja: sistemDogodka,
      ...ohranjeneNastavitve(),
      formatSrecanja: ekipno ? format : null,
      zmagZaSrecanje: ekipno ? pragVeljaven : null,
      tekmaZaTretjeMesto: mozna3mesto && zaTretje,
    })
  }

  return (
    <ModalnoOkno naslov={urejanje ? 'Uredi dogodek' : 'Nov dogodek'} onZapri={onZapri}>
      <form className="obrazec" onSubmit={obOddaji}>
        <label className="obrazec__polje">
          <span>Ime dogodka *</span>
          <input
            value={ime}
            onChange={(d) => nastaviIme(d.target.value)}
            placeholder="npr. Člani odprto"
            required
          />
        </label>

        <div className="obrazec__vrstica">
          <label className="obrazec__polje">
            <span>Disciplina *</span>
            <select
              value={disciplina}
              disabled={vrstaZaklenjena}
              onChange={(d) => zamenjajDisciplino(d.target.value as Disciplina)}
            >
              <option value="POSAMICNO">Posamično</option>
              <option value="DVOJICE">Dvojice</option>
              <option value="EKIPNO">Ekipno</option>
            </select>
          </label>
          <label className="obrazec__polje">
            <span>Kategorija *</span>
            <select
              value={spolKategorija}
              disabled={kategorijaZaklenjena}
              onChange={(d) => nastaviSpolKategorijo(d.target.value as SpolKategorija)}
            >
              {kategorije.map((vrednost) => (
                <option key={vrednost} value={vrednost}>
                  {OZNAKE_SPOL_KATEGORIJA[vrednost]}
                </option>
              ))}
            </select>
          </label>
          <label className="obrazec__polje">
            <span>Starostna kategorija</span>
            <input
              value={starostnaKategorija}
              onChange={(d) => nastaviStarostnoKategorijo(d.target.value)}
              placeholder="npr. do 15 let"
            />
          </label>
        </div>

        {vrstaZaklenjena && (
          <p className="namig">
            {ekipniDogodek
              ? 'Ekipnega dogodka ni mogoče spremeniti v drugo vrsto ali obratno, kategorije ekipnega dogodka pa tudi ne.'
              : 'Vrste dogodka ni mogoče spremeniti, ker so na dogodku že prijave.'}
          </p>
        )}

        {dvojice ? (
          <p className="namig">
            Dvojice se igrajo po sistemu <strong>takojšnjega izpadanja</strong> —
            enaka mreža kot posamično. Igralce prijaviš posamično, pare pa sestaviš pred
            žrebom.
            {spolKategorija === 'MESANO' && ' Vsak par mora sestavljati en moški in ena ženska.'}
          </p>
        ) : (
          <label className="obrazec__polje">
            <span>Sistem tekmovanja *</span>
            <select value={sistem} onChange={(d) => nastaviSistem(d.target.value as SistemTekmovanja)}>
              {Object.entries(OZNAKE_SISTEM).map(([vrednost, oznaka]) => (
                <option key={vrednost} value={vrednost}>
                  {oznaka}
                </option>
              ))}
            </select>
          </label>
        )}

        {ekipno && (
          <div className="obrazec__sklop">
            <div className="obrazec__vrstica">
              <label className="obrazec__polje">
                <span>Format srečanja *</span>
                <select value={format} onChange={(d) => zamenjajFormat(d.target.value as FormatSrecanja)}>
                  {(Object.keys(OZNAKE_FORMAT) as FormatSrecanja[]).map((f) => (
                    <option key={f} value={f}>{OZNAKE_FORMAT[f]}</option>
                  ))}
                </select>
              </label>
              <label className="obrazec__polje">
                {/* Meji sta v oznaki in ne v polju: števke se vpisujejo, ne
                    vrtijo, zato ju brez zapisa nikjer ne bi bilo videti. */}
                <span>Srečanje se konča pri zmagah ({najmanjsiPrag}–{tekemFormata})</span>
                <StevilskoPolje
                  najvec={tekemFormata}
                  vrednost={String(pragVeljaven)}
                  naSpremembo={(v) => nastaviPrag(Number(v))}
                  disabled={fiksniPrag !== undefined}
                  required
                />
              </label>
            </div>
            <p className="namig">
              Vrstni red tekem: {RAZPORED_FORMATA[format].join(' · ')}. Prijaviš ekipe s
              kadrom; postavo in posamične tekme vsakega srečanja vpišeš v zapisnik, izid
              srečanja pa gre v mrežo oz. skupino. Igralec sme biti v kadru ene same ekipe.
            </p>
          </div>
        )}

        {mozna3mesto && (
          <label className="obrazec__polje obrazec__polje--stikalo">
            <input type="checkbox" checked={zaTretje} onChange={(d) => nastaviZaTretje(d.target.checked)} />
            <span>Tekma za 3. mesto (poraženca polfinalov)</span>
          </label>
        )}

        {sistemDogodka === 'SV_REGIJA' && (
          <p className="namig">
            Igralci so po jakosti razdeljeni v nivoje (privzeto po 16), v vsakem nivoju igrajo
            skupine po 4 vsak z vsakim. Prvo- in drugouvrščeni gredo v glavni žreb nivoja,
            tretje- in četrtouvrščeni v tolažilni žreb; žreb se igra za vsa mesta. Število
            nivojev, velikosti skupin, ročni vpis skupin in razporeditev v žrebu nastaviš v
            pripravi dogodka. Igra se posamično.
          </p>
        )}

        {sistemDogodka === 'SKUPINE_ZA_MESTA' && (
          <p className="namig">
            Predtekmovalne skupine igrajo vsak z vsakim, nato prvo- in drugouvrščeni igrajo
            finalno skupino za 1.–4. mesto, tretje- in četrtouvrščeni za 5.–8. … Medsebojni
            izid iz predtekmovanja se prenese in se ne igra znova (PST, 14. člen).
          </p>
        )}

        {skupinskiSistem && (
          <div className="obrazec__sklop">
            <div className="obrazec__vrstica">
              <label className="obrazec__polje">
                <span>Število skupin *</span>
                <StevilskoPolje
                  najvec={26}
                  vrednost={steviloSkupin}
                  naSpremembo={nastaviSteviloSkupin}
                  required
                />
              </label>
              <label className="obrazec__polje">
                <span>Igralcev v skupini *</span>
                <StevilskoPolje
                  najvec={24}
                  vrednost={velikostSkupine}
                  naSpremembo={nastaviVelikostSkupine}
                  required
                />
              </label>
            </div>
            <p className="namig">
              {mejaIzbora > 0 ? (
                <>
                  Igralo bo <strong>najboljših {mejaIzbora}</strong> prijavljenih:
                  skupina A dobi mesta 1.–{velikostSkupine}, B naslednja in tako
                  naprej. Znotraj skupine igra vsak z vsakim, izločilnega dela ni.
                  Vrstni red urediš pred žrebom.
                </>
              ) : (
                'Vpiši število skupin in velikost skupine.'
              )}
            </p>
          </div>
        )}

        <div className="obrazec__vrstica">
          <label className="obrazec__polje">
            <span>Nizi (prvi do) *</span>
            {/* Prikazano je, do koliko dobljenih nizov se igra; dogodek in
                tekme hranijo najvecje mogoce stevilo nizov (3, 5, 7). */}
            <select value={steviloNizov} onChange={(d) => nastaviSteviloNizov(d.target.value)}>
              <option value="3">2</option>
              <option value="5">3</option>
              <option value="7">4</option>
            </select>
          </label>
          <label className="obrazec__polje">
            <span>Prijavnina (€)</span>
            <StevilskoPolje
              decimalno
              vrednost={prijavnina}
              naSpremembo={nastaviPrijavnino}
            />
          </label>
        </div>

        <label className="obrazec__polje">
          <span>Rok prijave</span>
          <input
            type="date"
            value={rokPrijave}
            onChange={(d) => nastaviRokPrijave(d.target.value)}
          />
        </label>

        <SporociloNapake napaka={shranjevanje.error} />
        <div className="obrazec__gumbi">
          <button type="button" className="gumb" onClick={onZapri}>
            Prekliči
          </button>
          <button type="submit" className="gumb gumb--glavni" disabled={shranjevanje.isPending}>
            {urejanje ? 'Shrani' : 'Dodaj dogodek'}
          </button>
        </div>
      </form>
    </ModalnoOkno>
  )
}
