/* Zivljenjski cikel narocnine. Placilo je Stripe checkout (redirect) + webhook,
   zato racun in narocnina za placljiv paket nastaneta SELE ob CAKA_PLACILO ->
   AKTIVNA (webhook "checkout.session.completed"). PREKLICANA ostane aktivna
   do konca placanega obdobja (trenutno_obdobje_do), ZAPADLA pomeni neuspelo
   obnovitveno placilo (Stripe "invoice.payment_failed"). PREIZKUS je prvi
   mesec Premium brez placila in brez Stripa (V49, PreizkusPremiumStoritev):
   velja do trenutno_obdobje_do, nato racun preide na Free sam od sebe. */
package si.turnirko.modeli;

public enum StatusNarocnine {
    CAKA_PLACILO,
    AKTIVNA,
    PREKLICANA,
    ZAPADLA,
    PREIZKUS
}
