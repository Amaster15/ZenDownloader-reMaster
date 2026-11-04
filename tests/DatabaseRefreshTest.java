import nobility.downloader.core.scraper.DatabaseUpdater;
import nobility.downloader.utils.ThumbnailCache;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.file.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
public class DatabaseRefreshTest {
 public static void main(String[] args) throws Exception {
  String base = "https://www.wcoflix.tv/anime/new-show";
  if (!nobility.downloader.utils.SeriesArtwork.imageUrl("<img class='img5' src='//images.example/cover.jpg'>", base).equals("https://images.example/cover.jpg")) throw new AssertionError("Protocol-relative artwork");
  if (!nobility.downloader.utils.SeriesArtwork.imageUrl("<meta property='og:image' content='/cover.jpg'>", base).equals("https://www.wcoflix.tv/cover.jpg")) throw new AssertionError("Metadata artwork fallback");
  if (!nobility.downloader.utils.SeriesArtwork.imageUrl("<img class='img5' data-src='https://images.example/lazy.jpg'>", base).equals("https://images.example/lazy.jpg")) throw new AssertionError("Lazy artwork");
  if (!nobility.downloader.utils.SeriesArtwork.imageUrl("<h1>Blocked</h1>", base).isEmpty()) throw new AssertionError("Missing artwork");
  var titles=DatabaseUpdater.parseIndex("<div class='ddmcc'><ul><li><a href='/anime/new-show/'>New Show</a></li><li><a href='/anime/new-show'>Duplicate</a></li><li><a href='https://ads.example/anime/x'>Ad</a></li></ul></div>","https://www.wcoflix.tv/dubbed-anime-list");
  if(titles.size()!=1 || !titles.get(0).getSlug().equals("anime/new-show")) throw new AssertionError("Catalog normalization/host filtering");
  if(!DatabaseUpdater.parseIndex("<h1>Verify you are human</h1>","https://www.wcoflix.tv/dubbed-anime-list").isEmpty()) throw new AssertionError("Challenge must not be imported");
  Path dir=Files.createTempDirectory("zen-thumbnails-");
  var target=dir.resolve("test.png").toFile();Files.writeString(target.toPath(),"<html>This was incorrectly cached as a picture</html>");
  if(ThumbnailCache.valid(target))throw new AssertionError("HTML accepted as image");
  var bytes=new java.io.ByteArrayOutputStream();ImageIO.write(new BufferedImage(4,4,BufferedImage.TYPE_INT_RGB),"png",bytes);
  var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
  server.createContext("/image",exchange->{var data=bytes.toByteArray();exchange.sendResponseHeaders(200,data.length);exchange.getResponseBody().write(data);exchange.close();});server.start();
  try {
   Boolean result=QualityProbe.await(c->ThumbnailCache.INSTANCE.ensure(target,"http://127.0.0.1:"+server.getAddress().getPort()+"/image",c));
   if(!result || !ThumbnailCache.valid(target) || target.length()!=bytes.size())throw new AssertionError("Corrupt image replacement failed");
  }finally{server.stop(0);}
  Boolean cached=QualityProbe.await(c->ThumbnailCache.INSTANCE.ensure(target,"http://127.0.0.1:1/unreachable",c));
  if(!cached)throw new AssertionError("Cached image should work offline");
  if(args.length>0){
   var live=dir.resolve("One Piece.jpg").toFile();
   Boolean result=QualityProbe.await(c->ThumbnailCache.INSTANCE.ensure(live,null,c));
   if(!result || !ThumbnailCache.valid(live))throw new AssertionError("Live archive fallback failed");
   System.out.println("Live archived thumbnail downloaded and decoded: "+live.length()+" bytes");
  }
  System.out.println("PASS: catalog parsing, challenge rejection, thumbnail repair and offline cache");System.exit(0);
 }
}
