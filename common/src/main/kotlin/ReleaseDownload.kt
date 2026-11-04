import java.io.File
import java.net.URI
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.jar.JarFile
import javax.net.ssl.HttpsURLConnection

/** Downloads to an unlaunchable staging file; never overwrites the running version. */
object ReleaseDownload {
    fun validate(file: File) {
        JarFile(file).use { jar ->
            require(!jar.manifest?.mainAttributes?.getValue("Main-Class").isNullOrBlank()) {
                "The release is not an executable application JAR."
            }
            val entries = jar.entries()
            val buffer = ByteArray(65536)
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                if (!entry.isDirectory) jar.getInputStream(entry).use { input ->
                    while (input.read(buffer) != -1) { /* Validate every compressed entry. */ }
                }
            }
        }
    }

    fun stage(update: Update, folder: File, progress: (Long, Long) -> Unit): File {
        require(update.downloadName.matches(Regex("ZenDownloader-\\d+\\.\\d+\\.\\d+\\.jar"))) { "Unsupported release filename." }
        val uri = URI(update.downloadLink)
        require(uri.scheme == "https" && uri.host == "github.com" &&
            uri.path.startsWith("/Amaster15/ZenDownloader-reMaster/releases/download/")) { "Unexpected release download source." }
        require(folder.isDirectory || folder.mkdirs()) { "Cannot create the app update folder." }
        val staged = File.createTempFile("zen-update-", ".ready", folder)
        val connection = uri.toURL().openConnection() as HttpsURLConnection
        try {
            connection.connectTimeout = 30000
            connection.readTimeout = 30000
            connection.setRequestProperty("Accept-Encoding", "identity")
            val total = connection.contentLengthLong
            var received = 0L
            connection.inputStream.use { input -> staged.outputStream().use { output ->
                val buffer = ByteArray(65536)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    output.write(buffer, 0, count)
                    received += count
                    progress(received, total)
                }
            } }
            require(received > 0 && (total < 0 || received == total)) { "Incomplete release download. Please retry." }
            validate(staged)
            return staged
        } catch (error: Exception) {
            staged.delete()
            throw error
        } finally {
            connection.disconnect()
        }
    }

    fun install(staged: File, name: String): File {
        require(name.matches(Regex("ZenDownloader-\\d+\\.\\d+\\.\\d+\\.jar")))
        val target = File(staged.parentFile, name)
        require(!target.exists()) { "That version already exists in the app folder." }
        Files.move(staged.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE)
        return target
    }
}
