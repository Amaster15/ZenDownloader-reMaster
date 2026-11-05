import nobility.downloader.core.scraper.AnimeFreak;
public class AnimeFreakTest {
 public static void main(String[]args)throws Exception{
 var p=AnimeFreak.INSTANCE;String base="https://animefreak.it.com/anime/example-dub";
 var series=p.parseSeries("<h1>Example (Dub)</h1><a href='/anime/example-dub/episode/2'>2</a><a href='/anime/example-dub/episode/1'>1</a><a href='/anime/example-dub/episode/1'>1</a><a href='https://ads.example/episode/3'>ad</a><a href='/anime/other/episode/4'>Other</a>",base);
 if(series.getEpisodes().size()!=2 || series.getEpisodes().get(0).getNumber()!=1)throw new AssertionError("Episode filtering/sorting");
 if(!p.filename(series,series.getEpisodes().get(0)).equals("Example (Dub) - S01E01.mp4"))throw new AssertionError("Filename");
 for(String url:new String[]{"http://animefreak.it.com/anime/a","https://evil.example/anime/a","https://animefreak.it.com.evil.example/anime/a","https://animefreak.it.com/anime/../x"}){
  try{p.seriesUrl(url);throw new AssertionError("Invalid URL accepted");}catch(IllegalArgumentException expected){}
 }
 System.out.println("PASS: series/episode URL validation, unrelated link exclusion, deduplication, numbering and filenames");
 if(args.length>0){
  nobility.downloader.core.BoxHelper.Companion.init();
  nobility.downloader.core.Core.Companion.setErrorPrintStream(System.err);
  nobility.downloader.core.driver.BrowserDriverSetup.INSTANCE.setup("/usr/bin/chromium");
  var live=QualityProbe.<AnimeFreak.Series>await(c->p.load(args[0],c));
  System.out.println("Live series: "+live.getTitle()+", episodes: "+live.getEpisodes().size());
  var stream=QualityProbe.<AnimeFreak.Stream>await(c->p.resolve(live.getEpisodes().get(0),c));
  java.nio.file.Files.writeString(java.nio.file.Path.of("/tmp/zen-animefreak-app-stream.txt"),stream.getUrl());
  System.out.println("PASS: app browser resolved first episode stream on "+java.net.URI.create(stream.getUrl()).getHost());
 }
 System.exit(0);
 }
}
