import nobility.downloader.core.*;
import nobility.downloader.core.entities.*;
import java.util.List;
public class SeriesMetadataRefreshTest {
 public static void main(String[] args) {
  BoxHelper.Companion.init();
  var series=new Series();series.setSlug("anime/watch-test");series.setName("Watch test");series.setIdentity(4);
  BoxHelper.Companion.addSeries(series,nobility.downloader.core.entities.data.SeriesIdentity.NEW);
  series.updateEpisodes(List.of(new Episode("Episode 1","watch-test-episode-1","anime/watch-test")));
  series.updateEpisodes(List.of(new Episode("Season 2 Episode 1","watch-test-episode-1","anime/watch-test")));
  var saved=BoxHelper.Companion.seriesForSlug("anime/watch-test");
  if(saved.getEpisodesSize()!=1 || !saved.getEpisodes().get(0).getName().equals("Season 2 Episode 1")) throw new AssertionError("Same-size season metadata refresh was lost");
  series.updateEpisodes(List.of());
  if(series.getEpisodesSize()!=1)throw new AssertionError("Empty refresh erased cached episodes");
  System.out.println("PASS: same-count season metadata refresh persists; empty responses retain episodes");System.exit(0);
 }
}
