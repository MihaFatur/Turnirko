-- ============================================================================
-- Turnirko: ura srecanja v kolu
--
-- V31 je ure lige (liga.ure_srecanj) opisala kot "toliko srecanj v kolu". Pravi
-- pomen je ozji: kolo je VECER, v katerem se odigra vec KROGOV kroznega sistema
-- zapored - ob 18.30 prvega, ob 19.45 drugega - in vsaka ekipa ta vecer igra
-- toliko srecanj, kolikor je ur. Kol je zato toliko manj. Zapis stolpca ostane
-- isti, zato ga ta migracija ne spreminja.
--
-- Srecanje si zapomni, ob kateri uri kola se igra (ura_v_kolu, 0 = prva).
-- Zacetek sam tega ne pove zanesljivo: liga brez datuma prvega kola ob zrebu
-- zacetkov nima, organizator pa sme uro posameznemu srecanju prestaviti -
-- polnilo terminov mora vseeno vedeti, katero uro lige srecanju vrne. Pri kolu
-- kroznega sistema je stolpec prazen.
--
-- Stolpec je sprva stal v V31, ki pa je bila v bazah ze uveljavljena brez njega;
-- sprememba uveljavljene migracije podre Flywayevo kontrolno vsoto in zagon.
-- ============================================================================

ALTER TABLE srecanje ADD COLUMN ura_v_kolu INTEGER
    CHECK (ura_v_kolu IS NULL OR ura_v_kolu >= 0);
