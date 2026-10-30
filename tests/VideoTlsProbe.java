import java.net.*;
import java.nio.file.*;
import java.io.*;
import javax.net.ssl.*;
import nobility.downloader.core.scraper.video_download.*;
public class VideoTlsProbe {
 public static void main(String[] args) throws Exception {
  var lines=Files.readAllLines(Path.of(args[0]));
  for(int i=1;i<lines.size();i++) {
   var c=(HttpsURLConnection)URI.create(lines.get(i)).toURL().openConnection();
   c.setInstanceFollowRedirects(true);
   c.setConnectTimeout(20000);c.setReadTimeout(20000);
   c.setRequestProperty("User-Agent",lines.get(0));c.setRequestProperty("Accept-Encoding","identity");
   c.setRequestProperty("Range","bytes=0-1023");
   VideoCertificateException.INSTANCE.configure(c);
   try {
    System.out.println("quality="+i+" status="+c.getResponseCode()+" host="+c.getURL().getHost()+" type="+c.getContentType()+" length="+c.getContentLengthLong()+" range="+c.getHeaderField("Content-Range"));
    String redirect=c.getHeaderField("Location");
    if(redirect!=null) {
     System.out.println("Redirect host="+URI.create(redirect).getHost());
     Files.writeString(Path.of(args[1]+"-"+i+".url"),redirect);
    }
    try(var in=c.getInputStream()) {
     byte[] prefix=in.readNBytes(1024);
     Files.write(Path.of(args[1]+"-"+i+".bin"),prefix);
     System.out.println("prefix="+java.util.HexFormat.of().formatHex(java.util.Arrays.copyOf(prefix,Math.min(24,prefix.length))));
    }
   } catch(Exception e) {System.out.println("Failed host="+c.getURL().getHost());for(Throwable t=e;t!=null;t=t.getCause()) System.out.println(t.getClass().getSimpleName()+": "+t.getMessage());}
   finally {c.disconnect();}
  }
 }
}
