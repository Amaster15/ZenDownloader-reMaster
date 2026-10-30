import javax.net.ssl.*;
import java.net.URI;
import nobility.downloader.core.scraper.video_download.VideoCertificateException;
public class VideoTlsScopeTest {
 public static void main(String[] args) throws Exception {
  var globalFactory=HttpsURLConnection.getDefaultSSLSocketFactory();
  var globalVerifier=HttpsURLConnection.getDefaultHostnameVerifier();
  boolean enabled="1".equals(System.getenv("ZEN_ALLOW_EXPIRED_VIDEO_CERTIFICATE"));
  for(String host:new String[]{"neptun.cizgifilmlerizle.com","u14.cizgifilmlerizle.com","u26.cizgifilmlerizle.com","u27.cizgifilmlerizle.com","u15.cizgifilmlerizle.com","u10.cizgifilmlerizle.com","u30.cizgifilmlerizle.com","u12.cizgifilmlerizle.com","u.cizgifilmlerizle.com","u12.cizgifilmlerizle.com.example.com","other.cizgifilmlerizle.com","example.com","neptun.cizgifilmlerizle.com.example.com"}) {
   var c=(HttpsURLConnection)URI.create("https://"+host+"/").toURL().openConnection();
   var before=c.getSSLSocketFactory();var verifier=c.getHostnameVerifier();
   VideoCertificateException.INSTANCE.configure(c);
   boolean allowed=host.equals("neptun.cizgifilmlerizle.com")||host.matches("u[0-9]+\\.cizgifilmlerizle\\.com");
   if((c.getSSLSocketFactory()!=before)!=(enabled&&allowed)) throw new AssertionError(host);
   if(c.getHostnameVerifier()!=verifier) throw new AssertionError("Hostname verification changed");
  }
  if(globalFactory!=HttpsURLConnection.getDefaultSSLSocketFactory()||globalVerifier!=HttpsURLConnection.getDefaultHostnameVerifier()) throw new AssertionError("Global TLS modified");
  System.out.println("PASS: opt-in="+enabled+", exact-host scope, hostname verifier preserved, global TLS unchanged");
 }
}
