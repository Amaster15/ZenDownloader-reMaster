import java.nio.file.*;
import nobility.downloader.core.BoxHelper;
import nobility.downloader.core.scraper.video_download.Functions;
public class FinalSizeProbe {
 public static void main(String[] args) throws Exception {
  BoxHelper.Companion.init();var lines=Files.readAllLines(Path.of(args[0]));
  long size=QualityProbe.<Long>await(c -> Functions.INSTANCE.fileSize(lines.get(3),lines.get(0),true,c));
  if(size!=Files.size(Path.of(args[1]))) throw new AssertionError("size mismatch");
  System.out.println("PASS: normal app size-check call matches completed download: "+size);
  System.exit(0);
 }
}
