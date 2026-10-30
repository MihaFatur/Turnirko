-- ============================================================================
-- Turnirko: profilna slika igralca in logotipi pokroviteljev (V56, okt 2026)
--
-- profilna_slika   slika, ki jo igralec s Premium naloži sam. Pripada RAČUNU
--                  (Premium je last računa), kaže pa se pri igralcu, s katerim je
--                  račun povezan - na profilu, v 1 na 1 in na stopničkah.
--                  Javna je samo, dokler ima račun veljaven Premium; ob izteku se
--                  skrije, ne izbriše. Z računom gre (ON DELETE CASCADE).
--   stanje         OBJAVLJENA = javna; CAKA = čaka na admina. Čaka slika
--                  mlajšega od 15 let in slika računa, ki ga NI povezal admin
--                  (samodejna povezava po imenu in datumu rojstva ni dokaz
--                  istovetnosti - glej VARNOSTNI-PREGLED V3). Ostale so javne
--                  takoj, admin pa dobi e-pošto in jo lahko odstrani.
--   zgostitev      SHA-256 velike različice; javni naslov
--                  /api/v1/slike/profil/{zgostitev}. Nova slika = nov naslov.
--
-- profilna_slika_vsebina  bajti (VELIKA 480 x 480, MALA 160 x 160, JPEG), ločeni
--                  od opisa kot pri logotipih: seznami in preverbe vidnosti bajtov
--                  ne berejo. Izbris slike izbriše bajte (CASCADE) - to je osebni
--                  podatek in ne sme ostati v bazi.
--
-- pokrovitelj      logotip pokrovitelja lige ALI turnirja (natanko eno od obeh).
--                  Slika je iz knjižnice logotipov (slika, V54). vrstni_red določi
--                  organizator; glavni je največ eden (varuje storitev - pogoj čez
--                  več vrstic bi SQLite terjal prožilec). Povezava je neobvezna in
--                  samo http(s) - javascript: ali data: na strani ne smeta v href.
-- ============================================================================

CREATE TABLE profilna_slika (
    id             INTEGER PRIMARY KEY,
    id_uporabnik   INTEGER NOT NULL UNIQUE REFERENCES uporabnik(id) ON DELETE CASCADE,
    zgostitev      TEXT NOT NULL UNIQUE CHECK (length(zgostitev) = 64),
    stanje         TEXT NOT NULL CHECK (stanje IN ('CAKA', 'OBJAVLJENA')),
    nalozena_ob    TEXT NOT NULL,
    objavljena_ob  TEXT
);

CREATE TABLE profilna_slika_vsebina (
    id                 INTEGER PRIMARY KEY,
    id_profilna_slika  INTEGER NOT NULL REFERENCES profilna_slika(id) ON DELETE CASCADE,
    velikost           TEXT NOT NULL CHECK (velikost IN ('VELIKA', 'MALA')),
    vsebina            BLOB NOT NULL,
    UNIQUE (id_profilna_slika, velikost)
);

CREATE TABLE pokrovitelj (
    id          INTEGER PRIMARY KEY,
    id_liga     INTEGER REFERENCES liga(id) ON DELETE CASCADE,
    id_turnir   INTEGER REFERENCES turnir(id) ON DELETE CASCADE,
    id_slika    INTEGER NOT NULL REFERENCES slika(id),
    ime         TEXT NOT NULL CHECK (length(ime) BETWEEN 1 AND 80),
    povezava    TEXT CHECK (povezava IS NULL
                            OR (length(povezava) <= 300
                                AND (povezava LIKE 'http://%' OR povezava LIKE 'https://%'))),
    glavni      INTEGER NOT NULL DEFAULT 0 CHECK (glavni IN (0, 1)),
    vrstni_red  INTEGER NOT NULL,
    CHECK ((id_liga IS NULL) <> (id_turnir IS NULL))
);

CREATE INDEX idx_pokrovitelj_liga ON pokrovitelj(id_liga, vrstni_red);
CREATE INDEX idx_pokrovitelj_turnir ON pokrovitelj(id_turnir, vrstni_red);
CREATE INDEX idx_pokrovitelj_slika ON pokrovitelj(id_slika);
