/* Prikazi poglavja Rezultati. */
import { Gumb, Legenda, MrezaSvg, Napis, Okno, Platno, Polje, Skatla, Tabela } from './gradniki'

export function ShemaVnosRezultata() {
  return (
    <Platno opis="Vnos rezultata: dobljeni nizi obeh igralcev, neobvezne točke po nizih, način zaključka in gumb Shrani rezultat.">
      <Okno naslov="Vnos rezultata">
        <div className="sh-vrstica">
          <span>Novak Ana</span>
          <Polje vrednost={<strong>3</strong>} st={1} />
        </div>
        <div className="sh-vrstica">
          <span>Kos Jan</span>
          <Polje vrednost="1" />
        </div>
        <div className="sh-vrstica">
          <span>
            <span className="sh-kljukica sh-kljukica--da" /> Vnesi tudi točke po nizih
          </span>
          <span className="sh-oznaka-ovoj">
            <span className="sh-mono">11:7 · 9:11 · 11:5 · 11:8</span>
          </span>
        </div>
        <div className="sh-vrstica">
          <span>Način zaključka</span>
          <Polje vrednost="Odigrana tekma ▾" st={2} />
        </div>
        <div className="sh-vrsta sh-vrsta--konec">
          <Gumb glavni st={3}>
            Shrani rezultat
          </Gumb>
        </div>
      </Okno>
      <Legenda
        postavke={[
          [1, 'vpišeš poraženca, zmagovalec dobi 3 sam'],
          [2, 'brez boja, predaja, diskvalifikacija'],
          [3, 'zmagovalec gre naprej v mreži'],
        ]}
      />
    </Platno>
  )
}

export function ShemaTockePoNizih() {
  const niz = (st: number, a: string, b: string, kazalec?: boolean, samodejno?: boolean) => (
    <tr key={st}>
      <th className="sh-mono">{st}.</th>
      <td>
        <span className={'sh-okence' + (kazalec ? ' sh-okence--kazalec' : '')}>{a}</span>
      </td>
      <td>
        <span className={'sh-okence' + (samodejno ? ' sh-okence--samo' : '')}>{b}</span>
      </td>
    </tr>
  )
  return (
    <Platno opis="Točke po nizih: vpišeš točke poraženca niza, nasprotnik dobi 11 sam, kazalec skoči v naslednji niz.">
      <div className="sh-vrsta sh-vrsta--sredina sh-vrsta--zgoraj">
        <table className="sh-tabela sh-tabela--sredina sh-nizi">
          <thead>
            <tr>
              <th />
              <th>NOVAK</th>
              <th>KOS</th>
            </tr>
          </thead>
          <tbody>
            {niz(1, '11', '7')}
            {niz(2, '9', '11')}
            {niz(3, '11', '5', false, true)}
            {niz(4, '', '', true)}
          </tbody>
        </table>
        <div className="sh-stolpec sh-stolpec--tesno sh-ozko">
          <Skatla naslov="Vpišeš 5">Kos dobi 5, Novak 11 sam.</Skatla>
          <Skatla naslov="Kazalec" ton="modra">
            skoči v 4. niz
          </Skatla>
        </div>
      </div>
    </Platno>
  )
}

export function ShemaPopravek() {
  return (
    <Platno opis="Popravljen četrtfinale: namesto Novak je zmagal Kos. Polfinale ima že izid, zato izbereš: izbriši izide ali zamenjaj igralca in obdrži izide.">
      <MrezaSvg
        naslovi={['Četrtfinale', 'Polfinale']}
        kola={[
          [
            { a: 'Novak', b: 'Kos ✓ popravek', poudarek: 'b' },
            { a: 'Horvat', b: 'Zupan', poudarek: 'a' },
          ],
          [{ a: 'Novak→Kos', b: 'Horvat', izid: '3:2', poudarek: 'a', ton: 'rjasta' }],
        ]}
      />
      <div className="sh-dva">
        <Skatla naslov="Izbriši njihove izide">
          <span className="sh-mono">Kos – Horvat · izid prazen</span>
          <span className="sh-tiho">vpišeš znova</span>
        </Skatla>
        <Skatla naslov="Zamenjaj igralca, izidi ostanejo">
          <span className="sh-mono">Kos – Horvat · 3:2</span>
          <span className="sh-tiho">v dvorani je igral Kos</span>
        </Skatla>
      </div>
    </Platno>
  )
}

export function ShemaKrog() {
  return (
    <Platno opis="Novak, Kos in Horvat imajo po dve zmagi in tvorijo krog. Med njimi odloči razlika nizov: Novak 4 proti 3, Kos 3 proti 3, Horvat 3 proti 4.">
      <Napis>Skupina A · trije z 2 zmagama</Napis>
      <Tabela
        glava={['Igralec', 'Zmage', 'Nizi med njimi', 'Mesto']}
        vrstice={[
          ['Novak', '2', '4 : 3', '1.'],
          ['Kos', '2', '3 : 3', '2.'],
          ['Horvat', '2', '3 : 4', '3.'],
          ['Zupan', '0', '—', '4.'],
        ]}
        toni={['modra', 'modra', 'modra', undefined]}
      />
      <p className="sh-pripis">Šteje samo tekme med izenačenimi; Zupan v krogu ni.</p>
    </Platno>
  )
}
