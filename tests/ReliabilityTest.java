import java.nio.file.*;
import java.util.*;
import nobility.downloader.core.BoxHelper;
import nobility.downloader.core.Core;
import nobility.downloader.core.entities.Download;
import nobility.downloader.core.maintenance.*;
import nobility.downloader.core.scraper.player.PlaybackTelemetry;
import nobility.downloader.core.settings.Defaults;

public class ReliabilityTest {
 public static void main(String[] args)throws Exception {
  BoxHelper.Companion.init();
  var root=Files.createTempDirectory("zen-repair-test-");
  BoxHelper.Companion.update(Defaults.SAVE_FOLDER,root.toString());
  BoxHelper.Companion.update(Defaults.SEPARATE_SEASONS,true);
  var source=root.resolve("Test Series - S01E02.mp4"); Files.write(source,new byte[12000]);
  var sub=root.resolve("Test Series - S01E02.srt"); Files.writeString(sub,"test captions");
  var record=new Download(source.toString(),1,720);record.setName("Test Series - S01E02");record.setSlug("https://example.invalid/episode/2|dub");record.setSeriesSlug("Test Series");record.setFileSize(12000);
  VideoValidation.INSTANCE.remember(source.toFile(),true,"Fixture");
  Core.Companion.getChild().addDownload(record);
  var plan=DownloadRepair.INSTANCE.preview();
  if(plan.size()!=1)throw new AssertionError("Expected preview");
  if(!Files.exists(source))throw new AssertionError("Preview mutated files");
  var target=plan.get(0).getTarget().toPath();Files.createDirectories(target.getParent());Files.writeString(target,"do not overwrite");
  String result=DownloadRepair.INSTANCE.apply(plan);
  if(!Files.exists(source)||!Files.readString(target).equals("do not overwrite"))throw new AssertionError("Conflict overwrote files");
  Files.delete(target); result=DownloadRepair.INSTANCE.apply(plan);
  if(Files.exists(source)||!Files.exists(target)||!record.getDownloadPath().equals(target.toString()))throw new AssertionError(result);
  if(!Files.exists(target.resolveSibling(sub.getFileName())))throw new AssertionError("Subtitle not moved");
  if(!BoxHelper.Companion.getShared().getDownloadBox().get(record.getId()).getDownloadPath().equals(target.toString()))throw new AssertionError("DB path");
  WatchProgress.update("episode",120,1000);if(WatchProgress.get("episode").getWatched())throw new AssertionError("Early watched");
  WatchProgress.update("episode",500,1000); if(WatchProgress.get("episode").getSeconds()!=500)throw new AssertionError("Resume");
  WatchProgress.update("episode",960,1000); if(!WatchProgress.get("episode").getWatched())throw new AssertionError("Completion");
  WatchProgress.INSTANCE.reset("episode");if(WatchProgress.get("episode").getSeconds()!=0)throw new AssertionError("Reset");
  var ff=PlaybackTelemetry.parse("Duration: 00:24:00.00\r  120.35 A-V: 0.00",false,0);
  if(ff==null||ff.getPosition()!=120.35||ff.getDuration()!=1440)throw new AssertionError("FFplay telemetry");
  var mpv=PlaybackTelemetry.parse("ZEN_PROGRESS:125.5:1440.0",true,0);
  if(mpv==null||mpv.getPosition()!=125.5)throw new AssertionError("MPV telemetry");
  System.out.println("PASS: repair preview, conflict preservation, subtitle move, DB paths, resume/watched/reset, FFplay/MPV timestamps");
  System.exit(0);
 }
}
