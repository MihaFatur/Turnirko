// Primerjava javnega API-ja dveh zagnanih aplikacij Turnirko (faza 3 prenove
// baze, docs/PRENOVA-BAZE.md, razdelek 6.2/3).
//
// Namen: ista aplikacija nad istimi podatki mora na SQLite in na PostgreSQL
// vrniti ISTE odgovore, do bajta. To preveri vse skupaj - tipe, pretvorbe,
// poizvedbe in vrstni red - in je zadnja obramba pred tihimi razlikami po
// preklopu.
//
//   node skripte/primerjaj-api.mjs <osnova-A> <osnova-B> [--vzorec=20] [--porocilo=datoteka.md]
//
//   osnova-A  referenca (aplikacija na SQLite), npr. http://localhost:8081
//   osnova-B  preizkusna (aplikacija na PostgreSQL), npr. http://localhost:8082
//   --vzorec  koliko zapisov vsake vrste (turnir, liga, srecanje, igralec ...)
//             se preveri; 0 = vsi. Zapisi so enakomerno razporejeni po seznamu.
//
// Obe aplikaciji morata imeti izklopljena opravila v ozadju (obvestila po
// kolu, varovalka ratinga), sicer se bazi razideta med merjenjem. Izhodna
// koda je 0, ce se vsi odgovori ujemajo, sicer 1.
import { writeFileSync } from 'node:fs';

const args = process.argv.slice(2);
const [osnovaA, osnovaB] = args.filter((a) => !a.startsWith('--'));
const moznost = (ime, privzeto) => {
  const a = args.find((x) => x.startsWith(`--${ime}=`));
  return a ? a.slice(ime.length + 3) : privzeto;
};
if (!osnovaA || !osnovaB) {
  console.error('Uporaba: node primerjaj-api.mjs <osnova-A> <osnova-B> [--vzorec=20] [--porocilo=datoteka.md]');
  process.exit(2);
}
const VZOREC = Number(moznost('vzorec', '20'));
const POROCILO = moznost('porocilo', '');

async function vzemi(osnova, pot) {
  const t0 = performance.now();
  try {
    const r = await fetch(osnova + pot, { headers: { Accept: 'application/json' } });
    const telo = await r.text();
    return { status: r.status, telo, ms: performance.now() - t0 };
  } catch (napaka) {
    return { status: 0, telo: String(napaka), ms: performance.now() - t0 };
  }
}

async function json(osnova, pot) {
  const o = await vzemi(osnova, pot);
  if (o.status !== 200) throw new Error(`${pot}: ${o.status} ${o.telo.slice(0, 200)}`);
  return JSON.parse(o.telo);
}

/* Enakomerno razporejen vzorec, vedno z zadnjim zapisom. */
function vzorec(seznam, n) {
  if (n <= 0 || seznam.length <= n) return seznam;
  const izbrani = new Set();
  for (let i = 0; i < n; i++) izbrani.add(Math.floor((i * (seznam.length - 1)) / (n - 1)));
  return [...izbrani].map((i) => seznam[i]);
}

/* Polja, ki so po pomenu MNOZICE: vrstni red elementov ni podatek (filter
   "Moje lige" le preveri, ali je liga v seznamu), v SQLite pa je bil
   nakljucen stranski ucinek poizvedbe. Primerjajo se urejena. */
const MNOZICE = new Set(['idjiLig']);

function urediMnozice(v, kljuc = '') {
  if (Array.isArray(v)) {
    const elementi = v.map((e) => urediMnozice(e));
    return MNOZICE.has(kljuc) ? elementi.sort((x, y) => JSON.stringify(x).localeCompare(JSON.stringify(y))) : elementi;
  }
  if (v && typeof v === 'object') {
    return Object.fromEntries(Object.entries(v).map(([k, e]) => [k, urediMnozice(e, k)]));
  }
  return v;
}

/* Prvo mesto, kjer se dva JSON-a razlikujeta. */
function prvaRazlika(a, b, pot = '$') {
  if (a === b) return null;
  if (typeof a !== typeof b || a === null || b === null || typeof a !== 'object') {
    return `${pot}: ${JSON.stringify(a)?.slice(0, 80)} proti ${JSON.stringify(b)?.slice(0, 80)}`;
  }
  if (Array.isArray(a) !== Array.isArray(b)) return `${pot}: seznam proti objektu`;
  if (Array.isArray(a)) {
    if (a.length !== b.length) return `${pot}: dolzina ${a.length} proti ${b.length}`;
    for (let i = 0; i < a.length; i++) {
      const r = prvaRazlika(a[i], b[i], `${pot}[${i}]`);
      if (r) return r;
    }
    return null;
  }
  const kljuci = new Set([...Object.keys(a), ...Object.keys(b)]);
  for (const k of kljuci) {
    if (!(k in a)) return `${pot}.${k}: manjka v A`;
    if (!(k in b)) return `${pot}.${k}: manjka v B`;
    const r = prvaRazlika(a[k], b[k], `${pot}.${k}`);
    if (r) return r;
  }
  return null;
}

// ---------------------------------------------------------------- poti

const poti = [];
const dodaj = (...p) => poti.push(...p);

const turnirji = await json(osnovaA, '/api/v1/turnirji');
const lige = await json(osnovaA, '/api/v1/lige');
const igralci = await json(osnovaA, '/api/v1/igralci');
const lestvica = await json(osnovaA, '/api/v1/lestvica');

dodaj('/api/v1/turnirji', '/api/v1/lige', '/api/v1/igralci', '/api/v1/klubi', '/api/v1/kraji',
  '/api/v1/lestvica', '/api/v1/zadnje-tekme', '/api/v1/zadnje-tekme?koliko=20',
  '/api/v1/domov/lige', '/api/v1/domov/turnirji', '/api/v1/rating/pravila', '/api/v1/premium/dokaz');

// koledar: po mesecih, od sezone 2012/13 do danes in naprej
for (let leto = 2012; leto <= 2027; leto++) {
  for (let mesec = 1; mesec <= 12; mesec++) {
    const m = String(mesec).padStart(2, '0');
    const zadnji = new Date(Date.UTC(leto, mesec, 0)).getUTCDate();
    dodaj(`/api/v1/koledar?od=${leto}-${m}-01&do=${leto}-${m}-${zadnji}`);
  }
}

const turnirjiVzorec = vzorec(turnirji, VZOREC);
for (const t of turnirjiVzorec) {
  dodaj(`/api/v1/turnirji/${t.id}`, `/api/v1/turnirji/${t.id}/statistika`, `/api/v1/turnirji/${t.id}/dogodki`);
}
const dogodki = [];
for (const t of turnirjiVzorec) {
  const seznam = await json(osnovaA, `/api/v1/turnirji/${t.id}/dogodki`);
  dogodki.push(...seznam);
}
for (const d of vzorec(dogodki, VZOREC * 2)) {
  dodaj(`/api/v1/dogodki/${d.id}`);
  if (d.disciplina === 'EKIPNO') dodaj(`/api/v1/dogodki/${d.id}/ekipe`);
}

const srecanja = [];
for (const l of vzorec(lige, VZOREC)) {
  dodaj(`/api/v1/lige/${l.id}`, `/api/v1/lige/${l.id}/ekipe`, `/api/v1/lige/${l.id}/srecanja`,
    `/api/v1/lige/${l.id}/lestvica`, `/api/v1/lige/${l.id}/lestvica-igralcev`,
    `/api/v1/lige/${l.id}/lestvica-dvojic`, `/api/v1/lige/${l.id}/statistika`, `/api/v1/lige/${l.id}/koncnica`);
  const s = await json(osnovaA, `/api/v1/lige/${l.id}/srecanja`);
  srecanja.push(...s);
}
for (const s of vzorec(srecanja, VZOREC * 2)) dodaj(`/api/v1/srecanja/${s.id}`);

// igralci: nakljucni vzorec in najvisji na lestvici (imajo najvec tekem)
const idIgralcev = new Set(vzorec(igralci, VZOREC).map((i) => i.id));
for (const i of lestvica.slice(0, Math.max(VZOREC, 10))) idIgralcev.add(i.idIgralca);
for (const id of idIgralcev) dodaj(`/api/v1/igralci/${id}`, `/api/v1/igralci/${id}/profil`);

const vrh = lestvica.slice(0, 12).map((i) => i.idIgralca);
for (let i = 0; i + 1 < vrh.length; i += 2) dodaj(`/api/v1/dvoboj?prvi=${vrh[i]}&drugi=${vrh[i + 1]}`);

// razlaga ratinga: preizkusa s fiksnimi vrednostmi
dodaj('/api/v1/rating/izracun?rating=1500&nasprotnik=1300&tekem=40&tekemNasprotnika=40',
  '/api/v1/rating/izracun?rating=900&nasprotnik=1700&tekem=5&tekemNasprotnika=200&vrnitev=true',
  '/api/v1/rating/prvi-dan?izhodisce=1000&nasprotnik=1200&zmaga=true&nasprotnik=950&zmaga=false&nasprotnik=1100&zmaga=true');

// ---------------------------------------------------------------- primerjava

const unikatne = [...new Set(poti)];
console.log(`Primerjam ${unikatne.length} poti: A=${osnovaA}  B=${osnovaB}`);
const razlike = [];
const casiA = [];
const casiB = [];
let enakih = 0;
let samoVrstniRedMnozic = 0;
let velikost = 0;
let naslednja = 0;
async function delavec() {
  while (naslednja < unikatne.length) {
    const pot = unikatne[naslednja++];
    const a = await vzemi(osnovaA, pot);
    const b = await vzemi(osnovaB, pot);
    casiA.push(a.ms);
    casiB.push(b.ms);
    velikost += a.telo.length;
    if (a.status === b.status && a.telo === b.telo) {
      enakih++;
      continue;
    }
    let opis = `status ${a.status} proti ${b.status}`;
    if (a.status === b.status) {
      try {
        const ja = urediMnozice(JSON.parse(a.telo));
        const jb = urediMnozice(JSON.parse(b.telo));
        opis = prvaRazlika(ja, jb);
        if (opis === null) {
          enakih++;
          samoVrstniRedMnozic++;
          continue;
        }
      } catch {
        opis = 'besedilo se razlikuje';
      }
    }
    razlike.push({ pot, opis });
  }
}
await Promise.all([delavec(), delavec(), delavec()]);

const centil = (t, p) => [...t].sort((x, y) => x - y)[Math.min(t.length - 1, Math.floor(t.length * p))] ?? 0;
const povzetek = `${enakih} od ${unikatne.length} odgovorov enakih (${(velikost / 1e6).toFixed(1)} MB, od tega ${samoVrstniRedMnozic} le do vrstnega reda mnozic), `
  + `razlik: ${razlike.length}; p50/p95 A ${centil(casiA, 0.5).toFixed(0)}/${centil(casiA, 0.95).toFixed(0)} ms, `
  + `B ${centil(casiB, 0.5).toFixed(0)}/${centil(casiB, 0.95).toFixed(0)} ms`;
console.log(povzetek);
for (const r of razlike.slice(0, 40)) console.log(`  RAZLIKA ${r.pot}\n    ${r.opis}`);

if (POROCILO) {
  const md = [`# Primerjava API-ja SQLite ↔ PostgreSQL`, '', `**${razlike.length === 0 ? 'ZELENO' : 'RDEČE'}** · ${povzetek}`, ''];
  if (razlike.length) {
    md.push('## Razlike', '', ...razlike.map((r) => `- \`${r.pot}\`: ${r.opis}`), '');
  }
  writeFileSync(POROCILO, md.join('\n'));
}
process.exit(razlike.length === 0 ? 0 : 1);
