/* Prikazi poglavja Liga. */
import { Gumb, MrezaSvg, Napis, Okno, Platno, Polje, Puscica, Skatla, Tabela, Znacka } from './gradniki'

export function ShemaFormatSntl() {
  /* Vrstni red tekem SNTL (FormatSrecanja.razpored): 1 dvojice, nato
     A-X, B-Y, C-Z, B-X, A-Z, C-Y, B-Z, C-X, A-Y. */
  const red: Record<string, number> = {
    AX: 2, BY: 3, CZ: 4, BX: 5, AZ: 6, CY: 7, BZ: 8, CX: 9, AY: 10,
  }
  return (
    <Platno opis="Format SNTL: najprej dvojice, nato vsak domači igralec A, B, C z vsakim gostujočim X, Y, Z; številka v celici je vrstni red tekme.">
      <div className="sh-vrsta sh-vrsta--sredina sh-vrsta--zgoraj">
        <table className="sh-tabela sh-tabela--sredina sh-matrika">
          <thead>
            <tr>
              <th />
              {['X', 'Y', 'Z'].map((g) => (
                <th key={g}>{g}</th>
              ))}
            </tr>
          </thead>
          <tbody>
            {['A', 'B', 'C'].map((d) => (
              <tr key={d}>
                <th>{d}</th>
                {['X', 'Y', 'Z'].map((g) => (
                  <td key={g}>
                    <span className="sh-nosilec">{red[d + g]}</span>
                  </td>
                ))}
              </tr>
            ))}
          </tbody>
        </table>
        <div className="sh-stolpec sh-stolpec--tesno sh-ozko">
          <Skatla naslov="1 · dvojice" ton="modra">
            prva tekma
          </Skatla>
          <span className="sh-tiho">A, B, C = domači · X, Y, Z = gostje · številka = vrstni red</span>
        </div>
      </div>
    </Platno>
  )
}

export function ShemaEkipe() {
  return (
    <Platno opis="Klubska ekipa ima klub iz registra, prosta ekipa nastopa samo v tej ligi; obe imata kader iz registra igralcev.">
      <div className="sh-dva">
        <Skatla naslov="Iz registra">
          <span className="sh-mono">NTK Žalec 1</span>
          <span className="sh-tiho">klub iz šifranta</span>
        </Skatla>
        <Skatla naslov="Prosta ekipa">
          <span className="sh-mono">Kuhinja</span>
          <span className="sh-tiho">samo v tej ligi</span>
        </Skatla>
      </div>
      <Puscica smer="dol" oznaka="Kader" />
      <Skatla naslov="Igralci iz registra" ton="modra">
        <span className="sh-mono">rating in profil delujeta enako</span>
      </Skatla>
    </Platno>
  )
}

export function ShemaKrozniRazpored() {
  return (
    <Platno opis="Štiri ekipe, tri kola: v vsakem kolu vsaka ekipa igra enkrat.">
      <Tabela
        glava={['Kolo', 'Srečanji']}
        vrstice={[
          ['1.', 'A – D · B – C'],
          ['2.', 'C – A · D – B'],
          ['3.', 'A – B · C – D'],
        ]}
      />
      <p className="sh-pripis">Pri dvokrožni ligi sledijo še ista kola z zamenjanim gostiteljem.</p>
    </Platno>
  )
}

export function ShemaRocniRazpored() {
  const par = (a: string, b: string) => (
    <div className="sh-vrstica">
      <Polje vrednost={`${a} ▾`} />
      <span className="sh-mono">–</span>
      <Polje vrednost={`${b} ▾`} />
    </div>
  )
  return (
    <Platno opis="Ročni razpored: kolo za kolom izbereš domačo in gostujočo ekipo; gumba Predlagaj razpored in Shrani razpored.">
      <Okno naslov="Vpiši razpored ročno">
        <div className="sh-vrsta">
          <Gumb majhen>Predlagaj razpored</Gumb>
          <Gumb majhen>Počisti vse</Gumb>
        </div>
        <Napis>1. kolo</Napis>
        {par('Žalec 1', 'Celje')}
        {par('Mozirje', 'Kuhinja')}
        <Napis>2. kolo</Napis>
        {par('Celje', 'Mozirje')}
        {par('—', '—')}
        <div className="sh-vrsta sh-vrsta--konec">
          <Gumb glavni>Shrani razpored</Gumb>
        </div>
      </Okno>
    </Platno>
  )
}

export function ShemaEnakomerna() {
  const zgornja = ['A', 'B', 'C', 'D', 'E']
  const spodnja = ['F', 'G', 'H', 'I', 'J']
  return (
    <Platno opis="Deset ekip po moči: zgornja polovica A do E, spodnja F do J. Pari so A s F, B z G, C s H, D z I, E z J.">
      <div className="sh-vrsta sh-vrsta--sredina">
        <div className="sh-stolpec sh-stolpec--tesno">
          <Napis>zgornja polovica</Napis>
          {zgornja.map((e, i) => (
            <div key={e} className="sh-par">
              <span className="sh-nosilec sh-nosilec--fiksen">{e}</span>
              <span className="sh-par__crta" />
              <span className="sh-nosilec">{spodnja[i]}</span>
            </div>
          ))}
        </div>
        <div className="sh-stolpec sh-stolpec--tesno sh-ozko">
          <Skatla naslov="Krog: par A–F proti B–G">
            <span className="sh-mono">kolo 1: A – B · F – G</span>
            <span className="sh-mono">kolo 2: A – G · F – B</span>
          </Skatla>
        </div>
      </div>
    </Platno>
  )
}

export function ShemaUreKola() {
  return (
    <Platno opis="Kolo v torek ob dveh urah: ob 18.30 prvi krog, ob 19.45 drugi; vsaka ekipa igra dvakrat.">
      <Okno naslov="3. kolo · tor, 4. nov">
        <Napis>18.30</Napis>
        <div className="sh-vrsta">
          <span className="sh-tekma">A – B</span>
          <span className="sh-tekma">C – D</span>
        </div>
        <Napis>19.45</Napis>
        <div className="sh-vrsta">
          <span className="sh-tekma">A – C</span>
          <span className="sh-tekma">B – D</span>
        </div>
      </Okno>
    </Platno>
  )
}

export function ShemaTermini() {
  const kola: [string, string, boolean][] = [
    ['1.', '1. 10.', false],
    ['2.', '8. 10.', false],
    ['3.', '15. 10.', false],
    ['4.', '23. 10.', true],
    ['5.', '29. 10.', false],
  ]
  return (
    <Platno opis="Prvo kolo 1. oktobra in razmik 7 dni dasta datume vseh kol. Četrto kolo je ročno premaknjeno na 23. oktober, peto ostane 29. oktobra.">
      <div className="sh-vrsta">
        <Skatla naslov="Prvo kolo">1. 10. · 19.00</Skatla>
        <Skatla naslov="Razmik">7 dni</Skatla>
      </div>
      <div className="sh-trak">
        {kola.map(([kolo, datum, rocno]) => (
          <div key={kolo} className={'sh-trak__kolo' + (rocno ? ' sh-trak__kolo--modra' : '')}>
            <span className="sh-mono">{kolo}</span>
            <strong>{datum}</strong>
            {rocno && <span className="sh-tiho">ročno</span>}
          </div>
        ))}
      </div>
      <p className="sh-pripis">Premaknjeno kolo ne premakne naslednjih.</p>
    </Platno>
  )
}

export function ShemaPrestavitev() {
  return (
    <Platno opis="Srečanje Žalec – Celje se iz 3. kola prestavi v 5. kolo; srečanje Mozirje – Kuhinja brez znanega termina gre na polje z vprašajem.">
      <div className="sh-trak sh-trak--sest">
        {['1', '2', '3', '4', '5', '?'].map((k) => (
          <div
            key={k}
            className={
              'sh-trak__kolo' +
              (k === '3' ? ' sh-trak__kolo--crtkano' : '') +
              (k === '5' || k === '?' ? ' sh-trak__kolo--modra' : '')
            }
          >
            <span className="sh-mono">{k === '?' ? '?' : `${k}.`}</span>
            {k === '3' && <span className="sh-tekma sh-tekma--prazna">Žalec – Celje</span>}
            {k === '5' && <span className="sh-tekma">Žalec – Celje</span>}
            {k === '?' && <span className="sh-tekma">Mozirje – Kuhinja</span>}
          </div>
        ))}
      </div>
      <p className="sh-pripis">Izberi srečanje → klikni kolo (ali povleci). »?« = termin še ni znan.</p>
    </Platno>
  )
}

export function ShemaPostava() {
  const mesto = (m: string, ime: string) => (
    <div className="sh-vrstica" key={m}>
      <span className="sh-mono">{m}</span>
      <Polje vrednost={`${ime} ▾`} />
    </div>
  )
  return (
    <Platno opis="Postava: domači na mestih A, B, C, gostje na X, Y, Z, par za dvojice; Shrani postavo in generiraj tekme ustvari tekme.">
      <Okno naslov="Postava">
        <div className="sh-dva">
          <div className="sh-stolpec sh-stolpec--tesno">
            <Napis>Žalec 1</Napis>
            {mesto('A', 'Novak')}
            {mesto('B', 'Kos')}
            {mesto('C', 'Horvat')}
          </div>
          <div className="sh-stolpec sh-stolpec--tesno">
            <Napis>Celje</Napis>
            {mesto('X', 'Zupan')}
            {mesto('Y', 'Breznik')}
            {mesto('Z', 'Golob')}
          </div>
        </div>
        <div className="sh-vrsta sh-vrsta--konec">
          <Gumb glavni>Shrani postavo in generiraj tekme</Gumb>
        </div>
      </Okno>
      <Puscica smer="dol" />
      <div className="sh-vrstica">
        <span>
          <span className="sh-mono">A-X</span> Novak – Zupan
        </span>
        <Znacka ton="modra">Vnesi</Znacka>
      </div>
    </Platno>
  )
}

export function ShemaCone() {
  const toni = ['zelena', 'zelena', 'zelena-mehko', undefined, undefined, 'rjasta-mehko', 'rjasta', 'rjasta'] as const
  return (
    <Platno opis="Lestvica osmih ekip: prvi dve napredujeta, tretja igra kvalifikacije za višjo ligo, šesta kvalifikacije za obstanek, zadnji dve izpadeta.">
      <Tabela
        glava={['', 'Ekipa', 'Točke']}
        vrstice={[
          ['1.', 'Žalec 1', '24'],
          ['2.', 'Celje', '21'],
          ['3.', 'Mozirje', '18'],
          ['4.', 'Velenje', '14'],
          ['5.', 'Kuhinja', '12'],
          ['6.', 'Šoštanj', '9'],
          ['7.', 'Polzela', '6'],
          ['8.', 'Prebold', '2'],
        ]}
        toni={[...toni]}
      />
      <div className="sh-vrsta">
        <Znacka ton="zelena">napreduje</Znacka>
        <Znacka ton="zelena-mehko">kval. gor</Znacka>
        <Znacka ton="rjasta-mehko">kval. dol</Znacka>
        <Znacka ton="rjasta">izpade</Znacka>
      </div>
    </Platno>
  )
}

export function ShemaPiramida() {
  return (
    <Platno opis="Tri lige v piramidi: med vsako sosednjo dve ekipi napredujeta in dve izpadeta, kvalifikacije so vrstica pod višjo ligo.">
      <div className="sh-stolpec sh-stolpec--sredina">
        <Skatla naslov="1. liga" ton="modra" />
        <div className="sh-vrsta">
          <Znacka ton="zelena">↑ 2</Znacka>
          <Znacka ton="rjasta">↓ 2</Znacka>
          <span className="sh-tiho sh-mono">kval. 1 + 1</span>
        </div>
        <Skatla naslov="2. liga" />
        <div className="sh-vrsta">
          <Znacka ton="zelena">↑ 2</Znacka>
          <Znacka ton="rjasta">↓ 2</Znacka>
        </div>
        <Skatla naslov="3. liga" />
      </div>
    </Platno>
  )
}

export function ShemaKvalifikacije() {
  return (
    <Platno opis="Križni pari kvalifikacij: 8. višje lige s 4. nižje lige, 9. višje s 3. nižje.">
      <div className="sh-dva">
        <div className="sh-stolpec sh-stolpec--tesno">
          <Napis>Višja liga</Napis>
          <div className="sh-vrstica sh-vrstica--rjasta-mehko">8. Polzela</div>
          <div className="sh-vrstica sh-vrstica--rjasta-mehko">9. Prebold</div>
        </div>
        <div className="sh-stolpec sh-stolpec--tesno">
          <Napis>Nižja liga</Napis>
          <div className="sh-vrstica sh-vrstica--zelena-mehko">3. Vransko</div>
          <div className="sh-vrstica sh-vrstica--zelena-mehko">4. Braslovče</div>
        </div>
      </div>
      <div className="sh-stolpec sh-stolpec--tesno">
        <span className="sh-tekma">8. Polzela – 4. Braslovče</span>
        <span className="sh-tekma">9. Prebold – 3. Vransko</span>
      </div>
      <p className="sh-pripis">Najboljši višje lige igra z najslabšim nižje; doma je ekipa višje lige.</p>
    </Platno>
  )
}

export function ShemaKoncnica() {
  return (
    <Platno opis="Končnica štirih: prvi s četrtim, drugi s tretjim, zmagovalca igrata finale.">
      <MrezaSvg
        naslovi={['Polfinale', 'Finale']}
        kola={[
          [
            { a: '1. Žalec 1', b: '4. Velenje', poudarek: 'a', izid: '2:0' },
            { a: '3. Mozirje', b: '2. Celje', poudarek: 'a', izid: '2:1' },
          ],
          [{ a: 'Žalec 1', b: 'Mozirje' }],
        ]}
      />
      <p className="sh-pripis">Serija do 2 zmag: prvo tekmo gosti slabše uvrščena ekipa, odločilno bolje.</p>
    </Platno>
  )
}
