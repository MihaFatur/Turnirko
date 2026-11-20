-- Vadnica stevk (F3, docs/NACRT-BRANJE-LISTKOV.md): list A4, na katerem ena
-- oseba v vsako okence prepise stevko (0-9) ali dvomestno stevilo (10-13), kot
-- jih pise na sodniski listek. Iz fotografije lista nastanejo oznacene stevke
-- slovenske pisave za ucenje mreze, se preden pride prvi pravi listek.
--
-- Id lista je v QR kodi (TKV:<razlicica>:<id>) in ga izbere vmesnik ob tisku
-- (nakljucno), zato list, natisnjen na testnem okolju, velja tudi v produkciji.
-- List nima polj za ime; shrani se poravnan list, na katerem so samo okenca
-- (vse ostalo belo), da se stevke lahko izrezejo znova z boljsim razrezom.
CREATE TABLE vadnica (
    id             bigint PRIMARY KEY CHECK (id > 0),
    razlicica      smallint NOT NULL CHECK (razlicica >= 1),
    slika          bytea NOT NULL,
    stevk          integer NOT NULL CHECK (stevk >= 0),
    praznih        integer NOT NULL CHECK (praznih >= 0),
    preskocenih    integer NOT NULL CHECK (preskocenih >= 0),
    odobrena       boolean NOT NULL DEFAULT false,
    ustvarjeno_ob  timestamp(3) NOT NULL,
    odobreno_ob    timestamp(3)
);

-- Stevka je z listka (id_branje) ali z vadnice (id_vadnica). Stevke vadnice gredo
-- v ucenje sele, ko admin list pregleda in odobri (odobreno); dotlej bi lahko
-- napacno napisana stevka (zastrupitev) pokvarila mrezo. Obstojece stevke so z
-- listkov, ki jih je potrdil organizator, zato so odobrene.
ALTER TABLE stevka_listka
    ADD COLUMN id_vadnica bigint REFERENCES vadnica (id) ON DELETE CASCADE,
    ADD COLUMN celica smallint CHECK (celica >= 0),
    ADD COLUMN odobreno boolean NOT NULL DEFAULT true,
    ADD CONSTRAINT stevka_listka_en_vir CHECK (id_branje IS NULL OR id_vadnica IS NULL);

CREATE INDEX idx_stevka_listka_vadnica ON stevka_listka (id_vadnica) WHERE id_vadnica IS NOT NULL;
