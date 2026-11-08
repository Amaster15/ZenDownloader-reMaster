import nobility.downloader.core.BoxHelper;
import nobility.downloader.core.driver.BrowserDriverSetup;
import nobility.downloader.core.scraper.WatchCatalog;
public class AnimeFreakSearchSmoke {
 public static void main(String[] args) throws Exception {
  BoxHelper.Companion.init();
  BrowserDriverSetup.INSTANCE.setup("");
  long start=System.nanoTime();
  try {
   var shows=QualityProbe.await(c -> WatchCatalog.INSTANCE.search("overgeared", WatchCatalog.Provider.ANIMEFREAK,c));
   System.out.println("RESULT: "+shows);
  } finally { System.out.println("Elapsed seconds: "+(System.nanoTime()-start)/1_000_000_000); }
  System.exit(0);
 }
}
