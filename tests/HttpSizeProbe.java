import java.nio.file.*;
import javax.net.ssl.HttpsURLConnection;
import java.net.URI;
public class HttpSizeProbe {
 public static void main(String[] args) throws Exception {
  var lines=Files.readAllLines(Path.of(args[0]));
  for(var method : new String[]{"HEAD", "GET"}) {
   var con=(HttpsURLConnection)URI.create(lines.get(1)).toURL().openConnection();
   con.setConnectTimeout(15000);con.setReadTimeout(15000);con.setRequestMethod(method);
   con.setRequestProperty("User-Agent",lines.get(0));con.setRequestProperty("Accept-Encoding","identity");
   if(method.equals("GET")) con.setRequestProperty("Range","bytes=0-1023");
   try {System.out.println(method+" status="+con.getResponseCode()+" size="+con.getContentLengthLong()+" type="+con.getContentType());}
   catch(Exception e){for(Throwable cause=e;cause!=null;cause=cause.getCause())System.out.println(cause.getClass().getSimpleName()+": "+cause.getMessage());}
   finally {con.disconnect();}
  }
 }
}
