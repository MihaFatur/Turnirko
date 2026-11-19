import type { Clanek } from '../tipi'

export const REZULTATI: Clanek[] = [
  {
    id: 'vnos-rezultata',
    naslov: 'Vnos rezultata tekme',
    poglavje: 'rezultati',
    kratko:
      'Klikni tekmo, vpiši dobljene nize obeh igralcev in shrani. Točke po nizih so neobvezne. Za tekmo brez igre izberi način zaključka (brez boja, predaja, diskvalifikacija) in zmagovalca.',
    kljucne: ['rezultat', 'vnos', 'nizi', 'brez boja', 'predaja', 'diskvalifikacija', 'walkover', 'b. b.'],
    shemaNamiga: 'vnos-rezultata',
    vsebina: [
      { tip: 'kje', pot: ['Kategorija', 'Tekme ali mreža', 'klik na tekmo'] },
      { tip: 'shema', shema: 'vnos-rezultata', opis: 'Okno vnosa: nizi, neobvezne točke, Shrani rezultat.' },
      {
        tip: 'koraki',
        postavke: [
          'Klikni tekmo (v mreži, skupini ali seznamu tekem). Odpre se **Vnos rezultata**.',
          'Vpiši dobljene nize. Dovolj je poraženec: vpišeš 1, zmagovalec dobi 3 sam (pri tekmi na tri dobljene).',
          'Če imaš na listku točke, obkljukaj **Vnesi tudi točke po nizih** in jih prepiši. [Točke po nizih](tocke-po-nizih)',
          'Klikni **Shrani rezultat**. Zmagovalec gre samodejno v naslednje kolo mreže.',
        ],
      },
      {
        tip: 'razdelek',
        id: 'brez-igre',
        naslov: 'Tekma brez igre',
        kratko:
          'Brez boja = nasprotnik ni nastopil (ne šteje v rating). Predaja med igro = začeta tekma, ki je igralec ni dokončal. Diskvalifikacija = odločil sodnik.',
      },
      {
        tip: 'tabela',
        glava: ['Način zaključka', 'Kdaj', 'Rating'],
        vrstice: [
          ['Odigrana tekma', 'Običajno.', 'šteje'],
          ['Brez boja (nasprotnik ni nastopil)', 'Igralec ni prišel k mizi.', 'ne šteje'],
          ['Predaja med igro', 'Tekma se je začela, igralec je ni dokončal (poškodba).', 'šteje'],
          ['Diskvalifikacija', 'Odločitev sodnika.', 'ne šteje'],
        ],
      },
      { tip: 'razdelek', id: 'nizi', naslov: 'Drugačno število nizov za eno tekmo' },
      {
        tip: 'odstavek',
        besedilo:
          'Polje **Prvi do (dobljenih nizov)** v oknu velja samo za to tekmo (npr. finale na 4 dobljene); kategorija ostane pri svoji nastavitvi.',
      },
      {
        tip: 'nasvet',
        besedilo:
          'Gledalci vidijo rezultat takoj. Stran kategorije med igro sama osvežuje mrežo in lestvice; čas zadnje osvežitve stoji ob statusu (»Osveženo ob …«).',
      },
    ],
    povezani: ['tocke-po-nizih', 'popravek-rezultata', 'odstop'],
  },
  {
    id: 'tocke-po-nizih',
    naslov: 'Točke po nizih',
    poglavje: 'rezultati',
    kratko:
      'Neobvezno: točke vsakega niza, npr. 11:7. Najhitreje vpišeš samo točke poraženca niza — nasprotnik dobi 11 sam in kazalec skoči v naslednji niz. Aplikacija preveri, da se točke ujemajo z izidom.',
    kljucne: ['točke', 'nizi', 'set', 'potek tekme', '11:9'],
    shemaNamiga: 'tocke-po-nizih',
    vsebina: [
      { tip: 'shema', shema: 'tocke-po-nizih', opis: 'Vsak niz ima dve okenci; kazalec gre sam naprej.' },
      {
        tip: 'seznam',
        postavke: [
          'Nad stolpcem stoji priimek igralca — tako kot na listku.',
          '**Najhitreje: vpiši samo točke poraženca niza.** Vpišeš 7, nasprotnik dobi 11 sam in kazalec skoči v naslednji niz. Pri 10 dobi nasprotnik 12.',
          'Pri števki 1 okence počaka, ker lahko sledi še ena (11, 13 …). Podaljšek (13:11, 21:19) vpišeš z obema številkama.',
          'S puščicami ← → ↑ ↓ greš na katero koli okence.',
          'Niz mora biti veljaven (do 11, razlika 2, pri podaljšku natanko 2), izid nizov pa se mora ujemati z vpisanim zmagovalcem.',
          'Po odločitvi tekme ne sme slediti noben niz več.',
        ],
      },
      {
        tip: 'odstavek',
        besedilo:
          'Točke se izpišejo na profilu igralca (statistika nizov) in na natisnjenem zapisniku. Če jih ne vpišeš, ni nič narobe — lestvica jih potrebuje samo, kadar izenačene loči razlika točk.',
      },
    ],
    povezani: ['vnos-rezultata', 'razvrstitev'],
  },
  {
    id: 'popravek-rezultata',
    naslov: 'Popravek rezultata',
    poglavje: 'rezultati',
    kratko:
      'Klikni odigrano tekmo in popravi izid — tudi po zaključku turnirja. Če popravek zamenja zmagovalca, okno pokaže, katere naslednje tekme so prizadete, in te vpraša, kaj naj se zgodi z njimi.',
    kljucne: ['popravi', 'napaka', 'napačen zmagovalec', 'zamenjava zmagovalca', 'popravek'],
    shemaNamiga: 'popravek',
    vsebina: [
      { tip: 'kje', pot: ['Kategorija', 'klik na odigrano tekmo', 'Popravek rezultata'] },
      {
        tip: 'odstavek',
        besedilo:
          'Isti zmagovalec, drugi nizi ali točke: shrani in to je vse — lestvica, mesta in rating se preračunajo sami.',
      },
      {
        tip: 'razdelek',
        id: 'zmagovalec',
        naslov: 'Zamenjan zmagovalec v mreži',
        kratko:
          'Kadar ima naslednja tekma že izid, izbereš: »Izbriši njihove izide« (vneseš znova) ali »Zamenjaj igralca, izidi ostanejo« (igralca si zamenjata mesti po vsej veji).',
        shemaNamiga: 'popravek',
      },
      { tip: 'shema', shema: 'popravek', opis: 'Popravljena tekma in tekme, ki stojijo na njej.' },
      {
        tip: 'tabela',
        glava: ['Izbira', 'Kaj se zgodi', 'Kdaj'],
        vrstice: [
          ['Izbriši njihove izide', 'Naslednje tekme izgubijo izide, pravi igralec gre naprej, izide vpišeš znova.', 'Naslednje tekme je igral napačen igralec.'],
          ['Zamenjaj igralca, izidi ostanejo', 'Igralca si zamenjata mesti v vseh teh tekmah, izidi ostanejo.', 'V dvorani je naprej igral pravi igralec, narobe je bilo samo vpisano.'],
        ],
      },
      {
        tip: 'odstavek',
        besedilo:
          'Če naslednja tekma še nima izida, odločitve ni: v njej se samo zamenja igralec.',
      },
      {
        tip: 'razdelek',
        id: 'skupina',
        naslov: 'Spremenjen vrstni red skupine',
        kratko:
          'Če je iz skupine že nastal izločilni del (ali finalne skupine, žrebi), izbereš: podri naslednji del ali ga pusti, kot je. Okno pove, koliko vnesenih tekem bi se izgubilo.',
      },
      {
        tip: 'seznam',
        postavke: [
          '**Podri izločilni del** — izločilni del izgine (tudi z vnesenimi izidi) in ga narediš znova.',
          '**Izločilni del ostane, kot je** — popravita se tabela in rating, mreža pa se s tabelo ne ujema več.',
        ],
      },
      {
        tip: 'pazi',
        besedilo:
          'Popravek, ki izbriše izide, kategorijo spet odpre. Končna mesta nastanejo ob novem zaključku.',
      },
      { tip: 'razdelek', id: 'liga', naslov: 'Popravek v ligi' },
      {
        tip: 'odstavek',
        besedilo:
          'Ligaško tekmo popraviš v zapisniku srečanja. Zmagovalca lahko zamenjaš, dokler ostane potek srečanja isti (iste tekme odigrane). Napačno postavo popraviš z razveljavitvijo zapisnika. [Razveljavitev](razveljavitev-zapisnika)',
      },
    ],
    povezani: ['vnos-rezultata', 'razveljavitev-zapisnika', 'statusi'],
  },
  {
    id: 'odstop',
    naslov: 'Odstop igralca ali ekipe',
    poglavje: 'rezultati',
    kratko:
      'Igralec, ki med turnirjem odstopi: odigrane tekme obveljajo, vse preostale dobijo nasprotniki brez boja in ne štejejo v rating. Dejanja ni mogoče razveljaviti.',
    kljucne: ['odstop', 'poškodba', 'odšel domov', 'brez boja', 'odstopil'],
    vsebina: [
      { tip: 'kje', pot: ['Kategorija', 'Udeleženci', 'Odstopil'] },
      {
        tip: 'koraki',
        postavke: [
          'Odpri zavihek **Udeleženci**.',
          'Ob igralcu, ki še igra, klikni **Odstopil** (pri ekipi **Odstopila**).',
          'Potrdi z **Potrdi odstop**.',
        ],
      },
      {
        tip: 'seznam',
        postavke: [
          'Že odigrane tekme obveljajo.',
          'Preostale tekme dobijo nasprotniki **brez boja**; v lestvici skupine štejejo kot zmaga nasprotnika, v rating ne gredo.',
          'Pred žrebom ni odstopa — takrat igralca preprosto odjaviš. [Odjava](prijave#odjava)',
        ],
      },
    ],
    povezani: ['vnos-rezultata', 'prijave'],
  },
  {
    id: 'razvrstitev',
    naslov: 'Lestvica skupine in krog',
    poglavje: 'rezultati',
    kratko:
      'Najprej štejejo zmage. Ko sta dva ali več izenačenih, odloča krog: medsebojne zmage, nato razlika nizov in nazadnje razlika točk — samo iz tekem med izenačenimi.',
    kljucne: ['lestvica', 'krog', 'izenačeni', 'tie break', 'razlika nizov', 'medsebojno', 'mesto v skupini'],
    shemaNamiga: 'krog',
    vsebina: [
      { tip: 'shema', shema: 'krog', opis: 'Trije izenačeni z 2 zmagama: odloči razlika nizov med njimi.' },
      {
        tip: 'koraki',
        postavke: [
          'Več zmag je višje.',
          'Pri enakih zmagah: manj porazov.',
          'Še vedno izenačeni tvorijo **krog**. Šteje se samo tekme med njimi: medsebojne zmage, nato razlika nizov, nato razlika točk.',
          'Če krog razbije le del izenačenih, se pravilo ponovi za preostale.',
          'Kjer je še vse enako, odloči jakostni vrstni red (nosilec).',
        ],
      },
      {
        tip: 'nasvet',
        besedilo:
          'Pod lestvico skupine je gumb **Kako je bil izračunan krog?**, kadar je bil krog. Pokaže tabelo meril, ki so odločila — uporabno, ko te igralec vpraša, zakaj je tretji.',
      },
      {
        tip: 'odstavek',
        besedilo:
          'Točke po nizih odločijo samo, kadar je vse drugo enako. Če jih ne vpisuješ, tak krog odloči jakostni vrstni red.',
      },
    ],
    povezani: ['tocke-po-nizih', 'sistemi', 'lestvica-lige'],
  },
  {
    id: 'rating-tekmovanja',
    naslov: 'Turnirko rating in tvoje tekmovanje',
    poglavje: 'rezultati',
    kratko:
      'Rating se preračuna ob vsakem vnosu in popravku. Koliko tekmovanje šteje, določi raven. Dvojice in tekme brez boja ne štejejo. Novinec dobi začetni rating po starosti, rekreativec 800.',
    kljucne: ['rating', 'elo', 'točke', 'novinec', 'rekreativec', 'raven', 'teža'],
    shemaNamiga: 'raven',
    vsebina: [
      { tip: 'shema', shema: 'raven', opis: 'Teža po ravni tekmovanja.' },
      {
        tip: 'seznam',
        postavke: [
          'Teža tekme je odvisna od ravni turnirja ali lige (uradno 100 %, klubsko 75 %, rekreativno 50 %, ne šteje 0). [Raven](turnir-nov#raven)',
          'Zmaga je zmaga: izid v nizih na spremembo ne vpliva.',
          'Dvojice, ekipna tekma kot celota, brez boja in diskvalifikacija ne štejejo. Šteje predaja med igro.',
          'Vsaka sprememba je v dnevniku na profilu igralca, s pojasnilom, kako je nastala.',
        ],
      },
      {
        tip: 'razdelek',
        id: 'novinci',
        naslov: 'Novinci in rekreativci',
        kratko:
          'Novinec začne pri ratingu svoje starosti in spola, po prvem turnirju pa se uvrsti po tem, koga je premagal. Rekreativec (kljukica pri igralcu) začne pri 800.',
      },
      {
        tip: 'odstavek',
        besedilo:
          'Novinec ne začne pri isti številki za vse: 12-letnik in 30-letnik sta različno močna. Po prvem dnevu igranja se uvrsti po rezultatih. Pri rekreativni ligi ali turnirju obkljukaj **Rekreativec** že ob vnosu igralca — sicer bi začetnik začel pri ratingu tekmovalca svojih let in z vsakim porazom izgubljal veliko točk.',
      },
      {
        tip: 'pazi',
        besedilo:
          'Vpisuj tekme po vrsti. Rating teče po času tekem; srečanje, vneseno za nazaj, se sicer preračuna samo, a vrstni red vnosa vpliva na to, kdo velja za novinca.',
      },
    ],
    povezani: ['turnir-nov', 'prijave'],
  },
]
