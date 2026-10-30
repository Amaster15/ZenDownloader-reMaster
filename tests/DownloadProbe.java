import java.nio.file.*;
import java.util.*;
import kotlin.Unit;
import nobility.downloader.core.BoxHelper;
import nobility.downloader.core.Core;
import nobility.downloader.core.driver.BrowserDriverSetup;
import nobility.downloader.core.entities.Episode;
import nobility.downloader.core.scraper.data.DownloadQueue;
import nobility.downloader.core.scraper.video_download.*;
import nobility.downloader.core.settings.*;
public class DownloadProbe {
 public static void main(String[] args) throws Exception {
  BoxHelper.Companion.init();Core.Companion.setErrorPrintStream(System.err);
  BrowserDriverSetup.INSTANCE.setup("/usr/bin/chromium");
  var lines=Files.readAllLines(Path.of(args[0]));String link=lines.get(3);String agent=lines.get(0);
  long size=QualityProbe.<Long>await(c -> Functions.INSTANCE.fileSize(link,agent,false,c));
  System.out.println("Expected bytes: "+size);
  Path output=Path.of(args[1]);Files.createDirectories(output.toAbsolutePath().getParent());
  var episode=new Episode("Episode 1 English Dubbed",args.length > 2 ? args[2] : "yu-gi-oh-sevens-episode-1-english-dubbed","anime/yu-gi-oh-sevens");
  var data=new VideoDownloadData(new DownloadQueue(episode,null,Quality.HIGH),Quality.HIGH,"Download test",agent);
  try {
   data.createDownload(episode.getSlug(),Quality.HIGH,output.toFile());
   data.getCurrentDownload().setFileSize(size);
   Core.Companion.getChild().softStart();
   long started=System.nanoTime();
   QualityProbe.<Unit>await(c -> Functions.INSTANCE.downloadVideo(link,output.toFile(),data,data.getCurrentDownload(),c));
   long actual=Files.size(output);
   if(actual!=size) throw new AssertionError("Expected "+size+" bytes, got "+actual);
   System.out.println("PASS: complete download via app Functions.downloadVideo, "+actual+" bytes in "+(System.nanoTime()-started)/1_000_000_000+" seconds");
  } finally {Core.Companion.getChild().stop();data.getBase().killDriver();}
  System.exit(0);
 }
}
