import type { Clanek } from '../tipi'

export const TEKMOVANJE: Clanek[] = [
  {
    id: 'logotip',
    naslov: 'Logotip tekmovanja',
    poglavje: 'tekmovanje',
    kratko:
      'Logotip turnirja ali lige stoji ob naslovu in na natisnjenih listih. Naložiš ga enkrat v svojo knjižnico in ga nato izbiraš za vsa svoja tekmovanja.',
    kljucne: ['logotip', 'slika', 'grb', 'logo', 'znak kluba'],
    shemaNamiga: 'logotip',
    vsebina: [
      { tip: 'kje', pot: ['Turnir ali liga', 'Logotip'] },
      {
        tip: 'koraki',
        postavke: [
          'Klikni **Logotip** med dejanji tekmovanja (na telefonu pod tremi pikami).',
          '**Naloži datoteko** (PNG ali JPEG) ali izberi enega izmed **Tvoji logotipi**.',
          'Po potrebi obkljukaj **Odstrani belo ozadje** in preveri predogled **Na strani** in **Na črno-belem tisku**.',
          'Klikni **Shrani logotip**.',
        ],
      },
      { tip: 'shema', shema: 'logotip', opis: 'Ista slika na strani in na natisnjenem listu.' },
      {
        tip: 'seznam',
        postavke: [
          'Logotip je prikaz, ne pravilo — spremeniš ga lahko v vsakem stanju, tudi pri uvoženem tekmovanju.',
          '**Brez logotipa** ga odstrani s tekmovanja; v knjižnici ostane.',
          'Široki in visoki logotipi se izpišejo z enako ploščino, da noben ne prevlada.',
        ],
      },
    ],
    povezani: ['pokrovitelji', 'izpis-rezultatov'],
  },
  {
    id: 'pokrovitelji',
    naslov: 'Pokrovitelji',
    poglavje: 'tekmovanje',
    kratko:
      'Seznam pokroviteljev turnirja ali lige: ime, povezava in logotip. Prikažejo se na strani tekmovanja in v nogi natisnjenih rezultatov; glavni pokrovitelj je poudarjen.',
    kljucne: ['pokrovitelj', 'sponzor', 'donator', 'partner'],
    vsebina: [
      { tip: 'kje', pot: ['Turnir ali liga', 'Pokrovitelji'] },
      {
        tip: 'koraki',
        postavke: [
          'Klikni **Pokrovitelji**.',
          'Za vsakega vpiši **Ime**, neobvezno **Povezavo** (spletno stran) in **Dodaj logotip**.',
          'Enega lahko označiš kot **Glavni pokrovitelj**.',
          'Klikni **Shrani pokrovitelje** — seznam se shrani v celoti.',
        ],
      },
      {
        tip: 'nasvet',
        besedilo:
          'Povezava brez »https://« ga dobi sama. Logotipi pokroviteljev so iz iste knjižnice kot logotip tekmovanja.',
      },
    ],
    povezani: ['logotip', 'izpis-rezultatov'],
  },
  {
    id: 'zanimivosti',
    naslov: 'Zavihek Zanimivosti',
    poglavje: 'tekmovanje',
    kratko:
      'Zgodbe tekmovanja za gledalce: največji vzponi ratinga, najdaljše tekme, klubi v številkah … Nastanejo same, ko je odigranih dovolj tekem. Posamezne razdelke lahko gledalcem skriješ.',
    kljucne: ['zanimivosti', 'statistika', 'zgodbe', 'skrij razdelek'],
    vsebina: [
      {
        tip: 'seznam',
        postavke: [
          'Zavihek se pojavi, ko je odigranih vsaj 9 tekem; pri turnirju zajame vse kategorije.',
          'Razdelek brez podatkov se ne pokaže (npr. brez vpisanih točk po nizih ni statistike nizov).',
          'Padcev ratinga zavihek namenoma ne kaže.',
        ],
      },
      { tip: 'razdelek', id: 'skrij', naslov: 'Kaj vidijo ostali', kratko: 'Odkljukaj razdelke, ki jih gledalci ne smejo videti. Ti jih vidiš še naprej.' },
      {
        tip: 'odstavek',
        besedilo:
          'V zavihku Zanimivosti odpri **Kaj vidijo ostali** in odkljukaj razdelke, ki jih ne želiš pokazati. Skrivanje je samo prikaz — podatki ostanejo.',
      },
    ],
    povezani: ['logotip'],
  },
  {
    id: 'narocnina-organizatorja',
    naslov: 'Naročnina organizatorja',
    poglavje: 'tekmovanje',
    kratko:
      'Paket (Basic, Plus, Pro) določa, koliko lig in turnirjev lahko ustvariš v sezoni. Nadgradnja velja takoj (doplačaš razliko), znižanje ob naslednji obnovi. Obstoječa tekmovanja ostanejo vedno.',
    kljucne: ['naročnina', 'paket', 'plačilo', 'basic', 'plus', 'pro', 'meja', 'kvota', 'nadgradnja', 'preklic'],
    shemaNamiga: 'paket',
    vsebina: [
      { tip: 'kje', pot: ['Meni z imenom', 'Naročnina'] },
      { tip: 'shema', shema: 'paket', opis: 'Poraba paketa v sezoni.' },
      {
        tip: 'tabela',
        glava: ['Paket', 'Za koga'],
        vrstice: [
          ['Basic', 'Klub z eno ligo in dvema turnirjema na sezono.'],
          ['Plus', 'Večji klub ali regija: tri lige hkrati in turnir skoraj vsak drugi mesec.'],
          ['Pro', 'Zveza ali organizator cele sezone: pet lig hkrati in deset turnirjev.'],
        ],
      },
      {
        tip: 'seznam',
        postavke: [
          'Meja velja za tekmovanja, ki jih **ustvariš** v sezoni. Kvalifikacije in tekmovanja, ki ti jih dodeli admin brez štetja, ne štejejo.',
          '**Nadgradnja** velja takoj: doplačaš sorazmerni del razlike do konca obdobja.',
          '**Znižanje** velja ob naslednji obnovi; do takrat ostane višji paket.',
          'Ob preklicu naročnina velja do konca plačanega obdobja. Potem novih tekmovanj ne moreš ustvariti, obstoječa, rezultati in arhiv pa ostanejo.',
        ],
      },
      {
        tip: 'nasvet',
        besedilo: 'Porabo paketa vidiš na Nadzorni plošči in na strani Naročnina. [Nadzorna plošča](nadzorna-plosca)',
      },
    ],
    povezani: ['nadzorna-plosca', 'dostop'],
  },
]
