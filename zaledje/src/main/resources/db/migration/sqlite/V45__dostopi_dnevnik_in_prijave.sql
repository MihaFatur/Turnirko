-- Stran »Dostopi« (administrator): dnevnik dogodkov racuna in prijave racuna
-- (oktober 2026, predaja design_handoff_dostopi).
--
-- racun_dogodek: kaj se je z racunom zgodilo (registracija, potrditev
--   naslova, povezava z igralcem, potrditev, placilo, geslo, izklop/vklop).
--   Besedilo je zapisano ob dogodku in se ne racuna nazaj: ime igralca, klub
--   in paket se lahko pozneje spremenijo, zapis o preteklosti pa ne.
-- racun_prijava: ena vrstica na PRIJAVO. Veriga je brez stanja (HTTP Basic,
--   vsaka zahteva je prijava), zato je prijava prva overjena zahteva po
--   najmanj 30 minutah mirovanja (RacunDnevnikStoritev).
--
-- Oba zapisa izgineta z racunom (ON DELETE CASCADE).

CREATE TABLE racun_dogodek (
    id         INTEGER PRIMARY KEY AUTOINCREMENT,
    id_racun   INTEGER NOT NULL REFERENCES uporabnik (id) ON DELETE CASCADE,
    ob         TEXT NOT NULL,
    tekst      TEXT NOT NULL
);

CREATE INDEX idx_racun_dogodek_racun ON racun_dogodek (id_racun, ob);

CREATE TABLE racun_prijava (
    id         INTEGER PRIMARY KEY AUTOINCREMENT,
    id_racun   INTEGER NOT NULL REFERENCES uporabnik (id) ON DELETE CASCADE,
    ob         TEXT NOT NULL
);

CREATE INDEX idx_racun_prijava_racun ON racun_prijava (id_racun, ob);

-- Dnevnik za obstojece racune: samo to, kar je iz stolpcev znano z datumom.
-- Zavrnitev, izklop in nastavitev gesla datuma nista hranila, zato jih ni.

INSERT INTO racun_dogodek (id_racun, ob, tekst)
SELECT id, ustvarjen_ob,
       'Registracija (' || CASE vloga WHEN 'IGRALEC' THEN 'igralec'
                                      WHEN 'ORGANIZATOR' THEN 'organizator'
                                      ELSE 'administrator' END || ')'
FROM uporabnik;

-- Racuni pred V33 so dobili datum potrditve = datum nastanka; koda tam ni bila vpisana.
INSERT INTO racun_dogodek (id_racun, ob, tekst)
SELECT id, email_potrjen_ob, 'E-pošta potrjena s kodo'
FROM uporabnik
WHERE vloga <> 'ADMIN' AND email_potrjen_ob IS NOT NULL AND email_potrjen_ob > ustvarjen_ob;

INSERT INTO racun_dogodek (id_racun, ob, tekst)
SELECT u.id, u.povezan_ob,
       CASE u.vir_povezave WHEN 'SAMODEJNO' THEN 'Samodejno povezan z igralcem '
                           ELSE 'Administrator povezal z igralcem ' END
           || i.ime || ' ' || i.priimek
FROM uporabnik u
JOIN igralec i ON i.id = u.id_igralec
WHERE u.vloga = 'IGRALEC' AND u.povezan_ob IS NOT NULL AND u.vir_povezave IS NOT NULL;

-- Placilo, ki ga je zabelezil Stripe (brezplacno leto Pro iz V34 placila nima).
INSERT INTO racun_dogodek (id_racun, ob, tekst)
SELECT id_uporabnik, zacetek_ob,
       'Plačilo: ' || CASE paket WHEN 'PREMIUM' THEN 'Premium'
                                 WHEN 'ORGANIZATOR_BASIC' THEN 'Basic'
                                 WHEN 'ORGANIZATOR_PLUS' THEN 'Plus'
                                 WHEN 'ORGANIZATOR_PRO' THEN 'Pro'
                                 ELSE paket END
           || CASE ciklus WHEN 'LETNO' THEN ', letno' WHEN 'MESECNO' THEN ', mesečno' ELSE '' END
FROM narocnina
WHERE stripe_narocnina_id IS NOT NULL AND zacetek_ob IS NOT NULL AND paket <> 'BREZPLACNO';
