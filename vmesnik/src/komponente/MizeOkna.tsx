/* Okna strani Mize (prenova »tloris dvorane«, 24. 11. 2026): klic tekme na mizo, hiter izid
   in prvi zagon (postavitev dvorane).

   Okno klica je namenoma preprosto: mizo je izbral organizator z vlečenjem ali klikom, tu
   samo potrdi sodnika in tisk listka. Pri spremembi obstoječega klica (zamenjava sodnika,
   druga miza ekipnega srečanja) ostaneta izbirnika mize.

   Hiter izid je samo IZBIRA: zmagovalca in izid v nizih. Shranjevanje poteka v
   VnosRezultataOkno (odločitev lastnika, 24. 11. 2026): tam so točke po nizih, ki jih
   zaledje sicer ne zahteva, a lahko odločijo o vrstnem redu v skupini. */
import { useMemo, useState, type FormEvent, type Ref } from 'react'
import { Link } from 'react-router-dom'
import { useMutation, useQuery } from '@tanstack/react-query'

import { nizovZaZmago } from '../api/tipi'
import type { KandidatMizeDto, MizeTurnirjaDto, OsebaMizeDto, TekmaMizeDto } from '../api/tipi'
import { mizeApi } from '../api/zahteve'
import {
  drugaMizaEkipnega,
  imenaTekme,
  minutOd,
  mizSklon,
  opisTekme,
  oznakaMize,
  priimekNaMizi,
  privzetiSodnik,
  prosteMize,
  razlogiPredloga,
  razlogiZasedenosti,
} from '../pomozno/mize'
import { IskalniIzbirnik } from './IskalniIzbirnik'
import { ModalnoOkno } from './ModalnoOkno'
import { SporociloNapake } from './SporociloNapake'

/* ---------- okno klica ---------- */

/* Kar organizator potrdi v oknu klica; klic na strežnik, sporočilo in tisk opravi stran. */
export interface PotrditevKlica {
  tekma: TekmaMizeDto
  miza: number
  miza2: number | null
  sodnik: OsebaMizeDto | null
  tisk: boolean
  sprememba: boolean
}

export interface Klic {
  tekma: TekmaMizeDto
  miza: number | null
  /* Tekma je že na mizi: premik, zamenjava sodnika. */
  sprememba: boolean
}

export function OknoKlica({
  idTurnirja,
  podatki,
  klic,
  kandidat,
  onZapri,
  onPotrdi,
}: {
  idTurnirja: number
  podatki: MizeTurnirjaDto
  klic: Klic
  kandidat: KandidatMizeDto | undefined
  onZapri: () => void
  onPotrdi: (v: PotrditevKlica) => Promise<unknown>
}) {
  const t = klic.tekma
  const proste = prosteMize(podatki.mize)
  // pri spremembi sta na voljo tudi mizi, na katerih tekma že je
  const moznosti = [
    ...new Set([...(klic.sprememba ? [t.miza, t.miza2] : []), ...proste].filter((m): m is number => m !== null)),
  ].sort((a, b) => a - b)
  const zacetnaMiza = klic.miza ?? moznosti[0] ?? null
  const mizaPo = (st: number | null) => podatki.mize.find((m) => m.stevilka === st)

  const [miza, nastaviMizo] = useState<number | null>(zacetnaMiza)
  const [miza2, nastaviMizo2] = useState<number | null>(
    klic.sprememba ? t.miza2 : t.ekipno && zacetnaMiza !== null ? drugaMizaEkipnega(proste, zacetnaMiza) : null,
  )
  const [sodnik, nastaviSodnika] = useState<OsebaMizeDto | null>(
    klic.sprememba ? t.sodnik : privzetiSodnik(mizaPo(zacetnaMiza), t),
  )
  // ročno izbran sodnik ostane, ko organizator zamenja mizo
  const [sodnikRocno, nastaviSodnikRocno] = useState(klic.sprememba)
  /* Listki so natisnjeni vnaprej: tisk ob klicu je izjema, ki jo organizator odkljuka sam
     (lastnik 24. 11. 2026). */
  const [tisk, nastaviTisk] = useState(false)

  const osebe = useQuery({
    queryKey: ['turnir', idTurnirja, 'mize', 'osebe'],
    queryFn: () => mizeApi.osebe(idTurnirja),
    staleTime: 60_000,
  })
  const moznostiSodnika = useMemo(() => {
    const vse = (osebe.data ?? []).filter((o) => !t.osebe.includes(o.id))
    if (sodnik && !vse.some((o) => o.id === sodnik.id)) vse.unshift(sodnik)
    return vse.map((o) => ({ id: o.id, ime: o.ime }))
  }, [osebe.data, t.osebe, sodnik])

  /* Kdo zdaj igra ali sodi: opozorilo za izbranega sodnika. */
  const zasedeniSodniki = useMemo(() => {
    const z = new Map<number, number>()
    for (const m of podatki.mize) {
      if (!m.tekma || m.tekma.tekma.id === t.tekma.id) continue
      m.tekma.osebe.forEach((o) => z.set(o, m.stevilka))
      if (m.tekma.sodnik) z.set(m.tekma.sodnik.id, m.stevilka)
    }
    return z
  }, [podatki.mize, t.tekma.id])

  const potrdi = useMutation({
    mutationFn: () =>
      onPotrdi({ tekma: t, miza: miza!, miza2, sodnik, tisk: tisk && !klic.sprememba && !t.ekipno, sprememba: klic.sprememba }),
    onSuccess: onZapri,
  })

  function obMizi(st: number) {
    nastaviMizo(st)
    if (miza2 === st) nastaviMizo2(null)
    if (!sodnikRocno) nastaviSodnika(privzetiSodnik(mizaPo(st), t))
  }

  function obOddaji(d: FormEvent) {
    d.preventDefault()
    if (miza !== null) potrdi.mutate()
  }

  const predlaganSodnik = !sodnikRocno && sodnik !== null && sodnik.id === mizaPo(miza)?.cakajociSodnik?.id
  const zasedenSodnik = sodnik ? zasedeniSodniki.get(sodnik.id) : undefined
  const razlogi = kandidat ? razlogiPredloga(kandidat) : []
  const oznaka = miza === null ? '' : oznakaMize({ miza, miza2 })

  return (
    <ModalnoOkno
      naslov={klic.sprememba ? 'Spremeni klic' : `Pokliči na mizo ${oznaka}`}
      nadnaslov={opisTekme(t)}
      razred="modal--mize"
      onZapri={onZapri}
    >
      <form className="mize-okno" onSubmit={obOddaji}>
        <div className="mize-okno__miza">
          <span className="mize-okno__mreza" />
          <span className="mize-okno__igralec">{imenaPolno(t)[0]}</span>
          <span className="mize-okno__igralec">{imenaPolno(t)[1]}</span>
        </div>

        {kandidat?.zaseden && (
          <p className="mize-okno__opozorilo">
            {razlogiZasedenosti(kandidat).join(' · ')}. Tekmo lahko vseeno pokličeš.
          </p>
        )}

        {klic.sprememba &&
          (moznosti.length === 0 ? (
            <p className="mize-okno__opozorilo">Ni proste mize.</p>
          ) : (
            <div className="obrazec__vrstica">
              <label className="obrazec__polje">
                Miza
                <select value={miza ?? ''} onChange={(d) => obMizi(Number(d.target.value))}>
                  {moznosti.map((st) => (
                    <option key={st} value={st}>
                      {st}
                    </option>
                  ))}
                </select>
              </label>
              {t.ekipno && (
                <label className="obrazec__polje">
                  Druga miza
                  <select
                    value={miza2 ?? ''}
                    onChange={(d) => nastaviMizo2(d.target.value === '' ? null : Number(d.target.value))}
                  >
                    <option value="">brez</option>
                    {moznosti
                      .filter((st) => st !== miza)
                      .map((st) => (
                        <option key={st} value={st}>
                          {st}
                        </option>
                      ))}
                  </select>
                </label>
              )}
            </div>
          ))}

        <div className="mize-okno__polje">
          <IskalniIzbirnik
            oznaka="Sodnik"
            vidnaOznaka
            vObrazcu
            namig="Vpiši ime ali pusti prazno"
            moznosti={moznostiSodnika}
            izbrano={sodnik?.id ?? null}
            naIzbiro={(id) => {
              nastaviSodnikRocno(true)
              nastaviSodnika(moznostiSodnika.find((o) => o.id === id) ?? null)
            }}
            naPraznjenje={() => {
              nastaviSodnikRocno(true)
              nastaviSodnika(null)
            }}
          />
          {predlaganSodnik && <span className="mize-okno__namig">Poraženec prejšnje tekme na tej mizi.</span>}
          {zasedenSodnik !== undefined && (
            <span className="mize-okno__namig mize-okno__namig--opozorilo">
              {sodnik!.ime} zdaj igra ali sodi na mizi {zasedenSodnik}.
            </span>
          )}
        </div>

        {!klic.sprememba && !t.ekipno && (
          <label className="mize-okno__potrditev">
            <input type="checkbox" checked={tisk} onChange={(d) => nastaviTisk(d.target.checked)} />
            {/* Številka mize je svoja postavka vrste (razmik 8 px), kot v prototipu. */}
            <span>Natisni listek z vpisano mizo</span>
            <span>{miza}</span>
          </label>
        )}

        {razlogi.length > 0 && <p className="mize-okno__razlogi">Predlagano: {razlogi.join(' · ')}</p>}

        <SporociloNapake napaka={potrdi.error} />
        <div className="mize-okno__gumbi">
          <button
            type="submit"
            className="mize-okno__gumb mize-okno__gumb--glavni"
            disabled={miza === null || potrdi.isPending}
          >
            {klic.sprememba ? 'Shrani' : 'Pokliči'}
          </button>
          <button type="button" className="mize-okno__gumb" onClick={onZapri}>
            Prekliči
          </button>
          {!klic.sprememba && <span className="mize-okno__pojasnilo">Drugo tekmo povleci z vrste na mizo.</span>}
        </div>
      </form>
    </ModalnoOkno>
  )
}

/* Polno ime vsake strani; ekipa kot ime ekipe, dvojica z » / «. */
function imenaPolno(t: TekmaMizeDto): [string, string] {
  const [a, b] = imenaTekme(t).split(' – ')
  return [a ?? '?', b ?? '?']
}

/* ---------- okno izida ---------- */

export interface IzbiraIzida {
  /* Kdo je zmagal (stran 1 ali 2) in koliko nizov je dobil poraženec. */
  zmagovalec: 1 | 2
  niziPorazenca: number
}

/* Najvec nizov, ki jih lahko dobi poraženec (pri 5 nizih 0, 1, 2). */
function izidiPorazenca(t: TekmaMizeDto): number[] {
  return Array.from({ length: nizovZaZmago(t.tekma.steviloNizov) }, (_, i) => i)
}

function napisShrani(t: TekmaMizeDto, izbira: IzbiraIzida | null): string {
  if (!izbira) return 'Izberi izid'
  const ime = priimekNaMizi(izbira.zmagovalec === 1 ? t.tekma.udelezenec1 : t.tekma.udelezenec2)
  return `Shrani: zmaga ${ime} ${nizovZaZmago(t.tekma.steviloNizov)}:${izbira.niziPorazenca}`
}

function StolpecIzida({
  ime,
  stran,
  tekma,
  izbira,
  naIzbiro,
}: {
  ime: string
  stran: 1 | 2
  tekma: TekmaMizeDto
  izbira: IzbiraIzida | null
  naIzbiro: (i: IzbiraIzida) => void
}) {
  const zaZmago = nizovZaZmago(tekma.tekma.steviloNizov)
  return (
    <div className="mize-okno__stolpec">
      <span className="mize-okno__ime">{ime}</span>
      <div className="mize-okno__izidi">
        {izidiPorazenca(tekma).map((p) => {
          const izbran = izbira?.zmagovalec === stran && izbira.niziPorazenca === p
          return (
            <button
              key={p}
              type="button"
              className={'mize-okno__izid' + (izbran ? ' mize-okno__izid--izbran' : '')}
              aria-pressed={izbran}
              aria-label={`${ime} zmaga ${zaZmago}:${p}`}
              onClick={() => naIzbiro({ zmagovalec: stran, niziPorazenca: p })}
            >
              {zaZmago}:{p}
            </button>
          )
        })}
      </div>
    </div>
  )
}

export function OknoIzida({
  tekma: t,
  casStreznika,
  telefon,
  onZapri,
  onShrani,
  onPreklic,
  onSodnik,
}: {
  tekma: TekmaMizeDto
  casStreznika: string
  telefon: boolean
  onZapri: () => void
  onShrani: (izbira: IzbiraIzida) => void
  onPreklic: () => void
  onSodnik: () => void
}) {
  const [izbira, nastaviIzbiro] = useState<IzbiraIzida | null>(null)
  const minute = minutOd(t.poklicanaOb, casStreznika) ?? 0
  const [ime1, ime2] = imenaPolno(t)
  const nadnaslov = `Miza ${oznakaMize(t)} · ${opisTekme(t)} · ${minute} min`
  const naslov = t.ekipno ? 'Ekipno srečanje' : 'Vnesi izid'
  const sodnik = t.sodnik ? `Sodi ${t.sodnik.ime}` : 'Brez sodnika'

  const shrani = (
    <button
      type="button"
      className="mize-okno__gumb mize-okno__gumb--glavni mize-okno__gumb--shrani"
      disabled={!izbira}
      onClick={() => izbira && onShrani(izbira)}
    >
      {napisShrani(t, izbira)}
    </button>
  )

  const spremeniDesno = (
    <button type="button" className="mize-okno__besedilni mize-okno__besedilni--desno" onClick={onSodnik}>
      {t.ekipno ? 'Spremeni mizi' : 'Zamenjaj sodnika'}
    </button>
  )

  const vsebina = t.ekipno ? (
    <>
      <p className="mize-okno__ekipno-ime">
        {ime1} – {ime2}
      </p>
      <p className="mize-okno__ekipno-besedilo">
        Ekipno srečanje se vpisuje v zapisnik. Ko je zaključen, se obe mizi sprostita sami.
      </p>
      {t.idSrecanje !== null && (
        <div className="mize-okno__gumbi">
          <Link to={`/srecanja/${t.idSrecanje}`} className="mize-okno__gumb">
            Odpri zapisnik
          </Link>
          {!telefon && spremeniDesno}
        </div>
      )}
    </>
  ) : telefon ? (
    <>
      <StolpecIzida ime={ime1} stran={1} tekma={t} izbira={izbira} naIzbiro={nastaviIzbiro} />
      <StolpecIzida ime={ime2} stran={2} tekma={t} izbira={izbira} naIzbiro={nastaviIzbiro} />
      {izbira && shrani}
    </>
  ) : (
    <>
      <div className="mize-okno__izbira">
        <StolpecIzida ime={ime1} stran={1} tekma={t} izbira={izbira} naIzbiro={nastaviIzbiro} />
        <StolpecIzida ime={ime2} stran={2} tekma={t} izbira={izbira} naIzbiro={nastaviIzbiro} />
      </div>
      <p className="mize-okno__namig-izid">Klikni zmagovalčev izid v nizih. {sodnik}.</p>
      <div className="mize-okno__gumbi">
        {shrani}
        <button type="button" className="mize-okno__gumb" onClick={onZapri}>
          Prekliči
        </button>
        {spremeniDesno}
      </div>
    </>
  )

  /* »Zamenjaj sodnika« (pri ekipnem srečanju »Spremeni mizi«) odpre okno klica v načinu
     spremembe. V prototipu ga ni, funkcija pa mora ostati: stoji v vrsti z gumbi, kjer je
     prostor, da noga okna ostane enaka kot v prototipu. */
  const spremeni = (
    <button type="button" className="mize-okno__besedilni" onClick={onSodnik}>
      {t.ekipno ? 'Spremeni mizi' : 'Zamenjaj sodnika'}
    </button>
  )
  const preklic = (
    <button type="button" className="mize-okno__preklic" onClick={onPreklic}>
      Prekliči klic
    </button>
  )

  if (telefon) {
    return (
      <ModalnoOkno
        naslov={naslov}
        nadnaslov={nadnaslov}
        skritNaslov
        spodaj
        razred="modal--mize-list"
        onZapri={onZapri}
      >
        <div className="mize-okno mize-okno--telefon">
          {vsebina}
          <div className="mize-okno__telefon-dejanja">
            {preklic}
            {spremeni}
          </div>
        </div>
      </ModalnoOkno>
    )
  }

  return (
    <ModalnoOkno naslov={naslov} nadnaslov={nadnaslov} razred="modal--mize" onZapri={onZapri}>
      <div className="mize-okno">
        {vsebina}
        <div className="mize-okno__noga">
          <span className="mize-okno__noga-besedilo">Premik: povleci mizo na drugo prosto mizo.</span>
          {preklic}
        </div>
      </div>
    </ModalnoOkno>
  )
}

/* ---------- prvi zagon: postavitev dvorane ---------- */

export function PrviZagon({
  telefon,
  stevilo,
  naStevilo,
  porazenecSodi,
  naPorazenec,
  naPostavi,
  zasedeno,
  napaka,
  koracnik,
}: {
  telefon: boolean
  stevilo: number
  naStevilo: (n: number) => void
  porazenecSodi: boolean
  naPorazenec: (v: boolean) => void
  naPostavi: () => void
  zasedeno: boolean
  napaka: unknown
  /* Za klic iz »Uredi dvorano«, ki je v prvem zagonu enako postavitvi dvorane. */
  koracnik?: Ref<HTMLButtonElement>
}) {
  const manj = () => naStevilo(Math.max(1, stevilo - 1))
  const vec = () => naStevilo(Math.min(99, stevilo + 1))
  const stevec = (
    <div className="mize-zagon__stevec">
      <button type="button" className="mize-zagon__koracek" aria-label="Manj miz" onClick={manj}>
        −
      </button>
      <span className="mize-zagon__stevilo" aria-live="polite">
        {stevilo}
      </span>
      <button type="button" className="mize-zagon__koracek" aria-label="Več miz" ref={koracnik} onClick={vec}>
        +
      </button>
    </div>
  )
  const gumb = (
    <button type="button" className="mize-zagon__gumb" disabled={zasedeno} onClick={naPostavi}>
      Postavi {stevilo} {mizSklon(stevilo)}
    </button>
  )

  if (telefon) {
    return (
      <div className="mize-zagon mize-zagon--telefon">
        <p className="mize-zagon__vprasanje">Koliko miz imaš v dvorani?</p>
        {stevec}
        <SporociloNapake napaka={napaka} />
        {gumb}
      </div>
    )
  }

  return (
    <div className="mize-zagon">
      <div className="mize-zagon__kartica">
        <span className="mize-zagon__nad">Postavi dvorano</span>
        <h2 className="mize-zagon__naslov">Koliko miz imaš?</h2>
        <p className="mize-zagon__besedilo">
          Mize so oštevilčene od 1 naprej, kot jih sodnik vpiše na listek. Tloris desno se riše sproti.
        </p>
        {stevec}
        <label className="mize-zagon__stikalo">
          <input type="checkbox" checked={porazenecSodi} onChange={(d) => naPorazenec(d.target.checked)} />
          Poraženec sodi naslednjo tekmo
        </label>
        <SporociloNapake napaka={napaka} />
        {gumb}
      </div>
      <ol className="mize-zagon__predogled">
        {Array.from({ length: stevilo }, (_, i) => (
          <li key={i} className="mize-zagon__enota">
            <span className="mize-zagon__st">{i + 1}</span>
            <div className="mize-zagon__miza">
              <span className="mize-zagon__mreza" />
            </div>
          </li>
        ))}
      </ol>
    </div>
  )
}
