-- ============================================================================
-- Turnirko: vloga NAVIJAC (racun osebe, ki ne igra)
-- Dvojnica db/migration/sqlite/V55__vloga_navijac.sql za PostgreSQL.
--
-- SQLite omejitve CHECK ne zna spremeniti, zato je tam prezidava tabele
-- uporabnik; PostgreSQL jo spremeni neposredno, brez prezidave in brez
-- izklopa tujih kljucev. Rezultat je isti: vloga sme biti tudi NAVIJAC, ta pa
-- nikoli nima zapisa v sifrantu igralcev.
-- ============================================================================

ALTER TABLE uporabnik DROP CONSTRAINT uporabnik_vloga_check;
ALTER TABLE uporabnik ADD CONSTRAINT uporabnik_vloga_check
    CHECK (vloga IN ('ADMIN', 'ORGANIZATOR', 'IGRALEC', 'NAVIJAC'));

-- navijac ne igra: zapisa v sifrantu igralcev nima nikoli
ALTER TABLE uporabnik ADD CONSTRAINT uporabnik_navijac_brez_igralca
    CHECK (vloga <> 'NAVIJAC' OR id_igralec IS NULL);
