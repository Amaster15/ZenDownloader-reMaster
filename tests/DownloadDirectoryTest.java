import java.io.File;
import nobility.downloader.utils.EpisodeNaming;
public class DownloadDirectoryTest {
 public static void main(String[] args) {
  File root=new File("/tmp/download-root");
  for(String title:new String[]{"Season 1 Episode 9 English Dubbed","Gachiakuta - S01E09 English Dubbed - DUB.mp4"}) {
   File result=EpisodeNaming.downloadDirectory(root,"Gachiakuta",title,"episode-9",true,false);
   if(!result.equals(new File(root,"Gachiakuta/Season 01")))throw new AssertionError(result);
  }
  if(!EpisodeNaming.downloadDirectory(root,"Example","S02E03","",true,false).equals(new File(root,"Example/Season 02")))throw new AssertionError("Season 2");
  if(!EpisodeNaming.downloadDirectory(root,"Example","S02E03","",false,false).equals(new File(root,"Example")))throw new AssertionError("Seasons disabled");
  if(!EpisodeNaming.downloadDirectory(root,"Example","Movie","",true,true).equals(new File(root,"Example")))throw new AssertionError("Movie");
  System.out.println("PASS: provider filenames and WCO titles share series/season folders; settings and movies respected");
 }
}
