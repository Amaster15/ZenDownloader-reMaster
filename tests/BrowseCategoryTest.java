import nobility.downloader.core.*;
import nobility.downloader.core.entities.*;
import nobility.downloader.core.entities.data.*;
import nobility.downloader.ui.windows.database.*;
import kotlinx.coroutines.flow.*;
import java.util.*;
public class BrowseCategoryTest {
 static Object field(Object o,String name)throws Exception{var f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(o);}
 static void expect(StateFlow<List<Series>> f,String title)throws Exception{long end=System.currentTimeMillis()+4000;while(System.currentTimeMillis()<end){if(f.getValue().size()==1&&f.getValue().get(0).getName().equals(title)){System.out.println("PASS "+title);return;}Thread.sleep(20);}throw new AssertionError("Expected "+title+" got "+f.getValue().stream().map(Series::getName).toList());}
 public static void main(String[] args)throws Exception{
 BoxHelper.Companion.init();
 for(var kind:SeriesIdentity.values()){var s=new Series();s.setName(kind.name());s.setSlug("anime/"+kind.name());s.setIdentity(kind.getType());BoxHelper.Companion.addSeries(s,kind);}
 var view=new DatabaseWindow(false);var source=(Flow<List<Series>>)field(view,"series");var flow=StateFlowKt.MutableStateFlow(List.<Series>of());var job=FlowKt.launchIn(FlowKt.onEach(source,(value,continuation)->{flow.setValue(value);return kotlin.Unit.INSTANCE;}),Core.Companion.getTaskScope());var type=(MutableStateFlow<DatabaseType>)field(view,"mDatabaseType");
 for(var pair:List.of(new Object[]{DatabaseType.DUBBED,"DUBBED"},new Object[]{DatabaseType.SUBBED,"SUBBED"},new Object[]{DatabaseType.MOVIE,"MOVIE"},new Object[]{DatabaseType.CARTOON,"CARTOON"},new Object[]{DatabaseType.MISC,"NEW"})){type.setValue((DatabaseType)pair[0]);expect(flow,(String)pair[1]);}
 type.setValue(DatabaseType.ALL);view.search("DUBBED");expect(flow,"DUBBED");view.search("CARTOON");expect(flow,"CARTOON");System.exit(0);
 }
}
