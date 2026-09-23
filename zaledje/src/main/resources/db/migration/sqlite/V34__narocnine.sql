-- ============================================================================
-- Turnirko: placilni paketi (igralec: brezplacno/premium; organizator: basic/
-- plus/pro)
--
-- Doslej je bil vsak prijavljen in povezan racun brezplacno enak: vsak
-- igralec je videl ves svoj zasebni profil, vsak organizator je neomejeno
-- ustvarjal turnirje in lige. Zdaj racun placa (Stripe, checkout + webhook) -
-- placljiv racun v tabeli uporabnik nastane SELE po uspesnem placilu, zato
-- vrstica tu ni "zgodovina placil" (to je Stripe), ampak TRENUTNO STANJE
-- enega racuna, ki se ob obnovi/preklicu/nadgradnji prepise - isto razmerje
-- kot rating_stanje do rating_zgodovina.
--
-- Obstojeci racuni niso izvzeti, z eno prehodno izjemo: kdor je bil
-- organizator ze pred to migracijo, je turnirje in lige doslej vodil
-- brezplacno, zato dobi ORGANIZATOR_PRO brezplacno za eno leto od danes -
-- v tem letu ga mora admin/lastnik rocno prestaviti na pravi (placan) paket
-- ali podaljsati. Obstojeci igralci posebne vrstice ne dobijo: odsotnost
-- vrstice pomeni BREZPLACNO (privzeto stanje, brez potrebe po zapisu).
-- ============================================================================

CREATE TABLE narocnina (
    id                     INTEGER PRIMARY KEY,
    id_uporabnik           INTEGER NOT NULL UNIQUE REFERENCES uporabnik (id) ON DELETE CASCADE,
    paket                  TEXT NOT NULL CHECK (paket IN (
                               'BREZPLACNO', 'PREMIUM',
                               'ORGANIZATOR_BASIC', 'ORGANIZATOR_PLUS', 'ORGANIZATOR_PRO')),
    ciklus                 TEXT CHECK (ciklus IS NULL OR ciklus IN ('MESECNO', 'LETNO')),
    cena_ob_sklenitvi      REAL,
    -- samo pri PREMIUM: ali je bil racun ob registraciji starejsi od 21 let
    -- (starostni pas U21 ali mlajsi -> 0) - sled cene, glej CenikStoritev
    starejsi_od_21         INTEGER CHECK (starejsi_od_21 IN (0, 1)),
    stripe_customer_id     TEXT,
    stripe_narocnina_id    TEXT UNIQUE,
    status                 TEXT NOT NULL CHECK (status IN (
                               'CAKA_PLACILO', 'AKTIVNA', 'PREKLICANA', 'ZAPADLA')),
    zacetek_ob             TEXT,
    trenutno_obdobje_do    TEXT,
    ustvarjena_ob          TEXT NOT NULL,
    posodobljena_ob        TEXT NOT NULL
);

CREATE INDEX idx_narocnina_stripe ON narocnina (stripe_narocnina_id);

-- Prehodna doba: obstojeci organizatorji dobijo Pro brezplacno za eno leto.
INSERT INTO narocnina (id_uporabnik, paket, ciklus, cena_ob_sklenitvi, status,
                        zacetek_ob, trenutno_obdobje_do, ustvarjena_ob, posodobljena_ob)
SELECT id, 'ORGANIZATOR_PRO', 'LETNO', 0, 'AKTIVNA',
       datetime('now'), datetime('now', '+1 year'), datetime('now'), datetime('now')
  FROM uporabnik
 WHERE vloga = 'ORGANIZATOR';
