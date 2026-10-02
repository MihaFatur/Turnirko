-- ============================================================================
-- Turnirko rating: zmaga je zmaga
--
-- Izid v nizih ne vpliva vec na spremembo ratinga (odlocitev lastnika,
-- 30. 9. 2026): 3 : 0 in 3 : 2 prineseta isto, 0 : 3 in 2 : 3 vzameta isto.
-- Sprememba je od zdaj K x teza x (izid - pricakovano), zato sestavina
-- "margina" (V24) nima vec pomena in stolpec odide.
--
-- Stare vrednosti niso izgubljena informacija: sestavine so izpeljanka iz
-- zaporedja tekem in jih ponovni preracun (PreracunRatingaStoritev) napise
-- znova po novem obrazcu. Po tej migraciji je zato treba rating preracunati
-- od zacetka - dokler se to ne zgodi, se stare vrstice ne zmnozijo v zapisano
-- spremembo.
--
-- Stolpec ni v nobenem indeksu, njegov CHECK pa je vezan nanj samega, zato ga
-- SQLite (3.35+) zna odstraniti brez prezidave tabele.
-- ============================================================================

ALTER TABLE rating_zgodovina DROP COLUMN margina;
