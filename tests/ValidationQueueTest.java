import java.nio.file.*;
import nobility.downloader.core.entities.Download;
import nobility.downloader.core.maintenance.*;
public class ValidationQueueTest {
 public static void main(String[] args)throws Exception {
  var dir=Files.createTempDirectory("zen-validation-queue-");var video=dir.resolve("video.mp4");
  var p=new ProcessBuilder("ffmpeg","-v","error","-f","lavfi","-i","testsrc2=size=320x240:rate=15","-t","1","-c:v","mpeg4",video.toString()).inheritIO().start();
  if(p.waitFor()!=0)throw new AssertionError("Fixture");
  Download good=download(video,"one");
  if(good.isComplete())throw new AssertionError("Before validation");good.updateProgress();
  waitFor(good);
  if(!good.isComplete()||!good.getDownloadProgress().getValue().contains("verified"))throw new AssertionError("Success: "+good.getDownloadProgress().getValue());
  var bad=dir.resolve("bad.mp4");Files.write(bad,new byte[12000]);var broken=download(bad,"two");broken.updateProgress();waitFor(broken);
  if(broken.isComplete()||!broken.getDownloadProgress().getValue().startsWith("Failed validation"))throw new AssertionError("Failure status");
  if(FailedDownloads.INSTANCE.getRequests().stream().noneMatch(r->r.getUrl().equals("two")))throw new AssertionError("Invalid file not offered for retry");
  var cancelled=download(video,"cancelled");cancelled.setProgress("Cancelled");cancelled.updateProgress();
  if(!cancelled.getDownloadProgress().getValue().equals("Cancelled"))throw new AssertionError("Cancellation overwritten");
  System.out.println("PASS: asynchronous validation gates completion, invalid files enter retries, cancellation status survives cleanup");
  System.exit(0);
 }
 static Download download(Path file,String slug)throws Exception {var d=new Download(file.toString(),1,720);d.setFileSize(Files.size(file));d.setName("Episode 1");d.setSlug(slug);d.setSeriesSlug("anime/test");return d;}
 static void waitFor(Download d)throws Exception {for(int i=0;i<100 && d.getDownloadProgress().getValue().startsWith("Validating");i++)Thread.sleep(100);}
}
