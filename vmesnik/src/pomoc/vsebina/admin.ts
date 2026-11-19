import type { Clanek } from '../tipi'

/* Vidi samo admin: članki in gumbi »?« z `samoAdmin` se organizatorju ne
   izrišejo, iskalnik jih ne najde. */
export const ADMIN: Clanek[] = [
  {
    id: 'admin-igralci',
    naslov: 'Igralci: register in dvojniki',
    poglavje: 'admin',
    samoAdmin: true,
    kratko:
      'Register vseh igralcev. Nov igralec se preveri proti podobnim imenom; dvojnike združiš v »Čistka duplikatov«. Organizator sme igralca samo dodati, urejanje in združevanje sta tvoja.',
    kljucne: ['igralci', 'register', 'dvojnik', 'duplikat', 'združi', 'čistka', 'začetni rating', 'licenca'],
    shemaNamiga: 'zdruzitev',
    vsebina: [
      { tip: 'kje', pot: ['Igralci'] },
      { tip: 'razdelek', id: 'nov', naslov: 'Nov igralec in preverba dvojnikov' },
      {
        tip: 'odstavek',
        besedilo:
          'Ob vnosu aplikacija poišče igralce z istim imenom, zamenjanim imenom in priimkom ali podobnim imenom. Če je to ista oseba, vnos prekliči; sicer **Vseeno dodaj igralca**.',
      },
      {
        tip: 'razdelek',
        id: 'cistka',
        naslov: 'Čistka duplikatov',
        kratko:
          'Pari zapisov, ki so morda ista oseba (isto ime, zamenjana ime in priimek, podobno ime). »Združi« prenese vse tekme in prijave na en zapis; drugi izgine.',
        shemaNamiga: 'zdruzitev',
      },
      { tip: 'shema', shema: 'zdruzitev', opis: 'Dva zapisa iste osebe postaneta eden; tekme gredo zraven.' },
      {
        tip: 'pazi',
        besedilo:
          'Licenca NTZS ni dokaz, da gre za isto osebo — v viru imata dve licenci po dve različni osebi. Združi samo ob ujemanju datuma rojstva ali če osebo poznaš.',
      },
      { tip: 'razdelek', id: 'rating', naslov: 'Začetni rating' },
      {
        tip: 'odstavek',
        besedilo:
          'Z **Postavi začetni rating** igralcu določiš rating ročno (npr. tujec z znano jakostjo). Vpiši vir in pojasnilo — oba se izpišeta v dnevniku ratinga.',
      },
    ],
    povezani: ['prijave', 'admin-rating'],
  },
  {
    id: 'admin-dostopi',
    naslov: 'Dostopi: računi in potrjevanje',
    poglavje: 'admin',
    samoAdmin: true,
    kratko:
      'Zgoraj nabiralnik računov, ki čakajo nate (organizator za potrditev, igralec brez samodejne povezave). Spodaj kartoteka vseh računov z dejanji: poveži z igralcem, izklopi, nastavi geslo, izbriši.',
    kljucne: ['računi', 'dostopi', 'potrdi organizatorja', 'nabiralnik', 'poveži z igralcem', 'geslo', 'kartoteka'],
    shemaNamiga: 'nabiralnik',
    vsebina: [
      { tip: 'kje', pot: ['Dostopi'] },
      { tip: 'shema', shema: 'nabiralnik', opis: 'Nabiralnik zgoraj, kartoteka spodaj.' },
      {
        tip: 'razdelek',
        id: 'nabiralnik',
        naslov: 'Nabiralnik',
        kratko: 'Računi s potrjenim e-naslovom, ki čakajo na tvojo odločitev. O vsakem dobiš tudi e-pošto na info@.',
      },
      {
        tip: 'seznam',
        postavke: [
          '**Potrdi organizatorja** — račun dobi organizatorske pravice. Preveri klub: organizator s klubom ureja tudi tekmovanja kolegov iz kluba.',
          '**Poveži z igralcem** — račun igralca, ki ga aplikacija ni povezala sama (drugačno ime, ni datuma rojstva). Izberi pravi zapis v registru.',
          '**Zavrni** — zahteva izgine iz nabiralnika.',
        ],
      },
      { tip: 'razdelek', id: 'kartoteka', naslov: 'Kartoteka' },
      {
        tip: 'odstavek',
        besedilo:
          'Vsi računi z iskalnikom. Karton računa pokaže vlogo, paket, povezavo z igralcem in dnevnik. Dejanja: **Razveži**, **Izklopi**/**Vklopi**, **Nastavi geslo** (geslo se pokaže enkrat — izroči ga lastniku) in **Izbriši**.',
      },
      {
        tip: 'nasvet',
        besedilo:
          'Račun, ki še čaka na kodo (potrditev e-naslova ali kodo skrbnika pri mladoletnem), je označen »čaka kodo«. Če lastnik kode ne vpiše, se račun po 48 urah izbriše sam.',
      },
    ],
    povezani: ['admin-kode', 'admin-organizatorji'],
  },
  {
    id: 'admin-kode',
    naslov: 'Kode za popust',
    poglavje: 'admin',
    samoAdmin: true,
    kratko:
      'Kode, ki jih vpišejo ob registraciji: več mesecev Premium, odstotek na prvo plačilo ali organizatorski paket brez plačila. Ugodnosti kode po nastanku ni mogoče spremeniti.',
    kljucne: ['koda', 'popust', 'kupon', 'brezplačno', 'promocija'],
    vsebina: [
      { tip: 'kje', pot: ['Dostopi', 'Kode za popust'] },
      {
        tip: 'tabela',
        glava: ['Vrsta', 'Kaj da'],
        vrstice: [
          ['Premium brez plačila (igralec)', 'Preizkus Premium traja toliko mesecev namesto enega.'],
          ['Paket Navijač brez plačila', 'Navijaški paket za določeno število mesecev.'],
          ['Popust na prvo plačilo', 'Odstotek na prvi račun.'],
          ['Organizatorski paket brez plačila', 'Paket velja toliko mesecev od tvoje potrditve organizatorja.'],
        ],
      },
      {
        tip: 'seznam',
        postavke: [
          'Kodi lahko omejiš število uporab in rok; brez roka velja, dokler je ne izklopiš.',
          'Ugodnost se po nastanku ne spreminja. Za drugačno ugodnost ustvari novo kodo, staro izklopi.',
          'Napačna, potekla ali izčrpana koda vrne isto sporočilo, da se kod ne da ugibati.',
        ],
      },
    ],
    povezani: ['admin-dostopi'],
  },
  {
    id: 'admin-organizatorji',
    naslov: 'Dodelitev dostopa organizatorjem',
    poglavje: 'admin',
    samoAdmin: true,
    kratko:
      'Na strani lige ali turnirja z gumbom »Organizatorji« dodaš organizatorja iz drugega kluba in določiš, ali tekmovanje šteje v mejo njegovega paketa.',
    kljucne: ['organizatorji', 'dodeli', 'dostop', 'meja paketa', 'šteje v mejo', 'odvzemi'],
    shemaNamiga: 'dostop',
    vsebina: [
      { tip: 'kje', pot: ['Liga ali turnir', 'Organizatorji'] },
      {
        tip: 'koraki',
        postavke: [
          'Odpri **Organizatorji**.',
          '**Dodaj organizatorja** z iskalnikom.',
          'Za vsakega preklopi, ali tekmovanje šteje v mejo njegovega paketa. Vsak preklop se shrani takoj.',
          '**Odvzemi dostop**, ko ga ne potrebuje več.',
        ],
      },
      {
        tip: 'seznam',
        postavke: [
          'Lastnik (kdor je tekmovanje ustvaril) ima dostop vedno; preklopiš mu lahko samo štetje.',
          'Organizatorji istega kluba imajo dostop samodejno in so v seznamu kot opomba.',
          'Mejo paketa smeš preseči — okno presežek pokaže rjasto, ne prepove.',
          'Kvalifikacije podedujejo dodeljen dostop višje lige.',
        ],
      },
    ],
    povezani: ['dostop', 'narocnina-organizatorja'],
  },
  {
    id: 'admin-sifranti',
    naslov: 'Šifranti: kraji in klubi',
    poglavje: 'admin',
    samoAdmin: true,
    kratko: 'Kraji (za turnirje) in klubi (za igralce in ekipe). Organizator ju izbira iz seznama, novega doda admin.',
    kljucne: ['šifranti', 'kraj', 'klub', 'dvorana', 'mesto'],
    vsebina: [
      { tip: 'kje', pot: ['Šifranti', 'Kraji / Klubi'] },
      {
        tip: 'seznam',
        postavke: [
          'Kraj potrebuje turnir; dvorano vpiše organizator sam v obrazec turnirja.',
          'Klub potrebujejo igralci in klubske ekipe lige. Za enkratno zasedbo (podjetje) naj organizator raje uporabi prosto ekipo — šifranta ne zasuje.',
        ],
      },
    ],
    povezani: ['turnir-nov', 'ekipe-kader'],
  },
  {
    id: 'admin-uvoz',
    naslov: 'Uvoz NTZS (Stupa)',
    poglavje: 'admin',
    samoAdmin: true,
    kratko:
      'Uskladitev z javnim virom NTZS: »Predogled« pokaže, kaj bi se spremenilo (tekmovanja, novi igralci, odločitve o istovetnosti), uvoz pa zapiše in preračuna rating.',
    kljucne: ['uvoz', 'Stupa', 'NTZS', 'sinhronizacija', 'uskladitev', 'koledar'],
    shemaNamiga: 'uvoz',
    vsebina: [
      { tip: 'kje', pot: ['Uvoz NTZS'] },
      { tip: 'shema', shema: 'uvoz', opis: 'Predogled → odločitve → uvoz.' },
      {
        tip: 'koraki',
        postavke: [
          'Klikni **Predogled**. Aplikacija prebere vir in ga primerja z bazo.',
          'Preglej **Tekmovanja**, **Novi igralci** in **Odločitve o istovetnosti** (ali je igralec iz vira že v registru).',
          'Ko predogled pove »Pripravljeno za uvoz«, uvozi. Uvoz preračuna rating.',
        ],
      },
      {
        tip: 'seznam',
        postavke: [
          '»Posnetek je enak zadnjemu uvozu« pomeni, da pri viru ni nič novega.',
          'Če se je vir med predogledom in uvozom spremenil, je uvoz zavrnjen — ponovi predogled.',
          'Uvožena tekmovanja so samo za branje; organizator lahko spremeni le logotip in pokrovitelje.',
          '**Turnir iz koledarja**: prazen turnir iz koledarja zveze se ob uvozu napolni, ne podvoji.',
        ],
      },
    ],
    povezani: ['admin-igralci', 'dostop'],
  },
  {
    id: 'admin-rating',
    naslov: 'Rating: preračun in neaktivnost',
    poglavje: 'admin',
    samoAdmin: true,
    kratko:
      '»Preračunaj rating« izračuna rating vseh igralcev znova od prve tekme. »Uveljavi zapadle odbitke« odšteje točke neaktivnim igralcem. Običajno ni potrebno nobeno — rating teče ob vsakem vnosu sam.',
    kljucne: ['preračun', 'rating', 'neaktivnost', 'odbitek'],
    vsebina: [
      { tip: 'kje', pot: ['Rating'] },
      {
        tip: 'seznam',
        postavke: [
          '**Preračunaj rating** — po spremembi pravil ratinga ali po večjem popravku zgodovine. Traja nekaj časa.',
          '**Uveljavi zapadle odbitke** — odbitki za neaktivnost; če ni nič zapadlo, to stran pove.',
        ],
      },
    ],
    povezani: ['rating-tekmovanja', 'admin-igralci'],
  },
]
