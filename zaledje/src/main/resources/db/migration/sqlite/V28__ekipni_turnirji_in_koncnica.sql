-- ============================================================================
-- Turnirko: ekipni turnirji, skupine za mesta in koncnica lige
--
-- EKIPNI TURNIR (disciplina EKIPNO)
-- ---------------------------------
-- Ekipna drzavna prvenstva mladih, njihove kvalifikacije in pokal NTZS niso
-- lige: igrajo se na turnirski dan, po skupinah in mrezi. Doslej jih Turnirko
-- ni znal zapisati, zato jih uvoz iz Stupe ni prenesel.
--
-- Zasnova sledi dvojicam (V14): TEKMOVALNA ENOTA JE PRIJAVA. Pri ekipnem
-- dogodku prijava nosi EKIPO (in ne igralca), zato zreb, mreza, skupine,
-- napredovanje, lestvica in koncna mesta tecejo po nespremenjeni kodi. Izid
-- ekipne tekme v mrezi so dobljene posamicne tekme (npr. 3 : 1).
--
-- Kdo je igral in kako, zapise SRECANJE - ista tabela kot pri ligi: postava
-- A/B/C : X/Y/Z, posamicne tekme po formatu, nizi in uradni zapisnik. Srecanje
-- zato pripada bodisi ligi bodisi tekmi turnirja (natanko enemu). Posamicne
-- tekme ostanejo v tekma_srecanja in od tam jih ze berejo rating, profil,
-- dvoboji in statistika - druge vzporedne tabele posamicnih tekem ni.
--
-- Ekipa zato pripada bodisi ligi bodisi dogodku, format srecanja pa se pri
-- ekipnem dogodku vpise v dogodek.
--
-- SKUPINE ZA MESTA (sistem SKUPINE_ZA_MESTA)
-- ------------------------------------------
-- PST 14. clen: ekipe igrajo v predtekmovalnih skupinah, nato prvo- in
-- drugouvrscene v finalni skupini za 1.-4. mesto, tretje- in cetrtouvrscene za
-- 5.-8. mesto, "v finalno skupino se prenese rezultat relevantnega dvoboja
-- iz predtekmovalne skupine". Skupina zato dobi STOPNJO (1 = predtekmovanje)
-- in PRVO MESTO, ki ga odloca, tekma pa PRENESEN IZID: tekma v finalni skupini
-- nosi izid prvotne tekme in kazalec nanjo, posamicnih tekem pa nima - sicer
-- bi isti dvoboj v ratingu in statistiki stel dvakrat.
--
-- KONCNICA LIGE (serija_koncnice)
-- -------------------------------
-- 1. SNTL po rednem delu igra koncnico: polfinale in finale na dve zmagi.
-- Doslej ga model ni poznal in uvoz je tekme koncnice zlozil v 1. in 2. kolo
-- rednega dela - lestvica je stela srecanja, ki niso bila del rednega dela.
-- Serija je par ekip v krogu koncnice; srecanja koncnice nosijo serijo in
-- zaporedno tekmo v njej, redna srecanja pa ne. Liga pove, ali koncnico ima
-- (koliko ekip, koliko zmag za serijo).
--
-- Tabele dogodek, ekipa, prijava in srecanje je treba prezidati (spremenjene
-- omejitve CHECK in NOT NULL). Nanje kazejo tuji kljuci, zato skripta tece
-- izven transakcije z zacasno izklopljenimi kljuci - postopek iz V14.
-- ============================================================================

PRAGMA foreign_keys = OFF;

-- ---------------------------------------------------------------------------
-- Dogodek: disciplina EKIPNO, sistem SKUPINE_ZA_MESTA, format srecanja
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
                                                                     'KROZNI', 'SKUPINE', 'SKUPINE_ZA_MESTA')),
    -- "najboljsi od N nizov" - privzeta vrednost za tekme tega dogodka (pri
    -- ekipnem dogodku za posamicne tekme srecanja)
    privzeto_stevilo_nizov  INTEGER NOT NULL CHECK (privzeto_stevilo_nizov IN (3, 5, 7)),
    prijavnina              REAL CHECK (prijavnina IS NULL OR prijavnina >= 0),
    rok_prijave             TEXT,
    status                  TEXT NOT NULL CHECK (status IN ('PRIPRAVA', 'V_TEKU', 'ZAKLJUCEN')),
    -- Nastavitvi skupinskega dela. Pri sistemu SKUPINE obvezni (format TOP),
    -- pri SKUPINE_ZA_MESTA je stevilo skupin neobvezno (brez njega ga doloci zreb).
    -- Zgornja meja 26 je posledica oznak skupin A..Z.
    stevilo_skupin          INTEGER CHECK (stevilo_skupin IS NULL OR stevilo_skupin BETWEEN 1 AND 26),
    velikost_skupine        INTEGER CHECK (velikost_skupine IS NULL OR velikost_skupine BETWEEN 2 AND 24),
    -- ekipni dogodek: format srecanja, prag zmag za srecanje (NULL = vse tekme)
    format_srecanja         TEXT CHECK (format_srecanja IS NULL OR format_srecanja IN
                                        ('SNTL', 'CORBILLON', 'SAVINJA', 'SNTL_PRVA',
                                         'SNTL_BREZ_DVOJIC', 'OLIMPIJSKI',
                                         'SNTL_DVOJICE_SEDMA', 'SNTL_PRVA_DVOJICE_CETRTA',
                                         'EKIPNI_DP', 'POKAL_NTZS')),
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
    CHECK (tekma_za_tretje_mesto = 0 OR sistem_tekmovanja IN ('IZLOCILNI', 'SKUPINE_IZLOCILNI'))
);

INSERT INTO dogodek_nov (id, id_turnir, ime, disciplina, spol_kategorija,
                         starostna_kategorija, sistem_tekmovanja, privzeto_stevilo_nizov,
                         prijavnina, rok_prijave, status, stevilo_skupin, velikost_skupine,
                         verzija, ustvarjen_ob)
SELECT id, id_turnir, ime, disciplina, spol_kategorija,
       starostna_kategorija, sistem_tekmovanja, privzeto_stevilo_nizov,
       prijavnina, rok_prijave, status, stevilo_skupin, velikost_skupine,
       verzija, ustvarjen_ob
FROM dogodek;

DROP TABLE dogodek;
ALTER TABLE dogodek_nov RENAME TO dogodek;

CREATE INDEX idx_dogodek_turnir ON dogodek (id_turnir);

-- ---------------------------------------------------------------------------
-- Ekipa: nastop v ligi ALI na ekipnem dogodku turnirja
-- ---------------------------------------------------------------------------

CREATE TABLE ekipa_nov (
    id              INTEGER PRIMARY KEY,
    id_liga         INTEGER REFERENCES liga (id),
    id_dogodek      INTEGER REFERENCES dogodek (id),
    -- NEOBVEZEN: prazen pomeni prosto ekipo, ki nastopa samo v tem tekmovanju
    id_klub         INTEGER REFERENCES klub (id),
    -- zaporedna ekipa kluba v tekmovanju (1 = prva mostvo, npr. "Savinja 1");
    -- prosta ekipa je vedno 1, ker je nima s cim steti
    zaporedna       INTEGER NOT NULL DEFAULT 1 CHECK (zaporedna >= 1),
    -- prikazano ime; pri klubski ekipi neobvezno (sestavi se iz kluba in
    -- zaporedne), pri prosti obvezno
    ime             TEXT CHECK (ime IS NULL OR length(trim(ime)) BETWEEN 2 AND 60),
    verzija         INTEGER NOT NULL DEFAULT 0,
    st_nosilca      INTEGER CHECK (st_nosilca IS NULL OR st_nosilca >= 1),
    UNIQUE (id_liga, id_klub, zaporedna),
    -- ekipa brez kluba brez imena ne bi imela nobenega poimenovanja
    CHECK (id_klub IS NOT NULL OR ime IS NOT NULL),
    -- ekipa nastopa v natanko enem tekmovanju
    CHECK ((id_liga IS NULL) <> (id_dogodek IS NULL))
);

INSERT INTO ekipa_nov (id, id_liga, id_klub, zaporedna, ime, verzija, st_nosilca)
SELECT id, id_liga, id_klub, zaporedna, ime, verzija, st_nosilca
FROM ekipa;

DROP TABLE ekipa;
ALTER TABLE ekipa_nov RENAME TO ekipa;

CREATE INDEX idx_ekipa_liga ON ekipa (id_liga);
CREATE INDEX idx_ekipa_dogodek ON ekipa (id_dogodek);
CREATE UNIQUE INDEX idx_ekipa_prosta_ime ON ekipa (id_liga, ime) WHERE id_klub IS NULL;
-- UNIQUE (id_liga, ...) ekip dogodka ne pokrije (NULL-i so v SQLite razlicni)
CREATE UNIQUE INDEX idx_ekipa_dogodek_klub ON ekipa (id_dogodek, id_klub, zaporedna)
    WHERE id_dogodek IS NOT NULL AND id_klub IS NOT NULL;
CREATE UNIQUE INDEX idx_ekipa_dogodek_prosta_ime ON ekipa (id_dogodek, ime)
    WHERE id_dogodek IS NOT NULL AND id_klub IS NULL;

-- ---------------------------------------------------------------------------
-- Prijava: tekmovalna enota je igralec (oz. par) ALI ekipa
-- ---------------------------------------------------------------------------

CREATE TABLE prijava_nov (
    id                      INTEGER PRIMARY KEY,
    id_dogodek              INTEGER NOT NULL REFERENCES dogodek (id),
    -- posamicna prijava oz. prvi igralec para; pri ekipnem dogodku prazen
    id_igralec              INTEGER REFERENCES igralec (id),
    -- drugi igralec para (samo pri disciplini DVOJICE)
    id_igralec_2            INTEGER REFERENCES igralec (id),
    -- ekipa (samo pri disciplini EKIPNO); igralce nosi njen kader
    id_ekipa                INTEGER REFERENCES ekipa (id),
    -- klub, za katerega je igralec (ekipa) nastopal OB PRIJAVI (posnetek)
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
    CHECK (id_igralec_2 IS NULL OR id_igralec_2 <> id_igralec),
    -- natanko ena tekmovalna enota: igralec (par) ali ekipa
    CHECK ((id_igralec IS NULL) <> (id_ekipa IS NULL)),
    CHECK (id_ekipa IS NULL OR id_igralec_2 IS NULL)
);

INSERT INTO prijava_nov (id, id_dogodek, id_igralec, id_igralec_2, id_klub_ob_prijavi,
                         id_klub_ob_prijavi_2, status, st_nosilca, rating_ob_zrebu,
                         rating_ob_zrebu_2, id_skupina, mesto_v_skupini, koncno_mesto,
                         placano, prijavljen_ob, verzija)
SELECT id, id_dogodek, id_igralec, id_igralec_2, id_klub_ob_prijavi,
       id_klub_ob_prijavi_2, status, st_nosilca, rating_ob_zrebu,
       rating_ob_zrebu_2, id_skupina, mesto_v_skupini, koncno_mesto,
       placano, prijavljen_ob, verzija
FROM prijava;

DROP TABLE prijava;
ALTER TABLE prijava_nov RENAME TO prijava;

CREATE INDEX idx_prijava_dogodek ON prijava (id_dogodek);
CREATE UNIQUE INDEX idx_prijava_soigralec ON prijava (id_dogodek, id_igralec_2)
    WHERE id_igralec_2 IS NOT NULL;
-- ekipa pripada enemu dogodku in je v njem prijavljena enkrat
CREATE UNIQUE INDEX idx_prijava_ekipa ON prijava (id_ekipa) WHERE id_ekipa IS NOT NULL;

-- ---------------------------------------------------------------------------
-- Koncnica lige: serija (par ekip v krogu koncnice)
-- ---------------------------------------------------------------------------

CREATE TABLE serija_koncnice (
    id              INTEGER PRIMARY KEY,
    id_liga         INTEGER NOT NULL REFERENCES liga (id),
    -- 1 = prvi krog koncnice; zadnji krog je finale
    krog            INTEGER NOT NULL CHECK (krog >= 1),
    -- mesto para v krogu (1 = zgornji); zmagovalec para p gre v par ceil(p/2)
    par             INTEGER NOT NULL CHECK (par >= 1),
    -- ekipi sta prazni, dokler ju ne doloci prejsnji krog
    id_ekipa_1      INTEGER REFERENCES ekipa (id),
    id_ekipa_2      INTEGER REFERENCES ekipa (id),
    -- mesto ekipe na lestvici rednega dela - odloca domace pravice v seriji
    mesto_1         INTEGER CHECK (mesto_1 IS NULL OR mesto_1 >= 1),
    mesto_2         INTEGER CHECK (mesto_2 IS NULL OR mesto_2 >= 1),
    zmage_1         INTEGER NOT NULL DEFAULT 0 CHECK (zmage_1 >= 0),
    zmage_2         INTEGER NOT NULL DEFAULT 0 CHECK (zmage_2 >= 0),
    id_zmagovalec   INTEGER REFERENCES ekipa (id),
    verzija         INTEGER NOT NULL DEFAULT 0,
    UNIQUE (id_liga, krog, par),
    CHECK (id_ekipa_1 IS NULL OR id_ekipa_2 IS NULL OR id_ekipa_1 <> id_ekipa_2)
);

CREATE INDEX idx_serija_koncnice_liga ON serija_koncnice (id_liga);

-- ---------------------------------------------------------------------------
-- Srecanje: pripada ligi ALI tekmi turnirja; srecanje koncnice nosi serijo
-- ---------------------------------------------------------------------------

CREATE TABLE srecanje_nov (
    id                  INTEGER PRIMARY KEY,
    id_liga             INTEGER REFERENCES liga (id),
    -- ekipna tekma turnirja (mreza ali skupina), katere izid je to srecanje
    id_tekma            INTEGER UNIQUE REFERENCES tekma (id),
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
    -- koncnica: serija in zaporedna tekma v njej; redna srecanja ju nimata
    id_serija           INTEGER REFERENCES serija_koncnice (id),
    tekma_v_seriji      INTEGER CHECK (tekma_v_seriji IS NULL OR tekma_v_seriji >= 1),
    verzija             INTEGER NOT NULL DEFAULT 0,
    CHECK (id_ekipa_domaci <> id_ekipa_gost),
    CHECK ((id_liga IS NULL) <> (id_tekma IS NULL)),
    CHECK (id_serija IS NULL OR id_liga IS NOT NULL),
    CHECK ((id_serija IS NULL) = (tekma_v_seriji IS NULL))
);

INSERT INTO srecanje_nov (id, id_liga, kolo, id_ekipa_domaci, id_ekipa_gost,
                          dobljene_domaci, dobljene_gost, status, predviden_zacetek,
                          odigran_ob, verzija)
SELECT id, id_liga, kolo, id_ekipa_domaci, id_ekipa_gost,
       dobljene_domaci, dobljene_gost, status, predviden_zacetek,
       odigran_ob, verzija
FROM srecanje;

DROP TABLE srecanje;
ALTER TABLE srecanje_nov RENAME TO srecanje;

CREATE INDEX idx_srecanje_liga ON srecanje (id_liga);
CREATE INDEX idx_srecanje_serija ON srecanje (id_serija);

-- ---------------------------------------------------------------------------
-- Liga: koncnica (koliko ekip, koliko zmag za serijo)
-- ---------------------------------------------------------------------------

ALTER TABLE liga ADD COLUMN koncnica_ekip INTEGER
    CHECK (koncnica_ekip IS NULL OR koncnica_ekip IN (2, 4, 8));
ALTER TABLE liga ADD COLUMN koncnica_zmag INTEGER
    CHECK ((koncnica_zmag IS NULL AND koncnica_ekip IS NULL)
           OR (koncnica_zmag BETWEEN 1 AND 4 AND koncnica_ekip IS NOT NULL));

-- ---------------------------------------------------------------------------
-- Skupina: stopnja in mesta, ki jih odloca; tekma: prenesen izid
-- ---------------------------------------------------------------------------

ALTER TABLE skupina ADD COLUMN stopnja INTEGER NOT NULL DEFAULT 1 CHECK (stopnja >= 1);
ALTER TABLE skupina ADD COLUMN ime TEXT
    CHECK (ime IS NULL OR length(trim(ime)) BETWEEN 1 AND 40);
ALTER TABLE skupina ADD COLUMN prvo_mesto INTEGER
    CHECK (prvo_mesto IS NULL OR prvo_mesto >= 1);

ALTER TABLE tekma ADD COLUMN id_prenesena INTEGER REFERENCES tekma (id);

-- Preveri, da prezidava ni pustila osirotelih vrstic (tuji kljuci so bili
-- izklopljeni, zato tega ni preverjal nihce drug).
PRAGMA foreign_key_check;

PRAGMA foreign_keys = ON;
