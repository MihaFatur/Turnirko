-- Par igralcev, za katera je administrator odlocil, da NISTA ista oseba
-- (stran Igralci, pogled »Cistka duplikatov«, oktober 2026).
--
-- Isto ime imata lahko dve osebi (Novak, Horvat), zato mora odlocitev »ni
-- duplikat« obstati: brez zapisa bi isti par ob vsakem odprtju cistke
-- vstal znova. Par je neurejen, zato se hrani urejeno (manjsi id prvi) in
-- ga baza zavrne v drugi obliki - tako ne more nastati dvojni zapis.
--
-- Zapis izgine skupaj z enim od igralcev (CASCADE). Zdruzitev igralca
-- (IgralciZdruzitevStoritev) zapise odvecnega igralca pobrise izrecno, ker
-- igralca ne brise, ampak arhivira.

CREATE TABLE igralec_ni_duplikat (
    id_igralec_a  INTEGER NOT NULL REFERENCES igralec (id) ON DELETE CASCADE,
    id_igralec_b  INTEGER NOT NULL REFERENCES igralec (id) ON DELETE CASCADE,
    ustvarjen_ob  TEXT NOT NULL DEFAULT (datetime('now')),
    PRIMARY KEY (id_igralec_a, id_igralec_b),
    CHECK (id_igralec_a < id_igralec_b)
);

CREATE INDEX idx_igralec_ni_duplikat_b ON igralec_ni_duplikat (id_igralec_b);
