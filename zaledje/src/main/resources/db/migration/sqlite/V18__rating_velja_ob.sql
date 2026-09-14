-- ============================================================================
-- Turnirko rating: dnevnik dobi casovno os (velja_ob)
--
-- Dnevnik je doslej poznal samo "ustvarjen_ob" - trenutek, ko je vrstica
-- nastala. Pri uvozu to pomeni, da ima vseh 183 tisoc zapisov isti dan (dan
-- uvoza), zato iz dnevnika ni bilo mogoce niti narisati napredka igralca po
-- letih niti povedati, kaksen je bil rating na dolocen datum.
--
-- "velja_ob" je datum in ura TEKME, ki je spremembo povzrocila - torej kdaj
-- sprememba VELJA, ne kdaj je bila zapisana. Sele s tem stolpcem sta mogoca:
--   * ponovni preracun od datuma (popravek napacno vnesenega rezultata),
--   * odbitek za neaktivnost, ki mora pasti na svoj datum,
--   * graf napredka na profilu in "rating ob koncu sezone".
--
-- Polnjenje za nazaj: turnirska tekma dobi datum zacetka turnirja ob polnoci
-- (vir dneva ne pove natancneje - enako kot pri uvozu), ligaska pa cas
-- srecanja. Postavitveni zapisi (brez tekme) obdrzijo svoj "ustvarjen_ob".
--
-- Zakaj prezidava tabele in ne samo ALTER TABLE ADD COLUMN: stolpec mora biti
-- NOT NULL, tega pa SQLite brez privzete vrednosti ne zna dodati. Na
-- rating_zgodovina ne kaze noben tuji kljuc, zato prezidava ne potrebuje
-- izklopa kljucev (za razliko od V5, V9, V11, V12 in V13) in tece v
-- transakciji kot vsaka druga migracija.
--
-- Oblika zapisa je ISO z uro ("2014-03-01T00:00"), ker stolpec v kodi bere in
-- pise pretvornik CasKotBesedilo - gonilnik sqlite-jdbc bi uro sicer odrezal.
-- ============================================================================

ALTER TABLE rating_zgodovina ADD COLUMN velja_ob TEXT;

-- turnirska tekma -> datum zacetka turnirja
UPDATE rating_zgodovina
   SET velja_ob = (SELECT tu.datum_zacetka
                     FROM tekma t
                     JOIN dogodek d ON d.id = t.id_dogodek
                     JOIN turnir tu ON tu.id = d.id_turnir
                    WHERE t.id = rating_zgodovina.id_tekma)
 WHERE id_tekma IS NOT NULL;

-- ligaska tekma -> cas srecanja (odigrano, sicer predviden termin)
UPDATE rating_zgodovina
   SET velja_ob = (SELECT COALESCE(s.odigran_ob, s.predviden_zacetek)
                     FROM tekma_srecanja ts
                     JOIN srecanje s ON s.id = ts.id_srecanje
                    WHERE ts.id = rating_zgodovina.id_tekma_srecanja)
 WHERE id_tekma_srecanja IS NOT NULL;

-- Ligaska srecanja BREZ termina: uvoz jim datuma ni dal (5 lig sezone
-- 2024/25, 4892 zapisov). Brez tega bi veljala na dan uvoza in bi se na
-- lestvici brala kot "odigrano ta teden" - crta gibanja in premik mesta bi
-- vsem tem igralcem skocila v sedanjost. Liga pa sezono pozna, zato zapis
-- postavimo na zacetek sezone ("2024-2025" -> 1. september 2024), premaknjen
-- za 14 dni na kolo. Priblizek je, a je v pravi sezoni - dan uvoza ni.
UPDATE rating_zgodovina
   SET velja_ob = (
        SELECT date(substr(l.sezona, 1, 4) || '-09-01',
                    '+' || ((s.kolo - 1) * 14) || ' days') || 'T00:00'
          FROM tekma_srecanja ts
          JOIN srecanje s ON s.id = ts.id_srecanje
          JOIN liga l ON l.id = s.id_liga
         WHERE ts.id = rating_zgodovina.id_tekma_srecanja
           AND l.sezona GLOB '[0-9][0-9][0-9][0-9]*')
 WHERE id_tekma_srecanja IS NOT NULL
   AND velja_ob IS NULL
   AND EXISTS (SELECT 1
                 FROM tekma_srecanja ts
                 JOIN srecanje s ON s.id = ts.id_srecanje
                 JOIN liga l ON l.id = s.id_liga
                WHERE ts.id = rating_zgodovina.id_tekma_srecanja
                  AND l.sezona GLOB '[0-9][0-9][0-9][0-9]*');

-- postavitveni zapisi in tekme, ki jim vir datuma ni dal
UPDATE rating_zgodovina SET velja_ob = ustvarjen_ob WHERE velja_ob IS NULL;

-- enotna oblika: zapisi brez ure (tako jih pise gonilnik) dobijo polnoc
UPDATE rating_zgodovina
   SET velja_ob = substr(velja_ob, 1, 10) || 'T00:00'
 WHERE length(velja_ob) = 10;
UPDATE rating_zgodovina
   SET velja_ob = replace(velja_ob, ' ', 'T')
 WHERE velja_ob LIKE '% %';

-- prezidava: velja_ob postane obvezen
CREATE TABLE rating_zgodovina_nova (
    id                  INTEGER PRIMARY KEY,
    id_igralec          INTEGER NOT NULL REFERENCES igralec (id),
    sistem              TEXT NOT NULL,
    id_tekma            INTEGER REFERENCES tekma (id),
    id_tekma_srecanja   INTEGER REFERENCES tekma_srecanja (id),
    sprememba           INTEGER NOT NULL,
    nova_vrednost       INTEGER NOT NULL,
    -- kdaj sprememba VELJA (cas tekme), ne kdaj je bila zapisana
    velja_ob            TEXT NOT NULL,
    ustvarjen_ob        TEXT NOT NULL,
    -- zapis se veze bodisi na turnirsko bodisi na ligasko tekmo, nikoli na obe
    CHECK (id_tekma IS NULL OR id_tekma_srecanja IS NULL)
);

INSERT INTO rating_zgodovina_nova
    (id, id_igralec, sistem, id_tekma, id_tekma_srecanja, sprememba, nova_vrednost,
     velja_ob, ustvarjen_ob)
SELECT id, id_igralec, sistem, id_tekma, id_tekma_srecanja, sprememba, nova_vrednost,
       velja_ob, ustvarjen_ob
  FROM rating_zgodovina;

DROP TABLE rating_zgodovina;
ALTER TABLE rating_zgodovina_nova RENAME TO rating_zgodovina;

CREATE INDEX idx_rating_zgodovina_igralec ON rating_zgodovina (id_igralec);
CREATE INDEX idx_rating_zgodovina_tekma ON rating_zgodovina (id_tekma);
CREATE INDEX idx_rating_zgodovina_tekma_srecanja ON rating_zgodovina (id_tekma_srecanja);
-- poizvedbe "kaj je bilo od datuma naprej" (preracun, graf, lestvica)
CREATE INDEX idx_rating_zgodovina_velja ON rating_zgodovina (sistem, velja_ob);
