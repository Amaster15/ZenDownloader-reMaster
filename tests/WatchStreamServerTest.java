import java.net.URI;
import java.net.http.*;
import java.util.concurrent.atomic.AtomicInteger;
import nobility.downloader.core.scraper.player.HiAnimePlaybackServer;
public class WatchStreamServerTest {
 public static void main(String[] args) throws Exception {
  var requests=new AtomicInteger();
  byte[] ts=new byte[188*5]; for(int i=0;i<ts.length;i+=188)ts[i]=0x47;
  String url;
  var client=HttpClient.newHttpClient();
  try (var bridge=new HiAnimePlaybackServer("#EXTM3U\n#EXT-X-TARGETDURATION:5\n#EXTINF:5,\nhttps://example.invalid/segment.image\n#EXT-X-ENDLIST\n",1,(i,c) -> {requests.incrementAndGet();return ts;})) {
   url=bridge.getUrl();
   var playlist=client.send(HttpRequest.newBuilder(URI.create(url)).GET().build(),HttpResponse.BodyHandlers.ofString());
   if(playlist.statusCode()!=200 || playlist.body().contains("example.invalid")) throw new AssertionError("Invalid local playlist");
   var segment=playlist.body().lines().filter(l -> l.endsWith(".ts")).findFirst().orElseThrow();
   for(int i=0;i<2;i++) {
    var response=client.send(HttpRequest.newBuilder(URI.create(url).resolve(segment)).GET().build(),HttpResponse.BodyHandlers.ofByteArray());
    if(response.statusCode()!=200 || !java.util.Arrays.equals(response.body(),ts)) throw new AssertionError("Wrong segment payload");
   }
   if(requests.get()!=1) throw new AssertionError("Segments not cached");
   for(String path:new String[]{"/missing/index.m3u8",segment.replace("0.ts","9.ts"),"/?url=https://example.invalid/"}) {
    var response=client.send(HttpRequest.newBuilder(URI.create(url).resolve(path)).GET().build(),HttpResponse.BodyHandlers.discarding());
    if(response.statusCode()!=404) throw new AssertionError("Unknown requests must be rejected");
   }
  }
  try { client.send(HttpRequest.newBuilder(URI.create(url)).GET().build(),HttpResponse.BodyHandlers.discarding()); throw new AssertionError("Server did not close"); }
  catch(java.io.IOException expected) {}
  System.out.println("PASS: private playback playlist, segment bytes, cache reuse, invalid path rejection, shutdown");
 }
}
