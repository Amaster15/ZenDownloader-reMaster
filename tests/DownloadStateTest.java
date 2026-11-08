import java.nio.file.*;
import nobility.downloader.core.entities.Download;
public class DownloadStateTest {
 public static void main(String[] args)throws Exception {
  var file=Files.createTempFile("zen-state-", ".mp4");
  try {
   Files.write(file,new byte[12000]);
   var d=new Download(file.toString(),1,720);
   if(d.isComplete())throw new AssertionError("Unverified existing file marked complete");
   d.setFileSize(12000);
   if(d.isComplete())throw new AssertionError("Size alone cannot prove a playable video");
   nobility.downloader.core.maintenance.VideoValidation.INSTANCE.remember(file.toFile(),true,"Test verified");
   if(!d.isComplete())throw new AssertionError("Verified file missing");
   d.setDownloading(true);
   if(d.isComplete())throw new AssertionError("Active file moved to completed");
   d.setDownloading(false);d.setQueued(true);
   if(d.isComplete())throw new AssertionError("Queued file moved to completed");
   d.setQueued(false);Files.write(file,new byte[6000]);
   if(d.isComplete())throw new AssertionError("Partial file marked complete");
   Files.delete(file);
   if(d.isComplete())throw new AssertionError("Missing file marked complete");
   System.out.println("PASS: completed, active, queued, partial, unverified and missing file states");
  }finally{Files.deleteIfExists(file);}
 }
}
