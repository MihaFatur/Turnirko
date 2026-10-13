-- ============================================================================
-- Turnirko: format srecanja DSI (DS-i: "prva ekipa do dveh zmag")
--
-- Dva igralca na stran, tekme si sledijo A-X, B-Y, nato dvojice; dvojice
-- odlocajo samo pri 1 : 1 (pri 2 : 0 srecanje konca ze po dveh posamicnih).
-- Razpored zivi v kodi (FormatSrecanja.razpored()), baza hrani samo ime
-- formata - edina sprememba sheme je razsiritev CHECK omejitev stolpcev
-- liga.format_srecanja in dogodek.format_srecanja.
--
-- SQLite omejitve CHECK ne zna spremeniti, zato tabeli prezidamo po istem
-- postopku kot V13 in V42 (glej .conf ob tej datoteki): izven transakcije in z
-- zacasno izklopljenimi tujimi kljuci, ker nanju kazejo ekipe, srecanja,
-- prijave, tekme in zreb. Shema je prepisana iz stanja po V45 (preverjeno na
-- bazi, ne samo iz migracij), vsi stolpci pa se kopirajo po imenu.
-- ============================================================================

PRAGMA foreign_keys = OFF;

-- ---------------------------------------------------------------------------
-- liga: nov format DSI v CHECK; prehodi, kvalifikacije in ure ostanejo
-- ---------------------------------------------------------------------------

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
                                         'SNTL_DVOJICE_SEDMA', 'SNTL_PRVA_DVOJICE_CETRTA',
                                         'DSI')),
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
    -- enakomerna razvrstitev (V16), lige na domaci strani (V17)
    enakomerna_razvrstitev          INTEGER NOT NULL DEFAULT 0 CHECK (enakomerna_razvrstitev IN (0, 1)),
    na_domaci                       INTEGER NOT NULL DEFAULT 0 CHECK (na_domaci IN (0, 1)),
    -- raven tekmovanja (V20)
    raven                           TEXT NOT NULL DEFAULT 'KLUBSKO'
                                        CHECK (raven IN ('URADNO', 'KLUBSKO', 'REKREATIVNO', 'NE_STEJE')),
    -- rocni razpored (V26), vir uvoza (V27)
    rocni_zreb                      INTEGER NOT NULL DEFAULT 0 CHECK (rocni_zreb IN (0, 1)),
    vir                             TEXT CHECK (vir IS NULL OR vir IN ('STUPA', 'STARA_NTZS')),
    -- koncnica (V28), odbitek za poraz brez borbe (V29), ure kola (V31)
    koncnica_ekip                   INTEGER CHECK (koncnica_ekip IS NULL OR koncnica_ekip IN (2, 4, 8)),
    koncnica_zmag                   INTEGER,
    odbitek_brez_boja               INTEGER NOT NULL DEFAULT 0 CHECK (odbitek_brez_boja BETWEEN 0 AND 5),
    ure_srecanj                     TEXT,
    -- kvalifikacije med ligami (V41)
    st_kvalifikacije_gor            INTEGER NOT NULL DEFAULT 0 CHECK (st_kvalifikacije_gor >= 0),
    st_kvalifikacije_dol            INTEGER NOT NULL DEFAULT 0 CHECK (st_kvalifikacije_dol >= 0),
    id_kvalifikacije_visja          INTEGER REFERENCES liga (id),
    id_kvalifikacije_nizja          INTEGER REFERENCES liga (id),
    CHECK ((koncnica_zmag IS NULL AND koncnica_ekip IS NULL)
           OR (koncnica_zmag BETWEEN 1 AND 4 AND koncnica_ekip IS NOT NULL)),
    CHECK ((id_kvalifikacije_nizja IS NULL) = (id_kvalifikacije_visja IS NULL)
           AND (id_kvalifikacije_nizja IS NULL OR id_kvalifikacije_nizja <> id_kvalifikacije_visja)),
    -- liga ne more biti sama sebi nadrejena (daljsih krogov baza ne vidi -
    -- te prepreci LigaStoritev.nastaviPrehode)
    CHECK (id_visja_liga IS NULL OR id_visja_liga <> id)
);

INSERT INTO liga_nov (id, ime, sezona, spol_kategorija, format_srecanja, stevilo_nizov,
                      zmag_za_srecanje, dvokrozno, tocke_zmaga, tocke_neodloceno, tocke_poraz,
                      dovoljeno_neodloceno, prepoved_dvojne_registracije,
                      id_visja_liga, st_napreduje, st_izpade, status, predloga_listka,
                      zacetek_prvega_kola, razmik_dni, id_ustvaril, id_klub_lastnik,
                      verzija, ustvarjen_ob, enakomerna_razvrstitev, na_domaci, raven,
                      rocni_zreb, vir, koncnica_ekip, koncnica_zmag, odbitek_brez_boja,
                      ure_srecanj, st_kvalifikacije_gor, st_kvalifikacije_dol,
                      id_kvalifikacije_visja, id_kvalifikacije_nizja)
SELECT id, ime, sezona, spol_kategorija, format_srecanja, stevilo_nizov,
       zmag_za_srecanje, dvokrozno, tocke_zmaga, tocke_neodloceno, tocke_poraz,
       dovoljeno_neodloceno, prepoved_dvojne_registracije,
       id_visja_liga, st_napreduje, st_izpade, status, predloga_listka,
       zacetek_prvega_kola, razmik_dni, id_ustvaril, id_klub_lastnik,
       verzija, ustvarjen_ob, enakomerna_razvrstitev, na_domaci, raven,
       rocni_zreb, vir, koncnica_ekip, koncnica_zmag, odbitek_brez_boja,
       ure_srecanj, st_kvalifikacije_gor, st_kvalifikacije_dol,
       id_kvalifikacije_visja, id_kvalifikacije_nizja
FROM liga;

DROP TABLE liga;
ALTER TABLE liga_nov RENAME TO liga;

-- indeksi so padli skupaj s staro tabelo
CREATE INDEX idx_liga_klub_lastnik ON liga (id_klub_lastnik);
CREATE INDEX idx_liga_visja ON liga (id_visja_liga);
CREATE INDEX idx_liga_kvalifikacije ON liga (id_kvalifikacije_visja, id_kvalifikacije_nizja);

-- ---------------------------------------------------------------------------
-- dogodek: nov format DSI v CHECK (ekipni dogodek turnirja)
-- ---------------------------------------------------------------------------

CREATE TABLE dogodek_nov (
    id                      INTEGER PRIMARY KEY,
    id_turnir               INTEGER NOT NULL REFERENCES turnir (id),
    -- npr. "Clani posamicno", "Clani dvojice", "U15 ekipno"
    ime                     TEXT NOT NULL CHECK (length(trim(ime)) BETWEEN 3 AND 60),
    disciplina              TEXT NOT NULL CHECK (disciplina IN ('POSAMICNO', 'DVOJICE', 'EKIPNO')),
    spol_kategorija         TEXT NOT NULL CHECK (spol_kategorija IN ('MOSKI', 'ZENSKE', 'MESANO', 'KDORKOLI')),
    -- prosto besedilo, npr. 'U15', 'CLANI', 'VETERANI 40+'
    starostna_kategorija    TEXT,
    sistem_tekmovanja       TEXT NOT NULL CHECK (sistem_tekmovanja IN ('IZLOCILNI', 'SKUPINE_IZLOCILNI',
                                                                     'KROZNI', 'SKUPINE', 'SKUPINE_ZA_MESTA',
                                                                     'SV_REGIJA')),
    -- "najboljsi od N nizov" - privzeta vrednost za tekme tega dogodka (pri
    -- ekipnem dogodku za posamicne tekme srecanja)
    privzeto_stevilo_nizov  INTEGER NOT NULL CHECK (privzeto_stevilo_nizov IN (3, 5, 7)),
    prijavnina              REAL CHECK (prijavnina IS NULL OR prijavnina >= 0),
    rok_prijave             TEXT,
    status                  TEXT NOT NULL CHECK (status IN ('PRIPRAVA', 'V_TEKU', 'ZAKLJUCEN')),
    -- Nastavitvi skupinskega dela. Pri sistemu SKUPINE obvezni (format TOP),
    -- pri SKUPINE_ZA_MESTA je stevilo skupin neobvezno (brez njega ga doloci zreb),
    -- pri SV_REGIJA sta to skupine na poln nivo in igralci v skupini (privzeto 4 in 4).
    -- Zgornja meja 26 je posledica oznak skupin A..Z.
    stevilo_skupin          INTEGER CHECK (stevilo_skupin IS NULL OR stevilo_skupin BETWEEN 1 AND 26),
    velikost_skupine        INTEGER CHECK (velikost_skupine IS NULL OR velikost_skupine BETWEEN 2 AND 24),
    -- sistem SV_REGIJA: stevilo nivojev in rocna razdelitev po nivojih
    stevilo_nivojev         INTEGER CHECK (stevilo_nivojev IS NULL OR stevilo_nivojev BETWEEN 1 AND 9),
    velikosti_nivojev       TEXT CHECK (velikosti_nivojev IS NULL OR length(velikosti_nivojev) <= 80),
    rangov_v_zreb           TEXT CHECK (rangov_v_zreb IS NULL OR length(rangov_v_zreb) <= 40),
    -- ekipni dogodek: format srecanja, prag zmag za srecanje (NULL = vse tekme)
    format_srecanja         TEXT CHECK (format_srecanja IS NULL OR format_srecanja IN
                                        ('SNTL', 'CORBILLON', 'SAVINJA', 'SNTL_PRVA',
                                         'SNTL_BREZ_DVOJIC', 'OLIMPIJSKI',
                                         'SNTL_DVOJICE_SEDMA', 'SNTL_PRVA_DVOJICE_CETRTA',
                                         'EKIPNI_DP', 'POKAL_NTZS', 'DSI')),
    zmag_za_srecanje        INTEGER CHECK (zmag_za_srecanje IS NULL OR zmag_za_srecanje >= 1),
    -- izlocilna mreza s tekmo za 3. mesto (porazenca polfinala)
    tekma_za_tretje_mesto   INTEGER NOT NULL DEFAULT 0 CHECK (tekma_za_tretje_mesto IN (0, 1)),
    verzija                 INTEGER NOT NULL DEFAULT 0,
    ustvarjen_ob            TEXT NOT NULL,
    CHECK (sistem_tekmovanja <> 'SKUPINE'
           OR (stevilo_skupin IS NOT NULL AND velikost_skupine IS NOT NULL)),
    -- dvojice igrajo izkljucno izlocilno mrezo: krozni sistem in skupine bi
    -- za pare potrebovala se lestvice parov, ki jih ni
    CHECK (disciplina <> 'DVOJICE' OR sistem_tekmovanja = 'IZLOCILNI'),
    -- "strogo mesano" je lastnost PARA, zato pri posamicnem in ekipnem dogodku
    -- ni izbira; tam je odprta kategorija KDORKOLI
    CHECK (spol_kategorija <> 'MESANO' OR disciplina = 'DVOJICE'),
    -- ekipni dogodek brez formata ne ve, kako se igra srecanje; drugi dogodki
    -- srecanj nimajo
    CHECK ((disciplina = 'EKIPNO') = (format_srecanja IS NOT NULL)),
    CHECK (zmag_za_srecanje IS NULL OR disciplina = 'EKIPNO'),
    -- tekma za 3. mesto je del izlocilne mreze
    CHECK (tekma_za_tretje_mesto = 0 OR sistem_tekmovanja IN ('IZLOCILNI', 'SKUPINE_IZLOCILNI')),
    -- nivoji so lastnost sistema SV_REGIJA
    CHECK ((stevilo_nivojev IS NULL AND velikosti_nivojev IS NULL AND rangov_v_zreb IS NULL)
           OR sistem_tekmovanja = 'SV_REGIJA')
);

INSERT INTO dogodek_nov (id, id_turnir, ime, disciplina, spol_kategorija,
                         starostna_kategorija, sistem_tekmovanja, privzeto_stevilo_nizov,
                         prijavnina, rok_prijave, status, stevilo_skupin, velikost_skupine,
                         stevilo_nivojev, velikosti_nivojev, rangov_v_zreb,
                         format_srecanja, zmag_za_srecanje, tekma_za_tretje_mesto,
                         verzija, ustvarjen_ob)
SELECT id, id_turnir, ime, disciplina, spol_kategorija,
       starostna_kategorija, sistem_tekmovanja, privzeto_stevilo_nizov,
       prijavnina, rok_prijave, status, stevilo_skupin, velikost_skupine,
       stevilo_nivojev, velikosti_nivojev, rangov_v_zreb,
       format_srecanja, zmag_za_srecanje, tekma_za_tretje_mesto,
       verzija, ustvarjen_ob
FROM dogodek;

DROP TABLE dogodek;
ALTER TABLE dogodek_nov RENAME TO dogodek;

CREATE INDEX idx_dogodek_turnir ON dogodek (id_turnir);

-- Preveri, da prezidava ni pustila osirotelih vrstic (tuji kljuci so bili
-- izklopljeni, zato tega ni preverjal nihce drug).
PRAGMA foreign_key_check;

PRAGMA foreign_keys = ON;
