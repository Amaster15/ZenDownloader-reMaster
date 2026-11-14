import nobility.downloader.core.scraper.video_download.HiAnimeFallback;
public class HiAnimePlayerConfigTest {
 public static void main(String[] args) throws Exception {
  String key="ExamplePublicPlayerConfigKey123";
  for(String declaration:new String[]{"let a=\""+key+"\",i=\"https://new.vidnest.fun\"", "let a=\""+key+"\",n=\"https://new.vidnest.fun\"", "const token = '"+key+"', endpoint = 'https://new.vidnest.fun'"}) {
   if(!key.equals(HiAnimeFallback.playerResponseKey(declaration))) throw new AssertionError("Variable rename broke parser");
  }
  if(HiAnimeFallback.playerResponseKey("let a=\""+key+"\",n=\"https://wrong.example\"")!=null) throw new AssertionError("Unrelated config matched");
  if(args.length>0 && HiAnimeFallback.playerResponseKey(java.nio.file.Files.readString(java.nio.file.Path.of(args[0])))==null) throw new AssertionError("Current player config missing");
  System.out.println("PASS: renamed declarations, whitespace, quotes, unrelated host and current fixture");
 }
}
