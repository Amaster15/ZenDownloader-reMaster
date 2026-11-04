import nobility.downloader.core.BoxHelper;
import nobility.downloader.core.Core;
import nobility.downloader.core.settings.Defaults;
import nobility.downloader.core.driver.BrowserDriverSetup;
import nobility.downloader.core.scraper.WatchCatalog;
public class WatchWcoProbe {
 public static void main(String[] args) throws Exception {
  BoxHelper.Companion.init();
  Core.Companion.setErrorPrintStream(System.err);
  BoxHelper.Companion.update(Defaults.WCO_DOMAIN,"wcoflix");
  BoxHelper.Companion.update(Defaults.WCO_EXTENSION,"tv");
  BrowserDriverSetup.INSTANCE.setup("/usr/bin/chromium");
  var found=QualityProbe.<java.util.List<WatchCatalog.Show>>await(c -> WatchCatalog.INSTANCE.search("Overgeared",WatchCatalog.Provider.WCO,c));
  System.out.println("WCO search results: "+found.size());
  if(found.isEmpty()) throw new AssertionError("Live WCO search returned no series");
  var show=new WatchCatalog.Show("Overgeared","https://wcoflix.tv/anime/overgeared",WatchCatalog.Provider.WCO);
  var listing=QualityProbe.<WatchCatalog.Listing>await(c -> WatchCatalog.INSTANCE.episodes(show,c));
  if (listing.getEntries().size()<5) throw new AssertionError("Missing episodes");
  if (WatchCatalog.INSTANCE.cached(show)==null) throw new AssertionError("No cache");
  System.out.println("PASS: live WCO episode listing and persisted cache: "+listing.getEntries().size()+" episodes");
  listing.getEntries().stream().limit(5).forEach(e -> System.out.println(e.getTitle()));
  System.exit(0);
 }
}
