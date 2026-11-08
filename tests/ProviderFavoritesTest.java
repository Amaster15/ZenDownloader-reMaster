import nobility.downloader.core.scraper.*;
public class ProviderFavoritesTest {
 public static void main(String[] args) {
  var favorites=ProviderFavorites.INSTANCE;favorites.load();
  var show=new WatchCatalog.Show("Digimon Beatbreak","https://kimcartoon.com.co/anime/digimon-beatbreak/",WatchCatalog.Provider.KIMCARTOON);
  if(args.length>0){if(!favorites.contains(show))throw new AssertionError("Favorites did not persist");favorites.toggle(show);if(favorites.contains(show))throw new AssertionError("Remove failed");}
  else {favorites.toggle(show);if(!favorites.contains(show))throw new AssertionError("Add failed");}
  System.out.println("PASS: provider favorite "+(args.length>0?"reload/removal":"save"));
 }
}
