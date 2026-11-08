package nobility.downloader.core.driver

import AppInfo
import com.alibaba.fastjson.JSON
import nobility.downloader.utils.FrogLog
import java.io.File
import java.net.URI
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.zip.ZipInputStream

/** A private, matching browser/driver pair; never modifies the user's browser. */
object ManagedBrowser {
    private val root get() = File(AppInfo.databasePath, "browser")
    private val windows get() = System.getProperty("os.name").startsWith("Windows")
    private val platform get() = if (windows) "win64" else "linux64"
    private val browserName get() = if (windows) "chrome.exe" else "chrome"
    private val driverName get() = if (windows) "chromedriver.exe" else "chromedriver"

    fun browserPath(): String = installed()?.first?.absolutePath.orEmpty()

    private fun installed(): Pair<File, File>? {
        val version = File(root, "current").takeIf { it.isFile }?.readText()?.trim() ?: return null
        if (!Regex("[0-9.]+").matches(version)) return null
        val folder = File(root, version)
        val browser = File(folder, "chrome-$platform/$browserName")
        val driver = File(folder, "chromedriver-$platform/$driverName")
        return if (browser.isFile && browser.canExecute() && driver.isFile && driver.canExecute()) browser to driver else null
    }

    @Synchronized
    fun ensure(): Pair<File, File> {
        installed()?.let { return it }
        check(System.getProperty("os.arch") in listOf("amd64", "x86_64") &&
            (windows || System.getProperty("os.name") == "Linux")) {
            "Automatic browser setup supports Windows and Linux x64. Configure a browser in Settings on this platform."
        }
        root.mkdirs()
        // Serialize installation across app instances, too.
        java.io.RandomAccessFile(File(root, "install.lock"), "rw").use { lockFile ->
            lockFile.channel.lock().use {
                installed()?.let { return it }
                FrogLog.message("Preparing private browser: fetching Google's Chrome for Testing release information…")
                val metadata = connection("https://googlechromelabs.github.io/chrome-for-testing/last-known-good-versions-with-downloads.json")
                val stable = JSON.parseObject(metadata.getInputStream().bufferedReader().use { it.readText() })
                    .getJSONObject("channels").getJSONObject("Stable")
                val version = stable.getString("version")
                check(Regex("[0-9.]+").matches(version))
                val stage = Files.createTempDirectory(root.toPath(), "install-").toFile()
                try {
                    for (kind in listOf("chrome", "chromedriver")) {
                        val entry = stable.getJSONObject("downloads").getJSONArray(kind)
                            .map { it as com.alibaba.fastjson.JSONObject }.first { it.getString("platform") == platform }
                        val url = entry.getString("url")
                        val uri = URI(url)
                        check(uri.scheme == "https" && uri.host == "storage.googleapis.com" && uri.path.startsWith("/chrome-for-testing-public/"))
                        FrogLog.message("Preparing private browser: downloading $kind $version from Google…")
                        ZipInputStream(connection(url).getInputStream().buffered()).use { zip ->
                            while (true) {
                                val item = zip.nextEntry ?: break
                                val output = stage.toPath().resolve(item.name).normalize()
                                check(output.startsWith(stage.toPath())) { "Invalid archive path" }
                                if (item.isDirectory) Files.createDirectories(output) else {
                                    Files.createDirectories(output.parent)
                                    Files.newOutputStream(output).use { zip.copyTo(it) }
                                    if (!windows) output.toFile().setExecutable(true, true)
                                }
                            }
                        }
                    }
                    check(File(stage, "chrome-$platform/$browserName").isFile &&
                        File(stage, "chromedriver-$platform/$driverName").isFile) { "Incomplete browser package" }
                    val target = File(root, version)
                    if (target.exists()) target.deleteRecursively()
                    Files.move(stage.toPath(), target.toPath())
                    val marker = File(root, "current.new")
                    marker.writeText(version)
                    Files.move(marker.toPath(), File(root, "current").toPath(), StandardCopyOption.REPLACE_EXISTING)
                    FrogLog.message("Private browser and matching driver are ready.")
                    return checkNotNull(installed())
                } finally {
                    stage.deleteRecursively()
                }
            }
        }
    }

    private fun connection(url: String) = URI(url).toURL().openConnection().apply {
        connectTimeout = 30_000
        readTimeout = 120_000
    }
}
