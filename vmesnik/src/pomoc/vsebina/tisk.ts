import type { Clanek } from '../tipi'

export const TISK: Clanek[] = [
  {
    id: 'listki',
    naslov: 'Listki za tiskanje (sodniški listki)',
    poglavje: 'tisk',
    kratko:
      'Natisne sodniške listke pripravljenih tekem, šest na list A4. Privzeto so izbrani vsi še nenatisnjeni; izbereš lahko sklop (skupino, žreb) ali posamezne tekme. Po tisku potrdiš, da so se natisnili.',
    kljucne: [
      'listki',
      'listek',
      'sodniški listek',
      'natisni',
      'tiskanje',
      'prazni listki',
      'natisnjeno',
      'obojestransko',
      'hrbtna stran',
    ],
    shemaNamiga: 'listki-a4',
    vsebina: [
      { tip: 'kje', pot: ['Kategorija', 'Listki za tiskanje'] },
      { tip: 'shema', shema: 'listki-a4', opis: 'Šest listkov na A4 brez robov; črtkane črte so za rez.' },
      {
        tip: 'koraki',
        postavke: [
          'Odpri **Listki za tiskanje** (vidno, ko kategorija teče).',
          'V **Kaj natisniti** izberi: vsi še nenatisnjeni, en sklop (»Skupina A«, »Glavni žreb«, »Tekma za 3. mesto«) ali posamezne tekme s kljukicami.',
          'Preveri predogled spodaj — natanko to gre na papir.',
          'Klikni **Natisni izbrane**. Po tisku potrdi, da so listki res prišli iz tiskalnika.',
        ],
      },
      {
        tip: 'razdelek',
        id: 'natisnjeno',
        naslov: 'Sled natisnjenih',
        kratko:
          'Potrjene tekme dobijo oznako »natisnjeno« in so v izbiri skrite. Tako naslednjič natisneš samo nove tekme. Oznako odstraniš z »Odznači«.',
      },
      {
        tip: 'odstavek',
        besedilo:
          'Oznaka se zapiše šele po tvoji potrditvi, ne ob kliku — če se je papir zataknil, odgovori »Ne« in tekme ostanejo nenatisnjene. Že natisnjene vidiš s **Pokaži tudi že natisnjene**; z izrecno kljukico natisneš listek znova.',
      },
      {
        tip: 'razdelek',
        id: 'prazni',
        naslov: 'Prazni listki',
        kratko: 'Listki z natisnjenim turnirjem in kategorijo, imena in faza so črte za ročni vpis. Število izbereš (1–120).',
      },
      {
        tip: 'odstavek',
        besedilo:
          'Uporabni za tekme, ki jih na hitro doda sodnik, ali kot rezerva. Prazni listki se ne beležijo kot natisnjeni.',
      },
      {
        tip: 'razdelek',
        id: 'hrbet',
        naslov: 'Hrbtna stran s kodo za sodnike',
        kratko:
          'Ko na strani Branje listkov vklopiš **Sodniki slikajo listke**, ima vsak list hrbtno stran: na vsakem listku koda, ki sodniku odpre stran za pošiljanje izida s telefonom.',
      },
      {
        tip: 'odstavek',
        besedilo:
          'Natisni **obojestransko** (na obe strani lista); vseeno je, ali tiskalnik list obrne po dolgem ali po kratkem robu. Če tiskalnik obojestransko ne zna, odkljukaj **Na hrbtno stran lista natisni kodo za sodnike** — sodnik nato listek prinese na pult. Prazni listki hrbta nimajo, ker tekme še ne poznajo.',
      },
      {
        tip: 'nasvet',
        besedilo:
          'Pri ekipnem dogodku turnirja je namesto listkov **Zapisniki za tiskanje** — uradni ekipni zapisnik za vsako ekipno tekmo, z isto izbiro in sledjo.',
      },
      {
        tip: 'pazi',
        besedilo:
          'V oknu za tisk mora biti papir A4 in merilo 100 % (ne »Prilagodi strani«). Pri drugem merilu se listki zamaknejo glede na črte za rez.',
      },
    ],
    povezani: ['zreb-skupin-tisk', 'vodic-dan-turnirja', 'branje-listkov'],
  },
  {
    id: 'zreb-skupin-tisk',
    naslov: 'Žreb skupin za tiskanje',
    poglavje: 'tisk',
    kratko:
      'Za vrata dvorane: vsaka skupina kot listek s križno tabelo (vsak z vsakim) in razporedom tekem pod njo. Na strani turnirja natisne skupine vseh kategorij hkrati.',
    kljucne: ['žreb skupin', 'skupine tisk', 'oglasna deska', 'vrata', 'križna tabela'],
    shemaNamiga: 'skupine-a4',
    vsebina: [
      { tip: 'kje', pot: ['Kategorija ali Turnir', 'Žreb skupin za tiskanje'] },
      { tip: 'shema', shema: 'skupine-a4', opis: 'Skupina: križna tabela in razpored, list A4 v šestinah.' },
      {
        tip: 'seznam',
        postavke: [
          'Izbirnik **Pod tabelo** določi, ali je pod tabelo razpored z okenci za nize ali samo pari po kolih.',
          'Velika skupina se nadaljuje na naslednjem listku.',
          'Odigrane tekme se izpišejo v tabeli (»3/1«), zato je list uporaben tudi med igro.',
          'S strani turnirja natisneš skupine vseh kategorij hkrati — vsak listek nosi ime kategorije.',
        ],
      },
    ],
    povezani: ['zreb', 'zreb-mreze-tisk', 'listki'],
  },
  {
    id: 'zreb-mreze-tisk',
    naslov: 'Izločilni žreb za tiskanje',
    poglavje: 'tisk',
    kratko:
      'Izločilna mreža na ležečem A4 za vrata dvorane. Do 32 igralcev gre na en list, večja mreža se razreže. Prazna mesta ostanejo črte za ročni vpis.',
    kljucne: ['mreža tisk', 'izločilni žreb', 'bracket', 'vrata dvorane'],
    shemaNamiga: 'mreza-a4',
    vsebina: [
      { tip: 'kje', pot: ['Kategorija ali Turnir', 'Izločilni žreb za tiskanje'] },
      { tip: 'shema', shema: 'mreza-a4', opis: 'Mreža na ležečem A4.' },
      {
        tip: 'odstavek',
        besedilo:
          'List je tudi delovni obrazec: kjer igralec še ni znan, je črta, na katero sodnik vpiše zmagovalca. Ko vpišeš rezultate v aplikacijo, je novi natis že izpolnjen.',
      },
    ],
    povezani: ['izlocilni-del', 'zreb-skupin-tisk'],
  },
  {
    id: 'izpis-rezultatov',
    naslov: 'Natisni rezultate kategorije',
    poglavje: 'tisk',
    kratko:
      'Ko je kategorija zaključena: končna razvrstitev, skupine in mreža na listih A4, posamezno ali v enem dokumentu. Vsak list nosi turnir, kategorijo, datum, logotip in pokrovitelje.',
    kljucne: ['izpis', 'rezultati', 'razvrstitev', 'natisni rezultate', 'poročilo', 'PDF', 'SVR'],
    shemaNamiga: 'izpis',
    vsebina: [
      { tip: 'kje', pot: ['Kategorija (zaključena)', 'Natisni rezultate'] },
      { tip: 'shema', shema: 'izpis', opis: 'Izpis v treh delih: razvrstitev, skupine, mreža.' },
      {
        tip: 'koraki',
        postavke: [
          'Obkljukaj, kaj želiš: razvrstitev, skupine, izločilni del.',
          'Stikalo **Točke po nizih** doda točke k tekmam (le, kjer so vpisane).',
          'Natisni ali shrani kot PDF (v oknu za tisk izberi »Shrani kot PDF«).',
        ],
      },
      {
        tip: 'razdelek',
        id: 'svr',
        naslov: 'Slog SVR (SV regija)',
        kratko: 'Pri sistemu SV regija izbereš še slog: Turnirko ali SVR — pokončni A4 kot Excelovi listi regije.',
      },
      {
        tip: 'odstavek',
        besedilo:
          'Slog SVR izpiše skupine in žrebe za vsa mesta tako, kot jih je regija doslej objavljala. Kljukica **dodatno oblikovanje** doda poudarke.',
      },
      {
        tip: 'nasvet',
        besedilo:
          'Razvrstitev gre na en list, največ dva (okoli 80 udeležencev na list). Logotip in pokrovitelje turnirja nastaviš na strani turnirja. [Logotip](logotip)',
      },
    ],
    povezani: ['zakljucek-turnirja', 'logotip', 'pokrovitelji'],
  },
  {
    id: 'zapisnik-tisk',
    naslov: 'Ekipni zapisnik za tisk',
    poglavje: 'tisk',
    kratko:
      'Zapisnik ekipnega srečanja natisneš pred tekmo (prazen obrazec z imeni ekip) ali po postavi. Izbereš uradni zapisnik NTZS ali Turnirkov ležeči zapisnik.',
    kljucne: ['zapisnik', 'ekipni zapisnik', 'NTZS', 'obrazec', 'SNTL', 'ležeči'],
    shemaNamiga: 'zapisnik-oblike',
    vsebina: [
      { tip: 'kje', pot: ['Srečanje', 'Zapisnik za tisk'] },
      { tip: 'shema', shema: 'zapisnik-oblike', opis: 'Dve obliki: uradni pokončni NTZS in Turnirkov ležeči.' },
      {
        tip: 'tabela',
        glava: ['Oblika', 'Kdaj'],
        vrstice: [
          ['Uradni zapisnik NTZS', 'Lige SNTL in uradna tekmovanja. Varianta 1. ali 2./3. SNTL (predloga v pravilih lige).'],
          ['Turnirkov zapisnik (ležeči A4)', 'Regionalne lige; z znamko Turnirka in logotipom lige, ime ekipe veliko.'],
        ],
      },
      {
        tip: 'odstavek',
        besedilo:
          'Pred postavo se natisne prazen obrazec — ekipi vpišeta postavo na papir. Po postavi so imena že natisnjena. Ko srečanje konča, gumba ni več.',
      },
    ],
    povezani: ['zapisnik-srecanja', 'liga-nova'],
  },
]
