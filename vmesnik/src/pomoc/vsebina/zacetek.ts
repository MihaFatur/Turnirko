import type { Clanek } from '../tipi'

export const ZACETEK: Clanek[] = [
  {
    id: 'zgradba',
    naslov: 'Kako je Turnirko zgrajen',
    poglavje: 'zacetek',
    kratko:
      'Turnir ima kategorije (dogodke), v vsaki kategoriji so prijave in tekme. Liga ima ekipe, ekipe igrajo srečanja, srečanje je sestavljeno iz posamičnih tekem.',
    kljucne: ['dogodek', 'kategorija', 'struktura', 'začetek', 'osnove'],
    shemaNamiga: 'zgradba',
    vsebina: [
      {
        tip: 'odstavek',
        besedilo:
          'Turnirko pozna dve vrsti tekmovanj: **turnir** (en ali dva dneva v dvorani, več kategorij) in **ligo** (ekipe skozi celo sezono).',
      },
      { tip: 'shema', shema: 'zgradba', opis: 'Turnir se deli na kategorije, liga na ekipe in srečanja.' },
      { tip: 'razdelek', id: 'turnir', naslov: 'Turnir' },
      {
        tip: 'seznam',
        postavke: [
          '**Turnir** nosi ime, datum, kraj in raven tekmovanja (ta pove, koliko turnir šteje v rating).',
          '**Kategorija** (v aplikaciji tudi »dogodek«) je npr. »Člani«, »U15 dekleta« ali »Dvojice«. Vsaka ima svoj sistem tekmovanja, svoje prijave in svoj žreb.',
          '**Tekma** je dvoboj dveh igralcev (ali parov, ali ekip). Nastane ob žrebu.',
        ],
      },
      { tip: 'razdelek', id: 'liga', naslov: 'Liga' },
      {
        tip: 'seznam',
        postavke: [
          '**Liga** nosi pravila: format srečanja, točkovanje, termine kol.',
          '**Ekipa** ima kader igralcev. Ekipa je lahko klubska ali prosta (samo za to ligo).',
          '**Srečanje** je dvoboj dveh ekip v kolu. V zapisniku srečanja vpišeš postavo in izide posamičnih tekem.',
        ],
      },
      { tip: 'razdelek', id: 'kje-kaj', naslov: 'Kje najdeš kaj' },
      {
        tip: 'tabela',
        glava: ['Kaj želiš', 'Kje'],
        vrstice: [
          ['Pregled svojih tekmovanj in rokov', 'Nadzorna plošča (prva postavka v navigaciji)'],
          ['Nov turnir', 'Turnirji → + Nov turnir'],
          ['Nova liga', 'Lige → + Nova liga'],
          ['Prijave, žreb, rezultati kategorije', 'Turnir → klik na kategorijo'],
          ['Zapisnik srečanja lige', 'Liga → Razpored → klik na srečanje'],
          ['Nov igralec v registru', 'Igralci → + Nov igralec'],
          ['Ta navodila', 'Pomoč v navigaciji ali v meniju z imenom zgoraj desno'],
        ],
      },
      {
        tip: 'nasvet',
        besedilo:
          'Ob funkcijah stoji okrogel gumb **?**. Klik nanj pokaže kratko razlago, **Preberi več** pa odpre ta navodila v predalu ob strani, tako da ostaneš, kjer si bil. Gumbe lahko skriješ v meniju z imenom zgoraj desno (**Namigi ?**).',
      },
    ],
    povezani: ['statusi', 'dostop', 'nadzorna-plosca'],
  },
  {
    id: 'statusi',
    naslov: 'Statusi: v pripravi, v teku, zaključeno',
    poglavje: 'zacetek',
    kratko:
      'V pripravi urejaš prijave in nastavitve. Žreb (pri ligi razpored) tekmovanje požene: od takrat se nastavitve ne spreminjajo več, rezultati pa se vnašajo. Zaključenega turnirja ni mogoče znova odpreti.',
    kljucne: ['status', 'priprava', 'v teku', 'zaključen', 'zaklenjeno'],
    shemaNamiga: 'statusi',
    vsebina: [
      { tip: 'shema', shema: 'statusi', opis: 'Tekmovanje gre skozi tri stanja. Prehod naprej sproži organizator.' },
      { tip: 'razdelek', id: 'kaj-kdaj', naslov: 'Kaj lahko spreminjaš v katerem stanju' },
      {
        tip: 'tabela',
        glava: ['', 'V pripravi', 'V teku', 'Zaključeno'],
        vrstice: [
          ['Podatki turnirja (ime, datum, kraj)', 'da', 'da', 'ne'],
          ['Pravila kategorije ali lige', 'da', 'ne', 'ne'],
          ['Prijave igralcev, ekipe lige', 'da', 'ne', 'ne'],
          ['Kader ekipe lige', 'da', 'dopolnjevanje', 'ne'],
          ['Ponoven žreb ali razveljavitev razporeda', '—', 'do prve tekme', 'ne'],
          ['Vnos rezultatov', '—', 'da', '—'],
          ['Popravek rezultata', '—', 'da', 'da'],
          ['Ime ekipe, logotip, pokrovitelji', 'da', 'da', 'da'],
        ],
      },
      { tip: 'razdelek', id: 'prikazni', naslov: 'Zakaj gledalec vidi »V pripravi«, čeprav je žreb že narejen' },
      {
        tip: 'odstavek',
        besedilo:
          'Žreb lahko narediš že nekaj dni pred turnirjem. Dejansko je kategorija takrat že v teku (prijave so zaprte), gledalec pa do dneva turnirja še vedno vidi **V pripravi** — sicer bi turnir, ki se še ni začel, na seznamu stal med tekočimi. Isto velja za ligo do datuma prvega kola. Ti vidiš kontrole po dejanskem stanju.',
      },
      { tip: 'razdelek', id: 'zakljucek', naslov: 'Kdaj se kaj zaključi' },
      {
        tip: 'seznam',
        postavke: [
          '**Kategorija** se zaključi sama, ko je odigrana zadnja tekma. Pri sistemu s skupinami in izločilnim delom šele, ko izbereš izločilni del in se odigra.',
          '**Turnir** zaključiš sam: v dejanjih turnirja **Zaključi turnir**. Mogoče je šele, ko so zaključene vse kategorije, in tega ni mogoče preklicati.',
          '**Liga** gumba za zaključek nima. Ko so vsa kola odigrana, glava lige to pove sama.',
        ],
      },
      {
        tip: 'pazi',
        besedilo:
          'Popravek rezultata je mogoč tudi po zaključku. Če popravek izbriše izide naslednjih tekem, se kategorija spet odpre in se zaključi ob novi zadnji tekmi.',
      },
    ],
    povezani: ['zreb', 'razpored', 'popravek-rezultata'],
  },
  {
    id: 'dostop',
    naslov: 'Kdo sme urejati tekmovanje',
    poglavje: 'zacetek',
    kratko:
      'Tekmovanje sme urejati organizator, ki ga je ustvaril, organizatorji istega kluba in organizator, ki mu je dostop dodelil admin. Uvožena tekmovanja so samo za branje.',
    kljucne: ['pravice', 'lastnik', 'klub', 'dostop', 'urejanje', 'organizatorji'],
    shemaNamiga: 'dostop',
    vsebina: [
      { tip: 'shema', shema: 'dostop', opis: 'Tri poti do pravice urejanja.' },
      {
        tip: 'koraki',
        postavke: [
          '**Ustvaril si ga.** Turnir ali liga, ki ju ustvariš, sta tvoja.',
          '**Si v istem klubu.** Organizatorji istega kluba urejajo tekmovanja drug drugega — tako lahko turnir vodi več ljudi hkrati.',
          '**Dostop ti je dodelil admin.** Na strani tekmovanja ima admin gumb **Organizatorji**, kjer doda organizatorja iz drugega kluba.',
        ],
      },
      { tip: 'razdelek', id: 'uvozeno', naslov: 'Uvožena tekmovanja' },
      {
        tip: 'odstavek',
        besedilo:
          'Lige in turnirji, uvoženi iz NTZS (Stupa, stara stran), so samo za branje: rezultate vodi zveza. Spremeniš lahko le prikaz — logotip in pokrovitelje.',
      },
      {
        tip: 'nasvet',
        besedilo:
          'Dodeljeno tekmovanje lahko šteje v mejo tvojega paketa ali pa ne — to določi admin ob dodelitvi. Koliko imaš porabljeno, vidiš na Nadzorni plošči.',
      },
    ],
    povezani: ['nadzorna-plosca', 'narocnina-organizatorja'],
  },
  {
    id: 'nadzorna-plosca',
    naslov: 'Nadzorna plošča',
    poglavje: 'zacetek',
    kratko:
      'Tvoj začetni zaslon: paket in poraba, termini v naslednjih 14 dneh, tekoče lige in turnirji ter vse, kar čaka nate (nevpisani zapisniki, žrebi).',
    kljucne: ['pregled', 'moj profil', 'čaka te', 'kvota', 'domov'],
    shemaNamiga: 'paket',
    vsebina: [
      { tip: 'kje', pot: ['Navigacija', 'Nadzorna plošča'] },
      {
        tip: 'seznam',
        postavke: [
          '**Moj paket** — kateri paket imaš, do kdaj velja in koliko lig ter turnirjev si v tej sezoni že porabil.',
          '**Naslednjih 14 dni** — kola lig in turnirji, ki prihajajo.',
          '**Tekoče lige** in turnirji — z enim klikom do strani tekmovanja.',
          '**Rezultati in zapisniki** — srečanja s preteklim terminom brez zapisnika in kategorije, ki čakajo na žreb.',
          '**Pretekle sezone** — arhiv tvojih tekmovanj.',
        ],
      },
      { tip: 'shema', shema: 'paket', opis: 'Poraba paketa: ustvarjena tekmovanja te sezone proti meji paketa.' },
      {
        tip: 'odstavek',
        besedilo:
          'Zapisnik »čaka«, ko je termin srečanja pred današnjim dnem. Današnje srečanje še ne čaka — ekipe ga lahko še igrajo.',
      },
    ],
    povezani: ['narocnina-organizatorja', 'dostop'],
  },
]
