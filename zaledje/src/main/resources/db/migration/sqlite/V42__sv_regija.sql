-- ============================================================================
-- Turnirko: sistem SV_REGIJA (nivoji, skupine, glavni in tolazilni zreb, igra
-- se za VSA mesta)
--
-- Igralci so po jakosti razdeljeni v NIVOJE (privzeto po 16 igralcev, zadnji
-- nivo dobi ostanek). V vsakem nivoju igrajo skupine "vsak z vsakim" (privzeto
-- 4 x 4). Prvo- in drugouvrsceni vsake skupine gredo v GLAVNI zreb nivoja,
-- tretje- in cetrtouvrsceni v TOLAZILNI zreb, in tako naprej po dva ranga.
-- Zreb se igra za vsa mesta: porazenca vsakega kola se srecata med seboj
-- (zmagovalec za boljse mesto, porazenec za slabse), do zadnjega para.
--
-- DOGODEK
--   sistem_tekmovanja dobi vrednost SV_REGIJA (prezidava zaradi CHECK).
--   stevilo_nivojev   neobvezno: koliko nivojev (brez njega ga doloci stevilo
--                     prijav).
--   velikosti_nivojev neobvezno, rocna razdelitev ("16,16,12"); ce je
--                     vpisana, velja namesto samodejne.
--   rangov_v_zreb     neobvezno, po nivojih ("2,2,1"): koliko rangov iz vsake
--                     skupine pride v en zreb. Privzeto 2 (1.-2. v glavni zreb,
--                     3.-4. v tolazilni); 1 pomeni zreb samo prvouvrscenih,
--                     drugouvrscenih ... - pri 8 skupinah so to zrebi po 8.
--   Stevilo skupin na nivo in velikost skupine ostaneta v ze obstojecih
--   stolpcih stevilo_skupin in velikost_skupine (privzeto 4 in 4).
--
-- SKUPINA
--   nivo              kateri nivo skupina pripada (privzeto 1, kot doslej).
--
-- ZREB (nova tabela)
--   en zreb nivoja: indeks 0 je glavni, 1 tolazilni, 2 tretji ... Nosi, katero
--   mesto na dogodku odloca njegov zmagovalec (prvo_mesto je ABSOLUTNO, torej
--   zreb drugega nivoja se zacne za zadnjim mestom prvega), koliko igralcev je
--   vanj prislo in ali so tekme ze zgrajene. Zreb nastane ob zrebu skupin,
--   njegove tekme pa sele, ko so odigrane vse skupine nivoja.
--
-- TEKMA
--   id_zreb           zreb, ki mu tekma pripada (skupinske tekme ga nimajo).
--   razpon_od/do      katera mesta odloca ta tekma ("za 5.-8. mesto" je
--                     razpon 5-8); polfinale glavnega zreba z 8 igralci ima
--                     razpon 1-4, finale 1-2.
--   mesto_zmagovalca  mesto, ki ga zmagovalec DOKONCNO dobi (prazno, dokler
--   mesto_porazenca   napreduje dalje); to sta natanko tekmi, ki odlocata
--                     par mest - iz njiju nastane koncna razvrstitev.
-- ============================================================================

PRAGMA foreign_keys = OFF;

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
    CHECK (tekma_za_tretje_mesto = 0 OR sistem_tekmovanja IN ('IZLOCILNI', 'SKUPINE_IZLOCILNI')),
    -- nivoji so lastnost sistema SV_REGIJA
    CHECK ((stevilo_nivojev IS NULL AND velikosti_nivojev IS NULL AND rangov_v_zreb IS NULL)
           OR sistem_tekmovanja = 'SV_REGIJA')
);

INSERT INTO dogodek_nov (id, id_turnir, ime, disciplina, spol_kategorija,
                         starostna_kategorija, sistem_tekmovanja, privzeto_stevilo_nizov,
                         prijavnina, rok_prijave, status, stevilo_skupin, velikost_skupine,
                         format_srecanja, zmag_za_srecanje, tekma_za_tretje_mesto,
                         verzija, ustvarjen_ob)
SELECT id, id_turnir, ime, disciplina, spol_kategorija,
       starostna_kategorija, sistem_tekmovanja, privzeto_stevilo_nizov,
       prijavnina, rok_prijave, status, stevilo_skupin, velikost_skupine,
       format_srecanja, zmag_za_srecanje, tekma_za_tretje_mesto,
       verzija, ustvarjen_ob
FROM dogodek;

DROP TABLE dogodek;
ALTER TABLE dogodek_nov RENAME TO dogodek;

CREATE INDEX idx_dogodek_turnir ON dogodek (id_turnir);

-- ---------------------------------------------------------------------------
-- Skupina: nivo
-- ---------------------------------------------------------------------------

ALTER TABLE skupina ADD COLUMN nivo INTEGER NOT NULL DEFAULT 1 CHECK (nivo >= 1);

-- ---------------------------------------------------------------------------
-- Zreb: glavni, tolazilni ... zreb enega nivoja
-- ---------------------------------------------------------------------------

CREATE TABLE zreb (
    id              INTEGER PRIMARY KEY,
    id_dogodek      INTEGER NOT NULL REFERENCES dogodek (id),
    nivo            INTEGER NOT NULL CHECK (nivo >= 1),
    -- 0 = glavni, 1 = tolazilni, 2 = tretji ...
    indeks          INTEGER NOT NULL CHECK (indeks >= 0),
    -- najvisje mesto na DOGODKU, ki ga odloca zmagovalec zreba
    prvo_mesto      INTEGER NOT NULL CHECK (prvo_mesto >= 1),
    -- koliko igralcev pride v zreb iz skupin (mreza je potenca 2 nad tem)
    st_udelezencev  INTEGER NOT NULL CHECK (st_udelezencev >= 1),
    -- tekme so zgrajene (zreb nastane, ko so odigrane vse skupine nivoja)
    zgrajen         INTEGER NOT NULL DEFAULT 0 CHECK (zgrajen IN (0, 1)),
    -- razpored mest je vpisal organizator in ni bil nakljucen
    rocni           INTEGER NOT NULL DEFAULT 0 CHECK (rocni IN (0, 1)),
    -- prijave po mestih v mrezi od vrha navzdol ("12,40,,7"), prazno = prosto
    -- mesto; iz tekem se razporeda ne da zanesljivo prebrati (prosti prehodi
    -- tekem ne ustvarijo), rocno urejanje pa mora izhajati iz trenutnega stanja
    razpored        TEXT,
    verzija         INTEGER NOT NULL DEFAULT 0,
    UNIQUE (id_dogodek, nivo, indeks)
);

CREATE INDEX idx_zreb_dogodek ON zreb (id_dogodek);

-- ---------------------------------------------------------------------------
-- Tekma: zreb, razpon mest in dokoncni mesti
-- ---------------------------------------------------------------------------

ALTER TABLE tekma ADD COLUMN id_zreb INTEGER REFERENCES zreb (id);
ALTER TABLE tekma ADD COLUMN razpon_od INTEGER CHECK (razpon_od IS NULL OR razpon_od >= 1);
ALTER TABLE tekma ADD COLUMN razpon_do INTEGER CHECK (razpon_do IS NULL OR razpon_do >= 1);
ALTER TABLE tekma ADD COLUMN mesto_zmagovalca INTEGER CHECK (mesto_zmagovalca IS NULL OR mesto_zmagovalca >= 1);
ALTER TABLE tekma ADD COLUMN mesto_porazenca INTEGER CHECK (mesto_porazenca IS NULL OR mesto_porazenca >= 1);

CREATE INDEX idx_tekma_zreb ON tekma (id_zreb);

-- Preveri, da prezidava ni pustila osirotelih vrstic (tuji kljuci so bili
-- izklopljeni, zato tega ni preverjal nihce drug).
PRAGMA foreign_key_check;

PRAGMA foreign_keys = ON;
