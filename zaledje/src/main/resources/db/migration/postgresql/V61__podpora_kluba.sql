-- ============================================================================
-- Turnirko: podpora klubu (V61)
-- Dvojnica db/migration/sqlite/V61__podpora_kluba.sql za PostgreSQL; razlaga
-- (en racun podpira najvec en klub, stetje podpornikov v storitvi) je v SQLite
-- razlicici.
-- ============================================================================

CREATE TABLE podpora_kluba (
    id_racun  bigint NOT NULL PRIMARY KEY REFERENCES uporabnik (id) ON DELETE CASCADE,
    id_klub   bigint NOT NULL REFERENCES klub (id) ON DELETE CASCADE,
    od        timestamp(3) NOT NULL
);

CREATE INDEX idx_podpora_kluba_klub ON podpora_kluba (id_klub);
