/* Podatki o ponudniku za stran Pogoji in zasebnost (/pogoji).

   Na enem mestu, ker jih bere več strani (pogoji, zasebnost, kasneje računi)
   in se spremenijo hkrati. Prodajalec naročnin je Jaka Golavšek s.p.
   (odločitev lastnika, 12. 10. 2026; prej NTK Žalec). Kar je null, še ni vpisano - stran
   vrstico izpusti, zato jih DOPOLNI pred objavo: brez naslova in e-pošte
   pogoji ne izpolnjujejo zahteve ZEPT po stalno dostopnih podatkih o
   ponudniku, brez kontakta pa igralec ne more uveljavljati pravic. */
export const PONUDNIK: {
  ime: string
  naslov: string | null
  maticna: string | null
  davcna: string | null
  eposta: string | null
  /* Kje teče strežnik (ime ponudnika gostovanja) - obdelovalec podatkov. */
  gostovanje: string | null
  /* Potrdila računovodkinja 21. 10. 2026: ni zavezanec za DDV ne atipični
     zavezanec. Ob prestopu praga (60.000 € prometa na leto) se spremeni
     skupaj z nastavitvijo v Moj Računu - od nje je odvisna klavzula na računih. */
  zavezanecZaDdv: boolean
} = {
  ime: 'Jaka Golavšek s.p.',
  naslov: 'Šešče pri Preboldu 6, 3312 Prebold',
  maticna: '7178638000',
  davcna: '19045301',
  eposta: 'info@turnirko-nt.si',
  gostovanje: 'Herznet',
  zavezanecZaDdv: false,
}

/* Od kdaj veljajo objavljeni pogoji (ISO datum); null = se ne izpiše. */
export const POGOJI_VELJAJO_OD: string | null = '2026-10-21'
