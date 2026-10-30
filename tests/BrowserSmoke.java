import java.nio.file.*;
import nobility.downloader.core.BoxHelper;
import nobility.downloader.core.driver.BrowserDriverSetup;
import nobility.downloader.core.driver.undetected_chrome.ChromeDriverBuilder;
import nobility.downloader.core.driver.undetected_chrome.UndetectedChromeDriver;
import nobility.downloader.utils.Tools;
import org.openqa.selenium.chrome.ChromeOptions;

/** Run with an isolated -Duser.home so the user's app database stays untouched. */
public class BrowserSmoke {
    public static void main(String[] args) throws Exception {
        BoxHelper.Companion.init();
        String driverPath = BrowserDriverSetup.INSTANCE.setup(args[0]);
        if (driverPath.equals("/usr/bin/chromedriver")) throw new AssertionError("Must not patch system driver");
        System.out.println("Resolved private driver: " + driverPath);
        ChromeDriverBuilder builder = new ChromeDriverBuilder();
        var driver = builder.build(new ChromeOptions(), driverPath, args[0], true);
        try {
            ((UndetectedChromeDriver) driver).go("data:text/html,<title>Zen browser test</title><p id='result'>ready</p>", false);
            if (!driver.getTitle().equals("Zen browser test")) throw new AssertionError(driver.getTitle());
            Object result = driver.executeScript("return document.getElementById('result').textContent");
            if (!"ready".equals(result)) throw new AssertionError(result);
            System.out.println("PASS: app browser builder starts Chromium, loads a page, and executes JavaScript");
            driver.get("https://example.com");
            if (!driver.getTitle().contains("Example Domain")) throw new AssertionError(driver.getTitle());
            System.out.println("PASS: browser network access");
            if (args.length > 1) {
                ((UndetectedChromeDriver) driver).go(args[1], false);
                System.out.println("Site title: " + driver.getTitle());
                System.out.println("Site URL: " + driver.getCurrentUrl());
                System.out.println("Links found: " + driver.findElements(org.openqa.selenium.By.tagName("a")).size());
            }
        } finally {
            try { driver.quit(); } finally { builder.close(); }
        }
        if (!Tools.INSTANCE.ffSetFound(Files.createTempDirectory("zen-ff-test").toFile())) {
            throw new AssertionError("Installed system FFmpeg tools were not detected");
        }
        System.out.println("PASS: system FFmpeg and FFplay detection");
        System.exit(0);
    }
}
