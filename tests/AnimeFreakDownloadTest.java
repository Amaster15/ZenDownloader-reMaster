import nobility.downloader.core.scraper.AnimeFreak;
import com.sun.net.httpserver.HttpServer;
import java.nio.file.*;
import java.net.*;
public class AnimeFreakDownloadTest {
 public static void main(String[] args)throws Exception {
  Path root=Path.of(args[0]);var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
  server.createContext("/",e->{try{var file=root.resolve(e.getRequestURI().getPath().substring(1));byte[]bytes=Files.readAllBytes(file);e.sendResponseHeaders(200,bytes.length);e.getResponseBody().write(bytes);}finally{e.close();}});
  server.setExecutor(java.util.concurrent.Executors.newCachedThreadPool());
  server.start();
  Path output=root.resolve("downloaded.mp4");Files.deleteIfExists(output);
  var stream=new AnimeFreak.Stream("http://127.0.0.1:"+server.getAddress().getPort()+"/test.m3u8","https://play2.echovideo.ru/","Mozilla/5.0");
  try {
   QualityProbe.await(c->AnimeFreak.INSTANCE.download(stream,output.toFile(),msg->kotlin.Unit.INSTANCE,c));
   if(Files.size(output)<10000)throw new AssertionError("No media output");
   long size=Files.size(output);
   try{QualityProbe.await(c->AnimeFreak.INSTANCE.download(stream,output.toFile(),msg->kotlin.Unit.INSTANCE,c));throw new AssertionError("Overwrote existing file");}catch(java.util.concurrent.ExecutionException expected){}
   if(Files.size(output)!=size)throw new AssertionError("Existing file changed");
   try(var files=Files.list(root)){if(files.anyMatch(p->p.getFileName().toString().startsWith(".animefreak-")))throw new AssertionError("Temporary files leaked");}
   var job=kotlinx.coroutines.JobKt.Job((kotlinx.coroutines.Job)null);
   var cancelled=new java.util.concurrent.CompletableFuture<Object>();
   server.createContext("/slow.m3u8",e->{try{Thread.sleep(5000);}catch(InterruptedException ignored){}finally{e.close();}});
   var slow=new AnimeFreak.Stream("http://127.0.0.1:"+server.getAddress().getPort()+"/slow.m3u8",stream.getReferer(),stream.getUserAgent());
   AnimeFreak.INSTANCE.download(slow,root.resolve("cancelled.mp4").toFile(),msg->kotlin.Unit.INSTANCE,new kotlin.coroutines.Continuation<java.nio.file.Path>(){
    public kotlin.coroutines.CoroutineContext getContext(){return job;}
    public void resumeWith(Object value){cancelled.complete(value);}
   });
   Thread.sleep(700);job.cancel(new java.util.concurrent.CancellationException("test"));
   Object cancellation=cancelled.get(10,java.util.concurrent.TimeUnit.SECONDS);
   try{kotlin.ResultKt.throwOnFailure(cancellation);throw new AssertionError("Cancellation lost");}catch(java.util.concurrent.CancellationException expected){}
   if(Files.exists(root.resolve("cancelled.mp4")))throw new AssertionError("Published partial file");
   try(var files=Files.list(root)){if(files.anyMatch(p->p.getFileName().toString().startsWith(".animefreak-")))throw new AssertionError("Cancellation leaked temporary files");}
   System.out.println("PASS: HLS download and MP4 mux, existing-file protection, temporary-file cleanup and cancellation");
  }finally{server.stop(0);}
  System.exit(0);
 }
}
