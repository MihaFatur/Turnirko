-- ============================================================================
-- Turnirko: formati srecanja OLIMPIJSKI, SNTL_DVOJICE_SEDMA in
--           SNTL_PRVA_DVOJICE_CETRTA
--
-- Zgodovina NTZS (sezone 2012/13-2023/24) pokaze tri razporede, ki jih shema
-- doslej ni poznala:
--
--   OLIMPIJSKI - A-X, B-Y, C-Z, A-Y, B-X
--     Ekipni drzavni prvenstvi mladincev in kadetov (EDPM, EDPK): trije
--     igralci na strani, brez dvojic, najvec pet tekem, prvi do treh zmag.
--     Od SNTL_BREZ_DVOJIC se loci ze pri cetrti tekmi (A-Y namesto B-X) in po
--     dolzini, zato ga ni mogoce zapisati kot skrajsan SNTL.
--
--   SNTL_DVOJICE_SEDMA - A-X, B-Y, C-Z, B-X, A-Z, C-Y, dvojice, B-Z, C-X, A-Y
--   SNTL_PRVA_DVOJICE_CETRTA - B-X, A-Z, C-Y, dvojice, B-Z, C-X, A-Y
--     Ista razporeda kot SNTL in SNTL_PRVA, le da dvojice ne odprejo
--     srecanja. Tako je SNTL igral sezono 2022/23 in samo njo: prej dvojic
--     sploh ni bilo (SNTL_BREZ_DVOJIC), od 2023/24 naprej so spet prve.
--
-- Razporedi zivijo v kodi
-- (FormatSrecanja.razpored()), baza hrani samo ime formata - edina sprememba
-- sheme je torej razsiritev CHECK omejitve stolpca liga.format_srecanja.
--
-- SQLite omejitve CHECK ne zna spremeniti, zato tabelo prezidamo po istem
-- postopku kot V9 in V12 (glej .conf ob tej datoteki): izven transakcije in z
-- zacasno izklopljenimi tujimi kljuci, ker nanjo kazejo ekipa, srecanje,
-- spremljana_liga in liga sama prek id_visja_liga.
--
-- Tabela je tu prepisana v stanju po V12.
-- ============================================================================

PRAGMA foreign_keys = OFF;

CREATE TABLE liga_nov (
    id                              INTEGER PRIMARY KEY,
    ime                             TEXT NOT NULL CHECK (length(trim(ime)) BETWEEN 3 AND 80),
    -- npr. "2025/26"
    sezona                          TEXT CHECK (sezona IS NULL OR length(sezona) BETWEEN 2 AND 20),
    spol_kategorija                 TEXT NOT NULL CHECK (spol_kategorija IN ('MOSKI', 'ZENSKE', 'MESANO')),
    -- format srecanja doloca stevilo igralcev, dvojice in vrstni red tekem
    format_srecanja                 TEXT NOT NULL CHECK (format_srecanja IN
                                        ('SNTL', 'CORBILLON', 'SAVINJA', 'SNTL_PRVA',
                                         'SNTL_BREZ_DVOJIC', 'OLIMPIJSKI',
                                         'SNTL_DVOJICE_SEDMA', 'SNTL_PRVA_DVOJICE_CETRTA')),
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
    -- predloga uradnega ekipnega zapisnika za natis (V7)
    predloga_listka                 TEXT NOT NULL DEFAULT 'SNTL_23' CHECK (predloga_listka IN ('SNTL_1', 'SNTL_23')),
    -- seme terminov kol (V10)
    zacetek_prvega_kola             TEXT,
    razmik_dni                      INTEGER CHECK (razmik_dni IS NULL OR razmik_dni BETWEEN 1 AND 365),
    -- lastnistvo (V6): racun, ki je ligo ustvaril, in posnetek njegovega kluba
    id_ustvaril                     INTEGER REFERENCES uporabnik (id),
    id_klub_lastnik                 INTEGER REFERENCES klub (id),
    verzija                         INTEGER NOT NULL DEFAULT 0,
    ustvarjen_ob                    TEXT NOT NULL,
    -- liga ne more biti sama sebi nadrejena (daljsih krogov baza ne vidi -
    -- te prepreci LigaStoritev.nastaviPrehode)
    CHECK (id_visja_liga IS NULL OR id_visja_liga <> id)
);

INSERT INTO liga_nov (id, ime, sezona, spol_kategorija, format_srecanja, stevilo_nizov,
                      zmag_za_srecanje, dvokrozno, tocke_zmaga, tocke_neodloceno, tocke_poraz,
                      dovoljeno_neodloceno, prepoved_dvojne_registracije, steje_v_elo,
                      id_visja_liga, st_napreduje, st_izpade, status, predloga_listka,
                      zacetek_prvega_kola, razmik_dni,
                      id_ustvaril, id_klub_lastnik, verzija, ustvarjen_ob)
SELECT id, ime, sezona, spol_kategorija, format_srecanja, stevilo_nizov,
       zmag_za_srecanje, dvokrozno, tocke_zmaga, tocke_neodloceno, tocke_poraz,
       dovoljeno_neodloceno, prepoved_dvojne_registracije, steje_v_elo,
       id_visja_liga, st_napreduje, st_izpade, status, predloga_listka,
       zacetek_prvega_kola, razmik_dni,
       id_ustvaril, id_klub_lastnik, verzija, ustvarjen_ob
FROM liga;

DROP TABLE liga;
ALTER TABLE liga_nov RENAME TO liga;

-- indeksa sta padla skupaj s staro tabelo
CREATE INDEX idx_liga_klub_lastnik ON liga (id_klub_lastnik);
CREATE INDEX idx_liga_visja ON liga (id_visja_liga);

-- Preveri, da prezidava ni pustila osirotelih vrstic (tuji kljuci so bili
-- izklopljeni, zato tega ni preverjal nihce drug).
PRAGMA foreign_key_check;

PRAGMA foreign_keys = ON;
