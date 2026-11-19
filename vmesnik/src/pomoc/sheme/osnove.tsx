/* Prikazi osnov, tekmovanja in administracije. */
import { Legenda, Napis, Okno, Platno, Potek, Puscica, Skatla, Znacka, type Ton } from './gradniki'

export function ShemaZgradba() {
  return (
    <Platno opis="Turnir ima kategorije s tekmami. Liga ima ekipe, ki igrajo srečanja s posamičnimi tekmami.">
      <div className="sh-dva">
        <div className="sh-stolpec">
          <Skatla naslov="Turnir" ton="modra">
            <span className="sh-mono">Odprto prvenstvo</span>
          </Skatla>
          <Puscica smer="dol" oznaka="kategorije" />
          <div className="sh-vrsta">
            <Znacka>Člani</Znacka>
            <Znacka>U15</Znacka>
            <Znacka>Dvojice</Znacka>
          </div>
          <Puscica smer="dol" oznaka="žreb" />
          <div className="sh-stolpec sh-stolpec--tesno">
            <span className="sh-tekma">Novak – Kos</span>
            <span className="sh-tekma">Horvat – Zupan</span>
          </div>
        </div>
        <div className="sh-stolpec">
          <Skatla naslov="Liga" ton="modra">
            <span className="sh-mono">1. liga Savinja</span>
          </Skatla>
          <Puscica smer="dol" oznaka="ekipe + kader" />
          <div className="sh-vrsta">
            <Znacka>Žalec 1</Znacka>
            <Znacka>Celje</Znacka>
            <Znacka>Kuhinja</Znacka>
          </div>
          <Puscica smer="dol" oznaka="razpored" />
          <Skatla naslov="Srečanje · 3. kolo">
            <span className="sh-mono">Žalec 1 – Celje 6 : 4</span>
            <span className="sh-mono sh-tiho">A-X · B-Y · dvojice …</span>
          </Skatla>
        </div>
      </div>
    </Platno>
  )
}

export function ShemaStatusi() {
  return (
    <Platno opis="V pripravi, nato žreb, v teku, nato zaključek, zaključeno.">
      <Potek
        koraki={[
          { naslov: 'V pripravi', postavke: ['pravila', 'prijave, ekipe', 'nosilci'] },
          { naslov: 'V teku', ton: 'modra', prehod: 'žreb / razpored', postavke: ['rezultati', 'tisk', 'popravki'] },
          { naslov: 'Zaključeno', prehod: 'zadnja tekma', postavke: ['popravki', 'izpis'] },
        ]}
      />
    </Platno>
  )
}

export function ShemaDostop() {
  return (
    <Platno opis="Urejati sme, kdor je tekmovanje ustvaril, organizator istega kluba ali organizator z dostopom, ki ga je dodelil admin. Uvoženo je samo za branje.">
      <div className="sh-vrsta sh-vrsta--sredina">
        <div className="sh-stolpec sh-stolpec--tesno">
          <Skatla naslov="1 · Ustvaril">tvoje tekmovanje</Skatla>
          <Skatla naslov="2 · Isti klub">kolegi iz kluba</Skatla>
          <Skatla naslov="3 · Dodelil admin">gumb Organizatorji</Skatla>
        </div>
        <Puscica />
        <div className="sh-stolpec sh-stolpec--tesno">
          <Skatla naslov="Sme urejati" ton="zelena" />
          <Skatla naslov="Uvoženo (NTZS)" ton="rjasta-mehko">
            samo logotip in pokrovitelji
          </Skatla>
        </div>
      </div>
    </Platno>
  )
}

function Palica({ oznaka, porabljeno, meja }: { oznaka: string; porabljeno: number; meja: number }) {
  return (
    <div className="sh-palica">
      <span className="sh-palica__oznaka">{oznaka}</span>
      <span className="sh-palica__deli">
        {Array.from({ length: meja }, (_, i) => (
          <span key={i} className={'sh-palica__del' + (i < porabljeno ? ' sh-palica__del--poln' : '')} />
        ))}
      </span>
      <span className="sh-mono">
        {porabljeno} / {meja}
      </span>
    </div>
  )
}

export function ShemaPaket() {
  return (
    <Platno opis="Paket Plus: porabljeni dve od treh lig in štirje od šestih turnirjev v sezoni.">
      <Okno naslov="Moj paket · Plus · velja do 1. 9.">
        <Palica oznaka="Lige" porabljeno={2} meja={3} />
        <Palica oznaka="Turnirji" porabljeno={4} meja={6} />
      </Okno>
    </Platno>
  )
}

export function ShemaPotekTurnirja() {
  return (
    <Platno opis="Turnir: priprava, žreb, igra, zaključek.">
      <Potek
        koraki={[
          { naslov: 'Priprava', postavke: ['turnir', 'kategorije', 'prijave', 'nosilci'] },
          { naslov: 'Žreb', ton: 'modra', postavke: ['skupine / mreža', 'tisk za vrata'] },
          { naslov: 'Igra', postavke: ['listki', 'rezultati', 'izločilni del'] },
          { naslov: 'Zaključek', ton: 'zelena', postavke: ['razvrstitev', 'izpis', 'zaključi turnir'] },
        ]}
      />
    </Platno>
  )
}

export function ShemaPotekLige() {
  return (
    <Platno opis="Liga: priprava s pravili in ekipami, razpored, sezona z zapisniki, konec s končnico. Pravila so odprta do razporeda, termini, kader in imena ekip vso sezono.">
      <Potek
        koraki={[
          { naslov: 'Priprava', postavke: ['pravila', 'ekipe, kader', 'prehodi'] },
          { naslov: 'Razpored', ton: 'modra', postavke: ['žreb ali ročno', 'termini kol'] },
          { naslov: 'Sezona', postavke: ['zapisniki', 'prestavitve', 'lestvice'] },
          { naslov: 'Konec', ton: 'zelena', postavke: ['končnica', 'kvalifikacije'] },
        ]}
      />
      <div className="sh-pasovi">
        <div className="sh-pas sh-pas--do-razporeda">
          <Napis>pravila lige</Napis>
        </div>
        <div className="sh-pas sh-pas--ves-cas">
          <Napis>termini · kader · imena ekip · prehodi</Napis>
        </div>
      </div>
    </Platno>
  )
}

export function ShemaRaven() {
  const ravni: [string, number][] = [
    ['Uradno (NTZS)', 100],
    ['Klubsko', 75],
    ['Rekreativno', 50],
    ['Ne šteje', 0],
  ]
  return (
    <Platno opis="Teža za rating: uradno 100 odstotkov, klubsko 75, rekreativno 50, ne šteje 0.">
      <div className="sh-stolpec sh-stolpec--tesno">
        {ravni.map(([ime, teza]) => (
          <div key={ime} className="sh-merilo">
            <span className="sh-merilo__oznaka">{ime}</span>
            <span className="sh-merilo__pot">
              <span className="sh-merilo__polnilo" style={{ width: `${teza}%` }} />
            </span>
            <span className="sh-mono">{teza} %</span>
          </div>
        ))}
      </div>
    </Platno>
  )
}

export function ShemaLogotip() {
  return (
    <Platno opis="Isti logotip ob naslovu na strani in v glavi natisnjenega lista.">
      <div className="sh-dva">
        <Okno naslov="Na strani">
          <div className="sh-vrsta">
            <span className="sh-logo">LOGO</span>
            <span className="sh-naslov-maketa">Odprto prvenstvo</span>
          </div>
        </Okno>
        <Okno naslov="Na tisku">
          <div className="sh-vrsta">
            <span className="sh-logo sh-logo--cb">LOGO</span>
            <span className="sh-mono">Rezultati · Člani</span>
          </div>
        </Okno>
      </div>
    </Platno>
  )
}

export function ShemaZdruzitev() {
  return (
    <Platno opis="Dva zapisa iste osebe se združita v enega, tekme obeh gredo zraven.">
      <div className="sh-vrsta sh-vrsta--sredina">
        <div className="sh-stolpec sh-stolpec--tesno">
          <Skatla naslov="Novak Ana">
            <span className="sh-mono">2010 · 34 tekem</span>
          </Skatla>
          <Skatla naslov="Ana Novak" crtkano>
            <span className="sh-mono">2010 · 3 tekme</span>
          </Skatla>
        </div>
        <Puscica oznaka="Združi" />
        <Skatla naslov="Novak Ana" ton="zelena">
          <span className="sh-mono">2010 · 37 tekem</span>
        </Skatla>
      </div>
    </Platno>
  )
}

export function ShemaNabiralnik() {
  const vrstica = (ime: string, kaj: string, gumb: string, ton: Ton) => (
    <div className="sh-vrstica">
      <span>
        <strong>{ime}</strong> <span className="sh-tiho">{kaj}</span>
      </span>
      <Znacka ton={ton}>{gumb}</Znacka>
    </div>
  )
  return (
    <Platno opis="Dostopi: nabiralnik čakajočih računov z dejanji, spodaj kartoteka vseh računov.">
      <Okno naslov="Dostopi">
        <Napis>Nabiralnik · 2 čakata</Napis>
        {vrstica('Jan Kos', 'organizator · NTK Žalec', 'Potrdi organizatorja', 'modra')}
        {vrstica('Ana Novak', 'igralka · ni povezave', 'Poveži z igralcem', 'modra')}
        <Napis>Kartoteka · 214 računov</Napis>
        <span className="sh-polje sh-polje--siroko">Išči po imenu ali e-pošti …</span>
      </Okno>
    </Platno>
  )
}

export function ShemaUvoz() {
  return (
    <Platno opis="Uvoz: predogled, pregled odločitev, uvoz s preračunom ratinga.">
      <Potek
        koraki={[
          { naslov: 'Predogled', postavke: ['prebere vir', 'primerja z bazo'] },
          { naslov: 'Odločitve', ton: 'modra', postavke: ['novi igralci', 'istovetnost'] },
          { naslov: 'Uvoz', ton: 'zelena', postavke: ['zapis', 'preračun ratinga'] },
        ]}
      />
      <Legenda postavke={[['!', 'Če se vir vmes spremeni, ponovi predogled.']]} />
    </Platno>
  )
}

