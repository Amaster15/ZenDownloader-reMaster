import java.net.*;
import java.io.*;
import java.nio.file.*;
import com.sun.net.httpserver.HttpServer;
import javax.net.ssl.SSLHandshakeException;
import java.security.cert.CertificateExpiredException;
import nobility.downloader.core.scraper.video_download.*;

public class MediaSizeProbeTest {
    static HttpURLConnection connection(String url) throws Exception {
        var c=(HttpURLConnection)URI.create(url).toURL().openConnection();
        c.setConnectTimeout(5000);c.setReadTimeout(5000);return c;
    }
    static void rejects(String url, String expected) throws Exception {
        try { MediaSizeProbe.INSTANCE.read(connection(url), true); throw new AssertionError("accepted " + url); }
        catch(IOException e) { if(!e.getMessage().contains(expected)) throw new AssertionError(e); }
    }
    public static void main(String[] args) throws Exception {
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/head", e -> {
            if(!e.getRequestMethod().equals("HEAD")) throw new AssertionError("Expected HEAD");
            e.getResponseHeaders().set("Content-Length","10000000000");
            e.sendResponseHeaders(200,-1);e.close();
        });
        server.createContext("/range", e -> {
            if(!e.getRequestMethod().equals("GET") || !"bytes=0-0".equals(e.getRequestHeaders().getFirst("Range"))) throw new AssertionError("Expected range GET");
            e.getResponseHeaders().set("Content-Range","bytes 0-0/10000000000");
            e.sendResponseHeaders(206,1);e.getResponseBody().write(0);e.close();
        });
        server.createContext("/forbidden", e -> {e.sendResponseHeaders(403,-1);e.close();});
        server.createContext("/no-head", e -> {e.sendResponseHeaders(405,-1);e.close();});
        server.createContext("/unknown", e -> {e.sendResponseHeaders(200,-1);e.close();});
        server.createContext("/html", e -> {
            e.getResponseHeaders().set("Content-Type","text/html; charset=utf-8");
            e.getResponseHeaders().set("Content-Length","10000");
            e.sendResponseHeaders(200,-1);e.close();
        });
        server.start();
        try {
            String root="http://127.0.0.1:"+server.getAddress().getPort();
            if(MediaSizeProbe.INSTANCE.read(connection(root+"/head"),true)!=10000000000L) throw new AssertionError("HEAD size");
            if(MediaSizeProbe.INSTANCE.read(connection(root+"/range"),false)!=10000000000L) throw new AssertionError("Content-Range total");
            rejects(root+"/forbidden","HTTP 403");rejects(root+"/no-head","HTTP 405");
            rejects(root+"/unknown","valid total file size");rejects(root+"/html","HTML page");
            final boolean[] disconnected={false};
            var tls = new HttpURLConnection(URI.create("https://expired.example/video").toURL()) {
                public void connect() {}
                public void disconnect() {disconnected[0]=true;}
                public boolean usingProxy() {return false;}
                public int getResponseCode() throws IOException {
                    var ex=new SSLHandshakeException("validation failed");
                    ex.initCause(new CertificateExpiredException("NotAfter: April 17 2026"));throw ex;
                }
            };
            try {MediaSizeProbe.INSTANCE.read(tls,true);throw new AssertionError("TLS failure hidden");}
            catch(MediaTlsException e) {
                if(!e.getMessage().contains("expired.example") || !e.getMessage().contains("April 17 2026")) throw new AssertionError(e);
            }
            if(!disconnected[0]) throw new AssertionError("Connection leak");
            System.out.println("PASS: HEAD, range total, 64-bit size, HTTP failures, unknown size, HTML rejection, TLS error, cleanup");
        } finally {server.stop(0);}
        if(args.length>0) {
            var lines=Files.readAllLines(Path.of(args[0]));
            var con=connection(lines.get(1));con.setRequestProperty("User-Agent",lines.get(0));
            try {System.out.println("Live size: "+MediaSizeProbe.INSTANCE.read(con,true));}
            catch(MediaTlsException e) {System.out.println("Live diagnosis: "+e.getMessage());}
        }
    }
}
