import java.nio.file.*;
import nobility.downloader.core.maintenance.VideoValidation;
public class VideoValidationTest {
 public static void main(String[] args)throws Exception {
  var dir=Files.createTempDirectory("zen-validation-");var video=dir.resolve("good.mp4");
  Process p=new ProcessBuilder("ffmpeg","-v","error","-f","lavfi","-i","testsrc2=size=320x240:rate=15","-t","2","-c:v","mpeg4",video.toString()).inheritIO().start();
  if(p.waitFor()!=0)throw new AssertionError("Fixture");
  if(VideoValidation.inspect(video.toFile(),"ffmpeg")!=null)throw new AssertionError("Playable file rejected");
  var fake=dir.resolve("fake.mp4");Files.write(fake,new byte[15000]);
  if(VideoValidation.inspect(fake.toFile(),"ffmpeg")==null)throw new AssertionError("Fake file accepted");
  var truncated=dir.resolve("truncated.mp4");byte[] bytes=Files.readAllBytes(video);Files.write(truncated,java.util.Arrays.copyOf(bytes,bytes.length/2));
  if(VideoValidation.inspect(truncated.toFile(),"ffmpeg")==null)throw new AssertionError("Truncated file accepted");
  if(!Files.exists(fake)||!Files.exists(truncated))throw new AssertionError("Failed validation must preserve files");
  System.out.println("PASS: full video decode accepts valid MP4; rejects fake/truncated media and preserves originals");
 }
}
