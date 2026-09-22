-- ============================================================================
-- Turnirko: potrditev e-poste, potrditvene kode in samodejna povezava racuna
--
-- Doslej je registracija verjela vsakemu naslovu: kdor je vpisal tujo e-posto,
-- jo je s tem zasedel, admin pa ni vedel, ali naslov sploh komu pripada. Zdaj
-- vsak nov racun potrdi lastnistvo naslova s 6-mestno kodo, ki jo dobi po
-- posti. Sele potrjen naslov steje - nepotrjen racun se po 48 urah izbrise.
--
-- Isti mehanizem sluzi za "pozabljeno geslo" (koda namesto admina) in za
-- soglasje starsa oz. skrbnika: kdor je ob registraciji mlajsi od 15 let
-- (ZVOP-2), navede skrbnikov naslov, ki dobi svojo kodo.
--
-- Racun igralca se lahko povezze z zapisom v sifrantu tudi SAMODEJNO: ce se
-- ob potrjeni e-posti ime, priimek in datum rojstva ujemajo z natanko enim
-- igralcem brez racuna. Vir povezave (ADMIN / SAMODEJNO) in cas ostaneta
-- zapisana, da admin samodejne povezave vidi in jih lahko razveze.
--
-- Stolpci se dodajo z ALTER TABLE (brez prezidave), stanje racuna ostane
-- CAKA / POTRJEN / ZAVRNJEN: "e-posta se ni potrjena" je izpeljanka
-- (email_potrjen_ob IS NULL), ne novo stanje. Obstojeci racuni dobijo naslov
-- potrjen z datumom nastanka - pravilo velja za nove, ne za nazaj.
-- ============================================================================

-- kdaj je lastnik naslova vpisal kodo; NULL = se ni
ALTER TABLE uporabnik ADD COLUMN email_potrjen_ob TEXT;
-- kar je oseba navedla ob registraciji (kot prijavljeno_ime): podlaga za
-- samodejno povezavo, starostni pas in kasneje mladinsko ceno; javno nikoli
ALTER TABLE uporabnik ADD COLUMN prijavljeni_datum_rojstva TEXT;
-- naslov starsa oz. skrbnika (samo pri mlajsih od 15 let ob registraciji)
ALTER TABLE uporabnik ADD COLUMN email_skrbnika TEXT;
ALTER TABLE uporabnik ADD COLUMN skrbnik_potrjen_ob TEXT;
-- kdo je racun povezal z igralcem in kdaj
ALTER TABLE uporabnik ADD COLUMN vir_povezave TEXT
    CHECK (vir_povezave IS NULL OR vir_povezave IN ('ADMIN', 'SAMODEJNO'));
ALTER TABLE uporabnik ADD COLUMN povezan_ob TEXT;

UPDATE uporabnik SET email_potrjen_ob = ustvarjen_ob;
UPDATE uporabnik SET vir_povezave = 'ADMIN' WHERE id_igralec IS NOT NULL;

-- Potrditvene kode. Koda je shranjena samo kot zgostitev (kot geslo); ziva je
-- najvec ena na racun in namen, veljavnost in stevilo poskusov omejujeta
-- ugibanje (6 stevk je milijon moznosti - brez omejitve bi bilo to nic).
CREATE TABLE potrditvena_koda (
    id           INTEGER PRIMARY KEY,
    id_racun     INTEGER NOT NULL REFERENCES uporabnik (id) ON DELETE CASCADE,
    -- EPOSTA = potrditev lastnega naslova, SKRBNIK = soglasje skrbnika,
    -- GESLO = pozabljeno geslo
    namen        TEXT NOT NULL CHECK (namen IN ('EPOSTA', 'SKRBNIK', 'GESLO')),
    koda_hash    TEXT NOT NULL,
    ustvarjen_ob TEXT NOT NULL,
    potece_ob    TEXT NOT NULL,
    poskusi      INTEGER NOT NULL DEFAULT 0 CHECK (poskusi >= 0),
    porabljen_ob TEXT
);

CREATE INDEX idx_potrditvena_koda_racun ON potrditvena_koda (id_racun, namen);
