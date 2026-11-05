import nobility.downloader.core.scraper.*;
import java.util.List;
public class AnimeTitleIndexTest {
 public static void main(String[] args) {
  var index=AnimeTitleIndex.INSTANCE;
  var show=new WatchCatalog.Show("Overgeared (Dub)","https://animefreak.it.com/anime/tempal-item-no-chikara-dub",WatchCatalog.Provider.ANIMEFREAK);
  var record=new AnimeTitleIndex.Record(show.getUrl(),show.getTitle(),List.of(index.alternateTitle(show),"テンパル"));
  for(String query:List.of("Overgeared", "TEMPAL: Item no Chikara", "tempal dub", "テンパル"))
   if(!index.matches(record,query)) throw new AssertionError("Missed alias: "+query);
  if(index.matches(record,"Naruto")||index.matches(record,"   "))throw new AssertionError("Unrelated match");
  if(!index.normalize("Pokémon： ＸＹ").equals("pokemon xy"))throw new AssertionError("Unicode normalization");
  index.remember(List.of(show));
  index.remember(List.of(new WatchCatalog.Show("Tempal: Item no Chikara (Dub)",show.getUrl(),show.getProvider())));
  if(index.search("Overgeared").size()!=1 || index.search("Tempal Item").size()!=1)throw new AssertionError("Lost persisted title alias");
  System.out.println("PASS: English/Japanese aliases, punctuation, accents, Unicode and persisted title changes");
 }
}
