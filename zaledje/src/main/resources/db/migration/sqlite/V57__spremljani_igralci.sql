-- ============================================================================
-- Turnirko: igralci, ki jih uporabnik spremlja
--
-- Domaca stran dobi sklop "Moj igralec": starsa, soigralca ali klubskega
-- cloveka zanima, kako gre doloceni osebi - ne le ligi, v kateri igra. Kot pri
-- spremljanih ligah (V8) je izbor osebna nastavitev racuna in ne zapis o
-- tekmovanju, zato ima svojo tabelo.
--
-- Koliko igralcev sme racun spremljati, v shemi NI: to je pravilo paketa
-- (SpremljaniIgralciStoritev.NAJVEC_BREZPLACNO) in se bo ob placljivem paketu
-- Navijac spremenilo, shema pa naj zato ostane enaka.
--
-- Ob izbrisu racuna ali igralca izbor odpade z njim (ON DELETE CASCADE): brez
-- enega od obeh vrstica ne pomeni nicesar. Igralce sicer arhiviramo in ne
-- brisemo; zdruzitev dveh zapisov igralca vrstice preusmeri
-- (IgralciZdruzitevStoritev).
-- ============================================================================

CREATE TABLE spremljan_igralec (
    id_racun    INTEGER NOT NULL REFERENCES uporabnik (id) ON DELETE CASCADE,
    id_igralec  INTEGER NOT NULL REFERENCES igralec (id) ON DELETE CASCADE,
    dodano_ob   TEXT NOT NULL,
    PRIMARY KEY (id_racun, id_igralec)
);

-- izbor racuna bere primarni kljuc; ta indeks je za zdruzitev igralcev in
-- brisanje igralca (iskanje po drugem stolpcu kljuca)
CREATE INDEX idx_spremljan_igralec_igralec ON spremljan_igralec (id_igralec);
