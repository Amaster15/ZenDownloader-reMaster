import nobility.downloader.core.AssetBoxHelper;
import nobility.downloader.core.BoxHelper;
import nobility.downloader.ui.theme.UiScale;
public class UiScaleStartupTest {
 public static void main(String[] args) {
  var startup = new AssetBoxHelper();
  if(UiScale.INSTANCE.getPercent()!=0 || UiScale.density(1,2160,0)!=2)throw new AssertionError("Startup scaling must work without opening settings");
  startup.close();
  BoxHelper.Companion.init();UiScale.INSTANCE.load();
  UiScale.INSTANCE.select(150);UiScale.INSTANCE.load();
  if(UiScale.INSTANCE.getPercent()!=150)throw new AssertionError("Saved size failed to load after updater closes");
  System.out.println("PASS: scale initialization while updater owns settings, then persisted settings load");System.exit(0);
 }
}
