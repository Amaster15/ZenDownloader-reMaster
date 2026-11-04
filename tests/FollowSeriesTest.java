import java.util.*;
import nobility.downloader.core.scraper.*;
public class FollowSeriesTest {
 static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
 public static void main(String[] args)throws Exception {
  var show=new WatchCatalog.Show("Test show","https://wcoflix.tv/anime/follow-test",WatchCatalog.Provider.WCO);
  var first=new WatchCatalog.Entry("Episode 1","https://wcoflix.tv/episode-1","");
  var second=new WatchCatalog.Entry("Episode 2","https://wcoflix.tv/episode-2","");
  var manager=FollowSeries.INSTANCE;manager.load();
  if(args[0].equals("seed")) {
   manager.toggle(show,List.of(first));
   var baseline=manager.getItems().get(0);
   check(baseline.getUnread().isEmpty(),"Existing episodes must not alert on first follow");
   var newer=FollowSeries.refreshed(baseline,List.of(first,second,second),20);
   check(newer.getUnread().size()==1,"New episode must alert once");
   var repeat=FollowSeries.refreshed(newer,List.of(new WatchCatalog.Entry("Renamed Episode 1",first.getUrl(),""),second),30);
   check(repeat.getUnread().size()==1,"Renames and repeated checks must not duplicate alerts");
   var shortened=FollowSeries.refreshed(repeat,List.of(second),40);
   check(FollowSeries.refreshed(shortened,List.of(first,second),50).getUnread().size()==1,"Reappearing old episodes must not alert");
   try {FollowSeries.refreshed(repeat,List.of(),60);throw new AssertionError("Empty provider response accepted");}catch(IllegalArgumentException expected){}
   check(FollowSeries.key(show).equals(FollowSeries.key(new WatchCatalog.Show("Test","https://www.wcoflix.tv/anime/follow-test/",WatchCatalog.Provider.WCO))),"Canonical follow identity");
   manager.getItems().set(0,repeat);
   var other=new WatchCatalog.Show("Other","https://wcoflix.tv/anime/other",WatchCatalog.Provider.WCO);
   manager.toggle(other,List.of(first));manager.toggle(other,List.of()); // Persist unread state through the normal save path.
  } else {
   check(manager.getItems().size()==1,"Follow did not survive restart");
   check(manager.getItems().get(0).getKnown().size()==2,"Known episodes lost");
   if(args[0].equals("load")) {
    check(manager.getUnreadCount()==1,"Unread notification lost on restart");
    manager.markSeen(show);
   } else check(manager.getUnreadCount()==0,"Mark seen did not persist");
  }
  System.out.println("PASS: Follow Series "+args[0]);
 }
}
