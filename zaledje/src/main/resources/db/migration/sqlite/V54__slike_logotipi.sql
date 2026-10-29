-- ============================================================================
-- Turnirko: slike in logotip tekmovanja (V54, okt 2026)
--
-- Organizator ligi ali turnirju doda logotip, ki stoji v glavi strani, na
-- Turnirkovem ekipnem zapisniku, na zrebu za tisk in v predogledu ob deljenju
-- povezave. Do zdaj je bil logotip en sam (Liga Savinja) in vgrajen v vmesnik.
--
-- slika            ena nalozena slika: zgostitev (SHA-256 razlicice TISK) je
--                  njen javni naslov (/api/v1/slike/{zgostitev}) in hkrati
--                  odstrani dvojnike - ista datoteka, nalozena dvakrat, je ena
--                  vrstica. sirina/visina sta meri razlicice TISK (vmesnik iz
--                  njiju ve razmerje stranic, preden se slika nalozi).
--   namen          zaenkrat samo LOGOTIP (profilna slika igralca bo svoj namen
--                  s svojimi razlicicami)
--   id_nalozil     racun, ki je sliko nalozil - po njem organizator vidi svoje
--                  logotipe in jih uporabi na drugem tekmovanju. Stevilka brez
--                  entitete (kot uporaba_kode_popusta): ob izbrisu racuna slika
--                  ostane, ker jo lahko se vedno nosi kaksno tekmovanje.
--
-- slika_razlicica  bajti, locena tabela: liga in turnir sliko nalozita ob
--                  vsakem branju (velikost, razmerje), bajtov pa nikoli - te
--                  bere samo pot, ki sliko postreze.
--   velikost       TISK (daljsa stranica do 1200 px - zapisnik, zreb),
--                  ZASLON (do 480 px - glava strani na telefonu in namizju),
--                  DELJENJE (1200 x 630, logotip na papirni podlagi -
--                  og:image; prosojen PNG bi WhatsApp v temnem nacinu pokazal
--                  na crnem in crn logotip bi izginil)
--   vrsta          PNG ali JPEG - vrsto doloci streznik ob ponovnem zapisu,
--                  ne koncnica ali glava, ki ju je poslal brskalnik
--
-- Vsaka slika je na strezniku prebrana in zapisana znova, zato v bazi ni
-- metapodatkov iz datoteke (EXIF s krajem in napravo).
-- ============================================================================

CREATE TABLE slika (
    id             INTEGER PRIMARY KEY,
    zgostitev      TEXT NOT NULL UNIQUE CHECK (length(zgostitev) = 64),
    namen          TEXT NOT NULL CHECK (namen IN ('LOGOTIP')),
    sirina         INTEGER NOT NULL CHECK (sirina > 0),
    visina         INTEGER NOT NULL CHECK (visina > 0),
    id_nalozil     INTEGER REFERENCES uporabnik(id) ON DELETE SET NULL,
    ustvarjena_ob  TEXT NOT NULL
);

CREATE INDEX idx_slika_nalozil ON slika(id_nalozil);

CREATE TABLE slika_razlicica (
    id         INTEGER PRIMARY KEY,
    id_slika   INTEGER NOT NULL REFERENCES slika(id) ON DELETE CASCADE,
    velikost   TEXT NOT NULL CHECK (velikost IN ('TISK', 'ZASLON', 'DELJENJE')),
    vrsta      TEXT NOT NULL CHECK (vrsta IN ('image/png', 'image/jpeg')),
    sirina     INTEGER NOT NULL CHECK (sirina > 0),
    visina     INTEGER NOT NULL CHECK (visina > 0),
    vsebina    BLOB NOT NULL,
    UNIQUE (id_slika, velikost)
);

-- Logotip je prikaz, ne pravilo tekmovanja: sme ga tudi uvozeno tekmovanje in v
-- vsakem stanju (kot skrite_zanimivosti, V51). Slike, ki jo nosi tekmovanje, ni
-- mogoce izbrisati (brez ON DELETE - storitev to pove z besedami).
ALTER TABLE liga ADD COLUMN id_logotip INTEGER REFERENCES slika(id);
ALTER TABLE turnir ADD COLUMN id_logotip INTEGER REFERENCES slika(id);
