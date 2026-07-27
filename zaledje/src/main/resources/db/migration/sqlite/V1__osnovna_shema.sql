-- ============================================================================
-- Turnirko: osnovna shema podatkovne baze (razlicica za SQLite)
--
-- Nacela:
--  * Shemo upravlja IZKLJUCNO Flyway; Hibernate je samo bere (ddl-auto=none).
--  * Baza sama varuje pravilnost podatkov (CHECK omejitve, tuji kljuci, UNIQUE).
--  * Vrednosti stanj (statusi, tipi) so zapisane tako, kot se imenujejo
--    Java enum konstante, ker jih Hibernate shranjuje kot besedilo.
--  * Datumi so besedilo v formatu 'yyyy-MM-dd', casi 'yyyy-MM-dd HH:mm:ss.SSS'
--    (glej nastavitve gonilnika v application.properties).
--  * Logicne vrednosti so INTEGER 0/1.
--  * Stolpci "verzija" so namenjeni optimisticnemu zaklepanju (@Version),
--    ki prepreci, da bi si dva socasna vnosa prepisala podatke.
-- ============================================================================

-- ---------------------------------------------------------------------------
-- Sifranti: kraji in klubi
-- ---------------------------------------------------------------------------

CREATE TABLE kraj (
    postna_st   INTEGER PRIMARY KEY,
    ime         TEXT NOT NULL CHECK (length(ime) BETWEEN 2 AND 40)
);

CREATE TABLE klub (
    id          INTEGER PRIMARY KEY,
    ime         TEXT NOT NULL CHECK (length(ime) BETWEEN 2 AND 50),
    -- kratko ime za izpise, npr. "NTK SAV"
    kratica     TEXT CHECK (kratica IS NULL OR length(kratica) BETWEEN 2 AND 10)
);

-- ---------------------------------------------------------------------------
-- Igralec
-- Opomba: rating NI stolpec igralca - zivi v tabeli rating_stanje,
-- ker ima lahko igralec vec ratingov (klubski ELO, kasneje tocke NTZS).
-- Igralcev nikoli ne brisemo (izgubili bi zgodovino tekem) - le arhiviramo.
-- ---------------------------------------------------------------------------

CREATE TABLE igralec (
    id              INTEGER PRIMARY KEY,
    ime             TEXT NOT NULL CHECK (length(trim(ime)) BETWEEN 2 AND 30),
    priimek         TEXT NOT NULL CHECK (length(trim(priimek)) BETWEEN 2 AND 40),
    -- spol je obvezen, ker dolocajo kategorije dogodkov (moski/zenske)
    spol            TEXT NOT NULL CHECK (spol IN ('MOSKI', 'ZENSKI')),
    datum_rojstva   TEXT NOT NULL CHECK (datum_rojstva >= '1900-01-01'),
    email           TEXT UNIQUE CHECK (email IS NULL OR email LIKE '%_@_%._%'),
    telefonska_st   TEXT UNIQUE,
    igralna_roka    TEXT CHECK (igralna_roka IS NULL OR igralna_roka IN ('LEVA', 'DESNA')),
    -- registrska stevilka pri NTZS (ce jo igralec ima)
    ntzs_licenca    TEXT UNIQUE,
    drzavljanstvo   TEXT NOT NULL DEFAULT 'SLO',
    naslov          TEXT,
    postna_st       INTEGER REFERENCES kraj (postna_st),
    id_klub         INTEGER REFERENCES klub (id),
    -- mehko brisanje: arhiviran igralec se ne pojavlja v seznamih za prijavo
    arhiviran       INTEGER NOT NULL DEFAULT 0 CHECK (arhiviran IN (0, 1)),
    ustvarjen_ob    TEXT NOT NULL,
    posodobljen_ob  TEXT
);

CREATE INDEX idx_igralec_priimek_ime ON igralec (priimek, ime);

-- ---------------------------------------------------------------------------
-- Turnir in dogodek
-- Turnir je prireditev (kraj, datumi); en turnir ima vec DOGODKOV -
-- posameznih tekmovanj (npr. "clani posamicno", "kadetinje posamicno").
-- Igralci se prijavljajo na dogodke, ne na turnir.
-- ---------------------------------------------------------------------------

CREATE TABLE turnir (
    id              INTEGER PRIMARY KEY,
    ime             TEXT NOT NULL CHECK (length(trim(ime)) BETWEEN 3 AND 80),
    postna_st       INTEGER REFERENCES kraj (postna_st),
    dvorana         TEXT,
    datum_zacetka   TEXT,
    datum_konca     TEXT,
    -- status doloca streznik, nikoli odjemalec
    status          TEXT NOT NULL CHECK (status IN ('PRIPRAVA', 'V_TEKU', 'ZAKLJUCEN')),
    opombe          TEXT,
    verzija         INTEGER NOT NULL DEFAULT 0,
    ustvarjen_ob    TEXT NOT NULL,
    CHECK (datum_konca IS NULL OR datum_zacetka IS NULL OR datum_konca >= datum_zacetka)
);

CREATE TABLE dogodek (
    id                      INTEGER PRIMARY KEY,
    id_turnir               INTEGER NOT NULL REFERENCES turnir (id),
    -- npr. "Clani posamicno" ali "Kadetinje posamicno"
    ime                     TEXT NOT NULL CHECK (length(trim(ime)) BETWEEN 3 AND 60),
    disciplina              TEXT NOT NULL CHECK (disciplina IN ('POSAMICNO', 'DVOJICE')),
    spol_kategorija         TEXT NOT NULL CHECK (spol_kategorija IN ('MOSKI', 'ZENSKE', 'MESANO')),
    -- prosto besedilo, npr. 'U15', 'CLANI', 'VETERANI 40+'
    starostna_kategorija    TEXT,
    -- IZLOCILNI je edini ze podprt sistem; SKUPINE_IZLOCILNI (format NTZS
    -- po Prilogi A) in KROZNI sta predvidena v nacrtu razvoja
    sistem_tekmovanja       TEXT NOT NULL CHECK (sistem_tekmovanja IN ('IZLOCILNI', 'SKUPINE_IZLOCILNI', 'KROZNI')),
    -- "najboljsi od N nizov" - privzeta vrednost za tekme tega dogodka;
    -- posamezna tekma jo lahko povozi (npr. finale na 7 nizov)
    privzeto_stevilo_nizov  INTEGER NOT NULL CHECK (privzeto_stevilo_nizov IN (3, 5, 7)),
    prijavnina              REAL CHECK (prijavnina IS NULL OR prijavnina >= 0),
    rok_prijave             TEXT,
    status                  TEXT NOT NULL CHECK (status IN ('PRIPRAVA', 'V_TEKU', 'ZAKLJUCEN')),
    verzija                 INTEGER NOT NULL DEFAULT 0,
    ustvarjen_ob            TEXT NOT NULL
);

CREATE INDEX idx_dogodek_turnir ON dogodek (id_turnir);

-- ---------------------------------------------------------------------------
-- Skupina (za skupinski del tekmovanja - sistem SKUPINE_IZLOCILNI)
-- Tabela je pripravljena vnaprej; logika skupin pride v naslednji fazi.
-- ---------------------------------------------------------------------------

CREATE TABLE skupina (
    id          INTEGER PRIMARY KEY,
    id_dogodek  INTEGER NOT NULL REFERENCES dogodek (id),
    -- oznaka skupine: 'A', 'B', 'C'...
    oznaka      TEXT NOT NULL CHECK (length(oznaka) BETWEEN 1 AND 3),
    UNIQUE (id_dogodek, oznaka)
);

-- ---------------------------------------------------------------------------
-- Prijava igralca na dogodek
-- Tekme se sklicujejo na PRIJAVE (ne neposredno na igralce), ker:
--  * prijava hrani "posnetek" kluba in ratinga ob zrebu (zgodovina prestopov),
--  * bo v prihodnosti prijava lahko predstavljala tudi par (dvojice),
--    ne da bi se morala tabela tekem spremeniti.
-- ---------------------------------------------------------------------------

CREATE TABLE prijava (
    id                  INTEGER PRIMARY KEY,
    id_dogodek          INTEGER NOT NULL REFERENCES dogodek (id),
    id_igralec          INTEGER NOT NULL REFERENCES igralec (id),
    -- klub, za katerega je igralec nastopal OB PRIJAVI (posnetek)
    id_klub_ob_prijavi  INTEGER REFERENCES klub (id),
    status              TEXT NOT NULL CHECK (status IN ('PRIJAVLJEN', 'ODJAVLJEN', 'DISKVALIFICIRAN')),
    -- stevilka nosilca pri zrebu (1 = prvi nosilec); NULL = ni nosilec
    st_nosilca          INTEGER CHECK (st_nosilca IS NULL OR st_nosilca >= 1),
    -- posnetek ratinga ob zrebu - za sledljivost in ponovljivost zreba
    rating_ob_zrebu     INTEGER,
    id_skupina          INTEGER REFERENCES skupina (id),
    mesto_v_skupini     INTEGER,
    -- koncna uvrstitev na dogodku (1 = zmagovalec); od nje bodo odvisne tocke
    koncno_mesto        INTEGER CHECK (koncno_mesto IS NULL OR koncno_mesto >= 1),
    placano             INTEGER NOT NULL DEFAULT 0 CHECK (placano IN (0, 1)),
    prijavljen_ob       TEXT NOT NULL,
    verzija             INTEGER NOT NULL DEFAULT 0,
    UNIQUE (id_dogodek, id_igralec)
);

CREATE INDEX idx_prijava_dogodek ON prijava (id_dogodek);

-- ---------------------------------------------------------------------------
-- Tekma
-- Kljucne zasnove:
--  * "kolo" in "pozicija" dolocata mesto v mrezi (kolo 1 = prvo kolo).
--  * Napredovanje NI izracunano s formulo, ampak z EKSPLICITNIMI povezavami:
--    id_izvor_tekma_1/2 + vloga_izvora_1/2 povesta, od kod prideta igralca
--    (npr. "zmagovalec tekme 12" ali - za tekmo za 3. mesto - "porazenec
--    polfinala 1"). To omogoca tolazilne tekme in razigravanja.
--  * izid_tip loci normalno odigrane tekme od posebnih izidov:
--    PROSTO (bye - prost prehod), BREZ_BOJA (w.o.), PREDAJA, DISKVALIFIKACIJA.
--  * dobljeni_nizi_1/2 sta povzetek; tocke posameznih nizov so v tabeli niz.
-- ---------------------------------------------------------------------------

CREATE TABLE tekma (
    id                      INTEGER PRIMARY KEY,
    id_dogodek              INTEGER NOT NULL REFERENCES dogodek (id),
    faza                    TEXT NOT NULL CHECK (faza IN ('SKUPINA', 'GLAVNI', 'TOLAZILNI')),
    id_skupina              INTEGER REFERENCES skupina (id),
    kolo                    INTEGER NOT NULL CHECK (kolo >= 1),
    pozicija                INTEGER NOT NULL CHECK (pozicija >= 1),
    id_prijava_1            INTEGER REFERENCES prijava (id),
    id_prijava_2            INTEGER REFERENCES prijava (id),
    id_izvor_tekma_1        INTEGER REFERENCES tekma (id),
    vloga_izvora_1          TEXT CHECK (vloga_izvora_1 IS NULL OR vloga_izvora_1 IN ('ZMAGOVALEC', 'PORAZENEC')),
    id_izvor_tekma_2        INTEGER REFERENCES tekma (id),
    vloga_izvora_2          TEXT CHECK (vloga_izvora_2 IS NULL OR vloga_izvora_2 IN ('ZMAGOVALEC', 'PORAZENEC')),
    -- "najboljsi od N nizov" za TO tekmo (lahko se razlikuje po kolih)
    stevilo_nizov           INTEGER NOT NULL CHECK (stevilo_nizov IN (3, 5, 7)),
    dobljeni_nizi_1         INTEGER NOT NULL DEFAULT 0 CHECK (dobljeni_nizi_1 >= 0),
    dobljeni_nizi_2         INTEGER NOT NULL DEFAULT 0 CHECK (dobljeni_nizi_2 >= 0),
    id_zmagovalec_prijava   INTEGER REFERENCES prijava (id),
    izid_tip                TEXT CHECK (izid_tip IS NULL OR izid_tip IN ('IGRANO', 'PROSTO', 'BREZ_BOJA', 'PREDAJA', 'DISKVALIFIKACIJA')),
    -- CAKA: se ni obeh igralcev | PRIPRAVLJENA: oba znana | V_IGRI | KONCANA
    status                  TEXT NOT NULL CHECK (status IN ('CAKA', 'PRIPRAVLJENA', 'V_IGRI', 'KONCANA')),
    miza                    INTEGER CHECK (miza IS NULL OR miza >= 1),
    predviden_zacetek       TEXT,
    verzija                 INTEGER NOT NULL DEFAULT 0,
    UNIQUE (id_dogodek, faza, kolo, pozicija),
    -- igralec ne more igrati sam s sabo
    CHECK (id_prijava_1 IS NULL OR id_prijava_2 IS NULL OR id_prijava_1 <> id_prijava_2)
);

CREATE INDEX idx_tekma_dogodek ON tekma (id_dogodek);
CREATE INDEX idx_tekma_izvor_1 ON tekma (id_izvor_tekma_1);
CREATE INDEX idx_tekma_izvor_2 ON tekma (id_izvor_tekma_2);

-- ---------------------------------------------------------------------------
-- Niz (posamezen niz tekme s tockami, npr. 11:7)
-- Vnos tock po nizih je za klubske turnirje neobvezen (dovolj so nizi),
-- za uradne turnirje pa obvezen - to doloca aplikacija, ne shema.
-- ---------------------------------------------------------------------------

CREATE TABLE niz (
    id              INTEGER PRIMARY KEY,
    id_tekma        INTEGER NOT NULL REFERENCES tekma (id) ON DELETE CASCADE,
    zaporedna_st    INTEGER NOT NULL CHECK (zaporedna_st >= 1),
    tocke_1         INTEGER NOT NULL CHECK (tocke_1 >= 0),
    tocke_2         INTEGER NOT NULL CHECK (tocke_2 >= 0),
    UNIQUE (id_tekma, zaporedna_st)
);

-- ---------------------------------------------------------------------------
-- Rating
-- rating_stanje: trenutna vrednost ratinga igralca v danem sistemu.
-- rating_zgodovina: dnevnik VSEH sprememb (append-only) - iz njega je mogoce
--   vsako stanje rekonstruirati, obenem pa je varovalka, da se ista tekma
--   nikoli ne obracuna dvakrat.
-- "sistem" je odprto besedilo: 'KLUBSKI_ELO' zdaj, kasneje npr. 'NTZS_TOCKE'.
-- ---------------------------------------------------------------------------

CREATE TABLE rating_stanje (
    id          INTEGER PRIMARY KEY,
    id_igralec  INTEGER NOT NULL REFERENCES igralec (id),
    sistem      TEXT NOT NULL,
    vrednost    INTEGER NOT NULL,
    st_tekem    INTEGER NOT NULL DEFAULT 0 CHECK (st_tekem >= 0),
    verzija     INTEGER NOT NULL DEFAULT 0,
    UNIQUE (id_igralec, sistem)
);

CREATE TABLE rating_zgodovina (
    id              INTEGER PRIMARY KEY,
    id_igralec      INTEGER NOT NULL REFERENCES igralec (id),
    sistem          TEXT NOT NULL,
    id_tekma        INTEGER REFERENCES tekma (id),
    sprememba       INTEGER NOT NULL,
    nova_vrednost   INTEGER NOT NULL,
    ustvarjen_ob    TEXT NOT NULL
);

CREATE INDEX idx_rating_zgodovina_igralec ON rating_zgodovina (id_igralec);
CREATE INDEX idx_rating_zgodovina_tekma ON rating_zgodovina (id_tekma);
