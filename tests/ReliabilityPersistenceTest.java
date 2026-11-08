import nobility.downloader.core.maintenance.*;
import nobility.downloader.core.scraper.WatchCatalog;
import nobility.downloader.core.settings.Quality;
public class ReliabilityPersistenceTest {
 public static void main(String[] args) {
  var failures=FailedDownloads.INSTANCE;
  if(args[0].equals("seed")) {
   failures.begin(new FailedDownloads.Request(WatchCatalog.Provider.HIANIME,"Series","https://hianime.ms/details/test","Episode 1","https://hianime.ms/watch/test","dub",Quality.HIGH,false,false,""));
   failures.begin(new FailedDownloads.Request(WatchCatalog.Provider.HIANIME,"Series","https://hianime.ms/details/test","Episode 1","https://hianime.ms/watch/test","sub",Quality.HIGH,true,false,""));
   failures.fail("https://hianime.ms/watch/test|dub","Server unavailable");
   WatchProgress.update("test|dub",120,1400);
  } else {
   failures.load();
   if(failures.getRequests().size()!=2)throw new AssertionError("Missing retries after restart");
   if(WatchProgress.get("test|dub").getSeconds()!=120)throw new AssertionError("Resume lost");
   failures.complete("https://hianime.ms/watch/test|dub");
   if(failures.getRequests().size()!=1 || !failures.getRequests().get(0).getLanguage().equals("sub"))throw new AssertionError("Completion removed other language");
   if(!failures.getRequests().get(0).getReason().contains("Interrupted"))throw new AssertionError("Interrupted job lost");
  }
  System.out.println("PASS: failure/resume persistence "+args[0]);
 }
}
