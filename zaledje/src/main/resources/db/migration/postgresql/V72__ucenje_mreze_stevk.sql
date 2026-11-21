-- Branje listkov F6: ucenje mreze stevk brez napak (docs/NACRT-BRANJE-LISTKOV.md).

-- Zgodovina ucenja: vsaka razlicica pove, kako je nastala (nocno ali na zahtevo
-- admina), na cem je bila izmerjena in zakaj ni bila sprejeta. Prekinjeno ucenje
-- (casovna meja) nima utezi, zapis pa ostane, da admin vidi, kaj se je zgodilo.
ALTER TABLE mreza_stevk ALTER COLUMN utezi DROP NOT NULL;
ALTER TABLE mreza_stevk ALTER COLUMN natancnost DROP NOT NULL;
ALTER TABLE mreza_stevk
    ADD COLUMN nacin        text NOT NULL DEFAULT 'NOC' CHECK (nacin IN ('NOC', 'ROCNO')),
    ADD COLUMN preizkusnih  integer CHECK (preizkusnih >= 0),
    ADD COLUMN mnist_prej   real,
    ADD COLUMN mnist        real,
    -- zlati nabor: delez pravilno prebranih celic celih listkov (F1.5); NULL, ce
    -- je bil nabor premajhen za primerjavo
    ADD COLUMN zlatih       integer CHECK (zlatih >= 0),
    ADD COLUMN zlati_prej   real,
    ADD COLUMN zlati        real,
    ADD COLUMN trajanje_ms  integer CHECK (trajanje_ms >= 0),
    ADD COLUMN razlog       text,
    -- »Vrni prejsnjo«: umaknjena razlicica se ne nalozi vec, ostane v zgodovini
    ADD COLUMN umaknjena_ob timestamp(3),
    ADD CONSTRAINT mreza_stevk_sprejeta_ima_utezi CHECK (utezi IS NOT NULL OR NOT sprejeta),
    ADD CONSTRAINT mreza_stevk_naucena_ima_natancnost CHECK (natancnost IS NOT NULL OR utezi IS NULL);

-- Mocna oznaka: celico je organizator izrecno popravil (ali vpisal, ker je bralnik
-- ni prebral) ali jo je popravil poznejsi popravek izida. Pri ucenju steje dvojno.
-- Stevka listka ima odslej tudi celico (stolpec celica iz V70): niz * 2 + stolpec
-- (0 levo, 1 desno), rezultat 100 + stolpec - da popravek izida ve, katera celica
-- je bila mocna. Starejse stevke listkov celice nimajo (NULL).
ALTER TABLE stevka_listka ADD COLUMN popravljena boolean NOT NULL DEFAULT false;

-- Popravek izida tekme izbrise in znova zapise stevke njenih listkov.
CREATE INDEX idx_stevka_listka_branje ON stevka_listka (id_branje) WHERE id_branje IS NOT NULL;
