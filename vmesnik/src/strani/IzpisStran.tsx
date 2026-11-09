/* Izpis rezultatov kategorije za tisk (/dogodki/:id/izpis): končna razvrstitev, skupinski
   in izločilni del, vsak posebej ali v enem dokumentu. Stran je kot žreb skupin in mreže
   zunaj skupne postavitve, tiska jo brskalnik (window.print), zaledje ne rabi ničesar
   svojega - vse je že v mreži dogodka. Izbira je v naslovu strani (?deli=&tocke=), da
   ostane ob osvežitvi.

   /dogodki/:id/razvrstitev-tisk je ista stran, zaklenjena na razvrstitev: tja vodi gumb
   »Natisni razvrstitev«, ki ga vidi vsak gledalec. */
import { useMemo } from 'react'
import { useQuery } from '@tanstack/react-query'
import { useParams, useSearchParams } from 'react-router-dom'

import { dogodkiApi, turnirjiApi } from '../api/zahteve'
import { IzpisListi } from '../komponente/IzpisListi'
import { ZrebOkvir } from '../komponente/ZrebOkvir'
import {
  deliDogodka,
  glavaIzpisa,
  imaSlogSvr,
  imaVpisaneTocke,
  NASLOVI_DELOV,
  sestaviIzpis,
  VRSTNI_RED_DELOV,
  type DelIzpisa,
  type SlogIzpisa,
} from '../pomozno/izpisRezultatov'
import { sklonListov } from '../pomozno/oblikovanje'
import { usePokrovitelje } from '../pomozno/pokrovitelji'

export function IzpisStran({ samoRazvrstitev = false }: { samoRazvrstitev?: boolean }) {
  const { id } = useParams()
  const idDogodka = Number(id)
  const [iskanje, nastaviIskanje] = useSearchParams()

  const mreza = useQuery({
    queryKey: ['dogodek', idDogodka],
    queryFn: () => dogodkiApi.mreza(idDogodka),
  })

  /* Ime turnirja, datum, kraj in logotip za glavo; dogodek pozna le id turnirja. */
  const idTurnir = mreza.data?.dogodek.idTurnir
  const turnir = useQuery({
    queryKey: ['turnir', idTurnir],
    queryFn: () => turnirjiApi.najdi(idTurnir!),
    enabled: idTurnir !== undefined,
  })
  const pokrovitelji = usePokrovitelje('turnir', idTurnir)

  const moznosti = useMemo(() => {
    if (!mreza.data) return []
    const deli = deliDogodka(mreza.data)
    return samoRazvrstitev ? deli.filter((del) => del === 'razvrstitev') : deli
  }, [mreza.data, samoRazvrstitev])

  /* Privzeto vse, kar dogodek ima; izbira je v naslovu, »deli=« pomeni nič izbranega. */
  const vNaslovu = iskanje.get('deli')
  const izbrani = useMemo(
    () => (vNaslovu === null ? moznosti : moznosti.filter((del) => vNaslovu.split(',').includes(del))),
    [moznosti, vNaslovu],
  )
  const sTockami = iskanje.get('tocke') !== '0'

  /* Slog SVR (Excelovi listi SV regije) ponudimo samo pri dogodkih SV regije; izbira je v
     naslovu, da ostane ob osvežitvi. Dodatno oblikovanje (znak, logotip, pokrovitelji,
     številka lista) je pri SVR privzeto vključeno. */
  const moznaSvr = !samoRazvrstitev && !!mreza.data && imaSlogSvr(mreza.data)
  const slog: SlogIzpisa = moznaSvr && iskanje.get('slog') === 'svr' ? 'svr' : 'turnirko'
  const oblikovanje = iskanje.get('oblikovanje') !== '0'

  const glava = useMemo(
    () => (mreza.data ? glavaIzpisa(mreza.data, turnir.data) : null),
    [mreza.data, turnir.data],
  )
  const listi = useMemo(
    () =>
      mreza.data
        ? sestaviIzpis(mreza.data, izbrani, sTockami, pokrovitelji.length > 0, slog, oblikovanje, glava?.naslov ?? '')
        : [],
    [mreza.data, izbrani, sTockami, pokrovitelji.length, slog, oblikovanje, glava],
  )

  const dogodek = mreza.data?.dogodek
  const prikaziTocke = !!mreza.data && imaVpisaneTocke(mreza.data, izbrani, slog)
  const imaLezece = listi.some((list) => list.usmeritev === 'lezeci')

  function preklopiDel(del: DelIzpisa, vkljucen: boolean) {
    const novi = VRSTNI_RED_DELOV.filter((d) => moznosti.includes(d) && (d === del ? vkljucen : izbrani.includes(d)))
    nastaviIskanje(
      (prej) => {
        const naprej = new URLSearchParams(prej)
        if (novi.length === moznosti.length) naprej.delete('deli')
        else naprej.set('deli', novi.join(','))
        return naprej
      },
      { replace: true },
    )
  }

  function preklopiTocke(vkljucene: boolean) {
    nastaviIskanje(
      (prej) => {
        const naprej = new URLSearchParams(prej)
        if (vkljucene) naprej.delete('tocke')
        else naprej.set('tocke', '0')
        return naprej
      },
      { replace: true },
    )
  }

  function izberiSlog(novi: SlogIzpisa) {
    nastaviIskanje(
      (prej) => {
        const naprej = new URLSearchParams(prej)
        if (novi === 'svr') naprej.set('slog', 'svr')
        else naprej.delete('slog')
        return naprej
      },
      { replace: true },
    )
  }

  function preklopiOblikovanje(vkljuceno: boolean) {
    nastaviIskanje(
      (prej) => {
        const naprej = new URLSearchParams(prej)
        if (vkljuceno) naprej.delete('oblikovanje')
        else naprej.set('oblikovanje', '0')
        return naprej
      },
      { replace: true },
    )
  }

  return (
    <ZrebOkvir
      nazaj={`/dogodki/${idDogodka}`}
      nazajOznaka="← Nazaj na dogodek"
      naslov={dogodek ? `${samoRazvrstitev ? 'Končna razvrstitev' : 'Rezultati'} · ${dogodek.ime}` : null}
      nalaganje={mreza.isPending}
      napaka={mreza.error}
      stevec={`${listi.length} ${sklonListov(listi.length)}`}
      steviloZaTisk={listi.length}
      prazno={
        moznosti.length === 0
          ? samoRazvrstitev
            ? 'Dogodek še nima končne razvrstitve. Natisne se, ko je dogodek zaključen.'
            : 'Dogodek še nima rezultatov za izpis.'
          : 'Izberi vsaj en del za tisk.'
      }
      sirokList={imaLezece}
      razred="zreb-stran--izpis"
      izbira={
        !samoRazvrstitev &&
        moznosti.length > 0 && (
          <div className="izpis-izbira">
            {moznaSvr && (
              <div className="izpis-izbira__slog" role="radiogroup" aria-label="Slog izpisa">
                <span className="izpis-izbira__naslov">Slog</span>
                <label className="izpis-izbira__del">
                  <input
                    type="radio"
                    name="slog-izpisa"
                    checked={slog === 'turnirko'}
                    onChange={() => izberiSlog('turnirko')}
                  />
                  Turnirko
                </label>
                <label
                  className="izpis-izbira__del"
                  title="Listi kot Excelova tabela SV regije: skupine s križno tabelo in žrebi za vsa mesta"
                >
                  <input
                    type="radio"
                    name="slog-izpisa"
                    checked={slog === 'svr'}
                    onChange={() => izberiSlog('svr')}
                  />
                  SVR
                </label>
              </div>
            )}
            {moznosti.map((del) => (
              <label key={del} className="izpis-izbira__del">
                <input
                  type="checkbox"
                  checked={izbrani.includes(del)}
                  onChange={(d) => preklopiDel(del, d.target.checked)}
                />
                {NASLOVI_DELOV[del]}
              </label>
            ))}
            {prikaziTocke && (
              <label className="izpis-izbira__del izpis-izbira__del--tocke">
                <input
                  type="checkbox"
                  checked={sTockami}
                  onChange={(d) => preklopiTocke(d.target.checked)}
                />
                Točke po nizih
              </label>
            )}
            {slog === 'svr' && (
              <label
                className="izpis-izbira__del izpis-izbira__del--tocke"
                title="Znak Turnirko, kategorija, datum in kraj ter logotip zgoraj; pokrovitelji in številka lista spodaj. Brez tega je list enak Excelovemu vzorcu."
              >
                <input
                  type="checkbox"
                  checked={oblikovanje}
                  onChange={(d) => preklopiOblikovanje(d.target.checked)}
                />
                Dodatno oblikovanje
              </label>
            )}
          </div>
        )
      }
      navodilo={
        slog === 'svr' ? (
          <>
            Slog SVR posnema Excelove liste SV regije: pokončni A4, vsi listi. Natisni v merilu 100 %,
            brez robov in brez glave in noge brskalnika. Za datoteko izberi »Shrani kot PDF«.
          </>
        ) : imaLezece && listi.some((list) => list.usmeritev === 'pokoncni') ? (
          <>
            Izločilni del je ležeč, ostalo pokončno: v oknu za tisk pusti usmeritev, kot je. Natisni na A4 v
            merilu 100 %. Za datoteko izberi »Shrani kot PDF«.
          </>
        ) : (
          <>Natisni na A4 v merilu 100 %. Za datoteko izberi »Shrani kot PDF«.</>
        )
      }
    >
      {glava && (
        <IzpisListi
          listi={listi}
          glava={glava}
          logotip={turnir.data?.logotip ?? null}
          pokrovitelji={pokrovitelji}
          oblikovanje={oblikovanje}
        />
      )}
    </ZrebOkvir>
  )
}
