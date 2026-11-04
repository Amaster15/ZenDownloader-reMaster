import java.nio.file.*;
import java.util.jar.*;
public class ReleaseUpdateTest {
 public static void main(String[] args) throws Exception {
  Path dir=Files.createTempDirectory("zen-update-test-");
  Manifest manifest=new Manifest(); manifest.getMainAttributes().putValue("Manifest-Version","1.0");
  manifest.getMainAttributes().putValue("Main-Class","TestMain");
  Path ready=dir.resolve("update.ready");
  try(JarOutputStream out=new JarOutputStream(Files.newOutputStream(ready),manifest)) {
   out.putNextEntry(new JarEntry("payload"));out.write(new byte[]{1,2,3});out.closeEntry();
  }
  ReleaseDownload.INSTANCE.validate(ready.toFile());
  Path old=dir.resolve("ZenDownloader-1.3.3.jar");Files.writeString(old,"old application");
  ReleaseDownload.INSTANCE.install(ready.toFile(),"ZenDownloader-1.3.4.jar");
  if(!Files.readString(old).equals("old application") || Files.exists(ready)) throw new AssertionError("Old version modified");
  Path bad=dir.resolve("bad.ready");Files.writeString(bad,"<html>server error</html>");
  try {ReleaseDownload.INSTANCE.validate(bad.toFile());throw new AssertionError("Invalid release accepted");}catch(Exception expected){if(!(expected instanceof java.util.zip.ZipException))throw expected;}
  try {ReleaseDownload.INSTANCE.install(bad.toFile(),"../escape.jar");throw new AssertionError("Path traversal accepted");}catch(IllegalArgumentException expected){}
  try {ReleaseDownload.INSTANCE.install(bad.toFile(),"ZenDownloader-1.3.3.jar");throw new AssertionError("Existing version overwritten");}catch(IllegalArgumentException expected){}
  if(!Updater.INSTANCE.isLatest("v" + AppInfo.VERSION) || Updater.INSTANCE.isLatest("v99.0.0")) throw new AssertionError("Version comparison");
  System.out.println("PASS: staged JAR validation, invalid download rejection, safe names, old-version preservation, version comparison");
 }
}
