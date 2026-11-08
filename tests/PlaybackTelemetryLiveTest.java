import java.nio.file.*;
import java.util.concurrent.atomic.AtomicReference;
import kotlin.Unit;
import nobility.downloader.core.scraper.player.PlaybackTelemetry;

/** Local fixture only: checks actual player timestamps without provider access. */
public class PlaybackTelemetryLiveTest {
 public static void main(String[] args)throws Exception {
  var dir=Files.createTempDirectory("zen-telemetry-"); var media=dir.resolve("fixture.mp4");
  var create=new ProcessBuilder("ffmpeg","-v","error","-f","lavfi","-i","testsrc2=size=160x120:rate=10","-t","8","-c:v","mpeg4",media.toString()).inheritIO().start();
  if(create.waitFor()!=0)throw new AssertionError("Fixture");
  var ffLog=dir.resolve("ff.log").toFile();
  var ffBuilder=new ProcessBuilder("ffplay","-autoexit","-stats","-ss","2","-an",media.toString()).redirectErrorStream(true).redirectOutput(ffLog);
  ffBuilder.environment().put("SDL_VIDEODRIVER","dummy");ffBuilder.environment().put("SDL_AUDIODRIVER","dummy");
  var ff=ffBuilder.start();var ffSample=new AtomicReference<Double>(0.0);
  PlaybackTelemetry.INSTANCE.log(ff,ffLog,false,(position,duration)->{if(duration>0)ffSample.set(position);return Unit.INSTANCE;});
  var mpvLog=dir.resolve("mpv.log").toFile();
  var mpv=new ProcessBuilder("mpv","--vo=null","--ao=null","--start=2","--terminal=yes","--term-status-msg=ZEN_PROGRESS:${=time-pos}:${=duration}",media.toString()).redirectErrorStream(true).redirectOutput(mpvLog).start();
  var mpvSample=new AtomicReference<Double>(0.0);
  PlaybackTelemetry.INSTANCE.log(mpv,mpvLog,true,(position,duration)->{if(duration>0)mpvSample.set(position);return Unit.INSTANCE;});
  int port=PlaybackTelemetry.INSTANCE.port();
  var vlc=new ProcessBuilder("vlc","--intf=dummy","--vout=dummy","--aout=dummy","--play-and-exit","--start-time=2","--extraintf=rc","--rc-host=127.0.0.1:"+port,media.toString()).redirectErrorStream(true).redirectOutput(dir.resolve("vlc.log").toFile()).start();
  var vlcSample=new AtomicReference<Double>(0.0);
  PlaybackTelemetry.INSTANCE.vlc(vlc,port,(position,duration)->{if(duration>0)vlcSample.set(position);return Unit.INSTANCE;});
  for(var process:new Process[]{ff,mpv,vlc})if(!process.waitFor(15,java.util.concurrent.TimeUnit.SECONDS))process.destroyForcibly();
  Thread.sleep(1500);
  System.out.println("Actual positions FFplay="+ffSample+" MPV="+mpvSample+" VLC="+vlcSample);
  if(ffSample.get()<2||mpvSample.get()<2||vlcSample.get()<2)throw new AssertionError("Player did not report resume timestamps; logs in "+dir);
  System.out.println("PASS: real FFplay, MPV, VLC playback timestamps and seeked playback");
  System.exit(0);
 }
}
