-- ============================================================================
-- Turnirko: skupinski sistem (TOP turnirji)
--
-- Nov sistem tekmovanja SKUPINE: izmed prijavljenih se izbere najboljsih N,
-- ki se jih ZAPOREDNO po jakosti razdeli v skupine (A = najmocnejsa), znotraj
-- skupine pa igra vsak z vsakim. Izlocilnega dela NI - turnir se konca po
-- zadnjem kolu skupin in vsaka skupina ima svojo lestvico.
--
-- Zato dogodek potrebuje dve novi nastavitvi (stevilo skupin in velikost
-- skupine), prijava pa dva nova statusa:
--   REZERVA  - prijavljen, a ni prisel v izbor najboljsih N (ne igra),
--   ODSTOPIL - odstopil med turnirjem (ze odigrane tekme obveljajo).
-- Obojega ne moremo izraziti z obstojecimi statusi: "odjavljen" pomeni, da se
-- je igralec odjavil sam, "diskvalificiran" pa je disciplinski ukrep - oboje
-- bi bila neresnicna trditev o igralcu.
--
-- Obe tabeli je treba prezidati, ker SQLite ne zna spremeniti omejitve CHECK.
-- Za razliko od migracije V4 (uporabnik) na ti dve tabeli KAZEJO tuji kljuci
-- (prijava, skupina in tekma na dogodek; tekma na prijavo), zato migracija
-- tece izven transakcije z zacasno izklopljenimi tujimi kljuci - tako je
-- predpisan tudi uradni postopek v dokumentaciji SQLite. Nastavitev velja
-- samo za to povezavo; nove povezave jih spet vklopijo (connection-init-sql).
-- Glej V5__skupinski_sistem.sql.conf (executeInTransaction=false).
-- ============================================================================

PRAGMA foreign_keys = OFF;

-- ---------------------------------------------------------------------------
-- Dogodek: nov sistem SKUPINE + nastavitvi skupinskega dela
-- ---------------------------------------------------------------------------

CREATE TABLE dogodek_nov (
    id                      INTEGER PRIMARY KEY,
    id_turnir               INTEGER NOT NULL REFERENCES turnir (id),
    -- npr. "Clani posamicno" ali "Kadetinje posamicno"
    ime                     TEXT NOT NULL CHECK (length(trim(ime)) BETWEEN 3 AND 60),
    disciplina              TEXT NOT NULL CHECK (disciplina IN ('POSAMICNO', 'DVOJICE')),
    spol_kategorija         TEXT NOT NULL CHECK (spol_kategorija IN ('MOSKI', 'ZENSKE', 'MESANO')),
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
           OR (stevilo_skupin IS NOT NULL AND velikost_skupine IS NOT NULL))
);

INSERT INTO dogodek_nov (id, id_turnir, ime, disciplina, spol_kategorija,
                         starostna_kategorija, sistem_tekmovanja, privzeto_stevilo_nizov,
                         prijavnina, rok_prijave, status, verzija, ustvarjen_ob)
SELECT id, id_turnir, ime, disciplina, spol_kategorija,
       starostna_kategorija, sistem_tekmovanja, privzeto_stevilo_nizov,
       prijavnina, rok_prijave, status, verzija, ustvarjen_ob
FROM dogodek;

DROP TABLE dogodek;
ALTER TABLE dogodek_nov RENAME TO dogodek;

CREATE INDEX idx_dogodek_turnir ON dogodek (id_turnir);

-- ---------------------------------------------------------------------------
-- Prijava: statusa REZERVA in ODSTOPIL
-- ---------------------------------------------------------------------------

CREATE TABLE prijava_nov (
    id                  INTEGER PRIMARY KEY,
    id_dogodek          INTEGER NOT NULL REFERENCES dogodek (id),
    id_igralec          INTEGER NOT NULL REFERENCES igralec (id),
    -- klub, za katerega je igralec nastopal OB PRIJAVI (posnetek)
    id_klub_ob_prijavi  INTEGER REFERENCES klub (id),
    status              TEXT NOT NULL CHECK (status IN ('PRIJAVLJEN', 'ODJAVLJEN', 'DISKVALIFICIRAN', 'REZERVA', 'ODSTOPIL')),
    -- Mesto na jakostni lestvici dogodka (1 = najmocnejsi). Pri sistemu
    -- SKUPINE po njem tece izbor najboljsih N in razporeditev v skupine,
    -- zato ga administrator lahko rocno uredi pred zrebom.
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

-- Preveri, da prezidava ni pustila osirotelih vrstic (tuji kljuci so bili
-- izklopljeni, zato tega ni preverjal nihce drug).
PRAGMA foreign_key_check;

PRAGMA foreign_keys = ON;
