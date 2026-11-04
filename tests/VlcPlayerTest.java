import java.nio.file.*;
import java.util.concurrent.atomic.AtomicReference;
import kotlin.Unit;
import nobility.downloader.core.BoxHelper;
import nobility.downloader.core.settings.*;
import nobility.downloader.core.scraper.player.PlayVideoWithVLC;
public class VlcPlayerTest {
 public static void main(String[] args)throws Exception {
  BoxHelper.Companion.init();
  var script=Files.createTempFile("zen-vlc-test-", ".sh");
  var output=Files.createTempFile("zen-vlc-args-", ".txt");
  var started=new AtomicReference<Process>();
  try {
   Files.writeString(script,"#!/bin/sh\nprintf '%s\\n' \"$@\" > '"+output+"'\nsleep 3\n");
   script.toFile().setExecutable(true);
   BoxHelper.Companion.update(Defaults.VLC_PATH,script.toString());
   BoxHelper.Companion.update(Defaults.VIDEO_PLAYER,"VLC");
   if(VideoPlayer.Companion.playerForSetting()!=VideoPlayer.VLC)throw new AssertionError("VLC setting missing");
   var result=PlayVideoWithVLC.INSTANCE.play("http://127.0.0.1:1234/private/index.m3u8","Test Agent","https://example.com/",p -> {started.set(p);return Unit.INSTANCE;});
   if(result.isFailed() || started.get()==null)throw new AssertionError(result.getMessage());
   var text=Files.readString(output);
   for(var part:new String[]{"--no-one-instance","--http-user-agent=Test Agent","--http-referrer=https://example.com/","http://127.0.0.1:1234/private/index.m3u8"})if(!text.contains(part))throw new AssertionError(part);
   System.out.println("PASS: VLC selection, custom path, headers, independent process and lifecycle callback");
  } finally {if(started.get()!=null)started.get().destroy();Files.deleteIfExists(script);Files.deleteIfExists(output);}
  System.exit(0);
 }
}
