import nobility.downloader.Page;
import java.util.List;
public class NavigationTest {
 public static void main(String[] args) {
  if(!Page.Companion.getMainPages().equals(List.of(Page.DATABASE,Page.WATCH,Page.DOWNLOADER,Page.FOLLOW)))throw new AssertionError("Main tab order");
  for(var p:List.of(Page.DOWNLOADS,Page.COMPLETED))if(p.getMainPage()!=Page.DOWNLOADER)throw new AssertionError("Downloads grouping");
  for(var p:List.of(Page.FAVORITES,Page.HISTORY,Page.NEW_EPISODES))if(p.getMainPage()!=Page.FOLLOW)throw new AssertionError("Library grouping");
  if(Page.SETTINGS.getMainPage()!=Page.SETTINGS || Page.ERROR_CONSOLE.getMainPage()!=Page.ERROR_CONSOLE)throw new AssertionError("Full-page destinations");
  if(Page.Companion.nextPage(Page.COMPLETED)!=Page.FOLLOW || Page.Companion.beforePage(Page.FAVORITES)!=Page.DOWNLOADER)throw new AssertionError("Grouped navigation");
  System.out.println("PASS: main navigation, nested sections, full-page settings and console");
 }
}
