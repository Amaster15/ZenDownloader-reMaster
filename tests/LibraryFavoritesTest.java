import nobility.downloader.core.*;
import nobility.downloader.core.entities.*;
import nobility.downloader.core.entities.data.SeriesIdentity;
import nobility.downloader.ui.windows.database.DatabaseWindow;
import java.util.Set;

public class LibraryFavoritesTest {
 static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
 static void awaitSize(kotlinx.coroutines.flow.StateFlow<java.util.List<Series>> flow, int size) throws Exception {
  long deadline = System.currentTimeMillis() + 5000;
  while (flow.getValue().size() != size && System.currentTimeMillis() < deadline) Thread.sleep(20);
  check(flow.getValue().size() == size, "Favorites tab did not update to " + size + " entries");
 }
 public static void main(String[] args) throws Exception {
  BoxHelper.Companion.init();
  var series = new Series(); series.setSlug("anime/library-test"); series.setName("Library Test"); series.setDescription("A matching description"); series.getGenreNames().add("Adventure"); series.setIdentity(4);
  if (args[0].equals("seed")) {
   long before = LibraryChanges.INSTANCE.getRevision().getValue();
   BoxHelper.Companion.addSeries(series, SeriesIdentity.NEW);
   BoxMaker.INSTANCE.makeFavorite(series.getSlug(), java.util.List.of(), "WCOFUN");
   check(LibraryChanges.INSTANCE.getRevision().getValue() > before, "Writes must invalidate database views");
  }
  check(BoxHelper.Companion.isSeriesFavorited(series), "Favorite must persist across restarts");
  check(BoxHelper.Companion.seriesForSlug(series.getSlug()) != null, "Favorite series must remain in database");
  check(!DatabaseWindow.matches(series, "matching", false, true, true, Set.of()), "Description cannot bypass favorites");
  check(!DatabaseWindow.matches(series, "Adventure", true, false, true, Set.of()), "Genre cannot bypass favorites");
  check(!DatabaseWindow.matches(series, series.getSlug(), false, false, true, Set.of()), "Slug cannot bypass favorites");
  check(DatabaseWindow.matches(series, " library ", false, false, true, Set.of(series.getSlug())), "Favorite title search");
  check(DatabaseWindow.matches(series, "adventure", true, false, false, Set.of()), "Genre case-insensitive search");
  if (args[0].equals("remove")) {
   var favoritesView = new DatabaseWindow(true);
   var field = DatabaseWindow.class.getDeclaredField("series"); field.setAccessible(true);
   var source = (kotlinx.coroutines.flow.Flow<java.util.List<Series>>) field.get(favoritesView);
   var favorites = kotlinx.coroutines.flow.StateFlowKt.MutableStateFlow(java.util.List.<Series>of());
   var subscription = kotlinx.coroutines.flow.FlowKt.launchIn(kotlinx.coroutines.flow.FlowKt.onEach(source, (value, continuation) -> { favorites.setValue(value); return kotlin.Unit.INSTANCE; }), Core.Companion.getTaskScope());
   awaitSize(favorites, 1);
   long before = LibraryChanges.INSTANCE.getRevision().getValue();
   BoxHelper.Companion.removeSeriesFavorite(series.getSlug());
   check(!BoxHelper.Companion.isSeriesFavorited(series), "Removal persisted");
   awaitSize(favorites, 0);
   subscription.cancel(null);
   check(LibraryChanges.INSTANCE.getRevision().getValue() > before, "Removal invalidates views");
  }
  System.out.println("PASS: favorites " + args[0] + ", persistence, filter grouping, and library invalidation");
  System.exit(0);
 }
}
