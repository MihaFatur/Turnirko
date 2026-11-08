/* Drugi korak popravka rezultata: pove, kaj bo popravek naredil, in kjer je po
   tekmi že kaj odigrano, organizatorja vpraša, kaj naj se s tem zgodi. Aplikacija
   tega ne more odločiti sama: ali so naslednje tekme res igrali pravi igralci, je
   znano samo v dvorani (glej PoslediceIzidaStoritev). */
import type { NacinPopravka, NaslednjiDelPopravka, PopravekPredogledDto } from '../api/tipi'
import { OZNAKE_IZID } from '../api/tipi'
import { sklonTekem } from '../pomozno/oblikovanje'

interface Lastnosti {
  predogled: PopravekPredogledDto
  nacin: NacinPopravka | null
  naIzbiro: (nacin: NacinPopravka) => void
}

/* Besedila o naslednjem delu. Spol in število sta odvisna od vrste, zato so to celi
   stavki in ne sestavljeni iz besed. */
function besedilaDela(del: NaslednjiDelPopravka) {
  switch (del.vrsta) {
    case 'ZREBI':
      return {
        narejen: `Žrebi Skupine ${del.nivo} so že narejeni.`,
        podri: `Podri žrebe Skupine ${del.nivo}`,
        ostane: 'Žrebi ostanejo, kot so',
        znova: 'Žrebe narediš znova, naključno ali ročno.',
        neskladje: 'Tabela in žrebi se potem ne ujemajo več.',
      }
    case 'IZLOCILNI':
      return {
        narejen: 'Izločilni del je že narejen.',
        podri: 'Podri izločilni del',
        ostane: 'Izločilni del ostane, kot je',
        znova: 'Izločilni del narediš znova, naključno ali ročno.',
        neskladje: 'Tabela in izločilni del se potem ne ujemata več.',
      }
    case 'FINALNE_SKUPINE':
      return {
        narejen: 'Finalne skupine so že narejene.',
        podri: 'Podri finalne skupine',
        ostane: 'Finalne skupine ostanejo, kot so',
        znova: 'Finalne skupine nastanejo same iz novega vrstnega reda.',
        neskladje: 'Tabela in finalne skupine se potem ne ujemajo več.',
      }
  }
}

/* Tožilnik: »izbriši 1 tekmo«; sklonTekem pozna imenovalnik »1 tekma«. */
function tekemZaIzbris(n: number): string {
  return `${n} ${n % 100 === 1 ? 'tekmo' : sklonTekem(n)}`
}

function opisIzbire(nacin: NacinPopravka, del: NaslednjiDelPopravka | null) {
  switch (nacin) {
    case 'IZBRISI_IZIDE':
      return {
        naslov: 'Izbriši njihove izide',
        opis:
          'Igralca se v naslednji tekmi zamenjata, izide teh tekem pa vneseš znova. '
          + 'Izberi, kadar teh izidov ni mogoče obdržati.',
      }
    case 'ZAMENJAJ_IGRALCA':
      return {
        naslov: 'Zamenjaj igralca, izidi ostanejo',
        opis:
          'Igralca si zamenjata mesti v vseh teh tekmah. Pravilno samo, če jih je res igral '
          + 'pravi igralec in je napačno le ime v aplikaciji.',
      }
    case 'PODRI_NASLEDNJI_DEL': {
      if (!del) return { naslov: '', opis: '' }
      const besedila = besedilaDela(del)
      return {
        naslov:
          del.vnesenihTekem > 0
            ? `${besedila.podri} in izbriši ${tekemZaIzbris(del.vnesenihTekem)}`
            : besedila.podri,
        opis:
          (del.vnesenihTekem === 0 ? 'Nobena tekma še ni vnesena, zato se nič ne izgubi. ' : '')
          + besedila.znova,
      }
    }
    case 'OHRANI_NASLEDNJI_DEL': {
      if (!del) return { naslov: '', opis: '' }
      const besedila = besedilaDela(del)
      return {
        naslov: besedila.ostane,
        opis: `Popravita se samo tabela skupine in rating. ${besedila.neskladje}`,
      }
    }
  }
}

export function PotrditevPopravka({ predogled, nacin, naIzbiro }: Lastnosti) {
  const del = predogled.naslednjiDel
  /* Dogodek spet teče, kadar mu izbira vzame izide. */
  const odpreDogodek =
    predogled.dogodekZakljucen && (nacin === 'IZBRISI_IZIDE' || nacin === 'PODRI_NASLEDNJI_DEL')

  return (
    <div className="popravek">
      {predogled.zamenjanZmagovalec ? (
        <p className="popravek__glavno">
          Zmagovalec tekme bo <strong>{predogled.novZmagovalec}</strong> namesto{' '}
          {predogled.stariZmagovalec}.
        </p>
      ) : (
        <p className="popravek__glavno">Popravek spremeni vrstni red skupine.</p>
      )}

      {predogled.odvisneZIzidom.length > 0 && (
        <>
          <p className="namig">Te tekme stojijo na popravljeni tekmi in imajo že vnesen izid:</p>
          <ul className="popravek__tekme">
            {predogled.odvisneZIzidom.map((tekma) => (
              <li key={tekma.id}>
                <span className="popravek__oznaka">{tekma.oznaka}</span>
                <span className="popravek__par">
                  {tekma.udelezenec1} : {tekma.udelezenec2}
                </span>
                <span className="popravek__izid">
                  {tekma.izidTip && tekma.izidTip !== 'IGRANO'
                    ? OZNAKE_IZID[tekma.izidTip]
                    : `${tekma.nizi1} : ${tekma.nizi2}`}
                </span>
              </li>
            ))}
          </ul>
        </>
      )}

      {predogled.odvisnihBrezIzida > 0 && predogled.odvisneZIzidom.length === 0 && (
        <p className="namig">V naslednji tekmi, ki še nima izida, se igralca samo zamenjata.</p>
      )}

      {del && (
        <>
          <p className="namig">Vrstni red skupine se spremeni. {besedilaDela(del).narejen}</p>
          <div className="popravek__vrstni-red">
            <div>
              <span className="popravek__oznaka">Zdaj</span>
              <ol>
                {del.vrstniRedPrej.map((ime) => (
                  <li key={ime}>{ime}</li>
                ))}
              </ol>
            </div>
            <div>
              <span className="popravek__oznaka">Po popravku</span>
              <ol>
                {del.vrstniRedPotem.map((ime) => (
                  <li key={ime}>{ime}</li>
                ))}
              </ol>
            </div>
          </div>
        </>
      )}

      {predogled.izbire.length > 0 && (
        <fieldset className="popravek__izbire">
          <legend>Kaj naj se zgodi?</legend>
          {predogled.izbire.map((izbira) => {
            const { naslov, opis } = opisIzbire(izbira, del)
            return (
              <label className="popravek__izbira" key={izbira}>
                <input
                  type="radio"
                  name="nacin-popravka"
                  checked={nacin === izbira}
                  onChange={() => naIzbiro(izbira)}
                />
                <span>
                  <span className="popravek__izbira-naslov">{naslov}</span>
                  <span className="popravek__izbira-opis">{opis}</span>
                </span>
              </label>
            )
          })}
        </fieldset>
      )}

      {odpreDogodek && (
        <p className="namig">
          Dogodek je zaključen. Po tem izboru spet teče, dokler ne vneseš manjkajočih izidov,
          končna mesta pa nastanejo znova.
        </p>
      )}
      {predogled.dogodekZakljucen && !odpreDogodek && (
        <p className="namig">Končna mesta se preračunajo.</p>
      )}
      {predogled.stejeVRating && (
        <p className="namig">
          Turnirko rating se preračuna od dneva te tekme naprej, kar lahko traja nekaj trenutkov.
        </p>
      )}
    </div>
  )
}
