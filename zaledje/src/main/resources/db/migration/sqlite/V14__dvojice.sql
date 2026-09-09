-- ============================================================================
-- Turnirko: dvojice (disciplina DVOJICE)
--
-- Dvojice se igrajo po sistemu takojsnjega izpadanja - torej po ISTI izlocilni
-- mrezi kot posamicno tekmovanje. Zato dvojice NISO nov sistem tekmovanja:
-- nov je DISCIPLINA dogodka, sistem ostane IZLOCILNI.
--
-- Tekmovalna enota je PRIJAVA (tekma se sklicuje nanjo ze od V1 prav zato,
-- "da bo prijava lahko predstavljala tudi par"). Prijava zato dobi drugega
-- igralca: pri posamicnem dogodku je prazen, pri dvojicah pa je prijava brez
-- njega samo se PRIJAVLJEN IGRALEC BREZ PARA, ki ga organizator pred zrebom
-- poveze s soigralcem. Ker je par ena vrstica, tabele tekma ni treba
-- spreminjati in ista koda zene mrezo, napredovanje in vnos rezultatov.
--
-- Spolne kategorije se razdelijo na stiri:
--   MOSKI, ZENSKE - kot doslej,
--   MESANO        - STROGO mesan par (moski + zenska); smiselno samo pri
--                   dvojicah, zato ga CHECK omeji nanje,
--   KDORKOLI      - nastopi lahko vsak (to je doslej pomenil MESANO).
-- Obstojeci dogodki z MESANO se zato prepisejo v KDORKOLI - njihov pomen se
-- ne sme spremeniti za nazaj. Tabele liga se to NE dotakne: tam "mesano"
-- pomeni ligo, v kateri igrajo oboji, in ostane, kar je bilo.
--
-- Obe tabeli je treba prezidati, ker SQLite ne zna spremeniti omejitve CHECK
-- niti dodati stolpca z omejitvijo. Nanju kazejo tuji kljuci (prijava, skupina
-- in tekma na dogodek; tekma na prijavo), zato migracija tece izven
-- transakcije z zacasno izklopljenimi tujimi kljuci - isti postopek kot V5.
-- Glej V14__dvojice.sql.conf (executeInTransaction=false).
-- ============================================================================

PRAGMA foreign_keys = OFF;

-- ---------------------------------------------------------------------------
-- Dogodek: disciplina DVOJICE je odslej dovoljena (le z izlocilnim sistemom)
--          in spolne kategorije so stiri
-- ---------------------------------------------------------------------------

CREATE TABLE dogodek_nov (
    id                      INTEGER PRIMARY KEY,
    id_turnir               INTEGER NOT NULL REFERENCES turnir (id),
    -- npr. "Clani posamicno" ali "Clani dvojice"
    ime                     TEXT NOT NULL CHECK (length(trim(ime)) BETWEEN 3 AND 60),
    disciplina              TEXT NOT NULL CHECK (disciplina IN ('POSAMICNO', 'DVOJICE')),
    spol_kategorija         TEXT NOT NULL CHECK (spol_kategorija IN ('MOSKI', 'ZENSKE', 'MESANO', 'KDORKOLI')),
    -- prosto besedilo, npr. 'U15', 'CLANI', 'VETERANI 40+'
    starostna_kategorija    TEXT,
    sistem_tekmovanja       TEXT NOT NULL CHECK (sistem_tekmovanja IN ('IZLOCILNI', 'SKUPINE_IZLOCILNI', 'KROZNI', 'SKUPINE')),
    -- "najboljsi od N nizov" - privzeta vrednost za tekme tega dogodka;
    -- posamezna tekma jo lahko povozi (npr. finale na 7 nizov)
    privzeto_stevilo_nizov  INTEGER NOT NULL CHECK (privzeto_stevilo_nizov IN (3, 5, 7)),
    prijavnina              REAL CHECK (prijavnina IS NULL OR prijavnina >= 0),
    rok_prijave             TEXT,
    status                  TEXT NOT NULL CHECK (status IN ('PRIPRAVA', 'V_TEKU', 'ZAKLJUCEN')),
    -- Nastavitvi skupinskega dela (samo pri sistemu SKUPINE).
    -- Zgornja meja 26 je posledica oznak skupin A..Z.
    stevilo_skupin          INTEGER CHECK (stevilo_skupin IS NULL OR stevilo_skupin BETWEEN 1 AND 26),
    velikost_skupine        INTEGER CHECK (velikost_skupine IS NULL OR velikost_skupine BETWEEN 2 AND 24),
    verzija                 INTEGER NOT NULL DEFAULT 0,
    ustvarjen_ob            TEXT NOT NULL,
    -- sistem SKUPINE brez teh dveh nastavitev ne ve, koliko igralcev vzeti
    CHECK (sistem_tekmovanja <> 'SKUPINE'
           OR (stevilo_skupin IS NOT NULL AND velikost_skupine IS NOT NULL)),
    -- dvojice igrajo izkljucno izlocilno mrezo: krozni sistem in skupine bi
    -- za pare potrebovala se lestvice parov, ki jih ni
    CHECK (disciplina <> 'DVOJICE' OR sistem_tekmovanja = 'IZLOCILNI'),
    -- "strogo mesano" je lastnost PARA, zato pri posamicnem dogodku ni izbira;
    -- tam je odprta kategorija KDORKOLI
    CHECK (spol_kategorija <> 'MESANO' OR disciplina = 'DVOJICE')
);

-- MESANO je doslej pomenil "nastopi lahko vsak" - to je odslej KDORKOLI.
INSERT INTO dogodek_nov (id, id_turnir, ime, disciplina, spol_kategorija,
                         starostna_kategorija, sistem_tekmovanja, privzeto_stevilo_nizov,
                         prijavnina, rok_prijave, status, stevilo_skupin, velikost_skupine,
                         verzija, ustvarjen_ob)
SELECT id, id_turnir, ime, disciplina,
       CASE WHEN spol_kategorija = 'MESANO' THEN 'KDORKOLI' ELSE spol_kategorija END,
       starostna_kategorija, sistem_tekmovanja, privzeto_stevilo_nizov,
       prijavnina, rok_prijave, status, stevilo_skupin, velikost_skupine,
       verzija, ustvarjen_ob
FROM dogodek;

DROP TABLE dogodek;
ALTER TABLE dogodek_nov RENAME TO dogodek;

CREATE INDEX idx_dogodek_turnir ON dogodek (id_turnir);

-- ---------------------------------------------------------------------------
-- Prijava: drugi igralec (par) s svojim posnetkom kluba in ratinga
-- ---------------------------------------------------------------------------

CREATE TABLE prijava_nov (
    id                      INTEGER PRIMARY KEY,
    id_dogodek              INTEGER NOT NULL REFERENCES dogodek (id),
    id_igralec              INTEGER NOT NULL REFERENCES igralec (id),
    -- drugi igralec para (samo pri disciplini DVOJICE); NULL pomeni posamicno
    -- prijavo oz. prijavljenega igralca, ki soigralca se nima
    id_igralec_2            INTEGER REFERENCES igralec (id),
    -- klub, za katerega je igralec nastopal OB PRIJAVI (posnetek)
    id_klub_ob_prijavi      INTEGER REFERENCES klub (id),
    id_klub_ob_prijavi_2    INTEGER REFERENCES klub (id),
    status                  TEXT NOT NULL CHECK (status IN ('PRIJAVLJEN', 'ODJAVLJEN', 'DISKVALIFICIRAN', 'REZERVA', 'ODSTOPIL')),
    -- Mesto na jakostni lestvici dogodka (1 = najmocnejsi). Pri sistemu
    -- SKUPINE po njem tece izbor najboljsih N in razporeditev v skupine,
    -- zato ga administrator lahko rocno uredi pred zrebom.
    st_nosilca              INTEGER CHECK (st_nosilca IS NULL OR st_nosilca >= 1),
    -- posnetek ratinga ob zrebu - za sledljivost in ponovljivost zreba
    rating_ob_zrebu         INTEGER,
    rating_ob_zrebu_2       INTEGER,
    id_skupina              INTEGER REFERENCES skupina (id),
    mesto_v_skupini         INTEGER,
    -- koncna uvrstitev na dogodku (1 = zmagovalec); od nje bodo odvisne tocke
    koncno_mesto            INTEGER CHECK (koncno_mesto IS NULL OR koncno_mesto >= 1),
    placano                 INTEGER NOT NULL DEFAULT 0 CHECK (placano IN (0, 1)),
    prijavljen_ob           TEXT NOT NULL,
    verzija                 INTEGER NOT NULL DEFAULT 0,
    UNIQUE (id_dogodek, id_igralec),
    -- igralec ne more biti sam svoj soigralec
    CHECK (id_igralec_2 IS NULL OR id_igralec_2 <> id_igralec)
);

INSERT INTO prijava_nov (id, id_dogodek, id_igralec, id_klub_ob_prijavi, status,
                         st_nosilca, rating_ob_zrebu, id_skupina, mesto_v_skupini,
                         koncno_mesto, placano, prijavljen_ob, verzija)
SELECT id, id_dogodek, id_igralec, id_klub_ob_prijavi, status,
       st_nosilca, rating_ob_zrebu, id_skupina, mesto_v_skupini,
       koncno_mesto, placano, prijavljen_ob, verzija
FROM prijava;

DROP TABLE prijava;
ALTER TABLE prijava_nov RENAME TO prijava;

CREATE INDEX idx_prijava_dogodek ON prijava (id_dogodek);

-- Isti igralec ne sme biti soigralec dveh parov istega dogodka. Delni indeks
-- (WHERE ... IS NOT NULL) je nujen: brez njega bi indeks po nepotrebnem
-- pokrival vse posamicne prijave. Da igralec ni hkrati nosilec ene in
-- soigralec druge prijave, pazi TurnirjiStoritev - tega z eno omejitvijo v
-- bazi ni mogoce izraziti.
CREATE UNIQUE INDEX idx_prijava_soigralec ON prijava (id_dogodek, id_igralec_2)
    WHERE id_igralec_2 IS NOT NULL;

-- Preveri, da prezidava ni pustila osirotelih vrstic (tuji kljuci so bili
-- izklopljeni, zato tega ni preverjal nihce drug).
PRAGMA foreign_key_check;

PRAGMA foreign_keys = ON;
