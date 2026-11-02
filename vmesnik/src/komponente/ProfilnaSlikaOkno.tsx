/* Okno »Profilna slika« (V56): igralec s Premium naloži fotografijo, izbere
   kvadratni izsek (premik z vlečenjem ali puščicami, povečava z drsnikom) in jo
   shrani. Izrez naredi brskalnik (pomozno/profilneSlike.izrezi), strežnik pa
   sliko vseeno prebere in zapiše znova - kraj fotografije (EXIF) ne pride v bazo.

   Okno pove, kje je slika javna, in ob sliki, ki čaka na admina, zakaj čaka. */
import { useEffect, useRef, useState, type KeyboardEvent, type PointerEvent } from 'react'
import { Link } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'

import { profilneSlikeApi } from '../api/zahteve'
import { izrezi } from '../pomozno/profilneSlike'
import { ModalnoOkno } from './ModalnoOkno'
import { NapakaPoizvedbe } from './NapakaPoizvedbe'
import { SlikaIgralca } from './SlikaIgralca'
import { SporociloNapake } from './SporociloNapake'

/* Stranica okna izreza v px (na telefonu se skrči na širino okna). */
const OKNO = 280
const NAJVEC_POVECAVA = 4
/* Korak puščice v px okna. */
const KORAK = 8

interface Izrez {
  slika: ImageBitmap
  /* Povečava nad »pokrij okno« (1 = krajša stranica slike je stranica okna). */
  povecava: number
  /* Zgornji levi vogal izseka v pikah slike. */
  x: number
  y: number
}

export function ProfilnaSlikaOkno({ onZapri }: { onZapri: () => void }) {
  const odjemalec = useQueryClient()
  const moja = useQuery({ queryKey: ['moja-profilna-slika'], queryFn: profilneSlikeApi.moja })
  const [izrez, nastaviIzrez] = useState<Izrez | null>(null)
  const [napakaDatoteke, nastaviNapakoDatoteke] = useState<string | null>(null)
  const izbirnik = useRef<HTMLInputElement>(null)
  const vlecenje = useRef<{ x: number; y: number } | null>(null)

  useEffect(() => () => izrez?.slika.close(), [izrez?.slika])

  function osvezi() {
    odjemalec.invalidateQueries({ queryKey: ['moja-profilna-slika'] })
    odjemalec.invalidateQueries({ queryKey: ['slike-igralcev'] })
  }

  const shrani = useMutation({
    mutationFn: async () => {
      if (!izrez) throw new Error('Najprej izberi fotografijo.')
      const { stranica } = mere(izrez)
      return profilneSlikeApi.nalozi(await izrezi(izrez.slika, izrez.x, izrez.y, stranica))
    },
    onSuccess: (odgovor) => {
      odjemalec.setQueryData(['moja-profilna-slika'], odgovor)
      osvezi()
      nastaviIzrez(null)
    },
  })

  const izbrisi = useMutation({
    mutationFn: () => profilneSlikeApi.izbrisi(),
    onSuccess: () => {
      osvezi()
      nastaviIzrez(null)
    },
  })

  async function obDatoteki(datoteka: File | undefined) {
    nastaviNapakoDatoteke(null)
    if (!datoteka) return
    try {
      const slika = await createImageBitmap(datoteka)
      if (Math.min(slika.width, slika.height) < 160) {
        slika.close()
        nastaviNapakoDatoteke('Fotografija je premajhna (najmanj 160 × 160 px).')
        return
      }
      const stranica = Math.min(slika.width, slika.height)
      nastaviIzrez({ slika, povecava: 1, x: (slika.width - stranica) / 2, y: (slika.height - stranica) / 2 })
    } catch {
      nastaviNapakoDatoteke('Datoteke ni bilo mogoče odpreti kot fotografije. Izberi JPG ali PNG.')
    } finally {
      if (izbirnik.current) izbirnik.current.value = ''
    }
  }

  /* Premik izseka za (dx, dy) px okna; izsek ne sme čez rob slike. */
  function premakni(dx: number, dy: number) {
    nastaviIzrez((prej) => {
      if (!prej) return prej
      const { merilo, stranica } = mere(prej)
      return {
        ...prej,
        x: omeji(prej.x - dx / merilo, 0, prej.slika.width - stranica),
        y: omeji(prej.y - dy / merilo, 0, prej.slika.height - stranica),
      }
    })
  }

  /* Povečava ohrani sredino izseka. */
  function povecaj(nova: number) {
    nastaviIzrez((prej) => {
      if (!prej) return prej
      const stara = mere(prej)
      const naslednji = { ...prej, povecava: nova }
      const nove = mere(naslednji)
      const sx = prej.x + stara.stranica / 2
      const sy = prej.y + stara.stranica / 2
      return {
        ...naslednji,
        x: omeji(sx - nove.stranica / 2, 0, prej.slika.width - nove.stranica),
        y: omeji(sy - nove.stranica / 2, 0, prej.slika.height - nove.stranica),
      }
    })
  }

  const m = moja.data
  const pojasnilo = m?.pojasnilo ?? null

  return (
    <ModalnoOkno srednje naslov="Profilna slika" onZapri={onZapri}>
      <div className="obrazec profilna-okno">
        <p className="profilna-okno__uvod">
          Slika stoji na tvojem profilu, v pregledu 1 na 1 in na stopničkah, dokler imaš Igralca
          Premium. Ko Premium poteče, se skrije, shranjena pa ostane. Na sliki naj boš ti — brez
          napisov in logotipov.
        </p>

        <NapakaPoizvedbe poizvedba={moja} kaj="profilne slike" />

        {m && !m.smeNalagati && (
          <div className="obvestilo obvestilo--opozorilo">
            {pojasnilo}{' '}
            {pojasnilo?.includes('Premium') && <Link to="/narocnina">Naročnina →</Link>}
          </div>
        )}

        {m?.smeNalagati && !izrez && (
          <div className="profilna-okno__trenutna">
            <SlikaIgralca zgostitev={m.zgostitev} velikost="VELIKA" mera={160} />
            <div className="profilna-okno__stanje">
              <span className="oznaka-polja">
                {m.zgostitev == null
                  ? 'Brez slike'
                  : m.stanje === 'CAKA'
                    ? 'Čaka na potrditev'
                    : 'Javna'}
              </span>
              {m.stanje === 'CAKA' && pojasnilo && <p className="profilna-okno__pojasnilo">{pojasnilo}</p>}
            </div>
          </div>
        )}

        {izrez && (
          <div className="profilna-okno__izrez">
            <div
              className="profilna-okno__okno"
              style={{ width: OKNO, height: OKNO }}
              tabIndex={0}
              role="group"
              aria-label="Izsek fotografije. Premakni ga s puščicami ali z vlečenjem."
              onPointerDown={(d: PointerEvent<HTMLDivElement>) => {
                d.currentTarget.setPointerCapture(d.pointerId)
                vlecenje.current = { x: d.clientX, y: d.clientY }
              }}
              onPointerMove={(d: PointerEvent<HTMLDivElement>) => {
                if (!vlecenje.current) return
                premakni(d.clientX - vlecenje.current.x, d.clientY - vlecenje.current.y)
                vlecenje.current = { x: d.clientX, y: d.clientY }
              }}
              onPointerUp={() => (vlecenje.current = null)}
              onPointerCancel={() => (vlecenje.current = null)}
              onKeyDown={(d: KeyboardEvent<HTMLDivElement>) => {
                const premik: Record<string, [number, number]> = {
                  ArrowLeft: [KORAK, 0],
                  ArrowRight: [-KORAK, 0],
                  ArrowUp: [0, KORAK],
                  ArrowDown: [0, -KORAK],
                }
                const p = premik[d.key]
                if (!p) return
                d.preventDefault()
                premakni(p[0], p[1])
              }}
            >
              <IzrezPlatno izrez={izrez} />
            </div>
            <label className="obrazec__polje profilna-okno__povecava">
              <span>Povečava</span>
              <input
                type="range"
                min={1}
                max={NAJVEC_POVECAVA}
                step={0.05}
                value={izrez.povecava}
                onChange={(d) => povecaj(Number(d.target.value))}
              />
            </label>
          </div>
        )}

        {napakaDatoteke && <div className="napaka">{napakaDatoteke}</div>}

        {m?.smeNalagati && (
          <div className="logotip-okno__dejanja">
            <label className="gumb logotip-okno__izberi">
              <input
                ref={izbirnik}
                type="file"
                accept="image/jpeg,image/png,image/webp"
                onChange={(d) => obDatoteki(d.target.files?.[0])}
              />
              {izrez ? 'Izberi drugo fotografijo' : m.zgostitev ? 'Zamenjaj sliko' : 'Izberi fotografijo'}
            </label>
            {!izrez && m.zgostitev && (
              <button
                type="button"
                className="gumb gumb--nevaren"
                disabled={izbrisi.isPending}
                onClick={() => izbrisi.mutate()}
              >
                Odstrani sliko
              </button>
            )}
          </div>
        )}

        <SporociloNapake napaka={shrani.error ?? izbrisi.error} />
        <div className="obrazec__noga">
          <span className="obrazec__noga-stanje">
            {izrez ? 'Izsek ni shranjen' : 'Ni neshranjenih sprememb'}
          </span>
          <div className="obrazec__noga-gumbi">
            <button type="button" className="gumb" onClick={onZapri}>
              Zapri
            </button>
            {izrez && (
              <button
                type="button"
                className="gumb gumb--glavni"
                disabled={shrani.isPending}
                onClick={() => shrani.mutate()}
              >
                {shrani.isPending ? 'Shranjujem …' : 'Shrani sliko'}
              </button>
            )}
          </div>
        </div>
      </div>
    </ModalnoOkno>
  )
}

/* Izsek na platnu v velikosti okna - tako vidi točno to, kar bo shranjeno. */
function IzrezPlatno({ izrez }: { izrez: Izrez }) {
  const platno = useRef<HTMLCanvasElement>(null)
  useEffect(() => {
    const c = platno.current
    const ctx = c?.getContext('2d')
    if (!c || !ctx) return
    const gostota = window.devicePixelRatio || 1
    c.width = OKNO * gostota
    c.height = OKNO * gostota
    const { stranica } = mere(izrez)
    ctx.imageSmoothingQuality = 'high'
    ctx.drawImage(izrez.slika, izrez.x, izrez.y, stranica, stranica, 0, 0, c.width, c.height)
  }, [izrez])
  return <canvas ref={platno} className="profilna-okno__platno" aria-hidden="true" />
}

/* Merilo (px okna na piko slike) in stranica izseka v pikah slike. */
function mere(izrez: Izrez) {
  const krajsa = Math.min(izrez.slika.width, izrez.slika.height)
  const stranica = krajsa / izrez.povecava
  return { stranica, merilo: OKNO / stranica }
}

function omeji(v: number, najmanj: number, najvec: number) {
  return Math.min(Math.max(v, najmanj), Math.max(najmanj, najvec))
}
