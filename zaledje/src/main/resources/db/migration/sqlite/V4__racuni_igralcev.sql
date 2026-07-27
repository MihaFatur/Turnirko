-- ============================================================================
-- Turnirko: racuni igralcev
--
-- Doslej je bil uporabnik samo administrator. Zdaj se lahko registrira tudi
-- igralec, da vidi svoj profil s statistiko. Registracija je prosta (ime,
-- priimek, klub, e-posta, geslo), racun pa zacne v stanju CAKA - dostop
-- odobri administrator in ga pri tem POVEZE z zapisom v sifrantu igralcev.
-- Dokler racun ni povezan, ne more videti nicesar zasebnega.
--
-- Prijavno ime: za administratorja ostane uporabnisko ime ("admin"), za
-- igralca je to njegova e-posta - zato en sam stolpec uporabnisko_ime
-- (enolicen) in ne dva vzporedna, ki bi lahko razpadla narazen.
--
-- Tabelo je treba prezidati, ker SQLite ne zna spremeniti omejitve CHECK
-- (vloga je bila omejena na 'ADMIN'), niti dodati UNIQUE stolpca s tujim
-- kljucem. Podatki obstojecih administratorjev se prenesejo nespremenjeni
-- in dobijo status POTRJEN.
-- ============================================================================

CREATE TABLE uporabnik_nov (
    id                  INTEGER PRIMARY KEY,
    -- prijavno ime: pri igralcu je to e-posta, zato daljse od prejsnjih 40
    uporabnisko_ime     TEXT NOT NULL UNIQUE CHECK (length(trim(uporabnisko_ime)) BETWEEN 3 AND 120),
    -- BCrypt zgostitev gesla (nikoli cistopis)
    geslo_hash          TEXT NOT NULL,
    vloga               TEXT NOT NULL CHECK (vloga IN ('ADMIN', 'IGRALEC')),
    status              TEXT NOT NULL DEFAULT 'POTRJEN' CHECK (status IN ('CAKA', 'POTRJEN', 'ZAVRNJEN')),
    -- povezava na sifrant igralcev; en igralec ima lahko najvec en racun
    id_igralec          INTEGER UNIQUE REFERENCES igralec (id),
    -- kar je igralec navedel ob registraciji (podlaga adminu za povezavo)
    prijavljeno_ime     TEXT,
    prijavljeni_priimek TEXT,
    id_klub_zelja       INTEGER REFERENCES klub (id),
    aktiven             INTEGER NOT NULL DEFAULT 1 CHECK (aktiven IN (0, 1)),
    ustvarjen_ob        TEXT NOT NULL,
    -- potrjen racun igralca mora biti povezan z igralcem, sicer ne ve, cigav je
    CHECK (vloga <> 'IGRALEC' OR status <> 'POTRJEN' OR id_igralec IS NOT NULL)
);

INSERT INTO uporabnik_nov (id, uporabnisko_ime, geslo_hash, vloga, status, aktiven, ustvarjen_ob)
SELECT id, uporabnisko_ime, geslo_hash, vloga, 'POTRJEN', aktiven, ustvarjen_ob
FROM uporabnik;

DROP TABLE uporabnik;
ALTER TABLE uporabnik_nov RENAME TO uporabnik;

CREATE INDEX idx_uporabnik_status ON uporabnik (status);
CREATE INDEX idx_uporabnik_igralec ON uporabnik (id_igralec);
