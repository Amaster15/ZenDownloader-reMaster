import nobility.downloader.core.scraper.video_download.HiAnimeFallback;
public class HiAnimeAudioTest {
 public static void main(String[] args)throws Exception {
  String base="https://media.example/master.m3u8", video="https://media.example/video.m3u8";
  String master="#EXTM3U\n#EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID=\"aac\",LANGUAGE=\"ja\",DEFAULT=YES,URI=\"ja.m3u8\"\n#EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID=\"aac\",LANGUAGE=\"en\",DEFAULT=NO,URI=\"en.m3u8\"\n#EXT-X-STREAM-INF:RESOLUTION=1280x720,AUDIO=\"aac\"\nvideo.m3u8\n";
  if(!"https://media.example/en.m3u8".equals(HiAnimeFallback.audioPlaylist(master,base,video,"dub")))throw new AssertionError("Dub selected default Japanese");
  if(!"https://media.example/ja.m3u8".equals(HiAnimeFallback.audioPlaylist(master,base,video,"sub")))throw new AssertionError("Sub wrong language");
  if(HiAnimeFallback.audioPlaylist(master.replace(",AUDIO=\"aac\"",""),base,video,"dub")!=null)throw new AssertionError("Muxed stream changed");
  try {HiAnimeFallback.audioPlaylist(master.replace("LANGUAGE=\"en\"","LANGUAGE=\"es\""),base,video,"dub");throw new AssertionError("Missing English accepted");}catch(IllegalStateException expected){}
  if(args.length>0 && !HiAnimeFallback.audioPlaylist(java.nio.file.Files.readString(java.nio.file.Path.of(args[0])),base,"https://media.example/index-f2.m3u8","dub").endsWith("audio/en/index.m3u8"))throw new AssertionError("Live fixture");
  System.out.println("PASS: English dub, Japanese sub, muxed audio, missing language and live master");
 }
}
