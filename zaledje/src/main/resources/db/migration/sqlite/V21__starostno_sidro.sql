-- ============================================================================
-- Turnirko rating: starostno sidro in uvrstitev novinca
--
-- Doslej je vsak novinec zacel pri 1000, ne glede na starost. Zato sta se dva
-- novinca v U11, ki sta igrala med sabo, ustalila pri priblizno isti stevilki
-- kot dva novinca v U19 - kar ni res, saj so starejsi igralci skoraj vedno
-- mocnejsi. Popravek ima dva dela:
--
--   1) STAROSTNO SIDRO (tabela starostno_sidro): pricakovana vrednost igralca
--      dane starosti in spola, preden o njem karkoli vemo. Novinec zacne tu in
--      ne pri 1000.
--
--   2) UVRSTITEV PO PRVEM DOGODKU: dokler traja prvi dan igranja, se rating ne
--      sesteva po tekmah, ampak se vsakic znova izracuna iz vseh izidov tega
--      dneva, rahlo potegnjen proti sidru (dve navidezni izenaceni tekmi).
--      Za to sta potrebna dva podatka, ki jih shema doslej ni imela:
--        rating_stanje.prva_tekma_ob  - kdaj se je prvi dan zacel,
--        rating_zgodovina.tocke       - ali je igralec tekmo dobil ali izgubil.
--
-- Vrednosti imajo dva dela, ki ju je treba brati LOCENO:
--
--   OBLIKA (razlike med starostmi in spoloma) je IZMERJENA na tej bazi:
--   mediana ratinga igralcev istega spola in starosti z vsaj 10 obracunanimi
--   tekmami, ki so igrali v zadnjih 36 mesecih. Surova mediana po letih je
--   sunkovita (pri nekaterih letnikih je igralcev pet) in ponekod celo pada z
--   leti, cesar fizikalno ni, zato je zravnana: drsece okno +-1 leto, monotono
--   nepadajoce, odrasli (26+) pa ena skupna vrednost z linearnim mostom od 21.
--   do 26. leta. Oblika je tisto, kar resi U11/U19.
--
--   RAVEN (skupni pribitek +270 vsem vrstica) je MERILO SKALE, ne meritev
--   moci. Elo nima absolutne skale: raven lestvice dolocajo izkljucno vstopne
--   vrednosti novincev. Ce novinci vstopajo pri goli mediani svoje starosti,
--   lestvica pade - izmerjeno na vseh 91.741 tekmah se je povprecje poseslo z
--   965 na 703, spodnji del pa se je zabil v dno 100. S pribitkom 270 se
--   povprecje ohrani (965 -> 968) in dno ne veze vec (najnizji 145).
--   Pribitek je torej izmerjena konstanta in ne ugibanje; ce se formula
--   spremeni, ga je treba izmeriti znova.
--
-- POZOR: tabela je POSNETEK in se ne sme iterativno izpeljevati iz lastnega
-- izhoda. Sidro, izracunano iz lestvice, ki so jo oblikovali novinci, zasidrani
-- po prejsnjem sidru, je povratna zanka. Osvezitev je zavestna odlocitev, ne
-- samodejno opravilo - in ob vsaki je treba raven umeriti znova.
-- ============================================================================

CREATE TABLE starostno_sidro (
    spol        TEXT NOT NULL CHECK (spol IN ('MOSKI', 'ZENSKI')),
    -- starost po 11. clenu PST (leto sezone minus letnica rojstva), kot pri
    -- StarostniPas - da imata igralec in sidro isto merilo starosti
    starost     INTEGER NOT NULL CHECK (starost BETWEEN 0 AND 120),
    vrednost    INTEGER NOT NULL CHECK (vrednost BETWEEN 100 AND 3000),
    PRIMARY KEY (spol, starost)
);

INSERT INTO starostno_sidro (spol, starost, vrednost) VALUES
    ('MOSKI', 6, 1139),
    ('MOSKI', 7, 1139),
    ('MOSKI', 8, 1139),
    ('MOSKI', 9, 1139),
    ('MOSKI', 10, 1139),
    ('MOSKI', 11, 1139),
    ('MOSKI', 12, 1139),
    ('MOSKI', 13, 1139),
    ('MOSKI', 14, 1139),
    ('MOSKI', 15, 1152),
    ('MOSKI', 16, 1152),
    ('MOSKI', 17, 1213),
    ('MOSKI', 18, 1226),
    ('MOSKI', 19, 1226),
    ('MOSKI', 20, 1269),
    ('MOSKI', 21, 1327),
    ('MOSKI', 22, 1368),
    ('MOSKI', 23, 1409),
    ('MOSKI', 24, 1451),
    ('MOSKI', 25, 1492),
    ('MOSKI', 26, 1533),
    ('MOSKI', 27, 1533),
    ('MOSKI', 28, 1533),
    ('MOSKI', 29, 1533),
    ('MOSKI', 30, 1533),
    ('MOSKI', 31, 1533),
    ('MOSKI', 32, 1533),
    ('MOSKI', 33, 1533),
    ('MOSKI', 34, 1533),
    ('MOSKI', 35, 1533),
    ('MOSKI', 36, 1533),
    ('MOSKI', 37, 1533),
    ('MOSKI', 38, 1533),
    ('MOSKI', 39, 1533),
    ('MOSKI', 40, 1533),
    ('ZENSKI', 6, 1089),
    ('ZENSKI', 7, 1089),
    ('ZENSKI', 8, 1089),
    ('ZENSKI', 9, 1089),
    ('ZENSKI', 10, 1089),
    ('ZENSKI', 11, 1089),
    ('ZENSKI', 12, 1089),
    ('ZENSKI', 13, 1089),
    ('ZENSKI', 14, 1089),
    ('ZENSKI', 15, 1126),
    ('ZENSKI', 16, 1141),
    ('ZENSKI', 17, 1203),
    ('ZENSKI', 18, 1235),
    ('ZENSKI', 19, 1235),
    ('ZENSKI', 20, 1235),
    ('ZENSKI', 21, 1235),
    ('ZENSKI', 22, 1299),
    ('ZENSKI', 23, 1363),
    ('ZENSKI', 24, 1428),
    ('ZENSKI', 25, 1492),
    ('ZENSKI', 26, 1556),
    ('ZENSKI', 27, 1556),
    ('ZENSKI', 28, 1556),
    ('ZENSKI', 29, 1556),
    ('ZENSKI', 30, 1556),
    ('ZENSKI', 31, 1556),
    ('ZENSKI', 32, 1556),
    ('ZENSKI', 33, 1556),
    ('ZENSKI', 34, 1556),
    ('ZENSKI', 35, 1556),
    ('ZENSKI', 36, 1556),
    ('ZENSKI', 37, 1556),
    ('ZENSKI', 38, 1556),
    ('ZENSKI', 39, 1556),
    ('ZENSKI', 40, 1556);

-- ---------------------------------------------------------------------------
-- Kdaj se je igralcev prvi dan igranja zacel. Iz njega se ve, katere tekme so
-- se v obdobju uvrstitve. Polni se iz dnevnika (najstarejsi zapis, vezan na
-- tekmo); ponovni preracun ga izpelje iz istega zaporedja.
-- ---------------------------------------------------------------------------

ALTER TABLE rating_stanje ADD COLUMN prva_tekma_ob TEXT;

UPDATE rating_stanje
   SET prva_tekma_ob = (
        SELECT MIN(z.velja_ob)
          FROM rating_zgodovina z
         WHERE z.id_igralec = rating_stanje.id_igralec
           AND z.sistem = rating_stanje.sistem
           AND (z.id_tekma IS NOT NULL OR z.id_tekma_srecanja IS NOT NULL));

-- ---------------------------------------------------------------------------
-- Ali je rating POSTAVIL clovek (administrator) in ne rezultati. Postavljenega
-- igralca uvrstitev novinca ne sme povoziti: kdor je rekel "ta igralec je
-- 1500", je s tem povedal vec, kot pove njegov prvi turnir pri nas. Isto velja
-- za redke goste, ki jih bo postavljala zunanja uvrstitev (Jorgic).
-- Polni se iz dnevnika: postavitveni zapis je tisti brez tekme.
-- ---------------------------------------------------------------------------

ALTER TABLE rating_stanje ADD COLUMN postavljen INTEGER NOT NULL DEFAULT 0
    CHECK (postavljen IN (0, 1));

UPDATE rating_stanje
   SET postavljen = 1
 WHERE EXISTS (
        SELECT 1 FROM rating_zgodovina z
         WHERE z.id_igralec = rating_stanje.id_igralec
           AND z.sistem = rating_stanje.sistem
           AND z.id_tekma IS NULL AND z.id_tekma_srecanja IS NULL);

-- ---------------------------------------------------------------------------
-- Ali je igralec tekmo dobil (1) ali izgubil (0). Dnevnik je doslej vedel samo
-- za spremembo tock, iz nje pa izida ni mogoce zanesljivo prebrati: sprememba
-- je lahko tudi 0 (favorit premaga mnogo sibkejsega in zaokrozi na nic).
-- Izid potrebuje uvrstitev novinca, uporabila pa ga bo tudi razlaga spremembe
-- v vmesniku. Postavitveni zapisi (brez tekme) ostanejo NULL - niso tekma.
-- ---------------------------------------------------------------------------

ALTER TABLE rating_zgodovina ADD COLUMN tocke INTEGER
    CHECK (tocke IS NULL OR tocke IN (0, 1));

UPDATE rating_zgodovina
   SET tocke = (
        SELECT CASE WHEN p.id_igralec = rating_zgodovina.id_igralec THEN 1 ELSE 0 END
          FROM tekma t JOIN prijava p ON p.id = t.id_zmagovalec_prijava
         WHERE t.id = rating_zgodovina.id_tekma)
 WHERE id_tekma IS NOT NULL;

UPDATE rating_zgodovina
   SET tocke = (
        SELECT CASE
                 WHEN ts.zmagovalec_stran = 'DOMACI'
                      AND ts.id_igralec_domaci = rating_zgodovina.id_igralec THEN 1
                 WHEN ts.zmagovalec_stran = 'GOST'
                      AND ts.id_igralec_gost = rating_zgodovina.id_igralec THEN 1
                 ELSE 0
               END
          FROM tekma_srecanja ts
         WHERE ts.id = rating_zgodovina.id_tekma_srecanja)
 WHERE id_tekma_srecanja IS NOT NULL;

-- ---------------------------------------------------------------------------
-- Indeks za uvrstitev novinca. Poizvedba uvrstitve poveze dnevnik sam s sabo
-- (igralceva vrstica in nasprotnikova vrstica iste tekme). Brez tega indeksa
-- sqlite ne zna oceniti, da je igralceva stran ozja, in zacne z NASPROTNIKOVO:
-- za vsako poizvedbo prebere vseh 183 tisoc vrstic. Izmerjeno: 2,7 sekunde na
-- poizvedbo namesto 0,8 milisekunde - celoten preracun bi namesto minut trajal
-- ure.
-- ---------------------------------------------------------------------------

CREATE INDEX idx_rating_zgodovina_igralec_velja
    ON rating_zgodovina (sistem, id_igralec, velja_ob);
