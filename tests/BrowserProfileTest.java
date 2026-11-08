import java.io.File;
import org.openqa.selenium.chrome.ChromeOptions;
import nobility.downloader.core.driver.undetected_chrome.ChromeDriverBuilder;

public class BrowserProfileTest {
    public static void main(String[] args) throws Exception {
        var builder = new ChromeDriverBuilder();
        var binary = ChromeDriverBuilder.class.getDeclaredField("binaryLocation");
        binary.setAccessible(true);
        binary.set(builder, "/snap/bin/chromium");
        var configure = ChromeDriverBuilder.class.getDeclaredMethod("setUserDataDir", ChromeOptions.class);
        configure.setAccessible(true);
        configure.invoke(builder, new ChromeOptions());
        var profile = ChromeDriverBuilder.class.getDeclaredField("userDataDir");
        profile.setAccessible(true);
        var directory = new File((String) profile.get(builder));
        var expected = new File(System.getProperty("user.home"), "snap/chromium/common/zen-profiles");
        if (!directory.getParentFile().equals(expected) || !directory.isDirectory())
            throw new AssertionError(directory);
        directory.delete();
        System.out.println("PASS: Snap browser profiles use the Snap-writable directory");
    }
}
