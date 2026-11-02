-- ============================================================================
-- Turnirko: igralci, ki jih uporabnik spremlja (V57)
-- Dvojnica db/migration/sqlite/V57__spremljani_igralci.sql za PostgreSQL.
-- Razlaga (pravilo paketa je v storitvi, ne v shemi) je v SQLite razlicici.
-- ============================================================================

CREATE TABLE spremljan_igralec (
    id_racun    bigint NOT NULL REFERENCES uporabnik (id) ON DELETE CASCADE,
    id_igralec  bigint NOT NULL REFERENCES igralec (id) ON DELETE CASCADE,
    dodano_ob   timestamp(3) NOT NULL,
    PRIMARY KEY (id_racun, id_igralec)
);

-- izbor racuna bere primarni kljuc; ta indeks je za zdruzitev igralcev in
-- brisanje igralca (iskanje po drugem stolpcu kljuca)
CREATE INDEX idx_spremljan_igralec_igralec ON spremljan_igralec (id_igralec);
