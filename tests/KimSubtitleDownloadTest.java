import nobility.downloader.core.scraper.AnimeFreak;
import java.nio.file.*;
import java.net.*;
public class KimSubtitleDownloadTest {
 public static void main(String[] args)throws Exception {
  var root=Path.of(args[0]);
  var server=com.sun.net.httpserver.HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
  server.setExecutor(java.util.concurrent.Executors.newCachedThreadPool());
  server.createContext("/",e->{try{byte[] data=Files.readAllBytes(root.resolve(e.getRequestURI().getPath().substring(1)));e.sendResponseHeaders(200,data.length);e.getResponseBody().write(data);}finally{e.close();}});server.start();
  var sub=Files.createTempFile("zen-subtest-",".vtt");Files.writeString(sub,"WEBVTT\n\n00:00:00.000 --> 00:00:04.000\nTest captions\n");
  try {
   var stream=new AnimeFreak.Stream("http://127.0.0.1:"+server.getAddress().getPort()+"/test.m3u8","https://megaplay.buzz/","Mozilla/5.0");
   for(boolean include:new boolean[]{true,false}){
    var output=Files.createTempDirectory("zen-kim-mux-").resolve("sample.mp4");
    QualityProbe.await(c->AnimeFreak.INSTANCE.downloadWithSubtitle(stream,output.toFile(),include?sub.toFile():null,s->kotlin.Unit.INSTANCE,c));
    var probe=new ProcessBuilder("ffprobe","-v","error","-select_streams","s","-show_entries","stream=codec_name","-of","csv=p=0",output.toString()).start();
    String codec=new String(probe.getInputStream().readAllBytes()).trim();
    if(probe.waitFor()!=0 || (include?!codec.equals("mov_text"):!codec.isEmpty()))throw new AssertionError("Checkbox output mismatch: "+codec);
   }
   System.out.println("PASS: subtitles checked embeds selectable mov_text; unchecked has no subtitle track");
  }finally {Files.deleteIfExists(sub);server.stop(0);}
  System.exit(0);
 }
}
