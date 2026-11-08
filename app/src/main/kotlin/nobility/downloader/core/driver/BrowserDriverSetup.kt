package nobility.downloader.core.driver

import AppInfo
import io.github.bonigarcia.wdm.WebDriverManager
import nobility.downloader.core.driver.undetected_chrome.ChromeUtils
import java.io.File
import java.util.concurrent.TimeUnit

/** Resolve a driver for the browser we actually launch, including distro Chromium. */
object BrowserDriverSetup {
    @Synchronized
    fun setup(browserPath: String = ""): String {
        if (browserPath.isBlank()) {
            val pair = ManagedBrowser.ensure()
            System.setProperty("webdriver.chrome.driver", pair.second.absolutePath)
            return pair.second.absolutePath
        }
        require(browserPath.isNotBlank()) { "Chrome or Chromium was not found." }
        val browserVersion = versionOf(File(browserPath))
            ?: error("Unable to determine browser version: $browserPath")
        val driverName = if (System.getProperty("os.name").startsWith("Windows")) "chromedriver.exe" else "chromedriver"
        val localDriver = System.getenv("PATH").orEmpty().split(File.pathSeparator)
            .filter { it.isNotBlank() }
            .map { File(it, driverName) }
            .firstOrNull { it.isFile && it.canExecute() && versionOf(it)?.substringBefore('.') == browserVersion.substringBefore('.') }
        if (localDriver != null) {
            // The existing driver builder patches its binary. Never modify a system package.
            val target = File(AppInfo.databasePath, "drivers/$browserVersion/$driverName")
            target.parentFile.mkdirs()
            localDriver.copyTo(target, overwrite = true)
            check(target.setExecutable(true) || target.canExecute()) { "Cannot make ChromeDriver executable: $target" }
            System.setProperty("webdriver.chrome.driver", target.absolutePath)
            return target.absolutePath
        }
        WebDriverManager.chromedriver().browserVersion(browserVersion).clearResolutionCache().setup()
        val resolved = System.getProperty("webdriver.chrome.driver")
        check(versionOf(File(resolved))?.substringBefore('.') == browserVersion.substringBefore('.')) {
            "ChromeDriver does not match browser $browserVersion. Set matching browser and driver paths in Settings."
        }
        return resolved
    }

    private fun versionOf(binary: File): String? = runCatching {
        val process = ProcessBuilder(binary.absolutePath, "--version").redirectErrorStream(true).start()
        if (!process.waitFor(10, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            return@runCatching null
        }
        if (process.exitValue() != 0) return@runCatching null
        Regex("""\d+\.\d+\.\d+\.\d+""").find(process.inputStream.bufferedReader().use { it.readText() })?.value
    }.getOrNull()
}
