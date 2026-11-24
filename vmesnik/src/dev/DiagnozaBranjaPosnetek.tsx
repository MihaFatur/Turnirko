/* Razvojni prikaz diagnostike branja (/dev/diagnoza-branja): komponenta DiagnozaBranja z
   izmišljenimi števili, da se videz preveri brez strežnika, prijave admina in zlatega
   nabora. Samo za razvoj (import.meta.env.DEV v App.tsx). */
import { useMemo } from 'react'

import type { DelDiagnozeDto, SkupinaMeritve, StanjeNaboraDto, VzrokNapakeBranja } from '../api/tipi'
import { DiagnozaBranja } from '../komponente/DiagnozaBranja'

/* Celica z napisano številko, kot jo vrne strežnik (PNG v sivinah, base64). */
function slikaCelice(besedilo: string): string {
  const c = document.createElement('canvas')
  c.width = 220
  c.height = 80
  const g = c.getContext('2d')!
  g.fillStyle = '#fff'
  g.fillRect(0, 0, c.width, c.height)
  g.fillStyle = '#222'
  g.font = 'italic 52px cursive'
  g.fillText(besedilo, 60, 60)
  return c.toDataURL('image/png').slice('data:image/png;base64,'.length)
}

function skupina(oznaka: string, izmerjenih: number, brezPopravka: number): SkupinaMeritve {
  return {
    oznaka, idTurnir: null, datum: null, branj: izmerjenih, prebranih: izmerjenih, neuspelih: 0,
    potrjenih: izmerjenih, brezPopravka, pripravljenih: Math.round(izmerjenih * 0.6), izmerjenih, tihihNapak: 3,
    kontrol: 0, kontrolZNapako: 0,
    celice: { pravZanesljive: 0, pravNegotove: 0, narobeNegotove: 0, narobeZanesljive: 0 },
    casi: { izmerjenih: 0, mediana: null, p90: null, enDotik: 0, medianaEnDotik: null, obrazec: 0, medianaObrazec: null },
  } as SkupinaMeritve
}

function del(listkov: number, celic: number, vzroki: [VzrokNapakeBranja, number, number][], napake: boolean): DelDiagnozeDto {
  const napacnih = vzroki.reduce((a, [, t, u]) => a + t + u, 0)
  return {
    listkov, celic, napacnih,
    vzroki: vzroki.map(([vzrok, tihih, ujetih]) => ({ vzrok, tihih, ujetih })),
    stevke: {
      celicZZapisom: Math.round(celic * 0.8), brezRazreza: Math.round(celic * 0.05), stevk: Math.round(celic * 1.3),
      prav: Math.round(celic * 1.3 * (napake ? 0.91 : 0.985)),
      poStevkah: Array.from({ length: 10 }, (_, s) => ({
        stevka: s, vseh: 40 + s * 3, prav: 36 + s * 3 - (s === 7 ? 6 : 0),
        zamenjave: s === 7 ? [{ prebrano: 1, krat: 5 }, { prebrano: 2, krat: 1 }] : s === 4 ? [{ prebrano: 9, krat: 2 }] : [],
      })),
    },
    umerjenostMreze: [
      [0, 0.5, 8, 2], [0.5, 0.8, 21, 11], [0.8, 0.9, 30, 22], [0.9, 0.95, 44, 37], [0.95, 0.99, 120, 109], [0.99, 1, 610, 598],
    ].map(([od, doMeje, c, p]) => ({ od, doMeje, celic: c, prav: p })),
    umerjenostKoncna: [
      [0, 0.5, 5, 2], [0.5, 0.8, 18, 10], [0.8, 0.9, 25, 19], [0.9, 0.95, 39, 33], [0.95, 0.99, 140, 128], [0.99, 1, 606, 597],
    ].map(([od, doMeje, c, p]) => ({ od, doMeje, celic: c, prav: p })),
    napake: napake
      ? [
          { niz: 2, stran: 'desno', napisano: 7, prebrano: 1, vzrok: 'MREZA', tiha: true, zaupanje: 0.97, slika: slikaCelice('7') },
          { niz: 1, stran: 'levo', napisano: 11, prebrano: 4, vzrok: 'RAZREZ', tiha: false, zaupanje: 0.62, slika: slikaCelice('11') },
          { niz: 4, stran: 'levo', napisano: 9, prebrano: 8, vzrok: 'PRAVILA', tiha: false, zaupanje: 0.71, slika: slikaCelice('9') },
          { niz: 3, stran: 'desno', napisano: 12, prebrano: 2, vzrok: 'IZBIRA_RAZREZA', tiha: true, zaupanje: 0.91, slika: null },
        ]
      : [],
  }
}

export default function DiagnozaBranjaPosnetek() {
  const stanje = useMemo<StanjeNaboraDto>(
    () => ({
      listkov: 412, najvec: 5000, tece: false, obdelanih: 412, vseh: 412, zacetek: null, konec: null, mreza: 'različica 3',
      rezultat: skupina('Zlati nabor', 412, 351), napaka: null,
      preizkusni: skupina('Preizkusni listki', 82, 55),
      ucni: skupina('Učni listki', 330, 296),
      diagnoza: {
        preizkusni: del(82, 833, [['NI_CRNILA', 1, 3], ['CRNILO_V_PRAZNI', 0, 2], ['RAZREZ', 2, 9], ['MREZA', 9, 21],
          ['IZBIRA_RAZREZA', 3, 4], ['PRAVILA', 4, 6]], true),
        ucni: del(330, 3340, [['NI_CRNILA', 2, 6], ['CRNILO_V_PRAZNI', 1, 4], ['RAZREZ', 4, 18], ['MREZA', 5, 14],
          ['IZBIRA_RAZREZA', 2, 5], ['PRAVILA', 3, 9]], false),
      },
    }),
    [],
  )
  return (
    <main className="stran" style={{ padding: 24, maxWidth: 960 }}>
      <DiagnozaBranja stanje={stanje} />
    </main>
  )
}
