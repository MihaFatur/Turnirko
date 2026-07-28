-- ============================================================================
-- Turnirko: vloga ORGANIZATOR in lastnistvo turnirjev/lig
--
-- Doslej sta obstajali vlogi ADMIN in IGRALEC. Zdaj dodamo ORGANIZATOR: klub
-- oz. oseba, ki ustvarja turnirje in lige ter upravlja SAMO tiste, ki jih je
-- ustvaril on ali kdo iz istega kluba. Registracija organizatorja je prosta
-- (racun nastane v stanju CAKA), vlogo in klub mu potrdi administrator.
--
-- Spremembe:
--  1) uporabnik.vloga sme biti tudi 'ORGANIZATOR' (razsiritev CHECK) in
--     uporabnik dobi 'id_klub' - klub, ki mu pripada (za organizatorja
--     neobvezen; doloci ga admin ob potrditvi). SQLite ne zna spremeniti
--     CHECK omejitve, zato tabelo prezidamo (kot pri V4).
--  2) turnir in liga dobita 'id_ustvaril' (racun, ki ju je ustvaril) in
--     'id_klub_lastnik' (posnetek organizatorjevega kluba ob nastanku).
--     Po njiju storitve preverjajo, kdo sme urejati. Obstojeci (adminovi)
--     zapisi ostanejo brez lastnika - upravlja jih le administrator.
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 1) Prezidava tabele uporabnik: razsirjen CHECK vloge + nov stolpec id_klub
-- ---------------------------------------------------------------------------

CREATE TABLE uporabnik_nov (
    id                  INTEGER PRIMARY KEY,
    uporabnisko_ime     TEXT NOT NULL UNIQUE CHECK (length(trim(uporabnisko_ime)) BETWEEN 3 AND 120),
    -- BCrypt zgostitev gesla (nikoli cistopis)
    geslo_hash          TEXT NOT NULL,
    vloga               TEXT NOT NULL CHECK (vloga IN ('ADMIN', 'ORGANIZATOR', 'IGRALEC')),
    status              TEXT NOT NULL DEFAULT 'POTRJEN' CHECK (status IN ('CAKA', 'POTRJEN', 'ZAVRNJEN')),
    -- povezava na sifrant igralcev; en igralec ima lahko najvec en racun
    id_igralec          INTEGER UNIQUE REFERENCES igralec (id),
    -- klub, ki mu uporabnik pripada (za organizatorja; doloci ga admin ob potrditvi)
    id_klub             INTEGER REFERENCES klub (id),
    -- kar je oseba navedla ob registraciji (podlaga adminu za potrditev)
    prijavljeno_ime     TEXT,
    prijavljeni_priimek TEXT,
    id_klub_zelja       INTEGER REFERENCES klub (id),
    aktiven             INTEGER NOT NULL DEFAULT 1 CHECK (aktiven IN (0, 1)),
    ustvarjen_ob        TEXT NOT NULL,
    -- potrjen racun igralca mora biti povezan z igralcem, sicer ne ve, cigav je
    CHECK (vloga <> 'IGRALEC' OR status <> 'POTRJEN' OR id_igralec IS NOT NULL)
);

INSERT INTO uporabnik_nov (id, uporabnisko_ime, geslo_hash, vloga, status, id_igralec,
                           prijavljeno_ime, prijavljeni_priimek, id_klub_zelja, aktiven, ustvarjen_ob)
SELECT id, uporabnisko_ime, geslo_hash, vloga, status, id_igralec,
       prijavljeno_ime, prijavljeni_priimek, id_klub_zelja, aktiven, ustvarjen_ob
FROM uporabnik;

DROP TABLE uporabnik;
ALTER TABLE uporabnik_nov RENAME TO uporabnik;

CREATE INDEX idx_uporabnik_status ON uporabnik (status);
CREATE INDEX idx_uporabnik_igralec ON uporabnik (id_igralec);
CREATE INDEX idx_uporabnik_klub ON uporabnik (id_klub);

-- ---------------------------------------------------------------------------
-- 2) Lastnistvo turnirjev in lig
--    id_ustvaril     - racun, ki je turnir/ligo ustvaril
--    id_klub_lastnik - posnetek organizatorjevega kluba ob nastanku; po njem
--                      sme upravljati vsak organizator istega kluba
-- ---------------------------------------------------------------------------

ALTER TABLE turnir ADD COLUMN id_ustvaril     INTEGER REFERENCES uporabnik (id);
ALTER TABLE turnir ADD COLUMN id_klub_lastnik INTEGER REFERENCES klub (id);

ALTER TABLE liga ADD COLUMN id_ustvaril     INTEGER REFERENCES uporabnik (id);
ALTER TABLE liga ADD COLUMN id_klub_lastnik INTEGER REFERENCES klub (id);

CREATE INDEX idx_turnir_klub_lastnik ON turnir (id_klub_lastnik);
CREATE INDEX idx_liga_klub_lastnik   ON liga (id_klub_lastnik);
