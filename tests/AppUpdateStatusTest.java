import nobility.downloader.core.updates.AppUpdateStatus;
public class AppUpdateStatusTest {
 public static void main(String[] args) {
  var status=AppUpdateStatus.INSTANCE;
  status.recordVersion("v99.0.0");
  if(!status.getAvailable() || !"v99.0.0".equals(status.getAvailableVersion()))throw new AssertionError("New release must show badge");
  status.recordVersion("v"+AppInfo.VERSION);
  if(status.getAvailable())throw new AssertionError("Current release must clear badge");
  status.recordVersion("99.0.0");
  status.recordVersion("1.0.0");
  if(status.getAvailable())throw new AssertionError("Older release must not show badge");
  System.out.println("PASS: update indicator for newer, current and older releases");
 }
}
