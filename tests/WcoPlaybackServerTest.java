import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.net.http.*;
import java.util.concurrent.atomic.AtomicReference;
import nobility.downloader.core.scraper.player.WcoPlaybackServer;
import kotlin.Unit;
public class WcoPlaybackServerTest {
 public static void main(String[] args)throws Exception {
  var range=new AtomicReference<String>(); var method=new AtomicReference<String>();
  var origin=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
  origin.createContext("/",e -> {method.set(e.getRequestMethod());range.set(e.getRequestHeaders().getFirst("Range"));e.getResponseHeaders().set("Content-Type","video/mp4");e.getResponseHeaders().set("Content-Range","bytes 2-4/10");e.sendResponseHeaders(206,3);e.getResponseBody().write(new byte[]{2,3,4});e.close();});origin.start();
  var client=HttpClient.newHttpClient();
  try(var bridge=new WcoPlaybackServer(c -> {try{return (HttpURLConnection)new URI("http://127.0.0.1:"+origin.getAddress().getPort()+"/").toURL().openConnection();}catch(Exception ex){throw new RuntimeException(ex);}},m -> Unit.INSTANCE)) {
   var uri=URI.create(bridge.getUrl());
   var response=client.send(HttpRequest.newBuilder(uri).header("Range","bytes=2-4").GET().build(),HttpResponse.BodyHandlers.ofByteArray());
   if(response.statusCode()!=206 || response.body().length!=3 || !"bytes=2-4".equals(range.get()))throw new AssertionError("Seek failed");
   var head=client.send(HttpRequest.newBuilder(uri).method("HEAD",HttpRequest.BodyPublishers.noBody()).build(),HttpResponse.BodyHandlers.discarding());
   if(head.statusCode()!=200 || !head.headers().firstValue("Content-Length").orElse("").equals("10") || !method.get().equals("GET"))throw new AssertionError("HEAD gateway compatibility");
   var missing=client.send(HttpRequest.newBuilder(uri.resolve("/wrong")).GET().build(),HttpResponse.BodyHandlers.discarding());if(missing.statusCode()!=404)throw new AssertionError();
  } finally {origin.stop(0);}
  System.out.println("PASS: WCO range seeking, streamed payload, HEAD translated to GET, private path");
 }
}
