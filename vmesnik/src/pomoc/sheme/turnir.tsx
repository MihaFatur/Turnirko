/* Prikazi poglavja Turnir. */
import type { ReactNode } from 'react'

import {
  Gumb,
  Legenda,
  MrezaSvg,
  Napis,
  Okno,
  Platno,
  Polje,
  Puscica,
  Skatla,
  Tabela,
  Znacka,
} from './gradniki'

/* Majhen piktogram sistema (80 × 48) — samo črte, brez besedila. */
function Piktogram({ children }: { children: ReactNode }) {
  return (
    <svg className="sh-pikto" viewBox="0 0 80 48" aria-hidden="true">
      {children}
    </svg>
  )
}

const PIKTOGRAMI: [string, string, ReactNode][] = [
  [
    'Izločilni',
    'poraženec izpade',
    <Piktogram key="i">
      <path d="M4 6H24V18H44M4 18H24M4 30H24V42H44M4 42H24M44 12V36M44 24H66" />
      <circle className="sh-pikto__pika" cx="70" cy="24" r="3" />
    </Piktogram>,
  ],
  [
    'Krožni',
    'vsak z vsakim',
    <Piktogram key="k">
      <path d="M40 6L60 18L54 40H26L20 18Z M40 6L54 40M40 6L26 40M60 18L20 18M60 18L26 40M20 18L54 40" />
    </Piktogram>,
  ],
  [
    'Skupine + izločilni',
    'prva dva v mrežo',
    <Piktogram key="s">
      <rect x="4" y="4" width="22" height="18" />
      <rect x="4" y="26" width="22" height="18" />
      <path d="M26 13H40V35H26M40 24H56M56 14V34M56 14H70M56 34H70" />
    </Piktogram>,
  ],
  [
    'TOP',
    'skupine po jakosti',
    <Piktogram key="t">
      <rect className="sh-pikto__polno" x="14" y="4" width="52" height="11" />
      <rect x="14" y="18" width="52" height="11" />
      <rect x="14" y="32" width="52" height="11" />
    </Piktogram>,
  ],
  [
    'Za mesta',
    'finalne skupine',
    <Piktogram key="m">
      <rect x="4" y="6" width="20" height="16" />
      <rect x="4" y="26" width="20" height="16" />
      <path d="M24 10H50M24 30H50M24 18H50M24 38H50" />
      <rect className="sh-pikto__polno" x="50" y="6" width="24" height="16" />
      <rect x="50" y="26" width="24" height="16" />
    </Piktogram>,
  ],
  [
    'SV regija',
    'žrebi za vsa mesta',
    <Piktogram key="sv">
      <rect x="4" y="4" width="10" height="10" />
      <rect x="4" y="19" width="10" height="10" />
      <rect x="4" y="34" width="10" height="10" />
      <path d="M14 9H34V24M14 24H34M14 39H34V24M34 24H44M44 12V36M44 12H58M44 36H58M58 8V16M58 32V40M58 8H74M58 16H74M58 32H74M58 40H74" />
    </Piktogram>,
  ],
]

export function ShemaSistemi() {
  return (
    <Platno opis="Šest sistemov: izločilni, krožni, skupine z izločilnim delom, TOP, skupine za mesta, SV regija.">
      <div className="sh-mreza-piktogramov">
        {PIKTOGRAMI.map(([ime, opis, slika]) => (
          <div key={ime} className="sh-piktogram">
            {slika}
            <strong>{ime}</strong>
            <span className="sh-tiho">{opis}</span>
          </div>
        ))}
      </div>
    </Platno>
  )
}

export function ShemaPrijava() {
  return (
    <Platno opis="Kategorija, razdelek Dodaj igralce: gumba za prilepljen seznam in prenos, iskalnik in gumb Prijavi izbrane.">
      <Okno naslov="Kategorija · Dodaj igralce">
        <div className="sh-vrsta">
          <Gumb majhen st={1}>
            Prilepi seznam imen
          </Gumb>
          <Gumb majhen st={2}>
            Prenesi z drugega dogodka
          </Gumb>
        </div>
        <Polje vrednost={<span>kriz<span className="sh-kazalec" /></span>} st={3} siroko />
        <div className="sh-predlogi">
          <span className="sh-predlogi__izbran">Križan Dora · NTK Žalec</span>
          <span>Križnik Jan · ŠD Mozirje</span>
        </div>
        <div className="sh-vrsta sh-vrsta--konec">
          <Gumb glavni st={4}>
            Prijavi izbrane (3)
          </Gumb>
        </div>
      </Okno>
      <Legenda
        postavke={[
          [1, 'seznam iz e-pošte ali Excela'],
          [2, 'iz druge kategorije'],
          [3, 'tipkaj, ↑ ↓ in Enter'],
          [4, 'prijavi vse obkljukane'],
        ]}
      />
    </Platno>
  )
}

export function ShemaPrilepiSeznam() {
  return (
    <Platno opis="Prilepljen seznam štirih imen: dve najdeni, eno z dvema možnostma, eno ni najdeno.">
      <div className="sh-vrsta sh-vrsta--sredina">
        <div className="sh-prilepljeno">
          <span>Ana Novak</span>
          <span>Križan Dora</span>
          <span>3. Bor Novak, NTK Žalec</span>
          <span>Zoran Kmet</span>
        </div>
        <Puscica />
        <div className="sh-stolpec sh-stolpec--tesno">
          <div className="sh-vrstica">
            Novak Ana <Znacka ton="zelena">najden</Znacka>
          </div>
          <div className="sh-vrstica">
            Križan Dora <Znacka ton="zelena">najden</Znacka>
          </div>
          <div className="sh-vrstica">
            Novak Bor <Znacka ton="modra">2 možnosti</Znacka>
          </div>
          <div className="sh-vrstica">
            Zoran Kmet <Znacka ton="rjasta">ni najden</Znacka>
          </div>
        </div>
      </div>
    </Platno>
  )
}

export function ShemaPar() {
  return (
    <Platno opis="Dve posamični prijavi se povežeta v eno prijavo para.">
      <div className="sh-vrsta sh-vrsta--sredina">
        <div className="sh-stolpec sh-stolpec--tesno">
          <Skatla naslov="Novak Ana">prijava</Skatla>
          <Skatla naslov="Kos Jan">prijava</Skatla>
        </div>
        <Puscica oznaka="Poveži v par" />
        <Skatla naslov="Novak / Kos" ton="modra">
          ena prijava v mreži
        </Skatla>
      </div>
    </Platno>
  )
}

export function ShemaVrstniRed() {
  const predlog = ['Horvat Nik · brez ratinga', 'Novak Ana · 1712', 'Kos Jan · 1650', 'Zupan Eva · 1598']
  const popravljen = ['Novak Ana · 1712', 'Kos Jan · 1650', 'Horvat Nik', 'Zupan Eva · 1598']
  return (
    <Platno opis="Igralec brez ratinga je v predlogu na vrhu; s puščicama ga premakneš na pravo mesto in shraniš.">
      <div className="sh-vrsta sh-vrsta--sredina">
        <div className="sh-stolpec sh-stolpec--tesno">
          <Napis>Predlog</Napis>
          {predlog.map((p, i) => (
            <div key={p} className={'sh-vrstica' + (i === 0 ? ' sh-vrstica--rjasta' : '')}>
              <span>
                <span className="sh-mono">{i + 1}.</span> {p}
              </span>
            </div>
          ))}
        </div>
        <Puscica oznaka="↑ ↓" />
        <div className="sh-stolpec sh-stolpec--tesno">
          <Napis>Po popravku</Napis>
          {popravljen.map((p, i) => (
            <div key={p} className={'sh-vrstica' + (i === 2 ? ' sh-vrstica--modra' : '')}>
              <span>
                <span className="sh-mono">{i + 1}.</span> {p}
              </span>
            </div>
          ))}
          <Gumb majhen>Shrani vrstni red</Gumb>
        </div>
      </div>
    </Platno>
  )
}

export function ShemaPasovi() {
  const vrstice: [string, number[], boolean][] = [
    ['Pas 1', [1, 2, 3, 4], false],
    ['Pas 2', [7, 5, 8, 6], true],
    ['Pas 3', [10, 12, 9, 11], true],
  ]
  return (
    <Platno opis="Štiri skupine. Prvi pas, nosilci 1 do 4, gre po vrsti v A, B, C, D. Drugi in tretji pas se žrebata, po enega v vsako skupino.">
      <table className="sh-tabela sh-tabela--sredina sh-pasovi-tabela">
        <thead>
          <tr>
            <th />
            <th>A</th>
            <th>B</th>
            <th>C</th>
            <th>D</th>
            <th />
          </tr>
        </thead>
        <tbody>
          {vrstice.map(([pas, st, zreb]) => (
            <tr key={pas}>
              <th className="sh-mono">{pas}</th>
              {st.map((n) => (
                <td key={n}>
                  <span className={'sh-nosilec' + (zreb ? '' : ' sh-nosilec--fiksen')}>{n}</span>
                </td>
              ))}
              <td className="sh-tiho sh-mono">{zreb ? 'žreb' : 'po vrsti'}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </Platno>
  )
}

export function ShemaMrezaNosilcev() {
  return (
    <Platno opis="Mreža osmih: 1. nosilec na vrhu, 2. na dnu, srečata se v finalu. 3. in 4. sta v svojih četrtinah in prvima dvema prideta šele v polfinalu.">
      <MrezaSvg
        naslovi={['Četrtfinale', 'Polfinale', 'Finale']}
        kola={[
          [
            { a: '1. nosilec', b: '8.', poudarek: 'a' },
            { a: '5.', b: '4. nosilec', poudarek: 'b', ton: 'zelena' },
            { a: '3. nosilec', b: '6.', poudarek: 'a', ton: 'zelena' },
            { a: '7.', b: '2. nosilec', poudarek: 'b' },
          ],
          [
            { a: '1.', b: '4.', poudarek: 'a' },
            { a: '3.', b: '2.', poudarek: 'b' },
          ],
          [{ a: '1.', b: '2.', poudarek: 'a' }],
        ]}
      />
    </Platno>
  )
}

export function ShemaRocneSkupine() {
  const vrstica = (ime: string, skupina: string, pozicija: string) => (
    <div className="sh-vrstica" key={ime}>
      <span>{ime}</span>
      <span className="sh-vrsta">
        <Polje vrednost={`${skupina} ▾`} />
        <Polje vrednost={`${pozicija} ▾`} />
      </span>
    </div>
  )
  return (
    <Platno opis="Ročni vpis skupin: vsak igralec dobi skupino in pozicijo v njej, nato Shrani skupine in začni.">
      <Okno naslov="Ročni vpis skupin">
        <div className="sh-vrstica sh-vrstica--glava">
          <span>Igralec</span>
          <span>Skupina · pozicija</span>
        </div>
        {vrstica('Novak Ana', '1', '1')}
        {vrstica('Kos Jan', '2', '1')}
        {vrstica('Horvat Nik', '1', '2')}
        {vrstica('Zupan Eva', '2', '2')}
        <div className="sh-vrsta sh-vrsta--konec">
          <Gumb glavni>Shrani skupine in začni</Gumb>
        </div>
      </Okno>
    </Platno>
  )
}

export function ShemaIzSkupin() {
  return (
    <Platno opis="Štiri skupine v mrežo osmih: zmagovalci skupin na nosilskih mestih, drugouvrščeni z žrebom v nasprotno polovico od zmagovalca svoje skupine.">
      <div className="sh-vrsta sh-vrsta--sredina sh-vrsta--zgoraj">
        <div className="sh-stolpec sh-stolpec--tesno sh-ozko">
          {['1', '2', '3', '4'].map((s) => (
            <Skatla key={s} naslov={`Skupina ${s}`}>
              <span className="sh-mono">
                <span className="sh-zmagovalec">{s}/1</span> · {s}/2
              </span>
            </Skatla>
          ))}
        </div>
        <div className="sh-raztegni">
          <MrezaSvg
            naslovi={['Četrtfinale', 'Polfinale']}
            kola={[
              [
                { a: '1/1', b: '2/2 · žreb', poudarek: 'a' },
                { a: '3/2 · žreb', b: '4/1', poudarek: 'b' },
                { a: '3/1', b: '4/2 · žreb', poudarek: 'a' },
                { a: '1/2 · žreb', b: '2/1', poudarek: 'b' },
              ],
              [
                { a: 'zgornja', b: 'polovica' },
                { a: 'spodnja', b: 'polovica' },
              ],
            ]}
          />
        </div>
      </div>
      <p className="sh-pripis">»1/2« je 2. mesto v skupini 1. Soigralca iz iste skupine (1/1 zgoraj, 1/2 spodaj) se srečata šele v finalu.</p>
    </Platno>
  )
}

export function ShemaSvRegija() {
  return (
    <Platno opis="Skupina 1 s 16 igralci: štiri skupine po štiri, nato glavni žreb za mesta 1 do 8 in tolažilni za mesta 9 do 16, oba od četrtfinala.">
      <Napis>Skupina 1 · 16 igralcev</Napis>
      <div className="sh-stiri">
        {['A', 'B', 'C', 'D'].map((s) => (
          <Skatla key={s} naslov={s}>
            <span className="sh-mono">1 2 3 4</span>
          </Skatla>
        ))}
      </div>
      <Puscica smer="dol" oznaka="po vrsti A1, B1 … A2, B2 …" />
      <div className="sh-dva">
        <Skatla naslov="Glavni žreb · 1.–8." ton="zelena">
          <span className="sh-mono">A1 B1 C1 D1 A2 B2 C2 D2</span>
        </Skatla>
        <Skatla naslov="Tolažilni žreb · 9.–16.">
          <span className="sh-mono">A3 B3 C3 D3 A4 B4 C4 D4</span>
        </Skatla>
      </div>
      <p className="sh-pripis">Vsak žreb se igra od četrtfinala do konca — za vsa mesta.</p>
    </Platno>
  )
}

export function ShemaEkipnaTekma() {
  return (
    <Platno opis="Ekipna tekma v mreži je srečanje: postava in posamične tekme; izid srečanja gre v mrežo.">
      <div className="sh-vrsta sh-vrsta--sredina sh-vrsta--zgoraj">
        <Skatla naslov="Mreža · polfinale" ton="modra">
          <span className="sh-mono">Žalec – Celje</span>
          <span className="sh-mono">
            <strong>3 : 1</strong>
          </span>
        </Skatla>
        <Puscica oznaka="klik" />
        <Okno naslov="Zapisnik srečanja">
          <Tabela
            vrstice={[
              ['A-X', 'Novak – Zupan', '3 : 1'],
              ['B-Y', 'Kos – Breznik', '1 : 3'],
              ['dvojice', 'Novak/Kos – …', '3 : 2'],
              ['A-Y', 'Novak – Breznik', '3 : 0'],
            ]}
          />
        </Okno>
      </div>
    </Platno>
  )
}

