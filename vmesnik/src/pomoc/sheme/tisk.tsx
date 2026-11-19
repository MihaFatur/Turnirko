/* Prikazi poglavja Tisk: pomanjšani listi papirja. */
import { List, Platno } from './gradniki'

/* En sodniški listek v pomanjšavi: dva stolpca imen in vrstice nizov. */
function MiniListek() {
  return (
    <div className="sh-listek">
      <div className="sh-listek__glava">
        <span />
        <span />
      </div>
      {[0, 1, 2, 3, 4].map((i) => (
        <div key={i} className="sh-listek__niz">
          <span />
          <span />
        </div>
      ))}
    </div>
  )
}

export function ShemaListkiA4() {
  return (
    <Platno opis="List A4 s šestimi listki v dveh stolpcih in treh vrsticah, med njimi črtkane črte za rez.">
      <div className="sh-vrsta sh-vrsta--sredina">
        <List naslov="A4 · 6 listkov">
          <div className="sh-list__mreza sh-list__mreza--2x3">
            {Array.from({ length: 6 }, (_, i) => (
              <MiniListek key={i} />
            ))}
          </div>
        </List>
        <div className="sh-stolpec sh-stolpec--tesno sh-ozko">
          <span className="sh-tekma">Natisni izbrane</span>
          <span className="sh-tiho">potem potrdiš, da so se natisnili → oznaka »natisnjeno«</span>
        </div>
      </div>
    </Platno>
  )
}

function MiniKriznaTabela() {
  return (
    <div className="sh-krizna">
      {Array.from({ length: 16 }, (_, i) => (
        <span key={i} className={Math.floor(i / 4) === i % 4 ? 'sh-krizna__diag' : ''} />
      ))}
    </div>
  )
}

export function ShemaSkupineA4() {
  return (
    <Platno opis="List A4 s skupinami: vsaka skupina je križna tabela vsak z vsakim, pod njo razpored tekem.">
      <div className="sh-vrsta sh-vrsta--sredina">
        <List naslov="A4 · skupine">
          <div className="sh-list__mreza sh-list__mreza--2x3">
            {['A', 'B', 'C', 'D', 'E', 'F'].map((s) => (
              <div key={s} className="sh-skupina-mini">
                <span className="sh-mono">{s}</span>
                <MiniKriznaTabela />
                <span className="sh-crtice" />
              </div>
            ))}
          </div>
        </List>
        <div className="sh-stolpec sh-stolpec--tesno sh-ozko">
          <span className="sh-tiho">siva diagonala = igralec sam s sabo</span>
          <span className="sh-tiho">pod tabelo: razpored po kolih</span>
        </div>
      </div>
    </Platno>
  )
}

function MiniMreza() {
  return (
    <svg className="sh-list__mreza-svg" viewBox="0 0 100 60" aria-hidden="true">
      {Array.from({ length: 8 }, (_, i) => (
        <path key={i} d={`M2 ${4 + i * 7} H20 V${7.5 + i * 7}`} />
      ))}
      {Array.from({ length: 4 }, (_, i) => (
        <path key={`b${i}`} d={`M20 ${7.5 + i * 14} H40 V${14.5 + i * 14}`} />
      ))}
      {Array.from({ length: 2 }, (_, i) => (
        <path key={`c${i}`} d={`M40 ${14.5 + i * 28} H62 V${28.5 + i * 28}`} />
      ))}
      <path d="M62 28.5 H86" />
    </svg>
  )
}

export function ShemaMrezaA4() {
  return (
    <Platno opis="Izločilna mreža na ležečem listu A4.">
      <div className="sh-vrsta sh-vrsta--sredina">
        <List lezeci naslov="A4 ležeče · do 32 igralcev">
          <MiniMreza />
        </List>
      </div>
    </Platno>
  )
}

export function ShemaIzpis() {
  return (
    <Platno opis="Izpis rezultatov v treh delih: razvrstitev, skupine in mreža, vsak list z glavo turnirja.">
      <div className="sh-vrsta sh-vrsta--sredina sh-vrsta--zgoraj">
        <List naslov="Razvrstitev">
          <div className="sh-list__glava" />
          <span className="sh-crtice sh-crtice--dolge" />
        </List>
        <List naslov="Skupine">
          <div className="sh-list__glava" />
          <div className="sh-list__mreza sh-list__mreza--2x2">
            {[0, 1, 2, 3].map((i) => (
              <MiniKriznaTabela key={i} />
            ))}
          </div>
        </List>
        <List lezeci naslov="Mreža">
          <div className="sh-list__glava" />
          <MiniMreza />
        </List>
      </div>
    </Platno>
  )
}

export function ShemaZapisnikOblike() {
  return (
    <Platno opis="Dve obliki ekipnega zapisnika: uradni NTZS na pokončnem A4 in Turnirkov na ležečem A4.">
      <div className="sh-vrsta sh-vrsta--sredina sh-vrsta--zgoraj">
        <List naslov="NTZS · pokončno">
          <div className="sh-list__glava" />
          <span className="sh-crtice sh-crtice--dolge" />
        </List>
        <List lezeci naslov="Turnirko · ležeče">
          <div className="sh-list__glava sh-list__glava--modra" />
          <span className="sh-crtice" />
        </List>
      </div>
    </Platno>
  )
}
