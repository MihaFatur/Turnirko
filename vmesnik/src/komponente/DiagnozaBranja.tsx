/* Diagnostika branja nad zlatim naborom (24. 11. 2026, samo admin; stran Meritev branja,
   razdelek Zlati nabor). Lastnik: »zakaj je zaznavanje na pravih listkih občutno slabše,
   kot kaže meritev?« Pokaže štiri stvari (docs/pravila/branje-listkov.md, »Diagnostika«):

   1. preizkusni listki (pisava, ki je mreža ni videla) proti učnim - koliko meritev
      nabora olepša to, da se je mreža na večini listkov že učila;
   2. zakaj so celice narobe, po korakih bralnika (razrez, mreža, izbira razreza, pravila);
   3. števke z listkov, izrezane kot za učenje, in celice, ki jih razrez ne razdeli
      (te v učenje in med preizkusne števke ne pridejo nikoli);
   4. umerjenost: zaupanje proti deležu prav (mreža sama in končno branje);
   in galerijo napačnih celic preizkusnih listkov. */
import type { DelDiagnozeDto, RazredUmerjenostiDto, SkupinaMeritve, StanjeNaboraDto, VzrokNapakeBranja } from '../api/tipi'
import { delezOd, zamenjaveBesedilo } from '../pomozno/branjeListkov'

const VZROKI: Record<VzrokNapakeBranja, { ime: string; opis: string }> = {
  NI_CRNILA: { ime: 'Ni zapisa', opis: 'Razrez v celici ni našel črnila (bled zapis, zapis na črti).' },
  CRNILO_V_PRAZNI: { ime: 'Črnilo v prazni', opis: 'Celica je prazna, razrez pa je v njej našel črto, senco ali ostanek.' },
  RAZREZ: { ime: 'Razrez', opis: 'Nobena razlaga razreza nima pravega števila števk (»11« v eni potezi, števki se dotikata).' },
  MREZA: { ime: 'Mreža', opis: 'Razrez je prav, mreža je števko prebrala narobe.' },
  IZBIRA_RAZREZA: { ime: 'Izbira razreza', opis: 'Mreža bi prebrala prav, a je obveljala druga razlaga razreza.' },
  PRAVILA: { ime: 'Pravila', opis: 'Mreža je celico prebrala prav, pravila ali pogostost nizov so izbrala drugo.' },
}

export function DiagnozaBranja({ stanje }: { stanje: StanjeNaboraDto }) {
  const d = stanje.diagnoza
  if (!d || !stanje.preizkusni || !stanje.ucni) return null
  const pre = d.preizkusni
  return (
    <div className="diagnoza-branja">
      <h3 className="meritev-branja__podnaslov">Diagnostika: kje bralnik izgubi</h3>
      <p className="meritev-branja__besedilo">
        Preizkusni listki so vsak peti potrjen listek: njihovih števk mreža pri učenju ni videla, zato so merilo za
        pisavo novih ljudi. Ostali so učni. Velika razlika med njima pomeni, da meritev nabora olepša učenje na istih
        listkih. Celice so samo točke po nizih (brez celice Rezultat).
      </p>
      <PrimerjavaDelov preizkusni={stanje.preizkusni} ucni={stanje.ucni} dPre={pre} dUc={d.ucni} />

      <h4 className="napake-mreze__naslov">Zakaj so celice narobe</h4>
      <Vzroki pre={pre} uc={d.ucni} />

      <h4 className="napake-mreze__naslov">Števke z listkov</h4>
      <Stevke del={pre} ucni={d.ucni} />

      <h4 className="napake-mreze__naslov">Umerjenost: zaupanje proti deležu prav</h4>
      <p className="meritev-branja__besedilo">
        Celice preizkusnih listkov po zaupanju. »Mreža« je najvišja verjetnost celice pred pravili, »končno« zaupanje
        izbrane vrednosti po pravilih in pogostosti nizov. Pri dobro umerjenem bralniku je delež prav vsaj spodnja meja
        razreda. Celica pod 0,8 je za pregled.
      </p>
      <Umerjenost mreza={pre.umerjenostMreze} koncno={pre.umerjenostKoncna} />

      <h4 className="napake-mreze__naslov">Napačne celice preizkusnih listkov ({pre.napacnih})</h4>
      {pre.napake.length === 0 ? (
        <p className="namig">Na preizkusnih listkih ni napačnih celic.</p>
      ) : (
        <>
          {pre.napacnih > pre.napake.length && (
            <p className="namig">Prikazanih je prvih {pre.napake.length}.</p>
          )}
          <ul className="diagnoza-branja__galerija">
            {pre.napake.map((n, i) => (
              <li key={i} className="napake-mreze__stevka">
                {n.slika ? (
                  <img
                    className="diagnoza-branja__slika"
                    src={`data:image/png;base64,${n.slika}`}
                    alt={`${n.niz}. niz, ${n.stran}: napisano ${n.napisano ?? 'prazno'}`}
                  />
                ) : (
                  <span className="diagnoza-branja__slika diagnoza-branja__slika--brez">brez slike</span>
                )}
                <span className="napake-mreze__branje">
                  {n.napisano ?? '–'} → {n.prebrano ?? '–'}
                </span>
                <span className="napake-mreze__vir">
                  {VZROKI[n.vzrok].ime} · {n.tiha ? 'tiha' : 'ujeta'}
                  <br />
                  {n.niz}. niz {n.stran} · {delezOd(Math.round(n.zaupanje * 1000), 1000)}
                </span>
              </li>
            ))}
          </ul>
        </>
      )}
    </div>
  )
}

function PrimerjavaDelov({
  preizkusni,
  ucni,
  dPre,
  dUc,
}: {
  preizkusni: SkupinaMeritve
  ucni: SkupinaMeritve
  dPre: DelDiagnozeDto
  dUc: DelDiagnozeDto
}) {
  const vrstica = (oznaka: string, s: SkupinaMeritve, d: DelDiagnozeDto) => (
    <tr>
      <td>{oznaka}</td>
      <td className="meritev-branja__stevilo">{s.izmerjenih}</td>
      <td className="meritev-branja__stevilo">{delezOd(s.brezPopravka, s.izmerjenih)}</td>
      <td className="meritev-branja__stevilo">{delezOd(d.celic - d.napacnih, d.celic)}</td>
      <td className="meritev-branja__stevilo">{d.vzroki.reduce((a, v) => a + v.tihih, 0)}</td>
    </tr>
  )
  return (
    <div className="tabela-ovoj">
      <table className="tabela meritev-branja__tabela">
        <thead>
          <tr>
            <th>Listki</th>
            <th className="meritev-branja__desno">Listkov</th>
            <th className="meritev-branja__desno">Brez napake</th>
            <th className="meritev-branja__desno">Celice prav</th>
            <th className="meritev-branja__desno">Tihe celice</th>
          </tr>
        </thead>
        <tbody>
          {vrstica('Preizkusni (nova pisava)', preizkusni, dPre)}
          {vrstica('Učni', ucni, dUc)}
        </tbody>
      </table>
    </div>
  )
}

function Vzroki({ pre, uc }: { pre: DelDiagnozeDto; uc: DelDiagnozeDto }) {
  return (
    <>
      <div className="tabela-ovoj">
        <table className="tabela meritev-branja__tabela">
          <thead>
            <tr>
              <th>Vzrok</th>
              <th className="meritev-branja__desno">Preizkusni</th>
              <th className="meritev-branja__desno">od tega tihih</th>
              <th className="meritev-branja__desno">Učni</th>
            </tr>
          </thead>
          <tbody>
            {pre.vzroki.map((v) => {
              const u = uc.vzroki.find((x) => x.vzrok === v.vzrok)
              return (
                <tr key={v.vzrok}>
                  <td>{VZROKI[v.vzrok].ime}</td>
                  <td className="meritev-branja__stevilo">{v.tihih + v.ujetih}</td>
                  <td className="meritev-branja__stevilo meritev-branja__stevilo--tiha">{v.tihih}</td>
                  <td className="meritev-branja__stevilo">{u ? u.tihih + u.ujetih : 0}</td>
                </tr>
              )
            })}
          </tbody>
        </table>
      </div>
      <dl className="diagnoza-branja__vzroki">
        {Object.values(VZROKI).map((v) => (
          <div key={v.ime}>
            <dt>{v.ime}</dt>
            <dd>{v.opis}</dd>
          </div>
        ))}
      </dl>
    </>
  )
}

function Stevke({ del, ucni }: { del: DelDiagnozeDto; ucni: DelDiagnozeDto }) {
  const s = del.stevke
  return (
    <>
      <p className="meritev-branja__besedilo">
        Števke preizkusnih listkov, izrezane natanko kot za učenje: mreža prebere prav {delezOd(s.prav, s.stevk)} od{' '}
        {s.stevk} (na učnih {delezOd(ucni.stevke.prav, ucni.stevke.stevk)}). Primerjaj z deležem pri »Napake mreže«, ki
        je večinoma z vadnice. Celic, ki jih razrez ne razdeli na pravo število števk: {s.brezRazreza} od{' '}
        {s.celicZZapisom} ({delezOd(s.brezRazreza, s.celicZZapisom)}). Teh v učenju in med preizkusnimi števkami ni nikoli.
      </p>
      <div className="tabela-ovoj">
        <table className="tabela napake-mreze__tabela">
          <thead>
            <tr>
              <th>Števka</th>
              <th className="meritev-branja__desno">Števk</th>
              <th className="meritev-branja__desno">Prav</th>
              <th>Prebrana kot</th>
            </tr>
          </thead>
          <tbody>
            {s.poStevkah.map((p) => (
              <tr key={p.stevka}>
                <td className="napake-mreze__stevilka">{p.stevka}</td>
                <td className="meritev-branja__stevilo">{p.vseh}</td>
                <td className="meritev-branja__stevilo">{delezOd(p.prav, p.vseh)}</td>
                <td className="meritev-branja__stevilo napake-mreze__zamenjave">{zamenjaveBesedilo(p.zamenjave)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </>
  )
}

function Umerjenost({ mreza, koncno }: { mreza: RazredUmerjenostiDto[]; koncno: RazredUmerjenostiDto[] }) {
  const meja = (x: number) => x.toFixed(2).replace('.', ',')
  return (
    <div className="tabela-ovoj">
      <table className="tabela meritev-branja__tabela">
        <thead>
          <tr>
            <th>Zaupanje</th>
            <th className="meritev-branja__desno">Mreža: celic</th>
            <th className="meritev-branja__desno">prav</th>
            <th className="meritev-branja__desno">Končno: celic</th>
            <th className="meritev-branja__desno">prav</th>
          </tr>
        </thead>
        <tbody>
          {mreza.map((r, i) => (
            <tr key={r.od}>
              <td>
                {meja(r.od)}–{meja(r.doMeje)}
              </td>
              <td className="meritev-branja__stevilo">{r.celic}</td>
              <td className="meritev-branja__stevilo">{delezOd(r.prav, r.celic)}</td>
              <td className="meritev-branja__stevilo">{koncno[i]?.celic ?? 0}</td>
              <td className="meritev-branja__stevilo">{delezOd(koncno[i]?.prav ?? 0, koncno[i]?.celic ?? 0)}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}
