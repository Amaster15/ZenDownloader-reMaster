import nobility.downloader.core.BoxHelper;
import nobility.downloader.ui.windows.FFSetDownloaderWindow;
import nobility.downloader.utils.computer_info.ComputerInfo;
import java.nio.file.*;
public class MediaSetupSmoke {
    public static void main(String[] args) throws Exception {
        BoxHelper.Companion.init();
        Resource<Boolean> result = QualityProbe.await(c -> new FFSetDownloaderWindow().downloadLatestFFmpeg(ComputerInfo.Companion.myComputer(), false, c));
        if (result.isFailed()) throw new AssertionError(result.getMessage());
        for (String name : new String[]{"ffmpeg", "ffplay"}) {
            Path file = Path.of(System.getProperty("user.home"), ".zen_database", name);
            if (!Files.isExecutable(file)) throw new AssertionError(file);
            Process p = new ProcessBuilder(file.toString(), "-version").redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
            if (!p.waitFor(15, java.util.concurrent.TimeUnit.SECONDS) || p.exitValue() != 0) throw new AssertionError(name);
        }
        System.out.println("PASS: automatic FFmpeg/FFplay download and executable validation");
        System.exit(0);
    }
}
