/* Žreb skupin enega dogodka, narejen za tisk na vrata dvorane.

   Listek je zapisnik, ki ga sodnik izpolni za mizo; ta list je obratno -
   objava žreba, ki jo igralci preberejo z razdalje. Zato so imena večja kot na
   listku, vsaka skupina je svoj okvir (se ne zlomi čez stran) in vsak okvir
   nosi ime dogodka, da list ostane berljiv tudi, ko ga kdo odlepi in obesi
   ločeno. Črno-belo ne glede na temo, kot listki (slogi v razdelku 16).

   Vsebina je ista kot na strani dogodka: igralci skupine (po jakosti), pod
   njimi tekme po kolih. Odigrana tekma pokaže izid, zato je list ob ponovnem
   tisku tudi pregled stanja. */
import type { MrezaDto, TurnirDto } from '../api/tipi'
import { sklonEkip, sklonIgralcev, sklonSkupin } from '../pomozno/oblikovanje'
import {
  sklopiZrebSkupin,
  steviloSkupinVSklopih,
  type TiskSkupina,
  vrsticaTurnirja,
} from '../pomozno/zrebSkupin'

export function ZrebSkupinDogodka({ mreza, turnir }: { mreza: MrezaDto; turnir?: TurnirDto }) {
  const dogodek = mreza.dogodek
  const ekipno = dogodek.disciplina === 'EKIPNO'
  const sklopi = sklopiZrebSkupin(mreza)
  const steviloSkupin = steviloSkupinVSklopih(sklopi)
  /* Vsak igralec enkrat, tudi ko nastopa v predtekmovalni in finalni skupini. */
  const steviloClanov = new Set(
    sklopi.flatMap((sklop) =>
      sklop.skupine.flatMap((skupina) => skupina.clani.map((clan) => clan.idPrijave)),
    ),
  ).size

  const nadNaslovom = vrsticaTurnirja(turnir)

  /* Ime dogodka je že naslov lista; spola in starosti ne ponavljamo (uvoz ju
     včasih zapiše kar v ime). */
  const podNaslovom = [
    'Žreb skupin',
    `${steviloSkupin} ${sklonSkupin(steviloSkupin)}`,
    steviloClanov > 0
      ? `${steviloClanov} ${ekipno ? sklonEkip(steviloClanov) : sklonIgralcev(steviloClanov)}`
      : null,
  ]
    .filter(Boolean)
    .join(' · ')

  return (
    <section className="zreb-dogodek">
      <header className="zreb-glava">
        {nadNaslovom && <p className="zreb-glava__nad">{nadNaslovom}</p>}
        <h1 className="zreb-glava__ime">{dogodek.ime}</h1>
        <p className="zreb-glava__pod">{podNaslovom}</p>
      </header>

      {sklopi.map((sklop) => (
        <div key={sklop.kljuc}>
          {sklop.naslov && <h2 className="zreb-sklop">{sklop.naslov}</h2>}
          {sklop.skupine.map((skupina) => (
            <ZrebSkupina key={skupina.id} skupina={skupina} imeDogodka={dogodek.ime} />
          ))}
        </div>
      ))}
    </section>
  )
}

function ZrebSkupina({ skupina, imeDogodka }: { skupina: TiskSkupina; imeDogodka: string }) {
  return (
    <article className={'zreb-skupina' + (skupina.imaMize ? ' zreb-skupina--mize' : '')}>
      <header className="zreb-skupina__glava">
        <h3 className="zreb-skupina__naslov">{skupina.naslov}</h3>
        <span className="zreb-skupina__dogodek">{imeDogodka}</span>
      </header>

      <div className="zreb-skupina__telo">
        <ul className="zreb-clani">
          {skupina.clani.map((clan) => (
            <li key={clan.idPrijave} className="zreb-clan">
              <span className="zreb-clan__ime">{clan.ime}</span>
              {clan.klub && <span className="zreb-clan__klub">{clan.klub}</span>}
            </li>
          ))}
        </ul>

        <div className="zreb-kola">
          {skupina.kola.map((kolo) => (
            <section key={kolo.kolo} className="zreb-kolo">
              <h4 className="zreb-kolo__naslov">{kolo.kolo}. kolo</h4>
              <ul className="zreb-tekme">
                {kolo.tekme.map((tekma) => (
                  <li key={tekma.id} className="zreb-tekma">
                    {skupina.imaMize && (
                      <span className="zreb-tekma__miza">
                        {tekma.miza !== null ? `miza ${tekma.miza}` : ''}
                      </span>
                    )}
                    <span className="zreb-tekma__ime zreb-tekma__ime--levo">{tekma.ime1}</span>
                    <span className="zreb-tekma__crtica">–</span>
                    <span className="zreb-tekma__ime">{tekma.ime2}</span>
                    <span className="zreb-tekma__izid">
                      {tekma.izid?.nizi}
                      {tekma.izid?.oznaka && (
                        <small className="zreb-tekma__oznaka">{tekma.izid.oznaka}</small>
                      )}
                    </span>
                  </li>
                ))}
                {kolo.prosti.length > 0 && (
                  <li className="zreb-prost">
                    <span className="zreb-prost__oznaka">Prost</span> {kolo.prosti.join(', ')}
                  </li>
                )}
              </ul>
            </section>
          ))}
          {skupina.kola.length === 0 && (
            <p className="zreb-kola__prazno">Tekme te skupine še niso določene.</p>
          )}
        </div>
      </div>
    </article>
  )
}
