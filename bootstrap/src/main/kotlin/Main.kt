import java.io.File

private val installationDir: File by lazy {
    File(object {}.javaClass.protectionDomain.codeSource.location.toURI()).parentFile
}

fun main(args: Array<String>) {

    val latestJar = findJar()

    if (latestJar == null) {
        println(
            """
                The ZenDownloader JAR wasn't found.
                Make sure to keep the file structure the same as it was downloaded.
                The JAR should be located inside the app folder.
            """.trimIndent()
        )
        return
    }

    var currentJar: File = latestJar
    while (launchJar(currentJar, args).waitFor() == AppInfo.UPDATE_CODE) {
        val installed = findJar()
        if (installed != null && installed != currentJar) {
            currentJar = installed
            continue
        }
        // Compatibility with older app versions that request a launcher download.
        val release = Updater.parseLatestRelease().data
        if (release != null && release.downloadName != currentJar.name) {
            try {
                val staged = ReleaseDownload.stage(release, File(installationDir, "app")) { _, _ -> }
                currentJar = ReleaseDownload.install(staged, release.downloadName)
            } catch (error: Exception) {
                println("Update failed; reopening the previous version: ${error.message}")
            }
        } else {
            println("No newer release was installed; reopening the current version.")
        }
    }
}

private fun launchJar(
    jar: File,
    args: Array<String>
): Process {
    val executable = if (System.getProperty("os.name").startsWith("Windows", ignoreCase = true)) {
        "java.exe"
    } else {
        "java"
    }
    val javaBin = File(System.getProperty("java.home"), "bin/$executable")
    val command = mutableListOf(
        javaBin.absolutePath,
        "-Xmx3G",
        "-Dzen.launcher.updates=2",
        "-jar",
        jar.absolutePath
    )
    command.addAll(args)
    return ProcessBuilder(command)
        .directory(installationDir)
        .inheritIO()
        .start()
}

private fun findJar(): File? {
    val appDir = File(installationDir, "app")
    return appDir
        .listFiles { f -> f.name.matches(Regex("""ZenDownloader-\d+\.\d+\.\d+\.jar""")) }
        ?.maxWithOrNull(Comparator { f1, f2 ->
            val v1 = extractVersion(f1.name)
            val v2 = extractVersion(f2.name)
            v1.zip(v2)
                .map { it.first.compareTo(it.second) }
                .firstOrNull { it != 0 } ?: 0
        })

}

private fun extractVersion(name: String): List<Int> {
    return Regex("""(\d+)\.(\d+)\.(\d+)""")
        .find(name)
        ?.groupValues
        ?.drop(1)
        ?.map { it.toInt() }
        ?: listOf(0, 0, 0)
}

