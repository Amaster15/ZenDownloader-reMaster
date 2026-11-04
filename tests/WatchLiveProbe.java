import java.util.List;
import nobility.downloader.core.scraper.WatchCatalog;
import nobility.downloader.core.scraper.video_download.HiAnimeFallback;
import nobility.downloader.core.settings.Quality;
public class WatchLiveProbe {
 public static void main(String[] args) throws Exception {
  var catalog=WatchCatalog.INSTANCE;
  var shows=QualityProbe.<List<WatchCatalog.Show>>await(c -> catalog.search("Overgeared",WatchCatalog.Provider.HIANIME,c));
  var show=shows.stream().filter(s -> s.getTitle().equals("Overgeared")).findFirst().orElseThrow();
  var listing=QualityProbe.<WatchCatalog.Listing>await(c -> catalog.episodes(show,c));
  System.out.println("Live search and episode list: "+listing.getEntries().size()+" language entries");
  var cached=catalog.cached(show);
  if (cached==null || !catalog.fresh(cached) || cached.getEntries().size()!=listing.getEntries().size()) throw new AssertionError("Cache round trip");
  var episode=listing.getEntries().stream().filter(e -> e.getTitle().equals("Episode 2 English Dubbed")).findFirst().orElseThrow();
  var provider=new HiAnimeFallback();
  var match=QualityProbe.<HiAnimeFallback.Match>await(c -> provider.watchMatch(episode.getUrl(),episode.getLanguage(),c));
  if(match.getEpisode()!=2 || !match.getLanguage().equals("dub")) throw new AssertionError("Wrong episode");
  var stream=QualityProbe.<HiAnimeFallback.Stream>await(c -> provider.resolve(match,Quality.MED,c));
  System.out.println("Resolved episode 2 dub: "+stream.getQuality()+", "+stream.getSeconds()+" seconds");
  java.nio.file.Files.writeString(java.nio.file.Path.of(args[0]),stream.getPlaylist());
  var playlist=QualityProbe.<String>await(c -> provider.playbackPlaylist(stream,c));
  try (var bridge=new nobility.downloader.core.scraper.player.HiAnimePlaybackServer(playlist,stream.getSegments().size(),(i,c) -> provider.playbackSegment(stream.getSegments().get(i),c))) {
   var process=new ProcessBuilder("ffmpeg","-nostdin","-v","error","-i",bridge.getUrl(),"-t","2","-f","null","-").redirectErrorStream(true).redirectOutput(new java.io.File("/tmp/zen-watch-decode.log")).start();
   if (!process.waitFor(90,java.util.concurrent.TimeUnit.SECONDS)) { process.destroyForcibly(); throw new AssertionError("Decode timed out"); }
   if (process.exitValue()!=0) throw new AssertionError("Decode failed; see /tmp/zen-watch-decode.log");
  }
  System.out.println("PASS: live HiAnime search, episode list, cache, exact selection, and 2-second video/audio decode through the streaming bridge");
  System.exit(0);
 }
}
