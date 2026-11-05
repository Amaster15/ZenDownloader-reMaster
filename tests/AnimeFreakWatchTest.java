import nobility.downloader.core.scraper.*;
import java.net.*;
public class AnimeFreakWatchTest {
 public static void main(String[] args)throws Exception {
  var provider=WatchCatalog.Provider.ANIMEFREAK;
  var found=WatchCatalog.parseSearch("<a href='/anime/example-dub'>Example</a><a href='/anime/example-dub/episode/1'>Episode</a><a href='https://ads.example/anime/ad'>Ad</a>","https://animefreak.it.com/search?keyword=example",provider);
  if(found.size()!=1)throw new AssertionError("Search must exclude episodes and external links");
  var entries=WatchCatalog.parseEpisodes("<h1>Example (Dub)</h1><a href='/anime/example-dub/episode/2'>2</a><a href='/anime/example-dub/episode/1'>1</a>","https://animefreak.it.com/anime/example-dub",provider);
  if(entries.size()!=2 || !entries.get(0).getLanguage().equals("dub"))throw new AssertionError("Episode integration");
  System.out.println("PASS: Watch search and episode parsing");
  if(args.length>0) {
   nobility.downloader.core.BoxHelper.Companion.init();
   nobility.downloader.core.Core.Companion.setErrorPrintStream(System.err);
   nobility.downloader.core.driver.BrowserDriverSetup.INSTANCE.setup("/usr/bin/chromium");
   var results=QualityProbe.<java.util.List<WatchCatalog.Show>>await(c->WatchCatalog.INSTANCE.search("tempal",provider,c));
   if(results.stream().noneMatch(x->x.getUrl().contains("tempal-item-no-chikara-dub")))throw new AssertionError("Live search missing known series: "+results);
   System.out.println("PASS: live title search, "+results.size()+" results");
   var stream=QualityProbe.<AnimeFreak.Stream>await(c->AnimeFreak.INSTANCE.resolve(new AnimeFreak.Episode(1,"https://animefreak.it.com/anime/tempal-item-no-chikara-dub/episode/1"),c));
   try(var bridge=QualityProbe.<nobility.downloader.core.scraper.player.HiAnimePlaybackServer>await(c->AnimeFreak.INSTANCE.playback(stream,c))) {
    String playlist=new String(URI.create(bridge.getUrl()).toURL().openStream().readAllBytes());
    String segment=playlist.lines().filter(x->!x.isBlank()&&!x.startsWith("#")).findFirst().orElseThrow();
    try(var in=URI.create(bridge.getUrl()).resolve(segment).toURL().openStream()) {if(in.readAllBytes().length<1000)throw new AssertionError("Missing media");}
    java.nio.file.Files.writeString(java.nio.file.Path.of("/tmp/zen-af-watch-url"),bridge.getUrl());
    System.out.println("PASS: fresh stream, loopback playlist and media segment");
   }
  }
  System.exit(0);
 }
}
