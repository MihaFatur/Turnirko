-- ============================================================================
-- Turnirko: lige, ki jih uporabnik spremlja
--
-- Domaca stran ima sklop "Moje lige": gledalec sam izbere, katere lige ga
-- zanimajo, in vidi samo tiste. Izbor je osebna nastavitev racuna (ne zapis o
-- tekmovanju), zato zivi v svoji tabeli in ne kot stolpec lige.
--
-- Gost izbora nima - njegov brskalnik si zapomni zadnje ogledane lige sam.
-- Ob izbrisu racuna ali lige izbor odpade z njim (ON DELETE CASCADE): brez
-- lige ali brez racuna vrstica ne pomeni nicesar.
-- ============================================================================

CREATE TABLE spremljana_liga (
    id_racun    INTEGER NOT NULL REFERENCES uporabnik (id) ON DELETE CASCADE,
    id_liga     INTEGER NOT NULL REFERENCES liga (id) ON DELETE CASCADE,
    dodano_ob   TEXT NOT NULL,
    PRIMARY KEY (id_racun, id_liga)
);

CREATE INDEX idx_spremljana_liga_racun ON spremljana_liga (id_racun);
