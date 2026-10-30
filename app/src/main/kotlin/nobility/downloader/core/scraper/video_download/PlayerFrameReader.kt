package nobility.downloader.core.scraper.video_download

import org.openqa.selenium.By
import org.openqa.selenium.JavascriptExecutor
import org.openqa.selenium.WebDriver
import org.openqa.selenium.support.ui.ExpectedConditions
import org.openqa.selenium.support.ui.WebDriverWait
import java.net.URI
import java.time.Duration

/** Follow the embedded player's normal navigation, including its enabled Close button. */
object PlayerFrameReader {
    @JvmOverloads
    fun read(driver: WebDriver, episodeUrl: String, frameUrl: String, timeoutSeconds: Long,
             status: (String) -> Unit = {}): String {
        driver.switchTo().defaultContent()
        driver.get(episodeUrl)
        val wait = WebDriverWait(driver, Duration.ofSeconds(timeoutSeconds.coerceAtLeast(20)))
        val absoluteFrame = URI(episodeUrl).resolve(frameUrl).toString()
        val frame = wait.until {
            it.findElements(By.tagName("iframe")).firstOrNull { element ->
                element.getAttribute("src") == absoluteFrame
            }
        }
        try {
            driver.switchTo().frame(requireNotNull(frame))
            wait.until { (it as JavascriptExecutor).executeScript("return document.readyState") == "complete" }
            if (driver.findElements(By.id("close-btn")).isNotEmpty()) {
                status("Player popup detected. Waiting for its Close button countdown.")
                val started = System.nanoTime()
                var previous = ""
                val close = wait.until { browser ->
                    val button = ExpectedConditions.elementToBeClickable(By.id("close-btn")).apply(browser)
                    if (button == null) {
                        // Read the site's animation clock without changing its timer or controls.
                        val remaining = (browser as JavascriptExecutor).executeScript("""
                            const roll = document.querySelector('#close-btn .cd-roll');
                            const animation = roll && roll.getAnimations && roll.getAnimations()[0];
                            if (!animation || animation.currentTime == null) return null;
                            const end = animation.effect.getComputedTiming().endTime;
                            return Number.isFinite(end) ? Math.max(0, Math.ceil((end-animation.currentTime)/1000)) : null;
                        """) as? Number
                        val message = if (remaining != null && remaining.toLong() > 0)
                            "Player popup: ${remaining.toLong()} seconds remaining before Close."
                        else "Player popup: waiting for Close (ELAPSED_PLACEHOLDER seconds elapsed)."
                            .replace("ELAPSED_PLACEHOLDER", ((System.nanoTime() - started) / 1_000_000_000).toString())
                        if (message != previous) {
                            status(message)
                            previous = message
                        }
                    }
                    button
                }
                status("Player popup countdown finished. Waiting 2 extra seconds before Close.")
                Thread.sleep(2000)
                status("Clicking Close and loading the player.")
                close.click()
                wait.until(ExpectedConditions.stalenessOf(close))
                wait.until { (it as JavascriptExecutor).executeScript("return document.readyState") == "complete" }
                status("Player popup closed.")
            }
            status("Waiting 2 seconds for the player to finish loading.")
            Thread.sleep(2000)
            status("Waiting for the player's Play button to become visible.")
            var busy = false
            try {
                wait.until { browser ->
                    busy = PlayerPageStatus.isServerBusy(browser.pageSource.orEmpty())
                    busy || browser.findElements(By.cssSelector(
                        "button.vjs-big-play-button, button.vjs-play-control[title='Play']"
                    )).any { it.isDisplayed && it.isEnabled && it.getAttribute("aria-disabled") != "true" }
                }
            } catch (e: org.openqa.selenium.TimeoutException) {
                status("Player Play button did not appear. This page attempt failed; retry settings apply.")
                throw e
            }
            if (busy) status("Video server is at full capacity. No player is ready; retry settings apply.")
            else status("Player Play button is visible. Looking for video links.")
            return driver.pageSource.orEmpty()
        } finally {
            driver.switchTo().defaultContent()
        }
    }
}
