import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.nio.charset.StandardCharsets;
import org.openqa.selenium.chrome.ChromeOptions;
import nobility.downloader.core.BoxHelper;
import nobility.downloader.core.driver.BrowserDriverSetup;
import nobility.downloader.core.driver.undetected_chrome.ChromeDriverBuilder;
import nobility.downloader.core.scraper.video_download.PlayerFrameReader;
public class PlayerFrameReaderTest {
 public static void main(String[] args) throws Exception {
  var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
  server.createContext("/",e->{
   String body=switch(e.getRequestURI().getPath()) {
    case "/busy-episode" -> "<iframe src='/busy-player'></iframe><iframe src='/irrelevant'></iframe>";
    case "/busy-player" -> "<title>Servers Busy</title>";
    case "/episode" -> "<iframe src='/irrelevant'></iframe><iframe src='/gate'></iframe>";
    case "/gate" -> "<button id='close-btn' disabled>Close</button><script>const b=document.getElementById('close-btn');setTimeout(()=>b.disabled=false,800);b.onclick=()=>setTimeout(()=>location.href='/player?ticket=from-button',300);</script>";
    case "/player" -> "ticket=from-button".equals(e.getRequestURI().getQuery()) ? "<title>Player</title><video></video><button class='vjs-big-play-button' style='display:none'>Play</button><script>setTimeout(()=>{document.querySelector('button').style.display='block';document.body.dataset.ready='yes'},4000)</script><p>READY-WITH-TICKET</p>" : "<title>Servers Busy</title>";
    default -> "<title>Other frame</title>";
   };
   byte[] bytes=body.getBytes(StandardCharsets.UTF_8);e.getResponseHeaders().set("Content-Type","text/html");e.sendResponseHeaders(200,bytes.length);e.getResponseBody().write(bytes);e.close();
  });server.start();
  BoxHelper.Companion.init();String path=BrowserDriverSetup.INSTANCE.setup("/usr/bin/chromium");
  var builder=new ChromeDriverBuilder();var driver=builder.build(new ChromeOptions(),path,"/usr/bin/chromium",true);
  try {
   String root="http://127.0.0.1:"+server.getAddress().getPort();
   var statuses=new java.util.ArrayList<String>();
   long started=System.nanoTime();
   String source=PlayerFrameReader.INSTANCE.read(driver,root+"/episode","/gate",20,message -> {statuses.add(message);return kotlin.Unit.INSTANCE;});
   if(statuses.stream().noneMatch(x -> x.contains("popup detected")) || statuses.stream().noneMatch(x -> x.contains("seconds elapsed")) || statuses.stream().noneMatch(x -> x.contains("Clicking Close")) || statuses.stream().noneMatch(x -> x.contains("popup closed"))) throw new AssertionError(statuses);
   if((System.nanoTime()-started)/1_000_000 < 4000) throw new AssertionError("Missing extra waits");
   if(statuses.stream().noneMatch(x -> x.contains("2 extra seconds")) || statuses.stream().noneMatch(x -> x.contains("2 seconds for the player"))) throw new AssertionError(statuses);
   if(!source.contains("data-ready=\"yes\"") || statuses.stream().noneMatch(x -> x.contains("Play button is visible"))) throw new AssertionError("Returned before visible Play button: "+statuses);
   String busy=PlayerFrameReader.INSTANCE.read(driver,root+"/busy-episode","/busy-player",20);
   if(!busy.contains("Servers Busy")) throw new AssertionError("Busy page was not preserved");
   System.out.println(statuses);
   if(!source.contains("READY-WITH-TICKET")) throw new AssertionError(source);
   if(driver.findElements(org.openqa.selenium.By.tagName("iframe")).size()!=2) throw new AssertionError("Did not return to episode context");
   System.out.println("PASS: correct iframe, waits for enabled Close, preserves button-generated URL, waits for navigation, restores frame context");
  } finally {driver.quit();builder.close();server.stop(0);}
  System.exit(0);
 }
}
