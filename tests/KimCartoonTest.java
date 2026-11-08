import nobility.downloader.core.scraper.*;
import nobility.downloader.core.settings.Quality;
import java.nio.file.*;
import java.util.*;
public class KimCartoonTest {
 public static void main(String[] args)throws Exception {
  var kim=KimCartoon.INSTANCE;
  for(String invalid:List.of("http://kimcartoon.com.co/anime/a/","https://evil.example/anime/a/","https://kimcartoon.com.co/admin/")) {
   try {kim.validate(invalid);throw new AssertionError("Invalid URL accepted");}catch(IllegalArgumentException expected){}
  }
  var shows=kim.parseSearch("<div class='postbody'><div class='listupd'><div class='bsx'><a href='/anime/digimon-beatbreak/'><h2>Digimon Beatbreak</h2></a></div></div></div><aside><a href='/anime/other/'>Other</a></aside>","https://kimcartoon.com.co/?s=digimon");
  if(shows.size()!=1)throw new AssertionError("Search result isolation");
  var entries=kim.parseEpisodes("<div class='episodes-ul'><a class='ep-item' data-number='1' href='/digimon-beatbreak-episode-1/'>1</a><a class='ep-item' data-number='2' href='https://evil.example/a-episode-2/'>ad</a></div>",shows.get(0).getUrl());
  if(entries.size()!=2 || !entries.get(0).getLanguage().equals("sub"))throw new AssertionError("Language listing");
  if(!kim.filename(shows.get(0),entries.get(0)).equals("Digimon Beatbreak - S01E01 - SUB.mp4"))throw new AssertionError(kim.filename(shows.get(0),entries.get(0)));
  if(kim.filename(shows.get(0),entries.get(0)).equals(kim.filename(shows.get(0),entries.get(1))))throw new AssertionError("Language collision");
  String playlist="#EXTM3U\n#EXT-X-STREAM-INF:RESOLUTION=1920x1080\n1080.m3u8\n#EXT-X-STREAM-INF:RESOLUTION=1280x720\n720.m3u8\n#EXT-X-STREAM-INF:RESOLUTION=640x360\n360.m3u8\n";
  for(int height:new int[]{360,720,1080})if(kim.selectQuality(playlist,"https://example.com/master.m3u8",height).getSecond()!=height)throw new AssertionError("Quality selection");
  String encoded=Base64.getEncoder().encodeToString("<iframe src='https://gogoanime.com.by/streaming.php?ep=145539&type=sub'></iframe>".getBytes());
  String markup="<a data-hash='"+encoded+"'>GogoAnime Sub</a>";
  if(!kim.playerUrl(markup,entries.get(0).getUrl(),"sub").contains("145539"))throw new AssertionError("Player extraction");
  try {kim.playerUrl(markup,entries.get(0).getUrl(),"dub");throw new AssertionError("Wrong language fallback");}catch(KimCartoon.MissingPlayer expected){}
  System.out.println("PASS: URL validation, catalog isolation, language selection, filenames, quality choice, missing-player errors");
  if(args.length>0) {
   var live=QualityProbe.<KimCartoon.Resolved>await(c->kim.resolveOnce(entries.get(0),Quality.MED,c));
   if(live.getHeight()!=720 || live.getSubtitle()==null)throw new AssertionError("Missing quality or captions");
   var sub=QualityProbe.<java.io.File>await(c->kim.subtitleFile(live,c));
   if(!Files.readString(sub.toPath()).startsWith("WEBVTT"))throw new AssertionError("Captions invalid");
   sub.delete();
   Files.writeString(Path.of("/tmp/zen-kim-integrated-url"),live.getMedia().getUrl());
   try(var bridge=QualityProbe.<nobility.downloader.core.scraper.player.HiAnimePlaybackServer>await(c->AnimeFreak.INSTANCE.playback(live.getMedia(),c))) {
    var log=Files.createTempFile("zen-kim-decode-", ".log");
    var process=new ProcessBuilder("ffmpeg","-nostdin","-v","error","-i",bridge.getUrl(),"-t","3","-f","null","-").redirectErrorStream(true).redirectOutput(log.toFile()).start();
    if(!process.waitFor(45,java.util.concurrent.TimeUnit.SECONDS)){process.destroyForcibly();throw new AssertionError("Playback bridge timed out");}
    if(process.exitValue()!=0)throw new AssertionError(Files.readString(log));
   }
   System.out.println("PASS: live fresh 720p source, English captions and playback bridge decoding");
  }
  System.exit(0);
 }
}
