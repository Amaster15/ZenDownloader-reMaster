import nobility.downloader.core.scraper.WatchCatalog;
import nobility.downloader.utils.EpisodeNaming;

public class WatchCatalogTest {
 static void equal(Object expected, Object actual) {
  if (!java.util.Objects.equals(expected,actual)) throw new AssertionError("Expected "+expected+", got "+actual);
 }
 static void naming(String title,String slug,String series,String expected) {
  equal(expected,EpisodeNaming.filename(title,slug,series,false));
 }
 public static void main(String[] args) throws Exception {
  naming("Season 1 Episode 1", "", "Show", "S01E01");
  naming("Show Season 2 Episode 12 English Dubbed", "", "Show", "Show S02E12 English Dubbed");
  naming("Episode 3 English Dubbed", "show-season-4-episode-3", "Show", "S04E03 English Dubbed");
  naming("Episode 3", "", "Show Season 2", "S02E03");
  naming("Episode 100", "", "Show", "S01E100");
  naming("S03E09 - Title", "", "Show", "S03E09 - Title");
  naming("Episode 12.5", "episode-12", "Show", "Episode 12.5");
  naming("Episode 1-2", "episode-1", "Show", "Episode 1-2");
  naming("OVA Episode 1", "episode-1", "Show", "OVA Episode 1");
  naming("The Movie", "", "Show", "The Movie");
  equal("Episode 1",EpisodeNaming.filename("Episode 1","","Show",true));
  equal("anime/show", WatchCatalog.slug("https://wcoflix.tv/anime/show?season=all"));
  equal("anime/show", WatchCatalog.slug("https://wcoflix.tv/anime/show/"));
  equal("Overgeared - S01E01 English Dubbed", EpisodeNaming.downloadFilename("Season 1 Episode 1 English Dubbed", "", "Overgeared", false));
  equal("Digimon Adventure - S01E01 English Dubbed", EpisodeNaming.downloadFilename("Digimon Adventure Episode 1 English Dubbed", "", "Digimon Adventure", false));
  equal("Digimon Adventure - S01E01 - Title", EpisodeNaming.downloadFilename("Digimon Adventure - S01E01 - Title", "", "Digimon Adventure", false));
  equal("Movie Title", EpisodeNaming.downloadFilename("Movie Title", "", "Series", true));
  var seasonDoc=org.jsoup.Jsoup.parse("<select><option value='s2-0'>Season 2</option></select><a data-season='s2-0'><span>Episode 1 English Dubbed</span></a>");
  equal("Season 2 Episode 1 English Dubbed",EpisodeNaming.sourceTitle(seasonDoc.selectFirst("a")));
  var wco=WatchCatalog.parseEpisodes("<div id='episodeList'><a class='dark-episode-item' href='/show-season-2-episode-1'><span>Season 2 Episode 1</span></a><a class='dark-episode-item' href='https://ad.invalid/ad'>Ad</a></div>","https://wcoflix.tv/anime/show",WatchCatalog.Provider.WCO);
  equal(1,wco.size()); equal("Season 2 Episode 1",wco.get(0).getTitle());
  var legacy=WatchCatalog.parseEpisodes("<div class='cat-eps'><a href='/show-episode-2'>Episode 2</a></div>","https://wcoflix.tv/anime/show",WatchCatalog.Provider.WCO);
  equal(1,legacy.size());
  var hi=WatchCatalog.parseEpisodes("<a data-episode='2' data-dub='1' data-sub='1' href='/watch-show-episode-2'></a><a data-episode='3' data-dub='0' data-sub='1' href='/watch-show-episode-3'></a>","https://hianime.ms/watch-show-episode-2",WatchCatalog.Provider.HIANIME);
  equal(3,hi.size()); equal("dub",hi.get(0).getLanguage()); equal("sub",hi.get(2).getLanguage());
  var search=WatchCatalog.parseSearch("<a href='/details/show'>Show</a><a href='/details/show'>Show</a><a href='https://ad.invalid/details/show'>Ad</a>","https://hianime.ms/search?q=show",WatchCatalog.Provider.HIANIME);
  equal(1,search.size());
  if (args.length>0) {
   var live=WatchCatalog.parseSearch(java.nio.file.Files.readString(java.nio.file.Path.of(args[0])),"https://hianime.ms/search?q=overgeared",WatchCatalog.Provider.HIANIME);
   if (live.stream().noneMatch(s -> s.getTitle().equals("Overgeared"))) throw new AssertionError("Live HiAnime search not parsed");
  }
  System.out.println("PASS: season/episode naming, specials, ranges, provider catalogs, language selection, duplicate and offsite filtering");
 }
}
