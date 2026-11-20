/* Vadnica števk (/vadnica, samo admin; načrt F3): hladen začetek učenja branja
   listkov s slovensko pisavo, preden pride dovolj pravih listkov.

   1. Natisni liste (vsak ima svojo kodo) in jih razdeli, npr. na treningu kluba:
      vsak udeleženec izpolni en list (~180 števk).
   2. Izpolnjene liste fotografiraj; Turnirko list prebere in shrani števke z
      oznako vrstice - še NE za učenje.
   3. Vsak list preglej, izloči slabo napisane ali narobe razrezane števke in ga
      odobri. Šele odobrene gredo v nočno učenje (zastrupitev mreže, F3 varnost). */
import { useState, type ChangeEvent } from 'react'
import { Link } from 'react-router-dom'
import { useQuery, useQueryClient } from '@tanstack/react-query'

import { opisNapake } from '../api/odjemalec'
import type { VadnicaDto } from '../api/tipi'
import { vadnicaApi } from '../api/zahteve'
import { SporociloNapake } from '../komponente/SporociloNapake'
import { pomanjsajFotografijo } from '../pomozno/branjeListkov'
import { useNaslovStrani } from '../pomozno/naslovStrani'
import { NAJVEC_LISTOV, NAJVEC_STRANICA_VADNICE, oznakaLista } from '../pomozno/vadnica'

/* Fotografija lista na poti ali že prebrana (sporočilo pod gumbom). */
interface Posiljanje {
  kljuc: number
  ime: string
  stanje: 'posiljam' | 'prebran' | 'napaka'
  besedilo: string
}

let naslednjiKljuc = 1

export function VadnicaStran() {
  useNaslovStrani('Vadnica števk')
  const odjemalec = useQueryClient()
  const seznam = useQuery({ queryKey: ['vadnice'], queryFn: vadnicaApi.seznam })
  const [listov, nastaviListov] = useState('10')
  const [posiljanja, nastaviPosiljanja] = useState<Posiljanje[]>([])

  const steviloZaTisk = Math.min(NAJVEC_LISTOV, Math.max(1, Math.floor(Number(listov)) || 1))

  async function poslji(datoteka: File) {
    const kljuc = naslednjiKljuc++
    const posodobi = (p: Partial<Posiljanje>) =>
      nastaviPosiljanja((x) => x.map((y) => (y.kljuc === kljuc ? { ...y, ...p } : y)))
    nastaviPosiljanja((x) => [{ kljuc, ime: datoteka.name, stanje: 'posiljam', besedilo: 'Berem list …' }, ...x])
    try {
      const jpeg = await pomanjsajFotografijo(datoteka, NAJVEC_STRANICA_VADNICE)
      const list = await vadnicaApi.nalozi(jpeg)
      posodobi({ stanje: 'prebran', besedilo: povzetekBranja(list) })
      void odjemalec.invalidateQueries({ queryKey: ['vadnice'] })
    } catch (napaka) {
      posodobi({ stanje: 'napaka', besedilo: opisNapake(napaka) })
    }
  }

  function obFotografiji(dogodek: ChangeEvent<HTMLInputElement>) {
    const datoteke = [...(dogodek.target.files ?? [])]
    dogodek.target.value = ''
    // ena za drugo: strežnik bere največ dve fotografiji hkrati
    void datoteke.reduce<Promise<void>>((veriga, d) => veriga.then(() => poslji(d)), Promise.resolve())
  }

  const listi = seznam.data?.listi ?? []
  const zaPregled = listi.filter((l) => !l.odobrena)
  const odobreni = listi.filter((l) => l.odobrena)

  return (
    <section className="vadnica">
      <div className="stran-glava">
        <div>
          <h1 className="naslov-strani naslov-strani--enovrsticni">Vadnica števk</h1>
          <p className="uvod uvod--tesno">
            Ročno napisane števke za učenje branja listkov, preden pride dovolj pravih listkov.
          </p>
        </div>
      </div>

      <div className="vadnica__korak">
        <h2>1. Natisni liste</h2>
        <p>
          Vsak list izpolni ena oseba: v 140 okenc prepiše števke 0–9 in števila 10–13, kot jih
          piše na sodniški listek. Vsak list ima svojo kodo, zato ga ne fotokopiraj. Natisnjeni
          listi veljajo, dokler se postavitev lista ne spremeni.
        </p>
        <div className="vadnica__vrsta">
          <label className="vadnica__polje">
            Listov
            <input
              type="number"
              inputMode="numeric"
              min={1}
              max={NAJVEC_LISTOV}
              value={listov}
              onChange={(e) => nastaviListov(e.target.value)}
            />
          </label>
          <Link to={`/vadnica/tisk?n=${steviloZaTisk}`} className="gumb">
            Odpri liste za tisk
          </Link>
        </div>
      </div>

      <div className="vadnica__korak">
        <h2>2. Fotografiraj izpolnjene liste</h2>
        <p>
          En list na fotografijo, od zgoraj, da se vidijo vsi trije črni kvadrati in koda v
          desnem kotu. Isti list lahko fotografiraš znova, dokler ga ne odobriš.
        </p>
        <div className="vadnica__vrsta">
          <label className="gumb gumb--glavni vadnica__fotografiraj">
            Fotografiraj list
            <input
              type="file"
              accept="image/*"
              capture="environment"
              multiple
              className="vadnica__datoteka"
              onChange={obFotografiji}
            />
          </label>
        </div>
        {posiljanja.length > 0 && (
          <ul className="vadnica__sporocila" aria-live="polite">
            {posiljanja.map((p) => (
              <li
                key={p.kljuc}
                className={
                  'vadnica__sporocilo' +
                  (p.stanje === 'napaka' ? ' vadnica__sporocilo--napaka' : '') +
                  (p.stanje === 'prebran' ? ' vadnica__sporocilo--uspeh' : '')
                }
              >
                {p.besedilo}
              </li>
            ))}
          </ul>
        )}
      </div>

      <div className="vadnica__korak">
        <h2>3. Preglej in odobri</h2>
        <p>
          Števke gredo v učenje šele, ko list odobriš. Pri pregledu izloči števke, ki niso
          napisane, kot piše v vrstici, ali so narobe razrezane.
        </p>
        <SporociloNapake napaka={seznam.error} />
        {seznam.isPending ? (
          <p className="obvestilo">Nalaganje …</p>
        ) : zaPregled.length === 0 ? (
          <p className="namig">Ni listov, ki čakajo na pregled.</p>
        ) : (
          <TabelaListov listi={zaPregled} />
        )}
      </div>

      <div className="vadnica__korak">
        <h2>V učenju</h2>
        <p>
          {seznam.data ? seznam.data.stevkVUcenju : '–'} števk z {odobreni.length}{' '}
          {odobreni.length === 1 ? 'odobrenega lista' : 'odobrenih listov'}. Učenje teče vsako noč,
          ko je novih števk vsaj 200; nova mreža se sprejme samo, če bere bolje.
        </p>
        {odobreni.length > 0 && <TabelaListov listi={odobreni} />}
      </div>
    </section>
  )
}

function TabelaListov({ listi }: { listi: VadnicaDto[] }) {
  return (
    <div className="tabela-ovoj">
      <table className="tabela vadnica__listi">
        <thead>
          <tr>
            <th>List</th>
            <th className="vadnica__stevilo">Števk</th>
            <th>Fotografiran</th>
          </tr>
        </thead>
        <tbody>
          {listi.map((l) => (
            <tr key={l.id}>
              <td>
                <Link to={`/vadnica/${l.id}`}>{oznakaLista(l.id)}</Link>
              </td>
              <td className="vadnica__stevilo">{l.stevk}</td>
              <td>{datumInUra(l.ustvarjenoOb)}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}

/* »List V-… prebran: 172 števk, 3 prazna okenca, 1 preskočeno.« */
export function povzetekBranja(l: VadnicaDto): string {
  const deli = [`${l.stevk} ${l.stevk === 1 ? 'števka' : l.stevk === 2 ? 'števki' : l.stevk <= 4 ? 'števke' : 'števk'}`]
  if (l.praznih > 0) deli.push(`praznih okenc ${l.praznih}`)
  if (l.preskocenih > 0) deli.push(`preskočenih okenc ${l.preskocenih}`)
  return `List ${oznakaLista(l.id)} prebran: ${deli.join(', ')}. Čaka na pregled.`
}

function datumInUra(cas: string): string {
  const d = new Date(cas)
  if (Number.isNaN(d.getTime())) return cas
  return d.toLocaleString('sl-SI', { day: 'numeric', month: 'numeric', hour: '2-digit', minute: '2-digit' })
}
