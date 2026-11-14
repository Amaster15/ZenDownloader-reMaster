package nobility.downloader.core.driver

import kotlinx.coroutines.*
import nobility.downloader.core.Core
import nobility.downloader.ui.components.dialog.DialogHelper
import nobility.downloader.utils.FrogLog
import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** Runs from the updated JAR too: ZIP-only helper files are not required. */
object BrowserSandboxRepair {
    private val prompted = AtomicBoolean(false)

    fun needsRepair(browser: String): Boolean {
        if (!System.getProperty("os.name").startsWith("Linux")) return false
        val dir = Files.createTempDirectory("zen-sandbox-check-").toFile()
        var process: Process? = null
        return try {
            val log = File(dir, "startup.log")
            process = ProcessBuilder(browser, "--headless=new", "--no-first-run", "--no-default-browser-check",
                "--user-data-dir=${dir.resolve("profile")}", "--dump-dom", "about:blank")
                .redirectErrorStream(true).redirectOutput(log).start()
            process.waitFor(15, TimeUnit.SECONDS)
            log.readText().contains("No usable sandbox!")
        } finally {
            process?.let { if (it.isAlive) { it.destroyForcibly(); it.waitFor(5, TimeUnit.SECONDS) } }
            dir.deleteRecursively()
        }
    }

    suspend fun checkOnStartup(allowReprompt: Boolean = false) {
        if (!System.getProperty("os.name").startsWith("Linux")) return
        val browser = ManagedBrowser.browserPath()
        if (!needsRepair(browser)) return
        if (!allowReprompt && !prompted.compareAndSet(false, true)) return
        prompted.set(true)
        withContext(Dispatchers.Main) {
            DialogHelper.showConfirm(
                "Ubuntu is blocking the managed browser's sandbox. Repair installs an AppArmor policy allowing user namespaces only for Chrome in your ZenDownloader browser directory. Other binaries placed at that matching path also receive permission. Your system will ask for administrator authentication. Chrome's sandbox and global AppArmor settings stay enabled.",
                "Browser setup needs repair", onConfirmTitle = "Repair", onDenyTitle = "Later"
            ) {
                Core.taskScope.launch(Dispatchers.IO) {
                    try {
                        installPolicy()
                        check(!needsRepair(browser)) { "The policy was loaded but Chrome still reports no usable sandbox." }
                        withContext(Dispatchers.Main) { DialogHelper.showMessage("Browser setup", "Browser repair completed. Retry your search.") }
                    } catch (e: Exception) {
                        FrogLog.error("Browser sandbox repair did not complete", e)
                        withContext(Dispatchers.Main) { DialogHelper.showError("Browser repair", e.message) }
                    }
                }
            }
        }
    }

    internal fun installPolicy() {
        val home = System.getProperty("user.home")
        require(home.startsWith("/") && Regex("[A-Za-z0-9_./-]+").matches(home)) { "Home path needs a manually reviewed AppArmor policy." }
        // Fixed program; the home directory is a separate validated argument, never executable text.
        val script = """
            set -eu
            home="${'$'}1"
            case "${'$'}home" in /*) ;; *) exit 2;; esac
            case "${'$'}home" in *[!a-zA-Z0-9_./-]*) exit 2;; esac
            command -v apparmor_parser >/dev/null
            tmp=${'$'}(mktemp)
            trap 'rm -f "${'$'}tmp"' EXIT
            printf '%s\n' 'abi <abi/4.0>,' 'include <tunables/global>' "profile zendownloader-managed-chrome \"${'$'}home/.zen_database/browser/*/chrome-linux64/chrome\" flags=(unconfined) {" '  userns,' '}' > "${'$'}tmp"
            apparmor_parser --skip-kernel-load "${'$'}tmp"
            install -m 0644 "${'$'}tmp" /etc/apparmor.d/zendownloader-managed-chrome
            apparmor_parser -r /etc/apparmor.d/zendownloader-managed-chrome
        """.trimIndent()
        val log = File.createTempFile("zen-sandbox-repair-", ".log")
        var process: Process? = null
        try {
            process = ProcessBuilder("pkexec", "/bin/sh", "-c", script, "zen-browser-repair", home)
                .redirectErrorStream(true).redirectOutput(log).start()
            check(process.waitFor(120, TimeUnit.SECONDS)) { "Administrator approval timed out. Retry after restarting the app." }
            check(process.exitValue() == 0) { "Repair cancelled or failed (exit ${process.exitValue()}). ${log.readText().takeLast(2000)}" }
        } finally {
            process?.let { if (it.isAlive) it.destroyForcibly() }
            log.delete()
        }
    }
}
