import nobility.downloader.core.scraper.*;
public class KimCatalogLiveTest {
 public static void main(String[] a)throws Exception {
  var results=QualityProbe.<java.util.List<WatchCatalog.Show>>await(c->WatchCatalog.INSTANCE.search("digimon",WatchCatalog.Provider.KIMCARTOON,c));
  var show=results.stream().filter(s->s.getTitle().equals("Digimon Beatbreak")).findFirst().orElseThrow();
  var byUrl=QualityProbe.<WatchCatalog.Show>await(c->KimCartoon.INSTANCE.show("https://kimcartoon.com.co/digimon-beatbreak-episode-1/",c));
  if(!show.getUrl().equals(byUrl.getUrl()))throw new AssertionError("Episode URL routing");
  var listing=QualityProbe.<WatchCatalog.Listing>await(c->WatchCatalog.INSTANCE.episodes(show,c));
  if(listing.getEntries().size()<2 || WatchCatalog.INSTANCE.cached(show)==null)throw new AssertionError("Catalog/cache missing");
  System.out.println("PASS: live title search, episode URL routing, episode list and disk cache: "+listing.getEntries().size()+" language entries");
  System.exit(0);
 }
}
