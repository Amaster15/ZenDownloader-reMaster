import nobility.downloader.core.scraper.AnimeFreak;
import nobility.downloader.core.scraper.player.HiAnimePlaybackServer;
import java.net.*;
public class AnimeBridgeTest {
 public static void main(String[] a)throws Exception {
 var server=com.sun.net.httpserver.HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
 server.createContext("/",ex->{
 byte[] data=switch(ex.getRequestURI().getPath()) {
 case "/master" -> "#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=1000\nmedia\n".getBytes();
 case "/media" -> "#EXTM3U\n#EXT-X-TARGETDURATION:5\n#EXTINF:5,\nchunk\n#EXT-X-ENDLIST\n".getBytes();
 default -> new byte[2048]; };
 if(!"agent".equals(ex.getRequestHeaders().getFirst("User-Agent"))||ex.getRequestHeaders().getFirst("Referer")==null){ex.sendResponseHeaders(403,-1);ex.close();return;}
 ex.sendResponseHeaders(200,data.length);ex.getResponseBody().write(data);ex.close(); });server.start();
 try(var bridge=QualityProbe.<HiAnimePlaybackServer>await(c->AnimeFreak.INSTANCE.playback(new AnimeFreak.Stream("http://127.0.0.1:"+server.getAddress().getPort()+"/master","https://example.com/player","agent"),c))){
 String playlist=new String(URI.create(bridge.getUrl()).toURL().openStream().readAllBytes());
 String segment=playlist.lines().filter(x->!x.isBlank()&&!x.startsWith("#")).findFirst().orElseThrow();
 try(var in=URI.create(bridge.getUrl()).resolve(segment).toURL().openStream()){if(in.readAllBytes().length!=2048)throw new AssertionError();}
 System.out.println("PASS: master playlist, relative URLs, request headers and stream forwarding");
 }finally{server.stop(0);}System.exit(0);
 }
}
