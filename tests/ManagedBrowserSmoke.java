import nobility.downloader.core.BoxHelper;
import nobility.downloader.core.driver.ManagedBrowser;
import nobility.downloader.core.driver.BrowserDriverSetup;
import nobility.downloader.core.driver.undetected_chrome.ChromeDriverBuilder;
import org.openqa.selenium.chrome.ChromeOptions;
public class ManagedBrowserSmoke {
    public static void main(String[] args) throws Exception {
        BoxHelper.Companion.init();
        var pair = ManagedBrowser.INSTANCE.ensure();
        if (!pair.equals(ManagedBrowser.INSTANCE.ensure())) throw new AssertionError("Cache not reused");
        String path = BrowserDriverSetup.INSTANCE.setup("");
        var builder = new ChromeDriverBuilder();
        var driver = builder.build(new ChromeOptions(), path, pair.getFirst().getAbsolutePath(), true);
        try {
            driver.get("data:text/html,<title>Managed browser works</title>");
            if (!driver.getTitle().equals("Managed browser works")) throw new AssertionError(driver.getTitle());
            System.out.println("PASS: fresh download, cached reuse, matching driver, and browser launch");
        } finally { driver.quit(); builder.close(); }
        System.exit(0);
    }
}
