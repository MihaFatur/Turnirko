-- ============================================================================
-- Turnirko: vloga NAVIJAC (racun osebe, ki ne igra)
--
-- Doslej je neigralec (stars, soigralec, klubski clovek), ki je zelel
-- spremljati lige, lahko odprl samo racun IGRALCA. Tak racun se ni mogel
-- povezati z nobenim zapisom, zato je obvisel v CAKA, admin pa je dobil
-- e-posto o cakajocem racunu, ki ga nima s cim povezati. Navijac se ne
-- povezuje z zapisom v sifrantu igralcev in ne caka admina: ko potrdi
-- naslov (in skrbnik, ce je mlajsi od 15 let), je racun POTRJEN.
--
-- Edina sprememba sheme je razsiritev CHECK omejitve stolpca vloga in nova
-- omejitev, da navijac nikoli nima zapisa igralca. SQLite omejitve CHECK ne
-- zna spremeniti, zato tabelo prezidamo izven transakcije in z zacasno
-- izklopljenimi tujimi kljuci (glej .conf ob tej datoteki): na uporabnik
-- kazejo narocnina, kode, dnevnik, prijave, spremljane lige, uporaba kod,
-- turnir in liga (ustvaril) ter slika (nalozil, V54). Z vklopljenimi kljuci
-- bi DROP TABLE izbrisal vse vrstice z ON DELETE CASCADE oz. izpraznil
-- stolpce z ON DELETE SET NULL. Shema je prepisana iz stanja po V53
-- (preverjeno na bazi, ne samo iz migracij; V54 tabele uporabnik ne
-- spreminja), stolpci se kopirajo po imenu.
-- ============================================================================

PRAGMA foreign_keys = OFF;

CREATE TABLE uporabnik_nov (
    id                        INTEGER PRIMARY KEY,
    uporabnisko_ime           TEXT NOT NULL UNIQUE CHECK (length(trim(uporabnisko_ime)) BETWEEN 3 AND 120),
    -- BCrypt zgostitev gesla (nikoli cistopis)
    geslo_hash                TEXT NOT NULL,
    vloga                     TEXT NOT NULL CHECK (vloga IN ('ADMIN', 'ORGANIZATOR', 'IGRALEC', 'NAVIJAC')),
    status                    TEXT NOT NULL DEFAULT 'POTRJEN' CHECK (status IN ('CAKA', 'POTRJEN', 'ZAVRNJEN')),
    -- povezava na sifrant igralcev; en igralec ima lahko najvec en racun
    id_igralec                INTEGER UNIQUE REFERENCES igralec (id),
    -- klub, ki mu uporabnik pripada (za organizatorja; doloci ga admin ob potrditvi)
    id_klub                   INTEGER REFERENCES klub (id),
    -- kar je oseba navedla ob registraciji (podlaga adminu za potrditev)
    prijavljeno_ime           TEXT,
    prijavljeni_priimek       TEXT,
    id_klub_zelja             INTEGER REFERENCES klub (id),
    aktiven                   INTEGER NOT NULL DEFAULT 1 CHECK (aktiven IN (0, 1)),
    ustvarjen_ob              TEXT NOT NULL,
    email_potrjen_ob          TEXT,
    prijavljeni_datum_rojstva TEXT,
    email_skrbnika            TEXT,
    skrbnik_potrjen_ob        TEXT,
    vir_povezave              TEXT CHECK (vir_povezave IS NULL OR vir_povezave IN ('ADMIN', 'SAMODEJNO')),
    povezan_ob                TEXT,
    obvestila_po_kolu         INTEGER NOT NULL DEFAULT 1 CHECK (obvestila_po_kolu IN (0, 1)),
    zeton_odjave              TEXT,
    -- potrjen racun igralca mora biti povezan z igralcem, sicer ne ve, cigav je
    CHECK (vloga <> 'IGRALEC' OR status <> 'POTRJEN' OR id_igralec IS NOT NULL),
    -- navijac ne igra: zapisa v sifrantu igralcev nima nikoli
    CHECK (vloga <> 'NAVIJAC' OR id_igralec IS NULL)
);

INSERT INTO uporabnik_nov (id, uporabnisko_ime, geslo_hash, vloga, status, id_igralec, id_klub,
                           prijavljeno_ime, prijavljeni_priimek, id_klub_zelja, aktiven, ustvarjen_ob,
                           email_potrjen_ob, prijavljeni_datum_rojstva, email_skrbnika, skrbnik_potrjen_ob,
                           vir_povezave, povezan_ob, obvestila_po_kolu, zeton_odjave)
SELECT id, uporabnisko_ime, geslo_hash, vloga, status, id_igralec, id_klub,
       prijavljeno_ime, prijavljeni_priimek, id_klub_zelja, aktiven, ustvarjen_ob,
       email_potrjen_ob, prijavljeni_datum_rojstva, email_skrbnika, skrbnik_potrjen_ob,
       vir_povezave, povezan_ob, obvestila_po_kolu, zeton_odjave
FROM uporabnik;

DROP TABLE uporabnik;
ALTER TABLE uporabnik_nov RENAME TO uporabnik;

CREATE INDEX idx_uporabnik_status ON uporabnik (status);
CREATE INDEX idx_uporabnik_igralec ON uporabnik (id_igralec);
CREATE INDEX idx_uporabnik_klub ON uporabnik (id_klub);
CREATE UNIQUE INDEX idx_uporabnik_zeton_odjave ON uporabnik (zeton_odjave)
    WHERE zeton_odjave IS NOT NULL;

-- Preveri, da prezidava ni pustila osirotelih vrstic (tuji kljuci so bili
-- izklopljeni, zato tega ni preverjal nihce drug).
PRAGMA foreign_key_check;

PRAGMA foreign_keys = ON;
