/* Stran »Mize« (/turnirji/:id/mize; M1, M2; docs/pravila/mize.md): organizator na
   prenosniku na pultu kliče tekme na mize. Igralce pokliče na glas, aplikacija klic samo
   zabeleži (odločitev lastnika, 23. 11. 2026).

   Prenova »tloris dvorane« (24. 11. 2026, design_handoff_mize_tloris): mize so narisane kot
   tloris dvorane, tekmo povlečeš z vrste na prosto mizo (ali s klikom v oknu klica), klik na
   zasedeno mizo odpre izid, »Uredi dvorano« izklaplja, dodaja in odstranjuje mize, »Zaslon
   za igralce« je ločena stran za TV v dvorani (MizeZaslonStran).

   Kaj je prosto in v kakšnem vrstnem redu, odloči strežnik (PredlogTekem); stran se osveži
   vsake 4 s (med vlečenjem ne) in ob sprostitvi mize kratko pisne. Izid se vpiše v istem
   oknu kot drugod (VnosRezultataOkno), vpis pa mizo sprosti sam. Vsak klic, premik in
   »Pokliči vse« se konča s sporočilom z razveljavitvijo (6 s). Telefon ima pomanjšan tloris
   (tapneš mizo, nato tekmo). */
import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'

import type { KandidatMizeDto, MizaDto, MizeTurnirjaDto, NastavitveMizVnos, TekmaMizeDto } from '../api/tipi'
import { mizeApi, turnirjiApi } from '../api/zahteve'
import { GlavaDejanja, useNazaj } from '../komponente/GlavaTelefona'
import { MeniDejanj } from '../komponente/MeniDejanj'
import {
  CrnaVrstica,
  GlavaMize,
  SporociloZRazveljavitvijo,
  Tloris,
  VrstaTekem,
  oznakaListkov,
  type DejanjaTloris,
  type KategorijaListkov,
  type SporociloMize,
} from '../komponente/MizeTloris'
import { MaliTloris, NaslovTelefon, PodrobnostiMize, VrstaTelefon } from '../komponente/MizeTelefon'
import { OknoIzida, OknoKlica, PrviZagon, type Klic, type PotrditevKlica } from '../komponente/MizeOkna'
import { useTiskListkov } from '../komponente/MizeListekTisk'
import { NapakaPoizvedbe } from '../komponente/NapakaPoizvedbe'
import { PotrditvenoOkno } from '../komponente/PotrditvenoOkno'
import { SporociloNapake } from '../komponente/SporociloNapake'
import { VnosRezultataOkno } from '../komponente/VnosRezultataOkno'
import {
  drugaMizaEkipnega,
  imenaTekme,
  kratkoImenaTekme,
  najdljeIgra,
  oznakaMize,
  privzetiSodnik,
  prosteMize,
  prviPredlogi,
  razlogiZasedenosti,
  sprosceneMize,
  uraIzCasa,
  type Vlecem,
} from '../pomozno/mize'
import { useNaslovStrani } from '../pomozno/naslovStrani'
import { useTelefon } from '../pomozno/telefon'

/* Osvežitev, dokler je stran vidna (skrit zavihek ne sprašuje). */
const OSVEZITEV_MS = 4000
/* Kako dolgo je sproščena miza poudarjena. */
const POUDAREK_MS = 30_000
/* Kako dolgo stoji sporočilo z razveljavitvijo. */
const SPOROCILO_MS = 6000
const KLJUC_PISKA = 'turnirko-mize-pisk'

/* Poklic kandidata na mizo z vlečenjem, klikom na telefonu ali »Pokliči vse«. */
interface KlicKandidata {
  kandidat: KandidatMizeDto
  miza: number
}

export function MizeStran() {
  const { id } = useParams()
  const idTurnirja = Number(id)
  const odjemalec = useQueryClient()
  const jeTelefon = useTelefon()
  useNaslovStrani('Mize')
  useNazaj(`/turnirji/${idTurnirja}`, 'Turnir')

  const [vlecem, nastaviVlecem] = useState<Vlecem>(null)
  const [nadMizo, nastaviNadMizo] = useState<number | null>(null)
  const [urejanje, nastaviUrejanje] = useState(false)
  const [sporocilo, nastaviSporocilo] = useState<SporociloMize | null>(null)
  const [klic, nastaviKlic] = useState<Klic | null>(null)
  const [vnosIzida, nastaviVnosIzida] = useState<TekmaMizeDto | null>(null)
  const [polniVnos, nastaviPolniVnos] = useState<{ tekma: TekmaMizeDto; prvi: number; drugi: number } | null>(null)
  const [preklic, nastaviPreklic] = useState<TekmaMizeDto | null>(null)
  const [vseKartice, nastaviVseKartice] = useState(false)
  const [izbranaMiza, nastaviIzbranoMizo] = useState<number | null>(null)
  const [zagonStevilo, nastaviZagonStevilo] = useState(20)
  const [zagonPorazenec, nastaviZagonPorazenca] = useState(true)
  const koracnikZagona = useRef<HTMLButtonElement>(null)

  const kljuc = ['turnir', idTurnirja, 'mize']

  /* Ročni vrstni red vrste (cela vrsta, kot jo vidi organizator; prazen = samodejni). */
  const vrstniRed = useMutation({
    mutationFn: ({ red }: { red: number[]; sporocilo: SporociloMize }) => mizeApi.vrstniRed(idTurnirja, red),
    onSuccess: (_, { sporocilo }) => sporoci(sporocilo),
    onSettled: () => osvezi(),
  })

  const pregled = useQuery({
    queryKey: kljuc,
    queryFn: () => mizeApi.pregled(idTurnirja),
    /* Med vlečenjem in med shranjevanjem vrstnega reda strežnik ne sprašuje: osvežitev bi
       zamenjala elemente pod kazalcem oziroma za hip vrnila stari vrstni red. */
    refetchInterval: vlecem || vrstniRed.isPending ? false : OSVEZITEV_MS,
  })

  /* Kategorije v teku za gumb »Listki za tiskanje« (stran Listki je po kategoriji). */
  const dogodki = useQuery({
    queryKey: ['turnir', idTurnirja, 'dogodki'],
    queryFn: () => turnirjiApi.dogodki(idTurnirja),
    staleTime: 60_000,
  })
  const kategorijeListkov: KategorijaListkov[] = (dogodki.data ?? [])
    .filter((d) => d.dejanskiStatus === 'V_TEKU')
    .map((d) => ({ id: d.id, ime: d.ime, ekipno: d.disciplina === 'EKIPNO' }))

  const tisk = useTiskListkov(pregled.data?.imeTurnirja ?? '')

  const osvezi = useCallback(() => {
    void odjemalec.invalidateQueries({ queryKey: ['turnir', idTurnirja] })
    void odjemalec.invalidateQueries({ queryKey: ['dogodek'] })
  }, [odjemalec, idTurnirja])

  /* Sporočilo z razveljavitvijo izgine po 6 s; novo nadomesti staro. */
  const casovnikSporocila = useRef<number | undefined>(undefined)
  const sporoci = useCallback((s: SporociloMize) => {
    window.clearTimeout(casovnikSporocila.current)
    nastaviSporocilo(s)
    casovnikSporocila.current = window.setTimeout(() => nastaviSporocilo(null), SPOROCILO_MS)
  }, [])
  useEffect(() => () => window.clearTimeout(casovnikSporocila.current), [])

  /* Sproščene mize: pisk in poudarek. Primerjava s prejšnjo osvežitvijo, zato
     stran ne piska, ko se odpre. Pisk sledi tudi organizatorjevemu vnosu izida:
     zvok pove, da je miza prosta, ne kdo jo je sprostil. */
  const [pisk, nastaviPisk] = useState(() => beriPisk())
  const piskni = usePisk()
  const prejsnje = useRef<MizaDto[] | undefined>(undefined)
  const [poudarjene, nastaviPoudarjene] = useState<Map<number, number>>(new Map())
  useEffect(() => {
    const zdaj = pregled.data?.mize
    if (!zdaj) return
    const sproscene = sprosceneMize(prejsnje.current, zdaj)
    prejsnje.current = zdaj
    if (sproscene.length === 0) return
    if (pisk) piskni()
    const do_ = Date.now() + POUDAREK_MS
    nastaviPoudarjene((p) => {
      const nov = new Map(p)
      sproscene.forEach((st) => nov.set(st, do_))
      return nov
    })
  }, [pregled.data, pisk, piskni])
  useEffect(() => {
    if (poudarjene.size === 0) return
    const casovnik = window.setTimeout(() => {
      const zdaj = Date.now()
      nastaviPoudarjene((p) => new Map([...p].filter(([, doKdaj]) => doKdaj > zdaj)))
    }, 2000)
    return () => window.clearTimeout(casovnik)
  }, [poudarjene])
  const poudarjeneMize = useMemo(() => new Set(poudarjene.keys()), [poudarjene])

  /* ---------- nastavitve dvorane ---------- */

  /* Vsaka sprememba bere stanje strežnika znova: dva hitra klika (izklop 3, izklop 7) ne
     smeta prepisati drug drugega z zastarelim seznamom izklopljenih miz. */
  const nastavi = useMutation({
    mutationFn: async (sprememba: (sveze: NastavitveMizVnos) => Partial<NastavitveMizVnos>) => {
      const sveze = await mizeApi.pregled(idTurnirja)
      const trenutne: NastavitveMizVnos = {
        steviloMiz: sveze.steviloMiz,
        izklopljeneMize: sveze.izklopljeneMize,
        porazenecSodi: sveze.porazenecSodi,
        pocitekMin: sveze.pocitekMin,
      }
      await mizeApi.nastavi(idTurnirja, { ...trenutne, ...sprememba(trenutne) })
    },
    onSuccess: osvezi,
  })

  /* ---------- klic, premik, preklic ---------- */

  const preklicKlica = useMutation({
    mutationFn: (t: TekmaMizeDto) => mizeApi.preklici(t.tekma.id),
    onSuccess: (_, t) => {
      osvezi()
      sporoci({ besedilo: `Klic preklican · ${kratkoImenaTekme(t)} je spet na vrsti` })
    },
  })

  /* Jedro vsakega klica: strežnik, sporočilo z razveljavitvijo in listek. Okno klica ga
     kliče samo (napako pokaže v oknu), vlečenje in telefon prek mutacije spodaj. */
  const izvediKlic = async (v: PotrditevKlica, kandidat?: KandidatMizeDto) => {
    const t = v.tekma
    const idSodnika = v.sodnik?.id ?? null
    await mizeApi.poklici(t.tekma.id, { miza: v.miza, miza2: v.miza2, idSodnika })
    osvezi()
    if (v.sprememba) {
      sporoci({ besedilo: `${kratkoImenaTekme(t)}: klic spremenjen` })
      return
    }
    const opozorilo = kandidat?.zaseden ? ` · pozor, ${razlogiZasedenosti(kandidat)[0] ?? 'igralec je zaseden'}` : ''
    sporoci({
      besedilo:
        `Miza ${oznakaMize({ miza: v.miza, miza2: v.miza2 })}: ${kratkoImenaTekme(t)} poklicana` +
        (v.tisk ? ' · listek v tisku' : '') +
        opozorilo,
      razveljavi: () => {
        void mizeApi.preklici(t.tekma.id).then(osvezi)
      },
    })
    if (v.tisk) {
      void tisk.natisni([{ idDogodka: t.idDogodka, idTekme: t.tekma.id, miza: v.miza }]).catch(() => {
        sporoci({ besedilo: 'Listka ni bilo mogoče pripraviti. Natisneš ga lahko na strani Listki.' })
      })
    }
  }

  const klicKandidata = useMutation({
    mutationFn: ({ kandidat, miza }: KlicKandidata) => {
      const podatki = pregled.data!
      const t = kandidat.tekma
      return izvediKlic(
        {
          tekma: t,
          miza,
          miza2: t.ekipno ? drugaMizaEkipnega(prosteMize(podatki.mize), miza) : null,
          sodnik: privzetiSodnik(podatki.mize.find((m) => m.stevilka === miza), t),
          /* Listki so natisnjeni vnaprej (lastnik 24. 11. 2026: tisk ob klicu ni privzet);
             listek z vpisano mizo natisne samo kljukica v oknu klica. */
          tisk: false,
          sprememba: false,
        },
        kandidat,
      )
    },
  })

  /* Tekma z mize na drugo prosto mizo: čas igre teče naprej, sodnik ostane. */
  const premik = useMutation({
    mutationFn: async ({ tekma: t, nova }: { tekma: TekmaMizeDto; nova: number }) => {
      const stara = t.miza!
      const idSodnika = t.sodnik?.id ?? null
      await mizeApi.poklici(t.tekma.id, { miza: nova, miza2: t.miza2, idSodnika })
      osvezi()
      sporoci({
        besedilo: `${kratkoImenaTekme(t)}: z mize ${stara} na mizo ${nova}`,
        razveljavi: () => {
          void mizeApi.poklici(t.tekma.id, { miza: stara, miza2: t.miza2, idSodnika }).then(osvezi)
        },
      })
    },
  })

  /* »Pokliči vse predloge«: zaporedni klici prvih predlogov prostih miz (vsak svoja
     preverba na strežniku); ob prvi napaki se ustavi in jo pokaže. Pred klici se stanje
     prebere znova: pokliče se samo par, ki je še vedno prvi predlog svoje mize (medtem je
     lahko drug organizator koga poklical drugam). Nič se ne tiska (lastnik 24. 11. 2026). */
  const klicVseh = useMutation({
    mutationFn: async (potrjeni: [MizaDto, KandidatMizeDto][]) => {
      const sveze = await mizeApi.pregled(idTurnirja)
      const pari = prviPredlogi(sveze.mize, sveze.kandidati).filter(([m, k]) =>
        potrjeni.some(([pm, pk]) => pm.stevilka === m.stevilka && pk.tekma.tekma.id === k.tekma.tekma.id))
      /* Druga miza ekipnega srečanja ne sme vzeti mize, ki jo čaka drug predlog. */
      const prosta = new Set(prosteMize(sveze.mize))
      const cakajoce = new Set(pari.map(([m]) => m.stevilka))
      const poklicani: { tekma: TekmaMizeDto; miza: number }[] = []
      try {
        for (const [miza, k] of pari) {
          const miza2 = k.tekma.ekipno
            ? drugaMizaEkipnega([...prosta].filter((st) => st === miza.stevilka || !cakajoce.has(st)), miza.stevilka)
            : null
          await mizeApi.poklici(k.tekma.tekma.id, {
            miza: miza.stevilka,
            miza2,
            idSodnika: privzetiSodnik(miza, k.tekma)?.id ?? null,
          })
          prosta.delete(miza.stevilka)
          if (miza2 !== null) prosta.delete(miza2)
          poklicani.push({ tekma: k.tekma, miza: miza.stevilka })
        }
      } finally {
        osvezi()
      }
      return poklicani
    },
    onSuccess: (poklicani) => {
      sporoci({
        besedilo: `Poklicanih ${poklicani.length} tekem`,
        razveljavi: () => {
          void Promise.all(poklicani.map((p) => mizeApi.preklici(p.tekma.tekma.id))).then(osvezi)
        },
      })
    },
  })

  if (pregled.isPending) return <p className="obvestilo">Nalaganje …</p>
  if (!pregled.data) return <NapakaPoizvedbe poizvedba={pregled} kaj="miz" />
  const podatki = pregled.data
  const nastavljene = podatki.steviloMiz !== null
  const kandidatPoId = new Map(podatki.kandidati.map((k) => [k.tekma.tekma.id, k]))
  const mizaPo = (st: number | null) => podatki.mize.find((m) => m.stevilka === st)
  const proste = prosteMize(podatki.mize)
  const vIgri = podatki.mize.filter((m) => m.tekma !== null).length
  const pari = prviPredlogi(podatki.mize, podatki.kandidati)
  const predlogi = new Map(pari.map(([m, k]) => [m.stevilka, k]))
  const predlagane = new Map(pari.map(([m, k]) => [k.tekma.tekma.id, m.stevilka]))
  const najdlje = najdljeIgra(podatki.mize, podatki.casStreznika)
  const najdljeBesedilo = najdlje
    ? `Najdlje igra miza ${najdlje.oznaka} (${najdlje.minute} min)`
    : ''
  const kazalniki = nastavljene ? { proste: proste.length, vIgri, naVrsti: podatki.kandidati.length } : undefined

  /* ---------- dejanja ---------- */

  const odpriKlic = (k: KandidatMizeDto, miza: number | null) => {
    const m = miza ?? k.tekma.miza ?? proste[0] ?? null
    if (m === null) {
      sporoci({ besedilo: 'Ni proste mize.' })
      return
    }
    nastaviKlic({ tekma: k.tekma, miza: m, sprememba: false })
  }

  const spusti = (miza: number) => {
    const v = vlecem
    nastaviVlecem(null)
    nastaviNadMizo(null)
    if (!v) return
    if (v.tip === 'k') {
      const k = kandidatPoId.get(v.id)
      if (k) klicKandidata.mutate({ kandidat: k, miza })
    } else if (v.miza !== miza) {
      const t = mizaPo(v.miza)?.tekma
      if (t) premik.mutate({ tekma: t, nova: miza })
    }
  }

  const dejanja: DejanjaTloris = {
    poklici: odpriKlic,
    izid: nastaviVnosIzida,
    preklopiMizo: (miza, izklopi) => {
      if (nastavi.isPending) return
      nastavi.mutate((s) => ({
        izklopljeneMize: izklopi ? [...s.izklopljeneMize, miza] : s.izklopljeneMize.filter((m) => m !== miza),
      }))
    },
    zacniVlecenje: nastaviVlecem,
    konecVlecenja: () => {
      nastaviVlecem(null)
      nastaviNadMizo(null)
    },
    nadMizo: nastaviNadMizo,
    spusti,
  }

  const dodajMizo = () => {
    if (nastavi.isPending) return
    nastavi.mutate((s) => ({ steviloMiz: Math.min(99, (s.steviloMiz ?? 0) + 1) }))
  }
  const odstraniZadnjo = () => {
    if (nastavi.isPending) return
    const zadnja = podatki.mize[podatki.mize.length - 1]
    if (!zadnja || podatki.mize.length <= 1) {
      sporoci({ besedilo: 'Vsaj ena miza mora ostati.' })
    } else if (zadnja.tekma) {
      sporoci({ besedilo: 'Na zadnji mizi se igra – ne moreš je odstraniti' })
    } else {
      const n = podatki.mize.length - 1
      nastavi.mutate((s) => ({ steviloMiz: n, izklopljeneMize: s.izklopljeneMize.filter((m) => m <= n) }))
    }
  }

  const urejanjeDvorane = urejanje
    ? {
        porazenecSodi: podatki.porazenecSodi,
        pocitekMin: podatki.pocitekMin,
        pisk,
        zasedeno: nastavi.isPending,
        naPorazenec: (v: boolean) => nastavi.mutate(() => ({ porazenecSodi: v })),
        naPocitek: (m: number) => nastavi.mutate(() => ({ pocitekMin: Math.max(0, Math.min(30, m)) })),
        naPisk: (v: boolean) => {
          nastaviPisk(v)
          zapisiPisk(v)
        },
        odstraniZadnjo,
        konec: () => nastaviUrejanje(false),
      }
    : undefined

  /* V prvem zagonu je »Uredi dvorano« enako postavitvi dvorane: fokus na števec. */
  const uredi = () => {
    if (!nastavljene) koracnikZagona.current?.focus()
    else nastaviUrejanje(true)
  }
  const pokliciVse = () => klicVseh.mutate(pari)

  /* Ročni vrstni red: vrsta se na zaslonu uredi takoj, strežnik shrani celo vrsto, kot jo
     organizator vidi. Razveljavitev vrne prejšnji red (ali samodejnega, če je bil). */
  const shraniVrsto = (red: number[], besedilo: string) => {
    const prej = podatki.rocniVrstniRed ? podatki.kandidati.map((k) => k.tekma.tekma.id) : []
    void odjemalec.cancelQueries({ queryKey: kljuc })
    if (red.length > 0) {
      odjemalec.setQueryData<MizeTurnirjaDto>(kljuc, (p) => {
        if (!p) return p
        const po = new Map(p.kandidati.map((k) => [k.tekma.tekma.id, k]))
        return { ...p, rocniVrstniRed: true, kandidati: red.flatMap((id) => (po.has(id) ? [po.get(id)!] : [])) }
      })
    }
    vrstniRed.mutate({
      red,
      sporocilo: {
        besedilo,
        razveljavi: () => {
          void mizeApi.vrstniRed(idTurnirja, prej).then(osvezi)
        },
      },
    })
  }

  const premakniVVrsti = (idTekme: number, mesto: number) => {
    const red = podatki.kandidati.map((k) => k.tekma.tekma.id)
    const i = red.indexOf(idTekme)
    if (i < 0 || vrstniRed.isPending) return
    const cilj = mesto > i ? mesto - 1 : mesto
    if (cilj === i) return
    red.splice(i, 1)
    red.splice(cilj, 0, idTekme)
    const t = podatki.kandidati[i].tekma
    shraniVrsto(red, `${kratkoImenaTekme(t)}: ${cilj + 1}. na vrsti`)
  }

  const samodejniRed = () => shraniVrsto([], 'Vrsta je spet v samodejnem vrstnem redu')

  const postavi = () =>
    nastavi.mutate(() => ({ steviloMiz: zagonStevilo, izklopljeneMize: [], porazenecSodi: zagonPorazenec }))

  const napake =
    klicKandidata.error ?? premik.error ?? klicVseh.error ?? nastavi.error ?? preklicKlica.error ?? vrstniRed.error

  /* ---------- okna ---------- */

  const okna = (
    <>
      {klic && (
        <OknoKlica
          idTurnirja={idTurnirja}
          podatki={podatki}
          klic={klic}
          kandidat={kandidatPoId.get(klic.tekma.tekma.id)}
          onZapri={() => nastaviKlic(null)}
          onPotrdi={(v) => izvediKlic(v, kandidatPoId.get(v.tekma.tekma.id))}
        />
      )}

      {vnosIzida && (
        <OknoIzida
          tekma={vnosIzida}
          casStreznika={podatki.casStreznika}
          telefon={jeTelefon}
          onZapri={() => nastaviVnosIzida(null)}
          onShrani={(izbira) => {
            const t = vnosIzida
            const zaZmago = Math.floor(t.tekma.steviloNizov / 2) + 1
            nastaviVnosIzida(null)
            nastaviPolniVnos({
              tekma: t,
              prvi: izbira.zmagovalec === 1 ? zaZmago : izbira.niziPorazenca,
              drugi: izbira.zmagovalec === 2 ? zaZmago : izbira.niziPorazenca,
            })
          }}
          onPreklic={() => {
            nastaviPreklic(vnosIzida)
            nastaviVnosIzida(null)
          }}
          onSodnik={() => {
            nastaviKlic({ tekma: vnosIzida, miza: vnosIzida.miza, sprememba: true })
            nastaviVnosIzida(null)
          }}
        />
      )}

      {polniVnos && (
        <VnosRezultataOkno
          tekma={polniVnos.tekma.tekma}
          zacetniNizi={{ prvi: polniVnos.prvi, drugi: polniVnos.drugi }}
          onZapri={() => nastaviPolniVnos(null)}
          onShranjeno={(rezultat) => {
            const t = polniVnos.tekma
            osvezi()
            sporoci({
              besedilo:
                `Miza ${oznakaMize(t)} je prosta · ${kratkoImenaTekme(t)}` +
                (rezultat ? ` ${rezultat.dobljeniNizi1}:${rezultat.dobljeniNizi2}` : ''),
            })
          }}
        />
      )}

      {preklic && (
        <PotrditvenoOkno
          naslov="Prekliči klic"
          sporocilo={`${imenaTekme(preklic)} gre z mize ${oznakaMize(preklic)} nazaj med pripravljene tekme.`}
          besedaPotrditve="Prekliči klic"
          onPotrdi={() => preklicKlica.mutate(preklic)}
          onZapri={() => nastaviPreklic(null)}
        />
      )}

      {sporocilo && <SporociloZRazveljavitvijo sporocilo={sporocilo} naZapri={() => nastaviSporocilo(null)} />}
      {tisk.izris}
    </>
  )

  /* ---------- telefon ---------- */

  if (jeTelefon) {
    const izb = izbranaMiza !== null && mizaPo(izbranaMiza) && !mizaPo(izbranaMiza)!.izklopljena
      ? izbranaMiza
      : (proste[0] ?? null)
    const mizaIzb = izb === null ? undefined : mizaPo(izb)
    const cilj = mizaIzb && !mizaIzb.tekma ? mizaIzb.stevilka : null
    return (
      <>
        <GlavaDejanja>
          {pari.length > 0 && (
            <button type="button" className="glava-telefon__gumb" disabled={klicVseh.isPending} onClick={pokliciVse}>
              Pokliči vse · {pari.length}
            </button>
          )}
          {kategorijeListkov.length > 0 && (
            <MeniDejanj naslov="Dejanja strani Mize">
              {(zapri) =>
                kategorijeListkov.map((k) => (
                  <Link
                    key={k.id}
                    to={`/dogodki/${k.id}/listki`}
                    role="menuitem"
                    className="uporabnik-meni__postavka"
                    onClick={zapri}
                  >
                    {oznakaListkov(k)}
                    {kategorijeListkov.length > 1 ? ` · ${k.ime}` : ''}
                  </Link>
                ))
              }
            </MeniDejanj>
          )}
        </GlavaDejanja>
        <section className="mize mize--telefon">
          <NaslovTelefon kazalniki={kazalniki} />
          {napake && (
            <div className="mize-tel__napaka">
              <SporociloNapake napaka={napake} />
            </div>
          )}
          {!nastavljene ? (
            <PrviZagon
              telefon
              stevilo={zagonStevilo}
              naStevilo={nastaviZagonStevilo}
              porazenecSodi={zagonPorazenec}
              naPorazenec={nastaviZagonPorazenca}
              naPostavi={postavi}
              zasedeno={nastavi.isPending}
              napaka={null}
            />
          ) : (
            <>
              <MaliTloris podatki={podatki} izbrana={izb} poudarjene={poudarjeneMize} naIzbiro={nastaviIzbranoMizo} />
              <PodrobnostiMize
                miza={mizaIzb}
                podatki={podatki}
                predlog={mizaIzb ? predlogi.get(mizaIzb.stevilka) : undefined}
                najdlje={najdljeBesedilo}
                onPoklici={(k, miza) => klicKandidata.mutate({ kandidat: k, miza })}
                onIzid={nastaviVnosIzida}
              />
              <VrstaTelefon
                kandidati={podatki.kandidati}
                predlagane={predlagane}
                cilj={cilj}
                prva={proste[0] ?? null}
                vse={vseKartice}
                onNaMizo={(k, miza) => klicKandidata.mutate({ kandidat: k, miza })}
                naPreklopVseh={() => nastaviVseKartice(!vseKartice)}
              />
            </>
          )}
        </section>
        {okna}
      </>
    )
  }

  /* ---------- namizje ---------- */

  return (
    <section className="mize">
      <GlavaMize
        idTurnirja={idTurnirja}
        imeTurnirja={podatki.imeTurnirja}
        ura={uraIzCasa(podatki.casStreznika)}
        kazalniki={kazalniki}
        listki={kategorijeListkov}
      />
      {pregled.error && (
        <div className="mize__napake">
          <NapakaPoizvedbe poizvedba={pregled} kaj="miz" />
        </div>
      )}
      {napake && (
        <div className="mize__napake">
          <SporociloNapake napaka={napake} />
        </div>
      )}

      <CrnaVrstica
        urejanje={urejanjeDvorane}
        vlecem={vlecem !== null}
        predlogov={pari.length}
        vseZasedeno={nastavljene && proste.length === 0}
        najdlje={najdljeBesedilo}
        onUredi={uredi}
        onPoklicVseh={pokliciVse}
        poklicVsehNalaga={klicVseh.isPending}
      />

      {!nastavljene ? (
        <PrviZagon
          telefon={false}
          stevilo={zagonStevilo}
          naStevilo={nastaviZagonStevilo}
          porazenecSodi={zagonPorazenec}
          naPorazenec={nastaviZagonPorazenca}
          naPostavi={postavi}
          zasedeno={nastavi.isPending}
          napaka={null}
          koracnik={koracnikZagona}
        />
      ) : (
        <div className="mize__telo">
          <Tloris
            podatki={podatki}
            predlogi={predlogi}
            poudarjene={poudarjeneMize}
            urejanje={urejanje}
            vlecem={vlecem !== null}
            nadMizo={nadMizo}
            dejanja={dejanja}
            naDodaj={dodajMizo}
          />
          <VrstaTekem
            kandidati={podatki.kandidati}
            predlagane={predlagane}
            vse={vseKartice}
            dejanja={dejanja}
            naPreklopVseh={() => nastaviVseKartice(!vseKartice)}
            dogodkiVTeku={podatki.dogodkiVTeku}
            vlecem={vlecem}
            rocniRed={podatki.rocniVrstniRed}
            naPremik={premakniVVrsti}
            naSamodejno={samodejniRed}
          />
        </div>
      )}

      {okna}
    </section>
  )
}

/* ---------- pisk ---------- */

/* Nastavitev piska je udobje tega prenosnika: hrani se v brskalniku, privzeto vklopljena. */
function beriPisk(): boolean {
  try {
    return localStorage.getItem(KLJUC_PISKA) !== 'ne'
  } catch {
    return true
  }
}

function zapisiPisk(vklopljen: boolean) {
  try {
    localStorage.setItem(KLJUC_PISKA, vklopljen ? 'da' : 'ne')
  } catch {
    // zasebno okno ali prepovedana shramba: nastavitev velja do osvežitve
  }
}

/* Kratek dvojni pisk. Brskalnik dovoli zvok šele po dejanju uporabnika, zato se
   zvočni kontekst ustvari ob prvem kliku na strani (organizator na strani klika). */
function usePisk(): () => void {
  const kontekst = useRef<AudioContext | null>(null)
  useEffect(() => {
    const obDotiku = () => {
      try {
        if (!kontekst.current) kontekst.current = new AudioContext()
        else void kontekst.current.resume()
      } catch {
        // brez zvoka
      }
    }
    window.addEventListener('pointerdown', obDotiku)
    return () => window.removeEventListener('pointerdown', obDotiku)
  }, [])
  return useCallback(() => {
    const k = kontekst.current
    if (!k) return
    for (const zamik of [0, 0.25]) {
      const ton = k.createOscillator()
      const glasnost = k.createGain()
      ton.frequency.value = 880
      glasnost.gain.value = 0.15
      ton.connect(glasnost)
      glasnost.connect(k.destination)
      ton.start(k.currentTime + zamik)
      ton.stop(k.currentTime + zamik + 0.15)
    }
  }, [])
}
