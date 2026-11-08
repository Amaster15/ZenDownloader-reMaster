import nobility.downloader.core.scraper.DownloadProvider;
import nobility.downloader.core.scraper.WatchCatalog.Provider;
import nobility.downloader.Page;

public class DownloadProviderTest {
    private static void check(String url, Provider expected) {
        if (DownloadProvider.INSTANCE.detect(url) != expected) throw new AssertionError(url);
    }
    public static void main(String[] args) {
        check("https://www.wcoflix.tv/anime/overgeared/?season=all&lang=dub", Provider.WCO);
        check("https://hianime.ms/watch-overgeared-episode-1-ba308", Provider.HIANIME);
        check("https://animefreak.it.com/anime/tempal-item-no-chikara-dub/episode/2", Provider.ANIMEFREAK);
        check("https://kimcartoon.com.co/digimon-beatbreak-episode-1/", Provider.KIMCARTOON);
        for (String bad : new String[]{"http://kimcartoon.com.co/test", "https://kimcartoon.com.co.evil.test/test", "https://user@kimcartoon.com.co/test", "https://kimcartoon.com.co:444/test", "not a link"}) {
            try { DownloadProvider.INSTANCE.detect(bad); throw new AssertionError("Accepted " + bad); }
            catch (IllegalArgumentException | IllegalStateException expected) {}
        }
        if (Page.DIRECT_LINK.getMainPage() != Page.DOWNLOADER) throw new AssertionError("Tab grouping");
        if (!Page.DOWNLOADER.getTitle().equals("In progress")) throw new AssertionError("Activity label");
        System.out.println("PASS: provider detection, invalid links and navigation");
    }
}
