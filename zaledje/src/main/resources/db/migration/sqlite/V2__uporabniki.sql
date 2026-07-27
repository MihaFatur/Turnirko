-- ============================================================================
-- Turnirko: uporabniki (administratorji/sodniki)
--
-- Gost NI uporabnik - dostopa brez prijave in ima samo bralni dostop.
-- Administrator se prijavi (uporabnisko ime + geslo) in edini sme
-- spreminjati podatke. Geslo je vedno shranjeno kot BCrypt zgostitev.
-- Zacetnega administratorja ob prvem zagonu ustvari aplikacija sama
-- (glej ZacetniAdmin), zato tu ne vpisujemo nobene vrstice.
-- ============================================================================

CREATE TABLE uporabnik (
    id                  INTEGER PRIMARY KEY,
    uporabnisko_ime     TEXT NOT NULL UNIQUE CHECK (length(trim(uporabnisko_ime)) BETWEEN 3 AND 40),
    -- BCrypt zgostitev gesla (nikoli cistopis)
    geslo_hash          TEXT NOT NULL,
    vloga               TEXT NOT NULL CHECK (vloga IN ('ADMIN')),
    aktiven             INTEGER NOT NULL DEFAULT 1 CHECK (aktiven IN (0, 1)),
    ustvarjen_ob        TEXT NOT NULL
);
