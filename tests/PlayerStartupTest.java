import java.nio.file.*;
import kotlin.Unit;
import nobility.downloader.core.scraper.player.PlayVideoWithFfplay;
import nobility.downloader.core.scraper.video_download.m3u8_downloader.util.VideoUtil;
public class PlayerStartupTest {
 public static void main(String[] args) throws Exception {
  if(!VideoUtil.FfmpegPathHolder.INSTANCE.getFfplayPath().equals("ffplay")) throw new AssertionError("Linux must prefer available system FFplay");
  var script=Files.createTempFile("zen-failing-player-", ".sh");
  try {
   Files.writeString(script,"#!/bin/sh\necho 'Could not initialize SDL - dsp: No such audio device' >&2\nexit 1\n");
   script.toFile().setExecutable(true);
   var result=PlayVideoWithFfplay.INSTANCE.play("https://example.invalid/video.mp4","test","test",script.toString(),"",p -> Unit.INSTANCE);
   if(!result.isFailed() || !result.getMessage().contains("No such audio device")) throw new AssertionError("Startup error missing: "+result.getMessage());
  } finally {Files.deleteIfExists(script);}
  System.out.println("PASS: Linux system FFplay preference and actual startup error surfaced");
 }
}
