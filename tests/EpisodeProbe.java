import java.nio.file.*;
import java.time.Duration;
import org.openqa.selenium.By;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.WebDriverWait;
import nobility.downloader.core.BoxHelper;
import nobility.downloader.core.driver.BrowserDriverSetup;
import nobility.downloader.core.driver.undetected_chrome.ChromeDriverBuilder;
import nobility.downloader.core.driver.undetected_chrome.UndetectedChromeDriver;

public class EpisodeProbe {
    public static void main(String[] args) throws Exception {
        BoxHelper.Companion.init();
        String path = BrowserDriverSetup.INSTANCE.setup("/usr/bin/chromium");
        ChromeDriverBuilder builder = new ChromeDriverBuilder();
        var driver = (UndetectedChromeDriver) builder.build(new ChromeOptions(), path, "/usr/bin/chromium", true);
        try {
            driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(45));
            driver.go(args[0], false);
            String frame = driver.findElement(By.cssSelector("#anime-js-0, #anime-js-1, #cizgi-js-0")).getAttribute("src");
            System.out.println("Episode: " + driver.getTitle());
            System.out.println("Player frame: " + frame);
            if (args.length > 2 && args[2].equals("embedded")) {
                driver.switchTo().frame(driver.findElement(By.cssSelector("#anime-js-0, #anime-js-1, #cizgi-js-0")));
            } else {
                driver.go(frame, false);
            }
            Files.writeString(Path.of(args[1]+".before.html"), driver.getPageSource());
            long gateStart = System.nanoTime();
            System.out.println("Close button present: " + !driver.findElements(By.id("close-btn")).isEmpty());
            if (!driver.findElements(By.id("close-btn")).isEmpty()) {
                new WebDriverWait(driver, Duration.ofSeconds(20)).until(d -> d.findElement(By.id("close-btn")).isEnabled());
                System.out.println("Close enabled after seconds: " + (System.nanoTime()-gateStart)/1_000_000_000.0);
                driver.findElement(By.id("close-btn")).click();
            }
            try { new WebDriverWait(driver, Duration.ofSeconds(20)).until(d ->
                d.getPageSource().contains("Servers Busy") || d.getPageSource().contains("$.getJSON") || !d.findElements(By.tagName("video")).isEmpty()); } catch (org.openqa.selenium.TimeoutException ignored) {}
            System.out.println("Player title: " + driver.getTitle());
            System.out.println("Player URL: " + driver.getCurrentUrl());
            System.out.println("Player text: " + driver.findElement(By.tagName("body")).getText());
            Files.writeString(Path.of(args[1]), driver.getPageSource());
        } finally {
            try { driver.quit(); } finally { builder.close(); }
        }
        System.exit(0);
    }
}
