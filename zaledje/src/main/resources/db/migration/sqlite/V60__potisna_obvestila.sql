-- ============================================================================
-- Turnirko: potisna obvestila med turnirjem (faza 3 predloga »paket Navijac«)
--
-- Navijac s paketom dobi obvestilo v telefon (Web Push, brez aplikacije), ko je
-- odigrana tekma igralca, ki ga spremlja ali ima v druzini.
--
--   potisna_naprava   ena vrstica na napravo (brskalnik), ki je dovolila obvestila:
--                     naslov potisne storitve (endpoint) in kljuca, s katerima se
--                     sporocilo sifrira za to napravo (RFC 8291). Endpoint je enolicen:
--                     ista naprava je ena vrstica, tudi ce se na njej zamenja racun.
--                     Ob izbrisu racuna naprave odpadejo z njim.
--   potisni_dogodek   kaj je ze poslano: kljuc je tekma in igralec ("T12:45" =
--                     turnirska tekma 12, igralec 45; "L7:45" = ligaska tekma
--                     srecanja 7). Preracun ratinga zbrise in znova zapise vrstice
--                     dnevnika z novimi id-ji, zato je dnevnik ratinga premalo, da bi
--                     se ista tekma ne poslala dvakrat.
-- ============================================================================

CREATE TABLE potisna_naprava (
    id             INTEGER PRIMARY KEY,
    id_racun       INTEGER NOT NULL REFERENCES uporabnik (id) ON DELETE CASCADE,
    endpoint       TEXT NOT NULL UNIQUE,
    p256dh         TEXT NOT NULL,
    auth           TEXT NOT NULL,
    ustvarjena_ob  TEXT NOT NULL
);

CREATE INDEX idx_potisna_naprava_racun ON potisna_naprava (id_racun);

CREATE TABLE potisni_dogodek (
    kljuc       TEXT NOT NULL PRIMARY KEY,
    poslano_ob  TEXT NOT NULL
);
