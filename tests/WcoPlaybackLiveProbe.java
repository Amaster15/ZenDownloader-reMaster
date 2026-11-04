import java.nio.file.*;
import nobility.downloader.core.*;
import nobility.downloader.core.scraper.player.WcoPlaybackServer;
import nobility.downloader.core.scraper.video_download.Functions;
import kotlin.Unit;
public class WcoPlaybackLiveProbe {
 public static void main(String[] args)throws Exception {
  BoxHelper.Companion.init();Core.Companion.setErrorPrintStream(System.err);
  var lines=Files.readAllLines(Path.of(args[0]));if(lines.size()<2)throw new AssertionError("No WCO link available");
  try(var bridge=new WcoPlaybackServer(c -> Functions.INSTANCE.wcoConnection(lines.get(1),lines.get(0),false,false,c),m -> {System.out.println(m);return Unit.INSTANCE;})) {
   var p=new ProcessBuilder("ffmpeg","-nostdin","-v","error","-i",bridge.getUrl(),"-t","2","-f","null","-").redirectErrorStream(true).redirectOutput(new java.io.File("/tmp/zen-wco-playback-decode.log")).start();
   if(!p.waitFor(90,java.util.concurrent.TimeUnit.SECONDS)){p.destroyForcibly();throw new AssertionError("Decode timeout");}
   if(p.exitValue()!=0)throw new AssertionError("Decode failed: "+Files.readString(Path.of("/tmp/zen-wco-playback-decode.log")));
   System.out.println("PASS: live WCO video/audio decode through downloader-backed playback connection");
  }
  System.exit(0);
 }
}
