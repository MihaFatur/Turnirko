-- ============================================================================
-- Turnirko: podpora klubu (faza 3 predloga »paket Navijac«, okt 2026)
--
-- Navijac s placljivim paketom izbere klub, ki ga podpira. Ko ima klub dovolj
-- podpornikov, dobi njegov organizator popust na organizatorski paket (stopnje:
-- PodporaKlubaStoritev). Izplacil ni: iz s.p. ne tece noben denar klubu, klub pa
-- dobi razlog, da Navijaca priporoci starsem.
--
-- En racun podpira najvec en klub (primarni kljuc je racun): menjava kluba je
-- prepis vrstice. Ob izbrisu racuna ali kluba podpora odpade z njim. Stevilo
-- podpornikov klubu steje samo racune, ki imajo paket Navijac ZDAJ (storitev, ne
-- shema): vrstica sama ni dokaz, da podpornik se placuje.
-- ============================================================================

CREATE TABLE podpora_kluba (
    id_racun  INTEGER NOT NULL PRIMARY KEY REFERENCES uporabnik (id) ON DELETE CASCADE,
    id_klub   INTEGER NOT NULL REFERENCES klub (id) ON DELETE CASCADE,
    od        TEXT NOT NULL
);

CREATE INDEX idx_podpora_kluba_klub ON podpora_kluba (id_klub);
