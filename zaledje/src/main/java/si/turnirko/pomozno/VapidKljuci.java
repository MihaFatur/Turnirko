/* Ustvari par kljucev VAPID za potisna obvestila (PotisnaObvestilaStoritev):
   izpise nastavitvi za streznik. Zasebni kljuc je skrivnost (okoljska spremenljivka
   ali datoteka config/application-<profil>.properties, nikoli v repozitorij); javni
   gre brskalnikom in ni skrivnost. Kljuca se ne menjata: z zamenjavo bi vse
   narocene naprave izgubile obvestila in bi se morale narociti znova.

   Zagon (po `mvnw package` ali iz target/classes):
     java -cp target/classes si.turnirko.pomozno.VapidKljuci */
package si.turnirko.pomozno;

public final class VapidKljuci {

    private VapidKljuci() {}

    public static void main(String[] argumenti) {
        String[] par = WebPush.novParKljucevVapid();
        System.out.println("# Potisna obvestila (VAPID). Zasebni kljuc je skrivnost.");
        System.out.println("TURNIRKO_PUSH_JAVNI_KLJUC=" + par[0]);
        System.out.println("TURNIRKO_PUSH_ZASEBNI_KLJUC=" + par[1]);
        System.out.println("# ali v config/application-<profil>.properties:");
        System.out.println("# turnirko.push.javni-kljuc=" + par[0]);
        System.out.println("# turnirko.push.zasebni-kljuc=" + par[1]);
    }
}
