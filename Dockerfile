# Turnirko kot ena slika: v prvem koraku se zgradi vmesnik in zaledje,
# v drugem ostane samo prevedena aplikacija brez orodij za prevajanje.
# Zato je koncna slika majhna in v njej ni ne Mavna ne izvorne kode.
#
# Gradi se iz KORENA projekta (mape turnirko), ker prevod potrebuje
# tako mapo zaledje kot vmesnik:
#     docker build -t turnirko .

# ---------- 1. prevod ----------
FROM eclipse-temurin:21-jdk AS prevod
WORKDIR /gradnja

# Najprej samo opisi odvisnosti: dokler se ne spremenijo, Docker ta korak
# preskoci in ni treba znova prenasati vsega interneta ob vsaki spremembi kode.
COPY zaledje/mvnw zaledje/mvnw
COPY zaledje/.mvn zaledje/.mvn
COPY zaledje/pom.xml zaledje/pom.xml
COPY vmesnik/package.json vmesnik/package-lock.json vmesnik/
RUN sh zaledje/mvnw -f zaledje/pom.xml -B -Psplet \
        frontend:install-node-and-npm frontend:npm@namesti-odvisnosti \
        dependency:go-offline || true

COPY zaledje zaledje
COPY vmesnik vmesnik
# Profil "splet" zgradi vmesnik in ga vlozi v .jar (glej zaledje/pom.xml).
RUN sh zaledje/mvnw -f zaledje/pom.xml -B -Psplet -DskipTests package

# ---------- 2. zagon ----------
FROM eclipse-temurin:21-jre
WORKDIR /app

# curl rabi samo preverba zdravja iz compose.yaml (osnovna slika ga nima).
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

# Aplikacija ne sme teci kot root: ce jo kdo prevzame, naj ne dobi
# celotnega vsebnika. Mapa /podatki je edino, kamor sme pisati.
RUN useradd --system --uid 10001 turnirko \
    && mkdir -p /podatki \
    && chown turnirko:turnirko /podatki

COPY --from=prevod /gradnja/zaledje/target/turnirko-zaledje-*.jar /app/turnirko.jar

USER turnirko
VOLUME ["/podatki"]
EXPOSE 8080

ENV SPRING_PROFILES_ACTIVE=splet
ENV SPRING_DATASOURCE_URL=jdbc:sqlite:/podatki/turnirko.db
# TURNIRKO_ADMIN_PRIVZETO_GESLO tu NAMENOMA ni: ce bi bilo, bi bilo enako
# pri vsaki namestitvi. Poda se ob zagonu (glej compose.yaml in .env).

ENTRYPOINT ["java", "-jar", "/app/turnirko.jar"]
