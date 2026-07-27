-- ============================================================================
-- Turnirko: ligaska (ekipna) tekmovanja med klubi - SNTL in rekreacijske lige
--
-- Nacela (enaka osnovni shemi):
--  * shemo upravlja izkljucno Flyway (ddl-auto=none),
--  * vrednosti stanj so zapisane tako, kot se imenujejo Java enum konstante,
--  * logicne vrednosti so INTEGER 0/1, casi so besedilo.
--
-- Zasnova:
--  * LIGA je sezonsko tekmovanje s prilagodljivo konfiguracijo (format srecanja,
--    stevilo nizov, prag zmag za srecanje, tockovanje, ELO). Lige so lahko
--    povezane (id_visja_liga) za prehode med sezonami.
--  * EKIPA je nastop kluba v ligi; en klub ima lahko vec ekip (Savinja 1, 2).
--  * KADER_EKIPE so igralci, upraviceni nastopati za ekipo.
--  * SRECANJE je dvoboj dveh ekip v enem kolu; sestavlja ga vec POSAMICNIH
--    TEKEM (tekma_srecanja) v vrstnem redu, ki ga doloca format.
--  * POSTAVA_SRECANJA dodeli igralce iz kadra na mesta (A/B/C doma, X/Y/Z gost)
--    in oznaci par za dvojice.
-- ============================================================================

CREATE TABLE liga (
    id                              INTEGER PRIMARY KEY,
    ime                             TEXT NOT NULL CHECK (length(trim(ime)) BETWEEN 3 AND 80),
    -- npr. "2025/26"
    sezona                          TEXT CHECK (sezona IS NULL OR length(sezona) BETWEEN 2 AND 20),
    spol_kategorija                 TEXT NOT NULL CHECK (spol_kategorija IN ('MOSKI', 'ZENSKE', 'MESANO')),
    -- format srecanja doloca stevilo igralcev, dvojice in vrstni red tekem
    format_srecanja                 TEXT NOT NULL CHECK (format_srecanja IN ('SNTL', 'CORBILLON')),
    -- "najboljsi od N nizov" za posamicne tekme lige
    stevilo_nizov                   INTEGER NOT NULL CHECK (stevilo_nizov IN (3, 5, 7)),
    -- prvi do N dobljenih tekem konca srecanje; NULL = odigrajo se vse tekme
    zmag_za_srecanje                INTEGER CHECK (zmag_za_srecanje IS NULL OR zmag_za_srecanje >= 1),
    -- 1 = dvokrozno (doma in v gosteh), 0 = enokrozno
    dvokrozno                       INTEGER NOT NULL DEFAULT 1 CHECK (dvokrozno IN (0, 1)),
    tocke_zmaga                     INTEGER NOT NULL DEFAULT 2 CHECK (tocke_zmaga >= 0),
    tocke_neodloceno                INTEGER NOT NULL DEFAULT 1 CHECK (tocke_neodloceno >= 0),
    tocke_poraz                     INTEGER NOT NULL DEFAULT 0 CHECK (tocke_poraz >= 0),
    dovoljeno_neodloceno            INTEGER NOT NULL DEFAULT 1 CHECK (dovoljeno_neodloceno IN (0, 1)),
    -- prepoved dvojne registracije: igralec sme biti v kadru le ene ekipe v ligi
    prepoved_dvojne_registracije    INTEGER NOT NULL DEFAULT 0 CHECK (prepoved_dvojne_registracije IN (0, 1)),
    -- ali posamicne tekme lige stejejo v klubski ELO (dvojice nikoli)
    steje_v_elo                     INTEGER NOT NULL DEFAULT 1 CHECK (steje_v_elo IN (0, 1)),
    -- povezava na visjo ligo (za prehode ob koncu sezone); NULL = najvisja
    id_visja_liga                   INTEGER REFERENCES liga (id),
    -- koliko ekip napreduje v visjo / izpade v nizjo ligo
    st_napreduje                    INTEGER NOT NULL DEFAULT 0 CHECK (st_napreduje >= 0),
    st_izpade                       INTEGER NOT NULL DEFAULT 0 CHECK (st_izpade >= 0),
    status                          TEXT NOT NULL CHECK (status IN ('PRIPRAVA', 'V_TEKU', 'ZAKLJUCEN')),
    verzija                         INTEGER NOT NULL DEFAULT 0,
    ustvarjen_ob                    TEXT NOT NULL
);

CREATE TABLE ekipa (
    id              INTEGER PRIMARY KEY,
    id_liga         INTEGER NOT NULL REFERENCES liga (id),
    id_klub         INTEGER NOT NULL REFERENCES klub (id),
    -- zaporedna ekipa kluba v ligi (1 = prva mostvo, npr. "Savinja 1")
    zaporedna       INTEGER NOT NULL DEFAULT 1 CHECK (zaporedna >= 1),
    -- prikazano ime; ce NULL, se sestavi iz kluba in zaporedne
    ime             TEXT CHECK (ime IS NULL OR length(trim(ime)) BETWEEN 2 AND 60),
    verzija         INTEGER NOT NULL DEFAULT 0,
    UNIQUE (id_liga, id_klub, zaporedna)
);

CREATE INDEX idx_ekipa_liga ON ekipa (id_liga);

CREATE TABLE kader_ekipe (
    id              INTEGER PRIMARY KEY,
    id_ekipa        INTEGER NOT NULL REFERENCES ekipa (id) ON DELETE CASCADE,
    id_igralec      INTEGER NOT NULL REFERENCES igralec (id),
    -- jakostni vrstni red v ekipi (1 = najboljsi); neobvezno
    vrstni_red      INTEGER CHECK (vrstni_red IS NULL OR vrstni_red >= 1),
    UNIQUE (id_ekipa, id_igralec)
);

CREATE INDEX idx_kader_ekipa ON kader_ekipe (id_ekipa);

CREATE TABLE srecanje (
    id                  INTEGER PRIMARY KEY,
    id_liga             INTEGER NOT NULL REFERENCES liga (id),
    kolo                INTEGER NOT NULL CHECK (kolo >= 1),
    id_ekipa_domaci     INTEGER NOT NULL REFERENCES ekipa (id),
    id_ekipa_gost       INTEGER NOT NULL REFERENCES ekipa (id),
    -- povzetek: dobljene posamicne tekme vsake ekipe (denormaliziran cache)
    dobljene_domaci     INTEGER NOT NULL DEFAULT 0 CHECK (dobljene_domaci >= 0),
    dobljene_gost       INTEGER NOT NULL DEFAULT 0 CHECK (dobljene_gost >= 0),
    -- RAZPORED: samo termin | POTEKA: postave dolocene, tekme generirane | KONCANO
    status              TEXT NOT NULL CHECK (status IN ('RAZPORED', 'POTEKA', 'KONCANO')),
    predviden_zacetek   TEXT,
    odigran_ob          TEXT,
    verzija             INTEGER NOT NULL DEFAULT 0,
    CHECK (id_ekipa_domaci <> id_ekipa_gost)
);

CREATE INDEX idx_srecanje_liga ON srecanje (id_liga);

CREATE TABLE postava_srecanja (
    id              INTEGER PRIMARY KEY,
    id_srecanje     INTEGER NOT NULL REFERENCES srecanje (id) ON DELETE CASCADE,
    stran           TEXT NOT NULL CHECK (stran IN ('DOMACI', 'GOST')),
    -- oznaka mesta: 'A','B','C' za domace, 'X','Y','Z' za goste
    pozicija        TEXT NOT NULL CHECK (length(pozicija) = 1),
    id_igralec      INTEGER NOT NULL REFERENCES igralec (id),
    -- ali je ta igralec v paru za dvojice
    v_dvojici       INTEGER NOT NULL DEFAULT 0 CHECK (v_dvojici IN (0, 1)),
    UNIQUE (id_srecanje, stran, pozicija),
    UNIQUE (id_srecanje, stran, id_igralec)
);

CREATE TABLE tekma_srecanja (
    id                      INTEGER PRIMARY KEY,
    id_srecanje             INTEGER NOT NULL REFERENCES srecanje (id) ON DELETE CASCADE,
    -- vrstni red tekme v srecanju (1 = prva na vrsti)
    zaporedje               INTEGER NOT NULL CHECK (zaporedje >= 1),
    tip                     TEXT NOT NULL CHECK (tip IN ('DVOJICE', 'POSAMICNA')),
    -- oznaka za prikaz, npr. 'dvojice' ali 'A-X'
    oznaka                  TEXT NOT NULL,
    id_igralec_domaci       INTEGER REFERENCES igralec (id),
    -- drugi igralec para (samo pri dvojicah)
    id_igralec_domaci2      INTEGER REFERENCES igralec (id),
    id_igralec_gost         INTEGER REFERENCES igralec (id),
    id_igralec_gost2        INTEGER REFERENCES igralec (id),
    stevilo_nizov           INTEGER NOT NULL CHECK (stevilo_nizov IN (3, 5, 7)),
    dobljeni_nizi_domaci    INTEGER NOT NULL DEFAULT 0 CHECK (dobljeni_nizi_domaci >= 0),
    dobljeni_nizi_gost      INTEGER NOT NULL DEFAULT 0 CHECK (dobljeni_nizi_gost >= 0),
    -- zmagovalna stran tekme: 'DOMACI' / 'GOST' / NULL (se ni odigrana)
    zmagovalec_stran        TEXT CHECK (zmagovalec_stran IS NULL OR zmagovalec_stran IN ('DOMACI', 'GOST')),
    izid_tip                TEXT CHECK (izid_tip IS NULL OR izid_tip IN ('IGRANO', 'BREZ_BOJA', 'PREDAJA', 'DISKVALIFIKACIJA')),
    -- CAKA: se ni vnesena | KONCANA: rezultat vnesen | NEODIGRANA: srecanje ze odloceno
    status                  TEXT NOT NULL CHECK (status IN ('CAKA', 'KONCANA', 'NEODIGRANA')),
    verzija                 INTEGER NOT NULL DEFAULT 0,
    UNIQUE (id_srecanje, zaporedje)
);

CREATE INDEX idx_tekma_srecanja_srecanje ON tekma_srecanja (id_srecanje);

-- Razsiritev rating dnevnika: posamicne tekme lige stejejo v isti klubski ELO
-- kot turnirske. Zapis se veze bodisi na turnirsko tekmo (id_tekma) bodisi na
-- ligasko posamicno tekmo (id_tekma_srecanja) - nikoli na obe hkrati.
ALTER TABLE rating_zgodovina ADD COLUMN id_tekma_srecanja INTEGER REFERENCES tekma_srecanja (id);

CREATE INDEX idx_rating_zgodovina_tekma_srecanja ON rating_zgodovina (id_tekma_srecanja);
