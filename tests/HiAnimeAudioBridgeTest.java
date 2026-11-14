import nobility.downloader.core.scraper.player.HiAnimePlaybackServer;
import java.net.*;import java.net.http.*;
public class HiAnimeAudioBridgeTest {
 static String get(String u)throws Exception {return HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create(u)).GET().build(),HttpResponse.BodyHandlers.ofString()).body();}
 public static void main(String[]x)throws Exception {
  String playlist="#EXTM3U\n#EXT-X-TARGETDURATION:4\n#EXTINF:4,\nhttps://example.invalid/1.ts\n#EXT-X-ENDLIST\n";
  try(var audio=new HiAnimePlaybackServer(playlist,1,(i,c)->new byte[188]);var video=new HiAnimePlaybackServer(playlist,1,audio.getUrl(),(i,c)->new byte[188])) {
   String master=get(video.getUrl());
   if(!master.contains("TYPE=AUDIO")||!master.contains(audio.getUrl()))throw new AssertionError("Separate audio missing");
   if(!get(video.getUrl().replace("index.m3u8","video.m3u8")).contains("/segment/0.ts"))throw new AssertionError("Video not routed");
   if(!get(audio.getUrl()).contains("/segment/0.ts"))throw new AssertionError("Audio not routed");
  }
  System.out.println("PASS: audio master and separate private audio/video playlist routes");
 }
}
