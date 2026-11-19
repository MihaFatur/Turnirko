/* Predal s podrobnimi navodili ob desnem robu (na telefonu čez zaslon).

   Odločitev lastnika: »Preberi več« ne odpelje s strani, ker ima organizator
   med turnirjem odprto mrežo ali vnos rezultata. Predal zato NI modalen — stran
   pod njim ostane uporabna in na namizju se da brati in klikati hkrati. Stoji
   nad modalnimi okni (z-index), ker ga lahko odpre gumb »?« v oknu. */
import { useEffect, useRef } from 'react'
import { Link } from 'react-router-dom'

import { potDoClanka, SkokPomociKontekst } from './Besedilo'
import { usePomoc } from './PomocKontekst'
import { idRazdelka, VsebinaClanka } from './VsebinaClanka'
import { najdiClanek, POGLAVJA, smeVideti } from './vsebina'

const PREDPONA = 'predal'

export function PredalPomoci() {
  const pomoc = usePomoc()
  const mesto = pomoc.predal[pomoc.predal.length - 1]
  const clanek = mesto ? najdiClanek(mesto.clanek) : undefined
  const dovoljen = clanek && smeVideti(clanek, pomoc.jeAdmin) ? clanek : undefined
  const telo = useRef<HTMLDivElement>(null)
  const naslov = useRef<HTMLHeadingElement>(null)

  /* Fokus ob odprtju na naslov, ob zaprtju nazaj, od koder je prišel. */
  useEffect(() => {
    const prej = document.activeElement as HTMLElement | null
    naslov.current?.focus({ preventScroll: true })
    return () => {
      if (prej?.isConnected) prej.focus({ preventScroll: true })
    }
  }, [])

  /* Ob vsakem novem mestu: na razdelek ali na vrh. Odmik se izračuna znotraj
     telesa predala — scrollIntoView bi premaknil še stran pod njim. */
  useEffect(() => {
    const t = telo.current
    if (!t || !mesto) return
    const cilj = mesto.razdelek
      ? t.querySelector<HTMLElement>('#' + CSS.escape(idRazdelka(PREDPONA, mesto.razdelek)))
      : null
    t.scrollTop = cilj
      ? cilj.getBoundingClientRect().top - t.getBoundingClientRect().top + t.scrollTop - 8
      : 0
  }, [mesto])

  if (!mesto) return null
  const poglavje = dovoljen ? POGLAVJA.find((p) => p.id === dovoljen.poglavje) : undefined
  const povezani = (dovoljen?.povezani ?? [])
    .map((id) => najdiClanek(id))
    .filter((c) => c && smeVideti(c, pomoc.jeAdmin))

  return (
    <aside className="pomoc-predal" role="dialog" aria-labelledby="pomoc-predal-naslov">
      <div className="pomoc-predal__glava">
        <div className="pomoc-predal__vrstica">
          {pomoc.predal.length > 1 ? (
            <button type="button" className="pomoc-predal__gumb" onClick={pomoc.nazajVPredalu}>
              ← Nazaj
            </button>
          ) : (
            <span className="pomoc-predal__nad">Pomoč{poglavje ? ` · ${poglavje.naslov}` : ''}</span>
          )}
          <button type="button" className="pomoc-predal__gumb" onClick={pomoc.zapriPredal}>
            Zapri ×
          </button>
        </div>
        <h2 id="pomoc-predal-naslov" ref={naslov} tabIndex={-1} className="pomoc-predal__naslov">
          {dovoljen ? dovoljen.naslov : 'Navodila ni mogoče najti'}
        </h2>
      </div>

      <div ref={telo} className="pomoc-predal__telo">
        {dovoljen ? (
          <SkokPomociKontekst.Provider value={(cilj) => pomoc.odpriVPredalu(cilj, true)}>
            <p className="pomoc-uvod">{dovoljen.kratko}</p>
            <VsebinaClanka clanek={dovoljen} predpona={PREDPONA} />
            {povezani.length > 0 && (
              <div className="pomoc-povezano">
                <h3 className="pomoc-povezano__naslov">Povezano</h3>
                <ul>
                  {povezani.map((c) => (
                    <li key={c!.id}>
                      <a
                        href={potDoClanka({ clanek: c!.id })}
                        className="pomoc-povezava"
                        onClick={(dogodek) => {
                          if (dogodek.button !== 0 || dogodek.ctrlKey || dogodek.metaKey) return
                          dogodek.preventDefault()
                          pomoc.odpriVPredalu({ clanek: c!.id }, true)
                        }}
                      >
                        {c!.naslov}
                      </a>
                    </li>
                  ))}
                </ul>
              </div>
            )}
          </SkokPomociKontekst.Provider>
        ) : (
          <p className="obvestilo">Ta navodila ne obstajajo ali niso zate.</p>
        )}
      </div>

      <div className="pomoc-predal__noga">
        {dovoljen && (
          <Link to={potDoClanka(mesto)} className="pomoc-predal__povezava" onClick={pomoc.zapriPredal}>
            Odpri celo stran →
          </Link>
        )}
        <Link to="/pomoc" className="pomoc-predal__povezava" onClick={pomoc.zapriPredal}>
          Vsa navodila
        </Link>
      </div>
    </aside>
  )
}
